import { useQuery } from '@tanstack/react-query';
import { clipsService } from '../services/clipsService';

export const clipsKeys = {
  byJob: (jobId: string) => ['clips', 'byJob', jobId] as const,
};

export function useJobClips(workspaceId: string | null, jobId: string | null) {
  return useQuery({
    queryKey: clipsKeys.byJob(jobId ?? ''),
    queryFn: () => clipsService.getJobClips(workspaceId!, jobId!),
    enabled: !!workspaceId && !!jobId,
    staleTime: 60_000,
  });
}
