"use client";

import React from 'react';

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html>
      <body style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', gap: '1rem', padding: '2rem', background: '#08090A', color: '#F3F4F6', fontFamily: 'sans-serif' }}>
        <h2>System Crash Boundary Exception</h2>
        <p style={{ fontSize: '0.85rem', color: '#9CA3AF' }}>{error.message}</p>
        <button onClick={() => reset()} style={{ background: '#10B981', color: '#08090A', border: 'none', padding: '0.5rem 1rem', borderRadius: '4px', cursor: 'pointer', fontWeight: 600 }}>Reset Application</button>
      </body>
    </html>
  );
}
