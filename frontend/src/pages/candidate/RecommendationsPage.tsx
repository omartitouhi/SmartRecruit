import { type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { jobsApi } from '../../api/jobs';
import { JobMeta } from '../../components/domain/JobMeta';
import { ButtonLink } from '../../components/ui/Button';
import { ScoreChip } from '../../components/ui/ScoreBreakdown';
import { EmptyState, ErrorState, SkeletonRows } from '../../components/ui/States';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';

const LIMIT = 10;

/** FE-REC-01 : les offres qui correspondent le mieux au profil, avec le score estimé. */
export function RecommendationsPage() {
  useDocumentTitle('Offres recommandées');
  const recommendations = useAsync(() => jobsApi.recommendations(LIMIT), []);

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Offres recommandées</h1>
          <p className="page-head__text">
            Classées par compatibilité estimée avec votre profil. Ouvrez une offre pour lire ce qui compose le score.
          </p>
        </div>
      </header>

      {recommendations.error ? (
        <ErrorState error={recommendations.error} onRetry={recommendations.reload} />
      ) : recommendations.loading ? (
        <div className="panel">
          <SkeletonRows rows={4} />
        </div>
      ) : recommendations.data?.length ? (
        <ol className="reco">
          {recommendations.data.map((job, i) => (
            <li key={job.jobId} className="reco__item reveal" style={{ '--i': i } as CSSProperties}>
              <Link to={`/candidate/match/${job.jobId}`} className="reco__link">
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
        <EmptyState
          title="Pas encore de recommandations"
          action={<ButtonLink to="/candidate/profile">Compléter mon profil</ButtonLink>}
        >
          Les recommandations se calculent à partir de votre profil et de votre CV. Renseignez vos compétences et
          déposez votre CV pour les voir apparaître.
        </EmptyState>
      )}
    </div>
  );
}
