import { type CSSProperties } from 'react';
import { Link, useParams } from 'react-router-dom';
import { companiesApi } from '../../api/companies';
import { jobsApi } from '../../api/jobs';
import { ApplyButton } from '../../components/domain/ApplyButton';
import { ButtonLink } from '../../components/ui/Button';
import { EmptyState, ErrorState, PageSkeleton } from '../../components/ui/States';
import { Tag } from '../../components/ui/Tag';
import { CONTRACT_LABEL, formatDate, LEVEL_LABEL } from '../../lib/format';
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
  return (
    <article className="page page--doc">
      <nav className="crumbs" aria-label="Fil d’Ariane">
        <Link to="/candidate/jobs">Offres</Link>
        <span aria-hidden="true">/</span>
        <span>{offer.companyName ?? 'Entreprise'}</span>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{offer.title}</span>
      </nav>

      <h1 className="page-head__title reveal">{offer.title}</h1>

      <dl className="props reveal" style={{ margin: '28px 0', '--i': 1 } as CSSProperties}>
        <dt>Entreprise</dt>
        <dd>{offer.companyName ?? '—'}</dd>
        <dt>Lieu</dt>
        <dd>{offer.location}</dd>
        <dt>Contrat</dt>
        <dd>
          <Tag tone="blue">{CONTRACT_LABEL[offer.contractType]}</Tag>
        </dd>
        <dt>Niveau</dt>
        <dd>{LEVEL_LABEL[offer.experienceLevel]}</dd>
        <dt>Publiée le</dt>
        <dd>{formatDate(offer.publishedAt)}</dd>
      </dl>

      <div className="detail-actions" style={{ marginBottom: 'var(--space-6)' }}>
        <ApplyButton jobId={offer.id} />
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
        </section>
      )}
    </article>
  );
}
