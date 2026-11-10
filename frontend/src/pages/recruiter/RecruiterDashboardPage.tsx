import { useState } from 'react';
import { Link } from 'react-router-dom';
import { applicationsApi } from '../../api/applications';
import { candidatesApi, fullName } from '../../api/candidates';
import { dashboardApi } from '../../api/dashboard';
import { jobsApi } from '../../api/jobs';
import { Funnel } from '../../components/domain/Funnel';
import { NoCompany } from '../../components/domain/NoCompany';
import { PeriodFilter } from '../../components/domain/PeriodFilter';
import { ButtonLink } from '../../components/ui/Button';
import { ScoreChip } from '../../components/ui/ScoreBreakdown';
import { EmptyState, ErrorState, PageSkeleton, Skeleton, SkeletonRows } from '../../components/ui/States';
import { ApplicationStatusTag, Tag } from '../../components/ui/Tag';
import {
  daysUntil,
  deadlineLabel,
  formatDateTimeLong,
  formatDays,
  formatDuration,
  formatInt,
  formatRate,
  INTERVIEW_MODE_LABEL,
  isoDay,
  pluralize,
} from '../../lib/format';
import { periodOf } from '../../lib/period';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import { useMyCompany } from '../../lib/useCompany';
import type { Application, CandidateView, Company, UpcomingInterview } from '../../types';

const JOBS_SCANNED = 3;
const TOP_SHOWN = 5;

interface TopApplication {
  application: Application;
  jobId: string;
  jobTitle: string;
  candidate: CandidateView | null;
}

/** The best-ranked applications across the offers that received the most candidates. */
async function loadTop(): Promise<TopApplication[]> {
  const page = await jobsApi.search({ companyId: 'me', status: 'PUBLISHED', size: 50 });
  const busiest = page.content
    .filter((j) => (j.applicationsCount ?? 0) > 0)
    .sort((a, b) => (b.applicationsCount ?? 0) - (a.applicationsCount ?? 0))
    .slice(0, JOBS_SCANNED);
  const lists = await Promise.all(busiest.map((j) => applicationsApi.forJob(j.id).catch(() => [])));
  const top = busiest
    .flatMap((job, i) => lists[i].map((application) => ({ application, jobId: job.id, jobTitle: job.title })))
    .filter((t) => t.application.matchScore)
    .sort((a, b) => (b.application.matchScore?.score ?? 0) - (a.application.matchScore?.score ?? 0))
    .slice(0, TOP_SHOWN);
  const candidates = await Promise.all(top.map((t) => candidatesApi.getCandidate(t.application.candidateId).catch(() => null)));
  return top.map((t, i) => ({ ...t, candidate: candidates[i] }));
}

const INTERVIEWS_SHOWN = 5;
const CLOSING_WINDOW_DAYS = 7;

interface AgendaRow {
  interview: UpcomingInterview;
  jobTitle: string | null;
  candidate: CandidateView | null;
}

/** EXT-APP-02 : prochains entretiens de l'entreprise, avec l'offre et le nom du candidat. */
async function loadAgenda(): Promise<AgendaRow[]> {
  const interviews = (await applicationsApi.upcomingInterviews()).slice(0, INTERVIEWS_SHOWN);
  const jobIds = [...new Set(interviews.map((i) => i.jobId))];
  const jobs = await Promise.all(jobIds.map((id) => jobsApi.get(id).catch(() => null)));
  const titles = new Map(jobIds.map((id, i) => [id, jobs[i]?.title ?? null]));
  const candidates = await Promise.all(interviews.map((i) => candidatesApi.getCandidate(i.candidateId).catch(() => null)));
  return interviews.map((interview, i) => ({ interview, jobTitle: titles.get(interview.jobId) ?? null, candidate: candidates[i] }));
}

