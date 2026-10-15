import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { applicationsApi } from '../../api/applications';
import { candidatesApi, fullName } from '../../api/candidates';
import { jobsApi } from '../../api/jobs';
import { Button, ButtonLink } from '../../components/ui/Button';
import { ScoreBreakdown, ScoreChip } from '../../components/ui/ScoreBreakdown';
import { Alert, EmptyState, ErrorState, PageSkeleton, SkeletonRows } from '../../components/ui/States';
import { ApplicationStatusTag, JobStatusTag, Tag } from '../../components/ui/Tag';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import {
  APPLICATION_STATUS,
  CONTRACT_LABEL,
  formatDateShort,
  formatDateTime,
  formatScore,
  formatYearMonth,
  LEVEL_LABEL,
  pluralize,
  STATUS_ACTION,
} from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import { APPLICATION_STATUS_TRANSITIONS, type Application, type ApplicationStatus, type CandidateView } from '../../types';

interface Row {
  application: Application;
  candidate: CandidateView | null;
}

async function loadRows(jobId: string): Promise<Row[]> {
  const applications = await applicationsApi.forJob(jobId);
  const candidates = await Promise.all(applications.map((a) => candidatesApi.getCandidate(a.candidateId).catch(() => null)));
  return applications
    .map((application, i) => ({ application, candidate: candidates[i] }))
    .sort((a, b) => (b.application.matchScore?.score ?? -1) - (a.application.matchScore?.score ?? -1));
}

type Filter = 'ALL' | 'TODO' | 'INTERVIEW' | 'DONE';

const FILTERS: { id: Filter; label: string; match: (s: ApplicationStatus) => boolean }[] = [
  { id: 'ALL', label: 'Toutes', match: (s) => s !== 'WITHDRAWN' },
  { id: 'TODO', label: 'À examiner', match: (s) => s === 'RECEIVED' || s === 'IN_REVIEW' },
  { id: 'INTERVIEW', label: 'Entretien', match: (s) => s === 'INTERVIEW' },
  { id: 'DONE', label: 'Décidées', match: (s) => s === 'ACCEPTED' || s === 'REJECTED' },
];

