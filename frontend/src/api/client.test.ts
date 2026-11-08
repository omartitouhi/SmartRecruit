import axios from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockHttp } from '../test/http';
import { apiClient, MOCK_MODULES, parseMockModules, SESSION_EXPIRED_EVENT, tokenStore } from './client';

describe('parseMockModules', () => {
  it('simulates every module when VITE_USE_MOCKS is true', () => {
    expect([...parseMockModules('true', undefined)]).toEqual([...MOCK_MODULES]);
    expect(parseMockModules('true', 'jobs').size).toBe(MOCK_MODULES.length);
  });

  it('simulates only the listed modules, ignoring spaces and unknown names', () => {
    expect([...parseMockModules(undefined, ' jobs, applications ,nope')]).toEqual(['jobs', 'applications']);
  });

  it('simulates nothing by default', () => {
    expect(parseMockModules(undefined, undefined).size).toBe(0);
    expect(parseMockModules('false', '').size).toBe(0);
  });
});

describe('token interceptor', () => {
  beforeEach(() => {
    tokenStore.set({ accessToken: 'old-access', refreshToken: 'old-refresh' });
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('adds the bearer token to every call', async () => {
    const calls = mockHttp();
    await apiClient.get('/jobs');
    expect(calls[0].headers.Authorization).toBe('Bearer old-access');
  });

  it('sends no Authorization header without a session', async () => {
    tokenStore.clear();
    const calls = mockHttp();
    await apiClient.get('/jobs');
    expect(calls[0].headers.Authorization).toBeUndefined();
  });

  it('refreshes once on a 401 and retries with the new token', async () => {
    const refresh = vi
      .spyOn(axios, 'post')
      .mockResolvedValue({ data: { accessToken: 'new-access', refreshToken: 'new-refresh' } });
    const calls = mockHttp((c) => (c.headers.Authorization === 'Bearer new-access' ? { data: { ok: true } } : { status: 401 }));

    const response = await apiClient.get('/applications/me');

    expect(response.data).toEqual({ ok: true });
    expect(refresh).toHaveBeenCalledWith('/api/v1/auth/refresh', { refreshToken: 'old-refresh' });
    expect(calls).toHaveLength(2);
    expect(tokenStore.access).toBe('new-access');
    expect(tokenStore.refresh).toBe('new-refresh');
  });

  it('shares one refresh between concurrent 401s (refresh tokens are single-use)', async () => {
    const refresh = vi
      .spyOn(axios, 'post')
      .mockResolvedValue({ data: { accessToken: 'new-access', refreshToken: 'new-refresh' } });
    mockHttp((c) => (c.headers.Authorization === 'Bearer new-access' ? {} : { status: 401 }));

    await Promise.all([apiClient.get('/a'), apiClient.get('/b'), apiClient.get('/c')]);

    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('clears the session and signals expiry when the refresh is refused', async () => {
    vi.spyOn(axios, 'post').mockRejectedValue(new Error('401'));
    mockHttp(() => ({ status: 401 }));
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);

    await expect(apiClient.get('/jobs')).rejects.toMatchObject({ response: { status: 401 } });

    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
    expect(expired).toHaveBeenCalledTimes(1);
    expect(tokenStore.access).toBeNull();
    expect(tokenStore.refresh).toBeNull();
  });

  it('expires the session on a 401 when there is no refresh token', async () => {
    tokenStore.clear();
    const refresh = vi.spyOn(axios, 'post');
    mockHttp(() => ({ status: 401 }));
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);

    await expect(apiClient.get('/jobs')).rejects.toBeTruthy();

    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
    expect(refresh).not.toHaveBeenCalled();
    expect(expired).toHaveBeenCalled();
  });

  it.each(['/auth/login', '/auth/register', '/auth/refresh', '/auth/forgot-password'])(
    'does not try to refresh when %s answers 401 (wrong credentials, not an expired token)',
    async (url) => {
      const refresh = vi.spyOn(axios, 'post');
      mockHttp(() => ({ status: 401 }));
      await expect(apiClient.post(url, {})).rejects.toMatchObject({ response: { status: 401 } });
      expect(refresh).not.toHaveBeenCalled();
      expect(tokenStore.access).toBe('old-access');
    },
  );

  it('does refresh on a 401 from /auth/me, so a reload with an expired access token keeps the session', async () => {
    const refresh = vi
      .spyOn(axios, 'post')
      .mockResolvedValue({ data: { accessToken: 'new-access', refreshToken: 'new-refresh' } });
    mockHttp((c) => (c.headers.Authorization === 'Bearer new-access' ? { data: { id: '1' } } : { status: 401 }));

    const response = await apiClient.get('/auth/me');

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(response.data).toEqual({ id: '1' });
  });

  it('passes other errors through untouched', async () => {
    const refresh = vi.spyOn(axios, 'post');
    mockHttp(() => ({ status: 403 }));
    await expect(apiClient.get('/admin')).rejects.toMatchObject({ response: { status: 403 } });
    expect(refresh).not.toHaveBeenCalled();
  });
});
