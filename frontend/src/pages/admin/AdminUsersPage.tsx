import { useState } from 'react';
import { dashboardApi } from '../../api/dashboard';
import { useAuth } from '../../auth/context';
import { Button } from '../../components/ui/Button';
import { SelectField } from '../../components/ui/Field';
import { Pager } from '../../components/ui/Pager';
import { Alert, EmptyState, ErrorState, SkeletonRows } from '../../components/ui/States';
import { Tag } from '../../components/ui/Tag';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { ACCOUNT_STATUS, formatDateShort, pluralize, ROLE_LABEL } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { Account, AccountStatus, Role } from '../../types';

const PAGE_SIZE = 20;

/** FE-ADMIN-01 : comptes filtrables par rôle et statut, activation / désactivation en un clic. */
export function AdminUsersPage() {
  useDocumentTitle('Utilisateurs');
  const { account: me } = useAuth();
  const notify = useToast();
  const [role, setRole] = useState<Role | ''>('');
  const [status, setStatus] = useState<AccountStatus | ''>('');
  const [page, setPage] = useState(0);
  const users = useAsync(() => dashboardApi.users({ role, status, page, size: PAGE_SIZE }), [role, status, page]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function toggle(user: Account) {
    const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    setBusy(user.id);
    setError(null);
    try {
      const updated = await dashboardApi.setUserStatus(user.id, next);
      users.setData((p) => ({
        ...(p as NonNullable<typeof p>),
        content: (p?.content ?? []).map((u) => (u.id === updated.id ? updated : u)),
      }));
      notify(next === 'DISABLED' ? `Compte de ${user.email} désactivé.` : `Compte de ${user.email} réactivé.`);
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setBusy(null);
    }
  }

  const filter = <T,>(set: (v: T) => void) => (e: { target: { value: string } }) => {
    set(e.target.value as T);
    setPage(0);
  };

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Utilisateurs</h1>
          <p className="page-head__text">Désactivez un compte pour couper l’accès : ses sessions sont révoquées.</p>
        </div>
        {users.data && <p className="muted">{pluralize(users.data.totalElements, 'compte')}</p>}
      </header>

      <form className="toolbar panel" role="search" aria-label="Filtrer les utilisateurs" onSubmit={(e) => e.preventDefault()}>
        <SelectField
          label="Rôle"
          placeholder="Tous"
          value={role}
          onChange={filter(setRole)}
          options={Object.entries(ROLE_LABEL).map(([value, label]) => ({ value, label }))}
        />
        <SelectField
          label="Statut"
          placeholder="Tous"
          value={status}
          onChange={filter(setStatus)}
          options={Object.entries(ACCOUNT_STATUS).map(([value, s]) => ({ value, label: s.label }))}
        />
      </form>

      {error && (
        <div style={{ margin: 'var(--space-4) 0' }}>
          <Alert tone="error">{error}</Alert>
        </div>
      )}

      {users.error ? (
        <ErrorState error={users.error} onRetry={users.reload} />
      ) : !users.data ? (
        <div className="panel">
          <SkeletonRows rows={6} />
        </div>
      ) : users.data.content.length === 0 ? (
        <EmptyState title="Aucun compte ne correspond">Retirez un filtre pour élargir la liste.</EmptyState>
      ) : (
        <div aria-busy={users.loading}>
          <div className="panel panel--flush">
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Email</th>
                    <th>Rôle</th>
                    <th>Statut</th>
                    <th>Inscrit le</th>
                    <th>
                      <span className="visually-hidden">Action</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {users.data.content.map((user) => (
                    <tr key={user.id}>
                      <td className="cell-strong">
                        {user.email}
                        {user.id === me?.id && <span className="muted"> (vous)</span>}
                      </td>
                      <td>{ROLE_LABEL[user.role]}</td>
                      <td>
                        <Tag tone={ACCOUNT_STATUS[user.status].tone}>{ACCOUNT_STATUS[user.status].label}</Tag>
                      </td>
                      <td className="muted">{formatDateShort(user.createdAt)}</td>
                      <td style={{ textAlign: 'right' }}>
                        {user.status !== 'DELETED' && (
                          <Button
                            variant="ghost"
                            loading={busy === user.id}
                            disabled={user.id === me?.id}
                            onClick={() => toggle(user)}
                            aria-label={`${user.status === 'ACTIVE' ? 'Désactiver' : 'Activer'} le compte de ${user.email}`}
                          >
                            {user.status === 'ACTIVE' ? 'Désactiver' : 'Activer'}
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <Pager page={page} totalPages={users.data.totalPages} onPage={setPage} />
        </div>
      )}
    </div>
  );
}
