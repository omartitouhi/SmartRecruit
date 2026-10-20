import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { applicationsApi } from '../../api/applications';
import { jobsApi, type JobSummary } from '../../api/jobs';
import { Button, ButtonLink } from '../../components/ui/Button';
import { ScoreBreakdown, ScoreChip } from '../../components/ui/ScoreBreakdown';
import { Alert, EmptyState, ErrorState, SkeletonRows } from '../../components/ui/States';
import { ApplicationStatusTag } from '../../components/ui/Tag';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { APPLICATION_STATUS, formatDateShort, formatDateTime, formatScore } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import { APPLICATION_STATUS_TRANSITIONS, type Application } from '../../types';

interface Row {
  application: Application;
  job: JobSummary | null;
}

async function loadRows(): Promise<Row[]> {
  const applications = await applicationsApi.mine();
  // Offers are fetched once each; a removed offer still shows the application.
  const ids = [...new Set(applications.map((a) => a.jobId))];
  const jobs = await Promise.all(ids.map((id) => jobsApi.get(id).catch(() => null)));
  const byId = new Map(ids.map((id, i) => [id, jobs[i]]));
  return applications.map((application) => ({ application, job: byId.get(application.jobId) ?? null }));
}

export function ApplicationsPage() {
  useDocumentTitle('Mes candidatures');
  const notify = useToast();
  const [params, setParams] = useSearchParams();
  const rows = useAsync(loadRows, []);
  const [withdrawing, setWithdrawing] = useState(false);
  const [confirmWithdraw, setConfirmWithdraw] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const list = rows.data ?? [];

  // A new application is scored in the background: refresh quietly until every score has arrived.
  const awaitingScore = list.some((r) => !r.application.matchScore && r.application.status !== 'WITHDRAWN');
  useEffect(() => {
    if (!awaitingScore) return;
    const timer = setInterval(() => {
      applicationsApi.mine().then(
        (fresh) =>
          rows.setData((prev) =>
            (prev ?? []).map((r) => ({ ...r, application: fresh.find((a) => a.id === r.application.id) ?? r.application })),
          ),
        () => undefined,
      );
    }, 4000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [awaitingScore]);
  const selectedId = params.get('id') ?? list[0]?.application.id;
  const selected = list.find((r) => r.application.id === selectedId);

  function select(id: string) {
    setParams({ id }, { replace: true });
    setConfirmWithdraw(false);
    setActionError(null);
  }

  async function withdraw(application: Application) {
    setWithdrawing(true);
    setActionError(null);
    try {
      await applicationsApi.withdraw(application.id);
      const entry = { status: 'WITHDRAWN' as const, changedAt: new Date().toISOString(), changedBy: application.candidateId };
      rows.setData((prev) =>
        (prev ?? []).map((r) =>
          r.application.id === application.id
            ? { ...r, application: { ...application, status: entry.status, statusHistory: [...application.statusHistory, entry] } }
            : r,
        ),
      );
      setConfirmWithdraw(false);
      notify('Candidature retirée.');
    } catch (err) {
      setActionError(toUiError(err).message);
    } finally {
      setWithdrawing(false);
    }
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Mes candidatures</h1>
          <p className="page-head__text">Où en est chaque candidature, et ce qui a compté dans votre score.</p>
        </div>
      </header>

      {rows.error ? (
        <ErrorState error={rows.error} onRetry={rows.reload} />
      ) : rows.loading ? (
        <div className="panel">
          <SkeletonRows rows={4} />
        </div>
      ) : list.length === 0 ? (
        <EmptyState title="Aucune candidature pour l’instant" action={<ButtonLink to="/candidate/jobs">Voir les offres</ButtonLink>}>
          Ouvrez une offre pour voir votre score, puis postulez en un clic : l’explication du score est jointe à votre
          candidature.
        </EmptyState>
      ) : (
        <div className="split">
          <div className="panel panel--flush">
            <div className="table-wrap">
              <table className="table table--interactive">
                <thead>
                  <tr>
                    <th>Offre</th>
                    <th>Score</th>
                    <th>Statut</th>
                    <th>Envoyée le</th>
                    <th>
                      <span className="visually-hidden">Détail</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {list.map(({ application, job }) => (
                    <tr
                      key={application.id}
                      aria-selected={application.id === selectedId}
                      tabIndex={0}
                      onClick={() => select(application.id)}
                      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), select(application.id))}
                    >
                      <td>
                        <div className="cell-strong">{job?.title ?? 'Offre indisponible'}</div>
                        <div className="muted">{job ? `${job.companyName ?? 'Entreprise'} · ${job.location}` : '—'}</div>
                      </td>
                      <td>
                        <ScoreChip score={application.matchScore?.score} />
                      </td>
                      <td>
                        <ApplicationStatusTag status={application.status} />
                      </td>
                      <td className="muted">{formatDateShort(application.appliedAt)}</td>
                      <td>
                        <Link className="text-link" to={`/candidate/applications?id=${application.id}`} replace>
                          Voir le détail
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {selected && (
            <aside className="panel sticky" aria-label="Détail de la candidature">
              <div className="panel__head">
                <div>
                  <h2 className="panel__title">{selected.job?.title ?? 'Offre indisponible'}</h2>
                  <p className="panel__sub">
                    {selected.job?.companyName ?? '—'} · <ApplicationStatusTag status={selected.application.status} />
                  </p>
                </div>
              </div>

              {selected.application.matchScore ? (
                <>
                  <p className="detail-score">
                    <span className="serif">{formatScore(selected.application.matchScore.score)}</span>
                    <span className="muted"> / 100</span>
                  </p>
                  <ScoreBreakdown match={selected.application.matchScore} compact />
                </>
              ) : (
                <Alert tone="info">Le score est en cours de calcul par le service d’analyse.</Alert>
              )}

              <h3 className="detail-sub">Historique</h3>
              <ol className="timeline">
                {selected.application.statusHistory.map((h, i) => (
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
                {selected.job && (
                  <Link className="text-link" to={`/candidate/jobs/${selected.application.jobId}`}>
                    Voir l’offre
                  </Link>
                )}
                {APPLICATION_STATUS_TRANSITIONS[selected.application.status].includes('WITHDRAWN') &&
                  (confirmWithdraw ? (
                    <span className="confirm-inline">
                      <span>Retirer cette candidature ?</span>
                      <Button variant="danger" loading={withdrawing} onClick={() => withdraw(selected.application)}>
                        Oui, retirer
                      </Button>
                      <Button variant="ghost" onClick={() => setConfirmWithdraw(false)}>
                        Annuler
                      </Button>
                    </span>
                  ) : (
                    <Button variant="ghost" onClick={() => setConfirmWithdraw(true)}>
                      Retirer ma candidature
                    </Button>
                  ))}
              </div>
              {actionError && <Alert tone="error">{actionError}</Alert>}
            </aside>
          )}
        </div>
      )}
    </div>
  );
}
