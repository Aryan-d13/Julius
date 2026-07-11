'use client';

import { useState, useCallback, useMemo } from 'react';
import { CURRENT_USER_KEY } from '../lib/constants';
import type { CurrentUser } from '../types';

export function useCurrentUser() {
  const [user, setUserState] = useState<CurrentUser | null>(() => {
    if (typeof window === 'undefined') return null;
    const raw = localStorage.getItem(CURRENT_USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as CurrentUser;
    } catch {
      return null;
    }
  });

  const setUser = useCallback((u: CurrentUser | null) => {
    setUserState(u);
    if (typeof window !== 'undefined') {
      if (u) {
        localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(u));
      } else {
        localStorage.removeItem(CURRENT_USER_KEY);
      }
    }
  }, []);

  return useMemo(() => ({ user, setUser }), [user, setUser]);
}
