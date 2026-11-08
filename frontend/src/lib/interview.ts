import type { Interview, InterviewMode, InterviewRequest } from '../types';

export const INTERVIEW_DEFAULT_MINUTES = 60;
export const INTERVIEW_MIN_MINUTES = 15;
export const INTERVIEW_MAX_MINUTES = 480;
export const INTERVIEW_LOCATION_MAX = 500;
export const STATUS_MESSAGE_MAX = 2000;

/**
 * Règles d'un entretien (API_CONTRACTS.md §7.3) : date dans le futur, durée 15..480 min, mode obligatoire,
 * lieu ou lien de 500 caractères au plus. Renvoie les erreurs par champ, vide si l'entretien est valide.
 */
export function validateInterview(request: Partial<InterviewRequest>, now: Date = new Date()): Record<string, string> {
  const errors: Record<string, string> = {};
  const at = request.scheduledAt ? new Date(request.scheduledAt) : null;
  if (!at || Number.isNaN(at.getTime())) errors.scheduledAt = 'Indiquez la date et l’heure de l’entretien.';
  else if (at.getTime() <= now.getTime()) errors.scheduledAt = 'L’entretien doit avoir lieu dans le futur.';
  const minutes = request.durationMinutes ?? INTERVIEW_DEFAULT_MINUTES;
  if (!Number.isInteger(minutes) || minutes < INTERVIEW_MIN_MINUTES || minutes > INTERVIEW_MAX_MINUTES) {
    errors.durationMinutes = `Entre ${INTERVIEW_MIN_MINUTES} et ${INTERVIEW_MAX_MINUTES} minutes.`;
  }
  if (!request.mode) errors.mode = 'Choisissez le format de l’entretien.';
  if (request.location && request.location.length > INTERVIEW_LOCATION_MAX) {
    errors.location = `${INTERVIEW_LOCATION_MAX} caractères maximum.`;
  }
  return errors;
}

/** Valeur d'un `<input type="datetime-local">` (heure locale) → ISO 8601 UTC ; '' si vide ou invalide. */
export function localInputToIso(value: string) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toISOString();
}

/** ISO 8601 → valeur d'un `<input type="datetime-local">` en heure locale (`YYYY-MM-DDTHH:mm`). */
export function isoToLocalInput(iso: string | null | undefined) {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Un lieu de visio est un lien cliquable ; une adresse reste du texte. */
export const isLink = (location: string | null | undefined) => Boolean(location && /^https?:\/\//i.test(location.trim()));

/** Saisie d'un entretien : l'heure reste locale dans le champ, convertie en ISO 8601 à l'envoi. */
export interface InterviewDraft {
  at: string;
  duration: string;
  mode: InterviewMode;
  location: string;
}

/** Brouillon vide (demain, 10 h) ou repris d'un entretien déjà planifié pour le replanifier. */
export function interviewDraft(current?: Interview | null, now: Date = new Date()): InterviewDraft {
  if (current) {
    return {
      at: isoToLocalInput(current.scheduledAt),
      duration: String(current.durationMinutes),
      mode: current.mode,
      location: current.location ?? '',
    };
  }
  const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 10, 0);
  return { at: isoToLocalInput(tomorrow.toISOString()), duration: String(INTERVIEW_DEFAULT_MINUTES), mode: 'VIDEO', location: '' };
}

export function draftToRequest(draft: InterviewDraft): InterviewRequest {
  return {
    scheduledAt: localInputToIso(draft.at),
    durationMinutes: Number(draft.duration),
    mode: draft.mode,
    location: draft.location.trim() || null,
  };
}

/** Erreurs par champ du brouillon (mêmes règles que l'API, API_CONTRACTS.md §7.3). */
export function draftErrors(draft: InterviewDraft, now?: Date) {
  return validateInterview(draftToRequest(draft), now);
}
