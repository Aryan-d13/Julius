import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminService } from '../services/adminService';
import type { CreateNoteRequest } from '../../../types';

export const adminKeys = {
  aiMetrics: ['admin', 'ai-metrics'] as const,
  queueMetrics: ['admin', 'queue-metrics'] as const,
  notes: (entityType: string, entityId: string) =>
    ['admin', 'notes', entityType, entityId] as const,
  userTimeline: (userId: string) => ['admin', 'timeline', 'user', userId] as const,
};

export function useAiMetrics() {
  return useQuery({
    queryKey: adminKeys.aiMetrics,
    queryFn: () => adminService.getAiMetrics(),
    staleTime: 120_000,
    retry: 1,
  });
}

export function useQueueMetrics() {
  return useQuery({
    queryKey: adminKeys.queueMetrics,
    queryFn: () => adminService.getQueueMetrics(),
    staleTime: 30_000,
    retry: 1,
  });
}

export function useInternalNotes(entityType: string, entityId: string) {
  return useQuery({
    queryKey: adminKeys.notes(entityType, entityId),
    queryFn: () => adminService.getInternalNotes(entityType, entityId),
    enabled: !!entityId,
  });
}

export function useAddNote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateNoteRequest) => adminService.addInternalNote(request),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: adminKeys.notes(variables.entityType, variables.entityId),
      });
    },
  });
}

export function useUserTimeline(userId: string) {
  return useQuery({
    queryKey: adminKeys.userTimeline(userId),
    queryFn: () => adminService.getUserTimeline(userId),
    enabled: !!userId,
  });
}
