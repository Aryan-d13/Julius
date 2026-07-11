import { httpClient } from "../../../lib/httpClient";

export const authService = {
  login: async (payload: any) => {
    const res = await httpClient.request<any>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    if (res.accessToken) {
      httpClient.setToken(res.accessToken);
    }
    return res;
  },

  register: async (payload: any) => {
    return httpClient.request<any>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  getSessions: async () => {
    return httpClient.request<any[]>("/api/auth/sessions", {
      method: "GET",
    });
  },

  revokeSession: async (sessionId: string) => {
    return httpClient.request<any>(`/api/auth/sessions/${sessionId}`, {
      method: "DELETE",
    });
  },

  logout: () => {
    httpClient.setToken(null);
  }
};
