import { type CSSProperties } from 'react';
import { Link, useParams } from 'react-router-dom';
import { companiesApi } from '../../api/companies';
import { jobsApi } from '../../api/jobs';
import { ApplyButton } from '../../components/domain/ApplyButton';
import { SaveJobButton } from '../../components/domain/SaveJobButton';
import { ButtonLink } from '../../components/ui/Button';
import { EmptyState, ErrorState, PageSkeleton } from '../../components/ui/States';
import { Tag } from '../../components/ui/Tag';
import {
  CONTRACT_LABEL,
  deadlineLabel,
  formatDate,
  formatDay,
  formatSalary,
  LEVEL_LABEL,
  pluralize,
  WORK_MODE_LABEL,
} from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';

/** Fiche d'une offre : tout ce que le candidat doit savoir avant de postuler. */
export function JobDetailPage() {
  const { jobId = '' } = useParams();
  const job = useAsync(() => jobsApi.get(jobId), [jobId]);
  const companyId = job.data?.companyId;
  // The company presentation is a bonus: the page stays usable if user-service does not answer.
  const company = useAsync(
    () => (companyId ? companiesApi.get(companyId).catch(() => null) : Promise.resolve(null)),
    [companyId],
  );
  useDocumentTitle(job.data?.title ?? 'Offre');

  if (job.error) {
    return (
      <div className="page page--doc">
        {job.error.status === 404 ? (
          <EmptyState title="Offre introuvable" action={<ButtonLink to="/candidate/jobs">Retour aux offres</ButtonLink>}>
            Cette offre a peut-être été clôturée ou retirée par l’entreprise.
          </EmptyState>
        ) : (
          <ErrorState error={job.error} onRetry={job.reload} />
        )}
      </div>
    );
  }
  if (job.loading || !job.data) return <PageSkeleton />;

  const offer = job.data;
  const salary = formatSalary(offer.salaryMin, offer.salaryMax);
  const deadline = deadlineLabel(offer.applicationDeadline);
  const companyLink = (
    <Link className="text-link" to={`/candidate/companies/${offer.companyId}`}>
      {offer.companyName ?? 'Voir l’entreprise'}
    </Link>
  );
  return (
    <article className="page page--doc">
      <nav className="crumbs" aria-label="Fil d’Ariane">
        <Link to="/candidate/jobs">Offres</Link>
        <span aria-hidden="true">/</span>
        <Link to={`/candidate/companies/${offer.companyId}`}>{offer.companyName ?? 'Entreprise'}</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{offer.title}</span>
      </nav>

      <h1 className="page-head__title reveal">{offer.title}</h1>

      <dl className="props reveal" style={{ margin: '28px 0', '--i': 1 } as CSSProperties}>
        <dt>Entreprise</dt>
        <dd>{companyLink}</dd>
        <dt>Lieu</dt>
        <dd>{offer.location}</dd>
        <dt>Contrat</dt>
        <dd>
          <Tag tone="blue">{CONTRACT_LABEL[offer.contractType]}</Tag>
        </dd>
        <dt>Mode de travail</dt>
        <dd>{WORK_MODE_LABEL[offer.workMode ?? 'ON_SITE']}</dd>
        <dt>Niveau</dt>
        <dd>{LEVEL_LABEL[offer.experienceLevel]}</dd>
        <dt>Salaire</dt>
        <dd>{salary ?? <span className="muted">Non communiqué</span>}</dd>
        {(offer.positions ?? 1) > 1 && (
          <>
            <dt>Postes</dt>
            <dd>{pluralize(offer.positions, 'poste ouvert', 'postes ouverts')}</dd>
          </>
        )}
        <dt>Publiée le</dt>
        <dd>{formatDate(offer.publishedAt)}</dd>
        {offer.applicationDeadline && deadline && (
          <>
            <dt>Candidater avant</dt>
            <dd>
              {formatDay(offer.applicationDeadline)}{' '}
              {deadline.tone !== 'grey' && <Tag tone={deadline.tone}>{deadline.label}</Tag>}
            </dd>
          </>
        )}
      </dl>

      <div className="detail-actions" style={{ marginBottom: 'var(--space-6)' }}>
        <ApplyButton jobId={offer.id} />
        {offer.saved !== null && offer.saved !== undefined && (
          <SaveJobButton jobId={offer.id} saved={offer.saved} onChange={(saved) => job.setData({ ...offer, saved })} />
        )}
        <Link className="text-link" to={`/candidate/match/${offer.id}`}>
          Voir mon score expliqué
        </Link>
      </div>

      <section className="job-description" aria-labelledby="desc-title">
        <h2 className="section-title" id="desc-title">
          Le poste
        </h2>
        <p>{offer.description}</p>
        <h3 className="job-description__sub">Compétences demandées</h3>
        <div className="tag-list">
          {offer.requiredSkills.map((s) => (
            <Tag key={s}>{s}</Tag>
          ))}
        </div>
      </section>

      {company.data && (
        <section className="company-about" aria-labelledby="company-title">
          <h2 className="section-title" id="company-title">
            À propos de {company.data.name}
          </h2>
          <p className="muted">{company.data.sector}</p>
          {company.data.description && <p>{company.data.description}</p>}
          <Link className="text-link" to={`/candidate/companies/${company.data.id}`}>
            Voir toutes les offres de {company.data.name}
          </Link>
        </section>
      )}
    </article>
  );
}
