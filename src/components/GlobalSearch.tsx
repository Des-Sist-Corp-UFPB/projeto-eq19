import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDatabase } from '../context/DatabaseContext';
import { SearchIcon, CloseIcon, CalendarIcon, TrophyIcon } from './Icons';
import { UserAvatar } from './UserAvatar';

interface GlobalSearchProps {
  isOpen: boolean;
  onClose: () => void;
}

export const GlobalSearch: React.FC<GlobalSearchProps> = ({ isOpen, onClose }) => {
  const [query, setQuery] = useState('');
  const { state } = useDatabase();
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);

  // Focar entrada ao abrir
  useEffect(() => {
    if (!isOpen) return;

    const focusTimer = window.setTimeout(() => {
      inputRef.current?.focus();
      setQuery('');
    }, 100);

    return () => window.clearTimeout(focusTimer);
  }, [isOpen]);

  // Tratar tecla escape
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  if (!isOpen) return null;

  const searchQuery = query.trim().toLowerCase();

  // 1. Buscar Jogos
  const matchingGames = searchQuery
    ? state.boardGames.filter(
        g =>
          g.name.toLowerCase().includes(searchQuery) ||
          g.category.toLowerCase().includes(searchQuery)
      )
    : [];

  // 2. Buscar Jogadores (Usuários)
  const matchingPlayers = searchQuery
    ? state.users.filter(
        u =>
          u.name.toLowerCase().includes(searchQuery) ||
          u.course.toLowerCase().includes(searchQuery)
      )
    : [];

  // 3. Buscar Eventos
  const matchingEvents = searchQuery
    ? state.events.filter(e => {
        const game = state.boardGames.find(g => g.id === e.gameId);
        return (
          e.location.toLowerCase().includes(searchQuery) ||
          e.description.toLowerCase().includes(searchQuery) ||
          (game && game.name.toLowerCase().includes(searchQuery))
        );
      })
    : [];

  // 4. Buscar Sessões
  const matchingSessions = searchQuery
    ? state.sessions.filter(s => {
        const game = state.boardGames.find(g => g.id === s.gameId);
        return (
          s.location.toLowerCase().includes(searchQuery) ||
          s.notes.toLowerCase().includes(searchQuery) ||
          (game && game.name.toLowerCase().includes(searchQuery))
        );
      })
    : [];

  const hasResults =
    matchingGames.length > 0 ||
    matchingPlayers.length > 0 ||
    matchingEvents.length > 0 ||
    matchingSessions.length > 0;

  const handleResultClick = (path: string) => {
    navigate(path);
    onClose();
  };

  return (
    <div className="modal-overlay" onClick={onClose} style={{ zIndex: 10001 }}>
      <div className="modal-content" onClick={e => e.stopPropagation()} style={searchModalStyle}>
        <div style={searchHeaderStyle}>
          <SearchIcon size={22} style={{ color: 'var(--color-primary)' }} />
          <input
            ref={inputRef}
            type="text"
            placeholder="Pesquisar jogos, jogadores, eventos ou sessões..."
            value={query}
            onChange={e => setQuery(e.target.value)}
            style={searchInputStyle}
          />
          <button onClick={onClose} style={closeButtonStyle}>
            <CloseIcon size={20} />
          </button>
        </div>

        <div style={resultsContainerStyle}>
          {searchQuery && !hasResults && (
            <div style={noResultsStyle}>
              <p>Nenhum resultado encontrado para "{query}"</p>
              <span style={{ fontSize: '0.85rem', color: 'var(--color-text-light)' }}>
                Tente buscar por termos diferentes como "Catan", "Computação", "Vivência" ou o nome de um jogador.
              </span>
            </div>
          )}

          {!searchQuery && (
            <div style={noResultsStyle}>
              <p style={{ color: 'var(--color-text-muted)' }}>Digite algo para começar a buscar...</p>
              <div style={shortcutsContainerStyle}>
                <span style={shortcutBadgeStyle}>Dica: Procure por jogos como "Dixit" ou cursos como "Design"</span>
              </div>
            </div>
          )}

          {searchQuery && hasResults && (
            <div style={scrollResultsStyle}>
              {/* Jogos */}
              {matchingGames.length > 0 && (
                <div style={sectionStyle}>
                  <h4 style={sectionHeaderStyle}>Jogos ({matchingGames.length})</h4>
                  {matchingGames.map(g => (
                    <div
                      key={g.id}
                      onClick={() => handleResultClick(`/games/${g.id}`)}
                      style={resultItemStyle}
                    >
                      <img src={g.coverUrl} alt={g.name} style={gameThumbStyle} />
                      <div>
                        <div style={resultTitleStyle}>{g.name}</div>
                        <div style={resultSubStyle}>{g.category} • {g.minPlayers}-{g.maxPlayers} Jogadores</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Jogadores */}
              {matchingPlayers.length > 0 && (
                <div style={sectionStyle}>
                  <h4 style={sectionHeaderStyle}>Jogadores ({matchingPlayers.length})</h4>
                  {matchingPlayers.map(u => (
                    <div
                      key={u.id}
                      onClick={() => handleResultClick(`/players/${u.id}`)}
                      style={resultItemStyle}
                    >
                      <UserAvatar user={u} size={40} style={avatarStyle} />
                      <div>
                        <div style={resultTitleStyle}>{u.name}</div>
                        <div style={resultSubStyle}>{u.course} • {u.winCount} vitórias</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Eventos */}
              {matchingEvents.length > 0 && (
                <div style={sectionStyle}>
                  <h4 style={sectionHeaderStyle}>Próximos Eventos ({matchingEvents.length})</h4>
                  {matchingEvents.map(e => {
                    const game = state.boardGames.find(g => g.id === e.gameId);
                    return (
                      <div
                        key={e.id}
                        onClick={() => handleResultClick('/events')}
                        style={resultItemStyle}
                      >
                        <span style={eventIconStyle}>
                          <CalendarIcon size={18} style={{ color: 'var(--color-secondary)' }} />
                        </span>
                        <div>
                          <div style={resultTitleStyle}>{game?.name} no {e.location}</div>
                          <div style={resultSubStyle}>
                            Agendado para {new Date(e.date + 'T00:00:00').toLocaleDateString('pt-BR')} às {e.time} ({e.participantIds.length}/{e.maxParticipants} vagas)
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Sessões */}
              {matchingSessions.length > 0 && (
                <div style={sectionStyle}>
                  <h4 style={sectionHeaderStyle}>Partidas Recentes ({matchingSessions.length})</h4>
                  {matchingSessions.map(s => {
                    const game = state.boardGames.find(g => g.id === s.gameId);
                    return (
                      <div
                        key={s.id}
                        onClick={() => handleResultClick(`/sessions/${s.id}`)}
                        style={resultItemStyle}
                      >
                        <span style={sessionIconStyle}>
                          <TrophyIcon size={18} style={{ color: 'var(--color-accent)' }} />
                        </span>
                        <div>
                          <div style={resultTitleStyle}>Partida de {game?.name}</div>
                          <div style={resultSubStyle}>
                            Jogada em {new Date(s.date).toLocaleDateString('pt-BR')} em {s.location} • {s.notes.substring(0, 75)}...
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

// Inline Styles for Global Search to keep layout precise and gorgeous
const searchModalStyle: React.CSSProperties = {
  maxWidth: '650px',
  padding: '0',
  borderRadius: '16px',
  overflow: 'hidden',
  display: 'flex',
  flexDirection: 'column',
  maxHeight: '80vh',
};

const searchHeaderStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  padding: '16px 20px',
  borderBottom: '1px solid var(--color-border)',
  gap: '12px',
};

const searchInputStyle: React.CSSProperties = {
  flexGrow: 1,
  border: 'none',
  outline: 'none',
  fontSize: '1.1rem',
  color: 'var(--color-text-main)',
  fontFamily: 'var(--font-body)',
};

const closeButtonStyle: React.CSSProperties = {
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  color: 'var(--color-text-light)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: '4px',
  borderRadius: '50%',
  transition: 'background-color 0.2s',
};

const resultsContainerStyle: React.CSSProperties = {
  padding: '16px 20px 24px 20px',
  backgroundColor: '#FCFBFA',
  overflowY: 'auto',
};

const scrollResultsStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '20px',
};

const noResultsStyle: React.CSSProperties = {
  textAlign: 'center',
  padding: '32px 16px',
  fontSize: '0.95rem',
  fontWeight: 500,
  color: 'var(--color-text-muted)',
  display: 'flex',
  flexDirection: 'column',
  gap: '8px',
};

const shortcutsContainerStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'center',
  marginTop: '12px',
};

const shortcutBadgeStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  backgroundColor: 'var(--color-secondary-light)',
  color: 'var(--color-secondary)',
  padding: '6px 12px',
  borderRadius: '20px',
  fontWeight: 600,
};

const sectionStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '8px',
};

const sectionHeaderStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
  color: 'var(--color-text-light)',
  borderBottom: '1px solid #ECEBE6',
  paddingBottom: '4px',
  marginBottom: '4px',
};

const resultItemStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '12px',
  padding: '8px 12px',
  borderRadius: '8px',
  cursor: 'pointer',
  transition: 'all 0.2s',
  backgroundColor: 'white',
  border: '1px solid #EBE9E4',
};

const gameThumbStyle: React.CSSProperties = {
  width: '40px',
  height: '40px',
  borderRadius: '6px',
  objectFit: 'cover',
  backgroundColor: '#eee',
};

const avatarStyle: React.CSSProperties = {
  fontSize: '1.8rem',
  width: '40px',
  height: '40px',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  backgroundColor: 'var(--color-primary-light)',
  borderRadius: '50%',
};

const eventIconStyle: React.CSSProperties = {
  fontSize: '1.4rem',
  width: '40px',
  height: '40px',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  backgroundColor: 'var(--color-secondary-light)',
  borderRadius: '8px',
};

const sessionIconStyle: React.CSSProperties = {
  fontSize: '1.4rem',
  width: '40px',
  height: '40px',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  backgroundColor: 'var(--color-accent-light)',
  borderRadius: '8px',
};

const resultTitleStyle: React.CSSProperties = {
  fontWeight: 600,
  fontSize: '0.95rem',
  color: 'var(--color-text-main)',
};

const resultSubStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  color: 'var(--color-text-muted)',
};
