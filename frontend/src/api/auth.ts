import type { Account, Role, TokenResponse } from '../types';
import { apiClient, isMocked } from './client';
import { mockAuthApi } from './mock/auth';

export interface RegisterRequest {
  email: string;
  password: string;
  /** ADMIN est refusé par le backend : seuls CANDIDATE et RECRUITER sont auto-attribuables. */
  role: Exclude<Role, 'ADMIN'>;
}

export interface AuthApi {
  register(request: RegisterRequest): Promise<Account>;
  login(email: string, password: string): Promise<TokenResponse>;
  me(): Promise<Account>;
  logout(refreshToken: string): Promise<void>;
  forgotPassword(email: string): Promise<void>;
  resetPassword(token: string, newPassword: string): Promise<void>;
}

// auth-service — AuthController (BE-AUTH-01 → 06).
export const httpAuthApi: AuthApi = {
  async register(request) {
    return (await apiClient.post<Account>('/auth/register', request)).data;
  },
  async login(email, password) {
    return (await apiClient.post<TokenResponse>('/auth/login', { email, password })).data;
  },
  async me() {
    return (await apiClient.get<Account>('/auth/me')).data;
  },
  async logout(refreshToken) {
    await apiClient.post('/auth/logout', { refreshToken });
  },
  async forgotPassword(email) {
    await apiClient.post('/auth/forgot-password', { email });
  },
  async resetPassword(token, newPassword) {
    await apiClient.post('/auth/reset-password', { token, newPassword });
  },
};

export const authApi: AuthApi = isMocked('auth') ? mockAuthApi : httpAuthApi;
