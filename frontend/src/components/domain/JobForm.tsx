import { useState, type FormEvent } from 'react';
import { toUiError } from '../../lib/errors';
import { CONTRACT_LABEL, isoDay, LEVEL_LABEL, WORK_MODE_LABEL } from '../../lib/format';
import { EMPTY_JOB, toFormState, toFormValues, validateJobForm, type JobFormState, type JobFormValues } from '../../lib/jobForm';
import type { ContractType, ExperienceLevel, WorkMode } from '../../types';
import { Button, ButtonLink } from '../ui/Button';
import { SelectField, TextAreaField, TextField } from '../ui/Field';
import { Alert } from '../ui/States';
import { TagInput } from '../ui/TagInput';

interface Props {
  /** Defaults to an empty publication form. */
  initial?: JobFormValues;
  submitLabel: string;
  cancelTo: string;
  /** A rejection is shown as a form message plus the `fieldErrors` of a 400, under their field. */
  onSubmit(values: JobFormValues): Promise<void>;
}

/** The job offer form shared by the publication and the edit pages (validation, server errors, saving state). */
export function JobForm({ initial = EMPTY_JOB, submitLabel, cancelTo, onSubmit }: Props) {
  const [form, setForm] = useState<JobFormState>(() => toFormState(initial));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const found = validateJobForm(form, initial.applicationDeadline);
    setErrors(found);
    if (Object.keys(found).length) return;
    setSaving(true);
    setFormError(null);
    try {
      await onSubmit(toFormValues(form));
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
      <div className="form-row">
        <SelectField
          label="Mode de travail"
          value={form.workMode}
          onChange={(e) => setForm({ ...form, workMode: e.target.value as WorkMode })}
          options={Object.entries(WORK_MODE_LABEL).map(([value, label]) => ({ value, label }))}
          error={errors.workMode}
        />
        <TextField
          label="Nombre de postes"
          type="number"
          inputMode="numeric"
          min={1}
          max={100}
          value={form.positions}
          onChange={(e) => setForm({ ...form, positions: e.target.value })}
          error={errors.positions}
        />
        <TextField
          label="Date limite de candidature"
          type="date"
          min={form.applicationDeadline && form.applicationDeadline === initial.applicationDeadline ? undefined : isoDay()}
          value={form.applicationDeadline}
          onChange={(e) => setForm({ ...form, applicationDeadline: e.target.value })}
          hint="Facultative. L’offre est clôturée automatiquement le lendemain."
          error={errors.applicationDeadline}
        />
      </div>
      <div className="form-row">
        <TextField
          label="Salaire minimum (TND brut / mois)"
          type="number"
          inputMode="numeric"
          min={0}
          step={100}
          value={form.salaryMin}
          placeholder="2 500"
          onChange={(e) => setForm({ ...form, salaryMin: e.target.value })}
          hint="Facultatif. Un salaire affiché attire plus de candidatures."
          error={errors.salaryMin}
        />
        <TextField
          label="Salaire maximum (TND brut / mois)"
          type="number"
          inputMode="numeric"
          min={0}
          step={100}
          value={form.salaryMax}
          placeholder="3 500"
          onChange={(e) => setForm({ ...form, salaryMax: e.target.value })}
          error={errors.salaryMax}
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
