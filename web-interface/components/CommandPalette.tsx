import React, { useState } from 'react';
import { useRouter } from 'next/navigation';

interface CommandPaletteProps {
  onClose: () => void;
}

export const CommandPalette: React.FC<CommandPaletteProps> = ({ onClose }) => {
  const router = useRouter();
  const [cmdQuery, setCmdQuery] = useState('');

  const getCmdFilteredOptions = () => {
    const options = [
      { label: 'Go to Home Dashboard', action: () => router.push('/dashboard'), shortcut: 'G + H' },
      { label: 'Submit New Job', action: () => router.push('/dashboard/jobs'), shortcut: 'G + C' },
      { label: 'Browse Settings panel', action: () => router.push('/dashboard/settings'), shortcut: 'G + S' },
      { label: 'View Clip Library', action: () => router.push('/dashboard/clips'), shortcut: 'G + L' },
      { label: 'Log out of current workspace', action: () => { localStorage.removeItem("julius_auth_token"); router.push('/'); }, shortcut: 'Alt + Q' }
    ];

    if (!cmdQuery) return options;
    return options.filter(o => o.label.toLowerCase().includes(cmdQuery.toLowerCase()));
  };

  return (
    <div className="cmd-palette-overlay" onClick={onClose}>
      <div className="cmd-palette-box" onClick={e => e.stopPropagation()}>
        <input 
          type="text" 
          className="cmd-input" 
          placeholder="Search workspaces or commands..." 
          value={cmdQuery} 
          onChange={e => setCmdQuery(e.target.value)} 
          autoFocus 
        />
        <div className="cmd-list">
          <div className="cmd-group-title">Navigation Controls</div>
          {getCmdFilteredOptions().map((o, idx) => (
            <div 
              key={idx} 
              className="cmd-option"
              onClick={() => { o.action(); onClose(); }}
            >
              <span>{o.label}</span>
              <span className="cmd-shortcut">{o.shortcut}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
