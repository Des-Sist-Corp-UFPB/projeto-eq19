import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useDatabase } from '../context/DatabaseContext';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { TrophyIcon, UsersIcon, PuzzleIcon, TrashIcon } from '../components/Icons';
import { UserAvatar } from '../components/UserAvatar';

export const Players: React.FC = () => {
  const { state, promoteUser, deleteUser } = useDatabase();
  const { currentUser, isAdmin } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  // Sort players by win count to create a friendly ranking/leaderboard
  const rankedPlayers = [...state.users].sort((a, b) => b.winCount - a.winCount);

  const handlePromote = async (userId: string, name: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const targetUser = state.users.find(u => u.id === userId);
    try {
      await promoteUser(userId);
      const becameAdmin = targetUser?.role === 'student';
      showToast(`Cargo de "${name}" alterado para ${becameAdmin ? 'Administrador' : 'Estudante'}.`, 'success');
    } catch { showToast('Não foi possível alterar o cargo.', 'error'); }
  };

  const handleDelete = async (userId: string, name: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (currentUser && currentUser.id === userId) {
      showToast('Você não pode apagar seu próprio perfil ativo!', 'error');
      return;
    }
    if (window.confirm(`Tem certeza que deseja apagar permanentemente o perfil de "${name}"? Isso removerá suas participações.`)) {
      try {
        await deleteUser(userId);
        showToast(`Perfil de "${name}" removido com sucesso.`, 'info');
      } catch { showToast('Não foi possível remover o perfil.', 'error'); }
    }
  };

  return (
    <div className="container" style={{ paddingTop: '24px' }}>
      
      {/* Header */}
      <div style={headerSectionStyle}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <UsersIcon size={32} style={{ color: 'var(--color-primary)' }} />
          <div>
            <h1 style={{ fontSize: '2rem', marginBottom: '4px' }}>Roster de Jogadores</h1>
            <p style={{ color: 'var(--color-text-muted)' }}>Membros ativos na nossa comunidade. Clique em um perfil para ver estatísticas detalhadas.</p>
          </div>
        </div>
      </div>

      {/* Leaderboard Card banner */}
      <div className="card" style={leaderboardBannerStyle}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <TrophyIcon size={40} style={{ color: '#F3B63F' }} />
          <div>
            <h2 style={{ fontSize: '1.25rem', color: '#A06E0E', fontFamily: 'var(--font-title)' }}>Líder do Acervo</h2>
            <p style={{ fontSize: '0.9rem', color: 'var(--color-text-muted)', marginTop: '2px' }}>
              Parabéns a <strong>{rankedPlayers[0]?.name}</strong> ({rankedPlayers[0]?.course}) com a impressionante marca de <strong>{rankedPlayers[0]?.winCount} vitórias</strong> registradas!
            </p>
          </div>
        </div>
      </div>

      {/* Players Grid */}
      <div style={playersGridStyle}>
        {rankedPlayers.map((user, index) => {
          const rank = index + 1;
          const isTop3 = rank <= 3;
          
          return (
            <div
              key={user.id}
              className="card card-hoverable"
              style={playerCardStyle}
              onClick={() => navigate(`/players/${user.id}`)}
            >
              {/* Leaderboard Badge Ribbon */}
              <div
                style={{
                  ...rankRibbonStyle,
                  backgroundColor: rank === 1 ? '#F3B63F' : rank === 2 ? '#B5B9C8' : rank === 3 ? '#C49470' : '#ECEBE6',
                  color: isTop3 ? 'white' : 'var(--color-text-muted)'
                }}
              >
                {rank === 1 ? '1º' : rank === 2 ? '2º' : rank === 3 ? '3º' : `#${rank}`}
              </div>

              {/* Avatar and Info */}
              <div style={avatarContainerStyle}>
                <UserAvatar user={user} size={76} style={avatarImageStyle} />
                {user.role === 'admin' && <span className="badge badge-primary" style={adminBadgeStyle}>ADMIN</span>}
              </div>

              <div style={playerInfoStyle}>
                <h3 style={playerNameStyle}>{user.name}</h3>
                <span style={playerCourseStyle}>{user.course}</span>
                <p style={playerBioStyle}>"{user.bio.substring(0, 70)}..."</p>
              </div>

              {/* Wins counter */}
              <div style={winsRowStyle}>
                <TrophyIcon size={14} style={{ color: 'var(--color-accent)' }} />
                <span><strong>{user.winCount}</strong> vitórias registradas</span>
              </div>

              {/* Admin contextual actions */}
              {isAdmin && (
                <div style={adminActionsStyle}>
                  <button
                    className="btn btn-outline btn-sm"
                    style={{ ...actionBtnStyle, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '4px' }}
                    onClick={(e) => handlePromote(user.id, user.name, e)}
                  >
                    <PuzzleIcon size={12} />
                    <span>Cargo</span>
                  </button>
                  <button
                    className="btn btn-outline btn-sm"
                    style={{ ...actionBtnStyle, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '4px', borderColor: 'var(--color-danger)', color: 'var(--color-danger)' }}
                    onClick={(e) => handleDelete(user.id, user.name, e)}
                    disabled={currentUser?.id === user.id}
                  >
                    <TrashIcon size={12} />
                    <span>Remover</span>
                  </button>
                </div>
              )}

            </div>
          );
        })}
      </div>

    </div>
  );
};

