'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useCreateJob } from '../../../features/jobs/hooks/useJobQueries';
import { useCurrentUser } from '../../../hooks/useCurrentUser';
import { Card } from '../../../components/ui/card';
import { Input } from '../../../components/ui/input';
import { Button } from '../../../components/ui/button';
import { ACTIVE_JOB_KEY } from '../../../lib/constants';
import { useWorkspace } from '../../../providers/WorkspaceProvider';

export default function CreateJobPage() {
  const router = useRouter();
  const { user: currentUser } = useCurrentUser();
  const { activeWorkspace } = useWorkspace();
  const createJob = useCreateJob();

  const [videoUrl, setVideoUrl] = useState('');
  const [clipCount, setClipCount] = useState(1);
  const [copyLanguage, setCopyLanguage] = useState('en');
  const [templateRef, setTemplateRef] = useState('default-dev');
  const [minDuration, setMinDuration] = useState(10);
  const [maxDuration, setMaxDuration] = useState(40);

  const submitJob = async (e: React.FormEvent) => {
    e.preventDefault();
    createJob.mutate(
      {
        workspaceId: activeWorkspace.id,
        payload: {
          url: videoUrl.trim(),
          count: clipCount,
          min_duration: minDuration,
          max_duration: maxDuration,
          template_ref: templateRef,
          copy_language: copyLanguage,
        },
        userId: currentUser?.id,
      },
      {
        onSuccess: (res) => {
          localStorage.setItem(ACTIVE_JOB_KEY, res.jobId);
          router.push(`/dashboard/jobs/${res.jobId}`);
        },
        onError: (err) => {
          alert(`Job creation failed: ${err.message}`);
        },
      },
    );
  };

  return (
    <Card>
      <div className="panel-header">
        <h2>Initialize Video Clipping Job</h2>
      </div>
      <form onSubmit={submitJob} className="control-form">
        <Input
          label="YouTube Link or Video URL"
          type="url"
          value={videoUrl}
          onChange={(e) => setVideoUrl(e.target.value)}
          required
          placeholder="https://www.youtube.com/watch?v=..."
        />

        <div className="form-row">
          <Input
            label="Max Clip Fragments"
            type="number"
            min="1"
            max="10"
            value={clipCount.toString()}
            onChange={(e) => setClipCount(parseInt(e.target.value, 10))}
          />
          <div className="form-group">
            <label htmlFor="copy-language">Caption Language</label>
            <select id="copy-language" value={copyLanguage} onChange={(e) => setCopyLanguage(e.target.value)}>
              <option value="en">English (en)</option>
              <option value="hi">Hindi (hi)</option>
            </select>
          </div>
        </div>

        <div className="form-group">
          <label htmlFor="template-ref">Ratio Preset Template</label>
          <select id="template-ref" value={templateRef} onChange={(e) => setTemplateRef(e.target.value)}>
            <option value="default-dev">Default Development preset</option>
            <option value="vertical-split">TikTok Split screen ratio</option>
            <option value="cinematic-portrait">Cinematic Reels ratio</option>
          </select>
        </div>

        <div className="form-row">
          <Input
            label="Min Duration (seconds)"
            type="number"
            min="5"
            value={minDuration.toString()}
            onChange={(e) => setMinDuration(parseInt(e.target.value, 10))}
          />
          <Input
            label="Max Duration (seconds)"
            type="number"
            min="10"
            value={maxDuration.toString()}
            onChange={(e) => setMaxDuration(parseInt(e.target.value, 10))}
          />
        </div>

        <Button type="submit" variant="primary" style={{ padding: '0.8rem' }} disabled={createJob.isPending}>
          {createJob.isPending ? 'Creating task...' : 'Start Clipper Pipeline'}
        </Button>
      </form>
    </Card>
  );
}
