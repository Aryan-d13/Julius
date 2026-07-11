"use client";

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useWorkspace } from '../../../providers/WorkspaceProvider';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { CheckCircle2 } from 'lucide-react';

export default function OnboardingPage() {
  const router = useRouter();
  const { setActiveOrg } = useWorkspace();
  const [onboardingStep, setOnboardingStep] = useState(1);
  const [onboardOrgName, setOnboardOrgName] = useState('');
  const [onboardMemberEmail, setOnboardMemberEmail] = useState('');

  const finishOnboarding = () => {
    if (onboardOrgName) {
      setActiveOrg({ id: 'org-' + Math.random().toString(36).substring(4), name: onboardOrgName });
    }
    router.push('/dashboard/jobs');
  };

  return (
    <div className="onboarding-layout">
      <div className="onboarding-card">
        <div className="onboarding-steps">
          <span className={`step-dot ${onboardingStep >= 1 ? 'active' : ''} ${onboardingStep > 1 ? 'completed' : ''}`}>1</span>
          <span className={`step-dot ${onboardingStep >= 2 ? 'active' : ''} ${onboardingStep > 2 ? 'completed' : ''}`}>2</span>
          <span className={`step-dot ${onboardingStep >= 3 ? 'active' : ''}`}>3</span>
        </div>

        {onboardingStep === 1 && (
          <div className="control-form" style={{ gap: '1rem' }}>
            <h2>Create Your Workspace Organization</h2>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Organizations group workspaces, rendering pipelines, and team billing configurations.</p>
            <Input 
              label="Org Name" 
              type="text" 
              value={onboardOrgName} 
              onChange={e => setOnboardOrgName(e.target.value)} 
              placeholder="Marketing Team Org" 
              required 
            />
            <Button variant="primary" onClick={() => setOnboardingStep(2)}>Continue</Button>
          </div>
        )}

        {onboardingStep === 2 && (
          <div className="control-form" style={{ gap: '1rem' }}>
            <h2>Invite Teammates (Optional)</h2>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Add your team email addresses to grant access limits to workspaces.</p>
            <Input 
              label="Colleague Email" 
              type="email" 
              value={onboardMemberEmail} 
              onChange={e => setOnboardMemberEmail(e.target.value)} 
              placeholder="colleague@julius.com" 
            />
            <Button variant="primary" onClick={() => setOnboardingStep(3)}>Add & Invite</Button>
            <Button onClick={() => setOnboardingStep(3)}>Skip invitation</Button>
          </div>
        )}

        {onboardingStep === 3 && (
          <div className="control-form" style={{ gap: '1rem', textAlign: 'center' }}>
            <CheckCircle2 size={48} color="#10B981" style={{ margin: '0 auto' }} />
            <h2>Organization Setup Complete!</h2>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Your default personal workspaces are active. Let's create your first rendering task.</p>
            <Button variant="primary" onClick={finishOnboarding}>Get Started</Button>
          </div>
        )}
      </div>
    </div>
  );
}
