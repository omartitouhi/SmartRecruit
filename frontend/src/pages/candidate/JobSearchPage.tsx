import { useState, type CSSProperties, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { jobsApi } from '../../api/jobs';
import { JobMeta } from '../../components/domain/JobMeta';
import { Button, ButtonLink } from '../../components/ui/Button';
import { SelectField, TextField } from '../../components/ui/Field';
import { ScoreChip } from '../../components/ui/ScoreBreakdown';
import { EmptyState, ErrorState, SkeletonRows } from '../../components/ui/States';
import { CONTRACT_LABEL, formatDateShort, LEVEL_LABEL, pluralize } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { ContractType, ExperienceLevel } from '../../types';

const PAGE_SIZE = 10;

export function JobSearchPage() {
  useDocumentTitle('Offres');
  const [params, setParams] = useSearchParams();
  const filters = {
    q: params.get('q') ?? '',
    location: params.get('location') ?? '',
    contractType: (params.get('contractType') ?? '') as ContractType | '',
    experienceLevel: (params.get('experienceLevel') ?? '') as ExperienceLevel | '',
  };
  const page = Number(params.get('page') ?? 0);
  const [draft, setDraft] = useState(filters);

  // Keep the form in sync when the URL changes (back/forward), adjusting state during render.
  const key = params.toString();
  const [draftKey, setDraftKey] = useState(key);
  if (draftKey !== key) {
    setDraftKey(key);
    setDraft(filters);
  }

  const recommendations = useAsync(() => jobsApi.recommendations(5), []);
  const results = useAsync(() => jobsApi.search({ ...filters, page, size: PAGE_SIZE }), [key]);
  const hasFilters = Object.values(filters).some(Boolean);

  function apply(next: typeof draft, nextPage = 0) {
    const p = new URLSearchParams();
    Object.entries(next).forEach(([k, v]) => v && p.set(k, v));
    if (nextPage) p.set('page', String(nextPage));
    setParams(p);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    apply(draft);
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Offres</h1>
          <p className="page-head__text">
            Chaque offre affiche votre score de compatibilité. Ouvrez-la pour lire ce qui le compose.
          </p>
        </div>
      </header>

      <section className="stack" aria-labelledby="reco-title">
        <h2 className="section-title" id="reco-title">
          Recommandées pour vous
        </h2>
        {recommendations.error ? (
          <ErrorState error={recommendations.error} onRetry={recommendations.reload} />
        ) : recommendations.loading ? (
          <div className="panel">
            <SkeletonRows rows={3} />
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
      </section>

      <section className="stack" aria-labelledby="all-title" style={{ marginTop: 'var(--space-8)' }}>
        <h2 className="section-title" id="all-title">
          Toutes les offres
        </h2>
        <form className="toolbar panel" onSubmit={onSubmit} role="search">
          <TextField
            label="Recherche"
            placeholder="Intitulé, compétence, entreprise"
            value={draft.q}
            onChange={(e) => setDraft({ ...draft, q: e.target.value })}
          />
          <TextField
            label="Ville"
            placeholder="Tunis, Sfax…"
            value={draft.location}
            onChange={(e) => setDraft({ ...draft, location: e.target.value })}
          />
          <SelectField
            label="Contrat"
            placeholder="Tous"
            value={draft.contractType}
            onChange={(e) => setDraft({ ...draft, contractType: e.target.value as ContractType | '' })}
            options={Object.entries(CONTRACT_LABEL).map(([value, label]) => ({ value, label }))}
          />
          <SelectField
            label="Niveau"
            placeholder="Tous"
            value={draft.experienceLevel}
            onChange={(e) => setDraft({ ...draft, experienceLevel: e.target.value as ExperienceLevel | '' })}
            options={Object.entries(LEVEL_LABEL).map(([value, label]) => ({ value, label }))}
          />
          <Button type="submit">Rechercher</Button>
        </form>

        {results.error ? (
          <ErrorState error={results.error} onRetry={results.reload} />
        ) : results.loading || !results.data ? (
          <div className="panel">
            <SkeletonRows rows={5} />
          </div>
        ) : results.data.content.length === 0 ? (
          <EmptyState
            title="Aucune offre ne correspond"
            action={
              hasFilters && (
                <Button variant="ghost" onClick={() => apply({ q: '', location: '', contractType: '', experienceLevel: '' })}>
                  Effacer les filtres
                </Button>
              )
            }
          >
            Essayez une autre ville ou retirez un filtre.
          </EmptyState>
        ) : (
          <>
            <p className="muted" aria-live="polite">
              {pluralize(results.data.totalElements, 'offre')}
            </p>
            <ul className="job-list panel panel--flush">
              {results.data.content.map((job) => (
                <li key={job.id}>
                  <Link to={`/candidate/match/${job.id}`} className="job-row">
                    <span className="job-row__main">
                      <span className="job-row__title">{job.title}</span>
                      <JobMeta
                        companyName={job.companyName}
                        location={job.location}
                        contractType={job.contractType}
                        experienceLevel={job.experienceLevel}
                      />
                    </span>
                    <span className="job-row__side">
                      <span className="muted">Publiée le {formatDateShort(job.publishedAt)}</span>
                      <span className="job-row__cta">Voir mon score</span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
            {results.data.totalPages > 1 && (
              <nav className="pager" aria-label="Pagination">
                <Button variant="ghost" disabled={page === 0} onClick={() => apply(filters, page - 1)}>
                  Précédent
                </Button>
                <span className="muted">
                  Page {page + 1} sur {results.data.totalPages}
                </span>
                <Button variant="ghost" disabled={page + 1 >= results.data.totalPages} onClick={() => apply(filters, page + 1)}>
                  Suivant
                </Button>
              </nav>
            )}
          </>
        )}
      </section>
    </div>
  );
}
