import { httpClient } from "../../../lib/httpClient";
import { JobClip } from "../../../types";

export const clipsService = {
  getJobClips: async (workspaceId: string, jobId: string) => {
    return httpClient.request<JobClip[]>(`/api/workspaces/${workspaceId}/jobs/${jobId}/clips`, {
      method: "GET",
    });
  }
};
