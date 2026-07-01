import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Register } from '../pages/Register';
import * as api from '../services/api';
import { renderWithProviders } from './renderWithProviders';

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api');
  return {
    ...actual,
    getServerState: vi.fn(),
    saveServerState: vi.fn(),
    registerUserBackend: vi.fn(),
  };
});

const mockedRegisterUserBackend = vi.mocked(api.registerUserBackend);
const mockedGetServerState = vi.mocked(api.getServerState);
const mockedSaveServerState = vi.mocked(api.saveServerState);

describe('Register page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetServerState.mockResolvedValue(null);
    mockedSaveServerState.mockResolvedValue();
    mockedRegisterUserBackend.mockResolvedValue({
      ok: true,
      message: 'Conta criada com sucesso.',
      token: 'token-123',
      user: {
        id: 'u9',
        name: 'Nova Usuária',
        email: 'nova@tabula.com',
        role: 'student',
        course: 'Design',
        avatar: 'NU',
        winCount: 0,
        favoriteGames: [],
        joinedAt: '2026-01-01T00:00:00.000Z',
        bio: 'Nova',
      },
    });
  });

  it('submits account details to the registration service', async () => {
    const user = userEvent.setup();
    const { container } = renderWithProviders(<Register />);

    const inputs = container.querySelectorAll('input.form-input');
    await user.type(inputs[0] as HTMLInputElement, 'Nova Usuária');
    await user.type(inputs[1] as HTMLInputElement, 'nova@tabula.com');
    await user.type(inputs[2] as HTMLInputElement, 'StrongP@ss1');
    await user.type(inputs[3] as HTMLInputElement, 'StrongP@ss1');
    await user.click(screen.getByRole('button', { name: /Criar conta/i }));

    await waitFor(() => expect(mockedRegisterUserBackend).toHaveBeenCalled());
  });
});
