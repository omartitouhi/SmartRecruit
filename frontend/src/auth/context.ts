import { createContext, useContext } from 'react';
import type { RegisterRequest } from '../api/auth';
import type { Account, Role } from '../types';

export type AuthStatus = 'loading' | 'anonymous' | 'authenticated';

export interface AuthContextValue {
  status: AuthStatus;
  account: Account | null;
  login(email: string, password: string): Promise<Account>;
  /** Crée le compte sans le connecter : l'utilisateur passe ensuite par la page de connexion. */
  register(request: RegisterRequest): Promise<Account>;
  logout(): Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}

/** Page d'arrivée après connexion, selon le rôle. */
export const HOME_BY_ROLE: Record<Role, string> = {
  CANDIDATE: '/candidate',
  RECRUITER: '/recruiter',
  ADMIN: '/admin',
};
