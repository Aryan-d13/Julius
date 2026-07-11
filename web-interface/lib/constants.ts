export const BACKEND_URL =
  process.env.NEXT_PUBLIC_BACKEND_URL || 'http://localhost:8080';

export const SSE_BASE_URL =
  process.env.NEXT_PUBLIC_SSE_URL || BACKEND_URL;

export const MEDIA_BASE_URL =
  process.env.NEXT_PUBLIC_MEDIA_URL || BACKEND_URL;

export const AUTH_TOKEN_KEY = 'julius_auth_token';
export const ACCESS_TOKEN_COOKIE_KEY = 'access_token';
export const CURRENT_USER_KEY = 'julius_current_user';
export const ACTIVE_ORG_KEY = 'julius_active_org';
export const ACTIVE_WORKSPACE_KEY = 'julius_active_workspace';
export const ACTIVE_JOB_KEY = 'julius_active_job_id';
