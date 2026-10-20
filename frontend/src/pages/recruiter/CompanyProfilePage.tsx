import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { applicationsApi } from '../../api/applications';
import { companiesApi, type CompanyRequest } from '../../api/companies';
import { jobsApi, type JobSummary } from '../../api/jobs';
import { Button, ButtonLink } from '../../components/ui/Button';
import { TextAreaField, TextField } from '../../components/ui/Field';
import { Alert, EmptyState, ErrorState, PageSkeleton, SkeletonRows } from '../../components/ui/States';
import { JobStatusTag, Tag } from '../../components/ui/Tag';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { CONTRACT_LABEL, formatDateShort, LEVEL_LABEL, pluralize } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { Company, JobStatus } from '../../types';

interface JobRow {
  job: JobSummary;
  applications: number;
  toReview: number;
}

async function loadJobs(): Promise<JobRow[]> {
  const page = await jobsApi.search({ companyId: 'me', size: 50 });
  const counts = await Promise.all(
    page.content.map((job) =>
      job.status === 'DRAFT' ? Promise.resolve([]) : applicationsApi.forJob(job.id).catch(() => []),
    ),
  );
  return page.content.map((job, i) => ({
    job,
    applications: counts[i].filter((a) => a.status !== 'WITHDRAWN').length,
    toReview: counts[i].filter((a) => a.status === 'RECEIVED').length,
  }));
}

/** Prochaine action proposée sur une offre, selon son statut. */
const NEXT_STATUS: Partial<Record<JobStatus, { status: JobStatus; label: string }>> = {
  DRAFT: { status: 'PUBLISHED', label: 'Publier' },
  PUBLISHED: { status: 'CLOSED', label: 'Clôturer' },
  CLOSED: { status: 'PUBLISHED', label: 'Rouvrir' },
};

export function CompanyProfilePage() {
  useDocumentTitle('Mes offres');
  const [params] = useSearchParams();
  const company = useAsync(
    () => companiesApi.getMine().catch((e) => (toUiError(e).status === 404 ? null : Promise.reject(e))),
    [],
  );
  const [editing, setEditing] = useState(false);

  if (company.error) {
    return (
      <div className="page">
        <ErrorState error={company.error} onRetry={company.reload} />
      </div>
    );
  }
  if (company.loading) return <PageSkeleton />;

  if (!company.data || editing) {
    return (
      <div className="page page--doc">
        <header className="page-head">
          <div>
            <h1 className="page-head__title">{company.data ? 'Modifier l’entreprise' : 'Votre entreprise'}</h1>
            <p className="page-head__text">
              {company.data
                ? 'Ces informations apparaissent sur chacune de vos offres.'
                : 'Présentez votre entreprise avant de publier votre première offre. Les candidats la verront sur chaque offre.'}
            </p>
          </div>
        </header>
        {!company.data && params.get('welcome') && (
          <div style={{ marginBottom: 'var(--space-5)' }}>
            <Alert tone="info" title="Bienvenue sur SmartRecruit">
              Deux étapes : votre entreprise, puis votre première offre.
            </Alert>
          </div>
        )}
        <CompanyForm
          initial={company.data ?? null}
          onSaved={(c) => {
            company.setData(c);
            setEditing(false);
          }}
          onCancel={company.data ? () => setEditing(false) : undefined}
        />
      </div>
    );
  }

  return <CompanyJobs company={company.data} onEdit={() => setEditing(true)} />;
}

