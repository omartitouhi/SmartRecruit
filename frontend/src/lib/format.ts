import type {
  AccountStatus,
  ApplicationStatus,
  ContractType,
  ExperienceLevel,
  InterviewMode,
  InvitationStatus,
  JobStatus,
  Role,
  WorkMode,
} from '../types';

export type Tone = 'grey' | 'blue' | 'green' | 'yellow' | 'red';

export const ROLE_LABEL: Record<Role, string> = {
  CANDIDATE: 'Candidat·e',
  RECRUITER: 'Recruteur·se',
  ADMIN: 'Administrateur·rice',
};

export const CONTRACT_LABEL: Record<ContractType, string> = {
  CDI: 'CDI',
  CDD: 'CDD',
  STAGE: 'Stage',
  ALTERNANCE: 'Alternance',
  FREELANCE: 'Freelance',
};

export const LEVEL_LABEL: Record<ExperienceLevel, string> = {
  JUNIOR: 'Junior',
  INTERMEDIATE: 'Confirmé·e',
  SENIOR: 'Senior',
  LEAD: 'Lead',
};

export const WORK_MODE_LABEL: Record<WorkMode, string> = {
  ON_SITE: 'Sur site',
  HYBRID: 'Hybride',
  REMOTE: 'Télétravail',
};

export const INTERVIEW_MODE_LABEL: Record<InterviewMode, string> = {
  VIDEO: 'Visioconférence',
  ON_SITE: 'Sur place',
  PHONE: 'Téléphone',
};

export const APPLICATION_STATUS: Record<ApplicationStatus, { label: string; tone: Tone }> = {
  RECEIVED: { label: 'Reçue', tone: 'grey' },
  IN_REVIEW: { label: 'En revue', tone: 'blue' },
  INTERVIEW: { label: 'Entretien', tone: 'yellow' },
  ACCEPTED: { label: 'Acceptée', tone: 'green' },
  REJECTED: { label: 'Non retenue', tone: 'red' },
  WITHDRAWN: { label: 'Retirée', tone: 'grey' },
};

export const JOB_STATUS: Record<JobStatus, { label: string; tone: Tone }> = {
  DRAFT: { label: 'Brouillon', tone: 'grey' },
  PUBLISHED: { label: 'Publiée', tone: 'green' },
  CLOSED: { label: 'Clôturée', tone: 'blue' },
  MODERATED: { label: 'Modérée', tone: 'red' },
};

export const ACCOUNT_STATUS: Record<AccountStatus, { label: string; tone: Tone }> = {
  ACTIVE: { label: 'Actif', tone: 'green' },
  DISABLED: { label: 'Désactivé', tone: 'yellow' },
  DELETED: { label: 'Supprimé', tone: 'red' },
};

export const INVITATION_STATUS: Record<InvitationStatus, { label: string; tone: Tone }> = {
  PENDING: { label: 'En attente', tone: 'yellow' },
  ACCEPTED: { label: 'Acceptée', tone: 'green' },
  EXPIRED: { label: 'Expirée', tone: 'grey' },
};

/** Libellés des actions du journal d'audit (API_CONTRACTS.md §5) ; une action inconnue s'affiche telle quelle. */
export const AUDIT_ACTION_LABEL: Record<string, string> = {
  CV_VIEWED: 'CV consulté',
  PROFILE_VIEWED: 'Profil consulté',
  APPLICATION_STATUS_CHANGED: 'Statut de candidature modifié',
  USER_STATUS_CHANGED: 'Statut de compte modifié',
  JOB_MODERATED: 'Offre modérée',
  INTERVIEW_SCHEDULED: 'Entretien planifié',
};

/** Tranche de score pour le badge de classement (FE-MATCH-02). */
export function scoreBand(score: number): { label: string; tone: Tone } {
  if (score >= 75) return { label: 'Excellent', tone: 'green' };
  if (score >= 50) return { label: 'Bon', tone: 'blue' };
  if (score >= 25) return { label: 'Moyen', tone: 'yellow' };
  return { label: 'Faible', tone: 'red' };
}

/** Libellé de l'action d'un recruteur qui fait passer une candidature à ce statut. */
export const STATUS_ACTION: Partial<Record<ApplicationStatus, string>> = {
  IN_REVIEW: 'Passer en revue',
  INTERVIEW: 'Proposer un entretien',
  ACCEPTED: 'Accepter',
  REJECTED: 'Ne pas retenir',
  WITHDRAWN: 'Retirer ma candidature',
};

const decimal = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const integer = new Intl.NumberFormat('fr-FR');
const dateLong = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
const dateShort = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
const dateTime = new Intl.DateTimeFormat('fr-FR', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});
const monthYear = new Intl.DateTimeFormat('fr-FR', { month: 'short', year: 'numeric' });

