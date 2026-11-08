import { HOME_BY_ROLE, useAuth } from '../auth/context';
import { ButtonLink } from '../components/ui/Button';
import { useDocumentTitle } from '../lib/useAsync';

export function NotFoundPage() {
  useDocumentTitle('Page introuvable');
  const { account } = useAuth();
  return (
    <div className="page page--doc not-found">
      <p className="not-found__code serif">404</p>
      <h1 className="page-head__title">Cette page n’existe pas.</h1>
      <p className="page-head__text">Le lien est peut-être ancien, ou l’offre a été retirée.</p>
      <div className="detail-actions" style={{ marginTop: 'var(--space-5)' }}>
        <ButtonLink to={account ? HOME_BY_ROLE[account.role] : '/'}>
          {account ? 'Retour à mon espace' : 'Retour à l’accueil'}
        </ButtonLink>
      </div>
    </div>
  );
}
