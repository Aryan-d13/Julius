'use client';

import React, { createContext, useState, useContext, useCallback, useMemo } from 'react';
import { ACTIVE_ORG_KEY, ACTIVE_WORKSPACE_KEY } from '../lib/constants';
import type { Organization, Workspace } from '../types';

interface WorkspaceContextProps {
  activeOrg: Organization;
  setActiveOrg: (org: Organization) => void;
  activeWorkspace: Workspace;
  setActiveWorkspace: (ws: Workspace) => void;
  orgList: Organization[];
  setOrgList: (list: Organization[]) => void;
}

const WorkspaceContext = createContext<WorkspaceContextProps | undefined>(undefined);

const DEFAULT_ORG: Organization = { id: 'org-1111', name: 'Personal Organization' };
const DEFAULT_WS: Workspace = { id: 'ws-2222', name: 'Default Workspace' };

function loadFromStorage<T>(key: string, fallback: T): T {
  if (typeof window === 'undefined') return fallback;
  const raw = localStorage.getItem(key);
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

export const WorkspaceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [activeOrg, setActiveOrgState] = useState<Organization>(
    () => loadFromStorage(ACTIVE_ORG_KEY, DEFAULT_ORG),
  );
  const [activeWorkspace, setActiveWorkspaceState] = useState<Workspace>(
    () => loadFromStorage(ACTIVE_WORKSPACE_KEY, DEFAULT_WS),
  );
  const [orgList, setOrgList] = useState<Organization[]>([
    { id: 'org-1111', name: 'Personal Organization' },
    { id: 'org-3333', name: 'Creative Hub Team' },
  ]);

  const setActiveOrg = useCallback((org: Organization) => {
    setActiveOrgState(org);
    localStorage.setItem(ACTIVE_ORG_KEY, JSON.stringify(org));
  }, []);

  const setActiveWorkspace = useCallback((ws: Workspace) => {
    setActiveWorkspaceState(ws);
    localStorage.setItem(ACTIVE_WORKSPACE_KEY, JSON.stringify(ws));
  }, []);

  const value = useMemo(
    () => ({
      activeOrg, setActiveOrg,
      activeWorkspace, setActiveWorkspace,
      orgList, setOrgList,
    }),
    [activeOrg, setActiveOrg, activeWorkspace, setActiveWorkspace, orgList],
  );

  return (
    <WorkspaceContext.Provider value={value}>
      {children}
    </WorkspaceContext.Provider>
  );
};

export const useWorkspace = (): WorkspaceContextProps => {
  const context = useContext(WorkspaceContext);
  if (!context) {
    throw new Error('useWorkspace must be used within a WorkspaceProvider');
  }
  return context;
};
