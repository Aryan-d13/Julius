'use client';

import React, { useState } from 'react';
import { Sparkles } from 'lucide-react';
import { useJobClips } from '../../../features/clips/hooks/useClipsQueries';
import { useNotification } from '../../../hooks/useNotification';
import { ClipViewerModal } from '../../../components/ClipViewerModal';
import { ACTIVE_JOB_KEY, MEDIA_BASE_URL } from '../../../lib/constants';
import type { JobClip } from '../../../types';
import { useWorkspace } from '../../../providers/WorkspaceProvider';

export default function ClipsLibraryPage() {
  const { activeWorkspace } = useWorkspace();
  const activeJobId = typeof window !== 'undefined'
    ? localStorage.getItem(ACTIVE_JOB_KEY)
    : null;

  const { data: clips = [] } = useJobClips(activeWorkspace?.id, activeJobId);
  const [selectedClip, setSelectedClip] = useState<JobClip | null>(null);
  const notification = useNotification();

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
          {clips.map((clip) => (
            <div key={clip.id} className="clip-box" style={{ cursor: 'pointer' }} onClick={() => setSelectedClip(clip)}>
              <div className="clip-preview-container">
                <span className="clip-badge">Fragment #{clip.clipIndex}</span>
                <span className="clip-score-badge">Score: {clip.score ?? 95}%</span>
                <video className="clip-video" src={`${MEDIA_BASE_URL}/data/jobs/${clip.jobId}/clips/${clip.filename}`} preload="metadata" muted />
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
        <ClipViewerModal clip={selectedClip} onClose={() => setSelectedClip(null)} showNotification={notification.show} />
      )}

      {notification.text && (
        <div className="notification-banner">
          <Sparkles size={16} color="#10B981" />
          <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{notification.text}</span>
        </div>
      )}
    </div>
  );
}
