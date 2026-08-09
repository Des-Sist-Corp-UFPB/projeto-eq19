import type { Comment, DatabaseState, Event, Session, User } from '../types';

export const AUTH_TOKEN_KEY = 'tabula_auth_token';

export interface PingResponse {
  status: string;
  service: string;
  timestamp: string;
}

export interface AuthResponse {
  ok: boolean;
  message: string;
  token: string;
  user: User;
}

export interface ProfileResponse {
  id: string;
  name: string;
  course: string;
  bio: string;
  avatarUrl?: string;
  joinedAt: string;
}

export interface ProfileUpdate {
  name: string;
  course: string;
  bio: string;
  avatarUrl?: string;
}

export type GameInput = Omit<import('../types').BoardGame, 'id'>;

export interface AuditLogEntry {
  id: number;
  userId: string | null;
  action: string;
  resourceType: string | null;
  resourceId: string | null;
  details: Record<string, unknown>;
  ipAddress: string | null;
  userAgent: string | null;
  success: boolean;
  traceId: string | null;
  createdAt: string;
}

export interface AuditLogPage {
  items: AuditLogEntry[];
  page: number;
  pageSize: number;
  total: number;
}

export interface AuditLogFilters {
  page?: number;
  pageSize?: number;
  action?: string;
  userId?: string;
  resourceType?: string;
  resourceId?: string;
  success?: boolean;
  startDate?: string;
  endDate?: string;
}

export interface AiEventDraftRequest {
  prompt: string;
}

export interface AiEventDraftResponse {
  gameId: string;
  gameName: string;
  date: string;
  time: string;
  location: string;
  maxParticipants: number;
  description: string;
  warnings: string[];
}

export type AiEventPartialDraftResponse = Partial<AiEventDraftResponse>;

export type AiEventAssistantResponse =
  | { status: 'draft'; draft: AiEventDraftResponse }
  | {
      status: 'needs_clarification';
      reasonCode: 'missing_required_information';
      missingFields: string[];
      message: string;
      partialDraft: AiEventPartialDraftResponse;
    }
  | { status: 'unsupported'; reasonCode: 'not_event_creation_request' };

export interface AiEventRefinementRequest {
  instruction: string;
  currentDraft: AiEventDraftResponse;
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '');

