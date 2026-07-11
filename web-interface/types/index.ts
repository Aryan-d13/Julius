export interface LogMessage {
  time: string;
  type: string;
  msg: string;
  step?: string;
  typeClass: string;
}

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

export interface UserSession {
  id: string;
  sessionId: string;
  createdIp: string;
  createdUserAgent: string;
  createdAt: string;
}
