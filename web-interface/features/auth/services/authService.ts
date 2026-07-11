import { httpClient } from '../../../lib/httpClient';
import type { LoginRequest, AuthResponse, RegisterRequest, RegisterResponse, UserSession } from '../../../types';

export const authService = {
  login: async (payload: LoginRequest): Promise<AuthResponse> => {
    const res = await httpClient.request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    if (res.accessToken) {
      httpClient.setToken(res.accessToken);
    }
    return res;
  },

  register: async (payload: RegisterRequest): Promise<RegisterResponse> => {
    return httpClient.request<RegisterResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  getSessions: async (): Promise<UserSession[]> => {
    return httpClient.request<UserSession[]>('/api/auth/sessions', {
      method: 'GET',
    });
  },

  revokeSession: async (sessionId: string): Promise<void> => {
    await httpClient.request<Record<string, never>>(`/api/auth/sessions/${sessionId}`, {
      method: 'DELETE',
    });
  },

  logout: (): void => {
    httpClient.setToken(null);
  },
};
