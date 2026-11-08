import { useState, type ReactNode } from 'react';
import { Button } from '../../../components/ui/Button';
import { formatYearMonth } from '../../../lib/format';
import type { CVParsingResult } from '../../../types';

type Section = keyof CVParsingResult;

/**
 * Validation des données extraites du CV : le candidat coche ce qu'il garde, le reste est ignoré.
 * `found` ne contient que du nouveau (voir newSuggestions) ; rien n'est enregistré avant « Appliquer ».
 */
export function CvSuggestions({ found, busy, onApply, onDismiss }: {
  found: CVParsingResult;
  busy: boolean;
  onApply: (picked: CVParsingResult) => void;
  onDismiss: () => void;
}) {
  const [ignored, setIgnored] = useState<Set<string>>(new Set());
  const id = (section: Section, i: number) => `${section}:${i}`;
  const toggle = (key: string) =>
    setIgnored((prev) => {
      const next = new Set(prev);
      if (!next.delete(key)) next.add(key);
      return next;
    });
  const pick = <T,>(section: Section, items: T[]) => items.filter((_, i) => !ignored.has(id(section, i)));

  const picked: CVParsingResult = {
    skills: pick('skills', found.skills),
    languages: pick('languages', found.languages),
    experiences: pick('experiences', found.experiences),
    education: pick('education', found.education),
  };
  const count = Object.values(picked).reduce((n, items) => n + items.length, 0);

  const group = (section: Section, legend: string, labels: ReactNode[]) =>
    labels.length > 0 && (
      <fieldset className="fieldset suggestions__group">
        <legend className="field__label">{legend}</legend>
        <ul className="suggestions__list">
          {labels.map((label, i) => (
            <li key={id(section, i)}>
              <label className="check">
                <input type="checkbox" checked={!ignored.has(id(section, i))} onChange={() => toggle(id(section, i))} />
                <span>{label}</span>
              </label>
            </li>
          ))}
        </ul>
      </fieldset>
    );

  return (
    <section className="panel suggestions" aria-labelledby="suggestions-title">
      <h2 className="panel__title" id="suggestions-title">
        Données extraites de votre CV
      </h2>
      <p className="panel__sub">
        Cochez ce que vous voulez ajouter à votre profil : ces données servent au calcul de vos scores. Ce que vous
        décochez est ignoré.
      </p>
      {group('skills', 'Compétences', found.skills)}
      {group(
        'experiences',
        'Expériences',
        found.experiences.map((x) => (
          <>
            <b>{x.title}</b> · {x.company}{' '}
            <span className="muted">
              ({formatYearMonth(x.startDate)} – {formatYearMonth(x.endDate)})
            </span>
          </>
        )),
      )}
      {group(
        'education',
        'Formation',
        found.education.map((x) => (
          <>
            <b>{x.degree}</b> · {x.institution} <span className="muted">({x.year})</span>
          </>
        )),
      )}
      {group('languages', 'Langues', found.languages)}
      <div className="detail-actions">
        <Button loading={busy} disabled={count === 0} onClick={() => onApply(picked)}>
          Appliquer {count > 0 ? `${count} élément${count > 1 ? 's' : ''}` : 'la sélection'} et enregistrer
        </Button>
        <Button variant="ghost" disabled={busy} onClick={onDismiss}>
          Tout ignorer
        </Button>
      </div>
    </section>
  );
}
