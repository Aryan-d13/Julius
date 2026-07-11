'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { AUTH_TOKEN_KEY } from '../lib/constants';

/**
 * Client-side auth guard. Redirects to /login if no token is found.
 * Returns true when the check is complete and the user is authenticated.
 */
export function useAuthGuard(): boolean {
  const router = useRouter();

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const token = localStorage.getItem(AUTH_TOKEN_KEY);
      if (!token) {
        router.replace('/login');
      }
    }
  }, [router]);

  if (typeof window === 'undefined') return false;
  return !!localStorage.getItem(AUTH_TOKEN_KEY);
}
