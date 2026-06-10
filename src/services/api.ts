export interface PingResponse {
  status: string;
  service: string;
  timestamp: string;
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8119';

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    const message = await response.text().catch(() => 'Request failed');
    throw new Error(`API request failed (${response.status}): ${message}`);
  }

  return (await response.json()) as T;
}

export async function pingBackend(): Promise<PingResponse> {
  return requestJson<PingResponse>('/ping');
}
