"use client";

import React, { useState, useEffect, useRef } from 'react';
import { 
  Zap, 
  SlidersHorizontal, 
  GitBranch, 
  Terminal, 
  Trash2, 
  Sparkles, 
  Clock, 
  Download, 
  ArrowRight, 
  Mic, 
  Video, 
  Brain, 
  Scissors, 
  CheckCircle2,
  AlertTriangle,
  Loader2
} from 'lucide-react';

interface LogMessage {
  time: string;
  type: string;
  msg: string;
  step?: string;
  typeClass: string;
}

interface JobClip {
  id: string;
  jobId: string;
  clipIndex: number;
  filename: string;
  storageKey: string;
  url: string;
  durationSeconds: number;
  sizeBytes: number;
  score?: number;
  reasoning?: string;
  povText?: string;
}

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

export default function ClipperDashboard() {
  // Form Configuration States
  const [videoUrl, setVideoUrl] = useState('');
  const [clipCount, setClipCount] = useState(1);
  const [copyLanguage, setCopyLanguage] = useState('en');
  const [templateRef, setTemplateRef] = useState('default-dev');
  const [minDuration, setMinDuration] = useState(10);
  const [maxDuration, setMaxDuration] = useState(40);

  // Process States
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [jobId, setJobId] = useState<string | null>(null);
  const [sseStatus, setSseStatus] = useState<'disconnected' | 'connecting' | 'connected'>('disconnected');
  
  // Pipeline Step Tracking
  const [activeStep, setActiveStep] = useState<string | null>(null);
  const [completedSteps, setCompletedSteps] = useState<string[]>([]);
  const [failedStep, setFailedStep] = useState<string | null>(null);

  // Output Displays
  const [logs, setLogs] = useState<LogMessage[]>([
    {
      time: '--:--:--',
      type: 'SYSTEM',
      msg: 'Awaiting job initialization... Submit a URL to connect dynamic SSE stream listener.',
      typeClass: 'placeholder-row'
    }
  ]);
  const [clips, setClips] = useState<JobClip[]>([]);
  const [showClips, setShowClips] = useState(false);

  const logConsoleRef = useRef<HTMLDivElement>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

  // Auto-scroll logs terminal
  useEffect(() => {
    if (logConsoleRef.current) {
      logConsoleRef.current.scrollTop = logConsoleRef.current.scrollHeight;
    }
  }, [logs]);

  // Cleanup EventSource on unmount
  useEffect(() => {
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, []);

  // Form Submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    setIsSubmitting(true);
    setJobId(null);
    setLogs([]);
    setCompletedSteps([]);
    setActiveStep(null);
    setFailedStep(null);
    setClips([]);
    setShowClips(false);

    logSystemMessage('Submitting pipeline request to Julius backend API...');

    const payload = {
      url: videoUrl.trim(),
      count: clipCount,
      min_duration: minDuration,
      max_duration: maxDuration,
      template_ref: templateRef,
      copy_language: copyLanguage,
      language_mode: 'mixed'
    };

    try {
      const response = await fetch(`${BACKEND_URL}/api/jobs`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-User-Id': '6b10e02b-ec31-48e5-bf0f-85fd02ad4fb9' // Default user matching cached transcripts
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error(`API submission failed with status: ${response.status}`);
      }

      const data = await response.json();
      setJobId(data.jobId);
      logSystemMessage(`Pipeline initialized successfully. Job ID: ${data.jobId}`, 'log-success');

      // Establish EventSource Connection
      connectSSE(data.jobId);

    } catch (error: any) {
      logSystemMessage(`Submission Error: ${error.message}`, 'log-error');
      setIsSubmitting(false);
    }
  };

  const logSystemMessage = (message: string, typeClass = 'log-info') => {
    const time = new Date().toLocaleTimeString();
    setLogs(prev => [
      ...prev,
      { time, type: 'SYS', msg: message, typeClass }
    ]);
  };

  // Connect to Server-Sent Events (SSE) Stream
  const connectSSE = (targetJobId: string) => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setSseStatus('connecting');
    logSystemMessage(`Opening event stream listener at: ${BACKEND_URL}/api/jobs/${targetJobId}/stream`);

    const es = new EventSource(`${BACKEND_URL}/api/jobs/${targetJobId}/stream`);
    eventSourceRef.current = es;

    es.addEventListener('subscribed', () => {
      setSseStatus('connected');
      logSystemMessage('Real-time pipeline event listener established.', 'log-success');
    });

    es.addEventListener('progress', (e: MessageEvent) => {
      try {
        const eventData = JSON.parse(e.data);
        handlePipelineEvent(eventData, targetJobId);
      } catch (err) {
        console.error('Error parsing SSE packet:', err);
      }
    });

    es.onerror = (err) => {
      console.error('SSE Connection error:', err);
      setSseStatus('disconnected');
      logSystemMessage('Event stream listener disconnected. Reconnect attempted automatically.', 'log-warn');
      es.close();
    };
  };

  // Process incoming event data
  const handlePipelineEvent = (event: any, activeJobId: string) => {
    const time = new Date().toLocaleTimeString();
    const type = event.event_type;
    const step = event.step;
    const msg = event.message || '';
    
    let typeClass = 'log-info';
    if (type === 'job_completed') typeClass = 'log-success';
    if (type === 'job_failed') typeClass = 'log-error';
    if (type === 'step_completed') typeClass = 'log-success';

    // Append to logs terminal display
    setLogs(prev => [
      ...prev,
      {
        time,
        type: type.toUpperCase(),
        msg: `${msg} ${step ? `(step: ${step})` : ''}`,
        step,
        typeClass
      }
    ]);

    // Handle pipeline node flow markers
    if (type === 'step_started') {
      setActiveStep(step);
      setCompletedSteps(prev => prev.filter(s => s !== step));
    } 
    else if (type === 'step_completed') {
      setActiveStep(null);
      setCompletedSteps(prev => [...prev, step]);
    } 
    else if (type === 'progress_update') {
      setActiveStep(step);
    }
    else if (type === 'job_completed') {
      setActiveStep(null);
      // Mark all steps as complete
      setCompletedSteps(['download', 'transcribe', 'download_video', 'analyze', 'smart_render', 'completed']);
      logSystemMessage('Job finalized successfully! Loading clips...', 'log-success');
      loadJobClips(activeJobId);
      setIsSubmitting(false);
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        setSseStatus('disconnected');
      }
    } 
    else if (type === 'job_failed') {
      setActiveStep(null);
      if (step) {
        setFailedStep(step);
      }
      logSystemMessage(`Pipeline aborted. Failure info: ${msg}`, 'log-error');
      setIsSubmitting(false);
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        setSseStatus('disconnected');
      }
    }
  };

  // Fetch clip details
  const loadJobClips = async (targetJobId: string) => {
    try {
      const response = await fetch(`${BACKEND_URL}/api/jobs/${targetJobId}/clips`);
      if (!response.ok) {
        throw new Error('Failed to fetch generated clip assets.');
      }

      const clipData = await response.json();
      setClips(clipData);
      setShowClips(true);
    } catch (error: any) {
      logSystemMessage(`Error loading clips: ${error.message}`, 'log-error');
    }
  };

  const getStepClass = (stepName: string) => {
    if (failedStep === stepName) return 'failed';
    if (completedSteps.includes(stepName)) return 'completed';
    if (activeStep === stepName) return 'active';
    return '';
  };

  return (
    <>
      {/* Ambient backgrounds */}
      <div className="glow-blob blob-1"></div>
      <div className="glow-blob blob-2"></div>
      <div className="glow-blob blob-3"></div>

      <header className="app-header">
        <div className="header-logo">
          <Zap className="logo-icon" />
          <span className="logo-text">JULIUS <span className="accent-text">AI CLIPPER</span></span>
        </div>
        <div className="system-status">
          <span className="pulse-indicator"></span>
          <span className="status-label">Cluster Live</span>
        </div>
      </header>

      <main className="app-container">
        {/* Left Column: Form & Progress */}
        <section className="sidebar-panel">
          
          {/* Form Card */}
          <div className="glass-card submit-card">
            <div className="card-header">
              <SlidersHorizontal className="header-icon" />
              <h2>Configure Clipping Job</h2>
            </div>
            
            <form onSubmit={handleSubmit} className="control-form">
              <div className="form-group">
                <label htmlFor="videoUrl">YouTube URL</label>
                <div className="input-with-icon">
                  <Video className="input-icon" />
                  <input 
                    type="url" 
                    id="videoUrl" 
                    value={videoUrl}
                    onChange={(e) => setVideoUrl(e.target.value)}
                    placeholder="https://www.youtube.com/watch?v=..." 
                    required 
                    disabled={isSubmitting}
                  />
                </div>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label htmlFor="clipCount">Max Clips</label>
                  <input 
                    type="number" 
                    id="clipCount" 
                    min="1" 
                    max="10" 
                    value={clipCount}
                    onChange={(e) => setClipCount(parseInt(e.target.value, 10))}
                    disabled={isSubmitting}
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="copyLanguage">POV Language</label>
                  <select 
                    id="copyLanguage"
                    value={copyLanguage}
                    onChange={(e) => setCopyLanguage(e.target.value)}
                    disabled={isSubmitting}
                  >
                    <option value="en">English (en)</option>
                    <option value="hi">Hindi (hi)</option>
                  </select>
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="templateRef">Template Reference</label>
                <select 
                  id="templateRef"
                  value={templateRef}
                  onChange={(e) => setTemplateRef(e.target.value)}
                  disabled={isSubmitting}
                >
                  <option value="default-dev">Default Development (Standard)</option>
                  <option value="vertical-split">Vertical Split (TikTok/Shorts)</option>
                  <option value="cinematic-portrait">Cinematic Portrait (Reels)</option>
                </select>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label htmlFor="minDuration">Min Secs</label>
                  <input 
                    type="number" 
                    id="minDuration" 
                    min="5" 
                    max="60" 
                    value={minDuration}
                    onChange={(e) => setMinDuration(parseInt(e.target.value, 10))}
                    disabled={isSubmitting}
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="maxDuration">Max Secs</label>
                  <input 
                    type="number" 
                    id="maxDuration" 
                    min="10" 
                    max="180" 
                    value={maxDuration}
                    onChange={(e) => setMaxDuration(parseInt(e.target.value, 10))}
                    disabled={isSubmitting}
                  />
                </div>
              </div>

              <button 
                type="submit" 
                className={`primary-btn ${isSubmitting ? 'loading' : ''}`}
                disabled={isSubmitting}
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="btn-icon animate-spin" />
                    <span className="btn-text">Processing...</span>
                  </>
                ) : (
                  <>
                    <span className="btn-text">Initialize Pipeline</span>
                    <ArrowRight className="btn-icon" />
                  </>
                )}
              </button>
            </form>
          </div>

          {/* Progress Node Tracker */}
          <div className="glass-card tracker-card">
            <div className="card-header">
              <GitBranch className="header-icon" />
              <h2>Pipeline Status</h2>
            </div>
            <div className="pipeline-tracker">
              
              <div className={`tracker-node ${getStepClass('download')}`}>
                <div className="node-circle"><Download /></div>
                <div className="node-content">
                  <h3>Download Audio</h3>
                  <p>Extracting YouTube track</p>
                </div>
              </div>

              <div className={`tracker-node ${getStepClass('transcribe')}`}>
                <div className="node-circle"><Mic /></div>
                <div className="node-content">
                  <h3>Transcribe</h3>
                  <p>Local Whisper large-v3-turbo</p>
                </div>
              </div>

              <div className={`tracker-node ${getStepClass('download_video')}`}>
                <div className="node-circle"><Video /></div>
                <div className="node-content">
                  <h3>Download Video</h3>
                  <p>Fetching source MP4 file</p>
                </div>
              </div>

              <div className={`tracker-node ${getStepClass('analyze')}`}>
                <div className="node-circle"><Brain /></div>
                <div className="node-content">
                  <h3>Gemini Analysis</h3>
                  <p>Viral scoring via gemini-3.5-flash</p>
                </div>
              </div>

              <div className={`tracker-node ${getStepClass('smart_render')}`}>
                <div className="node-circle"><Scissors /></div>
                <div className="node-content">
                  <h3>Smart Render</h3>
                  <p>Cutting H.264 video clips</p>
                </div>
              </div>

              <div className={`tracker-node ${getStepClass('completed')}`}>
                <div className="node-circle"><CheckCircle2 /></div>
                <div className="node-content">
                  <h3>Job Completed</h3>
                  <p>Fragments ready for download</p>
                </div>
              </div>

            </div>
          </div>
        </section>

        {/* Right Column: Console & Output Grid */}
        <section className="main-display">
          
          {/* Logs Console */}
          <div className="glass-card console-card">
            <div className="console-header">
              <div className="console-title">
                <Terminal className="header-icon" />
                <h2>Real-time Pipeline Logs</h2>
              </div>
              <div className="console-actions">
                <span className={`active-badge ${
                  sseStatus === 'connected' ? 'connected' : 
                  sseStatus === 'connecting' ? 'connecting' : ''
                }`}>
                  {sseStatus}
                </span>
                <button 
                  onClick={() => setLogs([])}
                  className="icon-btn" 
                  title="Clear Logs"
                >
                  <Trash2 />
                </button>
              </div>
            </div>
            <div className="console-body" ref={logConsoleRef}>
              {logs.length === 0 ? (
                <div className="terminal-row placeholder-row">
                  <span className="term-time">[--:--:--]</span>
                  <span className="term-sys">[SYSTEM]</span>
                  <span className="term-msg">Terminal console cleared.</span>
                </div>
              ) : (
                logs.map((log, index) => (
                  <div key={index} className="terminal-row">
                    <span className="term-time">[{log.time}]</span>
                    <span className={`term-sys ${log.typeClass}`}>[{log.type}]</span>
                    <span className="term-msg">{log.msg}</span>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Clips Card Grid */}
          <div className={`glass-card clips-card ${showClips ? '' : 'hidden'}`}>
            <div className="card-header">
              <Sparkles className="header-icon" />
              <h2>Generated Video Fragments</h2>
            </div>
            <div className="clips-grid">
              {clips.map((clip, index) => {
                const mediaUrl = `${BACKEND_URL}/data/jobs/${clip.jobId}/clips/${clip.filename}`;
                const score = clip.score || 95;
                const reasoning = clip.reasoning || 'Highly engaging visual hook matching core keyword themes.';
                const pov = clip.povText || 'Wait for the twist...';
                
                return (
                  <div key={clip.id || index} className="clip-box">
                    <div className="clip-preview-container">
                      <span className="clip-badge">Fragment #{clip.clipIndex}</span>
                      <span className="clip-score-badge">Viral Score: {score}%</span>
                      <video 
                        className="clip-video" 
                        src={mediaUrl} 
                        controls 
                        preload="metadata"
                      ></video>
                    </div>
                    <div className="clip-details">
                      <h3 className="clip-title">{clip.filename}</h3>
                      <div className="clip-time">
                        <Clock className="w-3.5 h-3.5" />
                        <span>Duration: {clip.durationSeconds ? clip.durationSeconds.toFixed(1) : 'N/A'}s</span>
                      </div>
                      <div className="clip-pov">
                        <strong>POV Caption:</strong> &quot;{pov}&quot;
                      </div>
                      <p className="clip-reasoning">{reasoning}</p>
                      <div className="clip-actions">
                        <a 
                          href={mediaUrl} 
                          download={clip.filename} 
                          className="download-btn"
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          <Download />
                          <span>Save to Device</span>
                        </a>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

        </section>
      </main>

      <footer className="app-footer">
          <p>Julius AI Clipper Dashboard &copy; 2026. Built with Spring Boot 3.3, Next.js, Redis, faster-whisper, and Google Gemini.</p>
      </footer>
    </>
  );
}
