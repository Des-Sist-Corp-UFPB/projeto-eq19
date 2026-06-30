import React from 'react';
import { Link, useParams } from 'react-router-dom';
import { useDatabase } from '../context/DatabaseContext';
import { CrownIcon } from '../components/Icons';

export const GameDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { state } = useDatabase();

  const game = state.boardGames.find(item => item.id === id);

  if (!game) {
    return (
      <div className="container" style={{ paddingTop: '32px' }}>
        <div className="card" style={{ padding: '32px', textAlign: 'center' }}>
          <h1 style={{ fontSize: '1.6rem', marginBottom: '10px' }}>Jogo não encontrado</h1>
          <p style={{ color: 'var(--color-text-muted)' }}>Este jogo não está mais disponível no acervo.</p>
          <Link to="/games" className="btn btn-primary mt-md">Voltar para o acervo</Link>
        </div>
      </div>
    );
  }

  const sessions = state.sessions.filter(session => session.gameId === game.id);
  const events = state.events.filter(event => event.gameId === game.id && event.status === 'active');
  const winnerCount = sessions.filter(session => session.winnerId).length;
  const playerCount = new Set(sessions.flatMap(session => session.participantIds)).size;

  return (
    <div className="container" style={{ paddingTop: '24px' }}>
      <div style={backLinkStyle}>
        <Link to="/games" className="btn btn-outline btn-sm">← Voltar ao acervo</Link>
      </div>

      <div className="card" style={heroCardStyle}>
        <img src={game.coverUrl} alt={game.name} style={coverStyle} />
        <div style={heroCopyStyle}>
          <span className="badge badge-primary" style={{ marginBottom: '8px' }}>{game.category}</span>
          <h1 style={titleStyle}>{game.name}</h1>
          <p style={descriptionStyle}>{game.description}</p>

        </div>
      </div>

      <div style={metricsGridStyle}>
        <div className="card" style={metricCardStyle}>
          <span style={metricLabelStyle}>Partidas registradas</span>
          <strong style={metricValueStyle}>{sessions.length}</strong>
        </div>
        <div className="card" style={metricCardStyle}>
          <span style={metricLabelStyle}>Jogadores distintos</span>
          <strong style={metricValueStyle}>{playerCount}</strong>
        </div>
        <div className="card" style={metricCardStyle}>
          <span style={metricLabelStyle}>Vitórias gravadas</span>
          <strong style={metricValueStyle}>{winnerCount}</strong>
        </div>
        <div className="card" style={metricCardStyle}>
          <span style={metricLabelStyle}>Eventos ativos</span>
          <strong style={metricValueStyle}>{events.length}</strong>
        </div>
      </div>

      <div style={contentGridStyle}>
        <section className="card" style={panelStyle}>
          <h2 style={panelTitleStyle}>Ficha técnica</h2>
          <ul style={infoListStyle}>
            <li><strong>Jogadores:</strong> {game.minPlayers} a {game.maxPlayers}</li>
            <li><strong>Duração média:</strong> {game.avgPlayTime} minutos</li>
            <li><strong>Complexidade:</strong> {game.complexity.toFixed(1)} / 5</li>
            <li><strong>Categoria:</strong> {game.category}</li>
          </ul>
        </section>

        <section className="card" style={panelStyle}>
          <h2 style={panelTitleStyle}>Histórico recente</h2>
          {sessions.length === 0 ? (
            <p style={mutedStyle}>Ainda não há partidas registradas para este jogo.</p>
          ) : (
            <div style={sessionListStyle}>
              {sessions.slice(0, 5).map(session => {
                const winner = state.users.find(user => user.id === session.winnerId);
                return (
                  <Link key={session.id} to={`/sessions/${session.id}`} style={sessionItemStyle}>
                    <strong>{new Date(session.date).toLocaleDateString('pt-BR')}</strong>
                    <span>{session.location}</span>
                    <span style={{ ...winnerStyle, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <CrownIcon size={14} style={{ color: 'var(--color-accent)' }} />
                      <span>{winner?.name || 'Vencedor não registrado'}</span>
                    </span>
                  </Link>
                );
              })}
            </div>
          )}
        </section>
      </div>
    </div>
  );
};

const backLinkStyle: React.CSSProperties = {
  marginBottom: '16px'
};

const heroCardStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '260px 1fr',
  gap: '24px',
  padding: '24px',
  alignItems: 'center'
};

const coverStyle: React.CSSProperties = {
  width: '100%',
  borderRadius: '18px',
  objectFit: 'cover',
  aspectRatio: '4 / 5',
  boxShadow: 'var(--shadow-md)'
};

const heroCopyStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column'
};

const titleStyle: React.CSSProperties = {
  fontSize: '2rem',
  fontFamily: 'var(--font-title)',
  marginBottom: '10px'
};

const descriptionStyle: React.CSSProperties = {
  color: 'var(--color-text-muted)',
  lineHeight: 1.6,
  marginBottom: '16px'
};



const metricsGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(4, 1fr)',
  gap: '16px',
  marginTop: '20px'
};

const metricCardStyle: React.CSSProperties = {
  padding: '18px'
};

const metricLabelStyle: React.CSSProperties = {
  color: 'var(--color-text-muted)',
  fontSize: '0.85rem',
  textTransform: 'uppercase',
  letterSpacing: '0.04em'
};

const metricValueStyle: React.CSSProperties = {
  display: 'block',
  fontSize: '1.6rem',
  fontWeight: 800,
  color: 'var(--color-text-main)',
  marginTop: '6px'
};

const contentGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1fr 1fr',
  gap: '20px',
  marginTop: '20px'
};

const panelStyle: React.CSSProperties = {
  padding: '20px'
};

const panelTitleStyle: React.CSSProperties = {
  fontSize: '1rem',
  fontFamily: 'var(--font-title)',
  marginBottom: '12px'
};

const infoListStyle: React.CSSProperties = {
  listStyle: 'none',
  display: 'flex',
  flexDirection: 'column',
  gap: '8px',
  color: 'var(--color-text-muted)'
};

const mutedStyle: React.CSSProperties = {
  color: 'var(--color-text-muted)',
  fontSize: '0.95rem'
};

const sessionListStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '10px'
};

const sessionItemStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '4px',
  padding: '10px 12px',
  borderRadius: '10px',
  backgroundColor: '#FAF9F6',
  border: '1px solid var(--color-border)',
  textDecoration: 'none',
  color: 'inherit'
};

const winnerStyle: React.CSSProperties = {
  color: 'var(--color-secondary)',
  fontSize: '0.85rem'
};

export default GameDetails;
