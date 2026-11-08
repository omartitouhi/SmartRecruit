import { useState, type FormEvent } from 'react';
import { dashboardApi } from '../../api/dashboard';
import { jobsApi, type JobSummary } from '../../api/jobs';
import { Button } from '../../components/ui/Button';
import { SelectField, TextAreaField, TextField } from '../../components/ui/Field';
import { Pager } from '../../components/ui/Pager';
import { Alert, EmptyState, ErrorState, SkeletonRows } from '../../components/ui/States';
import { JobStatusTag } from '../../components/ui/Tag';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { formatDateShort, JOB_STATUS, pluralize } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { JobStatus } from '../../types';

const PAGE_SIZE = 20;
const REASON_MAX = 500;

/** FE-ADMIN-02 : toutes les offres, filtrables, avec retrait motivé (statut MODERATED). */
export function AdminJobsPage() {
  useDocumentTitle('Modération des offres');
  const [status, setStatus] = useState<JobStatus | ''>('PUBLISHED');
  const [keyword, setKeyword] = useState('');
  const [applied, setApplied] = useState('');
  const [page, setPage] = useState(0);
  const jobs = useAsync(
    () => jobsApi.search({ status, keyword: applied, page, size: PAGE_SIZE }),
    [status, applied, page],
  );
  const [target, setTarget] = useState<JobSummary | null>(null);

  function onSearch(e: FormEvent) {
    e.preventDefault();
    setApplied(keyword.trim());
    setPage(0);
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Modération des offres</h1>
          <p className="page-head__text">
            Retirez une offre qui enfreint les règles : le recruteur voit le motif, l’offre disparaît de la recherche.
          </p>
        </div>
        {jobs.data && <p className="muted">{pluralize(jobs.data.totalElements, 'offre')}</p>}
      </header>

      <form className="toolbar panel" role="search" aria-label="Filtrer les offres" onSubmit={onSearch}>
        <TextField label="Recherche" placeholder="Intitulé ou description" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <SelectField
          label="Statut"
          placeholder="Tous"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as JobStatus | '');
            setPage(0);
          }}
          options={Object.entries(JOB_STATUS).map(([value, s]) => ({ value, label: s.label }))}
        />
        <Button type="submit">Rechercher</Button>
      </form>

      {jobs.error ? (
        <ErrorState error={jobs.error} onRetry={jobs.reload} />
      ) : !jobs.data ? (
        <div className="panel" style={{ marginTop: 'var(--space-4)' }}>
          <SkeletonRows rows={6} />
        </div>
      ) : jobs.data.content.length === 0 ? (
        <EmptyState title="Aucune offre ne correspond">Changez le statut ou la recherche.</EmptyState>
      ) : (
        <div aria-busy={jobs.loading} style={{ marginTop: 'var(--space-4)' }}>
          <div className="panel panel--flush">
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Offre</th>
                    <th>Statut</th>
                    <th>Publiée le</th>
                    <th>
                      <span className="visually-hidden">Action</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.data.content.map((job) => (
                    <tr key={job.id}>
                      <td>
                        <div className="cell-strong">{job.title}</div>
                        <div className="muted">
                          {job.companyName ?? 'Entreprise'} · {job.location}
                        </div>
                        {job.moderationReason && <div className="muted">Motif : {job.moderationReason}</div>}
                      </td>
                      <td>
                        <JobStatusTag status={job.status} />
                      </td>
                      <td className="muted">{formatDateShort(job.publishedAt)}</td>
                      <td style={{ textAlign: 'right' }}>
                        {job.status !== 'MODERATED' && (
                          <Button variant="danger" onClick={() => setTarget(job)} aria-label={`Retirer l’offre ${job.title}`}>
                            Retirer
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <Pager page={page} totalPages={jobs.data.totalPages} onPage={setPage} />
        </div>
      )}

      {target && (
        <ModerationForm
          job={target}
          onCancel={() => setTarget(null)}
          onDone={() => {
            setTarget(null);
            jobs.reload();
          }}
        />
      )}
    </div>
  );
}

function ModerationForm({ job, onCancel, onDone }: { job: JobSummary; onCancel: () => void; onDone: () => void }) {
  const notify = useToast();
  const [reason, setReason] = useState('');
  const [fieldError, setFieldError] = useState<string>();
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    if (!reason.trim()) {
      setFieldError('Indiquez le motif du retrait : il est communiqué au recruteur.');
      return;
    }
    setFieldError(undefined);
    setSaving(true);
    try {
      await dashboardApi.moderateJob(job.id, reason.trim());
      notify('Offre retirée.');
      onDone();
    } catch (err) {
      const ui = toUiError(err);
      setFieldError(ui.fieldErrors.reason);
      setFormError(ui.fieldErrors.reason ? null : ui.message);
      setSaving(false);
    }
  }

  return (
    <form className="panel form" onSubmit={onSubmit} noValidate aria-labelledby="moderation-title" style={{ marginTop: 'var(--space-5)' }}>
      <h2 className="panel__title" id="moderation-title">
        Retirer « {job.title} »
      </h2>
      {formError && <Alert tone="error">{formError}</Alert>}
      <TextAreaField
        label="Motif du retrait"
        rows={3}
        maxLength={REASON_MAX}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        error={fieldError}
        hint={`${reason.length} / ${REASON_MAX} caractères`}
      />
      <div className="detail-actions">
        <Button type="submit" variant="danger" loading={saving}>
          Retirer l’offre
        </Button>
        <Button variant="ghost" onClick={onCancel} disabled={saving}>
          Annuler
        </Button>
      </div>
    </form>
  );
}