function CompanyJobs({ company, onEdit }: { company: Company; onEdit: () => void }) {
  const notify = useToast();
  const jobs = useAsync(loadJobs, [company.id]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function changeStatus(row: JobRow, status: JobStatus) {
    setBusy(row.job.id);
    setError(null);
    try {
      const updated = await jobsApi.updateStatus(row.job.id, status);
      jobs.setData((list) => (list ?? []).map((r) => (r.job.id === updated.id ? { ...r, job: { ...r.job, ...updated } } : r)));
      notify(status === 'PUBLISHED' ? 'Offre publiée.' : 'Offre clôturée.');
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setBusy(null);
    }
  }

  const rows = jobs.data ?? [];
  const open = rows.filter((r) => r.job.status === 'PUBLISHED');
  const toReview = rows.reduce((n, r) => n + r.toReview, 0);

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">{company.name}</h1>
          <p className="page-head__text">
            {company.sector} · {pluralize(open.length, 'offre ouverte', 'offres ouvertes')}
            {toReview > 0 && ` · ${pluralize(toReview, 'candidature à examiner', 'candidatures à examiner')}`}
          </p>
        </div>
        <div className="detail-actions">
          <Button variant="ghost" onClick={onEdit}>
            Modifier l’entreprise
          </Button>
          <ButtonLink to="/recruiter/jobs/new">Publier une offre</ButtonLink>
        </div>
      </header>

      {error && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <Alert tone="error">{error}</Alert>
        </div>
      )}

      {jobs.error ? (
        <ErrorState error={jobs.error} onRetry={jobs.reload} />
      ) : jobs.loading ? (
        <div className="panel">
          <SkeletonRows rows={4} />
        </div>
      ) : rows.length === 0 ? (
        <EmptyState title="Aucune offre pour l’instant" action={<ButtonLink to="/recruiter/jobs/new">Rédiger une offre</ButtonLink>}>
          Une offre part en brouillon : relisez-la, puis publiez-la quand elle est prête. Les candidatures arrivent déjà
          classées par score.
        </EmptyState>
      ) : (
        <div className="panel panel--flush">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Offre</th>
                  <th>Statut</th>
                  <th>Candidatures</th>
                  <th>Publiée le</th>
                  <th>
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const next = NEXT_STATUS[row.job.status];
                  return (
                    <tr key={row.job.id}>
                      <td>
                        <div className="cell-strong">{row.job.title}</div>
                        <div className="muted">
                          {row.job.location} · {CONTRACT_LABEL[row.job.contractType]} · {LEVEL_LABEL[row.job.experienceLevel]}
                        </div>
                      </td>
                      <td>
                        <JobStatusTag status={row.job.status} />
                      </td>
                      <td>
                        {row.job.status === 'DRAFT' ? (
                          <span className="muted">—</span>
                        ) : (
                          <Link className="text-link" to={`/recruiter/jobs/${row.job.id}/candidates`}>
                            {pluralize(row.applications, 'candidature')}
                          </Link>
                        )}
                        {row.toReview > 0 && (
                          <span style={{ marginLeft: 8 }}>
                            <Tag tone="yellow">{row.toReview} à examiner</Tag>
                          </span>
                        )}
                      </td>
                      <td className="muted">{formatDateShort(row.job.publishedAt)}</td>
                      <td style={{ textAlign: 'right' }}>
                        {next ? (
                          <Button variant="ghost" loading={busy === row.job.id} onClick={() => changeStatus(row, next.status)}>
                            {next.label}
                          </Button>
                        ) : (
                          <span className="muted">Modérée par l’administration</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <section className="company-about" aria-labelledby="about-title">
        <h2 className="section-title" id="about-title">
          À propos de {company.name}
        </h2>
        <p>{company.description || <span className="muted">Aucune présentation pour l’instant.</span>}</p>
      </section>
    </div>
  );
}

function CompanyForm({ initial, onSaved, onCancel }: {
  initial: Company | null;
  onSaved: (company: Company) => void;
  onCancel?: () => void;
}) {
  const notify = useToast();
  const [form, setForm] = useState<CompanyRequest>({
    name: initial?.name ?? '',
    sector: initial?.sector ?? '',
    description: initial?.description ?? '',
    logoUrl: initial?.logoUrl ?? null,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (!form.name.trim()) found.name = 'Le nom est obligatoire.';
    if (!form.sector.trim()) found.sector = 'Le secteur est obligatoire.';
    setErrors(found);
    if (Object.keys(found).length) return;
    setSaving(true);
    setFormError(null);
    try {
      onSaved(await (initial ? companiesApi.update(initial.id, form) : companiesApi.create(form)));
      notify('Entreprise enregistrée.');
    } catch (err) {
      const ui = toUiError(err);
      setErrors(ui.fieldErrors);
      setFormError(ui.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="panel form" onSubmit={onSubmit} noValidate>
      {formError && <Alert tone="error">{formError}</Alert>}
      <div className="form-row">
        <TextField label="Nom de l’entreprise" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} error={errors.name} />
        <TextField label="Secteur" placeholder="Transport et logistique" value={form.sector} onChange={(e) => setForm({ ...form, sector: e.target.value })} error={errors.sector} />
      </div>
      <TextAreaField
        label="Présentation"
        rows={5}
        value={form.description}
        onChange={(e) => setForm({ ...form, description: e.target.value })}
        hint="Ce que fait l’entreprise, où elle se trouve, comment l’équipe travaille."
      />
      <div className="detail-actions">
        <Button type="submit" loading={saving}>
          Enregistrer
        </Button>
        {onCancel && (
          <Button variant="ghost" onClick={onCancel}>
            Annuler
          </Button>
        )}
      </div>
    </form>
  );
}
