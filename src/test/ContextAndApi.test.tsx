import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { AuthProvider, useAuth } from '../context/AuthContext';
import { DatabaseProvider, useDatabase } from '../context/DatabaseContext';
import { ToastProvider } from '../context/ToastContext';
import * as api from '../services/api';
import { getDefaultDatabaseState } from '../db/database';
import type { BoardGame } from '../types';

function AuthHarness() {
  const { currentUser, isAdmin, login, register, logout, resetPassword, changePassword, resendVerification, verifyEmailCode, availableUsers } = useAuth();

  return (
    <div>
      <div data-testid="user">{currentUser?.name ?? 'none'}</div>
      <div data-testid="admin">{String(isAdmin)}</div>
      <div data-testid="users">{availableUsers.length}</div>
      <button onClick={() => void login('caua@tabula.com', 'StrongP@ss1', false)}>login</button>
      <button onClick={() => void register('Nova Pessoa', 'nova@tabula.com', 'StrongP@ss1', 'StrongP@ss1')}>register</button>
      <button onClick={() => logout()}>logout</button>
      <button onClick={() => void resetPassword('caua@tabula.com', 'StrongP@ss1', 'StrongP@ss1')}>reset</button>
      <button onClick={() => void changePassword('StrongP@ss1', 'AnotherP@ss2', 'AnotherP@ss2')}>change</button>
      <button onClick={() => void resendVerification('caua@tabula.com')}>resend</button>
      <button onClick={() => void verifyEmailCode('caua@tabula.com', '123456')}>verify</button>
    </div>
  );
}

function DatabaseHarness() {
  const { state, addGame, editGame, deleteGame, addSession, addComment, addEvent, joinEvent, leaveEvent, completeEvent, addUser, deleteUser, promoteUser, editUser } = useDatabase();

  return (
    <div>
      <div data-testid="games-count">{state.boardGames.length}</div>
      <div data-testid="users-count">{state.users.length}</div>
      <div data-testid="sessions-count">{state.sessions.length}</div>
      <div data-testid="events-count">{state.events.length}</div>
      <button onClick={() => addGame({ name: 'Catan', description: 'Strategy', category: 'Estratégia', minPlayers: 2, maxPlayers: 4, avgPlayTime: 60, complexity: 2.5 })}>addGame</button>
      <button onClick={() => editGame({ ...state.boardGames[0], name: 'Catan editado', description: 'Updated' } as BoardGame)}>editGame</button>
      <button onClick={() => deleteGame(state.boardGames[0].id)}>deleteGame</button>
      <button onClick={() => addSession({ gameId: state.boardGames[0].id, date: '2026-07-02T18:00:00Z', location: 'Bloco A', organizerId: state.users[0].id, participantIds: [state.users[0].id], winnerId: state.users[0].id, duration: 45, notes: 'Test session' }, 'Primeiro comentário')}>addSession</button>
      <button onClick={() => addComment(state.sessions[0]?.id ?? '', state.users[0].id, 'Comentario')}>addComment</button>
      <button onClick={() => addEvent({ gameId: state.boardGames[0].id, date: '2026-08-01', time: '20:00', location: 'Sala 1', maxParticipants: 2, description: 'Evento de teste' }, state.users[0].id)}>addEvent</button>
      <button onClick={() => joinEvent(state.events[0]?.id ?? '', state.users[1].id)}>joinEvent</button>
      <button onClick={() => leaveEvent(state.events[0]?.id ?? '', state.users[1].id)}>leaveEvent</button>
      <button onClick={() => completeEvent(state.events[0]?.id ?? '', state.users[0].id, 30, 'Finalizado', 'Comentário', 'photo.png')}>completeEvent</button>
      <button onClick={() => addUser({ name: 'Usuário Teste', email: 'teste@tabula.com' })}>addUser</button>
      <button onClick={() => deleteUser(state.users[0].id)}>deleteUser</button>
      <button onClick={() => promoteUser(state.users[1].id)}>promoteUser</button>
      <button onClick={() => editUser(state.users[1].id, { course: 'Nova disciplina' })}>editUser</button>
    </div>
  );
}

function AuthValidationHarness() {
  const { login, register, resetPassword, changePassword, resendVerification, verifyEmailCode } = useAuth();

  return (
    <div>
      <button onClick={() => void login('', '')}>emptyLogin</button>
      <button onClick={() => void register(' ', 'bad', 'weak', 'other')}>invalidRegister</button>
      <button onClick={() => void resetPassword('', 'weak', 'weak')}>invalidReset</button>
      <button onClick={() => void changePassword('', 'weak', 'weak')}>invalidChange</button>
      <button onClick={() => void resendVerification('')}>emptyResend</button>
      <button onClick={() => void verifyEmailCode('', 'abc')}>invalidVerify</button>
    </div>
  );
}

