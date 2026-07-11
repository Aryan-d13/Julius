"use client";

import React, { useState, useEffect, useRef } from 'react';
import { useParams } from 'next/navigation';
import { clipsService } from '../../../../features/clips/services/clipsService';
import { JobClip, LogMessage } from '../../../../types';
import { Card } from '../../../../components/ui/card';
import { ClipViewerModal } from '../../../../components/ClipViewerModal';
import { Sparkles } from 'lucide-react';

export default function JobDetailPage() {
  const { jobId } = useParams() as { jobId: string };

  const [activeStep, setActiveStep] = useState<string | null>(null);
  const [completedSteps, setCompletedSteps] = useState<string[]>([]);
  const [failedStep, setFailedStep] = useState<string | null>(null);
  const [logs, setLogs] = useState<LogMessage[]>([]);
  const [sseStatus, setSseStatus] = useState<'disconnected' | 'connecting' | 'connected'>('disconnected');
  const [clips, setClips] = useState<JobClip[]>([]);
  const [selectedClip, setSelectedClip] = useState<JobClip | null>(null);
  const [showNotificationText, setShowNotificationText] = useState<string | null>(null);

  const logsEndRef = useRef<HTMLDivElement>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (logsEndRef.current) {
      logsEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs]);

  useEffect(() => {
    // Connect SSE
    setSseStatus('connecting');
    const es = new EventSource(`http://localhost:8080/api/jobs/${jobId}/stream`);
    eventSourceRef.current = es;

    es.addEventListener('subscribed', () => {
      setSseStatus('connected');
      logSystemMessage('Connection established with render worker thread pool.', 'log-success');
    });

    es.addEventListener('progress', (e: MessageEvent) => {
      try {
        const eventData = JSON.parse(e.data);
        handlePipelineEvent(eventData);
      } catch (err) {
        console.error('Error parsing event:', err);
      }
    });

    es.onerror = () => {
      setSseStatus('disconnected');
      es.close();
    };

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, [jobId]);

  const logSystemMessage = (message: string, typeClass = 'log-info') => {
    const time = new Date().toLocaleTimeString();
    setLogs(prev => [
      ...prev,
      { time, type: 'SYS', msg: message, typeClass }
    ]);
  };

  const handlePipelineEvent = (event: any) => {
    const time = new Date().toLocaleTimeString();
    const type = event.event_type;
    const step = event.step;
    const msg = event.message || '';
    
    let typeClass = 'log-info';
    if (type === 'job_completed') typeClass = 'log-success';
    if (type === 'job_failed') typeClass = 'log-error';
    if (type === 'step_completed') typeClass = 'log-success';

    setLogs(prev => [
      ...prev,
      { time, type: type.toUpperCase(), msg: `${msg} ${step ? `(step: ${step})` : ''}`, step, typeClass }
    ]);

    if (type === 'step_started') {
      setActiveStep(step);
      setCompletedSteps(prev => prev.filter(s => s !== step));
    } else if (type === 'step_completed') {
      setActiveStep(null);
      setCompletedSteps(prev => [...prev, step]);
    } else if (type === 'job_completed') {
      setActiveStep(null);
      setCompletedSteps(['download', 'transcribe', 'download_video', 'analyze', 'smart_render', 'completed']);
      clipsService.getJobClips(jobId).then(res => setClips(res)).catch(() => {});
      if (eventSourceRef.current) eventSourceRef.current.close();
      setSseStatus('disconnected');
    } else if (type === 'job_failed') {
      setActiveStep(null);
      if (step) setFailedStep(step);
      if (eventSourceRef.current) eventSourceRef.current.close();
      setSseStatus('disconnected');
    }
  };

  const getStepClass = (stepName: string) => {
    if (failedStep === stepName) return 'failed';
    if (completedSteps.includes(stepName)) return 'completed';
    if (activeStep === stepName) return 'active';
    return '';
  };

  const showNotification = (msg: string) => {
    setShowNotificationText(msg);
    setTimeout(() => setShowNotificationText(null), 3000);
  };

  return (
    <div>
      <div className="panel-header">
        <h2>Pipeline Status — Job Details</h2>
        <span className="landing-badge">{jobId}</span>
      </div>

      <div className="split-viewer">
        {/* Progress Nodes timeline */}
        <Card style={{ padding: '1.5rem' }}>
          <h3>Milestones</h3>
          <div className="timeline-stages" style={{ marginTop: '1.5rem' }}>
            <div className={`stage-node ${getStepClass('download')}`}>
              <div className="stage-indicator">1</div>
              <div>
                <strong>Audio Stream Extraction</strong>
                <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Extracting AAC dialogue waves</p>
              </div>
            </div>
            <div className={`stage-node ${getStepClass('transcribe')}`}>
              <div className="stage-indicator">2</div>
              <div>
                <strong>Dialogue Transcription</strong>
                <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Whisper transcription generation</p>
              </div>
            </div>
            <div className={`stage-node ${getStepClass('download_video')}`}>
              <div className="stage-indicator">3</div>
              <div>
                <strong>Source Video Download</strong>
                <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Fetching high definition source MP4</p>
              </div>
            </div>
            <div className={`stage-node ${getStepClass('analyze')}`}>
              <div className="stage-indicator">4</div>
              <div>
                <strong>Gemini Virality Analysis</strong>
                <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Determining engaging hooks and POV captions</p>
              </div>
            </div>
            <div className={`stage-node ${getStepClass('smart_render')}`}>
              <div className="stage-indicator">5</div>
              <div>
                <strong>Vertical Render Cut</strong>
                <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Encoding clips output streams</p>
              </div>
            </div>
          </div>
        </Card>

        {/* Terminal window */}
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

      {/* Rendered clips list results */}
      {clips.length > 0 && (
        <Card style={{ marginTop: '2rem' }}>
          <h3>Generated Clips Outputs</h3>
          <div className="clips-grid" style={{ marginTop: '1.25rem' }}>
            {clips.map(clip => (
              <div key={clip.id} className="clip-box" style={{ cursor: 'pointer' }} onClick={() => setSelectedClip(clip)}>
                <div className="clip-preview-container">
                  <span className="clip-badge">Fragment #{clip.clipIndex}</span>
                  <span className="clip-score-badge">Score: {clip.score || 95}%</span>
                  <video className="clip-video" src={`http://localhost:8080/data/jobs/${clip.jobId}/clips/${clip.filename}`} preload="metadata" muted />
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
