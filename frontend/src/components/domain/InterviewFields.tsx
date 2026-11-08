import { INTERVIEW_MODE_LABEL } from '../../lib/format';
import { INTERVIEW_LOCATION_MAX, INTERVIEW_MAX_MINUTES, INTERVIEW_MIN_MINUTES, type InterviewDraft } from '../../lib/interview';
import type { InterviewMode } from '../../types';
import { SelectField, TextField } from '../ui/Field';

const LOCATION_HINT: Record<InterviewMode, string> = {
  VIDEO: 'Lien de la visioconférence, envoyé au candidat.',
  ON_SITE: 'Adresse et étage, par exemple.',
  PHONE: 'Facultatif : qui appelle qui.',
};

/** Date, durée, format et lieu d'un entretien (EXT-APP-02) ; les règles sont dans `lib/interview`. */
export function InterviewFields({ value, onChange, errors = {} }: {
  value: InterviewDraft;
  onChange: (next: InterviewDraft) => void;
  errors?: Record<string, string>;
}) {
  return (
    <fieldset className="fieldset interview-fields">
      <legend className="field__label">Entretien</legend>
      <div className="form-row">
        <TextField
          label="Date et heure"
          type="datetime-local"
          value={value.at}
          onChange={(e) => onChange({ ...value, at: e.target.value })}
          error={errors.scheduledAt}
          required
        />
        <TextField
          label="Durée (minutes)"
          type="number"
          inputMode="numeric"
          min={INTERVIEW_MIN_MINUTES}
          max={INTERVIEW_MAX_MINUTES}
          step={15}
          value={value.duration}
          onChange={(e) => onChange({ ...value, duration: e.target.value })}
          error={errors.durationMinutes}
        />
        <SelectField
          label="Format"
          value={value.mode}
          onChange={(e) => onChange({ ...value, mode: e.target.value as InterviewMode })}
          options={Object.entries(INTERVIEW_MODE_LABEL).map(([v, label]) => ({ value: v, label }))}
          error={errors.mode}
        />
      </div>
      <TextField
        label={value.mode === 'VIDEO' ? 'Lien de visio' : value.mode === 'ON_SITE' ? 'Adresse' : 'Précisions'}
        value={value.location}
        maxLength={INTERVIEW_LOCATION_MAX}
        placeholder={value.mode === 'VIDEO' ? 'https://…' : undefined}
        onChange={(e) => onChange({ ...value, location: e.target.value })}
        hint={LOCATION_HINT[value.mode]}
        error={errors.location}
      />
    </fieldset>
  );
}
