'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useParams } from 'next/navigation';
import { 
  Undo2, Redo2, Save, Play, RotateCcw, Sparkles 
} from 'lucide-react';
import { editorService } from '../../../../features/editor/services/editorService';
import { useEditorHistory } from '../../../../hooks/useEditorHistory';
import { useNotification } from '../../../../hooks/useNotification';
import { useKeyboardShortcuts } from '../../../../hooks/useKeyboardShortcuts';
import { Card } from '../../../../components/ui/card';
import { Button } from '../../../../components/ui/button';
import { Input } from '../../../../components/ui/input';

interface WordItem {
  text: string;
  start: number;
  end: number;
}

interface TimelineState {
  durationSeconds: number;
  tracks: Array<{
    id: string;
    type: string;
    name: string;
    segments: Array<{
      id: string;
      assetId: string;
      sourceStart: number;
      timelineStart: number;
      duration: number;
      words: WordItem[];
    }>;
  }>;
}

export default function EditorPage() {
  const { sessionId } = useParams() as { sessionId: string };
  const notification = useNotification();

  const [loading, setLoading] = useState(true);
  const [sessionName, setSessionName] = useState('My Edit Session');
  
  // Style properties
  const [fontName, setFontName] = useState('Impact');
  const [fontSize, setFontSize] = useState(80);
  const [primaryColor, setPrimaryColor] = useState('#FFFF00');
  const [stylePresetId, setStylePresetId] = useState('');
  
  // Timeline details
  const {
    state: timeline,
    execute: executeTimelineChange,
    undo,
    redo,
    reset: resetHistory,
    canUndo,
    canRedo,
  } = useEditorHistory<TimelineState>({
    durationSeconds: 0,
    tracks: []
  });

  const [activeWordIndex, setActiveWordIndex] = useState<number | null>(null);
  const [checkpointName, setCheckpointName] = useState('');
  const [isRendering, setIsRendering] = useState(false);
  const [exportUrl, setExportUrl] = useState<string | null>(null);

  const videoRef = useRef<HTMLVideoElement>(null);

  // Sync keyboard controls
  useKeyboardShortcuts({
    ' ': (e) => {
      e.preventDefault();
      togglePlayback();
    },
    'z': (e) => {
      if (e.ctrlKey || e.metaKey) {
        e.preventDefault();
        undo();
      }
    },
    'y': (e) => {
      if (e.ctrlKey || e.metaKey) {
        e.preventDefault();
        redo();
      }
    }
  });

  // Load Session and Seed version on Mount
  useEffect(() => {
    editorService.getLatestVersion(sessionId)
      .then((res) => {
        setSessionName(res.name || 'My Edit Session');
        setStylePresetId(res.stylePreset.id);
        setFontName(res.stylePreset.fontName);
        setFontSize(res.stylePreset.fontSize);
        setPrimaryColor(res.stylePreset.primaryColor);

        try {
          const parsedTimeline = JSON.parse(res.timelineState) as TimelineState;
          resetHistory(parsedTimeline);
        } catch {
          // fallback
        }
        setLoading(false);
      })
      .catch(() => {
        setLoading(false);
      });
  }, [sessionId, resetHistory]);

  // Monitor playhead to highlight active word overlay in preview canvas
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const handleTimeUpdate = () => {
      const time = video.currentTime;
      const subSegment = timeline.tracks
        ?.find(t => t.type === 'SUBTITLE')
        ?.segments?.[0];

      if (subSegment?.words) {
        const activeIdx = subSegment.words.findIndex(
          w => time >= w.start && time <= w.end
        );
        setActiveWordIndex(activeIdx !== -1 ? activeIdx : null);
      }
    };

    video.addEventListener('timeupdate', handleTimeUpdate);
    return () => video.removeEventListener('timeupdate', handleTimeUpdate);
  }, [timeline]);

  const togglePlayback = () => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      video.play().catch(() => {});
    } else {
      video.pause();
    }
  };

  const jumpToWord = (start: number) => {
    if (videoRef.current) {
      videoRef.current.currentTime = start;
      videoRef.current.play().catch(() => {});
    }
  };

  // Handles text updates in editable spans
  const handleWordChange = (wordIdx: number, newText: string) => {
    const currentSegment = timeline.tracks?.[0]?.segments?.[0];
    if (!currentSegment) return;

    const updatedWords = [...currentSegment.words];
    updatedWords[wordIdx] = { ...updatedWords[wordIdx], text: newText };

    const updatedTimeline = {
      ...timeline,
      tracks: [
        {
          ...timeline.tracks[0],
          segments: [
            {
              ...currentSegment,
              words: updatedWords
            }
          ]
        }
      ]
    };

    executeTimelineChange(updatedTimeline);
  };

  // Trims segment boundaries
  const handleTrimChange = (start: number, duration: number) => {
    const currentSegment = timeline.tracks?.[0]?.segments?.[0];
    if (!currentSegment) return;

    const updatedTimeline = {
      ...timeline,
      tracks: [
        {
          ...timeline.tracks[0],
          segments: [
            {
              ...currentSegment,
              timelineStart: start,
              duration: duration
            }
          ]
        }
      ]
    };

    executeTimelineChange(updatedTimeline);
  };

  // Background Autosave Trigger
  const triggerAutosave = async () => {
    try {
      await editorService.autosave(sessionId, JSON.stringify(timeline), stylePresetId);
      notification.show('Autosaved successfully.');
    } catch {
      notification.show('Autosave failed.');
    }
  };

  // Save named checkpoint
  const triggerCheckpoint = async () => {
    if (!checkpointName.trim()) return;
    try {
      await editorService.saveCheckpoint(sessionId, checkpointName.trim(), JSON.stringify(timeline), stylePresetId);
      notification.show(`Version checkpoint '${checkpointName}' saved.`);
      setCheckpointName('');
    } catch {
      notification.show('Failed to save checkpoint.');
    }
  };

  // Trigger export rendering task
  const triggerExport = async () => {
    setIsRendering(true);
    setExportUrl(null);
    try {
      const renderRes = await editorService.dispatchRender(sessionId, stylePresetId);
      const artifactId = renderRes.artifactId;
      if (!artifactId) {
        setIsRendering(false);
        return;
      }
      notification.show('Rendering task dispatched to background worker queue.');

      // Simple polling loop for compilation status
      const pollInterval = setInterval(async () => {
        try {
          const statusRes = await editorService.getRenderStatus(artifactId);
          if (statusRes.status === 'COMPLETED') {
            clearInterval(pollInterval);
            setIsRendering(false);
            setExportUrl(statusRes.url ?? null);
            notification.show('Export compilation complete!');
          } else if (statusRes.status === 'FAILED') {
            clearInterval(pollInterval);
            setIsRendering(false);
            alert(`Rendering failed: ${statusRes.errorMessage}`);
          }
        } catch {
          clearInterval(pollInterval);
          setIsRendering(false);
        }
      }, 3000);
    } catch (err: unknown) {
      setIsRendering(false);
      const msg = err instanceof Error ? err.message : 'Unknown error';
      alert(`Export initialization failed: ${msg}`);
    }
  };

  if (loading) {
    return <div style={{ padding: '2rem', color: 'var(--text-muted)' }}>Loading editor Workspace assets...</div>;
  }

  const activeSegment = timeline.tracks?.[0]?.segments?.[0];
  const activeWords = activeSegment?.words || [];

  return (
    <div>
      {/* Top action header controls */}
      <div className="panel-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2>Editor — {sessionName}</h2>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Figma-like versioned drafts workspace</p>
        </div>

        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <Button onClick={undo} disabled={!canUndo} style={{ padding: '0.5rem' }}>
            <Undo2 size={16} />
          </Button>
          <Button onClick={redo} disabled={!canRedo} style={{ padding: '0.5rem' }}>
            <Redo2 size={16} />
          </Button>
          <Button onClick={triggerAutosave} variant="primary">
            <Save size={14} style={{ marginRight: '0.4rem' }} />
            Autosave
          </Button>
        </div>
      </div>

      <div className="split-viewer" style={{ marginTop: '1.5rem', gap: '1.5rem' }}>
        {/* Left Column: Player & Subtitles Canvas style */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', flex: 1.2 }}>
          <Card style={{ padding: '1.5rem', position: 'relative' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
              <h3>Instant Preview Canvas</h3>
              <span style={{ fontSize: '0.75rem', color: '#10B981' }}>Parity: 98% (HTML/CSS)</span>
            </div>

            {/* Video overlay wrapper */}
            <div className="player-frame" style={{ position: 'relative', width: '100%', height: '400px', background: '#000', borderRadius: '8px', overflow: 'hidden' }}>
              <video 
                ref={videoRef}
                style={{ width: '100%', height: '100%', objectFit: 'contain' }}
                src="http://localhost:8080/data/jobs/job-uuid-1111/clips/clip1.mp4"
                controls={false}
              />
              
              {/* Dynamic Overlay Subtitles */}
              {activeWordIndex !== null && activeWords[activeWordIndex] && (
                <div style={{
                  position: 'absolute',
                  bottom: '20%',
                  left: '5%',
                  right: '5%',
                  textAlign: 'center',
                  fontFamily: fontName === 'Impact' ? 'Impact, Arial Black' : 'sans-serif',
                  fontSize: `${fontSize}px`,
                  color: primaryColor,
                  textShadow: '2px 2px 0 #000, -2px -2px 0 #000, 2px -2px 0 #000, -2px 2px 0 #000',
                  fontWeight: 'bold',
                  pointerEvents: 'none'
                }}>
                  {activeWords[activeWordIndex].text}
                </div>
              )}

              {/* Safe Title guidelines boundary overlay */}
              <div style={{
                position: 'absolute',
                border: '1px dashed rgba(239, 68, 68, 0.4)',
                top: '10%', bottom: '15%', left: '10%', right: '10%',
                pointerEvents: 'none',
                display: 'flex',
                alignItems: 'flex-end',
                justifyContent: 'center'
              }}>
                <span style={{ fontSize: '0.65rem', color: 'rgba(239, 68, 68, 0.6)', marginBottom: '4px' }}>TikTok Overlay Safe Zone Area</span>
              </div>
            </div>

            {/* Playback Controls */}
            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
              <Button onClick={togglePlayback} style={{ flex: 1 }}>
                <Play size={14} style={{ marginRight: '0.4rem' }} />
                Play / Pause
              </Button>
              <Button onClick={() => jumpToWord(0.0)}>
                <RotateCcw size={14} />
              </Button>
            </div>
          </Card>

          {/* Subtitles Custom Style Configuration Card */}
          <Card style={{ padding: '1.5rem' }}>
            <h3>Caption & Style Presets</h3>
            <div className="control-form" style={{ marginTop: '1rem', gap: '1rem' }}>
              <div className="form-group">
                <label>Font Family</label>
                <select value={fontName} onChange={(e) => setFontName(e.target.value)} style={{ width: '100%', padding: '0.5rem', background: 'var(--bg-overlay)', border: '1px solid var(--border-muted)', color: 'var(--text-primary)', borderRadius: '4px' }}>
                  <option value="Impact">Impact (Viral style)</option>
                  <option value="sans-serif">Poppins / Editorial</option>
                </select>
              </div>

              <div className="form-row">
                <Input 
                  label="Font Size (px)"
                  type="number"
                  value={fontSize.toString()}
                  onChange={(e) => setFontSize(parseInt(e.target.value, 10))}
                />
                <div className="form-group">
                  <label>Highlight Color</label>
                  <input 
                    type="color"
                    value={primaryColor}
                    onChange={(e) => setPrimaryColor(e.target.value)}
                    style={{ width: '100%', height: '38px', background: 'transparent', border: 'none', cursor: 'pointer' }}
                  />
                </div>
              </div>
            </div>
          </Card>
        </div>

        {/* Right Column: Editable word transcript spans grid */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', flex: 1 }}>
          <Card style={{ padding: '1.5rem', height: '400px', display: 'flex', flexDirection: 'column' }}>
            <h3>Word-Level Transcript Editor</h3>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>Click word bounds to jump playhead.</p>
            
            <div style={{ overflowY: 'auto', flex: 1, marginTop: '1rem', display: 'flex', flexWrap: 'wrap', gap: '0.5rem', alignContent: 'flex-start' }}>
              {activeWords.map((word, idx) => (
                <div 
                  key={idx} 
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    background: activeWordIndex === idx ? 'rgba(255, 255, 0, 0.15)' : 'var(--bg-overlay)',
                    border: activeWordIndex === idx ? '1px solid #FFFF00' : '1px solid var(--border-muted)',
                    borderRadius: '4px',
                    padding: '0.25rem 0.5rem'
                  }}
                >
                  <input 
                    type="text"
                    value={word.text}
                    onChange={(e) => handleWordChange(idx, e.target.value)}
                    style={{ background: 'transparent', border: 'none', color: 'var(--text-primary)', fontSize: '0.85rem', width: '70px', textAlign: 'center' }}
                  />
                  <button 
                    onClick={() => jumpToWord(word.start)}
                    style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', fontSize: '0.7rem', cursor: 'pointer', marginLeft: '0.25rem' }}
                  >
                    {word.start.toFixed(1)}s
                  </button>
                </div>
              ))}
            </div>
          </Card>

          {/* Boundaries trimmers timeline */}
          <Card style={{ padding: '1.5rem' }}>
            <h3>Timeline Trimmer Bounds</h3>
            <div className="form-row" style={{ marginTop: '1rem' }}>
              <Input 
                label="Timeline Start (seconds)"
                type="number"
                step="0.1"
                value={activeSegment?.timelineStart.toString() || '0'}
                onChange={(e) => handleTrimChange(parseFloat(e.target.value), activeSegment?.duration || 0)}
              />
              <Input 
                label="Timeline Duration (seconds)"
                type="number"
                step="0.1"
                value={activeSegment?.duration.toString() || '0'}
                onChange={(e) => handleTrimChange(activeSegment?.timelineStart || 0, parseFloat(e.target.value))}
              />
            </div>
          </Card>

          {/* Export rendering & Checkpoints card */}
          <Card style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <h3>Export & Version checkpoints</h3>
            
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <Input 
                placeholder="Checkpoint version name..."
                value={checkpointName}
                onChange={(e) => setCheckpointName(e.target.value)}
                style={{ flex: 1 }}
              />
              <Button onClick={triggerCheckpoint}>Freeze Version</Button>
            </div>

            <Button variant="primary" onClick={triggerExport} disabled={isRendering} style={{ width: '100%', padding: '0.8rem' }}>
              <Sparkles size={14} style={{ marginRight: '0.4rem' }} />
              {isRendering ? 'Rendering video layers...' : 'Compile & Export Final MP4'}
            </Button>

            {exportUrl && (
              <div style={{ background: 'rgba(16, 185, 129, 0.1)', border: '1px solid #10B981', padding: '1rem', borderRadius: '6px', textAlign: 'center' }}>
                <span style={{ fontSize: '0.85rem', color: '#10B981', fontWeight: 600 }}>Compilation complete!</span>
                <p style={{ fontSize: '0.75rem', marginTop: '0.25rem' }}>Click below to download/stream the final clip.</p>
                <a href={exportUrl} target="_blank" rel="noopener noreferrer" className="btn btn-primary" style={{ display: 'inline-block', marginTop: '0.5rem', padding: '0.4rem 1rem', fontSize: '0.8rem' }}>
                  Download Video Artifact
                </a>
              </div>
            )}
          </Card>
        </div>
      </div>

      {notification.text && (
        <div className="notification-banner">
          <Sparkles size={16} color="#10B981" />
          <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{notification.text}</span>
        </div>
      )}
    </div>
  );
}
