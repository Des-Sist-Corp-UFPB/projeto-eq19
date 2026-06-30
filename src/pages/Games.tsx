import React, { useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useDatabase } from '../context/DatabaseContext';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import type { BoardGame } from '../types';
import { PlusIcon, SearchIcon, EditIcon, TrashIcon, StarIcon, DiceIcon, UsersIcon, ClockIcon, PuzzleIcon, CrownIcon } from '../components/Icons';

export const Games: React.FC = () => {
  const { state, addGame, editGame, deleteGame } = useDatabase();
  const { isAdmin } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  // Search, filter, sorting state
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('all');
  
  // Modals state
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editingGame, setEditingGame] = useState<BoardGame | null>(null);

  // Form states
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('Estratégia');
  const [minPlayers, setMinPlayers] = useState(2);
  const [maxPlayers, setMaxPlayers] = useState(4);
  const [avgPlayTime, setAvgPlayTime] = useState(60);
  const [complexity, setComplexity] = useState(2);

  const [coverUrl, setCoverUrl] = useState('');

  const selectedGame = useMemo(() => {
    const gameId = searchParams.get('id');
    return gameId ? state.boardGames.find(g => g.id === gameId) ?? null : null;
  }, [searchParams, state.boardGames]);

  // List of all unique categories
  const categories = ['all', ...Array.from(new Set(state.boardGames.map(g => g.category)))];

  // Filtering logic
  const filteredGames = state.boardGames.filter(g => {
    const matchesSearch = g.name.toLowerCase().includes(search.toLowerCase()) || 
                          g.description.toLowerCase().includes(search.toLowerCase());
    const matchesCategory = categoryFilter === 'all' || g.category === categoryFilter;
    return matchesSearch && matchesCategory;
  });

  // Add game submit
  const handleAddSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    const mockCover = coverUrl.trim() || '/images/tabletop-placeholder.svg';

    addGame({
      name,
      description,
      category,
      minPlayers,
      maxPlayers,
      avgPlayTime,
      complexity,
      coverUrl: mockCover
    });

    showToast(`Jogo "${name}" adicionado com sucesso!`, 'success');
    resetForm();
    setIsAddModalOpen(false);
  };

  // Open edit modal
  const handleOpenEdit = (game: BoardGame, e: React.MouseEvent) => {
    e.stopPropagation(); // Stop card click details opening
    setEditingGame(game);
    setName(game.name);
    setDescription(game.description);
    setCategory(game.category);
    setMinPlayers(game.minPlayers);
    setMaxPlayers(game.maxPlayers);
    setAvgPlayTime(game.avgPlayTime);
    setComplexity(game.complexity);
    setCoverUrl(game.coverUrl);
    setIsEditModalOpen(true);
  };

  // Submit edit
  const handleEditSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingGame || !name.trim()) return;

    editGame({
      ...editingGame,
      name,
      description,
      category,
      minPlayers,
      maxPlayers,
      avgPlayTime,
      complexity,
      coverUrl
    });

    showToast(`Jogo "${name}" atualizado!`, 'success');
    resetForm();
    setIsEditModalOpen(false);
  };

  // Delete handler
  const handleDelete = (gameId: string, name: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (window.confirm(`Tem certeza que deseja remover "${name}" do acervo?`)) {
      deleteGame(gameId);
      showToast(`Jogo "${name}" removido.`, 'info');
      if (selectedGame?.id === gameId) {
        navigate('/games');
      }
    }
  };

  const resetForm = () => {
    setName('');
    setDescription('');
    setCategory('Estratégia');
    setMinPlayers(2);
    setMaxPlayers(4);
    setAvgPlayTime(60);
    setComplexity(2);
    setCoverUrl('');
    setEditingGame(null);
  };

  const getComplexityLabel = (val: number) => {
    if (val < 1.8) return 'Fácil';
    if (val < 2.8) return 'Médio';
    return 'Complexo';
  };

  return (
    <div className="container" style={{ paddingTop: '24px' }}>
      
      {/* Page Header */}
      <div style={headerSectionStyle}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <DiceIcon size={32} style={{ color: 'var(--color-primary)' }} />
          <div>
            <h1 style={{ fontSize: '2rem', marginBottom: '4px' }}>Acervo de Jogos de Mesa</h1>
            <p style={{ color: 'var(--color-text-muted)' }}>Explore os jogos disponíveis para jogar em nossos encontros.</p>
          </div>
        </div>
        {isAdmin && (
          <button className="btn btn-primary" onClick={() => { resetForm(); setIsAddModalOpen(true); }}>
            <PlusIcon size={18} /> Adicionar Novo Jogo
          </button>
        )}
      </div>

      {/* Filter and Search Bar */}
      <div style={filterBarContainerStyle}>
        {/* Search */}
        <div style={searchWrapperStyle}>
          <SearchIcon size={18} style={searchIconStyle} />
          <input
            type="text"
            placeholder="Pesquisar por nome ou descrição..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={searchInputStyle}
          />
        </div>

        {/* Category Tags */}
        <div style={tagFiltersContainerStyle}>
          {categories.map(cat => (
            <button
              key={cat}
              onClick={() => setCategoryFilter(cat)}
              style={{
                ...categoryTagButtonStyle,
                backgroundColor: categoryFilter === cat ? 'var(--color-primary)' : 'white',
                color: categoryFilter === cat ? 'white' : 'var(--color-text-main)',
                borderColor: categoryFilter === cat ? 'var(--color-primary)' : 'var(--color-border)'
              }}
            >
              {cat === 'all' ? 'Todos' : cat}
            </button>
          ))}
        </div>
      </div>

      {/* Catalog Grid */}
      {filteredGames.length === 0 ? (
        <div className="card text-center" style={{ padding: '64px 32px', marginTop: '24px' }}>
          <p style={{ color: 'var(--color-text-muted)', fontSize: '1.1rem', fontWeight: 500 }}>Nenhum jogo encontrado com os filtros atuais.</p>
          <button className="btn btn-outline mt-md" onClick={() => { setSearch(''); setCategoryFilter('all'); }}>
            Limpar Filtros
          </button>
        </div>
      ) : (
        <div style={gamesGridStyle}>
          {filteredGames.map(game => (
            <div
              key={game.id}
              className="card card-hoverable"
              style={gameCardStyle}
              onClick={() => navigate(`/games?id=${game.id}`)}
            >
              <div style={coverWrapperStyle}>
                <img src={game.coverUrl} alt={game.name} style={coverImgStyle} />
                <span className="badge badge-primary" style={categoryBadgeStyle}>
                  {game.category}
                </span>
              </div>
              
              <div style={gameCardContentStyle}>
                <h3 style={gameCardTitleStyle}>{game.name}</h3>
                <p style={gameCardDescStyle}>
                  {game.description.substring(0, 100)}...
                </p>

                <div style={gameMetaGridStyle}>
                  <div style={metaItemStyle}>
                    <UsersIcon size={14} style={{ color: 'var(--color-primary)' }} /> 
                    <span>{game.minPlayers}-{game.maxPlayers} Jogadores</span>
                  </div>
                  <div style={metaItemStyle}>
                    <ClockIcon size={14} style={{ color: 'var(--color-primary)' }} /> 
                    <span>{game.avgPlayTime} min</span>
                  </div>
                  <div style={metaItemStyle}>
                    <PuzzleIcon size={14} style={{ color: 'var(--color-primary)' }} /> 
                    <span>Peso: {game.complexity} ({getComplexityLabel(game.complexity)})</span>
                  </div>
                </div>



                <div style={adminActionsWrapperStyle}>
                  <Link to={`/games/${game.id}`} className="btn btn-outline btn-sm" style={detailBtnStyle} onClick={e => e.stopPropagation()}>
                    Ver detalhes
                  </Link>
                  {isAdmin && (
                    <>
                    <button
                      className="btn btn-outline btn-sm"
                      style={editBtnStyle}
                      onClick={(e) => handleOpenEdit(game, e)}
                    >
                      <EditIcon size={14} /> Editar
                    </button>
                    <button
                      className="btn btn-outline btn-sm"
                      style={deleteBtnStyle}
                      onClick={(e) => handleDelete(game.id, game.name, e)}
                    >
                      <TrashIcon size={14} /> Excluir
                    </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Game Details Modal */}
      {selectedGame && (
        <div className="modal-overlay" onClick={() => navigate('/games')}>
          <div className="modal-content" onClick={e => e.stopPropagation()} style={detailsModalStyle}>
            <button className="modal-close" onClick={() => navigate('/games')}>×</button>
            
            <div style={modalHeaderStyle}>
              <img src={selectedGame.coverUrl} alt={selectedGame.name} style={modalCoverStyle} />
              <div>
                <span className="badge badge-primary" style={{ marginBottom: '8px' }}>{selectedGame.category}</span>
                <h2 style={{ fontSize: '1.8rem', fontFamily: 'var(--font-title)' }}>{selectedGame.name}</h2>
                <div style={starsContainerStyle}>
                  <span style={{ fontSize: '0.9rem', fontWeight: 600 }}>Dificuldade: {selectedGame.complexity} / 5</span>
                  <div style={{ display: 'flex', gap: '2px' }}>
                    {Array.from({ length: 5 }).map((_, idx) => (
                      <StarIcon
                        key={idx}
                        size={14}
                        fill={idx < Math.round(selectedGame.complexity) ? 'var(--color-accent)' : 'none'}
                        style={{ color: 'var(--color-accent)' }}
                      />
                    ))}
                  </div>
                </div>
              </div>
            </div>

            <div style={{ marginTop: '24px' }}>
              <h4 style={subTitleStyle}>Descrição</h4>
              <p style={descTextStyle}>{selectedGame.description}</p>
            </div>

            <div style={modalMetaGridStyle}>
              <div style={modalMetaItemStyle}>
                <span style={{ ...modalMetaLabelStyle, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <UsersIcon size={14} />
                  <span>Jogadores</span>
                </span>
                <span style={modalMetaValueStyle}>{selectedGame.minPlayers} a {selectedGame.maxPlayers}</span>
              </div>
              <div style={modalMetaItemStyle}>
                <span style={{ ...modalMetaLabelStyle, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <ClockIcon size={14} />
                  <span>Duração</span>
                </span>
                <span style={modalMetaValueStyle}>{selectedGame.avgPlayTime} min</span>
              </div>
              <div style={modalMetaItemStyle}>
                <span style={{ ...modalMetaLabelStyle, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <PuzzleIcon size={14} />
                  <span>Dificuldade</span>
                </span>
                <span style={modalMetaValueStyle}>{getComplexityLabel(selectedGame.complexity)}</span>
              </div>
            </div>



            {/* Recents plays of this game */}
            <div style={{ marginTop: '28px', borderTop: '1px solid var(--color-border)', paddingTop: '20px' }}>
              <h4 style={subTitleStyle}>Partidas Registradas deste Jogo</h4>
              {state.sessions.filter(s => s.gameId === selectedGame.id).length === 0 ? (
                <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem' }}>Nenhuma partida deste jogo foi registrada no histórico ainda.</p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '10px' }}>
                  {state.sessions
                    .filter(s => s.gameId === selectedGame.id)
                    .map(s => {
                      const winner = state.users.find(u => u.id === s.winnerId);
                      return (
                        <div key={s.id} style={playHistoryRowStyle}>
                          <span>📅 {new Date(s.date).toLocaleDateString('pt-BR')} em {s.location}</span>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                            <CrownIcon size={14} style={{ color: 'var(--color-accent)' }} />
                            <span>Vencedor:</span>
                            <strong>{winner?.avatar} {winner?.name.split(' ')[0]}</strong>
                          </span>
                        </div>
                      );
                    })}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Add Game Modal */}
      {isAddModalOpen && (
        <div className="modal-overlay" onClick={() => setIsAddModalOpen(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2 className="mb-lg" style={{ fontFamily: 'var(--font-title)' }}>Adicionar Novo Jogo ao Acervo</h2>
            <form onSubmit={handleAddSubmit}>
              <div className="form-group">
                <label className="form-label">Nome do Jogo *</label>
                <input type="text" className="form-input" required value={name} onChange={e => setName(e.target.value)} placeholder="Ex: Catan, Dixit..." />
              </div>
              <div className="form-group">
                <label className="form-label">Descrição *</label>
                <textarea className="form-textarea" required value={description} onChange={e => setDescription(e.target.value)} placeholder="Descreva brevemente a mecânica e objetivo do jogo..." />
              </div>
              
              <div style={formRowGridStyle}>
                <div className="form-group">
                  <label className="form-label">Categoria</label>
                  <select className="form-select" value={category} onChange={e => setCategory(e.target.value)}>
                    <option value="Estratégia">Estratégia</option>
                    <option value="Estratégia Avançada">Estratégia Avançada</option>
                    <option value="Família">Família</option>
                    <option value="Party Game">Party Game</option>
                    <option value="Cooperativo">Cooperativo</option>
                    <option value="Cartas">Cartas</option>
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">URL da Imagem de Capa</label>
                  <input type="text" className="form-input" value={coverUrl} onChange={e => setCoverUrl(e.target.value)} placeholder="Link público da imagem..." />
                </div>
              </div>

              <div style={formStatsGridStyle}>
                <div className="form-group">
                  <label className="form-label">Mín. Jogadores</label>
                  <input type="number" className="form-input" min={1} max={50} value={minPlayers} onChange={e => setMinPlayers(Number(e.target.value))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Máx. Jogadores</label>
                  <input type="number" className="form-input" min={1} max={50} value={maxPlayers} onChange={e => setMaxPlayers(Number(e.target.value))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Tempo Médio (min)</label>
                  <input type="number" className="form-input" min={5} max={600} value={avgPlayTime} onChange={e => setAvgPlayTime(Number(e.target.value))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Dificuldade (1-5)</label>
                  <input type="number" step="0.1" className="form-input" min={1} max={5} value={complexity} onChange={e => setComplexity(Number(e.target.value))} />
                </div>
              </div>



              <div style={formActionsStyle}>
                <button type="button" className="btn btn-outline" onClick={() => setIsAddModalOpen(false)}>Cancelar</button>
                <button type="submit" className="btn btn-primary">Adicionar Jogo</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Game Modal */}
      {isEditModalOpen && (
        <div className="modal-overlay" onClick={() => setIsEditModalOpen(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2 className="mb-lg" style={{ fontFamily: 'var(--font-title)' }}>Editar Jogo: {editingGame?.name}</h2>
            <form onSubmit={handleEditSubmit}>
              <div className="form-group">
                <label className="form-label">Nome do Jogo *</label>
                <input type="text" className="form-input" required value={name} onChange={e => setName(e.target.value)} />
              </div>
              <div className="form-group">
                <label className="form-label">Descrição *</label>
                <textarea className="form-textarea" required value={description} onChange={e => setDescription(e.target.value)} />
              </div>
              
              <div style={formRowGridStyle}>
                <div className="form-group">
                  <label className="form-label">Categoria</label>
                  <select className="form-select" value={category} onChange={e => setCategory(e.target.value)}>
                    <option value="Estratégia">Estratégia</option>
                    <option value="Estratégia Avançada">Estratégia Avançada</option>
                    <option value="Família">Família</option>
                    <option value="Party Game">Party Game</option>
                    <option value="Cooperativo">Cooperativo</option>
                    <option value="Cartas">Cartas</option>
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">URL da Imagem de Capa</label>
                  <input type="text" className="form-input" value={coverUrl} onChange={e => setCoverUrl(e.target.value)} />
                </div>
              </div>

              <div style={formStatsGridStyle}>
                <div className="form-group">
                  <label className="form-label">Mín. Jogadores</label>
                  <input type="number" className="form-input" min={1} max={50} value={minPlayers} onChange={e => setMinPlayers(Number(e.target.value))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Máx. Jogadores</label>
                  <input type="number" className="form-input" min={1} max={50} value={maxPlayers} onChange={e => setMaxPlayers(Number(e.target.value))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Tempo Médio (min)</label>
                  <input type="number" className="form-input" min={5} max={600} value={avgPlayTime} onChange={e => setAvgPlayTime(Number(e.target.value))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Dificuldade (1-5)</label>
                  <input type="number" step="0.1" className="form-input" min={1} max={5} value={complexity} onChange={e => setComplexity(Number(e.target.value))} />
                </div>
              </div>



              <div style={formActionsStyle}>
                <button type="button" className="btn btn-outline" onClick={() => setIsEditModalOpen(false)}>Cancelar</button>
                <button type="submit" className="btn btn-primary">Salvar Alterações</button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
};

// Styling definitions for Games catalog
const headerSectionStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  marginBottom: '32px',
  borderBottom: '2px solid var(--color-border)',
  paddingBottom: '16px',
  flexWrap: 'wrap',
  gap: '16px'
};

const filterBarContainerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '16px',
  marginBottom: '24px',
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
  padding: '12px 12px 12px 36px',
  border: 'none',
  outline: 'none',
  fontFamily: 'var(--font-body)',
  fontSize: '0.95rem',
  backgroundColor: 'transparent',
};

const tagFiltersContainerStyle: React.CSSProperties = {
  display: 'flex',
  gap: '8px',
  flexWrap: 'wrap',
};

const categoryTagButtonStyle: React.CSSProperties = {
  padding: '6px 14px',
  fontSize: '0.85rem',
  fontWeight: 600,
  borderRadius: '20px',
  border: '1px solid',
  cursor: 'pointer',
  transition: 'all 0.2s',
  fontFamily: 'var(--font-title)',
};

const gamesGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(3, 1fr)',
  gap: '24px',
};

const gameCardStyle: React.CSSProperties = {
  cursor: 'pointer',
  padding: '0',
  display: 'flex',
  flexDirection: 'column',
  height: '100%',
};

const coverWrapperStyle: React.CSSProperties = {
  position: 'relative',
  width: '100%',
  height: '200px',
  overflow: 'hidden',
  backgroundColor: '#eee',
};

const coverImgStyle: React.CSSProperties = {
  width: '100%',
  height: '100%',
  objectFit: 'cover',
  transition: 'transform 0.4s ease',
};

const categoryBadgeStyle: React.CSSProperties = {
  position: 'absolute',
  top: '12px',
  left: '12px',
  boxShadow: '0 2px 6px rgba(0,0,0,0.1)',
};

const gameCardContentStyle: React.CSSProperties = {
  padding: '16px',
  display: 'flex',
  flexDirection: 'column',
  flexGrow: 1,
};

const gameCardTitleStyle: React.CSSProperties = {
  fontSize: '1.25rem',
  fontWeight: 700,
  marginBottom: '6px',
};

const gameCardDescStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  color: 'var(--color-text-muted)',
  marginBottom: '16px',
  lineHeight: '1.5',
};

const gameMetaGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1fr',
  gap: '6px',
  fontSize: '0.8rem',
  color: 'var(--color-text-muted)',
  backgroundColor: '#FAF9F6',
  padding: '10px',
  borderRadius: '8px',
  border: '1px solid var(--color-border)',
  marginTop: 'auto', // Push to bottom of content
};

const metaItemStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '6px',
};

const adminActionsWrapperStyle: React.CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  gap: '8px',
  marginTop: '16px',
  borderTop: '1px dashed var(--color-border)',
  paddingTop: '12px',
};

const detailBtnStyle: React.CSSProperties = {
  flex: '1 1 140px',
  justifyContent: 'center',
  fontSize: '0.75rem',
  padding: '4px 8px',
};

const editBtnStyle: React.CSSProperties = {
  flexGrow: 1,
  fontSize: '0.75rem',
  padding: '4px 8px',
  borderColor: 'var(--color-secondary)',
  color: 'var(--color-secondary)',
};

const deleteBtnStyle: React.CSSProperties = {
  flexGrow: 1,
  fontSize: '0.75rem',
  padding: '4px 8px',
  borderColor: 'var(--color-danger)',
  color: 'var(--color-danger)',
};

// Modal styles
const detailsModalStyle: React.CSSProperties = {
  maxWidth: '600px',
};

const modalHeaderStyle: React.CSSProperties = {
  display: 'flex',
  gap: '20px',
  alignItems: 'center',
  borderBottom: '1px solid var(--color-border)',
  paddingBottom: '20px',
};

const modalCoverStyle: React.CSSProperties = {
  width: '120px',
  height: '120px',
  borderRadius: '12px',
  objectFit: 'cover',
  boxShadow: 'var(--shadow-md)',
};

const starsContainerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '4px',
  marginTop: '6px',
};

const subTitleStyle: React.CSSProperties = {
  fontSize: '0.95rem',
  fontWeight: 700,
  marginBottom: '8px',
  color: 'var(--color-text-main)',
  fontFamily: 'var(--font-title)',
};

const descTextStyle: React.CSSProperties = {
  fontSize: '0.9rem',
  color: 'var(--color-text-muted)',
  lineHeight: '1.6',
};

const modalMetaGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(3, 1fr)',
  gap: '12px',
  marginTop: '20px',
};

const modalMetaItemStyle: React.CSSProperties = {
  backgroundColor: 'var(--color-secondary-light)',
  padding: '12px',
  borderRadius: '10px',
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  textAlign: 'center',
  border: '1px solid rgba(42, 111, 96, 0.1)',
};

const modalMetaLabelStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  color: 'var(--color-secondary)',
  fontWeight: 600,
  marginBottom: '4px',
};

const modalMetaValueStyle: React.CSSProperties = {
  fontSize: '0.9rem',
  fontWeight: 700,
  color: 'var(--color-text-main)',
};

const playHistoryRowStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  fontSize: '0.8rem',
  padding: '8px 12px',
  backgroundColor: '#FAF9F6',
  borderRadius: '6px',
  border: '1px solid var(--color-border)',
};

// Form layouts
const formRowGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1fr 2fr',
  gap: '16px',
};

const formStatsGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(4, 1fr)',
  gap: '12px',
};

const formActionsStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'flex-end',
  gap: '12px',
  marginTop: '24px',
  borderTop: '1px solid var(--color-border)',
  paddingTop: '16px',
};

// Inject CSS response for grid
const responsiveGamesStyle = `
@media (max-width: 900px) {
  .games-grid-responsive {
    grid-template-columns: repeat(2, 1fr) !important;
  }
}
@media (max-width: 600px) {
  .games-grid-responsive {
    grid-template-columns: 1fr !important;
  }
  .form-row-responsive {
    grid-template-columns: 1fr !important;
  }
  .form-stats-responsive {
    grid-template-columns: repeat(2, 1fr) !important;
  }
}
`;
if (typeof document !== 'undefined') {
  const styleEl = document.createElement('style');
  styleEl.textContent = responsiveGamesStyle;
  document.head.appendChild(styleEl);
}
export default Games;