function DatabaseEdgeHarness() {
  const { state, addEvent, joinEvent, leaveEvent, deleteGame } = useDatabase();

  return (
    <div>
      <div data-testid="games-count">{state.boardGames.length}</div>
      <div data-testid="participant-count">{state.events.find(e => e.description === 'Edge event')?.participantIds.length ?? 0}</div>
      <div data-testid="waiting-count">{state.events.find(e => e.description === 'Edge event')?.waitingListIds.length ?? 0}</div>
      <button onClick={() => addEvent({ gameId: state.boardGames[0].id, date: '2026-10-01', time: '20:00', location: 'Sala 1', maxParticipants: 2, description: 'Edge event' }, state.users[0].id)}>createEdgeEvent</button>
      <button onClick={() => { const event = state.events.find(e => e.description === 'Edge event'); if (event) joinEvent(event.id, state.users[1].id); }}>joinEdgeParticipant</button>
      <button onClick={() => { const event = state.events.find(e => e.description === 'Edge event'); if (event) joinEvent(event.id, state.users[2].id); }}>joinEdgeWaiting</button>
      <button onClick={() => { const event = state.events.find(e => e.description === 'Edge event'); if (event) leaveEvent(event.id, state.users[0].id); }}>leaveEdgeOrganizer</button>
      <button onClick={() => deleteGame(state.boardGames[0].id)}>deleteLinkedGame</button>
    </div>
  );
}

