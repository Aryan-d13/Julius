'use client';

import React, { useState, useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import {
  Zap, Home, Plus, Sparkles, Settings, ChevronDown, Search,
} from 'lucide-react';
import { useWorkspace } from '../../providers/WorkspaceProvider';
import { useAuthGuard } from '../../hooks/useAuthGuard';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { CommandPalette } from '../../components/CommandPalette';
import { AUTH_TOKEN_KEY, CURRENT_USER_KEY } from '../../lib/constants';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { activeOrg, setActiveOrg, activeWorkspace, orgList } = useWorkspace();
  const { user: currentUser } = useCurrentUser();
  useAuthGuard();

  const [showWorkspaceDropdown, setShowWorkspaceDropdown] = useState(false);
  const [showCmdPalette, setShowCmdPalette] = useState(false);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setShowCmdPalette((prev) => !prev);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const handleLogout = () => {
    localStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem(CURRENT_USER_KEY);
    router.push('/');
  };

  const isActive = (path: string) => pathname === path;

  return (
    <div className="platform-layout">
      <aside className="platform-sidebar">
        <div className="sidebar-logo">
          <Zap size={20} />
          <span>JULIUS AI</span>
        </div>

        <div className="context-switcher">
          <label>Organization</label>
          <button
            className="switcher-btn"
            onClick={() => setShowWorkspaceDropdown(!showWorkspaceDropdown)}
            aria-expanded={showWorkspaceDropdown}
            aria-haspopup="listbox"
          >
            <span>{activeOrg.name}</span>
            <ChevronDown size={14} />
          </button>
          {showWorkspaceDropdown && (
            <div className="switcher-dropdown" role="listbox">
              {orgList.map((o) => (
                <div
                  key={o.id}
                  className="dropdown-item"
                  role="option"
                  aria-selected={o.id === activeOrg.id}
                  onClick={() => { setActiveOrg(o); setShowWorkspaceDropdown(false); }}
                >
                  {o.name}
                </div>
              ))}
            </div>
          )}
        </div>

        <nav className="sidebar-menu" aria-label="Main navigation">
          <button className={`menu-item ${isActive('/dashboard') ? 'active' : ''}`} onClick={() => router.push('/dashboard')}>
            <Home size={18} />
            <span>Dashboard</span>
          </button>
          <button className={`menu-item ${isActive('/dashboard/jobs') ? 'active' : ''}`} onClick={() => router.push('/dashboard/jobs')}>
            <Plus size={18} />
            <span>Create Clipping Job</span>
          </button>
          <button className={`menu-item ${isActive('/dashboard/clips') ? 'active' : ''}`} onClick={() => router.push('/dashboard/clips')}>
            <Sparkles size={18} />
            <span>Clip Library</span>
          </button>
          <button className={`menu-item ${isActive('/dashboard/settings') ? 'active' : ''}`} onClick={() => router.push('/dashboard/settings')}>
            <Settings size={18} />
            <span>Settings Control</span>
          </button>
        </nav>

        <div className="sidebar-footer">
          <label>Active User Profile</label>
          <div className="user-profile-block">
            <div className="avatar">OP</div>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{currentUser?.fullName ?? 'Operator'}</span>
              <span style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>{currentUser?.email ?? 'operator@julius.com'}</span>
            </div>
          </div>
          <button
            className="menu-item"
            style={{ fontSize: '0.8rem', color: '#EF4444', borderTop: '1px solid var(--border-muted)', paddingTop: '0.5rem' }}
            onClick={handleLogout}
          >
            Sign Out
          </button>
        </div>
      </aside>

      <main className="platform-content">
        <header className="content-header">
          <div className="header-logo">
            <span style={{ fontWeight: 700 }}>{activeWorkspace.name}</span>
          </div>
          <div className="header-actions">
            <button className="btn" onClick={() => setShowCmdPalette(true)} style={{ fontSize: '0.8rem', padding: '0.4rem 0.8rem' }}>
              <Search size={14} />
              <span>Search commands...</span>
              <span className="cmd-shortcut">⌘K</span>
            </button>
            <span className="pulse-indicator"></span>
            <span style={{ fontSize: '0.75rem', color: '#10B981', fontWeight: 600 }}>Cluster Connected</span>
          </div>
        </header>

        <div className="content-body">
          {children}
        </div>
      </main>

      {showCmdPalette && (
        <CommandPalette onClose={() => setShowCmdPalette(false)} />
      )}
    </div>
  );
}
