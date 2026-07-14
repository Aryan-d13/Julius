"use client";

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useLogin, useRegister } from '../../../features/auth/hooks/useAuthQueries';
import { httpClient } from '../../../lib/httpClient';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { CURRENT_USER_KEY } from '../../../lib/constants';

export default function LoginPage() {
  const router = useRouter();
  const [isLogin, setIsLogin] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');

  const loginMutation = useLogin();
  const registerMutation = useRegister();

  const handleAuthSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (isLogin) {
      loginMutation.mutate(
        { email, password },
        {
          onSuccess: (res) => {
            localStorage.setItem(CURRENT_USER_KEY, JSON.stringify({
              id: res.userId || 'user-1',
              email,
              fullName: res.fullName || 'Julius Customer'
            }));
            router.push('/dashboard');
          },
          onError: (err: Error) => {
            alert(err.message || 'Authentication failed');
          }
        }
      );
    } else {
      registerMutation.mutate(
        { email, password, fullName },
        {
          onSuccess: () => {
            loginMutation.mutate(
              { email, password },
              {
                onSuccess: (loginRes) => {
                  localStorage.setItem(CURRENT_USER_KEY, JSON.stringify({
                    id: loginRes.userId || 'user-1',
                    email,
                    fullName
                  }));
                  router.push('/onboarding');
                },
                onError: (err: Error) => {
                  alert(err.message || 'Authentication failed');
                }
              }
            );
          },
          onError: (err: Error) => {
            alert(err.message || 'Registration failed');
          }
        }
      );
    }
  };

  interface OAuth2CallbackResponse {
    access_token: string;
    refresh_token: string;
    session_id: string;
  }

  const handleFederatedLogin = async (provider: string) => {
    try {
      const attributes = provider === 'google' 
        ? {
            sub: 'mock-google-id',
            email: 'google@julius.com',
            name: 'GOOGLE User',
            email_verified: true
          }
        : {
            id: 'mock-github-id',
            email: 'github@julius.com',
            name: 'GITHUB User',
            login: 'github-user'
          };

      const res = await httpClient.request<OAuth2CallbackResponse>(`/api/auth/oauth2/callback?provider=${provider.toUpperCase()}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(attributes)
      });

      if (res.access_token) {
        httpClient.setToken(res.access_token);
        localStorage.setItem(CURRENT_USER_KEY, JSON.stringify({
          id: `user-${provider}`,
          email: `${provider}@julius.com`,
          fullName: `${provider.toUpperCase()} User`
        }));
        router.push('/dashboard');
      } else {
        alert('SSO authentication did not return a token');
      }
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : String(err);
      alert(errMsg || 'SSO authentication failed');
    }
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
