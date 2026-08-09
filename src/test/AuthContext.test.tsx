import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../context/AuthContext';
import { DatabaseProvider } from '../context/DatabaseContext';
import { ToastProvider } from '../context/ToastContext';
import * as api from '../services/api';
import { AUTH_TOKEN_KEY } from '../services/api';
import { getDefaultDatabaseState } from '../db/database';

function AuthHarness() {
  const auth = useAuth();

  return (
    <div>
      <div data-testid="user">{auth.currentUser?.name ?? 'none'}</div>
      <div data-testid="admin">{String(auth.isAdmin)}</div>
      <div data-testid="users">{auth.availableUsers.length}</div>
      <button onClick={() => void auth.login('existing@example.com', 'StrongP@ss1', false)}>login</button>
      <button onClick={() => void auth.register('New User', 'new@example.com', 'StrongP@ss1', 'StrongP@ss1')}>register</button>
      <button onClick={() => auth.logout()}>logout</button>
      <button onClick={() => void auth.resetPassword('existing@example.com', 'StrongP@ss2', 'StrongP@ss2')}>reset</button>
      <button onClick={() => void auth.changePassword('StrongP@ss1', 'StrongP@ss2', 'StrongP@ss2')}>change</button>
      <button onClick={() => void auth.resendVerification('existing@example.com')}>resend</button>
      <button onClick={() => void auth.verifyEmailCode('existing@example.com', '123456')}>verify</button>
    </div>
  );
}

function renderAuth() {
  return render(
    <ToastProvider>
      <DatabaseProvider>
        <AuthProvider>
          <AuthHarness />
        </AuthProvider>
      </DatabaseProvider>
    </ToastProvider>
  );
}

function ValidationHarness() {
  const auth = useAuth();

  return (
    <div>
      <div data-testid="user">{auth.currentUser?.name ?? 'none'}</div>

      <button onClick={() => void auth.register('', '', '', '')}>register missing</button>
      <button onClick={() => void auth.register('Bad Email', 'invalid-email', 'StrongP@ss1', 'StrongP@ss1')}>register invalid email</button>
      <button onClick={() => void auth.register('Mismatch', 'mismatch@example.com', 'StrongP@ss1', 'StrongP@ss2')}>register mismatch</button>
      <button onClick={() => void auth.register('Weak', 'weak@example.com', 'weak', 'weak')}>register weak</button>

      <button onClick={() => void auth.resendVerification('')}>resend empty</button>
      <button onClick={() => void auth.verifyEmailCode('', '')}>verify empty</button>
      <button onClick={() => void auth.verifyEmailCode('existing@example.com', '123')}>verify invalid code</button>

      <button onClick={() => void auth.resetPassword('', '', '')}>reset missing</button>
      <button onClick={() => void auth.resetPassword('existing@example.com', 'StrongP@ss1', 'StrongP@ss2')}>reset mismatch</button>
      <button onClick={() => void auth.resetPassword('existing@example.com', 'weak', 'weak')}>reset weak</button>

      <button onClick={() => void auth.changePassword('', '', '')}>change no user</button>
    </div>
  );
}

