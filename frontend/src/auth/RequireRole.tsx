import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { PageSkeleton } from '../components/ui/States';
import type { Role } from '../types';
import { HOME_BY_ROLE, useAuth } from './context';

/** Garde de route : connexion obligatoire et rôle attendu (le backend vérifie aussi, cf. BE-AUTH-04). */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { status, account } = useAuth();
  const location = useLocation();

  if (status === 'loading') return <PageSkeleton />;
  if (status === 'anonymous' || !account) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  if (!roles.includes(account.role)) return <Navigate to="/forbidden" replace />;
  return children;
}

/**
 * Pages publiques d'authentification : un visiteur déjà connecté est renvoyé vers son espace.
 * `busy` est vrai pendant la soumission du formulaire, pour laisser la page choisir sa destination
 * (`?next=`, ou le profil juste après l'inscription) au lieu de rediriger dès que la session existe.
 */
export function GuestOnly({ busy = false, children }: { busy?: boolean; children: ReactNode }) {
  const { account } = useAuth();
  if (account && !busy) return <Navigate to={HOME_BY_ROLE[account.role]} replace />;
  return children;
}
