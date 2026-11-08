import { Link } from 'react-router-dom';
import { applicationsApi } from '../../api/applications';
import { candidatesApi } from '../../api/candidates';
import { jobsApi } from '../../api/jobs';
import { JobMeta } from '../../components/domain/JobMeta';
import { ButtonLink } from '../../components/ui/Button';
import { ScoreChip } from '../../components/ui/ScoreBreakdown';
import { Alert, EmptyState, ErrorState, Skeleton, SkeletonRows } from '../../components/ui/States';
import { toUiError } from '../../lib/errors';
import { formatInt, formatScore } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { Application } from '../../types';

const ACTIVE: Application['status'][] = ['RECEIVED', 'IN_REVIEW', 'INTERVIEW'];
const RECOMMENDED_SHOWN = 3;

/** FE-DASH-01 : où j'en suis (candidatures actives, dernier score) et ce que je devrais regarder ensuite. */
export function CandidateDashboardPage() {
  useDocumentTitle('Tableau de bord');
  const applications = useAsync(() => applicationsApi.mine(), []);
  const recommendations = useAsync(() => jobsApi.recommendations(RECOMMENDED_SHOWN), []);
  const profile = useAsync(
    () => candidatesApi.getMyProfile().catch((e) => (toUiError(e).status === 404 ? null : Promise.reject(e))),
    [],
  );

  const list = applications.data ?? [];
  const active = list.filter((a) => ACTIVE.includes(a.status)).length;
  // `mine` is newest first, so the first application with a score is the latest one.
  const lastScore = list.find((a) => a.matchScore)?.matchScore?.score;
  const needsCv = !profile.loading && (!profile.data || profile.data.cvAnalysisStatus === 'NONE');

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Tableau de bord</h1>
          <p className="page-head__text">Vos candidatures, votre dernier score et les offres faites pour vous.</p>
        </div>
      </header>

      {needsCv && (
        <div style={{ marginBottom: 'var(--space-5)' }}>
          <Alert
            tone="info"
            title={profile.data ? 'Déposez votre CV' : 'Créez votre profil'}
            action={<ButtonLink to="/candidate/profile">Ouvrir mon profil</ButtonLink>}
          >
            Sans profil complet et sans CV analysé, vous ne pouvez ni voir de score ni postuler.
          </Alert>
        </div>
      )}

      {applications.error ? (
        <ErrorState error={applications.error} onRetry={applications.reload} />
      ) : (
        <div className="tiles tiles--three">
          <div className="panel tile">
            <p className="tile__label">Candidatures actives</p>
            <p className="tile__value">{applications.loading ? <Skeleton width={48} height={32} /> : formatInt(active)}</p>
            <Link className="text-link" to="/candidate/applications">
              Voir mes candidatures
            </Link>
          </div>
          <div className="panel tile">
            <p className="tile__label">Dernier score</p>
            <p className="tile__value">
              {applications.loading ? (
                <Skeleton width={48} height={32} />
              ) : lastScore === undefined ? (
                '—'
              ) : (
                <>
                  {formatScore(lastScore)}
                  <span className="tile__unit"> / 100</span>
                </>
              )}
            </p>
          </div>
          <div className="panel tile">
            <p className="tile__label">Offres recommandées</p>
            <p className="tile__value">
              {recommendations.loading ? <Skeleton width={48} height={32} /> : formatInt(recommendations.data?.length ?? 0)}
            </p>
            <Link className="text-link" to="/candidate/recommendations">
              Voir les recommandations
            </Link>
          </div>
        </div>
      )}

      <section className="stack" aria-labelledby="reco-title" style={{ marginTop: 'var(--space-7, var(--space-6))' }}>
        <h2 className="section-title" id="reco-title">
          Pour vous
        </h2>
        {recommendations.error ? (
          <ErrorState error={recommendations.error} onRetry={recommendations.reload} />
        ) : recommendations.loading ? (
          <div className="panel">
            <SkeletonRows rows={3} />
          </div>
        ) : recommendations.data?.length ? (
          <ol className="reco">
            {recommendations.data.map((job) => (
              <li key={job.jobId} className="reco__item">
                <Link to={`/candidate/jobs/${job.jobId}`} className="reco__link">
                  <span className="reco__score">
                    <ScoreChip score={job.score} />
                  </span>
                  <span className="reco__title">{job.title}</span>
                  <JobMeta
                    companyName={job.companyName}
                    location={job.location}
                    contractType={job.contractType}
                    experienceLevel={job.experienceLevel}
                  />
                </Link>
              </li>
            ))}
          </ol>
        ) : (
          <EmptyState title="Pas encore de recommandations" action={<ButtonLink to="/candidate/jobs">Parcourir les offres</ButtonLink>}>
            Elles apparaissent dès que votre profil et votre CV sont analysés.
          </EmptyState>
        )}
      </section>
    </div>
  );
}
