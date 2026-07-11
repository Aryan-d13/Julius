import { httpClient } from '../../../lib/httpClient';
import type { SubtitleStyle } from '../../../types';

export interface EditorSession {
  id: string;
  clipId: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface ClipVersionDTO {
  id: string;
  versionNumber: number;
  name: string;
  timelineState: string;
  stylePreset: SubtitleStyle;
  createdAt: string;
}

export interface RenderArtifactDTO {
  artifactId: string;
  status: string;
  renderHash: string;
  url: string;
}

export const editorService = {
  createSession: async (clipId: string, name: string): Promise<EditorSession> => {
    return httpClient.request<EditorSession>(
      `/api/editor/sessions?clipId=${encodeURIComponent(clipId)}&name=${encodeURIComponent(name)}`,
      { method: 'POST' }
    );
  },

  getLatestVersion: async (sessionId: string): Promise<ClipVersionDTO> => {
    return httpClient.request<ClipVersionDTO>(`/api/editor/sessions/${sessionId}/latest`, {
      method: 'GET',
    });
  },

  autosave: async (sessionId: string, timelineState: string, stylePresetId: string): Promise<{ versionNumber: number; status: string }> => {
    return httpClient.request<{ versionNumber: number; status: string }>(
      `/api/editor/sessions/${sessionId}/autosave`,
      {
        method: 'POST',
        body: JSON.stringify({ timelineState, stylePresetId }),
      }
    );
  },

  saveCheckpoint: async (sessionId: string, name: string, timelineState: string, stylePresetId: string): Promise<{ versionNumber: number; status: string }> => {
    return httpClient.request<{ versionNumber: number; status: string }>(
      `/api/editor/sessions/${sessionId}/checkpoint`,
      {
        method: 'POST',
        body: JSON.stringify({ name, timelineState, stylePresetId }),
      }
    );
  },

  dispatchRender: async (sessionId: string, profileId: string): Promise<RenderArtifactDTO> => {
    return httpClient.request<RenderArtifactDTO>(
      `/api/editor/sessions/${sessionId}/render?profileId=${encodeURIComponent(profileId)}`,
      { method: 'POST' }
    );
  },

  getRenderStatus: async (artifactId: string): Promise<RenderArtifactDTO> => {
    return httpClient.request<RenderArtifactDTO>(`/api/editor/render/${artifactId}/status`, {
      method: 'GET',
    });
  },
};
