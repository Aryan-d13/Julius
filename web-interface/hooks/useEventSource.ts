'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { SSE_BASE_URL } from '../lib/constants';
import type { LogMessage, PipelineEvent, SseConnectionStatus } from '../types';

interface UseEventSourceReturn {
  logs: LogMessage[];
  status: SseConnectionStatus;
  activeStep: string | null;
  completedSteps: string[];
  failedStep: string | null;
}

export function useEventSource(workspaceId: string, jobId: string): UseEventSourceReturn {
  const [logs, setLogs] = useState<LogMessage[]>([]);
  const [status, setStatus] = useState<SseConnectionStatus>('disconnected');
  const [activeStep, setActiveStep] = useState<string | null>(null);
  const [completedSteps, setCompletedSteps] = useState<string[]>([]);
  const [failedStep, setFailedStep] = useState<string | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

  const appendLog = useCallback(
    (message: string, type: string, typeClass: string, step?: string) => {
      const time = new Date().toLocaleTimeString();
      setLogs((prev) => [...prev, { time, type, msg: message, step, typeClass }]);
    },
    [],
  );

  const handlePipelineEvent = useCallback(
    (event: PipelineEvent) => {
      const type = event.event_type;
      const step = event.step ?? '';
      const msg = event.message ?? '';

      let typeClass = 'log-info';
      if (type === 'job_completed') typeClass = 'log-success';
      if (type === 'job_failed') typeClass = 'log-error';
      if (type === 'step_completed') typeClass = 'log-success';

      appendLog(
        `${msg} ${step ? `(step: ${step})` : ''}`,
        type.toUpperCase(),
        typeClass,
        step,
      );

      if (type === 'step_started') {
        setActiveStep(step);
        setCompletedSteps((prev) => prev.filter((s) => s !== step));
      } else if (type === 'step_completed') {
        setActiveStep(null);
        setCompletedSteps((prev) => [...prev, step]);
      } else if (type === 'job_completed') {
        setActiveStep(null);
        setCompletedSteps([
          'download', 'transcribe', 'download_video', 'analyze', 'smart_render', 'completed',
        ]);
        eventSourceRef.current?.close();
        setStatus('disconnected');
      } else if (type === 'job_failed') {
        setActiveStep(null);
        if (step) setFailedStep(step);
        eventSourceRef.current?.close();
        setStatus('disconnected');
      }
    },
    [appendLog],
  );

  useEffect(() => {
    if (!workspaceId || !jobId) return;

    setTimeout(() => {
      setStatus('connecting');
    }, 0);
    const es = new EventSource(`${SSE_BASE_URL}/api/workspaces/${workspaceId}/jobs/${jobId}/stream`);
    eventSourceRef.current = es;

    es.addEventListener('subscribed', () => {
      setStatus('connected');
      appendLog('Connection established with render worker thread pool.', 'SYS', 'log-success');
    });

    es.addEventListener('progress', (e: MessageEvent) => {
      try {
        const eventData: PipelineEvent = JSON.parse(e.data);
        handlePipelineEvent(eventData);
      } catch (err) {
        console.error('Error parsing SSE event:', err);
      }
    });

    es.onerror = () => {
      setStatus('disconnected');
      es.close();
    };

    return () => {
      es.close();
      eventSourceRef.current = null;
    };
  }, [workspaceId, jobId, appendLog, handlePipelineEvent]);

  return { logs, status, activeStep, completedSteps, failedStep };
}
