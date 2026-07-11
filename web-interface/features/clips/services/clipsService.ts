import { httpClient } from "../../../lib/httpClient";
import { JobClip } from "../../../types";

export const clipsService = {
  getJobClips: async (jobId: string) => {
    return httpClient.request<JobClip[]>(`/api/jobs/${jobId}/clips`, {
      method: "GET",
    });
  }
};
