import React from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useDatabase } from '../context/DatabaseContext';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { DiceIcon, CalendarIcon, TrophyIcon, ChessPieceIcon, CardsIcon, MapPinIcon, ClockIcon, UserIcon, UsersIcon, CloseIcon, PlusIcon, CrownIcon } from '../components/Icons';
import { UserAvatar } from '../components/UserAvatar';

export const Home: React.FC = () => {
  const { state, joinEvent, leaveEvent } = useDatabase();
  const { currentUser } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  // Get next 3 upcoming events (sorted by date)
  const upcomingEvents = [...state.events]
    .filter(e => e.status === 'active' && new Date(e.date + 'T' + e.time) >= new Date())
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
    .slice(0, 3);

  // Get 4 most popular games based on session count
  const gameSessionCounts = state.boardGames.map(game => {
    const count = state.sessions.filter(s => s.gameId === game.id).length;
    return { ...game, sessionCount: count };
  });
  const popularGames = [...gameSessionCounts]
    .sort((a, b) => b.sessionCount - a.sessionCount)
    .slice(0, 4);

  // Get 3 recent sessions
  const recentSessions = [...state.sessions]
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
    .slice(0, 3);

  // Attendance click handler
  const handleAttendance = (eventId: string, isJoining: boolean) => {
    if (!currentUser) return;

    const event = state.events.find(e => e.id === eventId);
    if (!event || event.status !== 'active') {
      showToast('Este encontro já foi concluído.', 'warning');
      return;
    }

    if (isJoining) {
      joinEvent(eventId, currentUser.id);
      const gameName = state.boardGames.find(g => g.id === event?.gameId)?.name || 'Jogo';
      const isFull = event ? event.participantIds.length >= event.maxParticipants : false;
      
      if (isFull) {
        showToast(`Adicionado à lista de espera de ${gameName}!`, 'warning');
      } else {
        showToast(`Inscrição realizada em ${gameName}! 🎲`, 'success');
      }
    } else {
      leaveEvent(eventId, currentUser.id);
      showToast('Inscrição cancelada com sucesso.', 'info');
    }
  };

  return (
    <div style={pageStyle}>
      {/* Hero Section */}
      <section style={heroSectionStyle}>
        <div className="container" style={heroContainerStyle}>
          <div style={heroContentStyle}>
            <span style={{ ...heroTagStyle, display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
              <DiceIcon size={14} />
              <span>Clube Universitário de Jogos de Mesa</span>
            </span>
            <h1 style={heroHeadlineStyle}>
              Conecte-se com amigos e jogue os melhores jogos de mesa.
            </h1>
            <p style={heroDescriptionStyle}>
              O Tabula é o ponto de encontro da nossa comunidade. Aqui você pode agendar partidas, garantir seu lugar nas mesas, registrar seus resultados históricos e disputar o ranking amistoso do clube.
            </p>
            <div style={heroActionsStyle}>
              <button className="btn btn-primary btn-lg" onClick={() => navigate('/events')} style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
                <CalendarIcon size={18} />
                <span>Ver Próximos Eventos</span>
              </button>
              <button className="btn btn-outline btn-lg" onClick={() => navigate('/sessions')} style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
                <TrophyIcon size={18} />
                <span>Ver Histórico de Partidas</span>
              </button>
            </div>
          </div>
          <div style={heroGraphicContainerStyle}>
            <div style={heroGraphicBgStyle}>
              <div style={heroVisualBadgeStyle}>
                <CrownIcon size={18} style={{ color: 'var(--color-accent)' }} />
                <span>Community nights com ranking</span>
              </div>
              <div style={heroVisualPanelStyle}>
                <div style={heroVisualChipStyle}>
                  <CardsIcon size={18} style={{ color: 'var(--color-primary)' }} />
                  <span>Partidas e mesas</span>
                </div>
                <div style={heroVisualChipStyle}>
                  <ChessPieceIcon size={18} style={{ color: 'var(--color-secondary)' }} />
                  <span>Jogos e vitórias</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Community Statistics Grid */}
      <section className="container" style={statsSectionStyle}>
        <div className="card" style={statsCardStyle}>
          <div style={statItemStyle}>
            <span style={statNumberStyle}>{state.sessions.length}</span>
            <span style={statLabelStyle}>Partidas Registradas</span>
          </div>
          <div style={{ borderRight: '1px solid var(--color-border)', height: '50px' }} />
          <div style={statItemStyle}>
            <span style={statNumberStyle}>{state.users.length}</span>
            <span style={statLabelStyle}>Jogadores Ativos</span>
          </div>
          <div style={{ borderRight: '1px solid var(--color-border)', height: '50px' }} />
          <div style={statItemStyle}>
            <span style={statNumberStyle}>{state.boardGames.length}</span>
            <span style={statLabelStyle}>Jogos no Acervo</span>
          </div>
          <div style={{ borderRight: '1px solid var(--color-border)', height: '50px' }} />
          <div style={statItemStyle}>
            <span style={statNumberStyle}>
              {state.events.filter(e => e.status === 'active').length}
            </span>
            <span style={statLabelStyle}>Próximos Encontros</span>
          </div>
        </div>
      </section>

      {/* Main Grid: Left (Upcoming Events & Popular Games), Right (Recent Sessions) */}
      <section className="container" style={mainGridStyle}>
        
        {/* Left Side */}
        <div style={leftColumnStyle}>
          
          {/* Upcoming Events */}
          <div style={sectionHeaderContainerStyle}>
            <h2 style={{ ...sectionTitleStyle, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <CalendarIcon size={20} style={{ color: 'var(--color-primary)' }} />
              <span>Próximos Encontros Agendados</span>
            </h2>
            <Link to="/events" style={sectionLinkStyle}>Ver calendário completo →</Link>
          </div>
          
          <div style={eventsListStyle}>
            {upcomingEvents.length === 0 ? (
              <div className="card text-center" style={{ padding: '32px' }}>
                <p style={{ color: 'var(--color-text-muted)', fontWeight: 500 }}>Nenhum evento agendado no momento.</p>
                <button className="btn btn-outline btn-sm mt-md" onClick={() => navigate('/events')}>
                  Agendar Novo Encontro
                </button>
              </div>
            ) : (
              upcomingEvents.map(event => {
                const game = state.boardGames.find(g => g.id === event.gameId);
                const organizer = state.users.find(u => u.id === event.organizerId);
                const isParticipant = currentUser && event.participantIds.includes(currentUser.id);
                const isWaiting = currentUser && event.waitingListIds.includes(currentUser.id);
                const isFull = event.participantIds.length >= event.maxParticipants;

                return (
                  <div key={event.id} className="card card-hoverable" style={eventCardStyle}>
                    <div style={eventCardHeaderStyle}>
                      <div>
                        <span className="badge badge-secondary" style={{ marginBottom: '4px' }}>
                          {game?.category || 'Geral'}
                        </span>
                        <h3 style={eventGameTitleStyle}>{game?.name}</h3>
                      </div>
                      <img src={game?.coverUrl} alt={game?.name} style={eventGameThumbStyle} />
                    </div>
                    
                    <p style={eventDescStyle}>{event.description}</p>
                    
                    <div style={eventMetaGridStyle}>
                      <div style={eventMetaItemStyle}>
                        <MapPinIcon size={14} style={{ color: 'var(--color-primary)' }} />
                        <span><strong>Local:</strong> {event.location}</span>
                      </div>
                      <div style={eventMetaItemStyle}>
                        <ClockIcon size={14} style={{ color: 'var(--color-primary)' }} />
                        <span><strong>Quando:</strong> {new Date(event.date + 'T' + event.time).toLocaleDateString('pt-BR')} às {event.time}</span>
                      </div>
                      <div style={eventMetaItemStyle}>
                        <UserIcon size={14} style={{ color: 'var(--color-primary)' }} />
                        <span><strong>Organizador:</strong> {organizer?.name.split(' ')[0]}</span>
                      </div>
                      <div style={eventMetaItemStyle}>
                        <UsersIcon size={14} style={{ color: 'var(--color-primary)' }} />
                        <span><strong>Vagas:</strong> {event.participantIds.length}/{event.maxParticipants} 
                        {isFull && <span style={waitingListIndicatorStyle}>(Fila: {event.waitingListIds.length})</span>}</span>
                      </div>
                    </div>

                    <div style={eventActionsContainerStyle}>
                      {currentUser ? (
                        isParticipant ? (
                          <button className="btn btn-outline btn-sm" onClick={() => handleAttendance(event.id, false)} style={{ color: 'var(--color-danger)', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                            <CloseIcon size={14} />
                            <span>Cancelar Inscrição</span>
                          </button>
                        ) : isWaiting ? (
                          <button className="btn btn-outline btn-sm" onClick={() => handleAttendance(event.id, false)} style={{ color: 'var(--color-primary)', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                            <ClockIcon size={14} />
                            <span>Sair da Fila de Espera</span>
                          </button>
                        ) : (
                          <button className="btn btn-primary btn-sm" onClick={() => handleAttendance(event.id, true)} style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                            {isFull ? (
                              <>
                                <ClockIcon size={14} />
                                <span>Entrar na Fila de Espera</span>
                              </>
                            ) : (
                              <>
                                <PlusIcon size={14} />
                                <span>Participar da Mesa</span>
                              </>
                            )}
                          </button>
                        )
                      ) : (
                        <span style={loginToJoinTextStyle}>Conecte-se para participar</span>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>

          {/* Popular Games Grid */}
          <div style={{ ...sectionHeaderContainerStyle, marginTop: '40px' }}>
            <h2 style={sectionTitleStyle}>🔥 Jogos Mais Jogados</h2>
            <Link to="/games" style={sectionLinkStyle}>Ver todos os jogos →</Link>
          </div>
          
          <div style={popularGamesGridStyle}>
            {popularGames.map(game => (
              <div key={game.id} className="card card-hoverable" style={popularGameCardStyle} onClick={() => navigate(`/games?id=${game.id}`)}>
                <img src={game.coverUrl} alt={game.name} style={popularGameImgStyle} />
                <div style={popularGameInfoStyle}>
                  <h3 style={popularGameTitleStyle}>{game.name}</h3>
                  <span style={popularGameStatsStyle}>{game.sessionCount} partidas registradas</span>
                </div>
              </div>
            ))}
          </div>

        </div>

        {/* Right Side */}
        <div style={rightColumnStyle}>
          
          {/* Recent Sessions */}
          <div style={sectionHeaderContainerStyle}>
            <h2 style={{ ...sectionTitleStyle, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <TrophyIcon size={20} style={{ color: 'var(--color-accent)' }} />
              <span>Partidas Recentes</span>
            </h2>
            <Link to="/sessions" style={sectionLinkStyle}>Histórico →</Link>
          </div>

          <div style={sessionsListStyle}>
            {recentSessions.length === 0 ? (
              <div className="card text-center" style={{ padding: '24px' }}>
                <p style={{ color: 'var(--color-text-muted)' }}>Nenhuma partida jogada ainda.</p>
              </div>
            ) : (
              recentSessions.map(session => {
                const game = state.boardGames.find(g => g.id === session.gameId);
                const winner = state.users.find(u => u.id === session.winnerId);
                
                return (
                  <div key={session.id} className="card card-hoverable" style={sessionCardStyle} onClick={() => navigate(`/sessions/${session.id}`)}>
                    <div style={sessionCardHeaderStyle}>
                      <div>
                        <span style={sessionDateStyle}>{new Date(session.date).toLocaleDateString('pt-BR')}</span>
                        <h4 style={sessionGameTitleStyle}>{game?.name}</h4>
                      </div>
                      <span className="badge badge-primary">
                        {session.duration} min
                      </span>
                    </div>
                    
                    <p style={sessionNotesStyle}>"{session.notes.substring(0, 100)}..."</p>
                    
                    <div style={sessionWinnerStyle}>
                      <CrownIcon size={14} style={{ color: 'var(--color-accent)' }} />
                      <span>Vencedor:</span>
                      <strong style={{ color: 'var(--color-primary)', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                        <UserAvatar user={winner} size={18} style={{ border: '1px solid var(--color-border)' }} />
                        <span>{winner?.name}</span>
                      </strong>
                    </div>
                  </div>
                );
              })
            )}
          </div>

        </div>

      </section>
    </div>
  );
};

// Home styling definitions
const pageStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '16px',
};

const heroSectionStyle: React.CSSProperties = {
  background: 'linear-gradient(135deg, #FAF6F0 0%, #FDFBF8 100%)',
  padding: '60px 0',
  borderBottom: '1px solid var(--color-border)',
};

const heroContainerStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.2fr 0.8fr',
  alignItems: 'center',
  gap: '40px',
};

const heroContentStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'flex-start',
  gap: '16px',
};

const heroTagStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  fontWeight: 700,
  backgroundColor: 'var(--color-primary-light)',
  color: 'var(--color-primary)',
  padding: '6px 12px',
  borderRadius: '20px',
  letterSpacing: '0.02em',
};

const heroHeadlineStyle: React.CSSProperties = {
  fontSize: '3rem',
  fontWeight: 800,
  color: 'var(--color-text-main)',
  lineHeight: '1.15',
};

const heroDescriptionStyle: React.CSSProperties = {
  fontSize: '1.1rem',
  color: 'var(--color-text-muted)',
  lineHeight: '1.6',
};

const heroActionsStyle: React.CSSProperties = {
  display: 'flex',
  gap: '16px',
  marginTop: '12px',
  flexWrap: 'wrap',
};

const heroGraphicContainerStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'center',
  alignItems: 'center',
};

const heroGraphicBgStyle: React.CSSProperties = {
  width: '100%',
  maxWidth: '420px',
  minHeight: '340px',
  borderRadius: '32px',
  position: 'relative',
  overflow: 'hidden',
  boxShadow: 'var(--shadow-lg)',
  border: '1px solid rgba(255, 255, 255, 0.35)',
  backgroundImage:
    'linear-gradient(145deg, rgba(24, 30, 58, 0.72), rgba(79, 70, 229, 0.28)), url("https://images.unsplash.com/photo-1511512578047-dfb367046420?auto=format&fit=crop&w=900&q=80")',
  backgroundSize: 'cover',
  backgroundPosition: 'center',
  display: 'flex',
  flexDirection: 'column',
  justifyContent: 'space-between',
  padding: '18px',
};

const heroVisualBadgeStyle: React.CSSProperties = {
  alignSelf: 'flex-start',
  display: 'inline-flex',
  alignItems: 'center',
  gap: '8px',
  background: 'rgba(255, 255, 255, 0.95)',
  color: 'var(--color-text-main)',
  padding: '8px 12px',
  borderRadius: '999px',
  fontSize: '0.92rem',
  fontWeight: 700,
  boxShadow: '0 12px 24px rgba(15, 23, 42, 0.15)',
};

const heroVisualPanelStyle: React.CSSProperties = {
  display: 'grid',
  gap: '10px',
  alignSelf: 'flex-end',
  width: '100%',
  maxWidth: '280px',
};

const heroVisualChipStyle: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: '8px',
  padding: '10px 12px',
  borderRadius: '16px',
  background: 'rgba(255, 255, 255, 0.94)',
  color: 'var(--color-text-main)',
  fontWeight: 700,
  boxShadow: '0 14px 30px rgba(15, 23, 42, 0.18)',
};

const statsSectionStyle: React.CSSProperties = {
  marginTop: '-30px',
  zIndex: 10,
};

const statsCardStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-around',
  alignItems: 'center',
  padding: '24px 16px',
  backgroundColor: 'white',
};

const statItemStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  textAlign: 'center',
  flexGrow: 1,
};

const statNumberStyle: React.CSSProperties = {
  fontFamily: 'var(--font-title)',
  fontSize: '2rem',
  fontWeight: 800,
  color: 'var(--color-secondary)',
};

const statLabelStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  color: 'var(--color-text-muted)',
  fontWeight: 600,
  marginTop: '4px',
};

const mainGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.3fr 0.7fr',
  gap: '40px',
  marginTop: '48px',
};

const leftColumnStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '20px',
};

const rightColumnStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '20px',
};

const sectionHeaderContainerStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'baseline',
  borderBottom: '2px solid var(--color-border)',
  paddingBottom: '8px',
  marginBottom: '8px',
};

const sectionTitleStyle: React.CSSProperties = {
  fontSize: '1.25rem',
  color: 'var(--color-text-main)',
  fontWeight: 800,
};

const sectionLinkStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  fontWeight: 600,
  color: 'var(--color-primary)',
};

const eventsListStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '16px',
};

const eventCardStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '12px',
};

const eventCardHeaderStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'flex-start',
};

const eventGameTitleStyle: React.CSSProperties = {
  fontSize: '1.2rem',
  fontWeight: 700,
};

const eventGameThumbStyle: React.CSSProperties = {
  width: '50px',
  height: '50px',
  borderRadius: '8px',
  objectFit: 'cover',
  boxShadow: 'var(--shadow-sm)',
};

const eventDescStyle: React.CSSProperties = {
  color: 'var(--color-text-muted)',
  fontSize: '0.9rem',
};

const eventMetaGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1fr 1fr',
  gap: '8px',
  fontSize: '0.85rem',
  backgroundColor: '#FAF9F6',
  padding: '12px',
  borderRadius: '8px',
  border: '1px solid var(--color-border)',
};

const eventMetaItemStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '6px',
};

const waitingListIndicatorStyle: React.CSSProperties = {
  color: 'var(--color-danger)',
  marginLeft: '4px',
  fontWeight: 'bold',
};

const eventActionsContainerStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'flex-end',
  marginTop: '4px',
};

const loginToJoinTextStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  color: 'var(--color-text-light)',
  fontStyle: 'italic',
};

const popularGamesGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(2, 1fr)',
  gap: '16px',
};

const popularGameCardStyle: React.CSSProperties = {
  display: 'flex',
  gap: '12px',
  padding: '12px',
  cursor: 'pointer',
  alignItems: 'center',
};

const popularGameImgStyle: React.CSSProperties = {
  width: '64px',
  height: '64px',
  borderRadius: '8px',
  objectFit: 'cover',
};

const popularGameInfoStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '2px',
};

const popularGameTitleStyle: React.CSSProperties = {
  fontSize: '1rem',
  fontWeight: 700,
};

const popularGameStatsStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  color: 'var(--color-text-muted)',
};

const sessionsListStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '16px',
};

const sessionCardStyle: React.CSSProperties = {
  cursor: 'pointer',
  display: 'flex',
  flexDirection: 'column',
  gap: '8px',
};

const sessionCardHeaderStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'flex-start',
};

const sessionDateStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  color: 'var(--color-text-light)',
  fontWeight: 600,
};

const sessionGameTitleStyle: React.CSSProperties = {
  fontSize: '1.05rem',
  fontWeight: 700,
  marginTop: '2px',
};

const sessionNotesStyle: React.CSSProperties = {
  fontSize: '0.85rem',
  color: 'var(--color-text-muted)',
  fontStyle: 'italic',
};

const sessionWinnerStyle: React.CSSProperties = {
  display: 'flex',
  gap: '8px',
  fontSize: '0.85rem',
  alignItems: 'center',
  marginTop: '4px',
  borderTop: '1px dashed var(--color-border)',
  paddingTop: '8px',
};

// Add responsive and animation styles
const globalResponseStyle = `
@keyframes float {
  0% { transform: translateY(0px) rotate(0deg); }
  50% { transform: translateY(-8px) rotate(5deg); }
  100% { transform: translateY(0px) rotate(0deg); }
}
@media (max-width: 900px) {
  .hero-container-responsive {
    grid-template-columns: 1fr !important;
    text-align: center;
  }
  .hero-container-responsive div {
    align-items: center !important;
  }
  .main-grid-responsive {
    grid-template-columns: 1fr !important;
  }
}
`;

if (typeof document !== 'undefined') {
  const styleEl = document.createElement('style');
  styleEl.textContent = globalResponseStyle;
  document.head.appendChild(styleEl);
}
