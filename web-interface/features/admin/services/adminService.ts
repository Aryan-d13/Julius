import { httpClient } from '../../../lib/httpClient';
import type {
  SearchResult, InternalNote, CreateNoteRequest,
  TimelineEvent, AiMetrics, QueueMetrics,
} from '../../../types';

export const adminService = {
  globalSearch: async (query: string): Promise<SearchResult> => {
    return httpClient.request<SearchResult>(
      `/api/admin/search?q=${encodeURIComponent(query)}`,
      { method: 'GET' },
    );
  },

  addInternalNote: async (request: CreateNoteRequest): Promise<InternalNote> => {
    return httpClient.request<InternalNote>('/api/admin/notes', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  getInternalNotes: async (entityType: string, entityId: string): Promise<InternalNote[]> => {
    return httpClient.request<InternalNote[]>(
      `/api/admin/notes/${entityType}/${entityId}`,
      { method: 'GET' },
    );
  },

  getUserTimeline: async (userId: string): Promise<TimelineEvent[]> => {
    return httpClient.request<TimelineEvent[]>(
      `/api/admin/users/${userId}/timeline`,
      { method: 'GET' },
    );
  },

  getOrganizationTimeline: async (orgId: string): Promise<TimelineEvent[]> => {
    return httpClient.request<TimelineEvent[]>(
      `/api/admin/organizations/${orgId}/timeline`,
      { method: 'GET' },
    );
  },

  getAiMetrics: async (): Promise<AiMetrics> => {
    return httpClient.request<AiMetrics>('/api/admin/ai/metrics', {
      method: 'GET',
    });
  },

  getQueueMetrics: async (): Promise<QueueMetrics> => {
    return httpClient.request<QueueMetrics>('/api/admin/queues/metrics', {
      method: 'GET',
    });
  },
};
