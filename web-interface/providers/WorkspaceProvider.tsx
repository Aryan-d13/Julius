"use client";

import React, { createContext, useState, useContext, useEffect } from 'react';

interface Org {
  id: string;
  name: string;
}

interface Workspace {
  id: string;
  name: string;
}

interface WorkspaceContextProps {
  activeOrg: Org;
  setActiveOrg: (org: Org) => void;
  activeWorkspace: Workspace;
  setActiveWorkspace: (ws: Workspace) => void;
  orgList: Org[];
  setOrgList: (list: Org[]) => void;
}

const WorkspaceContext = createContext<WorkspaceContextProps | undefined>(undefined);

export const WorkspaceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [activeOrg, setActiveOrgState] = useState<Org>({ id: 'org-1111', name: 'Personal Organization' });
  const [activeWorkspace, setActiveWorkspaceState] = useState<Workspace>({ id: 'ws-2222', name: 'Default Workspace' });
  const [orgList, setOrgList] = useState<Org[]>([
    { id: 'org-1111', name: 'Personal Organization' },
    { id: 'org-3333', name: 'Creative Hub Team' }
  ]);

  useEffect(() => {
    if (typeof window !== "undefined") {
      const savedOrg = localStorage.getItem("julius_active_org");
      const savedWs = localStorage.getItem("julius_active_workspace");
      if (savedOrg) {
        try { setActiveOrgState(JSON.parse(savedOrg)); } catch (e) {}
      }
      if (savedWs) {
        try { setActiveWorkspaceState(JSON.parse(savedWs)); } catch (e) {}
      }
    }
  }, []);

  const setActiveOrg = (org: Org) => {
    setActiveOrgState(org);
    localStorage.setItem("julius_active_org", JSON.stringify(org));
  };

  const setActiveWorkspace = (ws: Workspace) => {
    setActiveWorkspaceState(ws);
    localStorage.setItem("julius_active_workspace", JSON.stringify(ws));
  };

  return (
    <WorkspaceContext.Provider value={{
      activeOrg,
      setActiveOrg,
      activeWorkspace,
      setActiveWorkspace,
      orgList,
      setOrgList
    }}>
      {children}
    </WorkspaceContext.Provider>
  );
};

export const useWorkspace = () => {
  const context = useContext(WorkspaceContext);
  if (!context) {
    throw new Error('useWorkspace must be used within a WorkspaceProvider');
  }
  return context;
};
