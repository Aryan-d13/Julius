'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { SlidersHorizontal } from 'lucide-react';
import { useAiMetrics, useQueueMetrics } from '../../features/admin/hooks/useAdminQueries';
import { Card } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import { ACTIVE_JOB_KEY } from '../../lib/constants';

export default function DashboardHome() {
  const router = useRouter();
  const { data: aiMetrics } = useAiMetrics();
  const { data: queueMetrics } = useQueueMetrics();

  const activeJobId = typeof window !== 'undefined'
    ? localStorage.getItem(ACTIVE_JOB_KEY)
    : null;

  const totalCost = (aiMetrics?.whisperCost ?? 0) + (aiMetrics?.geminiCost ?? 0);

  return (
    <div>
      <div className="panel-header">
        <h2>Workspace Overview</h2>
      </div>

      <div className="bento-grid">
        <Card>
          <label>AI Cost Consumption</label>
          <div className="bento-value">${totalCost.toFixed(2)}</div>
          <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Combined Whisper &amp; Gemini cost allocations</p>
        </Card>
        <Card>
          <label>Active Render Workers</label>
          <div className="bento-value">{queueMetrics?.activeWorkers ?? 0} / 5</div>
          <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Virtual threads rendering clips</p>
        </Card>
        <Card>
          <label>Average Pipeline Latency</label>
          <div className="bento-value">{aiMetrics?.averageLatencyMs ?? 0}ms</div>
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
