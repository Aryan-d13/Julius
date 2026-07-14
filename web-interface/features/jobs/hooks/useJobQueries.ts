import { useMutation } from '@tanstack/react-query';
import { jobsService } from '../services/jobsService';
import type { CreateJobRequest } from '../../../types';

export function useCreateJob() {
  return useMutation({
    mutationFn: ({ workspaceId, payload, userId }: { workspaceId: string; payload: CreateJobRequest; userId?: string }) =>
      jobsService.createJob(workspaceId, payload, userId),
  });
}

export function useRetryJob() {
  return useMutation({
    mutationFn: (jobId: string) => jobsService.retryJob(jobId),
  });
}

export function useCancelJob() {
  return useMutation({
    mutationFn: (jobId: string) => jobsService.cancelJob(jobId),
  });
}
