import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import { DatabaseProvider, useDatabase } from '../context/DatabaseContext';
import { ToastProvider } from '../context/ToastContext';
import { getDefaultDatabaseState } from '../db/database';
import { SessionDetails } from '../pages/SessionDetails';
import * as api from '../services/api';
import type { DatabaseState, Session } from '../types';

const SESSION_ID = 's_5082d1a2-9178-4d9c-929d-e0664b1327db';
const baseState = getDefaultDatabaseState();
const emptySessionState: DatabaseState = { ...baseState, sessions: [] };
const session: Session = {
  id: SESSION_ID,
  gameId: baseState.boardGames[0].id,
  date: '2026-08-01T19:30:00.000Z',
  location: 'Sala de Jogos',
  organizerId: 'u_admin',
  participantIds: ['u_admin'],
  winnerId: 'u_admin',
  duration: 90,
  notes: 'Partida relacional de regressão',
  photos: [],
  comments: [],
};

const SessionCount = () => {
  const { state } = useDatabase();
  return <output data-testid="session-count">{state.sessions.filter(item => item.id === SESSION_ID).length}</output>;
};

const renderDetails = () => render(
  <ToastProvider>
    <DatabaseProvider>
      <AuthProvider>
        <MemoryRouter initialEntries={[`/sessions/${SESSION_ID}`]}>
          <Routes>
            <Route path="/sessions/:id" element={<><SessionDetails /><SessionCount /></>} />
            <Route path="/sessions" element={<div>Histórico</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </DatabaseProvider>
  </ToastProvider>,
);

describe('SessionDetails relational loading', () => {
  beforeEach(() => {
    vi.mocked(api.getServerState).mockClear();
    vi.mocked(api.getEvents).mockClear();
    vi.mocked(api.getSessions).mockClear();
    vi.mocked(api.getServerState).mockResolvedValue(emptySessionState);
    vi.mocked(api.getEvents).mockResolvedValue([]);
    vi.mocked(api.getSessions).mockResolvedValue([]);
    vi.mocked(api.getSession).mockReset();
  });

  it('loads a direct route with an initially empty session state and upserts without duplication', async () => {
    vi.mocked(api.getSession).mockResolvedValue(session);

    renderDetails();

    expect(screen.getByText('Carregando partida...')).toBeInTheDocument();
    expect(await screen.findByText(/Partida relacional de regressão/)).toBeInTheDocument();
    expect(api.getSession).toHaveBeenCalledWith(SESSION_ID);
    await waitFor(() => expect(screen.getByTestId('session-count')).toHaveTextContent('1'));
    expect(api.getServerState).toHaveBeenCalledTimes(1);
  });

  it('loads the same relational detail after a route reload', async () => {
    vi.mocked(api.getSession).mockResolvedValue(session);
    const firstRender = renderDetails();
    expect(await screen.findByText(/Partida relacional de regressão/)).toBeInTheDocument();
    firstRender.unmount();

    renderDetails();
    expect(await screen.findByText(/Partida relacional de regressão/)).toBeInTheDocument();
    expect(api.getSession).toHaveBeenCalledTimes(2);
  });

  it('shows not found only for a 404 response', async () => {
    vi.mocked(api.getSession).mockRejectedValue(new api.ApiError(404, 'Not found'));
    renderDetails();
    expect(await screen.findByText('Partida não encontrada')).toBeInTheDocument();
  });

  it('shows an expired-session error for 401 instead of not found', async () => {
    vi.mocked(api.getSession).mockRejectedValue(new api.ApiError(401, 'Unauthorized'));
    renderDetails();
    expect(await screen.findByText('Sua sessão expirou. Entre novamente.')).toBeInTheDocument();
    expect(screen.queryByText('Partida não encontrada')).not.toBeInTheDocument();
  });

  it('shows a permission error for 403 instead of not found', async () => {
    vi.mocked(api.getSession).mockRejectedValue(new api.ApiError(403, 'Forbidden'));
    renderDetails();
    expect(await screen.findByText('Você não tem permissão para visualizar esta partida.')).toBeInTheDocument();
    expect(screen.queryByText('Partida não encontrada')).not.toBeInTheDocument();
  });

  it('shows a service error for 500 without reading or writing state again', async () => {
    vi.mocked(api.getSession).mockRejectedValue(new api.ApiError(500, 'Internal error'));
    renderDetails();
    expect(await screen.findByText('Não foi possível carregar a partida. Tente novamente.')).toBeInTheDocument();
    expect(screen.queryByText('Partida não encontrada')).not.toBeInTheDocument();
    expect(api.getServerState).toHaveBeenCalledTimes(1);
  });
});
