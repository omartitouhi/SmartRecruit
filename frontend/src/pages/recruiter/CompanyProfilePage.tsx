import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { companiesApi, type CompanyRequest } from '../../api/companies';
import { Button, ButtonLink } from '../../components/ui/Button';
import { TextAreaField, TextField } from '../../components/ui/Field';
import { Alert, ErrorState, PageSkeleton } from '../../components/ui/States';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { useDocumentTitle } from '../../lib/useAsync';
import { useMyCompany } from '../../lib/useCompany';
import type { Company } from '../../types';

/** FE-COMP-01 : créer l'entreprise (puis retour au tableau de bord) ou modifier celle qui existe, champs pré-remplis. */
export function CompanyProfilePage() {
  useDocumentTitle('Mon entreprise');
  const [params] = useSearchParams();
  const company = useMyCompany();

  if (company.error) {
    return (
      <div className="page page--doc">
        <ErrorState error={company.error} onRetry={company.reload} />
      </div>
    );
  }
  if (company.loading) return <PageSkeleton />;

  const existing = company.data ?? null;
  return (
    <div className="page page--doc">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">{existing ? 'Mon entreprise' : 'Votre entreprise'}</h1>
          <p className="page-head__text">
            {existing
              ? 'Ces informations apparaissent sur chacune de vos offres.'
              : 'Présentez votre entreprise avant de publier votre première offre. Les candidats la verront sur chaque offre.'}
          </p>
        </div>
        {existing && (
          <ButtonLink to="/recruiter/team" variant="ghost">
            Gérer l’équipe
          </ButtonLink>
        )}
      </header>
      {!existing && params.get('welcome') && (
        <div style={{ marginBottom: 'var(--space-5)' }}>
          <Alert tone="info" title="Bienvenue sur SmartRecruit">
            Deux étapes : votre entreprise, puis votre première offre.
          </Alert>
        </div>
      )}
      <CompanyForm initial={existing} onSaved={company.setData} />
    </div>
  );
}

function CompanyForm({ initial, onSaved }: { initial: Company | null; onSaved: (company: Company) => void }) {
  const notify = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState<CompanyRequest>({
    name: initial?.name ?? '',
    sector: initial?.sector ?? '',
    description: initial?.description ?? '',
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
      const saved = await (initial ? companiesApi.update(initial.id, form) : companiesApi.create(form));
      onSaved(saved);
      notify('Entreprise enregistrée.');
      if (!initial) navigate('/recruiter');
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
        <TextField label="Nom de l’entreprise" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} error={errors.name} maxLength={150} required />
        <TextField label="Secteur" placeholder="Transport et logistique" value={form.sector} onChange={(e) => setForm({ ...form, sector: e.target.value })} error={errors.sector} maxLength={100} required />
      </div>
      <TextAreaField
        label="Présentation"
        rows={5}
        value={form.description}
        onChange={(e) => setForm({ ...form, description: e.target.value })}
        maxLength={5000}
        hint="Ce que fait l’entreprise, où elle se trouve, comment l’équipe travaille."
        error={errors.description}
      />
      <div className="detail-actions">
        <Button type="submit" loading={saving}>
          {initial ? 'Enregistrer' : 'Créer mon entreprise'}
        </Button>
      </div>
    </form>
  );
}
