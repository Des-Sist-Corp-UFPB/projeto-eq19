import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDatabase } from '../context/DatabaseContext';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { PlusIcon, SearchIcon, TrashIcon, ClockIcon, TrophyIcon, CalendarIcon, DiceIcon, CrownIcon, MapPinIcon } from '../components/Icons';
import { UserAvatar } from '../components/UserAvatar';

export const Sessions: React.FC = () => {
  const { state, addSession, deleteSession } = useDatabase();
  const { currentUser, isAdmin } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  // Search & Filter State
  const [search, setSearch] = useState('');
  const [selectedGameId, setSelectedGameId] = useState('all');
  const [selectedWinnerId, setSelectedWinnerId] = useState('all');
  const [sortBy, setSortBy] = useState<'date-desc' | 'date-asc' | 'duration-desc'>('date-desc');
  
  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  // Record Session Modal State
  const [isRecordModalOpen, setIsRecordModalOpen] = useState(false);

  // Form states
  const [gameId, setGameId] = useState('');
  const [date, setDate] = useState(new Date().toISOString().substring(0, 10));
  const [time, setTime] = useState('19:00');
  const [location, setLocation] = useState('Vivência do Bloco C');
  const [duration, setDuration] = useState(60);
  const [notes, setNotes] = useState('');
  const [selectedParticipants, setSelectedParticipants] = useState<string[]>([]);
  const [winnerId, setWinnerId] = useState('');
  const [initialComment, setInitialComment] = useState('');
  const [photoUrl, setPhotoUrl] = useState('');

  // Handle participant toggling
  const handleParticipantToggle = (userId: string) => {
    setSelectedParticipants(prev => {
      if (prev.includes(userId)) {
        const updated = prev.filter(id => id !== userId);
        // Reset winner if they are no longer a participant
        if (winnerId === userId) setWinnerId('');
        return updated;
      } else {
        return [...prev, userId];
      }
    });
  };

  // Submit recorded session
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!gameId) {
      alert('Por favor, selecione um jogo.');
      return;
    }
    if (selectedParticipants.length === 0) {
      alert('Por favor, selecione pelo menos um participante.');
      return;
    }
    if (!winnerId) {
      alert('Por favor, selecione o vencedor da partida.');
      return;
    }

    const organizerId = currentUser?.id || 'u1';
    // Combine date and time using the local browser timezone
    const sessionDateTime = `${date}T${time}:00`;

    await addSession(
      {
        gameId,
        date: sessionDateTime,
        location,
        organizerId,
        participantIds: selectedParticipants,
        winnerId,
        duration,
        notes,
        photos: photoUrl ? [photoUrl] : []
      },
      initialComment.trim() || undefined
    );

    const gameName = state.boardGames.find(g => g.id === gameId)?.name || 'Jogo';
    showToast(`Partida de ${gameName} gravada com sucesso! 🏆`, 'success');
    setIsRecordModalOpen(false);
    resetForm();
  };

  const resetForm = () => {
    setGameId('');
    setDate(new Date().toISOString().substring(0, 10));
    setTime('19:00');
    setLocation('Vivência do Bloco C');
    setDuration(60);
    setNotes('');
    setSelectedParticipants([]);
    setWinnerId('');
    setInitialComment('');
    setPhotoUrl('');
  };

  // Filter & Sort Logic
  const filteredSessions = state.sessions.filter(session => {
    const game = state.boardGames.find(g => g.id === session.gameId);
    const winner = state.users.find(u => u.id === session.winnerId);
    const participantsNames = session.participantIds
      .map(id => state.users.find(u => u.id === id)?.name || '')
      .join(' ')
      .toLowerCase();

    const matchesSearch =
      (game && game.name.toLowerCase().includes(search.toLowerCase())) ||
      session.notes.toLowerCase().includes(search.toLowerCase()) ||
      session.location.toLowerCase().includes(search.toLowerCase()) ||
      (winner && winner.name.toLowerCase().includes(search.toLowerCase())) ||
      participantsNames.includes(search.toLowerCase());

    const matchesGame = selectedGameId === 'all' || session.gameId === selectedGameId;
    const matchesWinner = selectedWinnerId === 'all' || session.winnerId === selectedWinnerId;

    return matchesSearch && matchesGame && matchesWinner;
  });

  // Sort sessions
  const sortedSessions = [...filteredSessions].sort((a, b) => {
    if (sortBy === 'date-desc') return new Date(b.date).getTime() - new Date(a.date).getTime();
    if (sortBy === 'date-asc') return new Date(a.date).getTime() - new Date(b.date).getTime();
    if (sortBy === 'duration-desc') return b.duration - a.duration;
    return 0;
  });

  // Pagination logic
  const totalItems = sortedSessions.length;
  const totalPages = Math.ceil(totalItems / itemsPerPage);
  const indexOfLastItem = currentPage * itemsPerPage;
  const indexOfFirstItem = indexOfLastItem - itemsPerPage;
  const currentItems = sortedSessions.slice(indexOfFirstItem, indexOfLastItem);

  const goToNextPage = () => {
    if (currentPage < totalPages) setCurrentPage(prev => prev + 1);
  };

  const goToPrevPage = () => {
    if (currentPage > 1) setCurrentPage(prev => prev - 1);
  };

  const handleDeleteSession = async (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (window.confirm('Tem certeza que deseja apagar permanentemente este registro de partida? Isso reverterá as estatísticas dos jogadores.')) {
      await deleteSession(sessionId);
      showToast('Registro de partida removido.', 'info');
      // Adjust current page if empty
      if (currentItems.length === 1 && currentPage > 1) {
        setCurrentPage(prev => prev - 1);
      }
    }
  };

  return (
    <div className="container" style={{ paddingTop: '24px' }}>
      
      {/* Page Header */}
      <div style={headerSectionStyle}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <TrophyIcon size={32} style={{ color: 'var(--color-primary)' }} />
          <div>
            <h1 style={{ fontSize: '2rem', marginBottom: '4px' }}>Histórico de Partidas</h1>
            <p style={{ color: 'var(--color-text-muted)' }}>Veja os resultados de todas as sessões jogadas pelo clube.</p>
          </div>
        </div>
        {currentUser && (
          <button className="btn btn-primary" onClick={() => { resetForm(); setIsRecordModalOpen(true); }}>
            <PlusIcon size={18} /> Registrar Nova Partida
          </button>
        )}
      </div>

      {/* Search and Filters */}
      <div className="card" style={filtersCardStyle}>
        <div style={filterFormGridStyle}>
          {/* Text Search */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">Pesquisa Geral</label>
            <div style={searchWrapperStyle}>
              <SearchIcon size={16} style={searchIconStyle} />
              <input
                type="text"
                placeholder="Buscar por jogo, local, notas, jogador..."
                value={search}
                onChange={e => { setSearch(e.target.value); setCurrentPage(1); }}
                style={searchInputStyle}
              />
            </div>
          </div>

          {/* Game Select */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
              <DiceIcon size={12} />
              <span>Filtrar por Jogo</span>
            </label>
            <select
              className="form-select"
              value={selectedGameId}
              onChange={e => { setSelectedGameId(e.target.value); setCurrentPage(1); }}
            >
              <option value="all">Todos os Jogos</option>
              {state.boardGames.map(g => (
                <option key={g.id} value={g.id}>{g.name}</option>
              ))}
            </select>
          </div>

          {/* Winner Select */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
              <CrownIcon size={12} />
              <span>Vencedor</span>
            </label>
            <select
              className="form-select"
              value={selectedWinnerId}
              onChange={e => { setSelectedWinnerId(e.target.value); setCurrentPage(1); }}
            >
              <option value="all">Qualquer Jogador</option>
              {state.users.map(u => (
                <option key={u.id} value={u.id}>{u.name}</option>
              ))}
            </select>
          </div>

          {/* Sort Select */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">📊 Ordenação</label>
            <select
              className="form-select"
              value={sortBy}
              onChange={e => setSortBy(e.target.value as 'date-desc' | 'date-asc' | 'duration-desc')}
            >
              <option value="date-desc">Mais Recentes Primeiro</option>
              <option value="date-asc">Mais Antigas Primeiro</option>
              <option value="duration-desc">Maior Duração</option>
            </select>
          </div>
        </div>
      </div>

      {/* Session List */}
      <div style={sessionsListContainerStyle}>
        {totalItems === 0 ? (
          <div className="card text-center" style={{ padding: '64px' }}>
            <p style={{ color: 'var(--color-text-muted)', fontSize: '1.1rem' }}>Nenhuma partida registrada com esses filtros.</p>
          </div>
        ) : (
          currentItems.map(session => {
            const game = state.boardGames.find(g => g.id === session.gameId);
            const winner = state.users.find(u => u.id === session.winnerId);

            return (
              <div
                key={session.id}
                className="card card-hoverable"
                style={sessionCardItemStyle}
                onClick={() => navigate(`/sessions/${session.id}`)}
              >
                {/* Header */}
                <div style={sessionCardHeaderStyle}>
                  <div>
                    <span style={{ ...sessionDateBadgeStyle, display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                      <CalendarIcon size={12} />
                      <span>{new Date(session.date).toLocaleDateString('pt-BR')} • {new Date(session.date).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</span>
                    </span>
                    <h3 style={sessionGameTitleStyle}>{game?.name}</h3>
                    <span style={{ ...sessionLocationStyle, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <MapPinIcon size={12} />
                      <span>{session.location}</span>
                    </span>
                  </div>
                  
                  <div style={headerRightStyle}>
                    <span className="badge badge-secondary" style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
                      <ClockIcon size={12} /> {session.duration} min
                    </span>
                    {isAdmin && (
                      <button
                        style={deleteBtnStyle}
                        onClick={(e) => handleDeleteSession(session.id, e)}
                        title="Excluir Registro de Partida"
                      >
                        <TrashIcon size={14} />
                      </button>
                    )}
                  </div>
                </div>

                {/* Notes */}
                <p style={sessionNotesStyle}>
                  "{session.notes.length > 180 ? `${session.notes.substring(0, 180)}...` : session.notes}"
                </p>

                {/* Players & Winner Grid */}
                <div style={playersFooterStyle}>
                  <div style={participantsListStyle}>
                    <strong style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>Jogaram:</strong>
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginTop: '4px' }}>
                      {session.participantIds.map(pid => {
                        const player = state.users.find(u => u.id === pid);
                        return (
                          <span
                            key={pid}
                            className="badge badge-neutral"
                            style={{ fontSize: '0.75rem', fontWeight: 500 }}
                            title={player?.name}
                          >
                            <UserAvatar user={player} size={18} style={{ border: '1px solid var(--color-border)' }} />
                            <span>{player?.name.split(' ')[0]}</span>
                          </span>
                        );
                      })}
                    </div>
                  </div>

                  <div style={winnerBadgeContainerStyle}>
                    <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--color-text-muted)' }}>Vencedor:</span>
                    <span className="badge badge-accent" style={{ ...winnerBadgeStyle, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <CrownIcon size={12} />
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                        <UserAvatar user={winner} size={18} style={{ border: '1px solid var(--color-border)' }} />
                        <span>{winner?.name}</span>
                      </span>
                    </span>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Pagination controls */}
      {totalPages > 1 && (
        <div style={paginationStyle}>
          <button
            className="btn btn-outline btn-sm"
            onClick={goToPrevPage}
            disabled={currentPage === 1}
          >
            ◀ Anterior
          </button>
          <span style={pageNumberStyle}>Página {currentPage} de {totalPages}</span>
          <button
            className="btn btn-outline btn-sm"
            onClick={goToNextPage}
            disabled={currentPage === totalPages}
          >
            Próxima ▶
          </button>
        </div>
      )}

      {/* Record Session Modal */}
      {isRecordModalOpen && (
        <div className="modal-overlay" onClick={() => setIsRecordModalOpen(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()} style={recordModalContentStyle}>
            <h2 className="mb-lg" style={{ fontFamily: 'var(--font-title)' }}>Registrar Nova Partida</h2>
            
            <form onSubmit={handleSubmit}>
              
              {/* Game selection */}
              <div className="form-group">
                <label className="form-label">Jogo Jogado *</label>
                <select
                  className="form-select"
                  required
                  value={gameId}
                  onChange={e => setGameId(e.target.value)}
                >
                  <option value="">Selecione o jogo...</option>
                  {state.boardGames.map(g => (
                    <option key={g.id} value={g.id}>{g.name} ({g.category})</option>
                  ))}
                </select>
              </div>

              {/* Date, Time and Location */}
              <div style={formThreeRowStyle}>
                <div className="form-group">
                  <label className="form-label">Data *</label>
                  <input type="date" className="form-input" required value={date} onChange={e => setDate(e.target.value)} />
                </div>
                <div className="form-group">
                  <label className="form-label">Hora *</label>
                  <input type="time" className="form-input" required value={time} onChange={e => setTime(e.target.value)} />
                </div>
                <div className="form-group">
                  <label className="form-label">Duração (min) *</label>
                  <input type="number" className="form-input" min={5} value={duration} onChange={e => setDuration(Number(e.target.value))} />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Local *</label>
                <input type="text" className="form-input" required value={location} onChange={e => setLocation(e.target.value)} placeholder="Ex: Biblioteca Universitária - Sala 3" />
              </div>

              {/* Participants multi-select checkboxes */}
              <div className="form-group">
                <label className="form-label">Participantes * (selecione os jogadores da mesa)</label>
                <div style={checkboxGridStyle}>
                  {state.users.map(u => {
                    const isSelected = selectedParticipants.includes(u.id);
                    return (
                      <div
                        key={u.id}
                        onClick={() => handleParticipantToggle(u.id)}
                        style={{
                          ...checkboxItemStyle,
                          backgroundColor: isSelected ? 'var(--color-primary-light)' : 'white',
                          borderColor: isSelected ? 'var(--color-primary)' : 'var(--color-border)'
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={isSelected}
                          readOnly
                          style={{ marginRight: '6px', pointerEvents: 'none' }}
                        />
                        <span>{u.avatar} {u.name}</span>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Winner selection from active participants */}
              <div className="form-group">
                <label className="form-label">Vencedor da Partida *</label>
                <select
                  className="form-select"
                  required
                  value={winnerId}
                  onChange={e => setWinnerId(e.target.value)}
                  disabled={selectedParticipants.length === 0}
                >
                  <option value="">
                    {selectedParticipants.length === 0
                      ? 'Selecione os participantes primeiro'
                      : 'Selecione o vencedor...'}
                  </option>
                  {selectedParticipants.map(pid => {
                    const player = state.users.find(u => u.id === pid);
                    return (
                      <option key={pid} value={pid}>
                        {player?.avatar} {player?.name}
                      </option>
                    );
                  })}
                </select>
              </div>

              {/* Notes */}
              <div className="form-group">
                <label className="form-label">Notas e Detalhes da Partida *</label>
                <textarea
                  className="form-textarea"
                  required
                  value={notes}
                  onChange={e => setNotes(e.target.value)}
                  placeholder="Ex: Como foi a partida? Alguma jogada épica? Descreva momentos divertidos!"
                />
              </div>

              {/* Image URL Optional */}
              <div className="form-group">
                <label className="form-label">URL de Foto da Partida (Opcional)</label>
                <input
                  type="text"
                  className="form-input"
                  value={photoUrl}
                  onChange={e => setPhotoUrl(e.target.value)}
                  placeholder="Link para foto tirada no encontro (Unsplash ou servidor de imagens)..."
                />
              </div>

              {/* Comments system initial */}
              <div className="form-group">
                <label className="form-label">Comentário Inicial Pós-Jogo (Opcional)</label>
                <input
                  type="text"
                  className="form-input"
                  value={initialComment}
                  onChange={e => setInitialComment(e.target.value)}
                  placeholder="Ex: Jogaríamos de novo com certeza!"
                />
              </div>

              <div style={formActionsStyle}>
                <button type="button" className="btn btn-outline" onClick={() => setIsRecordModalOpen(false)}>Cancelar</button>
                <button type="submit" className="btn btn-primary">Gravar Partida</button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
};

// Styling structures for Sessions view
const headerSectionStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  marginBottom: '32px',
  borderBottom: '2px solid var(--color-border)',
  paddingBottom: '16px',
  flexWrap: 'wrap',
  gap: '16px',
};

const filtersCardStyle: React.CSSProperties = {
  padding: '16px 20px',
  marginBottom: '28px',
};

const filterFormGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.2fr 1fr 1fr 1.2fr',
  gap: '16px',
};

const searchWrapperStyle: React.CSSProperties = {
  position: 'relative',
  display: 'flex',
  alignItems: 'center',
  backgroundColor: 'white',
  border: '1px solid var(--color-border)',
  borderRadius: '10px',
  paddingLeft: '14px',
};

const searchIconStyle: React.CSSProperties = {
  color: 'var(--color-text-light)',
  position: 'absolute',
};

const searchInputStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px 10px 10px 32px',
  border: 'none',
  outline: 'none',
  fontFamily: 'var(--font-body)',
  fontSize: '0.9rem',
  backgroundColor: 'transparent',
};

const sessionsListContainerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '20px',
};

const sessionCardItemStyle: React.CSSProperties = {
  cursor: 'pointer',
  display: 'flex',
  flexDirection: 'column',
  gap: '14px',
  padding: '20px 24px',
};

const sessionCardHeaderStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'flex-start',
};

const sessionDateBadgeStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  color: 'var(--color-text-light)',
  fontWeight: 600,
};

const sessionGameTitleStyle: React.CSSProperties = {
  fontSize: '1.4rem',
  fontWeight: 700,
  marginTop: '4px',
  color: 'var(--color-text-main)',
};

const sessionLocationStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  color: 'var(--color-text-muted)',
  display: 'inline-block',
  marginTop: '2px',
};

const headerRightStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '10px',
};

const deleteBtnStyle: React.CSSProperties = {
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  color: 'var(--color-text-light)',
  padding: '6px',
  borderRadius: '50%',
  transition: 'all 0.2s',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
};

// Add delete button hover style dynamically
if (typeof document !== 'undefined') {
  const hoverStyle = document.createElement('style');
  hoverStyle.textContent = `
    button[title="Excluir Registro de Partida"]:hover {
      background-color: var(--color-danger-light);
      color: var(--color-danger) !important;
    }
  `;
  document.head.appendChild(hoverStyle);
}

const sessionNotesStyle: React.CSSProperties = {
  fontSize: '0.95rem',
  color: 'var(--color-text-muted)',
  fontStyle: 'italic',
  lineHeight: '1.6',
  paddingLeft: '12px',
  borderLeft: '3px solid var(--color-border)',
};

const playersFooterStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  borderTop: '1px solid var(--color-border)',
  paddingTop: '12px',
  marginTop: '4px',
  flexWrap: 'wrap',
  gap: '12px',
};

const participantsListStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
};

const winnerBadgeContainerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'flex-end',
};

const winnerBadgeStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  fontWeight: 700,
  padding: '6px 14px',
  marginTop: '4px',
  boxShadow: '0 2px 8px rgba(243, 182, 63, 0.25)',
};

const paginationStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'center',
  alignItems: 'center',
  gap: '20px',
  marginTop: '32px',
};

const pageNumberStyle: React.CSSProperties = {
  fontSize: '0.9rem',
  fontWeight: 600,
  color: 'var(--color-text-muted)',
};

// Modal styles
const recordModalContentStyle: React.CSSProperties = {
  maxWidth: '650px',
};

const formThreeRowStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(3, 1fr)',
  gap: '12px',
};

const checkboxGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(2, 1fr)',
  gap: '8px',
  maxHeight: '150px',
  overflowY: 'auto',
  border: '1px solid var(--color-border)',
  padding: '8px',
  borderRadius: '8px',
  backgroundColor: '#FAF9F6',
};

const checkboxItemStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  padding: '8px 12px',
  borderRadius: '6px',
  border: '1px solid',
  cursor: 'pointer',
  transition: 'all 0.15s',
  fontSize: '0.85rem',
  fontWeight: 500,
  userSelect: 'none',
};

const formActionsStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'flex-end',
  gap: '12px',
  marginTop: '24px',
  borderTop: '1px solid var(--color-border)',
  paddingTop: '16px',
};

const responsiveSessionsStyle = `
@media (max-width: 900px) {
  .filters-grid-responsive {
    grid-template-columns: repeat(2, 1fr) !important;
    gap: 12px !important;
  }
}
@media (max-width: 600px) {
  .filters-grid-responsive {
    grid-template-columns: 1fr !important;
  }
  .form-three-responsive {
    grid-template-columns: 1fr !important;
  }
  .checkbox-grid-responsive {
    grid-template-columns: 1fr !important;
  }
  .players-footer-responsive {
    flex-direction: column !important;
    align-items: flex-start !important;
  }
  .winner-badge-responsive {
    align-items: flex-start !important;
  }
}
`;
if (typeof document !== 'undefined') {
  const styleEl = document.createElement('style');
  styleEl.textContent = responsiveSessionsStyle;
  document.head.appendChild(styleEl);
}
export default Sessions;
