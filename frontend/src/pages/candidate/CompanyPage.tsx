import { Link, useParams, useSearchParams } from 'react-router-dom';
import { companiesApi } from '../../api/companies';
import { jobsApi } from '../../api/jobs';
import { JobMeta } from '../../components/domain/JobMeta';
import { SaveJobButton } from '../../components/domain/SaveJobButton';
import { ButtonLink } from '../../components/ui/Button';
import { Pager } from '../../components/ui/Pager';
import { EmptyState, ErrorState, PageSkeleton, SkeletonRows } from '../../components/ui/States';
import { Tag } from '../../components/ui/Tag';
import { deadlineLabel, formatDateShort, initials, pluralize } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';

const PAGE_SIZE = 10;

/** Page publique d'une entreprise (EXT-FE) : sa présentation et ses offres publiées, paginées. */
export function CompanyPage() {
  const { companyId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? 0);
  const company = useAsync(() => companiesApi.get(companyId), [companyId]);
  const jobs = useAsync(() => jobsApi.search({ companyId, page, size: PAGE_SIZE }), [companyId, page]);
  useDocumentTitle(company.data?.name ?? 'Entreprise');

  if (company.error) {
    return (
      <div className="page page--doc">
        {company.error.status === 404 ? (
          <EmptyState title="Entreprise introuvable" action={<ButtonLink to="/candidate/jobs">Retour aux offres</ButtonLink>}>
            Cette entreprise n’existe pas ou n’est plus présente sur SmartRecruit.
          </EmptyState>
        ) : (
          <ErrorState error={company.error} onRetry={company.reload} />
        )}
      </div>
    );
  }
  if (company.loading || !company.data) return <PageSkeleton />;

  const { name, sector, description, logoUrl } = company.data;
  const total = jobs.data?.totalElements ?? 0;

  return (
    <div className="page">
      <nav className="crumbs" aria-label="Fil d’Ariane">
        <Link to="/candidate/jobs">Offres</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{name}</span>
      </nav>

      <header className="page-head company-head">
        <div className="company-head__id">
          {logoUrl ? (
            <img className="company-head__logo" src={logoUrl} alt="" width={56} height={56} />
          ) : (
            <span className="company-head__logo company-head__logo--initials" aria-hidden="true">
              {initials(name)}
            </span>
          )}
          <div>
            <h1 className="page-head__title">{name}</h1>
            <p className="page-head__text">{sector}</p>
          </div>
        </div>
        {jobs.data && <p className="muted">{pluralize(total, 'offre publiée', 'offres publiées')}</p>}
      </header>

      {description && (
        <section className="panel company-intro" aria-labelledby="about-title" style={{ marginBottom: 'var(--space-6)' }}>
          <h2 className="panel__title" id="about-title">
            À propos
          </h2>
          <p className="company-intro__text">{description}</p>
        </section>
      )}

      <section className="stack" aria-labelledby="company-jobs-title">
        <h2 className="section-title" id="company-jobs-title">
          Offres de {name}
        </h2>
        {jobs.error ? (
          <ErrorState error={jobs.error} onRetry={jobs.reload} />
        ) : !jobs.data ? (
          <div className="panel">
            <SkeletonRows rows={3} />
          </div>
        ) : jobs.data.content.length === 0 ? (
          <EmptyState title="Aucune offre publiée pour l’instant" action={<ButtonLink to="/candidate/jobs">Voir les autres offres</ButtonLink>}>
            Revenez plus tard : les nouvelles offres de {name} apparaîtront ici.
          </EmptyState>
        ) : (
          <div aria-busy={jobs.loading} style={{ opacity: jobs.loading ? 0.6 : 1 }}>
            <ul className="job-list panel panel--flush">
              {jobs.data.content.map((job) => {
                const deadline = deadlineLabel(job.applicationDeadline);
                return (
                  <li key={job.id} className="job-list__item">
                    <Link to={`/candidate/jobs/${job.id}`} className="job-row">
                      <span className="job-row__main">
                        <span className="job-row__title">{job.title}</span>
                        <JobMeta
                          companyName={job.companyName ?? name}
                          location={job.location}
                          contractType={job.contractType}
                          experienceLevel={job.experienceLevel}
                          workMode={job.workMode}
                          salaryMin={job.salaryMin}
                          salaryMax={job.salaryMax}
                        />
                      </span>
                      <span className="job-row__side">
                        <span className="muted">Publiée le {formatDateShort(job.publishedAt)}</span>
                        {deadline && deadline.tone !== 'grey' && <Tag tone={deadline.tone}>{deadline.label}</Tag>}
                        <span className="job-row__cta">Voir l’offre</span>
                      </span>
                    </Link>
                    {job.saved !== null && job.saved !== undefined && (
                      <SaveJobButton jobId={job.id} saved={job.saved} title={job.title} compact />
                    )}
                  </li>
                );
              })}
            </ul>
            <Pager
              page={page}
              totalPages={jobs.data.totalPages}
              onPage={(next) => setParams(next ? { page: String(next) } : {})}
              label="Pagination des offres"
            />
          </div>
        )}
      </section>
    </div>
  );
}
