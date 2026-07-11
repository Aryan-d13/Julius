"use client";

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { jobsService } from '../../../features/jobs/services/jobsService';
import { Card } from '../../../components/ui/card';
import { Input } from '../../../components/ui/input';
import { Button } from '../../../components/ui/button';

export default function CreateJobPage() {
  const router = useRouter();
  const [videoUrl, setVideoUrl] = useState('');
  const [clipCount, setClipCount] = useState(1);
  const [copyLanguage, setCopyLanguage] = useState('en');
  const [templateRef, setTemplateRef] = useState('default-dev');
  const [minDuration, setMinDuration] = useState(10);
  const [maxDuration, setMaxDuration] = useState(40);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [currentUser, setCurrentUser] = useState<any>(null);

  useEffect(() => {
    if (typeof window !== "undefined") {
      const user = localStorage.getItem("julius_current_user");
      if (user) {
        setCurrentUser(JSON.parse(user));
      }
    }
  }, []);

  const submitJob = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);

    const payload = {
      url: videoUrl.trim(),
      count: clipCount,
      min_duration: minDuration,
      max_duration: maxDuration,
      template_ref: templateRef,
      copy_language: copyLanguage
    };

    try {
      const res = await jobsService.createJob(payload, currentUser?.id);
      localStorage.setItem("julius_active_job_id", res.jobId);
      router.push(`/dashboard/jobs/${res.jobId}`);
    } catch (err: any) {
      alert(`Job creation failed: ${err.message}`);
      setIsSubmitting(false);
    }
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
          onChange={e => setVideoUrl(e.target.value)} 
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
            onChange={e => setClipCount(parseInt(e.target.value, 10))} 
          />
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
          <Input 
            label="Min Duration (seconds)" 
            type="number" 
            min="5" 
            value={minDuration.toString()} 
            onChange={e => setMinDuration(parseInt(e.target.value, 10))} 
          />
          <Input 
            label="Max Duration (seconds)" 
            type="number" 
            min="10" 
            value={maxDuration.toString()} 
            onChange={e => setMaxDuration(parseInt(e.target.value, 10))} 
          />
        </div>

        <Button type="submit" variant="primary" style={{ padding: '0.8rem' }} disabled={isSubmitting}>
          {isSubmitting ? 'Creating task...' : 'Start Clipper Pipeline'}
        </Button>
      </form>
    </Card>
  );
}
