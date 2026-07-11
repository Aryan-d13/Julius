"use client";

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { SlidersHorizontal } from 'lucide-react';
import { adminService } from '../../features/admin/services/adminService';
import { Card } from '../../components/ui/card';
import { Button } from '../../components/ui/button';

export default function DashboardHome() {
  const router = useRouter();
  const [aiMetrics, setAiMetrics] = useState<any>({ whisperVolume: 102, geminiVolume: 345, whisperCost: 3.42, geminiCost: 11.23, averageLatencyMs: 295 });
  const [queueMetrics, setQueueMetrics] = useState<any>({ queueDepth: 0, activeWorkers: 1, idleWorkers: 4 });
  const [activeJobId, setActiveJobId] = useState<string | null>(null);

  useEffect(() => {
    adminService.getAiMetrics().then(res => setAiMetrics(res)).catch(() => {});
    adminService.getQueueMetrics().then(res => setQueueMetrics(res)).catch(() => {});
    
    // Check if there is a running job saved in localStorage
    if (typeof window !== "undefined") {
      const savedJobId = localStorage.getItem("julius_active_job_id");
      if (savedJobId) {
        setActiveJobId(savedJobId);
      }
    }
  }, []);

  return (
    <div>
      <div className="panel-header">
        <h2>Workspace Overview</h2>
      </div>
      
      <div className="bento-grid">
        <Card>
          <label>AI Cost Consumption</label>
          <div className="bento-value">${(aiMetrics.whisperCost + aiMetrics.geminiCost).toFixed(2)}</div>
          <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Combined Whisper & Gemini cost allocations</p>
        </Card>
        <Card>
          <label>Active Render Workers</label>
          <div className="bento-value">{queueMetrics.activeWorkers} / 5</div>
          <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Virtual threads rendering clips</p>
        </Card>
        <Card>
          <label>Average Pipeline Latency</label>
          <div className="bento-value">{aiMetrics.averageLatencyMs}ms</div>
          <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>p99 API roundtrip latency</p>
        </Card>
      </div>

      <Card style={{ marginTop: '2rem' }}>
        <h3>Active Clipping Pipelines</h3>
        <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '0.5rem', marginBottom: '1.5rem' }}>Below are the jobs running in your default workspace.</p>
        
        {activeJobId ? (
          <div className="activity-item" style={{ cursor: 'pointer', padding: '1rem' }} onClick={() => router.push(`/dashboard/jobs/${activeJobId}`)}>
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
            <Button variant="primary" onClick={() => router.push('/dashboard/jobs')}>Initialize Pipeline</Button>
          </div>
        )}
      </Card>
    </div>
  );
}
