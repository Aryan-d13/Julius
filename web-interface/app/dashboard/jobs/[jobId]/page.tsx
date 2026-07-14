'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useParams } from 'next/navigation';
import { Sparkles } from 'lucide-react';
import { useEventSource } from '../../../../hooks/useEventSource';
import { useJobClips } from '../../../../features/clips/hooks/useClipsQueries';
import { useNotification } from '../../../../hooks/useNotification';
import { Card } from '../../../../components/ui/card';
import { ClipViewerModal } from '../../../../components/ClipViewerModal';
import { MEDIA_BASE_URL } from '../../../../lib/constants';
import type { JobClip } from '../../../../types';
import { useWorkspace } from '../../../../providers/WorkspaceProvider';

export default function JobDetailPage() {
  const { jobId } = useParams() as { jobId: string };
  const { activeWorkspace } = useWorkspace();
  const { logs, status: sseStatus, activeStep, completedSteps, failedStep } = useEventSource(activeWorkspace.id, jobId);
  const notification = useNotification();
  const logsEndRef = useRef<HTMLDivElement>(null);
  const [selectedClip, setSelectedClip] = useState<JobClip | null>(null);

  const isCompleted = completedSteps.includes('completed');
  const { data: clips = [] } = useJobClips(activeWorkspace.id, isCompleted ? jobId : null);

  useEffect(() => {
    logsEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  const getStepClass = (stepName: string) => {
    if (failedStep === stepName) return 'failed';
    if (completedSteps.includes(stepName)) return 'completed';
    if (activeStep === stepName) return 'active';
    return '';
  };

  return (
    <div>
      <div className="panel-header">
        <h2>Pipeline Status — Job Details</h2>
        <span className="landing-badge">{jobId}</span>
      </div>

      <div className="split-viewer">
        <Card style={{ padding: '1.5rem' }}>
          <h3>Milestones</h3>
          <div className="timeline-stages" style={{ marginTop: '1.5rem' }}>
            {[
              { key: 'download', label: 'Audio Stream Extraction', desc: 'Extracting AAC dialogue waves' },
              { key: 'transcribe', label: 'Dialogue Transcription', desc: 'Whisper transcription generation' },
              { key: 'download_video', label: 'Source Video Download', desc: 'Fetching high definition source MP4' },
              { key: 'analyze', label: 'Gemini Virality Analysis', desc: 'Determining engaging hooks and POV captions' },
              { key: 'smart_render', label: 'Vertical Render Cut', desc: 'Encoding clips output streams' },
            ].map((s, i) => (
              <div key={s.key} className={`stage-node ${getStepClass(s.key)}`}>
                <div className="stage-indicator">{i + 1}</div>
                <div>
                  <strong>{s.label}</strong>
                  <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>{s.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </Card>

        <div className="terminal-window">
          <div className="terminal-header">
            <span>Render Logs console</span>
            <span style={{ color: sseStatus === 'connected' ? '#10B981' : '#EF4444' }}>{sseStatus}</span>
          </div>
          <div className="terminal-body">
            {logs.length === 0 ? (
              <span style={{ color: 'var(--text-muted)' }}>Connecting to SSE event broadcast channels...</span>
            ) : (
              logs.map((l, index) => (
                <div key={index} style={{ display: 'flex', gap: '0.5rem' }}>
                  <span style={{ color: 'var(--text-muted)' }}>[{l.time}]</span>
                  <span className={l.typeClass}>[{l.type}]</span>
                  <span>{l.msg}</span>
                </div>
              ))
            )}
            <div ref={logsEndRef} />
          </div>
        </div>
      </div>

      {clips.length > 0 && (
        <Card style={{ marginTop: '2rem' }}>
          <h3>Generated Clips Outputs</h3>
          <div className="clips-grid" style={{ marginTop: '1.25rem' }}>
            {clips.map((clip) => (
              <div key={clip.id} className="clip-box" style={{ cursor: 'pointer' }} onClick={() => setSelectedClip(clip)}>
                <div className="clip-preview-container">
                  <span className="clip-badge">Fragment #{clip.clipIndex}</span>
                  <span className="clip-score-badge">Score: {clip.score ?? 95}%</span>
                  <video className="clip-video" src={`${MEDIA_BASE_URL}/data/jobs/${clip.jobId}/clips/${clip.filename}`} preload="metadata" muted />
                </div>
                <div className="clip-details">
                  <span style={{ fontSize: '0.85rem', fontWeight: 700 }}>{clip.filename}</span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Duration: {clip.durationSeconds.toFixed(1)}s</span>
                </div>
              </div>
            ))}
          </div>
        </Card>
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
