import type {
  AccountStatus,
  ApplicationStatus,
  ContractType,
  ExperienceLevel,
  InvitationStatus,
  JobStatus,
  Role,
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
