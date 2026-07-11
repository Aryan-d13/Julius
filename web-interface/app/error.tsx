"use client";

import React from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button } from '../components/ui/button';

export default function ErrorBoundary({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', gap: '1.25rem', padding: '2rem', textAlign: 'center', background: '#08090A', color: '#F3F4F6' }}>
      <AlertTriangle size={48} color="#EF4444" />
      <h2>Something went wrong!</h2>
      <p style={{ fontSize: '0.85rem', color: '#9CA3AF', maxWidth: '400px' }}>{error.message || 'An unexpected operational boundary error occurred.'}</p>
      <Button variant="primary" onClick={() => reset()}>Try Again</Button>
    </div>
  );
}
