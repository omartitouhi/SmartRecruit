import { useState, type FormEvent } from 'react';
import { dashboardApi, type AuditFilters } from '../../api/dashboard';
import { Button } from '../../components/ui/Button';
import { SelectField, TextField } from '../../components/ui/Field';
import { Pager } from '../../components/ui/Pager';
import { EmptyState, ErrorState, SkeletonRows } from '../../components/ui/States';
import { AUDIT_ACTION_LABEL, formatDateTime, formatInt } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import { AUDIT_ACTIONS } from '../../types';

const PAGE_SIZE = 20;

interface Draft {
  from: string;
  to: string;
  actorId: string;
  action: string;
}

const EMPTY: Draft = { from: '', to: '', actorId: '', action: '' };

/** « 2026-10-14 » (champ date) → borne ISO 8601 UTC du début ou de la fin de ce jour, à l'heure locale. */
const bound = (date: string, end: boolean) =>
  date ? new Date(`${date}T${end ? '23:59:59.999' : '00:00:00'}`).toISOString() : undefined;

/** FE-ADMIN-03 : journal d'audit filtrable par période, utilisateur et type d'événement. */
export function AdminAuditPage() {
  useDocumentTitle('Journal d’audit');
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [applied, setApplied] = useState<Draft>(EMPTY);
  const [page, setPage] = useState(0);
  const log = useAsync(() => {
    const filters: AuditFilters = {
      from: bound(applied.from, false),
      to: bound(applied.to, true),
      actorId: applied.actorId.trim(),
      action: applied.action,
      page,
      size: PAGE_SIZE,
    };
    return dashboardApi.auditLog(filters);
  }, [applied, page]);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setApplied(draft);
    setPage(0);
  }

  function reset() {
    setDraft(EMPTY);
    setApplied(EMPTY);
    setPage(0);
  }

  const hasFilters = Object.values(applied).some(Boolean);

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Journal d’audit</h1>
          <p className="page-head__text">Actions sensibles : consultations de profils et de CV, changements de statut, modération.</p>
        </div>
        {log.data && <p className="muted">{formatInt(log.data.totalElements)} entrées</p>}
      </header>

      <form className="toolbar panel" role="search" aria-label="Filtrer le journal" onSubmit={onSubmit}>
        <TextField label="Du" type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} max={draft.to || undefined} />
        <TextField label="Au" type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} min={draft.from || undefined} />
        <TextField
          label="Identifiant de l’utilisateur"
          placeholder="UUID de l’auteur"
          value={draft.actorId}
          onChange={(e) => setDraft({ ...draft, actorId: e.target.value })}
        />
        <SelectField
          label="Type d’événement"
          placeholder="Tous"
          value={draft.action}
          onChange={(e) => setDraft({ ...draft, action: e.target.value })}
          options={AUDIT_ACTIONS.map((value) => ({ value, label: AUDIT_ACTION_LABEL[value] }))}
        />
        <Button type="submit">Filtrer</Button>
        {hasFilters && (
          <Button variant="ghost" onClick={reset}>
            Réinitialiser
          </Button>
        )}
      </form>

      <section className="panel panel--flush audit" aria-labelledby="audit-title" style={{ marginTop: 'var(--space-4)' }}>
        <h2 className="visually-hidden" id="audit-title">
          Événements
        </h2>
        {log.error ? (
          <div style={{ padding: 24 }}>
            <ErrorState error={log.error} onRetry={log.reload} />
          </div>
        ) : !log.data ? (
          <div style={{ padding: 24 }}>
            <SkeletonRows rows={6} />
          </div>
        ) : log.data.content.length === 0 ? (
          <div style={{ padding: 24 }}>
            <EmptyState title={hasFilters ? 'Aucun événement ne correspond' : 'Journal vide'}>
              {hasFilters ? 'Élargissez la période ou retirez un filtre.' : 'Aucune action enregistrée pour l’instant.'}
            </EmptyState>
          </div>
        ) : (
          <div aria-busy={log.loading}>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Action</th>
                    <th>Ressource</th>
                    <th>Auteur</th>
                  </tr>
                </thead>
                <tbody>
                  {log.data.content.map((entry) => (
                    <tr key={entry.id}>
                      <td className="muted num">{formatDateTime(entry.timestamp)}</td>
                      <td className="cell-strong">{AUDIT_ACTION_LABEL[entry.action] ?? entry.action}</td>
                      <td>
                        {entry.resourceType} <span className="muted id">{entry.resourceId.slice(0, 8)}</span>
                      </td>
                      <td>
                        <button type="button" className="text-link id" title="Filtrer sur cet auteur" onClick={() => {
                          setDraft({ ...draft, actorId: entry.actorId });
                          setApplied({ ...applied, actorId: entry.actorId });
                          setPage(0);
                        }}>
                          {entry.actorId.slice(0, 8)}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="audit__pager">
              <Pager page={page} totalPages={log.data.totalPages} onPage={setPage} label="Pagination du journal" />
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
