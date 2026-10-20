import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { jobsApi, type JobCreateRequest } from '../../api/jobs';
import { NoCompany } from '../../components/domain/NoCompany';
import { Button, ButtonLink } from '../../components/ui/Button';
import { SelectField, TextAreaField, TextField } from '../../components/ui/Field';
import { Alert, ErrorState, PageSkeleton } from '../../components/ui/States';
import { TagInput } from '../../components/ui/TagInput';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { CONTRACT_LABEL, LEVEL_LABEL } from '../../lib/format';
import { useDocumentTitle } from '../../lib/useAsync';
import { useMyCompany } from '../../lib/useCompany';
import type { ContractType, ExperienceLevel } from '../../types';

type Draft = JobCreateRequest;

export function NewJobPage() {
  useDocumentTitle('Publier une offre');
  const navigate = useNavigate();
  const notify = useToast();
  const company = useMyCompany();
  const [form, setForm] = useState<Draft>({
    title: '',
    description: '',
    requiredSkills: [],
    experienceLevel: 'INTERMEDIATE',
    contractType: 'CDI',
    location: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  if (company.error) {
    return (
      <div className="page page--doc">
        <ErrorState error={company.error} onRetry={company.reload} />
      </div>
    );
  }
  if (company.loading) return <PageSkeleton />;
  if (!company.data) {
    return (
      <div className="page page--doc">
        <NoCompany />
      </div>
    );
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (!form.title.trim()) found.title = 'L’intitulé est obligatoire.';
    if (!form.location.trim()) found.location = 'Le lieu est obligatoire.';
    if (!form.description.trim()) found.description = 'Décrivez le poste en quelques lignes.';
    if (!form.requiredSkills.length) found.requiredSkills = 'Ajoutez au moins une compétence : elles comptent pour 50 % du score.';
    setErrors(found);
    if (Object.keys(found).length) return;
    setSaving(true);
    setFormError(null);
    try {
      await jobsApi.create(form);
      notify('Offre publiée.');
      navigate('/recruiter/jobs');
    } catch (err) {
      const ui = toUiError(err);
      setErrors(ui.fieldErrors);
      setFormError(ui.message);
      setSaving(false);
    }
  }

  return (
    <div className="page page--doc">
      <nav className="crumbs" aria-label="Fil d’Ariane">
        <Link to="/recruiter/jobs">{company.data.name}</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">Nouvelle offre</span>
      </nav>
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Nouvelle offre</h1>
          <p className="page-head__text">
            Les compétences demandées servent à calculer le score de chaque candidat. Soyez précis : « PostgreSQL » plutôt
            que « bases de données ».
          </p>
        </div>
      </header>

      <form className="panel form" onSubmit={onSubmit} noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField label="Intitulé du poste" placeholder="Développeur·se backend Java" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} error={errors.title} />
        <div className="form-row">
          <TextField label="Lieu" placeholder="Tunis, Les Berges du Lac" value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} error={errors.location} />
          <SelectField
            label="Contrat"
            value={form.contractType}
            onChange={(e) => setForm({ ...form, contractType: e.target.value as ContractType })}
            options={Object.entries(CONTRACT_LABEL).map(([value, label]) => ({ value, label }))}
          />
          <SelectField
            label="Niveau"
            value={form.experienceLevel}
            onChange={(e) => setForm({ ...form, experienceLevel: e.target.value as ExperienceLevel })}
            options={Object.entries(LEVEL_LABEL).map(([value, label]) => ({ value, label }))}
          />
        </div>
        <TagInput
          label="Compétences demandées"
          values={form.requiredSkills}
          onChange={(v) => setForm({ ...form, requiredSkills: v })}
          placeholder="Java, Spring Boot, PostgreSQL…"
          hint="Entrée ou virgule pour ajouter."
          error={errors.requiredSkills}
        />
        <TextAreaField
          label="Description du poste"
          rows={7}
          value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })}
          hint="Les missions, l’équipe, ce qui rend le poste intéressant."
          error={errors.description}
        />
        <div className="detail-actions">
          <Button type="submit" size="lg" loading={saving}>
            Publier l’offre
          </Button>
          <ButtonLink to="/recruiter/jobs" variant="ghost" size="lg">
            Annuler
          </ButtonLink>
        </div>
      </form>
    </div>
  );
}
