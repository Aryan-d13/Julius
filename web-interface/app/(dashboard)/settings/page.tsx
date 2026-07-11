"use client";

import React, { useState, useEffect } from 'react';
import { authService } from '../../../features/auth/services/authService';
import { adminService } from '../../../features/admin/services/adminService';
import { useWorkspace } from '../../../providers/WorkspaceProvider';
import { Card } from '../../../components/ui/card';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { Sparkles } from 'lucide-react';

export default function SettingsPage() {
  const { activeWorkspace, setActiveWorkspace } = useWorkspace();
  const [settingsSubView, setSettingsSubView] = useState<'USER' | 'WORKSPACE'>('USER');
  
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [activeSessions, setActiveSessions] = useState<any[]>([]);
  const [internalNotes, setInternalNotes] = useState<any[]>([]);
  const [newNoteText, setNewNoteText] = useState('');
  const [showNotificationText, setShowNotificationText] = useState<string | null>(null);

  useEffect(() => {
    if (typeof window !== "undefined") {
      const user = localStorage.getItem("julius_current_user");
      if (user) {
        const u = JSON.parse(user);
        setCurrentUser(u);
        
        // Fetch sessions and notes
        authService.getSessions().then(res => setActiveSessions(res)).catch(() => {});
        adminService.getInternalNotes('USER', u.id).then(res => setInternalNotes(res)).catch(() => {});
      }
    }
  }, []);

  const showNotification = (msg: string) => {
    setShowNotificationText(msg);
    setTimeout(() => setShowNotificationText(null), 3000);
  };

  const handleRevokeSession = async (sessionId: string) => {
    try {
      await authService.revokeSession(sessionId);
      setActiveSessions(prev => prev.filter(s => (s.id || s.sessionId) !== sessionId));
      showNotification('Session revoked successfully.');
    } catch (err) {
      console.error(err);
    }
  };

  const attachInternalNote = async () => {
    if (!newNoteText.trim() || !currentUser) return;
    try {
      const note = await adminService.addInternalNote('USER', currentUser.id, newNoteText);
      setInternalNotes(prev => [note, ...prev]);
      setNewNoteText('');
      showNotification('Internal note added.');
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <Card>
      <div className="panel-tabs">
        <button className={`tab-btn ${settingsSubView === 'USER' ? 'active' : ''}`} onClick={() => setSettingsSubView('USER')}>User profile & Staff Notes</button>
        <button className={`tab-btn ${settingsSubView === 'WORKSPACE' ? 'active' : ''}`} onClick={() => setSettingsSubView('WORKSPACE')}>Workspace & Org Members</button>
      </div>

      <div style={{ marginTop: '1.5rem' }}>
        {settingsSubView === 'USER' && (
          <div className="control-form" style={{ gap: '1.5rem' }}>
            <div>
              <h3>User Credentials Info</h3>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>Active user login details.</p>
              <Input label="Operator Email" type="text" value={currentUser?.email || ''} readOnly />
            </div>

            <div>
              <h3>Active Sessions List</h3>
              <div className="activity-feed" style={{ marginTop: '0.75rem' }}>
                {activeSessions.map((s, idx) => (
                  <div key={s.id || s.sessionId || idx} className="activity-item" style={{ display: 'flex', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>IP: {s.createdIp}</span>
                      <p style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>User Agent: {s.createdUserAgent}</p>
                    </div>
                    <Button style={{ padding: '0.35rem 0.6rem', fontSize: '0.75rem' }} onClick={() => handleRevokeSession(s.id || s.sessionId)}>
                      Revoke
                    </Button>
                  </div>
                ))}
              </div>
            </div>

            {/* Staff notes input */}
            <div style={{ borderTop: '1px solid var(--border-muted)', paddingTop: '1.5rem' }}>
              <h3>Internal Staff Notes (Operators Only)</h3>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '1rem' }}>These notes are saved privately and never leaked to clients.</p>
              <div className="form-group">
                <textarea value={newNoteText} onChange={e => setNewNoteText(e.target.value)} placeholder="Type notes..." rows={3} style={{ background: 'var(--bg-overlay)', border: '1px solid var(--border-muted)', color: 'var(--text-primary)', padding: '0.5rem', borderRadius: '4px' }} />
              </div>
              <Button variant="primary" onClick={attachInternalNote}>Add Note</Button>

              <div className="activity-feed" style={{ marginTop: '1.25rem' }}>
                {internalNotes.map(n => (
                  <div key={n.id} className="activity-item">
                    <p style={{ fontSize: '0.85rem' }}>{n.noteText}</p>
                    <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Added: {n.createdAt}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {settingsSubView === 'WORKSPACE' && (
          <div className="control-form" style={{ gap: '1.5rem' }}>
            <div>
              <h3>Workspace Metadata</h3>
              <Input label="Workspace Name" type="text" value={activeWorkspace.name} onChange={e => setActiveWorkspace({ ...activeWorkspace, name: e.target.value })} />
            </div>

            <div>
              <h3>Organization Members</h3>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Manage organizational access limits.</p>
              <div className="activity-item" style={{ marginTop: '0.75rem', display: 'flex', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <strong>{currentUser?.fullName} (You)</strong>
                  <p style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>ROLE_ORG_OWNER</p>
                </div>
                <span className="landing-badge">Owner</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {showNotificationText && (
        <div className="notification-banner">
          <Sparkles size={16} color="#10B981" />
          <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{showNotificationText}</span>
        </div>
      )}
    </Card>
  );
}
