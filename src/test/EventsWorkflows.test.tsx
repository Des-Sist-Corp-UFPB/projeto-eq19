import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Events } from '../pages/Events';
import { renderWithProviders } from './renderWithProviders';
import { getTestDatabaseState } from './testState';
import * as api from '../services/api';

vi.mock('../services/api', async importOriginal => {
  const original = await importOriginal<typeof import('../services/api')>();
  return {
    ...original,
    getServerState: vi.fn(),
    generateEventDraft: vi.fn(),
    refineEventDraft: vi.fn(),
    createEvent: vi.fn(),
    joinEventRequest: vi.fn(),
    leaveEventRequest: vi.fn(),
    completeEventRequest: vi.fn(),
    getEvents: vi.fn(),
    getSessions: vi.fn(),
  };
});

describe('Events workflows', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    sessionStorage.setItem('tabula_auth_token', 'session-token');
    sessionStorage.setItem('tabula_auth_session', 'u1');
    vi.mocked(api.getServerState).mockResolvedValue(getTestDatabaseState());
    let events = structuredClone(getTestDatabaseState().events);
    let sessions = structuredClone(getTestDatabaseState().sessions);
    vi.mocked(api.getEvents).mockImplementation(async () => structuredClone(events));
    vi.mocked(api.getSessions).mockImplementation(async () => structuredClone(sessions));
    vi.mocked(api.createEvent).mockImplementation(async input => {
      const event = {
        ...input, id: 'e_created', organizerId: 'u1', participantIds: ['u1'],
        waitingListIds: [], status: 'active' as const,
      };
      events = [event, ...events];
      return event;
    });
    vi.mocked(api.joinEventRequest).mockImplementation(async id => {
      const current = events.find(event => event.id === id)!;
      const waitlisted = current.participantIds.length >= current.maxParticipants;
      const event = {
        ...current,
        participantIds: waitlisted ? current.participantIds : [...current.participantIds, 'u1'],
        waitingListIds: waitlisted ? [...current.waitingListIds, 'u1'] : current.waitingListIds,
      };
      events = events.map(candidate => candidate.id === id ? event : candidate);
      return { event, waitlisted };
    });
    vi.mocked(api.leaveEventRequest).mockImplementation(async id => {
      const current = events.find(event => event.id === id)!;
      const event = {
        ...current,
        participantIds: current.participantIds.filter(userId => userId !== 'u1'),
        waitingListIds: current.waitingListIds.filter(userId => userId !== 'u1'),
      };
      events = events.map(candidate => candidate.id === id ? event : candidate);
      return { event, promoted: false };
    });
    vi.mocked(api.completeEventRequest).mockImplementation(async id => {
      const current = events.find(event => event.id === id)!;
      const event = { ...current, status: 'completed' as const };
      events = events.map(candidate => candidate.id === id ? event : candidate);
      sessions = [{
        id: 's_completed', gameId: event.gameId, date: `${event.date}T${event.time}:00`,
        location: event.location, organizerId: event.organizerId,
        participantIds: event.participantIds, winnerId: event.participantIds[0] ?? null,
        duration: 60, notes: 'Vitória', photos: [], comments: [],
      }, ...sessions];
      return event;
    });
  });

  it('filters the calendar, changes month and clears the selected day', async () => {
    renderWithProviders(<Events />);
    const user = userEvent.setup();

    await user.click(screen.getByText('12', { selector: 'span' }));
    expect(screen.getByRole('heading', { name: /Eventos em 12\/06\/2026/i })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Limpar Filtro de Dia/i }));
    expect(screen.getByRole('heading', { name: /Próximos Eventos/i })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '▶' }));
    expect(screen.getByRole('heading', { name: /Julho de 2026/i })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '◀' }));
    expect(screen.getByRole('heading', { name: /Junho de 2026/i })).toBeInTheDocument();
  });

  it('joins a full event waiting list and leaves an existing table', async () => {
    renderWithProviders(<Events />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /Entrar na Fila de Espera/i }));
    expect(await screen.findByText(/adicionado à lista de espera/i)).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: /Sair da Espera/i }));
    expect(await screen.findByText(/Participação cancelada/i)).toBeInTheDocument();

    const leaveButtons = screen.getAllByRole('button', { name: /Sair da Mesa/i });
    await user.click(leaveButtons[0]);
    expect((await screen.findAllByText(/Participação cancelada/i)).length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByRole('button', { name: /Inscrever-se|Entrar na Fila de Espera/i }).length)
      .toBeGreaterThan(0);
  });

  it('creates an event only after manual submission and resets the form', async () => {
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /Agendar Novo Encontro/i }));

    const form = screen.getByRole('heading', { name: /Agendar Encontro de Jogo/i }).closest('.modal-content')!
      .querySelector('form')!;
    const select = form.querySelector('select')!;
    const date = form.querySelector('input[type="date"]')!;
    const description = form.querySelectorAll('textarea')[1];
    await user.selectOptions(select, 'g1');
    await user.clear(date);
    await user.type(date, '2026-09-20');
    await user.clear(screen.getByLabelText(/Local de Encontro/i));
    await user.type(screen.getByLabelText(/Local de Encontro/i), 'Sala 5');
    await user.type(description, 'Evento confirmado manualmente.');

    await user.click(within(form).getByRole('button', { name: /^Agendar Encontro$/i }));
    expect(await screen.findByText(/agendado com sucesso/i)).toBeInTheDocument();
    expect(api.createEvent).toHaveBeenCalled();
    expect(screen.queryByRole('heading', { name: /Agendar Encontro de Jogo/i })).not.toBeInTheDocument();
  });

  it('validates and completes an event with winner and match details', async () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => undefined);
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    const concludeButtons = await screen.findAllByRole('button', { name: /Concluir Evento/i });
    await user.click(concludeButtons[0]);

    const modal = screen.getByRole('heading', { name: /Concluir Encontro de Jogo/i }).closest('.modal-content')!;
    const form = modal.querySelector('form')!;
    fireEvent.submit(form);
    expect(alertSpy).toHaveBeenCalledWith('Por favor, selecione o vencedor.');

    await user.selectOptions(form.querySelector('select')!, 'u1');
    fireEvent.submit(form);
    expect(alertSpy).toHaveBeenCalledWith('Por favor, adicione notas do relato da partida.');

    const inputs = form.querySelectorAll('input');
    await user.clear(form.querySelector('textarea')!);
    await user.type(form.querySelector('textarea')!, 'Vitória após uma disputa equilibrada.');
    await user.clear(inputs[1]);
    await user.type(inputs[1], 'https://example.test/partida.jpg');
    await user.type(inputs[2], 'Ótima partida!');
    await user.click(within(form).getByRole('button', { name: /Salvar Resultados/i }));

    expect(await screen.findByText(/finalizada e arquivada/i)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Concluir Encontro de Jogo/i })).not.toBeInTheDocument();
    alertSpy.mockRestore();
  });
});
