import { useState } from 'react';
import { Link } from 'react-router-dom';
import { jobsApi } from '../../api/jobs';
import { NoCompany } from '../../components/domain/NoCompany';
import { Button, ButtonLink } from '../../components/ui/Button';
import { Alert, EmptyState, ErrorState, PageSkeleton, SkeletonRows } from '../../components/ui/States';
import { JobStatusTag, Tag } from '../../components/ui/Tag';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { CONTRACT_LABEL, deadlineLabel, formatDateShort, formatSalary, LEVEL_LABEL, pluralize, WORK_MODE_LABEL } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import { useMyCompany } from '../../lib/useCompany';
import type { JobStatus } from '../../types';

/** Action proposée sur une offre selon son statut (les transitions du contrat, API_CONTRACTS.md §2). */
const NEXT: Partial<Record<JobStatus, { status: JobStatus; label: string; done: string }>> = {
  DRAFT: { status: 'PUBLISHED', label: 'Publier', done: 'Offre publiée.' },
  PUBLISHED: { status: 'CLOSED', label: 'Clôturer', done: 'Offre clôturée.' },
  CLOSED: { status: 'PUBLISHED', label: 'Rouvrir', done: 'Offre rouverte.' },
};

/** FE-JOB-04 : mes offres avec statut, date de publication, nombre de candidatures et actions de statut. */
export function RecruiterJobsPage() {
  useDocumentTitle('Mes offres');
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
  return <Jobs companyName={company.data.name} />;
}

