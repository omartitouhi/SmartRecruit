import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi, type RegisterRequest } from '../api/auth';
import { SESSION_EXPIRED_EVENT, tokenStore } from '../api/client';
import type { Account } from '../types';
import { AuthContext, type AuthContextValue, type AuthStatus } from './context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>(() => (tokenStore.access ? 'loading' : 'anonymous'));
  const [account, setAccount] = useState<Account | null>(null);

  // Restore the session from the stored token on first load.
  useEffect(() => {
    if (!tokenStore.access) return;
    let cancelled = false;
    authApi
      .me()
      .then((me) => {
        if (cancelled) return;
        setAccount(me);
        setStatus('authenticated');
      })
      .catch(() => {
        if (cancelled) return;
        tokenStore.clear();
        setStatus('anonymous');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const onExpired = () => {
      setAccount(null);
      setStatus('anonymous');
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await authApi.login(email.trim(), password);
    tokenStore.set(tokens);
    try {
      const me = await authApi.me();
      setAccount(me);
      setStatus('authenticated');
      return me;
    } catch (error) {
      tokenStore.clear();
      throw error;
    }
  }, []);

  const register = useCallback(
    async (request: RegisterRequest) => {
      await authApi.register({ ...request, email: request.email.trim() });
      return login(request.email, request.password);
    },
    [login],
  );

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.refresh;
    tokenStore.clear();
    setAccount(null);
    setStatus('anonymous');
    if (refreshToken) {
      // Revokes the refresh token server-side; the local session is already gone either way.
      await authApi.logout(refreshToken).catch(() => undefined);
    }
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, account, login, register, logout }),
    [status, account, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
