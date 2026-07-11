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
  Loader2,
  User,
  Plus,
  Search,
  Settings,
  Home,
  Play,
  Pause,
  X,
  ChevronDown,
  RefreshCw,
  XCircle,
  FileText,
  Share2
} from 'lucide-react';
import { apiClient } from './apiClient';

// Type Interfaces
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

type ViewState = 'LANDING' | 'AUTH' | 'ONBOARDING' | 'DASHBOARD' | 'CREATE_JOB' | 'JOB_DETAIL' | 'CLIP_LIBRARY' | 'SETTINGS';
type SettingsSubView = 'USER' | 'WORKSPACE';
type ClipTab = 'TRANSCRIPT' | 'AI_EXPLANATIONS' | 'SOCIAL_COPY';

export default function JuliusWebClient() {
  // Navigation & View State
  const [currentView, setCurrentView] = useState<ViewState>('LANDING');
  const [settingsSubView, setSettingsSubView] = useState<SettingsSubView>('USER');
  const [showWorkspaceDropdown, setShowWorkspaceDropdown] = useState(false);
  const [showCmdPalette, setShowCmdPalette] = useState(false);
  const [showNotifications, setShowNotifications] = useState<string | null>(null);

  // Authentication State
  const [isLogin, setIsLogin] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [authToken, setAuthToken] = useState<string | null>(null);
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [activeSessions, setActiveSessions] = useState<any[]>([]);

  // Onboarding Step State
  const [onboardingStep, setOnboardingStep] = useState(1);
  const [onboardOrgName, setOnboardOrgName] = useState('');
  const [onboardMemberEmail, setOnboardMemberEmail] = useState('');

  // Workspace & Org Context
  const [activeOrg, setActiveOrg] = useState({ id: 'org-1111', name: 'Personal Organization' });
  const [activeWorkspace, setActiveWorkspace] = useState({ id: 'ws-2222', name: 'Default Workspace' });
  const [orgList, setOrgList] = useState<any[]>([
    { id: 'org-1111', name: 'Personal Organization' },
    { id: 'org-3333', name: 'Creative Hub Team' }
  ]);

  // Job Submission States
  const [videoUrl, setVideoUrl] = useState('');
  const [clipCount, setClipCount] = useState(1);
  const [copyLanguage, setCopyLanguage] = useState('en');
  const [templateRef, setTemplateRef] = useState('default-dev');
  const [minDuration, setMinDuration] = useState(10);
  const [maxDuration, setMaxDuration] = useState(40);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Live Job Processing States
  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const [activeStep, setActiveStep] = useState<string | null>(null);
  const [completedSteps, setCompletedSteps] = useState<string[]>([]);
  const [failedStep, setFailedStep] = useState<string | null>(null);
  const [logs, setLogs] = useState<LogMessage[]>([]);
  const [sseStatus, setSseStatus] = useState<'disconnected' | 'connecting' | 'connected'>('disconnected');

  // Clip Library & Viewer States
  const [clips, setClips] = useState<JobClip[]>([]);
  const [selectedClip, setSelectedClip] = useState<JobClip | null>(null);
  const [clipTab, setClipTab] = useState<ClipTab>('TRANSCRIPT');
  const [isClipGlow, setIsClipGlow] = useState(false);
  const [isFavorite, setIsFavorite] = useState<Record<string, boolean>>({});

  // Command Palette Fuzzy Search Query
  const [cmdQuery, setCmdQuery] = useState('');
  const [cmdSelectedIndex, setCmdSelectedIndex] = useState(0);

  // Global Activity Feed
  const [activities, setActivities] = useState<any[]>([
    { id: 1, event: 'ORGANIZATION_CREATED', desc: 'Personal Organization set up.', time: 'Just now' },
    { id: 2, event: 'WORKSPACE_CREATED', desc: 'Default Workspace configured.', time: '10m ago' }
  ]);

  // Private Staff Notes state
  const [internalNotes, setInternalNotes] = useState<any[]>([]);
  const [newNoteText, setNewNoteText] = useState('');

  // Timelines state
  const [userTimeline, setUserTimeline] = useState<any[]>([]);

  // Telemetry metric values
  const [aiMetrics, setAiMetrics] = useState<any>({ whisperVolume: 102, geminiVolume: 345, whisperCost: 3.42, geminiCost: 11.23, averageLatencyMs: 295 });
  const [queueMetrics, setQueueMetrics] = useState<any>({ queueDepth: 0, activeWorkers: 1, idleWorkers: 4 });

  // Refs for element controls
  const logsEndRef = useRef<HTMLDivElement>(null);
  const videoPlayerRef = useRef<HTMLVideoElement>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

  // Auto scroll logs
  useEffect(() => {
    if (logsEndRef.current) {
      logsEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs]);

  // Keyboard shortcut binding controls
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Toggle palette: Cmd + K or Ctrl + K
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setShowCmdPalette(prev => !prev);
      }
      
      // Close overlay: Esc
      if (e.key === 'Escape') {
        setShowCmdPalette(false);
        setSelectedClip(null);
      }

      // If command palette or viewer modal is open, avoid triggering other shortcuts
      if (showCmdPalette || selectedClip) return;

      // New Job Shortcut: N (when not focusing input fields)
      if (e.key.toLowerCase() === 'n' && document.activeElement?.tagName !== 'INPUT' && document.activeElement?.tagName !== 'TEXTAREA') {
        e.preventDefault();
        setCurrentView('CREATE_JOB');
      }

      // Home Shortcut: H
      if (e.key.toLowerCase() === 'h' && document.activeElement?.tagName !== 'INPUT' && document.activeElement?.tagName !== 'TEXTAREA') {
        e.preventDefault();
        setCurrentView('DASHBOARD');
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [showCmdPalette, selectedClip]);

  // User timeline trigger when settings load
  useEffect(() => {
    if (currentView === 'SETTINGS' && currentUser) {
      apiClient.getUserTimeline(currentUser.id)
        .then(res => setUserTimeline(res))
        .catch(err => console.error("Error fetching timeline:", err));
      
      apiClient.getSessions()
        .then(res => setActiveSessions(res))
        .catch(err => console.error("Error fetching sessions:", err));
    }
  }, [currentView, currentUser]);

  // Fetch admin dashboards
  useEffect(() => {
    if (currentView === 'DASHBOARD') {
      apiClient.getAiMetrics().then(res => setAiMetrics(res)).catch(() => {});
      apiClient.getQueueMetrics().then(res => setQueueMetrics(res)).catch(() => {});
    }
  }, [currentView]);

  // Handle Auth submission
  const handleAuth = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (isLogin) {
        const res = await apiClient.login({ email, password });
        setAuthToken(res.accessToken);
        apiClient.setToken(res.accessToken);
        setCurrentUser({ id: res.userId || 'user-1', email, fullName: 'Julius Customer' });
        setCurrentView('DASHBOARD');
        addActivity('USER_LOGGED_IN', 'Session established successfully.');
      } else {
        const res = await apiClient.register({ email, password, fullName });
        const loginRes = await apiClient.login({ email, password });
        setAuthToken(loginRes.accessToken);
        apiClient.setToken(loginRes.accessToken);
        setCurrentUser({ id: loginRes.userId || 'user-1', email, fullName });
        setCurrentView('ONBOARDING');
        addActivity('USER_REGISTERED', 'New user account created.');
      }
    } catch (err: any) {
      alert(err.message || 'Authentication failed');
    }
  };

  // Onboarding setup finish
  const finishOnboarding = () => {
    if (onboardOrgName) {
      setActiveOrg({ id: 'org-' + Math.random().toString(36).substring(4), name: onboardOrgName });
    }
    setCurrentView('CREATE_JOB');
  };

  // Add event log
  const logSystemMessage = (message: string, typeClass = 'log-info') => {
    const time = new Date().toLocaleTimeString();
    setLogs(prev => [
      ...prev,
      { time, type: 'SYS', msg: message, typeClass }
    ]);
  };

  // Handle Pipeline Events
  const handlePipelineEvent = (event: any, jobId: string) => {
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
      loadJobClips(jobId);
      setIsSubmitting(false);
      if (eventSourceRef.current) eventSourceRef.current.close();
      setSseStatus('disconnected');
      showNotification('Success: Rendering job completed.');
      addActivity('JOB_COMPLETED', 'Generated clips loaded into your library.');
    } else if (type === 'job_failed') {
      setActiveStep(null);
      if (step) setFailedStep(step);
      setIsSubmitting(false);
      if (eventSourceRef.current) eventSourceRef.current.close();
      setSseStatus('disconnected');
      showNotification('Error: Video parsing aborted.');
    }
  };

  // Connect SSE
  const connectSSE = (jobId: string) => {
    if (eventSourceRef.current) eventSourceRef.current.close();
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
        handlePipelineEvent(eventData, jobId);
      } catch (err) {
        console.error('Error parsing event:', err);
      }
    });

    es.onerror = () => {
      setSseStatus('disconnected');
      es.close();
    };
  };

  // Load clips
  const loadJobClips = async (jobId: string) => {
    try {
      const res = await apiClient.getJobClips(jobId);
      setClips(res);
    } catch (err) {
      console.error(err);
    }
  };

  // Submit Job
  const submitJob = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setLogs([]);
    setCompletedSteps([]);
    setFailedStep(null);
    setActiveStep(null);

    logSystemMessage('Broadcasting request payload parameters to API host...');

    const payload = {
      url: videoUrl.trim(),
      count: clipCount,
      min_duration: minDuration,
      max_duration: maxDuration,
      template_ref: templateRef,
      copy_language: copyLanguage
    };

    try {
      const res = await apiClient.createJob(payload, currentUser?.id);
      setActiveJobId(res.jobId);
      logSystemMessage(`Pipeline initialized. Target Job ID: ${res.jobId}`, 'log-success');
      setCurrentView('JOB_DETAIL');
      connectSSE(res.jobId);
    } catch (err: any) {
      logSystemMessage(`Job creation failed: ${err.message}`, 'log-error');
      setIsSubmitting(false);
    }
  };

  // Add notes
  const attachInternalNote = async () => {
    if (!newNoteText.trim() || !currentUser) return;
    try {
      const note = await apiClient.addInternalNote('USER', currentUser.id, newNoteText);
      setInternalNotes(prev => [note, ...prev]);
      setNewNoteText('');
      addActivity('NOTE_ATTACHED', 'Internal operator note attached to profile.');
    } catch (err) {
      console.error(err);
    }
  };

  // Load internal notes
  useEffect(() => {
    if (currentView === 'SETTINGS' && currentUser) {
      apiClient.getInternalNotes('USER', currentUser.id)
        .then(res => setInternalNotes(res))
        .catch(() => {});
    }
  }, [currentView, currentUser]);

  const addActivity = (event: string, desc: string) => {
    setActivities(prev => [
      { id: Date.now(), event, desc, time: 'Just now' },
      ...prev.slice(0, 4)
    ]);
  };

  const showNotification = (msg: string) => {
    setShowNotifications(msg);
    setTimeout(() => setShowNotifications(null), 4000);
  };

  const getStepClass = (stepName: string) => {
    if (failedStep === stepName) return 'failed';
    if (completedSteps.includes(stepName)) return 'completed';
    if (activeStep === stepName) return 'active';
    return '';
  };

  // Jump Video seeking
  const handleWordClick = (seconds: number) => {
    if (videoPlayerRef.current) {
      videoPlayerRef.current.currentTime = seconds;
      videoPlayerRef.current.play().catch(() => {});
      setIsClipGlow(true);
      setTimeout(() => setIsClipGlow(false), 800);
    }
  };

  // Search filter options in command palette
  const getCmdFilteredOptions = () => {
    const options = [
      { label: 'Go to Home Dashboard', action: () => setCurrentView('DASHBOARD'), shortcut: 'G + H' },
      { label: 'Submit New Job', action: () => setCurrentView('CREATE_JOB'), shortcut: 'G + C' },
      { label: 'Browse Settings panel', action: () => setCurrentView('SETTINGS'), shortcut: 'G + S' },
      { label: 'View Clip Library', action: () => setCurrentView('CLIP_LIBRARY'), shortcut: 'G + L' },
      { label: 'Log out of current workspace', action: () => { setAuthToken(null); setCurrentView('LANDING'); }, shortcut: 'Alt + Q' }
    ];

    if (!cmdQuery) return options;
    return options.filter(o => o.label.toLowerCase().includes(cmdQuery.toLowerCase()));
  };

  return (
    <>
      {/* ─── LANDING VIEW ─── */}
      {currentView === 'LANDING' && (
        <div className="landing-layout">
          <div className="landing-hero">
            <span className="landing-badge">Platform V1 Live</span>
            <h1>Video Slicing. Powered by AI.</h1>
            <p>Paste any YouTube link, transcribe dialogue waves with local whisper, analyze virality scores with Gemini, and render clips instantly.</p>
            <div className="landing-actions">
              <button className="btn btn-primary" onClick={() => setCurrentView('AUTH')}>
                Enter Clipper Control
                <ArrowRight size={16} />
              </button>
              <span className="keyboard-hint">Or click to login</span>
            </div>
          </div>
        </div>
      )}

      {/* ─── AUTHENTICATION VIEW ─── */}
      {currentView === 'AUTH' && (
        <div className="auth-layout">
          <div className="auth-card">
            <div className="auth-header">
              <h2>{isLogin ? 'Sign In to Julius' : 'Create Account'}</h2>
              <p>{isLogin ? 'Enter your platform credentials to sync workspaces' : 'Get started by creating a default workspace profile'}</p>
            </div>
            
            <form onSubmit={handleAuth} className="control-form">
              {!isLogin && (
                <div className="form-group">
                  <label>Full Name</label>
                  <input type="text" value={fullName} onChange={e => setFullName(e.target.value)} required placeholder="Staff Operator" />
                </div>
              )}
              <div className="form-group">
                <label>Email Address</label>
                <input type="email" value={email} onChange={e => setEmail(e.target.value)} required placeholder="operator@julius.com" />
              </div>
              <div className="form-group">
                <label>Password</label>
                <input type="password" value={password} onChange={e => setPassword(e.target.value)} required placeholder="••••••••" />
              </div>
              <button type="submit" className="btn btn-primary">
                {isLogin ? 'Log In' : 'Sign Up'}
              </button>
            </form>

            <div className="divider">Or federate access via</div>
            <div className="oauth-grid">
              <button className="oauth-btn" onClick={() => { setAuthToken('google-mock-token'); apiClient.setToken('google-mock-token'); setCurrentUser({ id: 'user-google', email: 'google@julius.com', fullName: 'Google User' }); setCurrentView('DASHBOARD'); }}>
                Google SSO
              </button>
              <button className="oauth-btn" onClick={() => { setAuthToken('github-mock-token'); apiClient.setToken('github-mock-token'); setCurrentUser({ id: 'user-github', email: 'github@julius.com', fullName: 'Github User' }); setCurrentView('DASHBOARD'); }}>
                GitHub SSO
              </button>
            </div>

            <button className="menu-item" style={{ textAlign: 'center', fontSize: '0.8rem' }} onClick={() => setIsLogin(!isLogin)}>
              {isLogin ? "Need a new account? Register here" : "Already registered? Login here"}
            </button>
          </div>
        </div>
      )}

      {/* ─── ONBOARDING FLOW VIEW ─── */}
      {currentView === 'ONBOARDING' && (
        <div className="onboarding-layout">
          <div className="onboarding-card">
            <div className="onboarding-steps">
              <span className={`step-dot ${onboardingStep >= 1 ? 'active' : ''} ${onboardingStep > 1 ? 'completed' : ''}`}>1</span>
              <span className={`step-dot ${onboardingStep >= 2 ? 'active' : ''} ${onboardingStep > 2 ? 'completed' : ''}`}>2</span>
              <span className={`step-dot ${onboardingStep >= 3 ? 'active' : ''}`}>3</span>
            </div>

            {onboardingStep === 1 && (
              <div className="control-form" style={{ gap: '1rem' }}>
                <h2>Create Your Workspace Organization</h2>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Organizations group workspaces, rendering pipelines, and team billing configurations.</p>
                <div className="form-group">
                  <label>Org Name</label>
                  <input type="text" value={onboardOrgName} onChange={e => setOnboardOrgName(e.target.value)} placeholder="Marketing Team Org" required />
                </div>
                <button className="btn btn-primary" onClick={() => setOnboardingStep(2)}>Continue</button>
              </div>
            )}

            {onboardingStep === 2 && (
              <div className="control-form" style={{ gap: '1rem' }}>
                <h2>Invite Teammates (Optional)</h2>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Add your team email addresses to grant access limits to workspaces.</p>
                <div className="form-group">
                  <label>Colleague Email</label>
                  <input type="email" value={onboardMemberEmail} onChange={e => setOnboardMemberEmail(e.target.value)} placeholder="colleague@julius.com" />
                </div>
                <button className="btn btn-primary" onClick={() => setOnboardingStep(3)}>Add & Invite</button>
                <button className="btn" onClick={() => setOnboardingStep(3)}>Skip invitation</button>
              </div>
            )}

            {onboardingStep === 3 && (
              <div className="control-form" style={{ gap: '1rem', textAlign: 'center' }}>
                <CheckCircle2 size={48} color="#10B981" style={{ margin: '0 auto' }} />
                <h2>Organization Setup Complete!</h2>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Your default personal workspaces are active. Let's create your first rendering task.</p>
                <button className="btn btn-primary" onClick={finishOnboarding}>Get Started</button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ─── CENTRAL DASHBOARD WRAPPERS ─── */}
      {authToken && ['DASHBOARD', 'CREATE_JOB', 'JOB_DETAIL', 'CLIP_LIBRARY', 'SETTINGS'].includes(currentView) && (
        <div className="platform-layout">
          {/* Side Panel menu bar */}
          <aside className="platform-sidebar">
            <div className="sidebar-logo">
              <Zap size={20} />
              <span>JULIUS AI</span>
            </div>

            {/* Context switchers */}
            <div className="context-switcher">
              <label>Organization</label>
              <button className="switcher-btn" onClick={() => setShowWorkspaceDropdown(!showWorkspaceDropdown)}>
                <span>{activeOrg.name}</span>
                <ChevronDown size={14} />
              </button>
              {showWorkspaceDropdown && (
                <div className="switcher-dropdown">
                  {orgList.map(o => (
                    <div key={o.id} className="dropdown-item" onClick={() => { setActiveOrg(o); setShowWorkspaceDropdown(false); showNotification(`Switched to organization ${o.name}`); }}>
                      {o.name}
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Left Nav links */}
            <nav className="sidebar-menu">
              <button className={`menu-item ${currentView === 'DASHBOARD' ? 'active' : ''}`} onClick={() => setCurrentView('DASHBOARD')}>
                <Home size={18} />
                <span>Dashboard</span>
              </button>
              <button className={`menu-item ${currentView === 'CREATE_JOB' ? 'active' : ''}`} onClick={() => setCurrentView('CREATE_JOB')}>
                <Plus size={18} />
                <span>Create Clipping Job</span>
              </button>
              <button className={`menu-item ${currentView === 'CLIP_LIBRARY' ? 'active' : ''}`} onClick={() => setCurrentView('CLIP_LIBRARY')}>
                <Sparkles size={18} />
                <span>Clip Library</span>
              </button>
              <button className={`menu-item ${currentView === 'SETTINGS' ? 'active' : ''}`} onClick={() => setCurrentView('SETTINGS')}>
                <Settings size={18} />
                <span>Settings Control</span>
              </button>
            </nav>

            {/* Activity Logger lists */}
            <div className="sidebar-footer">
              <label>Recent Workspace Activities</label>
              <div className="activity-feed">
                {activities.map(a => (
                  <div key={a.id} className="activity-item">
                    <span style={{ fontSize: '0.75rem', fontWeight: 700 }}>{a.event}</span>
                    <span style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>{a.desc}</span>
                  </div>
                ))}
              </div>

              {/* User profile identifier block */}
              <div className="user-profile-block">
                <div className="avatar">OP</div>
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{currentUser?.fullName}</span>
                  <span style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>{currentUser?.email}</span>
                </div>
              </div>
            </div>
          </aside>

          {/* Central Frame Area */}
          <main className="platform-content">
            <header className="content-header">
              <div className="header-logo">
                <span style={{ fontWeight: 700 }}>{activeWorkspace.name}</span>
              </div>
              <div className="header-actions">
                <button className="btn" onClick={() => setShowCmdPalette(true)} style={{ fontSize: '0.8rem', padding: '0.4rem 0.8rem' }}>
                  <Search size={14} />
                  <span>Search commands...</span>
                  <span className="cmd-shortcut">⌘K</span>
                </button>
                <span className="pulse-indicator"></span>
                <span style={{ fontSize: '0.75rem', color: '#10B981', fontWeight: 600 }}>Cluster Connected</span>
              </div>
            </header>

            <div className="content-body">
              {/* ─── HOME DASHBOARD VIEW ─── */}
              {currentView === 'DASHBOARD' && (
                <div>
                  <div className="panel-header">
                    <h2>Workspace Overview</h2>
                  </div>
                  <div className="bento-grid">
                    <div className="bento-card">
                      <label>AI Cost Consumption</label>
                      <div className="bento-value">${(aiMetrics.whisperCost + aiMetrics.geminiCost).toFixed(2)}</div>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Combined Whisper & Gemini cost allocations</p>
                    </div>
                    <div className="bento-card">
                      <label>Active Render Workers</label>
                      <div className="bento-value">{queueMetrics.activeWorkers} / 5</div>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Virtual threads rendering clips</p>
                    </div>
                    <div className="bento-card">
                      <label>Average Pipeline Latency</label>
                      <div className="bento-value">{aiMetrics.averageLatencyMs}ms</div>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>p99 API roundtrip latency</p>
                    </div>
                  </div>

                  <div className="glass-panel" style={{ marginTop: '2rem' }}>
                    <h3>Active Clipping Pipelines</h3>
                    <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '0.5rem', marginBottom: '1.5rem' }}>Below are the jobs running in your default workspace.</p>
                    {activeJobId ? (
                      <div className="activity-item" style={{ cursor: 'pointer', padding: '1rem' }} onClick={() => setCurrentView('JOB_DETAIL')}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <strong>Job ID: {activeJobId}</strong>
                          <span className="landing-badge">Running</span>
                        </div>
                        <p style={{ fontSize: '0.75rem', marginTop: '0.5rem' }}>Click to view live transcribing log window and generated clips.</p>
                      </div>
                    ) : (
                      <div className="empty-state-box">
                        <SlidersHorizontal size={36} color="#4B5563" />
                        <h3>No rendering jobs found</h3>
                        <p>Initialize a video clipping job to stream logs and generate video fragments.</p>
                        <button className="btn btn-primary" onClick={() => setCurrentView('CREATE_JOB')}>Initialize Pipeline</button>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* ─── CREATE JOB VIEW ─── */}
              {currentView === 'CREATE_JOB' && (
                <div className="glass-panel">
                  <div className="panel-header">
                    <h2>Initialize Video Clipping Job</h2>
                  </div>
                  <form onSubmit={submitJob} className="control-form">
                    <div className="form-group">
                      <label>YouTube Link or Video URL</label>
                      <input type="url" value={videoUrl} onChange={e => setVideoUrl(e.target.value)} required placeholder="https://www.youtube.com/watch?v=..." />
                    </div>

                    <div className="form-row">
                      <div className="form-group">
                        <label>Max Clip Fragments</label>
                        <input type="number" min="1" max="10" value={clipCount} onChange={e => setClipCount(parseInt(e.target.value, 10))} />
                      </div>
                      <div className="form-group">
                        <label>Caption Language</label>
                        <select value={copyLanguage} onChange={e => setCopyLanguage(e.target.value)}>
                          <option value="en">English (en)</option>
                          <option value="hi">Hindi (hi)</option>
                        </select>
                      </div>
                    </div>

                    <div className="form-group">
                      <label>Ratio Preset Template</label>
                      <select value={templateRef} onChange={e => setTemplateRef(e.target.value)}>
                        <option value="default-dev">Default Development preset</option>
                        <option value="vertical-split">TikTok Split screen ratio</option>
                        <option value="cinematic-portrait">Cinematic Reels ratio</option>
                      </select>
                    </div>

                    <div className="form-row">
                      <div className="form-group">
                        <label>Min Duration (seconds)</label>
                        <input type="number" min="5" value={minDuration} onChange={e => setMinDuration(parseInt(e.target.value, 10))} />
                      </div>
                      <div className="form-group">
                        <label>Max Duration (seconds)</label>
                        <input type="number" min="10" value={maxDuration} onChange={e => setMaxDuration(parseInt(e.target.value, 10))} />
                      </div>
                    </div>

                    <button type="submit" className="btn btn-primary" style={{ padding: '0.8rem' }} disabled={isSubmitting}>
                      {isSubmitting ? 'Creating task...' : 'Start Clipper Pipeline'}
                    </button>
                  </form>
                </div>
              )}

              {/* ─── JOB DETAIL VIEW ─── */}
              {currentView === 'JOB_DETAIL' && (
                <div>
                  <div className="panel-header">
                    <h2>Pipeline Status — Job Details</h2>
                    <span className="landing-badge">{activeJobId}</span>
                  </div>

                  <div className="split-viewer">
                    {/* Progress Nodes timeline */}
                    <div className="glass-panel" style={{ padding: '1.5rem' }}>
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
                    </div>

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
                    <div className="glass-panel" style={{ marginTop: '2rem' }}>
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
                    </div>
                  )}
                </div>
              )}

              {/* ─── CLIP LIBRARY VIEW ─── */}
              {currentView === 'CLIP_LIBRARY' && (
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
                </div>
              )}

              {/* ─── SETTINGS VIEW ─── */}
              {currentView === 'SETTINGS' && (
                <div className="glass-panel">
                  <div className="panel-tabs">
                    <button className={`tab-btn ${settingsSubView === 'USER' ? 'active' : ''}`} onClick={() => setSettingsSubView('USER')}>User profile & Staff Notes</button>
                    <button className={`tab-btn ${settingsSubView === 'WORKSPACE' ? 'active' : ''}`} onClick={() => setSettingsSubView('WORKSPACE')}>Workspace & Org Members</button>
                  </div>

                  <div style={{ marginTop: '1.5rem' }}>
                    {settingsSubView === 'USER' && (
                      <div className="control-form" style={{ gap: '1.5rem' }}>
                        <div>
                          <h3>User Credentials Info</h3>
                          <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>Active user login details.</p>
                          <div className="form-group" style={{ marginTop: '1rem' }}>
                            <label>Operator Email</label>
                            <input type="text" value={currentUser?.email || ''} readOnly />
                          </div>
                        </div>

                        <div>
                          <h3>Active Sessions List</h3>
                          <div className="activity-feed" style={{ marginTop: '0.75rem' }}>
                            {activeSessions.map(s => (
                              <div key={s.id || s.sessionId} className="activity-item" style={{ display: 'flex', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                                <div>
                                  <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>IP: {s.createdIp}</span>
                                  <p style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>User Agent: {s.createdUserAgent}</p>
                                </div>
                                <button className="btn" style={{ padding: '0.35rem 0.6rem', fontSize: '0.75rem' }} onClick={() => apiClient.revokeSession(s.id || s.sessionId).then(() => setActiveSessions(prev => prev.filter(x => (x.id || x.sessionId) !== (s.id || s.sessionId)))).then(() => showNotification('Session revoked successfully.'))}>
                                  Revoke
                                </button>
                              </div>
                            ))}
                          </div>
                        </div>

                        {/* Staff notes input */}
                        <div style={{ borderTop: '1px solid var(--border-muted)', paddingTop: '1.5rem' }}>
                          <h3>Internal Staff Notes (Operators Only)</h3>
                          <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '1rem' }}>These notes are saved privately and never leaked to clients.</p>
                          <div className="form-group">
                            <textarea value={newNoteText} onChange={e => setNewNoteText(e.target.value)} placeholder="Type notes..." rows={3} />
                          </div>
                          <button className="btn btn-primary" onClick={attachInternalNote}>Add Note</button>

                          <div className="activity-feed" style={{ marginTop: '1.25rem' }}>
                            {internalNotes.map(n => (
                              <div key={n.id} className="activity-item">
                                <p style={{ fontSize: '0.85rem' }}>{n.noteText}</p>
                                <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Added: {n.createdAt}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    )}

                    {settingsSubView === 'WORKSPACE' && (
                      <div className="control-form" style={{ gap: '1.5rem' }}>
                        <div>
                          <h3>Workspace Metadata</h3>
                          <div className="form-group" style={{ marginTop: '1rem' }}>
                            <label>Workspace Name</label>
                            <input type="text" value={activeWorkspace.name} onChange={e => setActiveWorkspace({ ...activeWorkspace, name: e.target.value })} />
                          </div>
                        </div>

                        <div>
                          <h3>Organization Members</h3>
                          <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Manage organizational access limits.</p>
                          <div className="activity-item" style={{ marginTop: '0.75rem', display: 'flex', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                            <div>
                              <strong>{currentUser?.fullName} (You)</strong>
                              <p style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>ROLE_ORG_OWNER</p>
                            </div>
                            <span className="landing-badge">Owner</span>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </main>
        </div>
      )}

      {/* ─── GLOBAL COMMAND PALETTE MODAL ─── */}
      {showCmdPalette && (
        <div className="cmd-palette-overlay" onClick={() => setShowCmdPalette(false)}>
          <div className="cmd-palette-box" onClick={e => e.stopPropagation()}>
            <input 
              type="text" 
              className="cmd-input" 
              placeholder="Search workspaces or commands..." 
              value={cmdQuery} 
              onChange={e => setCmdQuery(e.target.value)} 
              autoFocus 
            />
            <div className="cmd-list">
              <div className="cmd-group-title">Navigation Controls</div>
              {getCmdFilteredOptions().map((o, idx) => (
                <div 
                  key={idx} 
                  className={`cmd-option ${cmdSelectedIndex === idx ? 'selected' : ''}`}
                  onClick={() => { o.action(); setShowCmdPalette(false); }}
                >
                  <span>{o.label}</span>
                  <span className="cmd-shortcut">{o.shortcut}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* ─── SPLIT-SCREEN CLIP VIEWER MODAL ─── */}
      {selectedClip && (
        <div className="cmd-palette-overlay" style={{ paddingTop: '5vh' }} onClick={() => setSelectedClip(null)}>
          <div className="cmd-palette-box" style={{ maxWidth: '850px', maxHeight: '85vh', height: '100%' }} onClick={e => e.stopPropagation()}>
            <div className="panel-header" style={{ padding: '1rem', marginBottom: 0 }}>
              <h3>Clip Detail Viewer — Fragment #{selectedClip.clipIndex}</h3>
              <button className="icon-btn" onClick={() => setSelectedClip(null)}><X size={18} /></button>
            </div>
            
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', padding: '1.5rem', overflowY: 'auto', flex: 1 }}>
              {/* Left Video Player */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div className={`player-frame ${isClipGlow ? 'active-glow' : ''}`}>
                  <video 
                    ref={videoPlayerRef}
                    className="player-video" 
                    src={`http://localhost:8080/data/jobs/${selectedClip.jobId}/clips/${selectedClip.filename}`}
                    controls 
                  />
                </div>
                <div style={{ display: 'flex', gap: '1rem' }}>
                  <a 
                    href={`http://localhost:8080/data/jobs/${selectedClip.jobId}/clips/${selectedClip.filename}`} 
                    className="btn btn-primary" 
                    style={{ flex: 1 }}
                    download 
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <Download size={14} />
                    <span>Download MP4</span>
                  </a>
                  <button 
                    className="btn" 
                    onClick={() => {
                      setIsFavorite(prev => ({ ...prev, [selectedClip.id]: !prev[selectedClip.id] }));
                      showNotification(isFavorite[selectedClip.id] ? 'Removed from favorites' : 'Added to favorites');
                    }}
                  >
                    <span>{isFavorite[selectedClip.id] ? 'Unfavorite' : 'Favorite'}</span>
                  </button>
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
                      <p><strong>POV Hook Overlay:</strong> "{selectedClip.povText || 'Wait for the twist...'}"</p>
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
      )}

      {/* ─── DYNAMIC NOTIFICATION BANNER ─── */}
      {showNotifications && (
        <div className="notification-banner">
          <Sparkles size={16} color="#10B981" />
          <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{showNotifications}</span>
        </div>
      )}
    </>
  );
}
