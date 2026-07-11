"use client";

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { authService } from '../../../features/auth/services/authService';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';

export default function LoginPage() {
  const router = useRouter();
  const [isLogin, setIsLogin] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');

  const handleAuthSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (isLogin) {
        const res = await authService.login({ email, password });
        localStorage.setItem("julius_current_user", JSON.stringify({
          id: res.userId || 'user-1',
          email,
          fullName: 'Julius Customer'
        }));
        router.push('/dashboard');
      } else {
        await authService.register({ email, password, fullName });
        const loginRes = await authService.login({ email, password });
        localStorage.setItem("julius_current_user", JSON.stringify({
          id: loginRes.userId || 'user-1',
          email,
          fullName
        }));
        router.push('/onboarding');
      }
    } catch (err: any) {
      alert(err.message || 'Authentication failed');
    }
  };

  const handleFederatedLogin = (provider: string) => {
    localStorage.setItem("julius_auth_token", `${provider}-mock-token`);
    localStorage.setItem("julius_current_user", JSON.stringify({
      id: `user-${provider}`,
      email: `${provider}@julius.com`,
      fullName: `${provider.toUpperCase()} User`
    }));
    router.push('/dashboard');
  };

  return (
    <div className="auth-layout">
      <div className="auth-card">
        <div className="auth-header">
          <h2>{isLogin ? 'Sign In to Julius' : 'Create Account'}</h2>
          <p>{isLogin ? 'Enter your credentials to sync workspaces' : 'Get started by creating a default workspace profile'}</p>
        </div>

        <form onSubmit={handleAuthSubmit} className="control-form">
          {!isLogin && (
            <Input 
              label="Full Name" 
              type="text" 
              value={fullName} 
              onChange={e => setFullName(e.target.value)} 
              required 
              placeholder="Staff Operator" 
            />
          )}
          <Input 
            label="Email Address" 
            type="email" 
            value={email} 
            onChange={e => setEmail(e.target.value)} 
            required 
            placeholder="operator@julius.com" 
          />
          <Input 
            label="Password" 
            type="password" 
            value={password} 
            onChange={e => setPassword(e.target.value)} 
            required 
            placeholder="••••••••" 
          />
          <Button type="submit" variant="primary">
            {isLogin ? 'Log In' : 'Sign Up'}
          </Button>
        </form>

        <div className="divider">Or federate access via</div>
        <div className="oauth-grid">
          <button className="oauth-btn" onClick={() => handleFederatedLogin('google')}>
            Google SSO
          </button>
          <button className="oauth-btn" onClick={() => handleFederatedLogin('github')}>
            GitHub SSO
          </button>
        </div>

        <button 
          className="menu-item" 
          style={{ textAlign: 'center', fontSize: '0.8rem', background: 'transparent', border: 'none' }} 
          onClick={() => setIsLogin(!isLogin)}
        >
          {isLogin ? "Need a new account? Register here" : "Already registered? Login here"}
        </button>
      </div>
    </div>
  );
}
