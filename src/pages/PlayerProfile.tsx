import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useDatabase } from '../context/DatabaseContext';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { UserAvatar } from '../components/UserAvatar';
import { HeartIcon, CalendarIcon, TrophyIcon, MapPinIcon, ClockIcon, CrownIcon } from '../components/Icons';

export const PlayerProfile: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { state, editUser } = useDatabase();
  const { currentUser, isAdmin, changePassword } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const user = state.users.find(u => u.id === id);
  const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl || '');
  const [uploading, setUploading] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [changingPassword, setChangingPassword] = useState(false);
  if (!user) {
    return (
      <div className="container text-center" style={{ padding: '64px' }}>
        <h2>Jogador não encontrado</h2>
        <button className="btn btn-primary mt-lg" onClick={() => navigate('/players')}>Voltar à Lista</button>
      </div>
    );
  }

  // Calculate stats for this player
  const playerSessions = state.sessions.filter(s => s.participantIds.includes(user.id));
  const canEditAvatar = currentUser?.id === user.id || isAdmin;
  const playerWins = state.sessions.filter(s => s.winnerId === user.id).length;
  const winRate = playerSessions.length > 0 ? Math.round((playerWins / playerSessions.length) * 100) : 0;

  // Get upcoming events where player is participant or on waiting list
  const upcomingEvents = state.events.filter(
    e => e.status === 'active' && (e.participantIds.includes(user.id) || e.waitingListIds.includes(user.id))
  );

  const handleAvatarSave = (e: React.FormEvent) => {
    e.preventDefault();
    editUser(user.id, { avatarUrl: avatarUrl.trim() || undefined });
    showToast('Foto de perfil atualizada.', 'success');
  };

  const handleAvatarUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    const reader = new FileReader();
    reader.onload = () => {
      const result = typeof reader.result === 'string' ? reader.result : '';
      setAvatarUrl(result);
      editUser(user.id, { avatarUrl: result });
      showToast('Foto enviada com sucesso.', 'success');
      setUploading(false);
    };
    reader.onerror = () => {
      showToast('Não foi possível ler a imagem.', 'error');
      setUploading(false);
    };
    reader.readAsDataURL(file);
  };

  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setChangingPassword(true);
    const result = await changePassword(currentPassword, newPassword, confirmPassword);
    setChangingPassword(false);
    if (result.ok) {
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    }
  };

  return (
    <div className="container" style={{ paddingTop: '24px' }}>
      
      {/* Back button */}
      <button onClick={() => navigate('/players')} style={backButtonStyle} className="no-print">
        ← Voltar à Lista de Jogadores
      </button>

      {/* Grid Layout */}
      <div style={profileGridStyle}>
        
        {/* Left Side: Profile Summary & Stats */}
        <div style={leftColStyle}>
          
          <div className="card" style={profileCardStyle}>
            {/* Header info */}
            <UserAvatar user={user} size={100} style={avatarImageStyle} />
            <h1 style={playerNameStyle}>{user.name}</h1>
            <span className="badge badge-primary">{user.course}</span>
            {user.role === 'admin' && (
              <span className="badge badge-secondary" style={{ marginTop: '6px' }}>ORGANIZAÇÃO / ADMIN</span>
            )}
            
            <p style={bioTextStyle}>"{user.bio}"</p>
            <span style={joinedDateStyle}>Membro desde: {new Date(user.joinedAt).toLocaleDateString('pt-BR')}</span>

            {canEditAvatar && (
              <form onSubmit={handleAvatarSave} style={avatarFormStyle}>
                <label style={avatarLabelStyle}>Enviar foto do usuário</label>
                <input
                  type="file"
                  accept="image/*"
                  className="form-input"
                  onChange={handleAvatarUpload}
                  style={avatarInputStyle}
                />
                <input
                  type="url"
                  className="form-input"
                  value={avatarUrl}
                  onChange={(e) => setAvatarUrl(e.target.value)}
                  placeholder="Ou cole uma URL de imagem"
                  style={avatarInputStyle}
                />
                <button className="btn btn-primary btn-sm" type="submit" disabled={uploading}>
                  {uploading ? 'Enviando...' : 'Salvar foto'}
                </button>
              </form>
            )}
          </div>

          {currentUser?.id === user.id && (
            <div className="card" style={{ marginTop: '24px', width: '100%' }}>
              <h3 style={{ ...sectionTitleStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>Alterar senha</h3>
              <form onSubmit={handlePasswordChange} style={avatarFormStyle}>
                <input className="form-input" type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} placeholder="Senha atual" required />
                <input className="form-input" type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder="Nova senha" required />
                <input className="form-input" type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} placeholder="Confirmar nova senha" required />
                <button className="btn btn-primary btn-sm" type="submit" disabled={changingPassword}>{changingPassword ? 'Salvando...' : 'Alterar senha'}</button>
              </form>
            </div>
          )}

          {/* Quick numbers */}
          <div style={statsBlockGridStyle}>
            <div className="card" style={statCardStyle}>
              <span style={statNumberStyle}>{playerSessions.length}</span>
              <span style={statLabelStyle}>Partidas Jogadas</span>
            </div>
            <div className="card" style={statCardStyle}>
              <span style={statNumberStyle}>{playerWins}</span>
              <span style={statLabelStyle}>Vitórias Gravadas</span>
            </div>
            <div className="card" style={{ ...statCardStyle, backgroundColor: 'var(--color-secondary-light)' }}>
              <span style={{ ...statNumberStyle, color: 'var(--color-secondary)' }}>{winRate}%</span>
              <span style={{ ...statLabelStyle, color: 'var(--color-secondary-hover)' }}>Taxa de Vitória</span>
            </div>
          </div>

          {/* Favorite Board Games */}
          <div className="card" style={{ marginTop: '24px' }}>
            <h3 style={{ ...sectionTitleStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <HeartIcon size={16} fill="var(--color-danger)" style={{ color: 'var(--color-danger)' }} />
              <span>Jogos Favoritos</span>
            </h3>
            {user.favoriteGames.length === 0 ? (
              <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem' }}>Nenhum jogo favoritado ainda.</p>
            ) : (
              <div style={favoritesGridStyle}>
                {user.favoriteGames.map(gid => {
                  const game = state.boardGames.find(g => g.id === gid);
                  if (!game) return null;
                  return (
                    <div key={gid} style={favoriteItemStyle} onClick={() => navigate(`/games?id=${gid}`)}>
                      <img src={game.coverUrl} alt={game.name} style={favoriteImgStyle} />
                      <span style={favoriteNameStyle}>{game.name}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

        </div>

        {/* Right Side: History of sessions & Upcoming Events */}
        <div style={rightColStyle}>
          
          {/* Upcoming scheduled games */}
          <div className="card" style={{ marginBottom: '24px' }}>
            <h3 style={{ ...sectionTitleStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <CalendarIcon size={16} style={{ color: 'var(--color-primary)' }} />
              <span>Próximos Encontros</span>
            </h3>
            {upcomingEvents.length === 0 ? (
              <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', fontStyle: 'italic' }}>
                Nenhum encontro agendado nos próximos dias.
              </p>
            ) : (
              <div style={eventListStyle}>
                {upcomingEvents.map(event => {
                  const game = state.boardGames.find(g => g.id === event.gameId);
                  const isWaiting = event.waitingListIds.includes(user.id);

                  return (
                    <div key={event.id} style={eventRowStyle} onClick={() => navigate('/events')}>
                      <div>
                        <strong style={{ fontSize: '0.9rem' }}>{game?.name}</strong>
                        <div style={{ ...eventMetaStyle, display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '2px' }}>
                            <MapPinIcon size={12} style={{ color: 'var(--color-primary)' }} />
                            <span>{event.location}</span>
                          </span>
                          <span>•</span>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '2px' }}>
                            <ClockIcon size={12} style={{ color: 'var(--color-primary)' }} />
                            <span>{new Date(event.date + 'T' + event.time).toLocaleDateString('pt-BR')} às {event.time}</span>
                          </span>
                        </div>
                      </div>
                      {isWaiting ? (
                        <span className="badge badge-danger" style={{ fontSize: '0.65rem' }}>FILA</span>
                      ) : (
                        <span className="badge badge-success" style={{ fontSize: '0.65rem' }}>CONFIRMADO</span>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Past sessions history */}
          <div className="card">
            <h3 style={{ ...sectionTitleStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <TrophyIcon size={16} style={{ color: 'var(--color-accent)' }} />
              <span>Histórico de Partidas Recentes</span>
            </h3>
            {playerSessions.length === 0 ? (
              <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', fontStyle: 'italic' }}>
                Nenhuma partida registrada no arquivo histórico ainda.
              </p>
            ) : (
              <div style={sessionsListStyle}>
                {[...playerSessions]
                  .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
                  .map(s => {
                    const game = state.boardGames.find(g => g.id === s.gameId);
                    const isWinner = s.winnerId === user.id;

                    return (
                      <div key={s.id} style={sessionRowStyle} onClick={() => navigate(`/sessions/${s.id}`)}>
                        <div style={{ display: 'flex', flexDirection: 'column' }}>
                          <span style={sessionDateStyle}>{new Date(s.date).toLocaleDateString('pt-BR')}</span>
                          <strong style={{ fontSize: '0.95rem' }}>{game?.name}</strong>
                          <span style={sessionDescStyle}>{s.location} • {s.duration} min</span>
                        </div>
                        {isWinner ? (
                          <span className="badge badge-accent" style={{ ...winnerLabelBadgeStyle, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                            <CrownIcon size={10} />
                            <span>VITÓRIA</span>
                          </span>
                        ) : (
                          <span className="badge badge-neutral" style={{ fontSize: '0.65rem' }}>JOGOU</span>
                        )}
                      </div>
                    );
                  })}
              </div>
            )}
          </div>

        </div>

      </div>

    </div>
  );
};

// Styling for player profile
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
};

const profileGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '0.9fr 1.1fr',
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

const profileCardStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  textAlign: 'center',
  padding: '32px 24px',
};

const avatarImageStyle: React.CSSProperties = {
  width: '100px',
  height: '100px',
  border: '3px solid var(--color-border)',
  boxShadow: 'var(--shadow-sm)',
  marginBottom: '16px',
};

const avatarFormStyle: React.CSSProperties = {
  width: '100%',
  display: 'flex',
  flexDirection: 'column',
  gap: '8px',
  marginTop: '16px',
};

const avatarLabelStyle: React.CSSProperties = {
  fontSize: '0.78rem',
  fontWeight: 600,
  color: 'var(--color-text-muted)',
};

const avatarInputStyle: React.CSSProperties = {
  width: '100%',
};

const playerNameStyle: React.CSSProperties = {
  fontSize: '1.8rem',
  fontWeight: 800,
  color: 'var(--color-text-main)',
  marginBottom: '6px',
};

const bioTextStyle: React.CSSProperties = {
  fontSize: '0.9rem',
  color: 'var(--color-text-muted)',
  fontStyle: 'italic',
  lineHeight: '1.5',
  margin: '16px 0',
};

const joinedDateStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  color: 'var(--color-text-light)',
  fontWeight: 500,
};

const statsBlockGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(3, 1fr)',
  gap: '12px',
  marginTop: '16px',
};

const statCardStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  padding: '12px',
  textAlign: 'center',
};

const statNumberStyle: React.CSSProperties = {
  fontSize: '1.4rem',
  fontWeight: 800,
  color: 'var(--color-text-main)',
  fontFamily: 'var(--font-title)',
};

const statLabelStyle: React.CSSProperties = {
  fontSize: '0.7rem',
  color: 'var(--color-text-muted)',
  fontWeight: 600,
  marginTop: '4px',
};

const sectionTitleStyle: React.CSSProperties = {
  fontSize: '1.05rem',
  fontWeight: 700,
  marginBottom: '16px',
  fontFamily: 'var(--font-title)',
};

const favoritesGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(3, 1fr)',
  gap: '12px',
};

const favoriteItemStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  textAlign: 'center',
  cursor: 'pointer',
  gap: '6px',
};

const favoriteImgStyle: React.CSSProperties = {
  width: '100%',
  aspectRatio: '1',
  borderRadius: '8px',
  objectFit: 'cover',
  boxShadow: 'var(--shadow-sm)',
  border: '1px solid var(--color-border)',
};

const favoriteNameStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  fontWeight: 600,
  color: 'var(--color-text-main)',
};

const eventListStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '12px',
};

const eventRowStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  padding: '10px 14px',
  backgroundColor: '#FAF9F6',
  borderRadius: '8px',
  border: '1px solid var(--color-border)',
  cursor: 'pointer',
};

const eventMetaStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  color: 'var(--color-text-muted)',
  marginTop: '2px',
};

const sessionsListStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '12px',
};

const sessionRowStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  padding: '10px 14px',
  backgroundColor: 'white',
  borderRadius: '8px',
  border: '1px solid var(--color-border)',
  cursor: 'pointer',
  transition: 'border-color 0.2s',
};

// Add profile row hover styling dynamically
if (typeof document !== 'undefined') {
  const hoverStyle = document.createElement('style');
  hoverStyle.textContent = `
    div[style*="cursor: pointer"]:hover {
      border-color: var(--color-border-hover) !important;
    }
  `;
  document.head.appendChild(hoverStyle);
}

const sessionDateStyle: React.CSSProperties = {
  fontSize: '0.7rem',
  color: 'var(--color-text-light)',
  fontWeight: 600,
};

const sessionDescStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  color: 'var(--color-text-muted)',
  marginTop: '2px',
};

const winnerLabelBadgeStyle: React.CSSProperties = {
  fontSize: '0.65rem',
  boxShadow: '0 2px 6px rgba(243, 182, 63, 0.2)',
};

const responsiveProfileStyle = `
@media (max-width: 800px) {
  .profile-grid-responsive {
    grid-template-columns: 1fr !important;
    gap: 24px !important;
  }
}
`;
if (typeof document !== 'undefined') {
  const styleEl = document.createElement('style');
  styleEl.textContent = responsiveProfileStyle;
  document.head.appendChild(styleEl);
}
export default PlayerProfile;
