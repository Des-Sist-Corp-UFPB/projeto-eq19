import React, { useState } from 'react';
import { useDatabase } from '../context/DatabaseContext';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import type { Event } from '../types';
import { PlusIcon, CalendarIcon, MapPinIcon, ClockIcon, UsersIcon, CrownIcon, TrophyIcon, CloseIcon } from '../components/Icons';
import { UserAvatar } from '../components/UserAvatar';
import { ApiError, generateEventDraft, refineEventDraft } from '../services/api';
import type { AiEventDraftResponse } from '../services/api';

const eventErrorMessage = (error: unknown) => {
  if (!(error instanceof ApiError)) return 'Não foi possível concluir a operação agora.';
  if (error.status === 401) return 'Sua sessão expirou. Entre novamente.';
  if (error.status === 403) return 'Você não tem permissão para realizar esta operação.';
  if (error.status === 404) return 'O evento não foi encontrado.';
  if (error.status === 409) return 'O evento foi alterado por outra operação. Atualize e tente novamente.';
  if (error.status === 422) return 'Confira os dados informados e tente novamente.';
  return 'Não foi possível concluir a operação agora.';
};

export const Events: React.FC = () => {
  const { state, addEvent, joinEvent, leaveEvent, completeEvent } = useDatabase();
  const { currentUser, isAdmin } = useAuth();
  const { showToast } = useToast();

  // Calendar State (Default to June 2026)
  const [calendarDate, setCalendarDate] = useState(new Date(2026, 5, 9)); // Year 2026, Month June (5 is index)
  const [selectedDayEvents, setSelectedDayEvents] = useState<Event[] | null>(null);
  const [selectedDayNumber, setSelectedDayNumber] = useState<number | null>(null);

  // Modals state
  const [isScheduleModalOpen, setIsScheduleModalOpen] = useState(false);
  const [isCompleteModalOpen, setIsCompleteModalOpen] = useState(false);
  const [completingEvent, setCompletingEvent] = useState<Event | null>(null);

  // Schedule Event Form State
  const [gameId, setGameId] = useState('');
  const [date, setDate] = useState('2026-06-12');
  const [time, setTime] = useState('18:00');
  const [location, setLocation] = useState('Vivência do Bloco C');
  const [maxParticipants, setMaxParticipants] = useState(4);
  const [description, setDescription] = useState('');
  const [aiPrompt, setAiPrompt] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState('');
  const [aiWarnings, setAiWarnings] = useState<string[]>([]);
  const [refinementInstruction, setRefinementInstruction] = useState('');
  const [hasAiDraft, setHasAiDraft] = useState(false);

  // Complete Event Form State
  const [winnerId, setWinnerId] = useState('');
  const [duration, setDuration] = useState(60);
  const [notes, setNotes] = useState('');
  const [initialComment, setInitialComment] = useState('');
  const [photoUrl, setPhotoUrl] = useState('');

  // 1. Calendar grid helpers
  const year = calendarDate.getFullYear();
  const month = calendarDate.getMonth();
  const monthName = calendarDate.toLocaleString('pt-BR', { month: 'long' });

  const firstDayOfMonth = new Date(year, month, 1).getDay(); // 0 is Sunday, 1 is Monday...
  const totalDaysInMonth = new Date(year, month + 1, 0).getDate();

  // Adjust firstDayOfMonth index to start week on Monday
  // Mon=0, Tue=1, Wed=2, Thu=3, Fri=4, Sat=5, Sun=6
  const adjustedFirstDay = firstDayOfMonth === 0 ? 6 : firstDayOfMonth - 1;

  const daysGrid: (number | null)[] = [];
  for (let i = 0; i < adjustedFirstDay; i++) {
    daysGrid.push(null);
  }
  for (let d = 1; d <= totalDaysInMonth; d++) {
    daysGrid.push(d);
  }

  // Get events on a specific day
  const getEventsForDay = (day: number) => {
    const formattedMonth = String(month + 1).padStart(2, '0');
    const formattedDay = String(day).padStart(2, '0');
    const dateStr = `${year}-${formattedMonth}-${formattedDay}`;
    return state.events.filter(e => e.date === dateStr && e.status === 'active');
  };

  const handleDayClick = (day: number) => {
    const dayEvents = getEventsForDay(day);
    setSelectedDayNumber(day);
    setSelectedDayEvents(dayEvents.length > 0 ? dayEvents : null);
  };

  const handleMonthChange = (offset: number) => {
    setCalendarDate(new Date(year, month + offset, 1));
    setSelectedDayEvents(null);
    setSelectedDayNumber(null);
  };

  // 2. Attendance triggers
  const handleAttendance = async (eventId: string, isJoining: boolean) => {
    if (!currentUser) return;

    const event = state.events.find(e => e.id === eventId);
    if (!event || event.status !== 'active') {
      showToast('Este encontro já foi concluído e não aceita novas inscrições.', 'warning');
      return;
    }

    if (isJoining) {
      try {
        const waitlisted = await joinEvent(eventId, currentUser.id);
        const gameName = state.boardGames.find(g => g.id === event?.gameId)?.name || 'Jogo';
        showToast(
          waitlisted
            ? `Você foi adicionado à lista de espera de ${gameName}!`
            : `Inscrição confirmada na mesa de ${gameName}! 🎲`,
          waitlisted ? 'warning' : 'success',
        );
      } catch (error) {
        showToast(eventErrorMessage(error), 'error');
      }
    } else {
      try {
        await leaveEvent(eventId, currentUser.id);
        showToast('Participação cancelada.', 'info');
      } catch (error) {
        showToast(eventErrorMessage(error), 'error');
      }
    }
  };

  // 3. Create event submit
  const handleScheduleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!gameId) {
      alert('Selecione um jogo.');
      return;
    }

    try {
      await addEvent({
        gameId,
        date,
        time,
        location,
        maxParticipants,
        description
      }, currentUser?.id || '');

      const gameName = state.boardGames.find(g => g.id === gameId)?.name || 'Jogo';
      showToast(`Evento de ${gameName} agendado com sucesso!`, 'success');
      setIsScheduleModalOpen(false);
      resetScheduleForm();
    } catch (error) {
      showToast(eventErrorMessage(error), 'error');
    }
  };

  const resetScheduleForm = () => {
    setGameId('');
    setDate('2026-06-12');
    setTime('18:00');
    setLocation('Vivência do Bloco C');
    setMaxParticipants(4);
    setDescription('');
    setAiPrompt('');
    setAiError('');
    setAiWarnings([]);
    setRefinementInstruction('');
    setHasAiDraft(false);
  };

  const applyAiDraft = (draft: AiEventDraftResponse) => {
    setGameId(draft.gameId);
    setDate(draft.date);
    setTime(draft.time);
    setLocation(draft.location);
    setMaxParticipants(draft.maxParticipants);
    setDescription(draft.description);
    setAiWarnings(draft.warnings);
  };

  const handleGenerateDraft = async () => {
    if (aiLoading || aiPrompt.trim().length < 5) return;
    setAiLoading(true);
    setAiError('');
    setAiWarnings([]);
    try {
      const draft = await generateEventDraft(aiPrompt);
      applyAiDraft(draft);
      setHasAiDraft(true);
    } catch (error) {
      let message = 'Não foi possível gerar o rascunho agora. Seus dados atuais foram mantidos.';
      if (error instanceof ApiError) {
        if (error.status === 401) message = 'Sua sessão expirou. Entre novamente para usar o assistente.';
        else if (error.status === 422) message = 'A IA não conseguiu montar um rascunho válido. Tente descrever o encontro com mais detalhes.';
        else if (error.status === 429) message = 'O limite temporário de gerações com IA foi atingido. Tente novamente mais tarde.';
        else if (error.status === 502 || error.status === 503) message = 'O assistente de IA está temporariamente indisponível. Tente novamente mais tarde.';
      }
      setAiError(message);
    } finally {
      setAiLoading(false);
    }
  };

  const handleRefineDraft = async () => {
    if (aiLoading || !hasAiDraft || refinementInstruction.trim().length < 3) return;
    const gameName = state.boardGames.find(game => game.id === gameId)?.name ?? '';
    setAiLoading(true);
    setAiError('');
    try {
      const draft = await refineEventDraft(refinementInstruction, {
        gameId, gameName, date, time, location, maxParticipants, description, warnings: aiWarnings,
      });
      applyAiDraft(draft);
      setRefinementInstruction('');
    } catch (error) {
      let message = 'Não foi possível refinar o rascunho agora. Seus dados atuais foram mantidos.';
      if (error instanceof ApiError) {
        if (error.status === 401) message = 'Sua sessão expirou. Entre novamente para usar o assistente.';
        else if (error.status === 422) message = 'A IA não conseguiu aplicar essa alteração. O rascunho foi mantido.';
        else if (error.status === 429) message = 'O limite temporário de gerações com IA foi atingido. Tente novamente mais tarde.';
        else if (error.status === 502 || error.status === 503) message = 'O assistente de IA está temporariamente indisponível. Tente novamente mais tarde.';
      }
      setAiError(message);
    } finally {
      setAiLoading(false);
    }
  };

  // 4. Complete Event Workflow triggers
  const handleOpenCompleteModal = (event: Event) => {
    setCompletingEvent(event);
    setWinnerId('');
    setDuration(60);
    setNotes('');
    setInitialComment('');
    setPhotoUrl('');
    setIsCompleteModalOpen(true);
  };

  const handleCompleteSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!completingEvent) return;
    if (!winnerId) {
      alert('Por favor, selecione o vencedor.');
      return;
    }
    if (!notes.trim()) {
      alert('Por favor, adicione notas do relato da partida.');
      return;
    }

    try {
      await completeEvent(
        completingEvent.id,
        winnerId,
        duration,
        notes,
        initialComment.trim() || undefined,
        photoUrl.trim() || undefined
      );
      const gameName = state.boardGames.find(g => g.id === completingEvent.gameId)?.name || 'Jogo';
      showToast(`Partida de ${gameName} finalizada e arquivada! 🏆`, 'success');
      setIsCompleteModalOpen(false);
      setCompletingEvent(null);
    } catch (error) {
      showToast(eventErrorMessage(error), 'error');
    }
    setSelectedDayEvents(null); // Clear day filter to refresh lists
    setSelectedDayNumber(null);
  };

  // List of active upcoming events (chronological order)
  const allActiveEvents = [...state.events]
    .filter(e => e.status === 'active')
    .sort((a, b) => new Date(a.date + 'T' + a.time).getTime() - new Date(b.date + 'T' + b.time).getTime());

  // Filter lists if day selected in calendar
  const displayedEvents = selectedDayEvents ? selectedDayEvents : allActiveEvents;

  return (
    <div className="container" style={{ paddingTop: '24px' }}>
      
      {/* Header */}
      <div style={headerSectionStyle}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <CalendarIcon size={32} style={{ color: 'var(--color-primary)' }} />
          <div>
            <h1 style={{ fontSize: '2rem', marginBottom: '4px' }}>Calendário & Agendamento</h1>
            <p style={{ color: 'var(--color-text-muted)' }}>Agende novas jogatinas ou participe das mesas montadas pela galera.</p>
          </div>
        </div>
        {currentUser && (
          <button className="btn btn-primary" onClick={() => setIsScheduleModalOpen(true)}>
            <PlusIcon size={18} /> Agendar Novo Encontro
          </button>
        )}
      </div>

      {/* Main Grid: Left Calendar Widget, Right Events List */}
      <div style={eventPageGridStyle}>
        
        {/* Left column: Calendar */}
        <div style={calendarColStyle}>
          <div className="card" style={{ padding: '20px' }}>
            
            {/* Month Header */}
            <div style={calendarMonthHeaderStyle}>
              <button className="btn btn-outline btn-sm" onClick={() => handleMonthChange(-1)}>◀</button>
              <h2 style={calendarMonthNameStyle}>
                {monthName.charAt(0).toUpperCase() + monthName.slice(1)} de {year}
              </h2>
              <button className="btn btn-outline btn-sm" onClick={() => handleMonthChange(1)}>▶</button>
            </div>

            {/* Weekdays row */}
            <div style={weekdaysGridStyle}>
              <span>Seg</span>
              <span>Ter</span>
              <span>Qua</span>
              <span>Qui</span>
              <span>Sex</span>
              <span>Sáb</span>
              <span>Dom</span>
            </div>

            {/* Days grid */}
            <div style={daysGridStyle}>
              {daysGrid.map((day, idx) => {
                if (day === null) {
                  return <div key={`empty-${idx}`} style={calendarCellEmptyStyle} />;
                }

                const dayEvents = getEventsForDay(day);
                const hasEvents = dayEvents.length > 0;
                const isSelected = selectedDayNumber === day;

                return (
                  <div
                    key={`day-${day}`}
                    onClick={() => handleDayClick(day)}
                    style={{
                      ...calendarCellStyle,
                      backgroundColor: isSelected 
                        ? 'var(--color-primary-light)' 
                        : hasEvents ? 'var(--color-secondary-light)' : 'white',
                      borderColor: isSelected 
                        ? 'var(--color-primary)' 
                        : hasEvents ? 'var(--color-secondary)' : 'var(--color-border)',
                      fontWeight: (hasEvents || isSelected) ? 700 : 400
                    }}
                  >
                    <span>{day}</span>
                    {hasEvents && (
                      <span style={calendarDotStyle} />
                    )}
                  </div>
                );
              })}
            </div>

            {/* Calendar helper */}
            <div style={legendStyle}>
              <div style={legendItemStyle}>
                <span style={{ ...legendDotStyle, backgroundColor: 'var(--color-secondary)' }} />
                <span>Dia com evento ativo</span>
              </div>
              {selectedDayNumber && (
                <button
                  className="btn btn-text btn-sm"
                  style={{ marginLeft: 'auto', padding: '2px 8px', fontSize: '0.75rem' }}
                  onClick={() => { setSelectedDayEvents(null); setSelectedDayNumber(null); }}
                >
                  Limpar Filtro de Dia
                </button>
              )}
            </div>

          </div>
        </div>

        {/* Right column: Event Cards List */}
        <div style={eventsListColStyle}>
          
          <h2 style={listHeaderTitleStyle}>
            {selectedDayNumber 
              ? `Eventos em ${selectedDayNumber}/${String(month + 1).padStart(2, '0')}/${year}`
              : 'Próximos Eventos em Destaque'
            }
          </h2>

          {displayedEvents.length === 0 ? (
            <div className="card text-center" style={{ padding: '48px 24px' }}>
              <p style={{ color: 'var(--color-text-muted)', fontWeight: 500 }}>
                {selectedDayNumber 
                  ? 'Nenhum encontro agendado para o dia escolhido.' 
                  : 'Nenhum encontro agendado para os próximos dias.'
                }
              </p>
              {currentUser && (
                <button className="btn btn-outline btn-sm mt-md" onClick={() => setIsScheduleModalOpen(true)}>
                  Agendar Primeiro Encontro
                </button>
              )}
            </div>
          ) : (
            <div style={cardsContainerStyle}>
              {displayedEvents.map(event => {
                const game = state.boardGames.find(g => g.id === event.gameId);
                const organizer = state.users.find(u => u.id === event.organizerId);
                const isParticipant = currentUser && event.participantIds.includes(currentUser.id);
                const isWaiting = currentUser && event.waitingListIds.includes(currentUser.id);
                const isFull = event.participantIds.length >= event.maxParticipants;
                
                // Active user is the organizer of the event? Or is admin?
                const canComplete = currentUser && (event.organizerId === currentUser.id || isAdmin);

                return (
                  <div key={event.id} className="card card-hoverable" style={eventCardStyle}>
                    <div style={eventCardHeaderStyle}>
                      <div>
                        <span className="badge badge-secondary" style={{ marginBottom: '4px' }}>{game?.category}</span>
                        <h3 style={eventCardGameNameStyle}>{game?.name}</h3>
                      </div>
                      <img src={game?.coverUrl} alt={game?.name} style={eventThumbStyle} />
                    </div>

                    <p style={eventDescStyle}>{event.description}</p>

                    <div style={eventMetaStyle}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <MapPinIcon size={14} style={{ color: 'var(--color-primary)' }} />
                        <span><strong>Local:</strong> {event.location}</span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <ClockIcon size={14} style={{ color: 'var(--color-primary)' }} />
                        <span><strong>Horário:</strong> {new Date(event.date + 'T' + event.time).toLocaleDateString('pt-BR')} às {event.time}</span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <UsersIcon size={14} style={{ color: 'var(--color-primary)' }} />
                        <span>
                          <strong>Inscritos:</strong> {event.participantIds.length} / {event.maxParticipants} 
                          {isFull && <span style={{ color: 'var(--color-danger)', marginLeft: '4px', fontWeight: 'bold' }}>(Espera: {event.waitingListIds.length})</span>}
                        </span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <CrownIcon size={14} style={{ color: 'var(--color-accent)' }} />
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                          <strong>Organizador:</strong>
                          <UserAvatar user={organizer} size={18} style={{ border: '1px solid var(--color-border)' }} />
                          <span>{organizer?.name}</span>
                        </span>
                      </div>
                    </div>

                    {/* Attendance and Completion controls */}
                    <div style={actionsRowStyle}>
                      
                      {/* Left: Organizer Complete Trigger */}
                      {canComplete && (
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => handleOpenCompleteModal(event)}
                          style={{ marginRight: 'auto', backgroundColor: 'var(--color-secondary)', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                        >
                          <TrophyIcon size={14} />
                          <span>Concluir Evento</span>
                        </button>
                      )}

                      {/* Right: Join / Leave */}
                      {currentUser ? (
                        isParticipant ? (
                          <button className="btn btn-outline btn-sm" onClick={() => handleAttendance(event.id, false)} style={{ color: 'var(--color-danger)', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                            <CloseIcon size={14} />
                            <span>Sair da Mesa</span>
                          </button>
                        ) : isWaiting ? (
                          <button className="btn btn-outline btn-sm" onClick={() => handleAttendance(event.id, false)} style={{ color: 'var(--color-primary)', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                            <ClockIcon size={14} />
                            <span>Sair da Espera</span>
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
                                <span>Inscrever-se</span>
                              </>
                            )}
                          </button>
                        )
                      ) : (
                        <span style={loginToJoinStyle}>Faça login simulado para participar</span>
                      )}

                    </div>

                  </div>
                );
              })}
            </div>
          )}

        </div>

      </div>

      {/* Schedule Event Modal */}
      {isScheduleModalOpen && (
        <div className="modal-overlay" onClick={() => setIsScheduleModalOpen(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2 className="mb-lg" style={{ fontFamily: 'var(--font-title)' }}>Agendar Encontro de Jogo</h2>
            <form onSubmit={handleScheduleSubmit}>
              <section style={aiAssistantStyle}>
                <h3 style={{ margin: 0, fontSize: '1rem' }}>Criar com IA</h3>
                <label className="form-label" htmlFor="ai-event-prompt">Descreva o encontro que deseja organizar</label>
                <textarea
                  id="ai-event-prompt"
                  className="form-textarea"
                  value={aiPrompt}
                  minLength={5}
                  maxLength={1000}
                  onChange={e => setAiPrompt(e.target.value)}
                  placeholder="Ex: Crie uma mesa de Xadrez sexta às 18h para seis pessoas na biblioteca."
                />
                <button
                  type="button"
                  className="btn btn-outline"
                  disabled={aiLoading || aiPrompt.trim().length < 5}
                  onClick={handleGenerateDraft}
                >
                  {aiLoading ? 'Gerando rascunho...' : 'Preencher formulário com IA'}
                </button>
                <small style={{ color: 'var(--color-text-muted)' }}>Revise as informações antes de agendar</small>
                <small style={{ color: 'var(--color-text-muted)' }}>
                  Para preservar a cota da equipe, gere um rascunho e ajuste os campos manualmente antes de salvar.
                </small>
                <div aria-live="polite" aria-atomic="true">
                  {aiLoading && <span className="sr-only">Gerando rascunho do evento</span>}
                  {aiError && <div role="alert" style={{ color: 'var(--color-danger, #b42318)' }}>{aiError}</div>}
                </div>
                {aiWarnings.length > 0 && (
                  <div role="status" aria-live="polite" style={{ color: 'var(--color-text-muted)' }}>
                    <strong>Atenção:</strong>
                    <ul>{aiWarnings.map((warning, index) => <li key={`${index}-${warning}`}>{warning}</li>)}</ul>
                  </div>
                )}
                {hasAiDraft && (
                  <div style={{ display: 'grid', gap: '8px', borderTop: '1px solid var(--color-border)', paddingTop: '12px' }}>
                    <h4 style={{ margin: 0 }}>Refinar com IA</h4>
                    <label className="form-label" htmlFor="ai-refinement-instruction">Alteração desejada</label>
                    <textarea
                      id="ai-refinement-instruction"
                      className="form-textarea"
                      value={refinementInstruction}
                      minLength={3}
                      maxLength={500}
                      onChange={event => setRefinementInstruction(event.target.value)}
                      placeholder="Ex.: troque para domingo às 15h e aumente para oito pessoas"
                    />
                    <button
                      type="button"
                      className="btn btn-outline"
                      disabled={aiLoading || refinementInstruction.trim().length < 3}
                      onClick={handleRefineDraft}
                    >
                      {aiLoading ? 'Aplicando alteração...' : 'Aplicar alteração'}
                    </button>
                    <small style={{ color: 'var(--color-text-muted)' }}>
                      Cada refinamento usa uma nova chamada e participa da mesma cota da geração inicial.
                    </small>
                  </div>
                )}
              </section>
              
              <div className="form-group">
                <label className="form-label">Escolher Jogo *</label>
                <select className="form-select" required value={gameId} onChange={e => setGameId(e.target.value)}>
                  <option value="">Selecione o jogo...</option>
                  {state.boardGames.map(g => (
                    <option key={g.id} value={g.id}>{g.name} ({g.category})</option>
                  ))}
                </select>
              </div>

              <div style={formStatsGridStyle}>
                <div className="form-group">
                  <label className="form-label">Data *</label>
                  <input type="date" className="form-input" required value={date} onChange={e => setDate(e.target.value)} />
                </div>
                <div className="form-group">
                  <label className="form-label">Horário *</label>
                  <input type="time" className="form-input" required value={time} onChange={e => setTime(e.target.value)} />
                </div>
                <div className="form-group">
                  <label className="form-label">Vagas Máximas *</label>
                  <input type="number" className="form-input" min={2} max={100} value={maxParticipants} onChange={e => setMaxParticipants(Number(e.target.value))} />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="event-location">Local de Encontro *</label>
                <input id="event-location" type="text" className="form-input" required value={location} onChange={e => setLocation(e.target.value)} placeholder="Ex: Biblioteca Universitária - Sala 4" />
              </div>

              <div className="form-group">
                <label className="form-label">Descrição / Regras / Nível de Conhecimento *</label>
                <textarea
                  className="form-textarea"
                  required
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                  placeholder="Ex: Vamos jogar Terraforming Mars. Ideal que os participantes já saibam as regras ou cheguem 15 minutos mais cedo para explicação."
                />
              </div>

              <div style={formActionsStyle}>
                <button type="button" className="btn btn-outline" onClick={() => setIsScheduleModalOpen(false)}>Cancelar</button>
                <button type="submit" className="btn btn-primary">Agendar Encontro</button>
              </div>

            </form>
          </div>
        </div>
      )}

      {/* Complete Event Workflow Modal */}
      {isCompleteModalOpen && completingEvent && (
        <div className="modal-overlay" onClick={() => setIsCompleteModalOpen(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()} style={recordModalContentStyle}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '4px' }}>
              <TrophyIcon size={24} style={{ color: 'var(--color-accent)' }} />
              <h2 style={{ fontFamily: 'var(--font-title)', margin: 0 }}>Concluir Encontro de Jogo</h2>
            </div>
            <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', marginBottom: '20px' }}>
              Insira o resultado da partida. Os dados básicos do encontro serão importados automaticamente.
            </p>

            <form onSubmit={handleCompleteSubmit}>
              
              {/* Read-Only Imported Data strip */}
              <div style={importedDataStripStyle}>
                <div style={{ fontWeight: 600, color: 'var(--color-secondary)' }}>Dados Importados do Evento:</div>
                <div style={importedItemsGridStyle}>
                  <div><strong>Jogo:</strong> {state.boardGames.find(g => g.id === completingEvent.gameId)?.name}</div>
                  <div><strong>Local:</strong> {completingEvent.location}</div>
                  <div><strong>Data:</strong> {new Date(completingEvent.date + 'T' + completingEvent.time).toLocaleDateString('pt-BR')}</div>
                  <div><strong>Jogadores:</strong> {completingEvent.participantIds.length} confirmados</div>
                </div>
              </div>

              {/* Winner Selector from CONFIRMED participants of the event */}
              <div className="form-group" style={{ marginTop: '20px' }}>
                <label className="form-label">Vencedor da Partida * (Dentre os confirmados na mesa)</label>
                <select
                  className="form-select"
                  required
                  value={winnerId}
                  onChange={e => setWinnerId(e.target.value)}
                >
                  <option value="">Selecione o vencedor...</option>
                  {completingEvent.participantIds.map(pid => {
                    const p = state.users.find(u => u.id === pid);
                    return (
                      <option key={pid} value={pid}>{p?.avatar} {p?.name}</option>
                    );
                  })}
                </select>
              </div>

              {/* Match duration */}
              <div className="form-group">
                <label className="form-label">Duração Real da Partida (minutos) *</label>
                <input
                  type="number"
                  className="form-input"
                  required
                  min={5}
                  value={duration}
                  onChange={e => setDuration(Number(e.target.value))}
                />
              </div>

              {/* Match Notes */}
              <div className="form-group">
                <label className="form-label">Relato da Partida e Curiosidades *</label>
                <textarea
                  className="form-textarea"
                  required
                  value={notes}
                  onChange={e => setNotes(e.target.value)}
                  placeholder="Ex: Como se deu a vitória? Quem quase ganhou? Alguma jogada engraçada ou blefe marcante?"
                />
              </div>

              {/* Optional Photo URL */}
              <div className="form-group">
                <label className="form-label">URL de Foto Pós-Jogo (Opcional)</label>
                <input
                  type="text"
                  className="form-input"
                  value={photoUrl}
                  onChange={e => setPhotoUrl(e.target.value)}
                  placeholder="Link público para a imagem da mesa..."
                />
              </div>

              {/* Optional Comment */}
              <div className="form-group">
                <label className="form-label">Comentário Pós-Jogo (Opcional)</label>
                <input
                  type="text"
                  className="form-input"
                  value={initialComment}
                  onChange={e => setInitialComment(e.target.value)}
                  placeholder="Ex: Partida tensa, mas muito divertida!"
                />
              </div>

              <div style={formActionsStyle}>
                <button type="button" className="btn btn-outline" onClick={() => setIsCompleteModalOpen(false)}>Cancelar</button>
                <button type="submit" className="btn btn-primary" style={{ backgroundColor: 'var(--color-secondary)' }}>
                  Salvar Resultados & Concluir
                </button>
              </div>

            </form>
          </div>
        </div>
      )}

    </div>
  );
};

// Styles for Events and Calendar view
const headerSectionStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  marginBottom: '32px',
  borderBottom: '2px solid var(--color-border)',
  paddingBottom: '16px',
  flexWrap: 'wrap',
  gap: '16px',
};

const eventPageGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '0.9fr 1.1fr',
  gap: '32px',
};

const calendarColStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
};

const eventsListColStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '16px',
};

// Calendar UI elements
const calendarMonthHeaderStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  marginBottom: '20px',
};

const calendarMonthNameStyle: React.CSSProperties = {
  fontSize: '1.2rem',
  fontFamily: 'var(--font-title)',
  textTransform: 'capitalize',
  fontWeight: 800,
};

const weekdaysGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(7, 1fr)',
  textAlign: 'center',
  fontWeight: 700,
  fontSize: '0.8rem',
  color: 'var(--color-text-light)',
  marginBottom: '10px',
};

const daysGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(7, 1fr)',
  gap: '8px',
};

const calendarCellStyle: React.CSSProperties = {
  aspectRatio: '1',
  borderRadius: '8px',
  border: '1px solid',
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  justifyContent: 'center',
  cursor: 'pointer',
  position: 'relative',
  fontSize: '0.9rem',
  transition: 'all 0.15s ease',
};

const calendarCellEmptyStyle: React.CSSProperties = {
  aspectRatio: '1',
};

const calendarDotStyle: React.CSSProperties = {
  width: '6px',
  height: '6px',
  backgroundColor: 'var(--color-secondary)',
  borderRadius: '50%',
  position: 'absolute',
  bottom: '4px',
};

const legendStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  marginTop: '16px',
  fontSize: '0.75rem',
  color: 'var(--color-text-muted)',
};

const legendItemStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '6px',
};

const legendDotStyle: React.CSSProperties = {
  width: '10px',
  height: '10px',
  borderRadius: '50%',
};

const listHeaderTitleStyle: React.CSSProperties = {
  fontSize: '1.2rem',
  fontWeight: 800,
  borderBottom: '2px solid var(--color-border)',
  paddingBottom: '8px',
  marginBottom: '8px',
};

const cardsContainerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '16px',
};

// Card styles
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

const eventCardGameNameStyle: React.CSSProperties = {
  fontSize: '1.25rem',
  fontWeight: 700,
};

const eventThumbStyle: React.CSSProperties = {
  width: '48px',
  height: '48px',
  borderRadius: '8px',
  objectFit: 'cover',
};

const eventDescStyle: React.CSSProperties = {
  fontSize: '0.9rem',
  color: 'var(--color-text-muted)',
  lineHeight: '1.5',
};

const eventMetaStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1fr 1fr',
  gap: '8px',
  fontSize: '0.8rem',
  backgroundColor: '#FAF9F6',
  padding: '12px',
  borderRadius: '10px',
  border: '1px solid var(--color-border)',
};

const actionsRowStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'flex-end',
  gap: '12px',
  alignItems: 'center',
  borderTop: '1px dashed var(--color-border)',
  paddingTop: '12px',
  marginTop: '4px',
};

const loginToJoinStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  color: 'var(--color-text-light)',
  fontStyle: 'italic',
};

// Form layouts
const formStatsGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(3, 1fr)',
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

const aiAssistantStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '10px',
  padding: '16px',
  marginBottom: '20px',
  border: '1px solid var(--color-border)',
  borderRadius: '10px',
  background: 'var(--color-primary-light, #f4f1ff)',
};

const recordModalContentStyle: React.CSSProperties = {
  maxWidth: '600px',
};

// Complete Event workflow styles
const importedDataStripStyle: React.CSSProperties = {
  backgroundColor: 'var(--color-secondary-light)',
  border: '1px solid rgba(42, 111, 96, 0.15)',
  padding: '14px',
  borderRadius: '8px',
  fontSize: '0.85rem',
};

const importedItemsGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(2, 1fr)',
  gap: '6px',
  marginTop: '8px',
  color: 'var(--color-text-muted)',
};

const responsiveEventsStyle = `
@media (max-width: 900px) {
  .events-grid-responsive {
    grid-template-columns: 1fr !important;
  }
}
@media (max-width: 600px) {
  .form-stats-resp {
    grid-template-columns: 1fr !important;
  }
  .meta-grid-resp {
    grid-template-columns: 1fr !important;
  }
}
`;
if (typeof document !== 'undefined') {
  const styleEl = document.createElement('style');
  styleEl.textContent = responsiveEventsStyle;
  document.head.appendChild(styleEl);
}
export default Events;
