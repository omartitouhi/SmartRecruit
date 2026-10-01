import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { TokenResponse } from '../types';

// Same-origin by default: Vite (dev) and nginx (Docker) proxy /api to the gateway, so no CORS is needed.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

/** true → toutes les données viennent de src/api/mock (aucun backend requis). */
export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true';

const ACCESS_KEY = 'smartrecruit_token';
const REFRESH_KEY = 'smartrecruit_refresh_token';

function read(key: string) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    // Storage blocked (private mode): the session simply won't survive a reload.
  }
}

export const tokenStore = {
  get access() {
    return read(ACCESS_KEY);
  },
  get refresh() {
    return read(REFRESH_KEY);
  },
  set(tokens: Pick<TokenResponse, 'accessToken' | 'refreshToken'>) {
    write(ACCESS_KEY, tokens.accessToken);
    write(REFRESH_KEY, tokens.refreshToken);
  },
  clear() {
    write(ACCESS_KEY, null);
    write(REFRESH_KEY, null);
  },
};

/** Émis quand le refresh token est refusé : l'AuthProvider déconnecte l'utilisateur. */
export const SESSION_EXPIRED_EVENT = 'smartrecruit:session-expired';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

apiClient.interceptors.request.use((config) => {
  const token = tokenStore.access;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Refresh tokens are single-use (rotation), so concurrent 401s must share one refresh call.
let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = tokenStore.refresh;
  if (!refreshToken) return null;
  try {
    const { data } = await axios.post<TokenResponse>(`${API_BASE_URL}/auth/refresh`, { refreshToken });
    tokenStore.set(data);
    return data.accessToken;
  } catch {
    return null;
  }
}

apiClient.interceptors.response.use(undefined, async (error: AxiosError) => {
  const config = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
  const isAuthCall = config?.url?.startsWith('/auth/') ?? false;

  if (error.response?.status !== 401 || !config || config._retried || isAuthCall) {
    throw error;
  }

  refreshing ??= refreshAccessToken().finally(() => {
    refreshing = null;
  });
  const token = await refreshing;
  if (!token) {
    tokenStore.clear();
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    throw error;
  }

  config._retried = true;
  config.headers.Authorization = `Bearer ${token}`;
  return apiClient(config);
});
