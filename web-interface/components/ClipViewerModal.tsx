import React, { useState, useRef } from 'react';
import { X, Download } from 'lucide-react';
import { JobClip } from '../types';
import { Button } from './ui/button';

interface ClipViewerModalProps {
  clip: JobClip;
  onClose: () => void;
  showNotification: (msg: string) => void;
}

export const ClipViewerModal: React.FC<ClipViewerModalProps> = ({ 
  clip, 
  onClose,
  showNotification
}) => {
  const videoPlayerRef = useRef<HTMLVideoElement>(null);
  const [clipTab, setClipTab] = useState<'TRANSCRIPT' | 'AI_EXPLANATIONS' | 'SOCIAL_COPY'>('TRANSCRIPT');
  const [isClipGlow, setIsClipGlow] = useState(false);
  const [isFavorite, setIsFavorite] = useState<Record<string, boolean>>({});

  const handleWordClick = (seconds: number) => {
    if (videoPlayerRef.current) {
      videoPlayerRef.current.currentTime = seconds;
      videoPlayerRef.current.play().catch(() => {});
      setIsClipGlow(true);
      setTimeout(() => setIsClipGlow(false), 800);
    }
  };

  const mediaUrl = `http://localhost:8080/data/jobs/${clip.jobId}/clips/${clip.filename}`;

  return (
    <div className="cmd-palette-overlay" style={{ paddingTop: '5vh' }} onClick={onClose}>
      <div className="cmd-palette-box" style={{ maxWidth: '850px', maxHeight: '85vh', height: '100%' }} onClick={e => e.stopPropagation()}>
        <div className="panel-header" style={{ padding: '1rem', marginBottom: 0 }}>
          <h3>Clip Detail Viewer — Fragment #{clip.clipIndex}</h3>
          <button className="icon-btn" onClick={onClose}><X size={18} /></button>
        </div>
        
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', padding: '1.5rem', overflowY: 'auto', flex: 1 }}>
          {/* Left Video Player */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div className={`player-frame ${isClipGlow ? 'active-glow' : ''}`}>
              <video 
                ref={videoPlayerRef}
                className="player-video" 
                src={mediaUrl}
                controls 
              />
            </div>
            <div style={{ display: 'flex', gap: '1rem' }}>
              <a 
                href={mediaUrl} 
                className="btn btn-primary" 
                style={{ flex: 1 }}
                download 
                target="_blank"
                rel="noopener noreferrer"
              >
                <Download size={14} />
                <span style={{ marginLeft: '0.4rem' }}>Download MP4</span>
              </a>
              <Button 
                onClick={() => {
                  setIsFavorite(prev => ({ ...prev, [clip.id]: !prev[clip.id] }));
                  showNotification(isFavorite[clip.id] ? 'Removed from favorites' : 'Added to favorites');
                }}
              >
                <span>{isFavorite[clip.id] ? 'Unfavorite' : 'Favorite'}</span>
              </Button>
            </div>
          </div>

          {/* Right tab panel */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div className="panel-tabs">
              <button className={`tab-btn ${clipTab === 'TRANSCRIPT' ? 'active' : ''}`} onClick={() => setClipTab('TRANSCRIPT')}>Transcript</button>
              <button className={`tab-btn ${clipTab === 'AI_EXPLANATIONS' ? 'active' : ''}`} onClick={() => setClipTab('AI_EXPLANATIONS')}>AI Explanations</button>
              <button className={`tab-btn ${clipTab === 'SOCIAL_COPY' ? 'active' : ''}`} onClick={() => setClipTab('SOCIAL_COPY')}>Social Post Copy</button>
            </div>

            <div className="tab-content" style={{ overflowY: 'auto' }}>
              {clipTab === 'TRANSCRIPT' && (
                <div className="transcript-flow">
                  <span className="word-pill highlight" onClick={() => handleWordClick(0.0)}>Welcome</span>
                  <span className="word-pill" onClick={() => handleWordClick(1.2)}>to</span>
                  <span className="word-pill" onClick={() => handleWordClick(1.8)}>the</span>
                  <span className="word-pill highlight" onClick={() => handleWordClick(2.5)}>future</span>
                  <span className="word-pill" onClick={() => handleWordClick(3.1)}>of</span>
                  <span className="word-pill highlight" onClick={() => handleWordClick(3.7)}>AI</span>
                  <span className="word-pill" onClick={() => handleWordClick(4.2)}>video</span>
                  <span className="word-pill highlight" onClick={() => handleWordClick(4.9)}>rendering.</span>
                  <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', width: '100%', marginTop: '1rem' }}>*Click words to jump player playback seconds.</p>
                </div>
              )}

              {clipTab === 'AI_EXPLANATIONS' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', fontSize: '0.85rem' }}>
                  <p><strong>Selected Hook Index:</strong> High viral potential centered around AI terminology keyword density.</p>
                  <p><strong>Gemini Scoring Reasoning:</strong> Dialogue contains intense pacing, delivering immediate audience retainment within the first 3 seconds of clipping.</p>
                </div>
              )}

              {clipTab === 'SOCIAL_COPY' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', fontSize: '0.85rem' }}>
                  <p><strong>POV Hook Overlay:</strong> "{clip.povText || 'Wait for the twist...'}"</p>
                  <div style={{ background: 'var(--bg-primary)', padding: '0.75rem', borderRadius: '4px', border: '1px solid var(--border-muted)' }}>
                    <p style={{ fontWeight: 600 }}>Suggested Title:</p>
                    <p style={{ color: 'var(--text-secondary)', marginTop: '0.25rem' }}>How AI is changing rendering forever! 🚀 #shorts #ai</p>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
