import React from 'react';
import Link from 'next/link';

export default function NotFound() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', gap: '1rem', padding: '2rem', textAlign: 'center', background: '#08090A', color: '#F3F4F6' }}>
      <h1>404 - Panel Not Found</h1>
      <p style={{ fontSize: '0.85rem', color: '#9CA3AF' }}>The requested operational route is invalid or restricted.</p>
      <Link href="/dashboard" style={{ background: '#10B981', color: '#08090A', padding: '0.5rem 1rem', borderRadius: '4px', textDecoration: 'none', fontWeight: 600 }}>
        Return to Control Panel
      </Link>
    </div>
  );
}