const getAuthToken = () => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(AUTH_TOKEN_KEY) || sessionStorage.getItem(AUTH_TOKEN_KEY);
};

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, message: string, code?: string, body?: string) {
    super(`API request failed (${status}): ${body || message}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getAuthToken();
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    const body = await response.text().catch(() => '');
    let message = 'Request failed';
    let code: string | undefined;
    try {
      const parsed = JSON.parse(body) as { error?: string; code?: string };
      message = parsed.error || message;
      code = parsed.code;
    } catch {
      // External details are intentionally not surfaced by callers.
    }
    throw new ApiError(response.status, message, code, body);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function pingBackend(): Promise<PingResponse> {
  return requestJson<PingResponse>('/ping');
}

export async function getServerState(): Promise<DatabaseState | null> {
  const response = await fetch(`${API_BASE_URL}/state`, {
    headers: { Accept: 'application/json' },
  });

  if (response.status === 404) return null;
  if (!response.ok) {
    const message = await response.text().catch(() => 'Request failed');
    throw new Error(`API request failed (${response.status}): ${message}`);
  }

  return (await response.json()) as DatabaseState;
}

export async function saveServerState(state: DatabaseState, includeEvents = false): Promise<void> {
  const payload: Omit<DatabaseState, 'events' | 'sessions'> & { events?: Event[] } = {
    users: state.users,
    boardGames: state.boardGames.map(game => ({
      id: game.id,
      name: game.name,
      description: game.description,
      coverUrl: game.coverUrl,
      category: game.category,
      minPlayers: game.minPlayers,
      maxPlayers: game.maxPlayers,
      avgPlayTime: game.avgPlayTime,
      complexity: game.complexity,
    })),
  };
  if (includeEvents) payload.events = state.events;

  await requestJson<{ ok: boolean }>('/state', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export type SessionWriteRequest = Pick<
  Session,
  'gameId' | 'date' | 'location' | 'participantIds' | 'winnerId' | 'duration' | 'notes'
>;

export async function getSessions(): Promise<Session[]> {
  return requestJson<Session[]>('/sessions', { method: 'GET', headers: { Accept: 'application/json' } });
}

export async function getSession(id: string): Promise<Session> {
  return requestJson<Session>(`/sessions/${encodeURIComponent(id)}`, { method: 'GET' });
}

export async function createSession(session: SessionWriteRequest): Promise<Session> {
  return requestJson<Session>('/sessions', { method: 'POST', body: JSON.stringify(session) });
}

export async function deleteSessionRequest(id: string): Promise<void> {
  await requestJson<void>(`/sessions/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export async function createComment(sessionId: string, content: string): Promise<Comment> {
  return requestJson<Comment>(`/sessions/${encodeURIComponent(sessionId)}/comments`, {
    method: 'POST', body: JSON.stringify({ content }),
  });
}

export async function deleteCommentRequest(sessionId: string, commentId: string): Promise<void> {
  await requestJson<void>(
    `/sessions/${encodeURIComponent(sessionId)}/comments/${encodeURIComponent(commentId)}`,
    { method: 'DELETE' },
  );
}

export async function getFavorites(): Promise<string[]> {
  return requestJson<string[]>('/favorites', { method: 'GET' });
}

export async function addFavoriteRequest(gameId: string): Promise<{ gameId: string }> {
  return requestJson(`/favorites/${encodeURIComponent(gameId)}`, { method: 'POST' });
}

export async function removeFavoriteRequest(gameId: string): Promise<void> {
  await requestJson<void>(`/favorites/${encodeURIComponent(gameId)}`, { method: 'DELETE' });
}

export type EventWriteRequest = Pick<
  Event,
  'gameId' | 'date' | 'time' | 'location' | 'maxParticipants' | 'description'
>;

export async function getEvents(): Promise<Event[]> {
  return requestJson<Event[]>('/events', { method: 'GET', headers: { Accept: 'application/json' } });
}

export async function getEvent(id: string): Promise<Event> {
  return requestJson<Event>(`/events/${encodeURIComponent(id)}`, { method: 'GET' });
}

export async function createEvent(event: EventWriteRequest): Promise<Event> {
  return requestJson<Event>('/events', { method: 'POST', body: JSON.stringify(event) });
}

export async function updateEvent(id: string, event: EventWriteRequest): Promise<Event> {
  return requestJson<Event>(`/events/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(event),
  });
}

export async function joinEventRequest(id: string): Promise<{ event: Event; waitlisted: boolean }> {
  return requestJson(`/events/${encodeURIComponent(id)}/join`, { method: 'POST' });
}

export async function leaveEventRequest(id: string): Promise<{ event: Event; promoted: boolean }> {
  return requestJson(`/events/${encodeURIComponent(id)}/leave`, { method: 'POST' });
}

export async function cancelEventRequest(id: string): Promise<Event> {
  return requestJson(`/events/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
}

export async function completeEventRequest(
  id: string,
  completion: {
    winnerId: string | null;
    duration: number;
    notes: string;
    initialComment?: string;
    photoUrl?: string;
  },
): Promise<Event> {
  return requestJson(`/events/${encodeURIComponent(id)}/complete`, {
    method: 'POST',
    body: JSON.stringify(completion),
  });
}

export async function generateEventDraft(prompt: string): Promise<AiEventAssistantResponse> {
  const payload: AiEventDraftRequest = { prompt };
  return requestJson<AiEventAssistantResponse>('/ai/event-drafts', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function refineEventDraft(
  instruction: string,
  currentDraft: AiEventDraftResponse,
): Promise<AiEventAssistantResponse> {
  return requestJson<AiEventAssistantResponse>('/ai/event-drafts/refine', {
    method: 'POST',
    body: JSON.stringify({ instruction, currentDraft }),
  });
}

export async function loginBackend(email: string, password: string): Promise<AuthResponse> {
  return requestJson<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export async function registerUserBackend(name: string, email: string, password: string): Promise<AuthResponse> {
  return requestJson<AuthResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ name, email, password }),
  });
}

export async function resetPasswordBackend(email: string, newPassword: string): Promise<{ ok: boolean; message: string }> {
  return requestJson<{ ok: boolean; message: string }>('/auth/reset-password', {
    method: 'POST',
    body: JSON.stringify({ email, newPassword }),
  });
}

export async function changePasswordBackend(
  email: string,
  currentPassword: string,
  newPassword: string,
): Promise<{ ok: boolean; message: string }> {
  return requestJson<{ ok: boolean; message: string }>('/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ email, currentPassword, newPassword }),
  });
}

export async function resendVerificationBackend(email: string): Promise<{ ok: boolean; message: string }> {
  return requestJson<{ ok: boolean; message: string }>('/auth/resend-verification', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export async function verifyEmailCode(email: string, code: string): Promise<{ ok: boolean; message: string }> {
  return requestJson<{ ok: boolean; message: string }>('/auth/verify-email', {
    method: 'POST',
    body: JSON.stringify({ email, code }),
  });
}

export async function getProfile(): Promise<ProfileResponse> {
  return requestJson<ProfileResponse>('/profile', { method: 'GET' });
}

export async function updateProfile(profile: ProfileUpdate): Promise<ProfileResponse> {
  return requestJson<ProfileResponse>('/profile', {
    method: 'PUT',
    body: JSON.stringify(profile),
  });
}

export async function getGames(): Promise<import('../types').BoardGame[]> {
  return requestJson('/games', { method: 'GET' });
}
export async function createGame(game: GameInput): Promise<import('../types').BoardGame> {
  return requestJson('/games', { method: 'POST', body: JSON.stringify(game) });
}
export async function updateGame(game: import('../types').BoardGame): Promise<import('../types').BoardGame> {
  return requestJson(`/games/${encodeURIComponent(game.id)}`, { method: 'PUT', body: JSON.stringify(game) });
}
export async function deleteGameRequest(id: string): Promise<void> {
  return requestJson(`/games/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export async function getAuditLogs(filters: AuditLogFilters = {}): Promise<AuditLogPage> {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value));
    }
  });
  const suffix = query.size > 0 ? `?${query.toString()}` : '';
  return requestJson<AuditLogPage>(`/audit-logs${suffix}`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });
}

