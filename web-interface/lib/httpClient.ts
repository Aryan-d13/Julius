import { BACKEND_URL, AUTH_TOKEN_KEY, ACCESS_TOKEN_COOKIE_KEY } from './constants';

class HttpClient {
  private token: string | null = null;

  constructor() {
    if (typeof window !== 'undefined') {
      this.token = localStorage.getItem(AUTH_TOKEN_KEY);
    }
  }

  setToken(token: string | null): void {
    this.token = token;
    if (typeof window !== 'undefined') {
      if (token) {
        localStorage.setItem(AUTH_TOKEN_KEY, token);
        document.cookie = `${ACCESS_TOKEN_COOKIE_KEY}=${token}; path=/; max-age=86400; SameSite=Lax`;
      } else {
        localStorage.removeItem(AUTH_TOKEN_KEY);
        document.cookie = `${ACCESS_TOKEN_COOKIE_KEY}=; path=/; expires=Thu, 01 Jan 1970 00:00:01 GMT`;
      }
    }
  }

  getToken(): string | null {
    if (!this.token && typeof window !== 'undefined') {
      this.token = localStorage.getItem(AUTH_TOKEN_KEY);
    }
    return this.token;
  }

  private getHeaders(): Record<string, string> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    const t = this.getToken();
    if (t) {
      headers['Authorization'] = `Bearer ${t}`;
    }
    return headers;
  }

  async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const url = `${BACKEND_URL}${path}`;
    const headers: Record<string, string> = {
      ...this.getHeaders(),
      ...(options.headers as Record<string, string> | undefined),
    };

    const response = await fetch(url, {
      ...options,
      headers,
    });

    if (!response.ok) {
      let errorMsg = `API request failed with status: ${response.status}`;
      try {
        const errData = await response.json();
        if (errData && typeof errData.error === 'string') {
          errorMsg = errData.error;
        }
      } catch {
        // ignore JSON parse errors on error responses
      }
      throw new Error(errorMsg);
    }

    if (response.status === 204) {
      return {} as T;
    }

    return response.json() as Promise<T>;
  }
}

export const httpClient = new HttpClient();
