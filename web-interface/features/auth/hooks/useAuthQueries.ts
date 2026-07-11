import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { authService } from '../services/authService';
import type { LoginRequest, RegisterRequest, AuthResponse } from '../../../types';

export const authKeys = {
  sessions: ['auth', 'sessions'] as const,
};

export function useSessions() {
  return useQuery({
    queryKey: authKeys.sessions,
    queryFn: () => authService.getSessions(),
    staleTime: 60_000,
  });
}

export function useLogin() {
  return useMutation({
    mutationFn: (payload: LoginRequest) => authService.login(payload),
    onSuccess: (data: AuthResponse) => {
      if (typeof window !== 'undefined') {
        localStorage.setItem('julius_current_user', JSON.stringify({
          id: data.userId,
          email: data.email,
          fullName: data.fullName,
        }));
      }
    },
  });
}

export function useRegister() {
  return useMutation({
    mutationFn: (payload: RegisterRequest) => authService.register(payload),
  });
}

export function useRevokeSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) => authService.revokeSession(sessionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: authKeys.sessions });
    },
  });
}
