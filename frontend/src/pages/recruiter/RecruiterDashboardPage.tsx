import { Link } from 'react-router-dom';
import { applicationsApi } from '../../api/applications';
import { candidatesApi, fullName } from '../../api/candidates';
import { dashboardApi } from '../../api/dashboard';
import { jobsApi } from '../../api/jobs';
import { NoCompany } from '../../components/domain/NoCompany';
import { ButtonLink } from '../../components/ui/Button';
import { ScoreChip } from '../../components/ui/ScoreBreakdown';
import { EmptyState, ErrorState, PageSkeleton, Skeleton, SkeletonRows } from '../../components/ui/States';
import { ApplicationStatusTag } from '../../components/ui/Tag';
import { formatInt, formatRate } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import { useMyCompany } from '../../lib/useCompany';
import type { Application, CandidateView, Company } from '../../types';

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
  const stats = useAsync(() => dashboardApi.companyStats(company.id), [company.id]);
  const top = useAsync(loadTop, [company.id]);

  const tiles = stats.data && [
    { label: 'Offres publiées', value: formatInt(stats.data.publishedJobs) },
    { label: 'Candidatures reçues', value: formatInt(stats.data.applicationsReceived) },
    { label: 'Candidatures acceptées', value: formatInt(stats.data.acceptedApplications) },
    { label: 'Taux d’acceptation', value: stats.data.acceptanceRate === null ? '—' : formatRate(stats.data.acceptanceRate) },
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
