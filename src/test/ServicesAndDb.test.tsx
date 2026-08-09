import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AUTH_TOKEN_KEY } from '../services/api';
import { getDefaultDatabaseState, normalizeGameCoverUrl, sanitizeDatabaseState } from '../db/database';
import type { DatabaseState } from '../types';
import { getTestDatabaseState } from './testState';

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

  it('requests the official audit endpoint with filters and bearer authentication', async () => {
    const actualApi = await vi.importActual<typeof import('../services/api')>('../services/api');
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ items: [], page: 2, pageSize: 25, total: 0 }),
    });
    vi.stubGlobal('fetch', fetchMock);
    sessionStorage.setItem(AUTH_TOKEN_KEY, 'admin-token');

    await actualApi.getAuditLogs({
      page: 2,
      pageSize: 25,
      action: 'PROFILE_UPDATED',
      userId: 'u_admin',
      success: true,
    });

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toContain('/audit-logs?');
    expect(url).toContain('action=PROFILE_UPDATED');
    expect(url).toContain('userId=u_admin');
    expect(url).toContain('success=true');
    expect(options.headers).toEqual(expect.objectContaining({
      Authorization: 'Bearer admin-token',
      Accept: 'application/json',
    }));
  });

  it('requests a relational session detail with authentication and an encoded id', async () => {
    const actualApi = await vi.importActual<typeof import('../services/api')>('../services/api');
    const detail = { ...getTestDatabaseState().sessions[0], id: 'session/id' };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => JSON.stringify(detail),
    });
    vi.stubGlobal('fetch', fetchMock);
    sessionStorage.setItem(AUTH_TOKEN_KEY, 'session-token');

    await expect(actualApi.getSession('session/id')).resolves.toEqual(detail);

    expect(fetchMock).toHaveBeenCalledWith('/api/sessions/session%2Fid', expect.objectContaining({
      method: 'GET',
      headers: expect.objectContaining({ Authorization: 'Bearer session-token' }),
    }));
  });

  it('covers the remaining session, event, favorite, and game helper wrappers', async () => {
    const actualApi = await vi.importActual<typeof import('../services/api')>('../services/api');
    const sessionPayload = {
      gameId: 'g1',
      date: '2026-01-01',
      location: 'Casa',
      participantIds: ['u1'],
      winnerId: null,
      duration: 45,
      notes: 'Partida teste',
    };
    const eventPayload = {
      gameId: 'g1',
      date: '2026-01-02',
      time: '19:00',
      location: 'Sala',
      maxParticipants: 4,
      description: 'Evento teste',
    };
    const completionPayload = {
      winnerId: 'u1',
      duration: 30,
      notes: 'Concluído',
      initialComment: 'Boa partida',
      photoUrl: 'https://example.com/photo.png',
    };

    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ id: 's1' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => '' })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ id: 'c1' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => '' })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify(['g1']) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ gameId: 'g1' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => '' })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({ users: [], sessions: [], events: [], boardGames: [] }),
        text: async () => JSON.stringify({ users: [], sessions: [], events: [], boardGames: [] }),
      })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ id: 'e1' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ id: 'e2' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ id: 'e3' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ event: { id: 'e4' }, waitlisted: false }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ event: { id: 'e5' }, promoted: true }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ id: 'e6' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ id: 'e7' }) })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        text: async () => JSON.stringify({
          status: 'draft',
          draft: {
            gameId: 'g1',
            gameName: 'Catan',
            date: '2026-01-02',
            time: '19:00',
            location: 'Sala',
            maxParticipants: 4,
            description: 'Evento teste',
            warnings: [],
          },
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        text: async () => JSON.stringify({
          status: 'draft',
          draft: {
            gameId: 'g1',
            gameName: 'Catan',
            date: '2026-01-03',
            time: '20:00',
            location: 'Casa',
            maxParticipants: 6,
            description: 'Refinado',
            warnings: ['x'],
          },
        }),
      })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify([{ id: 'g1', name: 'Catan' }]) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ id: 'g2', name: 'Azul' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ id: 'g2', name: 'Azul' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, text: async () => '' });

    vi.stubGlobal('fetch', fetchMock);
    sessionStorage.setItem(AUTH_TOKEN_KEY, 'helper-token');

    await expect(actualApi.createSession(sessionPayload)).resolves.toMatchObject({ id: 's1' });
    await expect(actualApi.deleteSessionRequest('s1')).resolves.toBeUndefined();
    await expect(actualApi.createComment('s1', 'Muito bom')).resolves.toMatchObject({ id: 'c1' });
    await expect(actualApi.deleteCommentRequest('s1', 'c1')).resolves.toBeUndefined();
    await expect(actualApi.getFavorites()).resolves.toEqual(['g1']);
    await expect(actualApi.addFavoriteRequest('g1')).resolves.toMatchObject({ gameId: 'g1' });
    await expect(actualApi.removeFavoriteRequest('g1')).resolves.toBeUndefined();

    await expect(actualApi.getServerState()).resolves.toEqual({ users: [], sessions: [], events: [], boardGames: [] });

    await expect(actualApi.getEvent('e1')).resolves.toMatchObject({ id: 'e1' });
    await expect(actualApi.createEvent(eventPayload)).resolves.toMatchObject({ id: 'e2' });
    await expect(actualApi.updateEvent('e3', eventPayload)).resolves.toMatchObject({ id: 'e3' });
    await expect(actualApi.joinEventRequest('e4')).resolves.toEqual({ event: { id: 'e4' }, waitlisted: false });
    await expect(actualApi.leaveEventRequest('e5')).resolves.toEqual({ event: { id: 'e5' }, promoted: true });
    await expect(actualApi.cancelEventRequest('e6')).resolves.toMatchObject({ id: 'e6' });
    await expect(actualApi.completeEventRequest('e7', completionPayload)).resolves.toMatchObject({ id: 'e7' });
    await expect(actualApi.generateEventDraft('Crie um evento')).resolves.toMatchObject({ status: 'draft' });
    await expect(actualApi.refineEventDraft('Melhore', { gameId: 'g1', gameName: 'Catan', date: '2026-01-02', time: '19:00', location: 'Sala', maxParticipants: 4, description: 'Evento teste', warnings: [] })).resolves.toMatchObject({ status: 'draft' });

    await expect(actualApi.getGames()).resolves.toEqual([{ id: 'g1', name: 'Catan' }]);
    await expect(actualApi.createGame({ name: 'Azul', category: 'Family', minPlayers: 2, maxPlayers: 4, avgPlayTime: 30, complexity: 2, description: 'Jogo' })).resolves.toMatchObject({ id: 'g2' });
    await expect(actualApi.updateGame({ id: 'g2', name: 'Azul', category: 'Family', minPlayers: 2, maxPlayers: 4, avgPlayTime: 30, complexity: 2, description: 'Jogo' })).resolves.toMatchObject({ id: 'g2' });
    await expect(actualApi.deleteGameRequest('g2')).resolves.toBeUndefined();

    const [sessionUrl, sessionOptions] = fetchMock.mock.calls[0];
    expect(sessionUrl).toBe('/api/sessions');
    expect(sessionOptions).toMatchObject({ method: 'POST', body: JSON.stringify(sessionPayload) });
    expect(sessionOptions?.headers).toEqual(expect.objectContaining({ Authorization: 'Bearer helper-token' }));
    expect(fetchMock.mock.calls[1][0]).toBe('/api/sessions/s1');
    expect(fetchMock.mock.calls[18][0]).toBe('/api/games');
  });

  it('does not expose whole-state persistence', async () => {
    const actualApi = await vi.importActual<typeof import('../services/api')>('../services/api');
    expect(actualApi).not.toHaveProperty('saveServerState');
  });

  it('normalizes covers and preserves valid relational state without injecting demo data', () => {
    expect(normalizeGameCoverUrl('Xadrez')).toBe('/images/chess_cover.jpg');
    expect(normalizeGameCoverUrl('Unknown Game', '/images/test.png')).toBe('/images/test.png');
    expect(normalizeGameCoverUrl('Unknown Game')).toBe('/images/tabletop-placeholder.svg');

    const relationalState: DatabaseState = {
      users: [], sessions: [], events: [],
      boardGames: [{ id: 'g-catan', name: 'Catan', description: 'Relational', coverUrl: '/images/custom.png', category: 'Strategy', minPlayers: 3, maxPlayers: 4, avgPlayTime: 60, complexity: 2.3 }],
    };
    const sanitized = sanitizeDatabaseState(relationalState);
    expect(sanitized.boardGames).toHaveLength(1);
    expect(sanitized.boardGames[0].name).toBe('Catan');
    expect(sanitized.users).toEqual([]);
    expect(getDefaultDatabaseState()).toEqual({ users: [], boardGames: [], sessions: [], events: [] });
    expect(() => sanitizeDatabaseState({ users: [] } as DatabaseState)).toThrow('Invalid relational database state');
  });
});
