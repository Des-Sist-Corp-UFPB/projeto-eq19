import type { DatabaseState, User } from '../types';

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

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '');

const getAuthToken = () => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(AUTH_TOKEN_KEY) || sessionStorage.getItem(AUTH_TOKEN_KEY);
};

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
    const message = await response.text().catch(() => 'Request failed');
    throw new Error(`API request failed (${response.status}): ${message}`);
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

export async function saveServerState(state: DatabaseState): Promise<void> {
  const payload: DatabaseState = {
    users: state.users,
    boardGames: state.boardGames,
    sessions: state.sessions,
    events: state.events,
  };

  await requestJson<{ ok: boolean }>('/state', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export async function generateEventDraft(prompt: string): Promise<AiEventDraftResponse> {
  const payload: AiEventDraftRequest = { prompt };
  return requestJson<AiEventDraftResponse>('/ai/event-drafts', {
    method: 'POST',
    body: JSON.stringify(payload),
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

