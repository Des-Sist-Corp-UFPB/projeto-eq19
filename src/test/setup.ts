import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api');
  return {
    ...actual,
    getServerState: vi.fn().mockResolvedValue(null),
    saveServerState: vi.fn().mockResolvedValue(undefined),
    loginBackend: vi.fn(),
    registerUserBackend: vi.fn(),
    resetPasswordBackend: vi.fn(),
    changePasswordBackend: vi.fn(),
    resendVerificationBackend: vi.fn(),
    verifyEmailCode: vi.fn(),
    getAuditLogs: vi.fn(),
    createEvent: vi.fn(async event => ({
      ...event,
      id: 'e_mock',
      organizerId: 'u1',
      participantIds: ['u1'],
      waitingListIds: [],
      status: 'active' as const,
    })),
    updateEvent: vi.fn(),
    joinEventRequest: vi.fn(),
    leaveEventRequest: vi.fn(),
    cancelEventRequest: vi.fn(),
    completeEventRequest: vi.fn(),
    getEvents: vi.fn(),
    getEvent: vi.fn(),
    getSessions: vi.fn().mockResolvedValue([]),
    getSession: vi.fn(),
    createSession: vi.fn(),
    deleteSessionRequest: vi.fn().mockResolvedValue(undefined),
    createComment: vi.fn(),
    deleteCommentRequest: vi.fn().mockResolvedValue(undefined),
    getFavorites: vi.fn().mockResolvedValue([]),
    addFavoriteRequest: vi.fn(),
    removeFavoriteRequest: vi.fn().mockResolvedValue(undefined),
    getProfile: vi.fn(),
    updateProfile: vi.fn(async profile => ({
      id: 'u2',
      ...profile,
      joinedAt: '2026-01-01T00:00:00',
    })),
  };
});

afterEach(() => {
  cleanup();
});

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

Object.defineProperty(window, 'scrollTo', {
  writable: true,
  value: vi.fn(),
});

Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
  configurable: true,
  value: vi.fn(),
});

class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

vi.stubGlobal('ResizeObserver', ResizeObserver);
