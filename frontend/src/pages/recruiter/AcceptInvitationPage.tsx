import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { companiesApi } from '../../api/companies';
import { Button, ButtonLink } from '../../components/ui/Button';
import { Alert, EmptyState } from '../../components/ui/States';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { useDocumentTitle } from '../../lib/useAsync';

/** Lien reçu par email : /accept-invitation?token=… — l'utilisateur confirme avant de rejoindre l'entreprise. */
export function AcceptInvitationPage() {
  useDocumentTitle('Invitation');
  const token = useSearchParams()[0].get('token');
  const navigate = useNavigate();
  const notify = useToast();
  const [accepting, setAccepting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!token) {
    return (
      <div className="page page--doc">
        <EmptyState title="Lien d’invitation incomplet" action={<ButtonLink to="/recruiter/jobs">Retour à mon espace</ButtonLink>}>
          Ouvrez le lien tel qu’il figure dans l’email d’invitation.
        </EmptyState>
      </div>
    );
  }

  async function accept() {
    setAccepting(true);
    setError(null);
    try {
      const company = await companiesApi.acceptInvitation(token as string);
      notify(`Vous faites maintenant partie de ${company.name}.`);
      navigate('/recruiter', { replace: true });
    } catch (err) {
      const ui = toUiError(err);
      setError(
        ui.status === 410
          ? 'Cette invitation a expiré. Demandez à votre collègue de vous en envoyer une nouvelle.'
          : ui.status === 400
            ? 'Cette invitation n’est pas valide ou a déjà été utilisée.'
            : ui.status === 409
              ? 'Votre compte est déjà rattaché à une entreprise.'
              : ui.message,
      );
      setAccepting(false);
    }
  }

  return (
    <div className="page page--doc">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Rejoindre une entreprise</h1>
          <p className="page-head__text">
            Vous avez été invité·e à rejoindre une équipe de recrutement. Vous verrez ses offres et ses candidatures.
          </p>
        </div>
      </header>
      {error && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <Alert tone="error">{error}</Alert>
        </div>
      )}
      <div className="detail-actions">
        <Button size="lg" loading={accepting} onClick={accept}>
          Accepter l’invitation
        </Button>
        <ButtonLink to="/recruiter/jobs" variant="ghost" size="lg">
          Plus tard
        </ButtonLink>
      </div>
    </div>
  );
}
