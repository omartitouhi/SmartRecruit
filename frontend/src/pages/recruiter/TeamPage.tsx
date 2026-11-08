import { useState, type FormEvent } from 'react';
import { companiesApi } from '../../api/companies';
import { useAuth } from '../../auth/context';
import { NoCompany } from '../../components/domain/NoCompany';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { EmptyState, ErrorState, PageSkeleton, SkeletonRows } from '../../components/ui/States';
import { Tag } from '../../components/ui/Tag';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { formatDate, INVITATION_STATUS } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import { useMyCompany } from '../../lib/useCompany';
import type { Company } from '../../types';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** FE-COMP-02 : les recruteurs rattachés à l'entreprise et les invitations envoyées. */
export function TeamPage() {
  useDocumentTitle('Équipe');
  const company = useMyCompany();

  if (company.error) {
    return (
      <div className="page">
        <ErrorState error={company.error} onRetry={company.reload} />
      </div>
    );
  }
  if (company.loading) return <PageSkeleton />;
  if (!company.data) {
    return (
      <div className="page page--doc">
        <NoCompany />
      </div>
    );
  }
  return <Team company={company.data} />;
}

function Team({ company }: { company: Company }) {
  const { account } = useAuth();
  const notify = useToast();
  const invitations = useAsync(() => companiesApi.invitations(company.id), [company.id]);
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function onInvite(e: FormEvent) {
    e.preventDefault();
    if (!EMAIL.test(email.trim())) {
      setError('Saisissez un email valide, par exemple prenom.nom@entreprise.tn.');
      return;
    }
    setSending(true);
    setError(null);
    try {
      const created = await companiesApi.invite(company.id, email.trim());
      invitations.setData((list) => [created, ...(list ?? [])]);
      setEmail('');
      notify(`Invitation envoyée à ${created.email}.`);
    } catch (err) {
      const ui = toUiError(err);
      setError(ui.fieldErrors.email ?? ui.message);
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="page page--doc">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Équipe</h1>
          <p className="page-head__text">
            Les recruteurs de {company.name} voient les mêmes offres et les mêmes candidatures.
          </p>
        </div>
      </header>

      <section className="panel" aria-labelledby="members-title">
        <h2 className="panel__title" id="members-title">
          Membres
        </h2>
        <ul className="member-list">
          {company.members.map((id) => (
            <li key={id}>
              {id === account?.id ? (
                <>
                  <span className="cell-strong">Vous</span> {account.email && <span className="muted">· {account.email}</span>}
                </>
              ) : (
                <>
                  Recruteur·se <span className="muted id">{id.slice(0, 8)}</span>
                </>
              )}
            </li>
          ))}
        </ul>
      </section>

      <section className="panel stack" aria-labelledby="invite-title" style={{ marginTop: 'var(--space-5)' }}>
        <h2 className="panel__title" id="invite-title">
          Inviter un recruteur
        </h2>
        <form className="toolbar" onSubmit={onInvite} noValidate>
          <TextField
            label="Email du recruteur"
            type="email"
            autoComplete="off"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={error ?? undefined}
            hint="Il reçoit un lien valable 7 jours pour rejoindre l’entreprise."
          />
          <Button type="submit" loading={sending}>
            Envoyer l’invitation
          </Button>
        </form>

        {invitations.error ? (
          <ErrorState error={invitations.error} onRetry={invitations.reload} />
        ) : invitations.loading && !invitations.data ? (
          <SkeletonRows rows={2} />
        ) : invitations.data?.length ? (
          <ul className="member-list" aria-label="Invitations envoyées">
            {invitations.data.map((inv) => (
              <li key={inv.id}>
                <span className="cell-strong">{inv.email}</span>{' '}
                <Tag tone={INVITATION_STATUS[inv.status].tone}>{INVITATION_STATUS[inv.status].label}</Tag>{' '}
                <span className="muted">· expire le {formatDate(inv.expiresAt)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState title="Aucune invitation envoyée">Invitez un collègue pour partager le suivi des candidatures.</EmptyState>
        )}
      </section>
    </div>
  );
}
