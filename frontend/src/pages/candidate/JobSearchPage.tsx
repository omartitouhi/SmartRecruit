import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { jobsApi } from '../../api/jobs';
import { JobMeta } from '../../components/domain/JobMeta';
import { SaveJobButton } from '../../components/domain/SaveJobButton';
import { Button } from '../../components/ui/Button';
import { SelectField, TextField } from '../../components/ui/Field';
import { EmptyState, ErrorState, SkeletonRows } from '../../components/ui/States';
import { Tag } from '../../components/ui/Tag';
import { CONTRACT_LABEL, deadlineLabel, formatDateShort, LEVEL_LABEL, pluralize, WORK_MODE_LABEL } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { ContractType, ExperienceLevel, WorkMode } from '../../types';

const PAGE_SIZE = 20;
/** Pause after the last keystroke before the list is refreshed. */
export const SEARCH_DEBOUNCE_MS = 350;

interface Filters {
  keyword: string;
  location: string;
  skills: string;
  contractType: ContractType | '';
  experienceLevel: ExperienceLevel | '';
  workMode: WorkMode | '';
  /** Salaire mensuel minimum en TND, saisi comme texte (vide = pas de filtre). */
  minSalary: string;
}

const NO_FILTERS: Filters = {
  keyword: '',
  location: '',
  skills: '',
  contractType: '',
  experienceLevel: '',
  workMode: '',
  minSalary: '',
};

/** A positive whole number of dinars, or no filter. */
const salaryFilter = (text: string): number | '' => {
  const n = Number(text.trim());
  return text.trim() && Number.isInteger(n) && n >= 0 ? n : '';
};
const splitSkills = (skills: string) => skills.split(',').map((s) => s.trim()).filter(Boolean);

export function JobSearchPage() {
  useDocumentTitle('Offres');
  const [params, setParams] = useSearchParams();
  const filters: Filters = {
    keyword: params.get('keyword') ?? '',
    location: params.get('location') ?? '',
    skills: params.get('skills') ?? '',
    contractType: (params.get('contractType') ?? '') as ContractType | '',
    experienceLevel: (params.get('experienceLevel') ?? '') as ExperienceLevel | '',
    workMode: (params.get('workMode') ?? '') as WorkMode | '',
    minSalary: params.get('minSalary') ?? '',
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

  const results = useAsync(
    () =>
      jobsApi.search({
        ...filters,
        skills: splitSkills(filters.skills),
        minSalary: salaryFilter(filters.minSalary),
        page,
        size: PAGE_SIZE,
      }),
    [key],
  );
  const hasFilters = Object.values(filters).some(Boolean);

  function apply(next: Filters, nextPage = 0) {
    const p = new URLSearchParams();
    Object.entries(next).forEach(([k, v]) => v && p.set(k, v));
    if (nextPage) p.set('page', String(nextPage));
    setParams(p);
  }

  // Typing refreshes the list after a short pause; the URL keeps the search shareable.
  const draftJson = JSON.stringify(draft);
  useEffect(() => {
    if (draftJson === JSON.stringify(filters)) return;
    const timer = setTimeout(() => apply(draft), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draftJson]);

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
            Parcourez les offres publiées. Ouvrez-en une pour lire le détail, voir votre score et postuler.
          </p>
        </div>
        <Link className="text-link" to="/candidate/recommendations">
          Voir mes recommandations
        </Link>
      </header>

      <section className="stack" aria-labelledby="all-title">
        <h2 className="section-title visually-hidden" id="all-title">
          Toutes les offres
        </h2>
        <form className="toolbar panel" onSubmit={onSubmit} role="search" aria-label="Filtrer les offres">
          <TextField
            label="Recherche"
            placeholder="Intitulé ou description"
            value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
          />
          <TextField
            label="Ville"
            placeholder="Tunis, Sfax…"
            value={draft.location}
            onChange={(e) => setDraft({ ...draft, location: e.target.value })}
          />
          <TextField
            label="Compétences"
            placeholder="Java, React"
            hint="Séparées par des virgules : une seule suffit."
            value={draft.skills}
            onChange={(e) => setDraft({ ...draft, skills: e.target.value })}
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
          <SelectField
            label="Mode de travail"
            placeholder="Tous"
            value={draft.workMode}
            onChange={(e) => setDraft({ ...draft, workMode: e.target.value as WorkMode | '' })}
            options={Object.entries(WORK_MODE_LABEL).map(([value, label]) => ({ value, label }))}
          />
          <TextField
            label="Salaire minimum"
            type="number"
            inputMode="numeric"
            min={0}
            step={100}
            placeholder="TND / mois"
            hint="Brut mensuel ; écarte les offres sans salaire."
            value={draft.minSalary}
            onChange={(e) => setDraft({ ...draft, minSalary: e.target.value })}
          />
        </form>

        {results.error ? (
          <ErrorState error={results.error} onRetry={results.reload} />
        ) : !results.data ? (
          <div className="panel">
            <SkeletonRows rows={5} />
          </div>
        ) : results.data.content.length === 0 ? (
          <EmptyState
            title="Aucune offre ne correspond"
            action={
              hasFilters && (
                <Button
                  variant="ghost"
                  onClick={() => {
                    setDraft(NO_FILTERS);
                    apply(NO_FILTERS);
                  }}
                >
                  Effacer les filtres
                </Button>
              )
            }
          >
            Essayez une autre ville ou retirez un filtre.
          </EmptyState>
        ) : (
          <div aria-busy={results.loading} style={{ opacity: results.loading ? 0.6 : 1 }}>
            <p className="muted" aria-live="polite">
              {pluralize(results.data.totalElements, 'offre')}
            </p>
            <ul className="job-list panel panel--flush">
              {results.data.content.map((job) => {
                const deadline = deadlineLabel(job.applicationDeadline);
                return (
                  <li key={job.id} className="job-list__item">
                    <Link to={`/candidate/jobs/${job.id}`} className="job-row">
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
          </div>
        )}
      </section>
    </div>
  );
}
