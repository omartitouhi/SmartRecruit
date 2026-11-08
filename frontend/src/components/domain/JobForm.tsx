import { useState, type FormEvent } from 'react';
import type { JobCreateRequest } from '../../api/jobs';
import { toUiError } from '../../lib/errors';
import { CONTRACT_LABEL, LEVEL_LABEL } from '../../lib/format';
import type { ContractType, ExperienceLevel } from '../../types';
import { Button, ButtonLink } from '../ui/Button';
import { SelectField, TextAreaField, TextField } from '../ui/Field';
import { Alert } from '../ui/States';
import { TagInput } from '../ui/TagInput';

const EMPTY_JOB: JobCreateRequest = {
  title: '',
  description: '',
  requiredSkills: [],
  experienceLevel: 'INTERMEDIATE',
  contractType: 'CDI',
  location: '',
};

interface Props {
  /** Defaults to an empty publication form. */
  initial?: JobCreateRequest;
  submitLabel: string;
  cancelTo: string;
  /** A rejection is shown as a form message plus the `fieldErrors` of a 400, under their field. */
  onSubmit(values: JobCreateRequest): Promise<void>;
}

/** The job offer form shared by the publication and the edit pages (validation, server errors, saving state). */
export function JobForm({ initial = EMPTY_JOB, submitLabel, cancelTo, onSubmit }: Props) {
  const [form, setForm] = useState<JobCreateRequest>(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (!form.title.trim()) found.title = 'L’intitulé est obligatoire.';
    if (!form.location.trim()) found.location = 'Le lieu est obligatoire.';
    if (form.description.trim().length < 20) found.description = 'Décrivez le poste en quelques lignes (20 caractères au minimum).';
    if (!form.requiredSkills.length) found.requiredSkills = 'Ajoutez au moins une compétence : elles comptent pour 50 % du score.';
    setErrors(found);
    if (Object.keys(found).length) return;
    setSaving(true);
    setFormError(null);
    try {
      await onSubmit(form);
    } catch (err) {
      const ui = toUiError(err);
      setErrors(ui.fieldErrors);
      setFormError(ui.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="panel form" onSubmit={submit} noValidate>
      {formError && <Alert tone="error">{formError}</Alert>}
      <TextField label="Intitulé du poste" placeholder="Développeur·se backend Java" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} error={errors.title} maxLength={255} />
      <div className="form-row">
        <TextField label="Lieu" placeholder="Tunis, Les Berges du Lac" value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} error={errors.location} maxLength={255} />
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
        max={50}
        hint="Entrée ou virgule pour ajouter (50 au maximum)."
        error={errors.requiredSkills}
      />
      <TextAreaField
        label="Description du poste"
        rows={7}
        value={form.description}
        maxLength={20000}
        onChange={(e) => setForm({ ...form, description: e.target.value })}
        hint="Les missions, l’équipe, ce qui rend le poste intéressant."
        error={errors.description}
      />
      <div className="detail-actions">
        <Button type="submit" size="lg" loading={saving}>
          {submitLabel}
        </Button>
        <ButtonLink to={cancelTo} variant="ghost" size="lg">
          Annuler
        </ButtonLink>
      </div>
    </form>
  );
}
