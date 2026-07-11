import { useMutation } from '@tanstack/react-query';
import { jobsService } from '../services/jobsService';
import type { CreateJobRequest } from '../../../types';

export function useCreateJob() {
  return useMutation({
    mutationFn: ({ payload, userId }: { payload: CreateJobRequest; userId?: string }) =>
      jobsService.createJob(payload, userId),
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
