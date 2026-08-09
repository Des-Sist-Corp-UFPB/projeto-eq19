import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Login } from '../pages/Login';
import * as api from '../services/api';
import { renderWithProviders } from './renderWithProviders';

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api');
  return {
    ...actual,
    getServerState: vi.fn(),
    loginBackend: vi.fn(),
  };
});

const mockedLoginBackend = vi.mocked(api.loginBackend);
const mockedGetServerState = vi.mocked(api.getServerState);

describe('Login page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetServerState.mockResolvedValue(null);
    mockedLoginBackend.mockResolvedValue({
      ok: true,
      message: 'Login realizado com sucesso.',
      token: 'token-123',
      user: {
        id: 'u1',
        name: 'Cauã Botelho',
        email: 'caua@tabula.com',
        role: 'student',
        course: 'Computação',
        avatar: 'CB',
        winCount: 0,
        favoriteGames: [],
        joinedAt: '2026-01-01T00:00:00.000Z',
        bio: 'Aluno',
      },
    });
  });

  it('submits credentials to the auth service', async () => {
    const user = userEvent.setup();
    const { container } = renderWithProviders(<Login />);

    const emailInput = container.querySelector('input[type="email"]') as HTMLInputElement;
    const passwordInput = container.querySelector('input[type="password"]') as HTMLInputElement;

    await user.type(emailInput, 'caua@tabula.com');
    await user.type(passwordInput, 'StrongP@ss1');
    await user.click(screen.getByRole('button', { name: /Entrar/i }));

    await waitFor(() => expect(mockedLoginBackend).toHaveBeenCalledWith('caua@tabula.com', 'StrongP@ss1'));
  });
});