function renderValidationAuth() {
  return render(
    <ToastProvider>
      <DatabaseProvider>
        <AuthProvider>
          <ValidationHarness />
        </AuthProvider>
      </DatabaseProvider>
    </ToastProvider>
  );
}

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it('logs in successfully and persists the session to sessionStorage when remember is false', async () => {
    const user = userEvent.setup();

    vi.mocked(api.loginBackend).mockResolvedValue({
      ok: true,
      message: 'Login realizado',
      token: 'abc-token',
      user: {
        id: 'u_existing',
        name: 'Existing User',
        email: 'existing@example.com',
        role: 'student',
        course: 'Computer Science',
        avatar: 'EU',
        winCount: 0,
        favoriteGames: [],
        joinedAt: '2026-01-01T00:00:00.000Z',
        bio: 'Existing',
      },
    });

    renderAuth();

    await user.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('Existing User'));

    expect(sessionStorage.getItem(AUTH_TOKEN_KEY)).toBe('abc-token');
    expect(sessionStorage.getItem('tabula_auth_session')).toBe('u_existing');

    expect(localStorage.getItem(AUTH_TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem('tabula_auth_session')).toBeNull();

    expect(screen.getByTestId('admin')).toHaveTextContent('false');
  });

  it('selects an existing user on login without persisting app state', async () => {
    const user = userEvent.setup();
    const existingState = getDefaultDatabaseState();
    const existingUser = existingState.users[0];
    vi.mocked(api.getServerState).mockResolvedValue(existingState);
    vi.mocked(api.getEvents).mockResolvedValue(existingState.events);
    vi.mocked(api.getSessions).mockResolvedValue(existingState.sessions);
    vi.mocked(api.loginBackend).mockResolvedValue({
      ok: true,
      message: 'Login realizado',
      token: 'existing-token',
      user: {
        ...existingUser,
        name: 'Nome simplificado da resposta de autenticação',
        joinedAt: '2026-08-09T00:00:00.000Z',
      },
    });

    renderAuth();
    await waitFor(() => expect(api.getServerState).toHaveBeenCalledTimes(1));
    await new Promise(resolve => window.setTimeout(resolve, 0));
    vi.mocked(api.saveServerState).mockClear();

    await user.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => expect(sessionStorage.getItem(AUTH_TOKEN_KEY)).toBe('existing-token'));
    expect(sessionStorage.getItem('tabula_auth_session')).toBe(existingUser.id);
    expect(screen.getByTestId('user')).toHaveTextContent(existingUser.name);
    await new Promise(resolve => window.setTimeout(resolve, 400));
    expect(api.saveServerState).not.toHaveBeenCalled();
  });

  it('handles login failures from the API and keeps the user signed out', async () => {
    const user = userEvent.setup();

    vi.mocked(api.loginBackend).mockRejectedValue(
      new Error('API request failed (401): {"error":"Credenciais inválidas"}')
    );

    renderAuth();

    await user.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('none'));

    expect(vi.mocked(api.loginBackend)).toHaveBeenCalled();
    expect(localStorage.getItem(AUTH_TOKEN_KEY)).toBeNull();
    expect(sessionStorage.getItem(AUTH_TOKEN_KEY)).toBeNull();
  });

  it('registers successfully, persists the session, and adds a new user', async () => {
    const user = userEvent.setup();

    vi.mocked(api.registerUserBackend).mockResolvedValue({
      ok: true,
      message: 'Conta criada',
      token: 'new-token',
      user: {
        id: 'u_new',
        name: 'New User',
        email: 'new@example.com',
        role: 'student',
        course: 'Computer Science',
        avatar: 'NU',
        winCount: 0,
        favoriteGames: [],
        joinedAt: '2026-01-01T00:00:00.000Z',
        bio: 'New',
      },
    });

    renderAuth();

    await user.click(screen.getByRole('button', { name: /register/i }));

    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('New User'));

    expect(localStorage.getItem(AUTH_TOKEN_KEY)).toBe('new-token');
    expect(localStorage.getItem('tabula_auth_session')).toBe('u_new');
  });

  it('handles register failures from the API', async () => {
    const user = userEvent.setup();

    vi.mocked(api.registerUserBackend).mockRejectedValue(
      new Error('API request failed (400): {"error":"E-mail já cadastrado"}')
    );

    renderAuth();

    await user.click(screen.getByRole('button', { name: /register/i }));

    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('none'));

    expect(vi.mocked(api.registerUserBackend)).toHaveBeenCalled();
  });

  it('verifies email codes and resends verification for both success and failure paths', async () => {
    const user = userEvent.setup();

    vi.mocked(api.verifyEmailCode).mockResolvedValue({ ok: true, message: 'Verificado' });
    vi.mocked(api.resendVerificationBackend).mockRejectedValue(
      new Error('API request failed (500): Falha')
    );

    renderAuth();

    await user.click(screen.getByRole('button', { name: /verify/i }));
    await user.click(screen.getByRole('button', { name: /resend/i }));

    await waitFor(() => expect(vi.mocked(api.verifyEmailCode)).toHaveBeenCalled());

    expect(vi.mocked(api.resendVerificationBackend)).toHaveBeenCalled();
    expect(screen.getByTestId('user').textContent).toBe('none');
  });

  it('resets and changes passwords for success and failure paths', async () => {
    const user = userEvent.setup();

    vi.mocked(api.resetPasswordBackend).mockResolvedValue({
      ok: true,
      message: 'Senha redefinida',
    });

    vi.mocked(api.changePasswordBackend).mockRejectedValue(
      new Error('API request failed (400): Erro ao trocar')
    );

    localStorage.setItem(AUTH_TOKEN_KEY, 'persisted-token');
    localStorage.setItem('tabula_auth_session', 'u2');

    renderAuth();

    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('Mariana Souza'));

    await user.click(screen.getByRole('button', { name: /reset/i }));
    await user.click(screen.getByRole('button', { name: /change/i }));

    await waitFor(() => expect(vi.mocked(api.resetPasswordBackend)).toHaveBeenCalled());

    expect(vi.mocked(api.changePasswordBackend)).toHaveBeenCalledWith(
      'mariana.souza@universidade.edu.br',
      'StrongP@ss1',
      'StrongP@ss2'
    );

    expect(screen.getByTestId('user').textContent).toBe('Mariana Souza');
  });

  it('logs out and clears persisted auth data', async () => {
    const user = userEvent.setup();

    localStorage.setItem(AUTH_TOKEN_KEY, 'persisted-token');
    localStorage.setItem('tabula_auth_session', 'u2');

    sessionStorage.setItem(AUTH_TOKEN_KEY, 'session-token');
    sessionStorage.setItem('tabula_auth_session', 'u2');

    renderAuth();

    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('Mariana Souza'));

    await user.click(screen.getByRole('button', { name: /logout/i }));

    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('none'));

    expect(localStorage.getItem(AUTH_TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem('tabula_auth_session')).toBeNull();
    expect(sessionStorage.getItem(AUTH_TOKEN_KEY)).toBeNull();
    expect(sessionStorage.getItem('tabula_auth_session')).toBeNull();
  });

  it('initializes from localStorage and handles malformed stored user ids', () => {
    localStorage.setItem(AUTH_TOKEN_KEY, 'persisted-token');
    localStorage.setItem('tabula_auth_session', 'u_missing');

    renderAuth();

    expect(screen.getByTestId('user').textContent).toBe('none');
    expect(screen.getByTestId('admin')).toHaveTextContent('false');
  });

  it('covers auth validation branches without calling the backend', async () => {
    const user = userEvent.setup();

    renderValidationAuth();

    await user.click(screen.getByRole('button', { name: /register missing/i }));
    await user.click(screen.getByRole('button', { name: /register invalid email/i }));
    await user.click(screen.getByRole('button', { name: /register mismatch/i }));
    await user.click(screen.getByRole('button', { name: /register weak/i }));

    await user.click(screen.getByRole('button', { name: /resend empty/i }));
    await user.click(screen.getByRole('button', { name: /verify empty/i }));
    await user.click(screen.getByRole('button', { name: /verify invalid code/i }));

    await user.click(screen.getByRole('button', { name: /reset missing/i }));
    await user.click(screen.getByRole('button', { name: /reset mismatch/i }));
    await user.click(screen.getByRole('button', { name: /reset weak/i }));

    await user.click(screen.getByRole('button', { name: /change no user/i }));

    expect(screen.getByTestId('user').textContent).toBe('none');

    expect(vi.mocked(api.registerUserBackend)).not.toHaveBeenCalled();
    expect(vi.mocked(api.resendVerificationBackend)).not.toHaveBeenCalled();
    expect(vi.mocked(api.verifyEmailCode)).not.toHaveBeenCalled();
    expect(vi.mocked(api.resetPasswordBackend)).not.toHaveBeenCalled();
    expect(vi.mocked(api.changePasswordBackend)).not.toHaveBeenCalled();
  });
});