function Jobs({ companyName }: { companyName: string }) {
  const notify = useToast();
  const jobs = useAsync(() => jobsApi.search({ companyId: 'me', size: 50 }), []);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  /** EXT-JOB-01: the copy is a draft at the top of the list, to adjust then publish. */
  async function duplicate(jobId: string) {
    setBusy(`copy:${jobId}`);
    setError(null);
    try {
      const copy = await jobsApi.duplicate(jobId);
      jobs.setData((page) => ({
        ...(page as NonNullable<typeof page>),
        totalElements: (page?.totalElements ?? 0) + 1,
        content: [{ ...copy, companyName: null, applicationsCount: 0, saved: null }, ...(page?.content ?? [])],
      }));
      notify(`Brouillon « ${copy.title} » créé : modifiez-le, puis publiez-le.`);
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setBusy(null);
    }
  }

  async function remove(jobId: string, title: string) {
    if (!window.confirm(`Supprimer définitivement le brouillon « ${title} » ?`)) return;
    setBusy(`delete:${jobId}`);
    setError(null);
    try {
      await jobsApi.remove(jobId);
      jobs.setData((page) => ({
        ...(page as NonNullable<typeof page>),
        totalElements: (page?.totalElements ?? 1) - 1,
        content: (page?.content ?? []).filter((j) => j.id !== jobId),
      }));
      notify(`Brouillon « ${title} » supprimé.`);
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setBusy(null);
    }
  }

  async function changeStatus(jobId: string, status: JobStatus, done: string) {
    setBusy(jobId);
    setError(null);
    try {
      const updated = await jobsApi.updateStatus(jobId, status);
      jobs.setData((page) => ({
        ...(page as NonNullable<typeof page>),
        // The status endpoint answers a JobResponse: the list-only fields stay as they were.
        content: (page?.content ?? []).map((j) =>
          j.id === jobId ? { ...j, ...updated, companyName: j.companyName, applicationsCount: j.applicationsCount } : j,
        ),
      }));
      notify(done);
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setBusy(null);
    }
  }

  const rows = jobs.data?.content ?? [];

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Mes offres</h1>
          <p className="page-head__text">
            {companyName}
            {jobs.data && ` · ${pluralize(jobs.data.totalElements, 'offre')}`}
          </p>
        </div>
        <ButtonLink to="/recruiter/jobs/new">Publier une offre</ButtonLink>
      </header>

      {error && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <Alert tone="error">{error}</Alert>
        </div>
      )}

      {jobs.error ? (
        <ErrorState error={jobs.error} onRetry={jobs.reload} />
      ) : jobs.loading && !jobs.data ? (
        <div className="panel">
          <SkeletonRows rows={4} />
        </div>
      ) : rows.length === 0 ? (
        <EmptyState title="Aucune offre pour l’instant" action={<ButtonLink to="/recruiter/jobs/new">Rédiger une offre</ButtonLink>}>
          Une offre publiée apparaît tout de suite dans la recherche des candidats. Leurs candidatures arrivent déjà
          classées par score.
        </EmptyState>
      ) : (
        <div className="panel panel--flush">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Offre</th>
                  <th>Statut</th>
                  <th>Candidatures</th>
                  <th>Publiée le</th>
                  <th>
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((job) => {
                  const next = NEXT[job.status];
                  return (
                    <tr key={job.id}>
                      <td>
                        <Link className="cell-strong text-link" to={`/recruiter/jobs/${job.id}/candidates`}>
                          {job.title}
                        </Link>
                        <div className="muted">
                          {job.location} · {CONTRACT_LABEL[job.contractType]} · {LEVEL_LABEL[job.experienceLevel]}
                          {job.workMode && job.workMode !== 'ON_SITE' && ` · ${WORK_MODE_LABEL[job.workMode]}`}
                          {(job.positions ?? 1) > 1 && ` · ${pluralize(job.positions, 'poste')}`}
                        </div>
                        {formatSalary(job.salaryMin, job.salaryMax) && (
                          <div className="muted">{formatSalary(job.salaryMin, job.salaryMax)}</div>
                        )}
                      </td>
                      <td>
                        <JobStatusTag status={job.status} />
                        {job.status === 'PUBLISHED' && deadlineLabel(job.applicationDeadline) && (
                          <div>
                            <Tag tone={deadlineLabel(job.applicationDeadline)!.tone}>{deadlineLabel(job.applicationDeadline)!.label}</Tag>
                          </div>
                        )}
                        {job.status === 'MODERATED' && job.moderationReason && (
                          <div className="muted">Motif : {job.moderationReason}</div>
                        )}
                      </td>
                      <td>
                        {job.status === 'DRAFT' ? (
                          <span className="muted">—</span>
                        ) : (
                          <Link className="text-link" to={`/recruiter/jobs/${job.id}/candidates`}>
                            {pluralize(job.applicationsCount ?? 0, 'candidature')}
                          </Link>
                        )}
                      </td>
                      <td className="muted">{formatDateShort(job.publishedAt)}</td>
                      <td className="row-actions">
                        {(job.status === 'DRAFT' || job.status === 'PUBLISHED') && (
                          <ButtonLink to={`/recruiter/jobs/${job.id}/edit`} variant="ghost">
                            Modifier
                          </ButtonLink>
                        )}
                        {next ? (
                          <Button
                            variant="ghost"
                            loading={busy === job.id}
                            onClick={() => changeStatus(job.id, next.status, next.done)}
                          >
                            {next.label}
                          </Button>
                        ) : (
                          <span className="muted">Modérée par l’administration</span>
                        )}
                        {job.status !== 'MODERATED' && (
                          <Button
                            variant="ghost"
                            loading={busy === `copy:${job.id}`}
                            disabled={busy !== null && busy !== `copy:${job.id}`}
                            aria-label={`Dupliquer l’offre ${job.title}`}
                            onClick={() => duplicate(job.id)}
                          >
                            Dupliquer
                          </Button>
                        )}
                        {job.status === 'DRAFT' && (
                          <Button
                            variant="ghost"
                            loading={busy === `delete:${job.id}`}
                            disabled={busy !== null && busy !== `delete:${job.id}`}
                            aria-label={`Supprimer le brouillon ${job.title}`}
                            onClick={() => remove(job.id, job.title)}
                          >
                            Supprimer
                          </Button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