/** Taux d'acceptation : 0.5 ou 50 → « 50 % » (le contrat ne dit pas si c'est une fraction ou un pourcentage). */
export const formatRate = (rate: number) => `${integer.format(Math.round(rate <= 1 ? rate * 100 : rate))} %`;

/** 1_300_000 → « 1,2 Mo » */
export function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} o`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} Ko`;
  return `${decimal.format(bytes / (1024 * 1024))} Mo`;
}

/** 78.5 → « 78,5 » */
export const formatScore = (n: number) => decimal.format(n);
export const formatInt = (n: number) => integer.format(n);
export const formatDate = (iso: string | null | undefined) => (iso ? dateLong.format(new Date(iso)) : '—');
export const formatDateShort = (iso: string | null | undefined) => (iso ? dateShort.format(new Date(iso)) : '—');
export const formatDateTime = (iso: string | null | undefined) => (iso ? dateTime.format(new Date(iso)) : '—');

/** « 2022-01 » → « janv. 2022 » */
export function formatYearMonth(ym: string | null | undefined) {
  if (!ym) return 'aujourd’hui';
  const [y, m] = ym.split('-').map(Number);
  return monthYear.format(new Date(y, (m || 1) - 1, 1));
}

/** Initiales pour l'avatar, à partir d'un nom ou d'un email. */
export function initials(source: string) {
  const name = source.split('@')[0].replace(/[._-]+/g, ' ').trim();
  const parts = name.split(/\s+/).filter(Boolean);
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || '?';
}

export function pluralize(count: number, singular: string, plural = `${singular}s`) {
  return `${formatInt(count)} ${count > 1 ? plural : singular}`;
}

/** Salaire brut mensuel en TND : « 2 500 – 3 500 TND / mois », « À partir de… », « Jusqu’à… » ; null sans salaire. */
export function formatSalary(min: number | null | undefined, max: number | null | undefined): string | null {
  const has = (n: number | null | undefined): n is number => typeof n === 'number';
  if (has(min) && has(max)) return min === max ? `${integer.format(min)} TND / mois` : `${integer.format(min)} – ${integer.format(max)} TND / mois`;
  if (has(min)) return `À partir de ${integer.format(min)} TND / mois`;
  if (has(max)) return `Jusqu’à ${integer.format(max)} TND / mois`;
  return null;
}

/** Jour calendaire `YYYY-MM-DD` (UTC) d'aujourd'hui, décalé de `offset` jours. */
export function isoDay(offset = 0, from: Date = new Date()) {
  return new Date(from.getTime() + offset * 86400000).toISOString().slice(0, 10);
}

/** `YYYY-MM-DD` → « 20 novembre 2026 » (lu à midi UTC pour ne jamais changer de jour). */
export const formatDay = (day: string | null | undefined) => (day ? dateLong.format(new Date(`${day}T12:00:00Z`)) : '—');
export const formatDayShort = (day: string | null | undefined) => (day ? dateShort.format(new Date(`${day}T12:00:00Z`)) : '—');

/** Jours restants avant une date limite incluse : 0 = dernier jour, négatif = passée. */
export function daysUntil(day: string, from: Date = new Date()) {
  return Math.round((new Date(`${day}T00:00:00Z`).getTime() - new Date(`${isoDay(0, from)}T00:00:00Z`).getTime()) / 86400000);
}

/** « Jusqu’au 20 nov. », « Dernier jour », « Clôture dépassée » pour une date limite de candidature. */
export function deadlineLabel(day: string | null | undefined, from: Date = new Date()): { label: string; tone: Tone } | null {
  if (!day) return null;
  const left = daysUntil(day, from);
  if (left < 0) return { label: 'Date limite dépassée', tone: 'red' };
  if (left === 0) return { label: 'Dernier jour pour postuler', tone: 'yellow' };
  if (left <= 7) return { label: `Clôture dans ${pluralize(left, 'jour')}`, tone: 'yellow' };
  return { label: `Jusqu’au ${formatDayShort(day)}`, tone: 'grey' };
}

/** 4.5 → « 4,5 jours » */
export const formatDays = (days: number) => `${decimal.format(days)} ${days > 1 ? 'jours' : 'jour'}`;

/** 75 → « 1 h 15 », 30 → « 30 min » */
export function formatDuration(minutes: number) {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (!h) return `${m} min`;
  return m ? `${h} h ${String(m).padStart(2, '0')}` : `${h} h`;
}

const dateTimeLong = new Intl.DateTimeFormat('fr-FR', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  hour: '2-digit',
  minute: '2-digit',
});
/** « mardi 18 novembre, 10:00 » */
export const formatDateTimeLong = (iso: string | null | undefined) => (iso ? dateTimeLong.format(new Date(iso)) : '—');
