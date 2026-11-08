import { useState } from 'react';
import { jobsApi } from '../../api/jobs';
import { toUiError } from '../../lib/errors';
import { useToast } from '../ui/toast';

/**
 * Bascule « Enregistrer » / « Enregistrée » d'une offre dans les favoris du candidat (EXT-SAVED-01).
 * Mise à jour optimiste : l'état revient en arrière si l'API refuse.
 */
export function SaveJobButton({ jobId, saved, title, onChange, compact }: {
  jobId: string;
  saved: boolean;
  /** Intitulé de l'offre, pour le nom accessible quand le bouton est répété dans une liste. */
  title?: string;
  onChange?: (saved: boolean) => void;
  /** Icône seule (listes) ; le libellé reste lisible par les lecteurs d'écran. */
  compact?: boolean;
}) {
  const notify = useToast();
  const [value, setValue] = useState(saved);
  const [busy, setBusy] = useState(false);

  // Follow the parent when it reloads the offer (adjusted during render, not in an effect).
  const [prop, setProp] = useState(saved);
  if (prop !== saved) {
    setProp(saved);
    setValue(saved);
  }

  async function toggle() {
    const next = !value;
    setValue(next);
    setBusy(true);
    try {
      if (next) await jobsApi.save(jobId);
      else await jobsApi.unsave(jobId);
      onChange?.(next);
      notify(next ? 'Offre ajoutée à vos favoris.' : 'Offre retirée de vos favoris.');
    } catch (err) {
      setValue(!next);
      notify(toUiError(err).message);
    } finally {
      setBusy(false);
    }
  }

  const label = value ? 'Retirer des favoris' : 'Ajouter aux favoris';
  return (
    <button
      type="button"
      className={`btn btn--ghost save-toggle${compact ? ' save-toggle--compact' : ''}`}
      aria-pressed={value}
      aria-label={title ? `${label} : ${title}` : compact ? label : undefined}
      title={compact ? label : undefined}
      disabled={busy}
      onClick={toggle}
    >
      <svg width="14" height="14" viewBox="0 0 16 16" aria-hidden="true">
        <path
          d="M8 1.6l1.9 4 4.3.5-3.2 3 .9 4.3L8 11.2l-3.9 2.2.9-4.3-3.2-3 4.3-.5z"
          fill={value ? 'currentColor' : 'none'}
          stroke="currentColor"
          strokeWidth="1.3"
          strokeLinejoin="round"
        />
      </svg>
      {!compact && <span>{value ? 'Enregistrée' : 'Enregistrer'}</span>}
    </button>
  );
}
