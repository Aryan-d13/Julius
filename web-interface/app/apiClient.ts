const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

export interface ApiClientConfig {
  token: string | null;
}

class ApiClient {
  private token: string | null = null;

  setToken(token: string | null) {
    this.token = token;
  }

  private getHeaders(contentType: string = "application/json"): HeadersInit {
    const headers: Record<string, string> = {};
    if (contentType) {
      headers["Content-Type"] = contentType;
    }
    if (this.token) {
      headers["Authorization"] = `Bearer ${this.token}`;
    }
    return headers;
  }

  async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const url = `${BACKEND_URL}${path}`;
    const headers = {
      ...this.getHeaders((options.body instanceof FormData) ? "" : "application/json"),
      ...options.headers,
    };

    const response = await fetch(url, {
      ...options,
      headers,
    });

    if (!response.ok) {
      let errorMsg = `API request failed with status: ${response.status}`;
      try {
        const errData = await response.json();
        if (errData && errData.error) {
          errorMsg = errData.error;
        }
      } catch (e) {
        // use default status error
      }
      throw new Error(errorMsg);
    }

    // Handle empty response bodies
    if (response.status === 204) {
      return {} as T;
    }

    return response.json();
  }

  // Auth Operations
  async login(payload: any) {
    return this.request<any>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  }

  async register(payload: any) {
    return this.request<any>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  }

  async getSessions() {
    return this.request<any[]>("/api/auth/sessions", {
      method: "GET",
    });
  }

  async revokeSession(sessionId: string) {
    return this.request<any>(`/api/auth/sessions/${sessionId}`, {
      method: "DELETE",
    });
  }

  // Job Operations
  async createJob(payload: any, userId?: string) {
    const headers: Record<string, string> = {};
    if (userId) {
      headers["X-User-Id"] = userId;
    }
    return this.request<any>("/api/jobs", {
      method: "POST",
      headers,
      body: JSON.stringify(payload),
    });
  }

  async getJobClips(jobId: string) {
    return this.request<any[]>(`/api/jobs/${jobId}/clips`, {
      method: "GET",
    });
  }

  // Admin / Operations
  async globalSearch(query: string) {
    return this.request<any>(`/api/admin/search?q=${encodeURIComponent(query)}`, {
      method: "GET",
    });
  }

  async addInternalNote(entityType: string, entityId: string, noteText: string) {
    return this.request<any>("/api/admin/notes", {
      method: "POST",
      body: JSON.stringify({ entityType, entityId, noteText }),
    });
  }

  async getInternalNotes(entityType: string, entityId: string) {
    return this.request<any[]>(`/api/admin/notes/${entityType}/${entityId}`, {
      method: "GET",
    });
  }

  async getUserTimeline(userId: string) {
    return this.request<any[]>(`/api/admin/users/${userId}/timeline`, {
      method: "GET",
    });
  }

  async getOrganizationTimeline(orgId: string) {
    return this.request<any[]>(`/api/admin/organizations/${orgId}/timeline`, {
      method: "GET",
    });
  }

  async retryJob(jobId: string) {
    return this.request<any>(`/api/admin/jobs/${jobId}/retry`, {
      method: "POST",
    });
  }

  async cancelJob(jobId: string) {
    return this.request<any>(`/api/admin/jobs/${jobId}/cancel`, {
      method: "POST",
    });
  }

  async getAiMetrics() {
    return this.request<any>("/api/admin/ai/metrics", {
      method: "GET",
    });
  }

  async getQueueMetrics() {
    return this.request<any>("/api/admin/queues/metrics", {
      method: "GET",
    });
  }
}

export const apiClient = new ApiClient();
