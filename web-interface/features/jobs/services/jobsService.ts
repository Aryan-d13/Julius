import { httpClient } from "../../../lib/httpClient";

export const jobsService = {
  createJob: async (payload: any, userId?: string) => {
    const headers: Record<string, string> = {};
    if (userId) {
      headers["X-User-Id"] = userId;
    }
    return httpClient.request<any>("/api/jobs", {
      method: "POST",
      headers,
      body: JSON.stringify(payload),
    });
  },

  retryJob: async (jobId: string) => {
    return httpClient.request<any>(`/api/admin/jobs/${jobId}/retry`, {
      method: "POST",
    });
  },

  cancelJob: async (jobId: string) => {
    return httpClient.request<any>(`/api/admin/jobs/${jobId}/cancel`, {
      method: "POST",
    });
  }
};
