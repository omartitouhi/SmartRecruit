import { act, render, screen, waitFor } from '@testing-library/react';
import { useEffect } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../api/auth';
import { SESSION_EXPIRED_EVENT, tokenStore } from '../api/client';
import { HttpError } from '../lib/errors';
import { makeAccount } from '../test/fixtures';
import { AuthProvider } from './AuthProvider';
import { useAuth, type AuthContextValue } from './context';

vi.mock('../api/auth', () => ({
  authApi: { register: vi.fn(), login: vi.fn(), me: vi.fn(), logout: vi.fn(), forgotPassword: vi.fn(), resetPassword: vi.fn() },
}));

const api = vi.mocked(authApi);
/** The latest context value, captured after each render. */
const latest = { current: null as AuthContextValue | null };
const auth = {
  login: (...args: Parameters<AuthContextValue['login']>) => latest.current!.login(...args),
  register: (...args: Parameters<AuthContextValue['register']>) => latest.current!.register(...args),
  logout: () => latest.current!.logout(),
};

function Probe() {
  const value = useAuth();
  useEffect(() => {
    latest.current = value;
  });
  return (
    <p>
      {value.status}:{value.account?.email ?? 'nobody'}
    </p>
  );
}
const mount = () =>
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
const tokens = { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer' as const, expiresIn: 3600 };

beforeEach(() => {
  vi.resetAllMocks();
  tokenStore.clear();
});

describe('AuthProvider', () => {
  it('is anonymous without a stored token and does not call the API', () => {
    mount();
    expect(screen.getByText('anonymous:nobody')).toBeInTheDocument();
    expect(api.me).not.toHaveBeenCalled();
  });

  it('restores the session from a stored token', async () => {
    tokenStore.set({ accessToken: 'a1', refreshToken: 'r1' });
    api.me.mockResolvedValue(makeAccount('RECRUITER', { email: 'rh@demo.tn' }));
    mount();
    expect(screen.getByText('loading:nobody')).toBeInTheDocument();
    expect(await screen.findByText('authenticated:rh@demo.tn')).toBeInTheDocument();
  });

  it('clears an unusable stored token', async () => {
    tokenStore.set({ accessToken: 'stale', refreshToken: 'r1' });
    api.me.mockRejectedValue(new HttpError(401));
    mount();
    expect(await screen.findByText('anonymous:nobody')).toBeInTheDocument();
    expect(tokenStore.access).toBeNull();
  });

  it('stores the tokens and loads the account on login, trimming the email', async () => {
    api.login.mockResolvedValue(tokens);
    api.me.mockResolvedValue(makeAccount('CANDIDATE', { email: 'a@b.tn' }));
    mount();
    await act(async () => {
      await auth.login(' a@b.tn ', 'secret123');
    });
    expect(api.login).toHaveBeenCalledWith('a@b.tn', 'secret123');
    expect(tokenStore.access).toBe('a1');
    expect(tokenStore.refresh).toBe('r1');
    expect(screen.getByText('authenticated:a@b.tn')).toBeInTheDocument();
  });

  it('does not keep the tokens when the account cannot be loaded after login', async () => {
    api.login.mockResolvedValue(tokens);
    api.me.mockRejectedValue(new HttpError(403));
    mount();
    await act(async () => {
      await expect(auth.login('a@b.tn', 'x')).rejects.toBeTruthy();
    });
    expect(tokenStore.access).toBeNull();
    expect(screen.getByText('anonymous:nobody')).toBeInTheDocument();
  });

  it('registers without logging the user in', async () => {
    api.register.mockResolvedValue(makeAccount('CANDIDATE'));
    mount();
    await act(async () => {
      await auth.register({ email: ' new@b.tn ', password: 'secret123', role: 'CANDIDATE' });
    });
    expect(api.register).toHaveBeenCalledWith({ email: 'new@b.tn', password: 'secret123', role: 'CANDIDATE' });
    expect(api.login).not.toHaveBeenCalled();
    expect(screen.getByText('anonymous:nobody')).toBeInTheDocument();
  });

  it('logs out locally first, then revokes the refresh token, even if the call fails', async () => {
    tokenStore.set({ accessToken: 'a1', refreshToken: 'r1' });
    api.me.mockResolvedValue(makeAccount('CANDIDATE', { email: 'a@b.tn' }));
    api.logout.mockRejectedValue(new Error('network'));
    mount();
    await screen.findByText('authenticated:a@b.tn');
    await act(async () => {
      await auth.logout();
    });
    expect(api.logout).toHaveBeenCalledWith('r1');
    expect(tokenStore.access).toBeNull();
    expect(screen.getByText('anonymous:nobody')).toBeInTheDocument();
  });

  it('becomes anonymous when the session expires during use', async () => {
    tokenStore.set({ accessToken: 'a1', refreshToken: 'r1' });
    api.me.mockResolvedValue(makeAccount('CANDIDATE', { email: 'a@b.tn' }));
    mount();
    await screen.findByText('authenticated:a@b.tn');
    act(() => {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    });
    await waitFor(() => expect(screen.getByText('anonymous:nobody')).toBeInTheDocument());
  });
});
