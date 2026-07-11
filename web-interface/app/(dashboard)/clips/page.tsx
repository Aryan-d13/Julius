"use client";

import React, { useState, useEffect } from 'react';
import { clipsService } from '../../../features/clips/services/clipsService';
import { JobClip } from '../../../types';
import { Card } from '../../../components/ui/card';
import { ClipViewerModal } from '../../../components/ClipViewerModal';
import { Sparkles } from 'lucide-react';

export default function ClipsLibraryPage() {
  const [clips, setClips] = useState<JobClip[]>([]);
  const [selectedClip, setSelectedClip] = useState<JobClip | null>(null);
  const [showNotificationText, setShowNotificationText] = useState<string | null>(null);

  useEffect(() => {
    // Attempt to load from active job first
    if (typeof window !== "undefined") {
      const activeJobId = localStorage.getItem("julius_active_job_id");
      if (activeJobId) {
        clipsService.getJobClips(activeJobId).then(res => setClips(res)).catch(() => {});
      }
    }
  }, []);

  const showNotification = (msg: string) => {
    setShowNotificationText(msg);
    setTimeout(() => setShowNotificationText(null), 3000);
  };

  return (
    <div>
      <div className="panel-header">
        <h2>Your Clip Highlight Library</h2>
      </div>

      {clips.length === 0 ? (
        <div className="empty-state-box">
          <Sparkles size={36} color="#4B5563" />
          <h3>No clips generated yet</h3>
          <p>Completed jobs will output fragments automatically here.</p>
        </div>
      ) : (
        <div className="clips-grid">
          {clips.map(clip => (
            <div key={clip.id} className="clip-box" style={{ cursor: 'pointer' }} onClick={() => setSelectedClip(clip)}>
              <div className="clip-preview-container">
                <span className="clip-badge">Fragment #{clip.clipIndex}</span>
                <span className="clip-score-badge">Score: {clip.score || 95}%</span>
                <video className="clip-video" src={`http://localhost:8080/data/jobs/${clip.jobId}/clips/${clip.filename}`} preload="metadata" muted />
              </div>
              <div className="clip-details">
                <span style={{ fontSize: '0.85rem', fontWeight: 700 }}>{clip.filename}</span>
                <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Duration: {clip.durationSeconds.toFixed(1)}s</p>
              </div>
            </div>
          ))}
        </div>
      )}

      {selectedClip && (
        <ClipViewerModal clip={selectedClip} onClose={() => setSelectedClip(null)} showNotification={showNotification} />
      )}

      {showNotificationText && (
        <div className="notification-banner">
          <Sparkles size={16} color="#10B981" />
          <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{showNotificationText}</span>
        </div>
      )}
    </div>
  );
}
