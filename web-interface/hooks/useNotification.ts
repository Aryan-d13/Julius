'use client';

import { useState, useCallback } from 'react';

export function useNotification() {
  const [text, setText] = useState<string | null>(null);

  const show = useCallback((msg: string, durationMs = 3000) => {
    setText(msg);
    setTimeout(() => setText(null), durationMs);
  }, []);

  const dismiss = useCallback(() => setText(null), []);

  return { text, show, dismiss };
}
