import { useId, useState, type KeyboardEvent } from 'react';
import { Tag } from './Tag';

/** Saisie d'une liste de mots (compétences, langues) : Entrée ou virgule pour ajouter. */
export function TagInput({ label, values, onChange, placeholder, hint, error, max = 100 }: {
  label: string;
  values: string[];
  onChange: (values: string[]) => void;
  placeholder?: string;
  hint?: string;
  error?: string;
  max?: number;
}) {
  const id = useId();
  const [draft, setDraft] = useState('');

  function add(raw: string) {
    const items = raw
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    const next = [...values];
    for (const item of items) {
      if (next.length >= max) break;
      if (!next.some((v) => v.toLowerCase() === item.toLowerCase())) next.push(item);
    }
    if (next.length !== values.length) onChange(next);
    setDraft('');
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      if (draft.trim()) add(draft);
    } else if (e.key === 'Backspace' && !draft && values.length) {
      onChange(values.slice(0, -1));
    }
  }

  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>
        {label}
      </label>
      {values.length > 0 && (
        <div className="tag-list" aria-label={`${label} ajoutées`}>
          {values.map((v) => (
            <Tag key={v} onRemove={() => onChange(values.filter((x) => x !== v))} removeLabel={`Retirer ${v}`}>
              {v}
            </Tag>
          ))}
        </div>
      )}
      <input
        id={id}
        className="input"
        value={draft}
        placeholder={placeholder}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={onKeyDown}
        onBlur={() => draft.trim() && add(draft)}
        aria-invalid={error ? true : undefined}
      />
      {hint && !error && <span className="field__hint">{hint}</span>}
      {error && (
        <span className="field__error" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}