// Styles for Players view
const headerSectionStyle: React.CSSProperties = {
  marginBottom: '32px',
  borderBottom: '2px solid var(--color-border)',
  paddingBottom: '16px',
};

const leaderboardBannerStyle: React.CSSProperties = {
  backgroundColor: 'var(--color-accent-light)',
  border: '1px solid rgba(243, 182, 63, 0.25)',
  padding: '20px 24px',
  marginBottom: '32px',
  boxShadow: 'var(--shadow-sm)',
};

const playersGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(3, 1fr)',
  gap: '24px',
};

const playerCardStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  textAlign: 'center',
  padding: '24px 16px',
  position: 'relative',
  cursor: 'pointer',
};

const rankRibbonStyle: React.CSSProperties = {
  position: 'absolute',
  top: '12px',
  right: '12px',
  fontSize: '0.8rem',
  fontWeight: 700,
  padding: '4px 10px',
  borderRadius: '20px',
  minWidth: '38px',
  textAlign: 'center',
};

const avatarContainerStyle: React.CSSProperties = {
  position: 'relative',
  marginBottom: '16px',
};

const avatarImageStyle: React.CSSProperties = {
  width: '76px',
  height: '76px',
  boxShadow: 'var(--shadow-sm)',
  border: '2px solid var(--color-border)',
};

const adminBadgeStyle: React.CSSProperties = {
  position: 'absolute',
  bottom: '-6px',
  left: '50%',
  transform: 'translateX(-50%)',
  fontSize: '0.55rem',
  padding: '2px 6px',
};

const playerInfoStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  gap: '4px',
  flexGrow: 1,
};

const playerNameStyle: React.CSSProperties = {
  fontSize: '1.15rem',
  fontWeight: 700,
  color: 'var(--color-text-main)',
};

const playerCourseStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  color: 'var(--color-text-light)',
  fontWeight: 600,
};

const playerBioStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  color: 'var(--color-text-muted)',
  fontStyle: 'italic',
  marginTop: '8px',
  lineHeight: '1.4',
  padding: '0 8px',
};

const winsRowStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '6px',
  fontSize: '0.85rem',
  marginTop: '16px',
  backgroundColor: '#FAF9F6',
  padding: '8px 16px',
  borderRadius: '20px',
  border: '1px solid var(--color-border)',
};

const adminActionsStyle: React.CSSProperties = {
  display: 'flex',
  gap: '8px',
  marginTop: '16px',
  width: '100%',
  borderTop: '1px dashed var(--color-border)',
  paddingTop: '12px',
};

const actionBtnStyle: React.CSSProperties = {
  flexGrow: 1,
  fontSize: '0.75rem',
  padding: '4px 8px',
};

// Responsive mobile details CSS injections
const responsivePlayersStyle = `
@media (max-width: 900px) {
  .players-grid-responsive {
    grid-template-columns: repeat(2, 1fr) !important;
  }
}
@media (max-width: 600px) {
  .players-grid-responsive {
    grid-template-columns: 1fr !important;
  }
}
`;
if (typeof document !== 'undefined') {
  const styleEl = document.createElement('style');
  styleEl.textContent = responsivePlayersStyle;
  document.head.appendChild(styleEl);
}
export default Players;
