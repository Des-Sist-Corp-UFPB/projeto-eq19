import React, { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useDatabase } from '../context/DatabaseContext';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { UserAvatar } from '../components/UserAvatar';
import { HeartIcon, CalendarIcon, TrophyIcon, MapPinIcon, ClockIcon, CrownIcon } from '../components/Icons';

const CameraIcon: React.FC<{ size?: number; style?: React.CSSProperties }> = ({ size = 16, style }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={style}>
    <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
    <circle cx="12" cy="13" r="4" />
  </svg>
);

export const PlayerProfile: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { state, editUser } = useDatabase();
  const { currentUser, changePassword } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const user = state.users.find(u => u.id === id);
  const [avatarUrl, setAvatarUrl] = useState('');
  const [uploading, setUploading] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [changingPassword, setChangingPassword] = useState(false);

  // Modal states
  const [isAvatarModalOpen, setIsAvatarModalOpen] = useState(false);
  const [isPasswordModalOpen, setIsPasswordModalOpen] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Sync avatarUrl state when user changes
  useEffect(() => {
    if (user) {
      // The local draft must reset when navigation selects another player.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setAvatarUrl(user.avatarUrl || '');
    }
  }, [user]);

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
  const canEditAvatar = currentUser?.id === user.id;
  const isSelf = currentUser?.id === user.id;
  const playerWins = state.sessions.filter(s => s.winnerId === user.id).length;
  const winRate = playerSessions.length > 0 ? Math.round((playerWins / playerSessions.length) * 100) : 0;

  // Get upcoming events where player is participant or on waiting list
  const upcomingEvents = state.events.filter(
    e => e.status === 'active' && (e.participantIds.includes(user.id) || e.waitingListIds.includes(user.id))
  );

  const handleAvatarClick = () => {
    if (canEditAvatar) {
      setIsAvatarModalOpen(true);
    }
  };

  const handleAlterarFotoClick = () => {
    fileInputRef.current?.click();
  };

  const handleRemoverFotoClick = async () => {
    try {
      await editUser(user.id, { avatarUrl: "" });
      setAvatarUrl("");
      showToast('Foto de perfil removida com sucesso.', 'success');
      setIsAvatarModalOpen(false);
    } catch {
      showToast('Não foi possível atualizar a foto de perfil.', 'error');
    }
  };

  const handleUrlSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await editUser(user.id, { avatarUrl: avatarUrl.trim() });
      showToast('Foto de perfil atualizada com sucesso.', 'success');
      setIsAvatarModalOpen(false);
    } catch {
      showToast('Não foi possível atualizar a foto de perfil.', 'error');
    }
  };

  const handleAvatarUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    const reader = new FileReader();
    reader.onload = async () => {
      const result = typeof reader.result === 'string' ? reader.result : '';
      try {
        await editUser(user.id, { avatarUrl: result });
        setAvatarUrl(result);
        showToast('Foto de perfil atualizada com sucesso.', 'success');
        setIsAvatarModalOpen(false);
      } catch {
        showToast('Não foi possível atualizar a foto de perfil.', 'error');
      } finally {
        setUploading(false);
      }

      // Reset file input value so selection of same file works next time
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
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
      setIsPasswordModalOpen(false); // Only close modal on success
    }
  };

  // Helper to determine role label
  const getRoleLabel = () => {
    if (user.role === 'admin') return 'Organização / Admin';
    return 'Estudante / Membro';
  };

  return (
    <div className="container" style={{ paddingTop: '24px' }}>
      
      {/* Back button */}
      <button onClick={() => navigate('/players')} style={backButtonStyle} className="no-print">
        ← Voltar à Lista de Jogadores
      </button>

      {/* Modern Profile Header Card */}
      <div className="card" style={profileHeaderCardStyle}>
        <div style={profileHeaderBannerStyle}></div>
        <div style={profileHeaderContentStyle}>
          <div style={profileHeaderMainInfoStyle}>
            {/* Avatar container wrapper */}
            <div 
              className={`avatar-edit-container ${canEditAvatar ? 'editable' : ''}`}
              onClick={handleAvatarClick}
              style={profileAvatarContainerStyle}
            >
              <UserAvatar user={user} size={110} style={profileAvatarStyle} />
              {canEditAvatar && (
                <div className="avatar-edit-overlay">
                  <CameraIcon size={18} />
                  <span>Alterar</span>
                </div>
              )}
              {/* Hidden file input */}
              <input
                type="file"
                ref={fileInputRef}
                accept="image/*"
                style={{ display: 'none' }}
                onChange={handleAvatarUpload}
              />
            </div>

            <div style={profileTextDetailsStyle}>
              <div style={nameAndBadgeRowStyle}>
                <h1 style={profileNameStyle}>{user.name}</h1>
                <span className={`badge ${user.role === 'admin' ? 'badge-secondary' : 'badge-primary'}`} style={badgeRoleStyle}>
                  {getRoleLabel()}
                </span>
              </div>
              <p style={profileCourseStyle}>{user.course}</p>
              <p style={profileBioStyle}>"{user.bio || 'Sem bio informada.'}"</p>
              <span style={profileJoinedStyle}>
                <CalendarIcon size={14} style={{ marginRight: '6px', color: 'var(--color-primary)' }} />
                Membro desde: {new Date(user.joinedAt).toLocaleDateString('pt-BR')}
              </span>
            </div>
          </div>

          {/* Stats Bar */}
          <div style={headerStatsGridStyle}>
            <div style={headerStatItemStyle}>
              <span style={headerStatValueStyle}>{playerSessions.length}</span>
              <span style={headerStatLabelStyle}>Partidas Jogadas</span>
            </div>
            <div style={headerStatItemStyle}>
              <span style={headerStatValueStyle}>{playerWins}</span>
              <span style={headerStatLabelStyle}>Vitórias</span>
            </div>
            <div style={{ ...headerStatItemStyle, borderRight: 'none', backgroundColor: 'var(--color-secondary-light)' }}>
              <span style={{ ...headerStatValueStyle, color: 'var(--color-secondary)' }}>{winRate}%</span>
              <span style={{ ...headerStatLabelStyle, color: 'var(--color-secondary-hover)' }}>Taxa de Vitória</span>
            </div>
          </div>
        </div>
      </div>

      {/* Main Responsive Grid Layout */}
      <div className="profile-grid-responsive" style={{ marginTop: '24px' }}>
        
        {/* Left Column: Upcoming Meetings & Past Matches */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          
          {/* Upcoming Events Card */}
          <div className="card">
            <h3 style={cardTitleStyle}>
              <CalendarIcon size={18} style={{ color: 'var(--color-primary)' }} />
              <span>Próximos Encontros</span>
            </h3>
            {upcomingEvents.length === 0 ? (
              <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', fontStyle: 'italic', padding: '8px 0' }}>
                Nenhum encontro agendado nos próximos dias.
              </p>
            ) : (
              <div style={eventListStyle}>
                {upcomingEvents.map(event => {
                  const game = state.boardGames.find(g => g.id === event.gameId);
                  const isWaiting = event.waitingListIds.includes(user.id);

                  return (
                    <div key={event.id} className="profile-clickable-row" style={eventRowStyle} onClick={() => navigate('/events')}>
                      <div>
                        <strong style={{ fontSize: '0.92rem', color: 'var(--color-text-main)' }}>{game?.name}</strong>
                        <div style={eventMetaStyle}>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', marginRight: '10px' }}>
                            <MapPinIcon size={12} style={{ color: 'var(--color-primary)' }} />
                            <span>{event.location}</span>
                          </span>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
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

          {/* Past Sessions History Card */}
          <div className="card">
            <h3 style={cardTitleStyle}>
              <TrophyIcon size={18} style={{ color: 'var(--color-accent)' }} />
              <span>Histórico de Partidas Recentes</span>
            </h3>
            {playerSessions.length === 0 ? (
              <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', fontStyle: 'italic', padding: '8px 0' }}>
                Nenhuma partida registrada no arquivo histórico ainda.
              </p>
            ) : (
              <div style={sessionsListStyle}>
                {[...playerSessions]
                  .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
                  .slice(0, 5) // Display top 5 recent sessions
                  .map(s => {
                    const game = state.boardGames.find(g => g.id === s.gameId);
                    const isWinner = s.winnerId === user.id;

                    return (
                      <div key={s.id} className="profile-clickable-row" style={sessionRowStyle} onClick={() => navigate(`/sessions/${s.id}`)}>
                        <div style={{ display: 'flex', flexDirection: 'column' }}>
                          <span style={sessionDateStyle}>{new Date(s.date).toLocaleDateString('pt-BR')}</span>
                          <strong style={{ fontSize: '0.92rem', color: 'var(--color-text-main)', marginTop: '2px' }}>{game?.name}</strong>
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

        {/* Right Column: Favorite Games & Compact Security Settings */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          
          {/* Favorite Board Games Card */}
          <div className="card">
            <h3 style={cardTitleStyle}>
              <HeartIcon size={18} fill="var(--color-danger)" style={{ color: 'var(--color-danger)' }} />
              <span>Jogos Favoritos</span>
            </h3>
            {user.favoriteGames.length === 0 ? (
              <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', padding: '8px 0' }}>Nenhum jogo favoritado ainda.</p>
            ) : (
              <div style={favoritesGridStyle}>
                {user.favoriteGames.map(gid => {
                  const game = state.boardGames.find(g => g.id === gid);
                  if (!game) return null;
                  return (
                    <div key={gid} style={favoriteItemStyle} onClick={() => navigate(`/games?id=${gid}`)}>
                      <img src={game.coverUrl} alt={game.name} style={favoriteImgStyle} className="profile-clickable-row" />
                      <span style={favoriteNameStyle} title={game.name}>{game.name}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Account Security Card (Only shown if viewing own profile) */}
          {isSelf && (
            <div className="card" style={{ padding: '24px' }}>
              <h3 style={cardTitleStyle}>
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                  <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                </svg>
                <span>Segurança da Conta</span>
              </h3>
              <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', marginBottom: '16px', lineHeight: '1.5' }}>
                Atualize sua senha de acesso para manter sua conta protegida.
              </p>
              <button className="btn btn-outline" onClick={() => setIsPasswordModalOpen(true)} style={{ width: '100%' }}>
                Alterar senha
              </button>
            </div>
          )}

        </div>

      </div>

      {/* --- MODALS --- */}

      {/* Avatar configuration Modal */}
      {isAvatarModalOpen && (
        <div className="modal-overlay" onClick={() => setIsAvatarModalOpen(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()} style={avatarModalContentStyle}>
            <h2 className="mb-md" style={{ fontFamily: 'var(--font-title)' }}>Foto de perfil</h2>
            
            <div style={avatarModalBodyStyle}>
              {/* Current Avatar Preview */}
              <UserAvatar user={{ ...user, avatarUrl }} size={96} style={{ border: '2px solid var(--color-border)', marginBottom: '12px' }} />
              <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', textAlign: 'center', marginBottom: '20px' }}>
                Escolha uma nova imagem ou remova a foto atual.
              </p>
              
              <div style={avatarModalActionsStyle}>
                <button type="button" className="btn btn-primary" onClick={handleAlterarFotoClick} disabled={uploading} style={{ width: '100%' }}>
                  {uploading ? 'Enviando...' : 'Alterar foto'}
                </button>
                
                {user.avatarUrl && (
                  <button type="button" className="btn btn-outline" onClick={handleRemoverFotoClick} style={removerFotoBtnStyle}>
                    Remover foto
                  </button>
                )}

                {/* Secondary Option: URL Upload */}
                <form onSubmit={handleUrlSave} style={{ marginTop: '16px', borderTop: '1px solid var(--color-border)', paddingTop: '16px', width: '100%' }}>
                  <label className="form-label" style={{ fontSize: '0.8rem', fontWeight: 600 }}>Ou use uma URL de imagem:</label>
                  <div style={{ display: 'flex', gap: '8px', marginTop: '6px' }}>
                    <input
                      type="url"
                      className="form-input"
                      value={avatarUrl}
                      onChange={(e) => setAvatarUrl(e.target.value)}
                      placeholder="https://exemplo.com/foto.png"
                      style={{ flex: 1, fontSize: '0.8rem' }}
                    />
                    <button type="submit" className="btn btn-primary btn-sm" style={{ whiteSpace: 'nowrap' }}>
                      Salvar URL
                    </button>
                  </div>
                </form>

                <button type="button" className="btn btn-outline" onClick={() => setIsAvatarModalOpen(false)} style={{ width: '100%', marginTop: '12px' }}>
                  Cancelar
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Password Edit Modal */}
      {isPasswordModalOpen && (
        <div className="modal-overlay" onClick={() => setIsPasswordModalOpen(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()} style={passwordModalContentStyle}>
            <h2 className="mb-md" style={{ fontFamily: 'var(--font-title)' }}>Alterar Senha</h2>
            <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', marginBottom: '20px' }}>
              Preencha os campos abaixo para atualizar sua senha de acesso.
            </p>
            
            <form onSubmit={handlePasswordChange} style={formStyle}>
              <div className="form-group">
                <label className="form-label" style={{ fontWeight: 600 }}>Senha Atual *</label>
                <input 
                  className="form-input" 
                  type="password" 
                  value={currentPassword} 
                  onChange={e => setCurrentPassword(e.target.value)} 
                  placeholder="Digite sua senha atual" 
                  required 
                  style={{ width: '100%' }}
                />
              </div>
              <div className="form-group">
                <label className="form-label" style={{ fontWeight: 600 }}>Nova Senha *</label>
                <input 
                  className="form-input" 
                  type="password" 
                  value={newPassword} 
                  onChange={e => setNewPassword(e.target.value)} 
                  placeholder="Mínimo de 8 caracteres" 
                  required 
                  style={{ width: '100%' }}
                />
              </div>
              <div className="form-group">
                <label className="form-label" style={{ fontWeight: 600 }}>Confirmar Nova Senha *</label>
                <input 
                  className="form-input" 
                  type="password" 
                  value={confirmPassword} 
                  onChange={e => setConfirmPassword(e.target.value)} 
                  placeholder="Confirme sua nova senha" 
                  required 
                  style={{ width: '100%' }}
                />
              </div>
              
              <div style={formActionsStyle}>
                <button type="button" className="btn btn-outline" onClick={() => setIsPasswordModalOpen(false)}>
                  Cancelar
                </button>
                <button className="btn btn-primary" type="submit" disabled={changingPassword}>
                  {changingPassword ? 'Atualizando...' : 'Salvar nova senha'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
};

// Styling definitions
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
  display: 'inline-flex',
  alignItems: 'center',
  gap: '4px',
};

const profileHeaderCardStyle: React.CSSProperties = {
  position: 'relative',
  overflow: 'hidden',
  padding: '0',
  display: 'flex',
  flexDirection: 'column',
  width: '100%',
};

const profileHeaderBannerStyle: React.CSSProperties = {
  height: '120px',
  background: 'linear-gradient(135deg, var(--color-primary-light) 0%, var(--color-secondary-light) 100%)',
  borderBottom: '1px solid var(--color-border)',
};

const profileHeaderContentStyle: React.CSSProperties = {
  padding: '24px 32px 32px 32px',
  display: 'flex',
  flexDirection: 'column',
  gap: '24px',
};

const profileHeaderMainInfoStyle: React.CSSProperties = {
  display: 'flex',
  gap: '24px',
  alignItems: 'flex-start',
  flexWrap: 'wrap',
};

const profileAvatarContainerStyle: React.CSSProperties = {
  marginTop: '-72px',
  border: '4px solid white',
  boxShadow: 'var(--shadow-md)',
  zIndex: 2,
};

const profileAvatarStyle: React.CSSProperties = {
  display: 'block',
  borderRadius: '50%',
};

const profileTextDetailsStyle: React.CSSProperties = {
  flex: '1',
  minWidth: '250px',
  display: 'flex',
  flexDirection: 'column',
  gap: '6px',
};

const nameAndBadgeRowStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '12px',
  flexWrap: 'wrap',
};

const profileNameStyle: React.CSSProperties = {
  fontSize: '1.8rem',
  fontWeight: 800,
  color: 'var(--color-text-main)',
  lineHeight: 1.2,
};

const badgeRoleStyle: React.CSSProperties = {
  fontSize: '0.7rem',
  fontWeight: 700,
  letterSpacing: '0.05em',
  padding: '4px 8px',
};

const profileCourseStyle: React.CSSProperties = {
  fontSize: '0.95rem',
  fontWeight: 600,
  color: 'var(--color-primary)',
};

const profileBioStyle: React.CSSProperties = {
  fontSize: '0.9rem',
  color: 'var(--color-text-muted)',
  fontStyle: 'italic',
  lineHeight: '1.5',
  margin: '8px 0 4px 0',
};

const profileJoinedStyle: React.CSSProperties = {
  fontSize: '0.78rem',
  color: 'var(--color-text-light)',
  fontWeight: 500,
  display: 'inline-flex',
  alignItems: 'center',
};

const headerStatsGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(3, 1fr)',
  border: '1px solid var(--color-border)',
  borderRadius: '12px',
  overflow: 'hidden',
  backgroundColor: '#FAF9F6',
};

const headerStatItemStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  padding: '16px 12px',
  textAlign: 'center',
  borderRight: '1px solid var(--color-border)',
};

const headerStatValueStyle: React.CSSProperties = {
  fontSize: '1.5rem',
  fontWeight: 800,
  color: 'var(--color-text-main)',
  fontFamily: 'var(--font-title)',
  lineHeight: '1',
};

const headerStatLabelStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  color: 'var(--color-text-muted)',
  fontWeight: 600,
  marginTop: '6px',
  textTransform: 'uppercase',
  letterSpacing: '0.04em',
};

const cardTitleStyle: React.CSSProperties = {
  fontSize: '1.1rem',
  fontWeight: 700,
  fontFamily: 'var(--font-title)',
  marginBottom: '16px',
  display: 'flex',
  alignItems: 'center',
  gap: '8px',
  color: 'var(--color-text-main)',
};

const formStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '14px',
};

const favoritesGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(80px, 1fr))',
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
  transition: 'border-color 0.2s',
};

const favoriteNameStyle: React.CSSProperties = {
  fontSize: '0.78rem',
  fontWeight: 600,
  color: 'var(--color-text-main)',
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  width: '100%',
};

const eventListStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '10px',
};

const eventRowStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  padding: '12px 14px',
  backgroundColor: '#FAF9F6',
  borderRadius: '8px',
  border: '1px solid var(--color-border)',
  cursor: 'pointer',
};

const eventMetaStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  color: 'var(--color-text-muted)',
  marginTop: '4px',
};

const sessionsListStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '10px',
};

const sessionRowStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  padding: '12px 14px',
  backgroundColor: 'white',
  borderRadius: '8px',
  border: '1px solid var(--color-border)',
  cursor: 'pointer',
};

const sessionDateStyle: React.CSSProperties = {
  fontSize: '0.72rem',
  color: 'var(--color-text-light)',
  fontWeight: 600,
};

const sessionDescStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  color: 'var(--color-text-muted)',
  marginTop: '4px',
};

const winnerLabelBadgeStyle: React.CSSProperties = {
  fontSize: '0.65rem',
  boxShadow: '0 2px 6px rgba(243, 182, 63, 0.2)',
};

// Modals styling
const avatarModalContentStyle: React.CSSProperties = {
  maxWidth: '400px',
  width: '90%',
  padding: '28px',
};

const avatarModalBodyStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
};

const avatarModalActionsStyle: React.CSSProperties = {
  width: '100%',
  display: 'flex',
  flexDirection: 'column',
  gap: '10px',
};

const removerFotoBtnStyle: React.CSSProperties = {
  width: '100%',
  borderColor: 'var(--color-danger)',
  color: 'var(--color-danger)',
};

const passwordModalContentStyle: React.CSSProperties = {
  maxWidth: '450px',
  width: '90%',
  padding: '28px',
};

const formActionsStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'flex-end',
  gap: '12px',
  marginTop: '20px',
};

export default PlayerProfile;
