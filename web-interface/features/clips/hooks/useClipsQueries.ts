import { useQuery } from '@tanstack/react-query';
import { clipsService } from '../services/clipsService';

export const clipsKeys = {
  byJob: (jobId: string) => ['clips', 'byJob', jobId] as const,
};

export function useJobClips(jobId: string | null) {
  return useQuery({
    queryKey: clipsKeys.byJob(jobId ?? ''),
    queryFn: () => clipsService.getJobClips(jobId!),
    enabled: !!jobId,
    staleTime: 60_000,
  });
}
