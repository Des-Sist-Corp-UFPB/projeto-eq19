import React, { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { SearchIcon, DiceIcon } from './Icons';
import { GlobalSearch } from './GlobalSearch';
import { UserAvatar } from './UserAvatar';

export const Navbar: React.FC = () => {
  const { currentUser, isAdmin, logout } = useAuth();
  const location = useLocation();
  const [searchOpen, setSearchOpen] = useState(false);

  const isActive = (path: string) => {
    return location.pathname === path;
  };

  return (
    <>
      {/* Main Header / Navigation */}
      <header style={headerStyle} className="no-print">
        <div className="container" style={headerContainerStyle}>
          {/* Logo */}
          <Link to="/" style={logoLinkStyle}>
            <div style={logoBadgeStyle}>
              <DiceIcon size={24} style={{ color: 'var(--color-primary)' }} />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={logoTextStyle}>TABULA</span>
              <span style={logoSubStyle}>Clube de Mesa</span>
            </div>
          </Link>

          {/* Navigation Links */}
          <nav style={navStyle}>
            <Link to="/" style={isActive('/') ? activeLinkStyle : navLinkStyle}>Início</Link>
            <Link to="/games" style={isActive('/games') ? activeLinkStyle : navLinkStyle}>Jogos</Link>
            <Link to="/sessions" style={isActive('/sessions') ? activeLinkStyle : navLinkStyle}>Partidas</Link>
            <Link to="/events" style={isActive('/events') ? activeLinkStyle : navLinkStyle}>Agenda</Link>
            <Link to="/players" style={isActive('/players') ? activeLinkStyle : navLinkStyle}>Jogadores</Link>
            <Link to="/stats" style={isActive('/stats') ? activeLinkStyle : navLinkStyle}>Estatísticas</Link>
            {isAdmin && (
              <Link to="/audit-logs" style={isActive('/audit-logs') ? activeLinkStyle : navLinkStyle}>
                Auditoria
              </Link>
            )}
          </nav>

          {/* Right Section Actions */}
          <div style={rightActionsStyle}>
            {/* Global Search Trigger */}
            <button style={searchTriggerStyle} onClick={() => setSearchOpen(true)} title="Buscar (Ctrl+K)">
              <SearchIcon size={18} />
              <span style={searchPlaceholderTextStyle}>Buscar...</span>
            </button>

            {/* Auth Actions */}
            {currentUser ? (
              <>
                <Link to={`/players/${currentUser.id}`} style={profileBadgeStyle}>
                  <UserAvatar user={currentUser} size={36} style={avatarImageStyle} />
                  <div style={profileTextContainerStyle}>
                    <span style={profileNameStyle}>{currentUser.name.split(' ')[0]}</span>
                    {isAdmin && <span style={adminBadgeStyle}>ADMIN</span>}
                  </div>
                </Link>
                <button className="btn btn-outline btn-sm" onClick={logout}>Sair</button>
              </>
            ) : (
              <div style={authActionsStyle}>
                <Link to="/login" className="btn btn-outline btn-sm">Entrar</Link>
                <Link to="/register" className="btn btn-primary btn-sm">Criar Conta</Link>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Global Search Overlay */}
      <GlobalSearch isOpen={searchOpen} onClose={() => setSearchOpen(false)} />
    </>
  );
};

// Styling Object declarations
const headerStyle: React.CSSProperties = {
  backgroundColor: 'var(--color-card)',
  borderBottom: '1px solid var(--color-border)',
  position: 'sticky',
  top: '0', // Stays below the simulator bar
  zIndex: 999,
  padding: '12px 0',
  boxShadow: 'var(--shadow-sm)',
};

const headerContainerStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
};

const logoLinkStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '10px',
  color: 'var(--color-text-main)',
  textDecoration: 'none',
};

const logoBadgeStyle: React.CSSProperties = {
  fontSize: '1.8rem',
  backgroundColor: 'var(--color-primary-light)',
  width: '44px',
  height: '44px',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  borderRadius: '12px',
  boxShadow: '0 4px 10px rgba(224, 106, 71, 0.15)',
};

const logoTextStyle: React.CSSProperties = {
  fontFamily: 'var(--font-title)',
  fontWeight: 800,
  fontSize: '1.25rem',
  letterSpacing: '0.05em',
  color: 'var(--color-text-main)',
  lineHeight: '1.1',
};

const logoSubStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  color: 'var(--color-primary)',
  fontWeight: 600,
  letterSpacing: '0.02em',
};

const navStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '24px',
};

const navLinkStyle: React.CSSProperties = {
  color: 'var(--color-text-muted)',
  fontFamily: 'var(--font-title)',
  fontWeight: 600,
  fontSize: '0.95rem',
  padding: '6px 4px',
  borderBottom: '2px solid transparent',
};

const activeLinkStyle: React.CSSProperties = {
  color: 'var(--color-primary)',
  fontFamily: 'var(--font-title)',
  fontWeight: 700,
  fontSize: '0.95rem',
  padding: '6px 4px',
  borderBottom: '2px solid var(--color-primary)',
};

const rightActionsStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '16px',
};

const searchTriggerStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '8px',
  padding: '6px 12px',
  backgroundColor: '#F5F3ED',
  border: '1px solid var(--color-border)',
  borderRadius: '20px',
  cursor: 'pointer',
  color: 'var(--color-text-muted)',
  transition: 'all 0.2s',
};

const searchPlaceholderTextStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  fontWeight: 500,
};

const authActionsStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '8px',
};

const profileBadgeStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '8px',
  padding: '4px 10px',
  borderRadius: '30px',
  backgroundColor: 'var(--color-secondary-light)',
  border: '1px solid rgba(42, 111, 96, 0.15)',
  textDecoration: 'none',
};

const avatarImageStyle: React.CSSProperties = {
  width: '36px',
  height: '36px',
  border: '2px solid rgba(255,255,255,0.9)',
};

const profileTextContainerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'flex-start',
};

const profileNameStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  fontWeight: 700,
  color: 'var(--color-secondary)',
  lineHeight: 1.1,
};

const adminBadgeStyle: React.CSSProperties = {
  fontSize: '0.6rem',
  fontWeight: 800,
  backgroundColor: 'var(--color-primary)',
  color: 'white',
  padding: '1px 4px',
  borderRadius: '4px',
  marginTop: '2px',
};

// Add CSS selectors to make nav hide on small screens
// We can embed a small media queries tag in the head or handle responsive navbar inside App layout
const responsiveStyle = `
@media (max-width: 800px) {
  header nav {
    display: none !important;
  }
}
`;
if (typeof document !== 'undefined') {
  const styleEl = document.createElement('style');
  styleEl.textContent = responsiveStyle;
  document.head.appendChild(styleEl);
}