/** EXT-JOB-01 : offres publiées dont la date limite tombe dans les 7 jours, la plus proche d'abord. */
async function loadClosingSoon() {
  const page = await jobsApi.search({ companyId: 'me', status: 'PUBLISHED', size: 50 });
  const last = isoDay(CLOSING_WINDOW_DAYS);
  return page.content
    .filter((j) => j.applicationDeadline && j.applicationDeadline <= last && daysUntil(j.applicationDeadline) >= 0)
    .sort((a, b) => (a.applicationDeadline ?? '').localeCompare(b.applicationDeadline ?? ''));
}

/** FE-DASH-02 : chiffres clés de l'entreprise et accès rapide aux candidatures les mieux classées. */
export function RecruiterDashboardPage() {
  useDocumentTitle('Tableau de bord');
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
  return <Dashboard company={company.data} />;
}

function Dashboard({ company }: { company: Company }) {
  const [days, setDays] = useState('');
  const stats = useAsync(() => dashboardApi.companyStats(company.id, periodOf(days)), [company.id, days]);
  const top = useAsync(loadTop, [company.id]);
  const agenda = useAsync(loadAgenda, [company.id]);
  const closing = useAsync(loadClosingSoon, [company.id]);

  const tiles = stats.data && [
    { label: 'Offres publiées', value: formatInt(stats.data.publishedJobs) },
    { label: 'Candidatures reçues', value: formatInt(stats.data.applicationsReceived) },
    { label: 'Candidatures acceptées', value: formatInt(stats.data.acceptedApplications) },
    { label: 'Taux d’acceptation', value: stats.data.acceptanceRate === null ? '—' : formatRate(stats.data.acceptanceRate) },
  ];
  // EXT-DASH-01: each figure is null when its source service is down.
  const pace = stats.data && [
    { label: 'Délai moyen de décision', value: stats.data.averageDecisionDays == null ? '—' : formatDays(stats.data.averageDecisionDays) },
    { label: 'Entretiens à venir', value: stats.data.upcomingInterviews == null ? '—' : formatInt(stats.data.upcomingInterviews) },
    { label: 'Offres bientôt clôturées', value: stats.data.jobsClosingSoon == null ? '—' : formatInt(stats.data.jobsClosingSoon) },
  ];

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">{company.name}</h1>
          <p className="page-head__text">Vos chiffres clés et les candidatures à regarder en premier.</p>
        </div>
        <div className="detail-actions">
          <ButtonLink to="/recruiter/jobs" variant="ghost">
            Mes offres
          </ButtonLink>
          <ButtonLink to="/recruiter/jobs/new">Publier une offre</ButtonLink>
        </div>
      </header>

      <div className="toolbar panel">
        <PeriodFilter value={days} onChange={setDays} />
      </div>

      {stats.error ? (
        <ErrorState error={stats.error} onRetry={stats.reload} />
      ) : (
        <div className="tiles">
          {(tiles ?? Array.from({ length: 4 }, () => null)).map((t, i) => (
            <div className="panel tile" key={t?.label ?? i}>
              {t ? (
                <>
                  <p className="tile__label">{t.label}</p>
                  <p className="tile__value">{t.value}</p>
                </>
              ) : (
                <>
                  <Skeleton width="50%" />
                  <Skeleton width="40%" height={36} style={{ marginTop: 12 }} />
                </>
              )}
            </div>
          ))}
        </div>
      )}

      {pace && (
        <div className="tiles tiles--three" style={{ marginTop: 'var(--space-3)' }}>
          {pace.map((t) => (
            <div className="panel tile" key={t.label}>
              <p className="tile__label">{t.label}</p>
              <p className="tile__value tile__value--sm">{t.value}</p>
            </div>
          ))}
        </div>
      )}

      {stats.data?.applicationsByStatus && (
        <div style={{ marginTop: 'var(--space-5)' }}>
          <Funnel byStatus={stats.data.applicationsByStatus} />
        </div>
      )}

      <div className="dash-columns">
        <section className="stack" aria-labelledby="agenda-title">
          <h2 className="section-title" id="agenda-title">
            Prochains entretiens
          </h2>
          {agenda.error ? (
            <ErrorState error={agenda.error} onRetry={agenda.reload} />
          ) : agenda.loading ? (
            <div className="panel">
              <SkeletonRows rows={2} />
            </div>
          ) : agenda.data?.length ? (
            <ul className="agenda panel panel--flush">
              {agenda.data.map(({ interview, jobTitle, candidate }) => (
                <li key={interview.applicationId} className="agenda__item">
                  <span className="agenda__when">{formatDateTimeLong(interview.scheduledAt)}</span>
                  <span className="agenda__what">
                    <span className="cell-strong">{candidate ? fullName(candidate) : 'Profil indisponible'}</span>
                    <span className="muted"> · {jobTitle ?? 'Offre'}</span>
                    <span className="muted">
                      {' '}
                      · {INTERVIEW_MODE_LABEL[interview.mode]}, {formatDuration(interview.durationMinutes)}
                    </span>
                  </span>
                  <Link className="text-link" to={`/recruiter/jobs/${interview.jobId}/candidates?id=${interview.applicationId}`}>
                    Préparer
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState title="Aucun entretien planifié">
              Proposez un entretien depuis le classement d’une offre : il apparaîtra ici et dans l’espace du candidat.
            </EmptyState>
          )}
        </section>

        <section className="stack" aria-labelledby="closing-title">
          <h2 className="section-title" id="closing-title">
            Clôture dans les 7 jours
          </h2>
          {closing.error ? (
            <ErrorState error={closing.error} onRetry={closing.reload} />
          ) : closing.loading ? (
            <div className="panel">
              <SkeletonRows rows={2} />
            </div>
          ) : closing.data?.length ? (
            <ul className="agenda panel panel--flush">
              {closing.data.map((job) => {
                const deadline = deadlineLabel(job.applicationDeadline);
                return (
                  <li key={job.id} className="agenda__item">
                    <span className="agenda__what">
                      <span className="cell-strong">{job.title}</span>
                      <span className="muted"> · {pluralize(job.applicationsCount ?? 0, 'candidature')}</span>
                    </span>
                    {deadline && <Tag tone={deadline.tone}>{deadline.label}</Tag>}
                    <Link className="text-link" to={`/recruiter/jobs/${job.id}/edit`}>
                      Prolonger
                    </Link>
                  </li>
                );
              })}
            </ul>
          ) : (
            <EmptyState title="Aucune offre ne se termine bientôt">
              Les offres dont la date limite approche apparaîtront ici, pour la prolonger à temps.
            </EmptyState>
          )}
        </section>
      </div>

      <section className="stack" aria-labelledby="top-title" style={{ marginTop: 'var(--space-7, var(--space-6))' }}>
        <h2 className="section-title" id="top-title">
          Candidatures les mieux classées
        </h2>
        {top.error ? (
          <ErrorState error={top.error} onRetry={top.reload} />
        ) : top.loading ? (
          <div className="panel">
            <SkeletonRows rows={3} />
          </div>
        ) : top.data?.length ? (
          <div className="panel panel--flush">
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Candidat·e</th>
                    <th>Offre</th>
                    <th>Score</th>
                    <th>Statut</th>
                    <th>
                      <span className="visually-hidden">Action</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {top.data.map(({ application, jobId, jobTitle, candidate }) => (
                    <tr key={application.id}>
                      <td className="cell-strong">{candidate ? fullName(candidate) : 'Profil indisponible'}</td>
                      <td>{jobTitle}</td>
                      <td>
                        <ScoreChip score={application.matchScore?.score} />
                      </td>
                      <td>
                        <ApplicationStatusTag status={application.status} />
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <Link className="text-link" to={`/recruiter/jobs/${jobId}/candidates?id=${application.id}`}>
                          Examiner
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ) : (
          <EmptyState title="Aucune candidature classée pour l’instant">
            Dès qu’un candidat postule, son score apparaît ici.
          </EmptyState>
        )}
      </section>
    </div>
  );
}
