import React from 'react';
import { Loader2 } from 'lucide-react';

export default function Loading() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', gap: '1rem', background: '#08090A', color: '#F3F4F6' }}>
      <Loader2 className="animate-spin" size={32} color="#10B981" />
      <span style={{ fontSize: '0.9rem', fontWeight: 500 }}>Optimizing workspace pipeline caches...</span>
    </div>
  );
}
