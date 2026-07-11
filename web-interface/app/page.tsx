"use client";

import React, { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowRight } from 'lucide-react';
import { Button } from '../components/ui/button';

export default function LandingPage() {
  const router = useRouter();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Enter') {
        router.push('/login');
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [router]);

  return (
    <div className="landing-layout">
      <div className="landing-hero">
        <span className="landing-badge">Platform V1 Live</span>
        <h1>Video Slicing. Powered by AI.</h1>
        <p>Paste any YouTube link, transcribe dialogue waves with local whisper, analyze virality scores with Gemini, and render clips instantly.</p>
        <div className="landing-actions">
          <Button variant="primary" onClick={() => router.push('/login')}>
            Enter Clipper Control
            <ArrowRight size={16} />
          </Button>
          <span className="keyboard-hint">Press Enter to Start</span>
        </div>
      </div>
    </div>
  );
}