export function CandidateRankingPage() {
  const { jobId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const job = useAsync(() => jobsApi.get(jobId), [jobId]);
  const rows = useAsync(() => loadRows(jobId), [jobId]);
  useDocumentTitle(job.data ? `Candidatures · ${job.data.title}` : 'Candidatures');

  const filter = (params.get('filter') as Filter) || 'ALL';
  const all = rows.data ?? [];
  const visible = all.filter((r) => FILTERS.find((f) => f.id === filter)!.match(r.application.status));
  const selectedId = params.get('id') ?? visible[0]?.application.id;
  const selected = all.find((r) => r.application.id === selectedId);
  const rank = new Map(all.map((r, i) => [r.application.id, i + 1]));

  function update(next: Record<string, string>) {
    const p = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k)));
    setParams(p, { replace: true });
  }

  if (job.error) {
    return (
      <div className="page">
        {job.error.status === 404 || job.error.status === 403 ? (
          <EmptyState title="Offre introuvable" action={<ButtonLink to="/recruiter/company">Retour à mes offres</ButtonLink>}>
            Cette offre n’existe pas ou n’appartient pas à votre entreprise.
          </EmptyState>
        ) : (
          <ErrorState error={job.error} onRetry={job.reload} />
        )}
      </div>
    );
  }
  if (job.loading || !job.data) return <PageSkeleton />;

  return (
    <div className="page">
      <nav className="crumbs" aria-label="Fil d’Ariane">
        <Link to="/recruiter/company">Mes offres</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{job.data.title}</span>
      </nav>
      <header className="page-head">
        <div>
          <h1 className="page-head__title">{job.data.title}</h1>
          <p className="page-head__text">
            {job.data.location} · {CONTRACT_LABEL[job.data.contractType]} · {LEVEL_LABEL[job.data.experienceLevel]} ·{' '}
            <JobStatusTag status={job.data.status} />
          </p>
        </div>
        {rows.data && <p className="muted">{pluralize(all.filter((r) => r.application.status !== 'WITHDRAWN').length, 'candidature')}, triées par score</p>}
      </header>

      <div className="segmented" role="tablist" aria-label="Filtrer les candidatures">
        {FILTERS.map((f) => {
          const count = all.filter((r) => f.match(r.application.status)).length;
          return (
            <button
              key={f.id}
              type="button"
              role="tab"
              aria-selected={filter === f.id}
              className="segmented__item"
              onClick={() => update({ filter: f.id === 'ALL' ? '' : f.id, id: '' })}
            >
              {f.label}
              <span className="segmented__count">{count}</span>
            </button>
          );
        })}
      </div>

      {rows.error ? (
        <ErrorState error={rows.error} onRetry={rows.reload} />
      ) : rows.loading ? (
        <div className="panel">
          <SkeletonRows rows={5} />
        </div>
      ) : visible.length === 0 ? (
        <EmptyState title={all.length ? 'Rien dans cette catégorie' : 'Aucune candidature pour l’instant'}>
          {all.length
            ? 'Choisissez un autre filtre pour voir les autres candidatures.'
            : 'Les candidatures apparaîtront ici dès leur envoi, déjà classées par score.'}
        </EmptyState>
      ) : (
        <div className="split">
          <div className="panel panel--flush">
            <div className="table-wrap">
              <table className="table table--interactive">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Candidat·e</th>
                    <th>Score</th>
                    <th>Statut</th>
                    <th>Reçue le</th>
                  </tr>
                </thead>
                <tbody>
                  {visible.map(({ application, candidate }) => (
                    <tr
                      key={application.id}
                      aria-selected={application.id === selectedId}
                      tabIndex={0}
                      onClick={() => update({ id: application.id })}
                      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), update({ id: application.id }))}
                    >
                      <td className="muted num">{rank.get(application.id)}</td>
                      <td>
                        <span className={candidate?.deleted ? 'muted' : 'cell-strong'}>{candidate ? fullName(candidate) : 'Profil indisponible'}</span>
                      </td>
                      <td>
                        <ScoreChip score={application.matchScore?.score} />
                      </td>
                      <td>
                        <ApplicationStatusTag status={application.status} />
                      </td>
                      <td className="muted">{formatDateShort(application.appliedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {selected && (
            <CandidateDetail
              key={selected.application.id}
              row={selected}
              onUpdated={(updated) =>
                rows.setData((list) =>
                  (list ?? []).map((r) => (r.application.id === updated.id ? { ...r, application: updated } : r)),
                )
              }
            />
          )}
        </div>
      )}
    </div>
  );
}

function CandidateDetail({ row, onUpdated }: { row: Row; onUpdated: (a: Application) => void }) {
  const notify = useToast();
  const { application, candidate } = row;
  const [busy, setBusy] = useState<ApplicationStatus | 'cv' | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Recruiters move applications forward; only the candidate may withdraw.
  const transitions = APPLICATION_STATUS_TRANSITIONS[application.status].filter((s) => s !== 'WITHDRAWN');

  async function move(status: ApplicationStatus) {
    setBusy(status);
    setError(null);
    try {
      onUpdated(await applicationsApi.updateStatus(application.id, status));
      notify(`Candidature passée à « ${APPLICATION_STATUS[status].label} ».`);
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setBusy(null);
    }
  }

  async function openCv() {
    setBusy('cv');
    setError(null);
    try {
      const url = URL.createObjectURL(await candidatesApi.downloadCv(application.candidateId));
      window.open(url, '_blank', 'noopener');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setBusy(null);
    }
  }

  const live = candidate && !candidate.deleted ? candidate : null;

  return (
    <aside className="panel sticky detail" aria-label="Détail du candidat">
      <div className="panel__head">
        <div>
          <h2 className="panel__title">{candidate ? fullName(candidate) : 'Profil indisponible'}</h2>
          <p className="panel__sub">
            {application.matchScore ? `Score ${formatScore(application.matchScore.score)} / 100 · ` : ''}
            <ApplicationStatusTag status={application.status} />
          </p>
        </div>
      </div>

      {candidate?.deleted && (
        <Alert tone="neutral" title="Compte supprimé">
          Ce candidat a exercé son droit à l’oubli. Ses données personnelles ne sont plus disponibles.
        </Alert>
      )}

      {live && (
        <dl className="props detail__props">
          <dt>Email</dt>
          <dd>
            <a className="text-link" href={`mailto:${live.email}`}>
              {live.email}
            </a>
          </dd>
          {live.phone && (
            <>
              <dt>Téléphone</dt>
              <dd>{live.phone}</dd>
            </>
          )}
          {live.location && (
            <>
              <dt>Ville</dt>
              <dd>{live.location}</dd>
            </>
          )}
        </dl>
      )}

      {application.matchScore && <ScoreBreakdown match={application.matchScore} compact />}

      {live && live.experiences.length > 0 && (
        <>
          <h3 className="detail-sub">Parcours</h3>
          <ul className="career">
            {live.experiences.map((x, i) => (
              <li key={i}>
                <span className="cell-strong">{x.title}</span> · {x.company}
                <span className="muted">
                  {' '}
                  · {formatYearMonth(x.startDate)} – {formatYearMonth(x.endDate)}
                </span>
              </li>
            ))}
            {live.education.map((x, i) => (
              <li key={`e${i}`}>
                <span className="cell-strong">{x.degree}</span> · {x.institution}
                <span className="muted"> · {x.year}</span>
              </li>
            ))}
          </ul>
          <div className="tag-list" style={{ marginTop: 12 }}>
            {live.skills.map((s) => (
              <Tag key={s}>{s}</Tag>
            ))}
          </div>
        </>
      )}

      <h3 className="detail-sub">Historique</h3>
      <ol className="timeline">
        {application.statusHistory.map((h, i) => (
          <li key={i}>
            <span className="timeline__dot" aria-hidden="true" />
            <span>
              <span className="cell-strong">{APPLICATION_STATUS[h.status].label}</span>
              <span className="muted"> · {formatDateTime(h.changedAt)}</span>
            </span>
          </li>
        ))}
      </ol>

      <div className="detail-actions">
        {transitions.map((status, i) => (
          <Button
            key={status}
            variant={i === 0 && status !== 'REJECTED' ? 'primary' : 'ghost'}
            loading={busy === status}
            disabled={busy !== null && busy !== status}
            onClick={() => move(status)}
          >
            {STATUS_ACTION[status]}
          </Button>
        ))}
        {live?.cvUrl && (
          <Button variant="ghost" loading={busy === 'cv'} onClick={openCv}>
            Voir le CV
          </Button>
        )}
      </div>
      {transitions.length === 0 && (
        <p className="field__hint">Décision prise : cette candidature ne peut plus changer de statut.</p>
      )}
      {error && <Alert tone="error">{error}</Alert>}
    </aside>
  );
}
