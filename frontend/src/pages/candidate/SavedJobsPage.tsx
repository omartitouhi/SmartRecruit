import { Link } from 'react-router-dom';
import { jobsApi } from '../../api/jobs';
import { JobMeta } from '../../components/domain/JobMeta';
import { SaveJobButton } from '../../components/domain/SaveJobButton';
import { ButtonLink } from '../../components/ui/Button';
import { EmptyState, ErrorState, SkeletonRows } from '../../components/ui/States';
import { JobStatusTag, Tag } from '../../components/ui/Tag';
import { deadlineLabel, pluralize } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';

/** EXT-SAVED-01 : les offres mises de côté, encore publiées ou clôturées depuis. */
export function SavedJobsPage() {
  useDocumentTitle('Mes favoris');
  const saved = useAsync(() => jobsApi.saved(), []);
  const list = saved.data ?? [];

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Mes favoris</h1>
          <p className="page-head__text">
            Les offres que vous avez mises de côté. Une offre clôturée reste ici pour mémoire, sans pouvoir y postuler.
          </p>
        </div>
        {saved.data && list.length > 0 && <p className="muted">{pluralize(list.length, 'offre enregistrée', 'offres enregistrées')}</p>}
      </header>

      {saved.error ? (
        <ErrorState error={saved.error} onRetry={saved.reload} />
      ) : saved.loading && !saved.data ? (
        <div className="panel">
          <SkeletonRows rows={3} />
        </div>
      ) : list.length === 0 ? (
        <EmptyState title="Aucune offre enregistrée" action={<ButtonLink to="/candidate/jobs">Parcourir les offres</ButtonLink>}>
          Touchez l’étoile d’une offre pour la retrouver ici, et comparer avant de postuler.
        </EmptyState>
      ) : (
        <ul className="job-list panel panel--flush">
          {list.map((job) => {
            const open = job.status === 'PUBLISHED';
            const deadline = open ? deadlineLabel(job.applicationDeadline) : null;
            const body = (
              <>
                <span className="job-row__main">
                  <span className="job-row__title">{job.title}</span>
                  <JobMeta
                    companyName={job.companyName}
                    location={job.location}
                    contractType={job.contractType}
                    experienceLevel={job.experienceLevel}
                    workMode={job.workMode}
                    salaryMin={job.salaryMin}
                    salaryMax={job.salaryMax}
                  />
                </span>
                <span className="job-row__side">
                  {open ? (
                    <>
                      {deadline && <Tag tone={deadline.tone}>{deadline.label}</Tag>}
                      <span className="job-row__cta">Voir l’offre</span>
                    </>
                  ) : (
                    <JobStatusTag status={job.status} />
                  )}
                </span>
              </>
            );
            return (
              <li key={job.id} className="job-list__item">
                {open ? (
                  <Link to={`/candidate/jobs/${job.id}`} className="job-row">
                    {body}
                  </Link>
                ) : (
                  <div className="job-row job-row--muted">{body}</div>
                )}
                <SaveJobButton
                  jobId={job.id}
                  saved
                  title={job.title}
                  compact
                  onChange={(isSaved) => {
                    if (!isSaved) saved.setData((prev) => (prev ?? []).filter((j) => j.id !== job.id));
                  }}
                />
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
