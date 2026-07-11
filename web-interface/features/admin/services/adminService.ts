import { httpClient } from "../../../lib/httpClient";

export const adminService = {
  globalSearch: async (query: string) => {
    return httpClient.request<any>(`/api/admin/search?q=${encodeURIComponent(query)}`, {
      method: "GET",
    });
  },

  addInternalNote: async (entityType: string, entityId: string, noteText: string) => {
    return httpClient.request<any>("/api/admin/notes", {
      method: "POST",
      body: JSON.stringify({ entityType, entityId, noteText }),
    });
  },

  getInternalNotes: async (entityType: string, entityId: string) => {
    return httpClient.request<any[]>(`/api/admin/notes/${entityType}/${entityId}`, {
      method: "GET",
    });
  },

  getUserTimeline: async (userId: string) => {
    return httpClient.request<any[]>(`/api/admin/users/${userId}/timeline`, {
      method: "GET",
    });
  },

  getOrganizationTimeline: async (orgId: string) => {
    return httpClient.request<any[]>(`/api/admin/organizations/${orgId}/timeline`, {
      method: "GET",
    });
  },

  getAiMetrics: async () => {
    return httpClient.request<any>("/api/admin/ai/metrics", {
      method: "GET",
    });
  },

  getQueueMetrics: async () => {
    return httpClient.request<any>("/api/admin/queues/metrics", {
      method: "GET",
    });
  }
};