describe('Auth and database contexts', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it('handles auth lifecycle and password flows', async () => {
    const mockedLoginBackend = vi.mocked(api.loginBackend);
    const mockedRegisterUserBackend = vi.mocked(api.registerUserBackend);
    const mockedResetPasswordBackend = vi.mocked(api.resetPasswordBackend);
    const mockedChangePasswordBackend = vi.mocked(api.changePasswordBackend);
    const mockedResendVerificationBackend = vi.mocked(api.resendVerificationBackend);
    const mockedVerifyEmailCode = vi.mocked(api.verifyEmailCode);

    mockedLoginBackend.mockResolvedValue({ ok: true, message: 'login', token: 'token', user: { id: 'u1', name: 'Cauã Botelho', email: 'caua@tabula.com', role: 'student', course: 'Computação', avatar: 'CB', winCount: 0, favoriteGames: [], joinedAt: '2026-01-01T00:00:00.000Z', bio: 'Aluno' } });
    mockedRegisterUserBackend.mockResolvedValue({ ok: true, message: 'registro', token: 'token', user: { id: 'u9', name: 'Nova Pessoa', email: 'nova@tabula.com', role: 'student', course: 'Design', avatar: 'NP', winCount: 0, favoriteGames: [], joinedAt: '2026-01-01T00:00:00.000Z', bio: 'Nova' } });
    mockedResetPasswordBackend.mockResolvedValue({ ok: true, message: 'Senha atualizada' });
    mockedChangePasswordBackend.mockResolvedValue({ ok: true, message: 'Senha alterada' });
    mockedResendVerificationBackend.mockResolvedValue({ ok: true, message: 'Reenviado' });
    mockedVerifyEmailCode.mockResolvedValue({ ok: true, message: 'Verificado' });

    render(
      <ToastProvider>
        <DatabaseProvider>
          <AuthProvider>
            <AuthHarness />
          </AuthProvider>
        </DatabaseProvider>
      </ToastProvider>
    );

    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: /login/i }));
    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('Cauã Botelho'));

    await user.click(screen.getByRole('button', { name: /logout/i }));
    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('none'));

    await user.click(screen.getByRole('button', { name: /register/i }));
    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('Nova Pessoa'));

    await user.click(screen.getByRole('button', { name: /reset/i }));
    await user.click(screen.getByRole('button', { name: /change/i }));
    await user.click(screen.getByRole('button', { name: /resend/i }));
    await user.click(screen.getByRole('button', { name: /verify/i }));

    expect(mockedLoginBackend).toHaveBeenCalled();
    expect(mockedRegisterUserBackend).toHaveBeenCalled();
    expect(mockedResetPasswordBackend).toHaveBeenCalled();
    expect(mockedChangePasswordBackend).toHaveBeenCalled();
    expect(mockedResendVerificationBackend).toHaveBeenCalled();
    expect(mockedVerifyEmailCode).toHaveBeenCalled();
  });

  it('drives the database provider actions and keeps state in sync', async () => {
    const user = userEvent.setup();
    render(
      <ToastProvider>
        <DatabaseProvider>
          <DatabaseHarness />
        </DatabaseProvider>
      </ToastProvider>
    );

    await waitFor(() => expect(screen.getByTestId('games-count').textContent).toBe('3'));

    await user.click(screen.getByRole('button', { name: /addGame/i }));
    await waitFor(() => expect(screen.getByTestId('games-count').textContent).toBe('4'));

    await user.click(screen.getByRole('button', { name: /editGame/i }));
    await waitFor(() => expect(screen.getByTestId('games-count').textContent).toBe('4'));

    await user.click(screen.getByRole('button', { name: /addSession/i }));
    await waitFor(() => expect(screen.getByTestId('sessions-count').textContent).toBe('4'));

    await user.click(screen.getByRole('button', { name: /addComment/i }));

    await user.click(screen.getByRole('button', { name: /addEvent/i }));
    await waitFor(() => expect(screen.getByTestId('events-count').textContent).toBe('4'));

    await user.click(screen.getByRole('button', { name: /joinEvent/i }));
    await user.click(screen.getByRole('button', { name: /completeEvent/i }));
    await waitFor(() => expect(screen.getByTestId('sessions-count').textContent).toBe('5'));

    const initialUserCount = Number(screen.getByTestId('users-count').textContent);

    await user.click(screen.getByRole('button', { name: /addUser/i }));
    await waitFor(() => expect(screen.getByTestId('users-count').textContent).toBe(String(initialUserCount + 1)));

    await user.click(screen.getByRole('button', { name: /promoteUser/i }));
    await user.click(screen.getByRole('button', { name: /editUser/i }));
    await user.click(screen.getByRole('button', { name: /deleteUser/i }));
    await waitFor(() => expect(screen.getByTestId('users-count').textContent).toBe(String(initialUserCount)));
  });

  it('covers auth validation branches and database edge cases', async () => {
    const user = userEvent.setup();
    render(
      <ToastProvider>
        <DatabaseProvider>
          <AuthProvider>
            <AuthValidationHarness />
            <DatabaseEdgeHarness />
          </AuthProvider>
        </DatabaseProvider>
      </ToastProvider>
    );

    await user.click(screen.getByRole('button', { name: /emptyLogin/i }));
    await user.click(screen.getByRole('button', { name: /invalidRegister/i }));
    await user.click(screen.getByRole('button', { name: /invalidReset/i }));
    await user.click(screen.getByRole('button', { name: /invalidChange/i }));
    await user.click(screen.getByRole('button', { name: /emptyResend/i }));
    await user.click(screen.getByRole('button', { name: /invalidVerify/i }));

    await user.click(screen.getByRole('button', { name: /deleteLinkedGame/i }));
    await waitFor(() => expect(screen.getByTestId('games-count').textContent).toBe('3'));

    await user.click(screen.getByRole('button', { name: /createEdgeEvent/i }));
    await waitFor(() => expect(screen.getByTestId('participant-count').textContent).toBe('1'));
    await user.click(screen.getByRole('button', { name: /joinEdgeParticipant/i }));
    await waitFor(() => expect(screen.getByTestId('participant-count').textContent).toBe('2'));
    await user.click(screen.getByRole('button', { name: /joinEdgeWaiting/i }));
    await waitFor(() => expect(screen.getByTestId('waiting-count').textContent).toBe('1'));
    await user.click(screen.getByRole('button', { name: /leaveEdgeOrganizer/i }));
    await waitFor(() => expect(screen.getByTestId('waiting-count').textContent).toBe('1'));
  });
});

describe('API helpers', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('covers the API helpers for health checks and state persistence', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ status: 'ok', service: 'backend', timestamp: '2026-01-01' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ users: [] }), text: async () => JSON.stringify({ users: [] }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ ok: true }) });

    vi.stubGlobal('fetch', fetchMock);

    const ping = await api.pingBackend();
    const serverState = await api.getServerState();
    await api.saveServerState(getDefaultDatabaseState());

    expect(ping.status).toBe('ok');
    expect(serverState).toBeNull();
    expect(fetchMock).toHaveBeenCalled();
  });

  it('covers password hashing and verification helpers', async () => {
    const password = 'StrongP@ss2';
    const hash = await import('../auth/security').then(module => module.hashPassword(password));
    const { verifyPassword } = await import('../auth/security');

    const storedHash = await hash;
    const isValid = await verifyPassword(password, storedHash);
    const isInvalid = await verifyPassword('other', storedHash);
    const strong = await import('../auth/security').then(module => module.isStrongPassword(password));

    expect(isValid).toBe(true);
    expect(isInvalid).toBe(false);
    expect(strong).toBe(true);
  });
});
