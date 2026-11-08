import type { JobCreateRequest } from '../api/jobs';
import { isoDay } from './format';

/** Tous les champs du formulaire d'offre, y compris ceux facultatifs à la création (EXT-JOB-01). */
export type JobFormValues = Required<JobCreateRequest>;

export const EMPTY_JOB: JobFormValues = {
  title: '',
  description: '',
  requiredSkills: [],
  experienceLevel: 'INTERMEDIATE',
  contractType: 'CDI',
  location: '',
  workMode: 'ON_SITE',
  salaryMin: null,
  salaryMax: null,
  applicationDeadline: null,
  positions: 1,
};

const SALARY_MAX = 1_000_000;

/** Numbers are typed as text so that an empty field stays empty (and « 12abc » can be reported). */
export interface JobFormState extends Omit<JobFormValues, 'salaryMin' | 'salaryMax' | 'positions' | 'applicationDeadline'> {
  salaryMin: string;
  salaryMax: string;
  positions: string;
  applicationDeadline: string;
}

export const toFormState = (v: JobFormValues): JobFormState => ({
  ...v,
  salaryMin: v.salaryMin === null ? '' : String(v.salaryMin),
  salaryMax: v.salaryMax === null ? '' : String(v.salaryMax),
  positions: String(v.positions),
  applicationDeadline: v.applicationDeadline ?? '',
});

const toNumber = (text: string) => (text.trim() === '' ? null : Number(text.trim()));

export const toFormValues = (s: JobFormState): JobFormValues => ({
  ...s,
  salaryMin: toNumber(s.salaryMin),
  salaryMax: toNumber(s.salaryMax),
  positions: Number(s.positions),
  applicationDeadline: s.applicationDeadline || null,
});

/**
 * Same rules as the API (API_CONTRACTS.md §2, §7.1). The deadline is only checked when it changes, so that
 * editing another field of an offer whose deadline is today keeps working tomorrow.
 */
export function validateJobForm(form: JobFormState, initialDeadline: string | null, today = isoDay()) {
  const found: Record<string, string> = {};
  if (!form.title.trim()) found.title = 'L’intitulé est obligatoire.';
  if (!form.location.trim()) found.location = 'Le lieu est obligatoire.';
  if (form.description.trim().length < 20) found.description = 'Décrivez le poste en quelques lignes (20 caractères au minimum).';
  if (!form.requiredSkills.length) found.requiredSkills = 'Ajoutez au moins une compétence : elles comptent pour 50 % du score.';
  const positions = Number(form.positions);
  if (!Number.isInteger(positions) || positions < 1 || positions > 100) found.positions = 'Entre 1 et 100 postes.';
  const min = toNumber(form.salaryMin);
  const max = toNumber(form.salaryMax);
  const badSalary = (n: number | null) => n !== null && (!Number.isInteger(n) || n < 0 || n > SALARY_MAX);
  if (badSalary(min)) found.salaryMin = 'Un montant entier entre 0 et 1 000 000.';
  if (badSalary(max)) found.salaryMax = 'Un montant entier entre 0 et 1 000 000.';
  if (!found.salaryMin && !found.salaryMax && min !== null && max !== null && min > max) {
    found.salaryMax = 'Le maximum doit être supérieur ou égal au minimum.';
  }
  if (form.applicationDeadline && form.applicationDeadline !== initialDeadline && form.applicationDeadline < today) {
    found.applicationDeadline = 'Choisissez aujourd’hui ou une date future.';
  }
  return found;
}
