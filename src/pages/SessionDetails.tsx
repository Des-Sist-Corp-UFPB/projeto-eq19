import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useDatabase } from '../context/DatabaseContext';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { MapPinIcon, CrownIcon, MessageSquareIcon, InstagramIcon, UsersIcon } from '../components/Icons';
import { UserAvatar } from '../components/UserAvatar';
import { ApiError } from '../services/api';
import type { Session } from '../types';

type LoadState =
  | { status: 'loading'; id?: string }
  | { status: 'success'; id: string; session: Session }
  | { status: 'not-found'; id?: string }
  | { status: 'error'; id: string; message: string };

export const SessionDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { state, getSessionById, addComment, deleteComment } = useDatabase();
  const { currentUser } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [commentText, setCommentText] = useState('');
  const [loadState, setLoadState] = useState<LoadState>({ status: 'loading', id });

  useEffect(() => {
    let cancelled = false;

    if (!id) {
      return () => { cancelled = true; };
    }

    void getSessionById(id)
      .then(session => {
        if (!cancelled) setLoadState({ status: 'success', id, session });
      })
      .catch(error => {
        if (cancelled) return;
        if (error instanceof ApiError && error.status === 404) {
          setLoadState({ status: 'not-found', id });
          return;
        }
        if (error instanceof ApiError && error.status === 401) {
          setLoadState({ status: 'error', id, message: 'Sua sessão expirou. Entre novamente.' });
          return;
        }
        if (error instanceof ApiError && error.status === 403) {
          setLoadState({ status: 'error', id, message: 'Você não tem permissão para visualizar esta partida.' });
          return;
        }
        setLoadState({ status: 'error', id, message: 'Não foi possível carregar a partida. Tente novamente.' });
      });

    return () => { cancelled = true; };
  }, [getSessionById, id]);

  if (!id) {
    return (
      <div className="container text-center" style={{ padding: '64px' }}>
        <h2>Partida não encontrada</h2>
      </div>
    );
  }

  if (loadState.status === 'loading' || loadState.id !== id) {
    return <div className="container text-center" style={{ padding: '64px' }}>Carregando partida...</div>;
  }

  if (loadState.status === 'not-found') {
    return (
      <div className="container text-center" style={{ padding: '64px' }}>
        <h2>Partida não encontrada</h2>
        <p style={{ color: 'var(--color-text-muted)', marginTop: '8px' }}>O registro que você procura pode ter sido removido ou não existe.</p>
        <button className="btn btn-primary mt-lg" onClick={() => navigate('/sessions')}>Voltar ao Histórico</button>
      </div>
    );
  }

  if (loadState.status === 'error') {
    return (
      <div className="container text-center" style={{ padding: '64px' }} role="alert">
        <h2>Não foi possível carregar a partida</h2>
        <p style={{ color: 'var(--color-text-muted)', marginTop: '8px' }}>{loadState.message}</p>
        <button className="btn btn-primary mt-lg" onClick={() => navigate('/sessions')}>Voltar ao Histórico</button>
      </div>
    );
  }

  const session = state.sessions.find(candidate => candidate.id === loadState.session.id) ?? loadState.session;

  const game = state.boardGames.find(g => g.id === session.gameId);
  const organizer = state.users.find(u => u.id === session.organizerId);
  const winner = state.users.find(u => u.id === session.winnerId);

  const handleCommentSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentUser) {
      showToast('Faça login simulado para comentar.', 'error');
      return;
    }
    if (!commentText.trim()) return;

    try {
      await addComment(session.id, commentText.trim());
      showToast('Comentário publicado!', 'success');
      setCommentText('');
    } catch {
      showToast('Não foi possível publicar o comentário.', 'error');
    }
  };

  const handleCommentDelete = async (commentId: string) => {
    try {
      await deleteComment(session.id, commentId);
      showToast('Comentário excluído.', 'success');
    } catch (error) {
      showToast(error instanceof ApiError && error.status === 403
        ? 'Você não pode excluir este comentário.'
        : 'Não foi possível excluir o comentário.', 'error');
    }
  };

  return (
    <div className="container" style={{ paddingTop: '24px' }}>
      
      {/* Back button */}
      <button
        onClick={() => navigate('/sessions')}
        style={backButtonStyle}
        className="no-print"
      >
        <span>←</span> Voltar ao Histórico
      </button>

      {/* Main card grid layout */}
      <div style={detailsGridStyle}>
        
        {/* Left Side: Session Summary Card */}
        <div style={leftColStyle}>
          
          <div className="card" style={{ padding: '32px' }}>
            <span style={dateLabelStyle}>
              PARTIDA JOGADA EM {new Date(session.date).toLocaleDateString('pt-BR')} às {new Date(session.date).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
            </span>
            <h1 style={titleStyle}>{game?.name}</h1>
            <span style={{ ...locationBadgeStyle, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
              <MapPinIcon size={14} style={{ color: 'var(--color-primary)' }} />
              <span>{session.location}</span>
            </span>

            {/* Game Info Details Card */}
            <div style={gameStripStyle} onClick={() => navigate(`/games?id=${game?.id}`)}>
              <img src={game?.coverUrl} alt={game?.name} style={gameStripImgStyle} />
              <div>
                <h4 style={gameStripTitleStyle}>{game?.name}</h4>
                <p style={gameStripDescStyle}>{game?.category} • {game?.minPlayers}-{game?.maxPlayers} Jogadores • {game?.avgPlayTime} min</p>
              </div>
            </div>

            {/* Winner Spotlight Section */}
            <div style={winnerSpotlightCardStyle}>
              <div style={winnerSpotlightHeaderStyle}>
                <CrownIcon size={32} style={{ color: '#F3B63F' }} />
                <div>
                  <span style={winnerLabelStyle}>Vencedor da Partida</span>
                  <h3 style={winnerNameStyle}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
                      <UserAvatar user={winner} size={28} style={{ border: '1px solid rgba(255,255,255,0.7)' }} />
                      <span>{winner?.name}</span>
                    </span>
                  </h3>
                </div>
              </div>
              <p style={{ fontSize: '0.8rem', color: 'var(--color-secondary)', marginTop: '8px', fontWeight: 500 }}>
                Curso: {winner?.course}
              </p>
            </div>

            {/* Match notes */}
            <div style={sectionWrapperStyle}>
              <h3 style={{ ...sectionTitleStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>
                <MessageSquareIcon size={16} style={{ color: 'var(--color-primary)' }} />
                <span>Relato da Mesa</span>
              </h3>
              <p style={notesContentStyle}>"{session.notes}"</p>
            </div>

            {/* Photo reference if any */}
            {session.photos && session.photos.length > 0 && (
              <div style={sectionWrapperStyle}>
                <h3 style={{ ...sectionTitleStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <InstagramIcon size={16} style={{ color: 'var(--color-primary)' }} />
                  <span>Registro Visual</span>
                </h3>
                <div style={photoGalleryStyle}>
                  {session.photos.map((pUrl, idx) => (
                    <img key={idx} src={pUrl} alt={`Foto da partida de ${game?.name}`} style={photoStyle} />
                  ))}
                </div>
              </div>
            )}

            {/* Technical details grid */}
            <div style={technicalGridStyle}>
              <div style={techItemStyle}>
                <span style={techLabelStyle}>Organizador</span>
                <span style={{ ...techValueStyle, display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
                  <UserAvatar user={organizer} size={18} style={{ border: '1px solid var(--color-border)' }} />
                  <span>{organizer?.name.split(' ')[0]}</span>
                </span>
              </div>
              <div style={techItemStyle}>
                <span style={techLabelStyle}>Duração da Partida</span>
                <span style={techValueStyle}>{session.duration} minutos</span>
              </div>
              <div style={techItemStyle}>
                <span style={techLabelStyle}>Membros Jogando</span>
                <span style={techValueStyle}>{session.participantIds.length} jogadores</span>
              </div>
            </div>

          </div>

        </div>

        {/* Right Side: Players list and Comments */}
        <div style={rightColStyle}>
          
          {/* Players Roster */}
          <div className="card" style={{ marginBottom: '24px' }}>
            <h3 style={{ ...sectionTitleStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <UsersIcon size={16} style={{ color: 'var(--color-primary)' }} />
              <span>Mesa de Jogadores</span>
            </h3>
            <div style={participantsContainerStyle}>
              {session.participantIds.map(pid => {
                const player = state.users.find(u => u.id === pid);
                const isWinner = pid === session.winnerId;
                return (
                  <div key={pid} style={participantRowStyle} onClick={() => navigate(`/players/${pid}`)}>
                    <UserAvatar user={player} size={36} style={participantAvatarStyle} />
                    <div style={{ flexGrow: 1 }}>
                      <div style={participantNameStyle}>
                        {player?.name}
                        {isWinner && <span className="badge badge-accent" style={winnerSmallBadgeStyle}>VENCEDOR</span>}
                        {pid === session.organizerId && <span className="badge badge-secondary" style={organizerBadgeStyle}>ORGANIZADOR</span>}
                      </div>
                      <span style={participantCourseStyle}>{player?.course}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Comments Section */}
          <div className="card comments-section">
            <h3 style={{ ...sectionTitleStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <MessageSquareIcon size={16} style={{ color: 'var(--color-secondary)' }} />
              <span>Comentários ({session.comments.length})</span>
            </h3>
            
            {/* Form */}
            {currentUser ? (
              <form onSubmit={handleCommentSubmit} style={commentFormStyle}>
                <input
                  type="text"
                  placeholder="Escreva algo sobre a mesa..."
                  value={commentText}
                  onChange={e => setCommentText(e.target.value)}
                  style={commentInputStyle}
                />
                <button type="submit" className="btn btn-primary btn-sm">Enviar</button>
              </form>
            ) : (
              <p style={loginToCommentStyle}>Faça login simulado na barra superior para comentar.</p>
            )}

            {/* List */}
            <div style={commentsListStyle}>
              {session.comments.length === 0 ? (
                <p style={noCommentsStyle}>Nenhum comentário ainda. Seja o primeiro a opinar!</p>
              ) : (
                session.comments.map(c => (
                  <div key={c.id} style={commentItemStyle}>
                    <UserAvatar
                      user={{ name: c.userName, avatarUrl: state.users.find(u => u.id === c.userId)?.avatarUrl }}
                      size={30}
                      style={commentUserAvatarStyle}
                    />
                    <div style={{ flexGrow: 1 }}>
                      <div style={commentHeaderStyle}>
                        <strong style={commentUserNameStyle}>{c.userName}</strong>
                        <span style={commentTimeStyle}>
                          {new Date(c.createdAt).toLocaleDateString('pt-BR')} • {new Date(c.createdAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                        </span>
                      </div>
                      <p style={commentContentStyle}>{c.content}</p>
                      {(currentUser?.id === c.userId || currentUser?.role === 'admin') && (
                        <button type="button" className="btn btn-ghost no-print"
                          aria-label={`Excluir comentário de ${c.userName}`}
                          onClick={() => void handleCommentDelete(c.id)}>
                          Excluir
                        </button>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>

        </div>

      </div>

    </div>
  );
};

// Styles for Session Details
const backButtonStyle: React.CSSProperties = {
  background: 'none',
  border: 'none',
  fontFamily: 'var(--font-title)',
  fontWeight: 600,
  fontSize: '0.9rem',
  color: 'var(--color-primary)',
  cursor: 'pointer',
  padding: '6px 0',
  marginBottom: '20px',
  display: 'flex',
  alignItems: 'center',
  gap: '6px',
};

const detailsGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.2fr 0.8fr',
  gap: '32px',
};

const leftColStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
};

const rightColStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
};

const dateLabelStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  fontWeight: 700,
  color: 'var(--color-text-light)',
  letterSpacing: '0.05em',
  display: 'block',
  marginBottom: '4px',
};

const titleStyle: React.CSSProperties = {
  fontSize: '2.2rem',
  fontWeight: 800,
  color: 'var(--color-text-main)',
  lineHeight: '1.2',
};

const locationBadgeStyle: React.CSSProperties = {
  display: 'inline-block',
  fontSize: '0.9rem',
  color: 'var(--color-text-muted)',
  fontWeight: 500,
  marginTop: '4px',
};

const gameStripStyle: React.CSSProperties = {
  display: 'flex',
  gap: '12px',
  padding: '12px',
  backgroundColor: '#FAF9F6',
  borderRadius: '12px',
  border: '1px solid var(--color-border)',
  marginTop: '20px',
  cursor: 'pointer',
  alignItems: 'center',
  transition: 'border-color 0.2s',
};

const gameStripImgStyle: React.CSSProperties = {
  width: '50px',
  height: '50px',
  borderRadius: '6px',
  objectFit: 'cover',
};

const gameStripTitleStyle: React.CSSProperties = {
  fontSize: '1rem',
  fontWeight: 700,
};

const gameStripDescStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  color: 'var(--color-text-muted)',
};

const winnerSpotlightCardStyle: React.CSSProperties = {
  marginTop: '24px',
  backgroundColor: 'var(--color-accent-light)',
  border: '1px solid rgba(243, 182, 63, 0.25)',
  borderRadius: '16px',
  padding: '20px',
  boxShadow: 'var(--shadow-sm)',
};

const winnerSpotlightHeaderStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '12px',
};

const winnerLabelStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  fontWeight: 700,
  color: '#A06E0E',
  textTransform: 'uppercase',
  letterSpacing: '0.03em',
};

const winnerNameStyle: React.CSSProperties = {
  fontSize: '1.2rem',
  fontWeight: 800,
  color: 'var(--color-text-main)',
  marginTop: '2px',
};

const sectionWrapperStyle: React.CSSProperties = {
  marginTop: '28px',
  borderTop: '1px solid var(--color-border)',
  paddingTop: '20px',
};

const sectionTitleStyle: React.CSSProperties = {
  fontSize: '1.05rem',
  fontWeight: 700,
  marginBottom: '12px',
  fontFamily: 'var(--font-title)',
};

const notesContentStyle: React.CSSProperties = {
  fontSize: '1rem',
  lineHeight: '1.6',
  color: 'var(--color-text-muted)',
  fontStyle: 'italic',
};

const photoGalleryStyle: React.CSSProperties = {
  display: 'flex',
  gap: '12px',
  flexWrap: 'wrap',
};

const photoStyle: React.CSSProperties = {
  maxWidth: '100%',
  maxHeight: '300px',
  borderRadius: '12px',
  objectFit: 'cover',
  border: '1px solid var(--color-border)',
};

const technicalGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(3, 1fr)',
  gap: '16px',
  marginTop: '28px',
  borderTop: '1px solid var(--color-border)',
  paddingTop: '20px',
};

const techItemStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '4px',
};

const techLabelStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  color: 'var(--color-text-light)',
  fontWeight: 600,
};

const techValueStyle: React.CSSProperties = {
  fontSize: '0.9rem',
  fontWeight: 700,
  color: 'var(--color-text-main)',
};

// Right column lists
const participantsContainerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '12px',
};

const participantRowStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '12px',
  cursor: 'pointer',
  padding: '6px',
  borderRadius: '8px',
  transition: 'background-color 0.2s',
};

// Add meeple row hover style dynamically
if (typeof document !== 'undefined') {
  const hoverStyle = document.createElement('style');
  hoverStyle.textContent = `
    .participant-row-hover:hover {
      background-color: var(--color-bg) !important;
    }
  `;
  document.head.appendChild(hoverStyle);
}

const participantAvatarStyle: React.CSSProperties = {
  fontSize: '1.6rem',
  width: '36px',
  height: '36px',
  backgroundColor: 'var(--color-bg)',
  borderRadius: '50%',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
};

const participantNameStyle: React.CSSProperties = {
  fontSize: '0.9rem',
  fontWeight: 600,
  display: 'flex',
  alignItems: 'center',
  gap: '6px',
  flexWrap: 'wrap',
};

const winnerSmallBadgeStyle: React.CSSProperties = {
  fontSize: '0.55rem',
  backgroundColor: 'var(--color-accent)',
  color: '#6E4E0A',
  padding: '1px 4px',
  borderRadius: '4px',
};

const organizerBadgeStyle: React.CSSProperties = {
  fontSize: '0.55rem',
  backgroundColor: 'var(--color-secondary)',
  color: 'white',
  padding: '1px 4px',
  borderRadius: '4px',
};

const participantCourseStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  color: 'var(--color-text-muted)',
};

// Comments Section styles
const commentFormStyle: React.CSSProperties = {
  display: 'flex',
  gap: '10px',
  marginBottom: '20px',
  alignItems: 'center',
};

const commentInputStyle: React.CSSProperties = {
  flexGrow: 1,
  padding: '8px 12px',
  borderRadius: '6px',
  border: '1px solid var(--color-border)',
  fontSize: '0.85rem',
  outline: 'none',
};

const loginToCommentStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  color: 'var(--color-text-light)',
  fontStyle: 'italic',
  marginBottom: '20px',
  textAlign: 'center',
};

const commentsListStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '16px',
};

const noCommentsStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  color: 'var(--color-text-muted)',
  fontStyle: 'italic',
  textAlign: 'center',
  padding: '16px 0',
};

const commentItemStyle: React.CSSProperties = {
  display: 'flex',
  gap: '10px',
  alignItems: 'flex-start',
  paddingBottom: '12px',
  borderBottom: '1px solid #FAF9F6',
};

const commentUserAvatarStyle: React.CSSProperties = {
  fontSize: '1.4rem',
  width: '30px',
  height: '30px',
  backgroundColor: 'var(--color-secondary-light)',
  borderRadius: '50%',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  flexShrink: 0,
};

const commentHeaderStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'baseline',
  marginBottom: '2px',
};

const commentUserNameStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  fontWeight: 700,
  color: 'var(--color-text-main)',
};

const commentTimeStyle: React.CSSProperties = {
  fontSize: '0.7rem',
  color: 'var(--color-text-light)',
};

const commentContentStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  color: 'var(--color-text-muted)',
  lineHeight: '1.4',
};

// Responsive mobile details CSS injections
const responsiveDetailsStyle = `
@media (max-width: 800px) {
  .details-grid-responsive {
    grid-template-columns: 1fr !important;
    gap: 24px !important;
  }
}
`;
if (typeof document !== 'undefined') {
  const styleEl = document.createElement('style');
  styleEl.textContent = responsiveDetailsStyle;
  document.head.appendChild(styleEl);
}
export default SessionDetails;
