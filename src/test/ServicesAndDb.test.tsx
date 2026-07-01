import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AUTH_TOKEN_KEY } from '../services/api';
import { createLog, getDefaultDatabaseState, normalizeGameCoverUrl, sanitizeDatabaseState } from '../db/database';
import type { DatabaseState } from '../types';

describe('API helpers and database utilities', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('covers successful service calls, auth headers, and empty payload handling', async () => {
    const actualApi = await vi.importActual<typeof import('../services/api')>('../services/api');
    const sampleUser = {
      id: 'u1',
      name: 'Cauã Botelho',
      email: 'caua@tabula.com',
      role: 'student' as const,
      course: 'Computação',
      avatar: 'CB',
      winCount: 0,
      favoriteGames: [],
      joinedAt: '2026-01-01T00:00:00.000Z',
      bio: 'Aluno'
    };

    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ ok: true, message: 'login', token: 'token', user: sampleUser }) })
      .mockResolvedValueOnce({ ok: false, status: 404, text: async () => '' })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => '' })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ status: 'ok', service: 'backend', timestamp: '2026-01-01' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ ok: true, message: 'registered', token: 'token', user: sampleUser }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ ok: true, message: 'reset' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ ok: true, message: 'change' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ ok: true, message: 'resend' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ ok: true, message: 'verify' }) });

    vi.stubGlobal('fetch', fetchMock);
    localStorage.setItem(AUTH_TOKEN_KEY, 'abc-token');

    const login = await actualApi.loginBackend('caua@tabula.com', 'StrongP@ss1');
    await expect(actualApi.getServerState()).resolves.toBeNull();
    await expect(actualApi.saveServerState(getDefaultDatabaseState())).resolves.toBeUndefined();
    await expect(actualApi.pingBackend()).resolves.toMatchObject({ service: 'backend' });

    await expect(actualApi.registerUserBackend('Nova Pessoa', 'nova@tabula.com', 'StrongP@ss1')).resolves.toMatchObject({ ok: true });
    await expect(actualApi.resetPasswordBackend('caua@tabula.com', 'StrongP@ss1')).resolves.toMatchObject({ ok: true });
    await expect(actualApi.changePasswordBackend('caua@tabula.com', 'StrongP@ss1', 'StrongP@ss2')).resolves.toMatchObject({ ok: true });
    await expect(actualApi.resendVerificationBackend('caua@tabula.com')).resolves.toMatchObject({ ok: true });
    await expect(actualApi.verifyEmailCode('caua@tabula.com', '123456')).resolves.toMatchObject({ ok: true });

    expect(login.token).toBe('token');
    expect(fetchMock.mock.calls[0][1]?.headers).toEqual(expect.objectContaining({ Authorization: 'Bearer abc-token' }));
  });

  it('throws useful errors for failed service responses', async () => {
    const actualApi = await vi.importActual<typeof import('../services/api')>('../services/api');
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: false, status: 500, text: async () => 'server down' })
      .mockResolvedValueOnce({ ok: false, status: 400, text: async () => 'bad request' });

    vi.stubGlobal('fetch', fetchMock);

    await expect(actualApi.pingBackend()).rejects.toThrow('API request failed (500): server down');
    await expect(actualApi.getServerState()).rejects.toThrow('API request failed (400): bad request');
  });

  it('normalizes cover URLs and sanitizes legacy database state', () => {
    expect(normalizeGameCoverUrl('Xadrez')).toBe('/images/chess_cover.jpg');
    expect(normalizeGameCoverUrl('Unknown Game', '/images/test.png')).toBe('/images/test.png');
    expect(normalizeGameCoverUrl('Unknown Game')).toBe('/images/tabletop-placeholder.svg');

    const baseState = getDefaultDatabaseState();
    const legacyState = {
      ...baseState,
      boardGames: [{ id: 'g99', name: 'Catan', description: 'Legacy', coverUrl: '', category: 'Estratégia', minPlayers: 2, maxPlayers: 4, avgPlayTime: 60, complexity: 2 }]
    } as DatabaseState;

    const sanitized = sanitizeDatabaseState(legacyState);
    expect(sanitized.boardGames[0].coverUrl).toBe('/images/chess_cover.jpg');
    expect(sanitized.boardGames[0].name).toBe('Xadrez');

    const logEntries = createLog(baseState, 'u1', 'Cauã', 'tested the helpers');
    expect(logEntries[0].action).toContain('tested the helpers');
    expect(logEntries[0].userId).toBe('u1');
  });
});
