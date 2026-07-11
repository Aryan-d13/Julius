import { httpClient } from '../../../lib/httpClient';
import type { CreateJobRequest, CreateJobResponse, JobActionResponse } from '../../../types';

export const jobsService = {
  createJob: async (payload: CreateJobRequest, userId?: string): Promise<CreateJobResponse> => {
    const headers: Record<string, string> = {};
    if (userId) {
      headers['X-User-Id'] = userId;
    }
    return httpClient.request<CreateJobResponse>('/api/jobs', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    });
  },

  retryJob: async (jobId: string): Promise<JobActionResponse> => {
    return httpClient.request<JobActionResponse>(`/api/admin/jobs/${jobId}/retry`, {
      method: 'POST',
    });
  },

  cancelJob: async (jobId: string): Promise<JobActionResponse> => {
    return httpClient.request<JobActionResponse>(`/api/admin/jobs/${jobId}/cancel`, {
      method: 'POST',
    });
  },
};
