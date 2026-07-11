// ─── Auth Domain ────────────────────────────────────────────

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface AuthResponse {
  accessToken: string;
  userId: string;
  email: string;
  fullName: string;
}

export interface RegisterResponse {
  userId: string;
  email: string;
}

export interface UserSession {
  id: string;
  sessionId: string;
  createdIp: string;
  createdUserAgent: string;
  createdAt: string;
}

export interface CurrentUser {
  id: string;
  email: string;
  fullName: string;
}

// ─── Jobs Domain ────────────────────────────────────────────

export interface CreateJobRequest {
  url: string;
  count: number;
  min_duration: number;
  max_duration: number;
  template_ref: string;
  copy_language: string;
}

export interface CreateJobResponse {
  jobId: string;
  status: string;
}

export interface JobActionResponse {
  jobId: string;
  status: string;
  message?: string;
}

// ─── Clips Domain ───────────────────────────────────────────

export interface JobClip {
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

// ─── SSE / Pipeline Domain ──────────────────────────────────

export interface PipelineEvent {
  event_type: string;
  step?: string;
  message?: string;
  progress?: number;
}

export interface LogMessage {
  time: string;
  type: string;
  msg: string;
  step?: string;
  typeClass: string;
}

export type SseConnectionStatus = 'disconnected' | 'connecting' | 'connected';

// ─── Admin Domain ───────────────────────────────────────────

export interface InternalNote {
  id: string;
  entityType: string;
  entityId: string;
  noteText: string;
  createdAt: string;
  createdBy?: string;
}

export interface CreateNoteRequest {
  entityType: string;
  entityId: string;
  noteText: string;
}

export interface TimelineEvent {
  id: string;
  eventType: string;
  description: string;
  timestamp: string;
  metadata?: Record<string, string>;
}

export interface AiMetrics {
  whisperVolume: number;
  geminiVolume: number;
  whisperCost: number;
  geminiCost: number;
  averageLatencyMs: number;
}

export interface QueueMetrics {
  queueDepth: number;
  activeWorkers: number;
  idleWorkers: number;
}

export interface SearchResult {
  users: Array<{ id: string; email: string; fullName: string }>;
  organizations: Array<{ id: string; name: string }>;
  workspaces: Array<{ id: string; name: string }>;
  jobs: Array<{ id: string; status: string }>;
}

// ─── Workspace / Organization Domain ────────────────────────

export interface Organization {
  id: string;
  name: string;
}

export interface Workspace {
  id: string;
  name: string;
}
