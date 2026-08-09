import { fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Home } from '../pages/Home';
import * as api from '../services/api';
import { renderWithProviders } from './renderWithProviders';
import { getTestDatabaseState } from './testState';

describe('Home page', () => {
  beforeEach(() => {
    localStorage.setItem('tabula_auth_token', 'token');
    localStorage.setItem('tabula_auth_session', 'u2');
    const futureState = structuredClone(getTestDatabaseState());
    futureState.events = futureState.events.map((event, index) => ({
      ...event,
      date: index === 0 ? '2030-06-12' : event.date,
      time: index === 0 ? '18:00' : event.time,
    }));
    vi.mocked(api.getServerState).mockResolvedValue(futureState);
    vi.mocked(api.getEvents).mockResolvedValue(futureState.events);
    vi.mocked(api.getSessions).mockResolvedValue(futureState.sessions);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders the main hero content and seeded community highlights', () => {
    renderWithProviders(<Home />);

    expect(screen.getByRole('heading', { name: /Conecte-se com amigos/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Próximos Encontros Agendados/i })).toBeInTheDocument();
    expect(screen.getByText(/^Partidas Registradas$/i, { selector: 'span' })).toBeInTheDocument();
    expect(screen.getByText(/^Jogadores Ativos$/i, { selector: 'span' })).toBeInTheDocument();
    expect(screen.getByText(/^Jogos no Acervo$/i, { selector: 'span' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Atividades do Clube/i })).not.toBeInTheDocument();
  });

  it('lets an authenticated user join and leave an event', async () => {
    renderWithProviders(<Home />);

    const joinButton = await screen.findByRole('button', { name: /Participar da Mesa/i });
    fireEvent.click(joinButton);

    await vi.waitFor(() => expect(api.joinEventRequest).toHaveBeenCalledTimes(1));
    expect(screen.getByRole('button', { name: /Participar da Mesa/i })).toBeInTheDocument();
  });
});
