import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { companiesApi } from '../../api/companies';
import { jobsApi, type JobCreateRequest } from '../../api/jobs';
import { Button, ButtonLink } from '../../components/ui/Button';
import { SelectField, TextAreaField, TextField } from '../../components/ui/Field';
import { Alert, EmptyState, ErrorState, PageSkeleton } from '../../components/ui/States';
import { TagInput } from '../../components/ui/TagInput';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { CONTRACT_LABEL, LEVEL_LABEL } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { ContractType, ExperienceLevel } from '../../types';

type Draft = Omit<JobCreateRequest, 'companyId'>;

export function NewJobPage() {
  useDocumentTitle('Publier une offre');
  const navigate = useNavigate();
  const notify = useToast();
  const company = useAsync(
    () => companiesApi.getMine().catch((e) => (toUiError(e).status === 404 ? null : Promise.reject(e))),
    [],
  );
  const [form, setForm] = useState<Draft>({
    title: '',
    description: '',
    requiredSkills: [],
    experienceLevel: 'INTERMEDIATE',
    contractType: 'CDI',
    location: '',
  });
  const [publishNow, setPublishNow] = useState(true);
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
        <EmptyState title="Présentez d’abord votre entreprise" action={<ButtonLink to="/recruiter/company">Renseigner l’entreprise</ButtonLink>}>
          Chaque offre est rattachée à une entreprise. Il ne vous faut que son nom, son secteur et quelques lignes de
          présentation.
        </EmptyState>
      </div>
    );
  }
  const companyId = company.data.id;

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
      const job = await jobsApi.create({ ...form, companyId });
      if (publishNow) await jobsApi.updateStatus(job.id, 'PUBLISHED');
      notify(publishNow ? 'Offre publiée.' : 'Offre enregistrée en brouillon.');
      navigate('/recruiter/company');
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
        <Link to="/recruiter/company">{company.data.name}</Link>
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
        <label className="check">
          <input type="checkbox" checked={publishNow} onChange={(e) => setPublishNow(e.target.checked)} />
          Publier tout de suite (sinon, l’offre reste en brouillon)
        </label>
        <div className="detail-actions">
          <Button type="submit" size="lg" loading={saving}>
            {publishNow ? 'Publier l’offre' : 'Enregistrer le brouillon'}
          </Button>
          <ButtonLink to="/recruiter/company" variant="ghost" size="lg">
            Annuler
          </ButtonLink>
        </div>
      </form>
    </div>
  );
}
