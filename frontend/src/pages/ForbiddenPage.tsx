import { HOME_BY_ROLE, useAuth } from '../auth/context';
import { ButtonLink } from '../components/ui/Button';
import { useDocumentTitle } from '../lib/useAsync';

/** Cible de la garde de rôle : l'utilisateur est connecté mais cet espace n'est pas le sien. */
export function ForbiddenPage() {
  useDocumentTitle('Accès refusé');
  const { account } = useAuth();
  return (
    <div className="page page--doc not-found">
      <p className="not-found__code serif">403</p>
      <h1 className="page-head__title">Vous n’avez pas accès à cette page.</h1>
      <p className="page-head__text">Cet espace est réservé à un autre type de compte.</p>
      <div className="detail-actions" style={{ marginTop: 'var(--space-5)' }}>
        <ButtonLink to={account ? HOME_BY_ROLE[account.role] : '/'}>
          {account ? 'Retour à mon espace' : 'Retour à l’accueil'}
        </ButtonLink>
      </div>
    </div>
  );
}
