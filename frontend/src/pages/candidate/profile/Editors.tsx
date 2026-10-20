import { Button } from '../../../components/ui/Button';
import { TextAreaField, TextField } from '../../../components/ui/Field';
import type { Education, Experience } from '../../../types';

export function ExperienceEditor({ items, onChange, errors }: {
  items: Experience[];
  onChange: (items: Experience[]) => void;
  errors: Record<string, string>;
}) {
  const update = (i: number, patch: Partial<Experience>) => onChange(items.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  return (
    <section className="panel form" aria-labelledby="exp-title">
      <div className="panel__head" style={{ marginBottom: 0 }}>
        <h2 className="panel__title" id="exp-title">
          Expériences
        </h2>
        <Button
          variant="ghost"
          onClick={() => onChange([...items, { title: '', company: '', startDate: '', endDate: null, description: '' }])}
        >
          Ajouter une expérience
        </Button>
      </div>
      {items.length === 0 && <p className="muted">Aucune expérience. Un stage ou une alternance compte aussi.</p>}
      {items.map((x, i) => (
        <fieldset className="repeat" key={i}>
          <legend className="visually-hidden">Expérience {i + 1}</legend>
          <div className="form-row">
            <TextField label="Intitulé du poste" value={x.title} onChange={(e) => update(i, { title: e.target.value })} error={errors[`experiences[${i}].title`]} />
            <TextField label="Entreprise" value={x.company} onChange={(e) => update(i, { company: e.target.value })} error={errors[`experiences[${i}].company`]} />
          </div>
          <div className="form-row">
            <TextField label="Début" type="month" value={x.startDate} onChange={(e) => update(i, { startDate: e.target.value })} error={errors[`experiences[${i}].startDate`]} />
            <TextField
              label="Fin"
              type={x.endDate === null ? 'text' : 'month'}
              value={x.endDate ?? 'Aujourd’hui'}
              disabled={x.endDate === null}
              onChange={(e) => update(i, { endDate: e.target.value || null })}
              hint={
                <label className="check">
                  <input type="checkbox" checked={x.endDate === null} onChange={(e) => update(i, { endDate: e.target.checked ? null : x.startDate || '' })} />
                  Poste actuel
                </label>
              }
              error={errors[`experiences[${i}].endDate`]}
            />
          </div>
          <TextAreaField label="Description" value={x.description} onChange={(e) => update(i, { description: e.target.value })} maxLength={2000} rows={3} />
          <div>
            <Button variant="quiet" onClick={() => onChange(items.filter((_, j) => j !== i))}>
              Retirer cette expérience
            </Button>
          </div>
        </fieldset>
      ))}
    </section>
  );
}

export function EducationEditor({ items, onChange, errors }: {
  items: Education[];
  onChange: (items: Education[]) => void;
  errors: Record<string, string>;
}) {
  const update = (i: number, patch: Partial<Education>) => onChange(items.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  return (
    <section className="panel form" aria-labelledby="edu-title">
      <div className="panel__head" style={{ marginBottom: 0 }}>
        <h2 className="panel__title" id="edu-title">
          Formation
        </h2>
        <Button variant="ghost" onClick={() => onChange([...items, { degree: '', institution: '', year: new Date().getFullYear() }])}>
          Ajouter une formation
        </Button>
      </div>
      {items.length === 0 && <p className="muted">Aucune formation renseignée.</p>}
      {items.map((x, i) => (
        <fieldset className="repeat" key={i}>
          <legend className="visually-hidden">Formation {i + 1}</legend>
          <TextField label="Diplôme" placeholder="Master Génie Logiciel" value={x.degree} onChange={(e) => update(i, { degree: e.target.value })} error={errors[`education[${i}].degree`]} />
          <div className="form-row">
            <TextField label="Établissement" placeholder="ESPRIT" value={x.institution} onChange={(e) => update(i, { institution: e.target.value })} error={errors[`education[${i}].institution`]} />
            <TextField label="Année" type="number" min={1950} max={2100} value={x.year || ''} onChange={(e) => update(i, { year: Number(e.target.value) })} error={errors[`education[${i}].year`]} />
          </div>
          <div>
            <Button variant="quiet" onClick={() => onChange(items.filter((_, j) => j !== i))}>
              Retirer cette formation
            </Button>
          </div>
        </fieldset>
      ))}
    </section>
  );
}
