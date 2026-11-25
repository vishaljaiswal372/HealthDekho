import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import * as api from '../api/healthDekhoApi.js';
import { UNAUTHORIZED_EVENT } from '../api/client.js';
import { clearSession, loadSession, saveSession } from './session.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => loadSession());
  const [expiredNotice, setExpiredNotice] = useState(false);

  const login = useCallback(async (email, password) => {
    const response = await api.login(email.trim(), password);
    setExpiredNotice(false);
    setSession(saveSession(response));
    return response.customer;
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
  }, []);

  const sessionRef = useRef(session);
  useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  // Any 401 from the API (expired or invalid token) ends the session.
  useEffect(() => {
    const onUnauthorized = () => {
      if (sessionRef.current) setExpiredNotice(true);
      clearSession();
      setSession(null);
    };
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, []);

  // Log out automatically when the token's expiry time is reached.
  useEffect(() => {
    if (!session) return undefined;
    const ms = new Date(session.expiresAt).getTime() - Date.now();
    const timer = setTimeout(() => {
      clearSession();
      setSession(null);
      setExpiredNotice(true);
    }, Math.max(ms, 0));
    return () => clearTimeout(timer);
  }, [session]);

  const value = useMemo(
    () => ({
      isAuthenticated: Boolean(session),
      customer: session?.customer ?? null,
      expiredNotice,
      login,
      logout,
    }),
    [session, expiredNotice, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}
