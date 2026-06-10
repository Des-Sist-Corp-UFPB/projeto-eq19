import React from 'react';
import { Link } from 'react-router-dom';
import { DiceIcon, InstagramIcon, MessageSquareIcon, SparklesIcon, HandshakeIcon, PuzzleIcon, TrophyIcon, MapPinIcon, ClockIcon } from './Icons';

export const Footer: React.FC = () => {
  return (
    <footer style={footerStyle} className="no-print">
      <div className="container" style={footerContainerStyle}>
        
        {/* About Club column */}
        <div style={footerColumnStyle}>
          <div style={{ ...logoStyle, display: 'flex', alignItems: 'center', gap: '8px' }}>
            <DiceIcon size={24} style={{ color: 'var(--color-primary)' }} />
            <span>TABULA</span>
          </div>
          <p style={descriptionStyle}>
            O clube oficial de jogos de mesa da nossa universidade. Unindo estudantes de todos os cursos através de dados, cartas, tabuleiros e muitas risadas desde 2026.
          </p>
          <div style={socialsContainerStyle}>
            <a href="#instagram" style={{ ...socialIconStyle, display: 'inline-flex', alignItems: 'center', gap: '6px' }} title="Instagram">
              <InstagramIcon size={14} />
              <span>Instagram</span>
            </a>
            <a href="#discord" style={{ ...socialIconStyle, display: 'inline-flex', alignItems: 'center', gap: '6px' }} title="Discord">
              <MessageSquareIcon size={14} />
              <span>Discord</span>
            </a>
          </div>
        </div>

        {/* Community Guidelines column */}
        <div style={footerColumnStyle}>
          <h4 style={columnTitleStyle}>Diretrizes do Clube</h4>
          <ul style={listStyle}>
            <li style={{ ...listItemStyle, display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
              <span style={{ marginTop: '2px', color: 'var(--color-primary)' }}><SparklesIcon size={16} /></span>
              <span><strong>Diversão acima de tudo:</strong> Acolher novos jogadores e explicar regras com paciência.</span>
            </li>
            <li style={{ ...listItemStyle, display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
              <span style={{ marginTop: '2px', color: 'var(--color-primary)' }}><HandshakeIcon size={16} /></span>
              <span><strong>Inclusão:</strong> Respeitar a todos os membros independente de curso, nível de habilidade ou experiência.</span>
            </li>
            <li style={{ ...listItemStyle, display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
              <span style={{ marginTop: '2px', color: 'var(--color-primary)' }}><PuzzleIcon size={16} /></span>
              <span><strong>Cuidado com os Componentes:</strong> Evitar bebidas/comidas na mesa. Cuidar de cartas e meeples alheios.</span>
            </li>
            <li style={{ ...listItemStyle, display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
              <span style={{ marginTop: '2px', color: 'var(--color-primary)' }}><TrophyIcon size={16} /></span>
              <span><strong>Jogo Limpo:</strong> Competir de forma saudável. Vitórias são legais, mas novas amizades são melhores.</span>
            </li>
          </ul>
        </div>

        {/* Contacts column */}
        <div style={footerColumnStyle}>
          <h4 style={columnTitleStyle}>Contato & Local</h4>
          <ul style={listStyle}>
            <li style={{ ...listItemStyle, display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
              <span style={{ marginTop: '2px', color: 'var(--color-primary)' }}><MapPinIcon size={16} /></span>
              <span><strong>Local Frequente:</strong> Centro de Vivência (Bloco C) & Biblioteca Universitária.</span>
            </li>
            <li style={{ ...listItemStyle, display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
              <span style={{ marginTop: '2px', color: 'var(--color-primary)' }}><ClockIcon size={16} /></span>
              <span><strong>Encontros Gerais:</strong> Terças e Quintas, a partir das 17:30.</span>
            </li>
            <li style={listItemStyle}>📧 <strong>E-mail:</strong> <a href="mailto:caua.brito@dcx.ufpb.br" style={{ color: 'var(--color-primary-hover)' }}>caua.brito@dcx.ufpb.br</a></li>
            <li style={listItemStyle}>🏢 <strong>Coordenação:</strong> DCE - Diretório Central dos Estudantes, Sala 102.</li>
          </ul>
        </div>

      </div>

      <div style={footerBottomStyle}>
        <div className="container" style={footerBottomContainerStyle}>
          <span>© 2026 Tabula Boardgame Club. Desenvolvido para a Comunidade Universitária.</span>
          <div style={bottomLinksStyle}>
            <Link to="/games" style={bottomLinkStyle}>Acervo de Jogos</Link>
            <Link to="/events" style={bottomLinkStyle}>Calendário de Eventos</Link>
            <Link to="/stats" style={bottomLinkStyle}>Estatísticas Globais</Link>
          </div>
        </div>
      </div>
    </footer>
  );
};

// Footer CSS Styles
const footerStyle: React.CSSProperties = {
  backgroundColor: '#FAF9F5',
  borderTop: '1px solid var(--color-border)',
  color: 'var(--color-text-muted)',
  padding: '48px 0 0 0',
  marginTop: '64px',
  fontSize: '0.9rem',
};

const footerContainerStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.2fr 1fr 1fr',
  gap: '40px',
  paddingBottom: '32px',
};

const footerColumnStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '16px',
};

const logoStyle: React.CSSProperties = {
  fontFamily: 'var(--font-title)',
  fontWeight: 800,
  fontSize: '1.4rem',
  color: 'var(--color-text-main)',
  letterSpacing: '0.03em',
};

const descriptionStyle: React.CSSProperties = {
  lineHeight: '1.6',
  color: 'var(--color-text-muted)',
};

const socialsContainerStyle: React.CSSProperties = {
  display: 'flex',
  gap: '16px',
  marginTop: '8px',
};

const socialIconStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  fontWeight: 600,
  color: 'var(--color-text-main)',
  backgroundColor: 'white',
  padding: '6px 12px',
  borderRadius: '20px',
  border: '1px solid var(--color-border)',
};

const columnTitleStyle: React.CSSProperties = {
  fontFamily: 'var(--font-title)',
  fontSize: '1rem',
  color: 'var(--color-text-main)',
  fontWeight: 700,
  borderBottom: '2px solid var(--color-border)',
  paddingBottom: '8px',
  width: 'fit-content',
};

const listStyle: React.CSSProperties = {
  listStyleType: 'none',
  padding: '0',
  display: 'flex',
  flexDirection: 'column',
  gap: '10px',
};

const listItemStyle: React.CSSProperties = {
  lineHeight: '1.4',
  color: 'var(--color-text-muted)',
};

const footerBottomStyle: React.CSSProperties = {
  borderTop: '1px solid var(--color-border)',
  padding: '24px 0',
  backgroundColor: '#F5F3ED',
  fontSize: '0.8rem',
};

const footerBottomContainerStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  flexWrap: 'wrap',
  gap: '12px',
};

const bottomLinksStyle: React.CSSProperties = {
  display: 'flex',
  gap: '20px',
};

const bottomLinkStyle: React.CSSProperties = {
  color: 'var(--color-text-muted)',
  fontWeight: 500,
};

// Inject footer mobile response
const responsiveStyle = `
@media (max-width: 800px) {
  footer > .container {
    grid-template-columns: 1fr !important;
    gap: 32px !important;
  }
}
`;
if (typeof document !== 'undefined') {
  const styleEl = document.createElement('style');
  styleEl.textContent = responsiveStyle;
  document.head.appendChild(styleEl);
}
