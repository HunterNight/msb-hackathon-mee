import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

import { configureApi } from '../api/client';
import { beginLogin, completeLogin, loadSession, logout, refresh } from './oidc';
import type { Session } from './oidc';

interface AuthValue {
  session: Session | null;
  status: 'loading' | 'anonymous' | 'authenticated' | 'error';
  error: string | null;
  login: () => void;
  signOut: () => void;
  tokenProvider: () => Promise<string | null>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [status, setStatus] = useState<AuthValue['status']>('loading');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (window.location.pathname === '/callback') {
      // Captured and the URL cleaned up *before* the async exchange starts, not after it
      // resolves: React 19's Strict Mode runs this effect twice in dev (mount, cleanup,
      // mount again), and with the code still sitting in the URL the second run called
      // completeLogin a second time with the same authorization code — Keycloak accepts a
      // code once, so whichever of the two exchanges lost the race got back "invalid_grant"
      // and the login was shown as failed even though the other one had just succeeded.
      // Once the URL no longer says /callback, the second run takes the plain
      // anonymous/loadSession branch below instead of repeating the exchange.
      const search = window.location.search;
      window.history.replaceState({}, '', '/');
      completeLogin(search)
        .then((next) => {
          setSession(next);
          setStatus('authenticated');
        })
        .catch((e: Error) => {
          setError(e.message);
          setStatus('error');
        });
      return;
    }
    const existing = loadSession();
    setSession(existing);
    setStatus(existing ? 'authenticated' : 'anonymous');
  }, []);

  // The access token is short lived; refresh a minute before it expires so a chat mid-turn never
  // fails on an expired token.
  const tokenProvider = useCallback(async () => {
    if (!session) return null;
    if (session.expiresAt - Date.now() > 60_000) return session.accessToken;
    const next = await refresh(session);
    if (!next) {
      setSession(null);
      setStatus('anonymous');
      return null;
    }
    setSession(next);
    return next.accessToken;
  }, [session]);

  // Installed during render, not in an effect: React runs a child's effects before its parent's,
  // so a page that loads data on mount would otherwise fire its first request before the token
  // provider existed and get a 401 until you navigated away and back (same reasoning as admin).
  configureApi(tokenProvider);

  const value = useMemo<AuthValue>(
    () => ({
      session,
      status,
      error,
      login: () => void beginLogin(),
      signOut: () => logout(session),
      tokenProvider,
    }),
    [session, status, error, tokenProvider],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
