import { fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Home } from '../pages/Home';
import { renderWithProviders } from './renderWithProviders';

describe('Home page', () => {
  beforeEach(() => {
    localStorage.setItem('tabula_auth_token', 'token');
    localStorage.setItem('tabula_auth_session', 'u2');
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
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-10T10:00:00Z'));

    renderWithProviders(<Home />);

    const joinButtons = screen.getAllByRole('button', { name: /Participar da Mesa/i });
    fireEvent.click(joinButtons[0]);
    expect(screen.getAllByRole('button', { name: /Cancelar Inscrição/i }).length).toBeGreaterThan(0);

    fireEvent.click(screen.getAllByRole('button', { name: /Cancelar Inscrição/i })[0]);
    expect(screen.getAllByRole('button', { name: /Participar da Mesa/i }).length).toBeGreaterThan(0);
  });
});
