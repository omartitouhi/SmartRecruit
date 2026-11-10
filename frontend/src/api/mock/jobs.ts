import { HttpError } from '../../lib/errors';
import { JOB_STATUS_TRANSITIONS, type Job } from '../../types';
import type { JobCreateRequest, JobsApi } from '../jobs';
import { computeMatch } from './scoring';
import {
  assertOwnsJob,
  clone,
  closeExpiredJobs,
  currentAccount,
  db,
  deadlinePassed,
  delay,
  findJob,
  findProfile,
  now,
  recruiterCompany,
  requireRole,
  save,
  summary,
  today,
  uuid,
} from './store';

const SALARY_MAX = 1_000_000;
export const MAX_SAVED_JOBS = 200;
const WORK_MODES = ['ON_SITE', 'HYBRID', 'REMOTE'];

const isSalary = (n: unknown) => typeof n === 'number' && Number.isInteger(n) && n >= 0 && n <= SALARY_MAX;

/**
 * Field rules of JobCreateRequest (API_CONTRACTS.md §2 and §7.1); a patch only checks the fields it carries,
 * except the salary order, checked against the saved offer (`current`) when only one bound is sent.
 */
function validateJob(request: Partial<JobCreateRequest>, partial: boolean, current?: Job) {
  const e: Record<string, string> = {};
  const { title, description, requiredSkills, location } = request;
  const has = (k: keyof JobCreateRequest) => !partial || k in request;
  if (has('title')) {
    if (!title?.trim()) e.title = 'L’intitulé est obligatoire';
    else if (title.length > 255) e.title = '255 caractères maximum';
  }
  if (has('description')) {
    if (!description || description.trim().length < 20 || description.length > 20000) {
      e.description = 'La description doit contenir entre 20 et 20 000 caractères';
    }
  }
  if (has('requiredSkills')) {
    if (!requiredSkills?.length || requiredSkills.length > 50) e.requiredSkills = 'Indiquez entre 1 et 50 compétences';
    else if (requiredSkills.some((s) => s.length > 100)) e.requiredSkills = '100 caractères maximum par compétence';
  }
  if (has('location')) {
    if (!location?.trim()) e.location = 'Le lieu est obligatoire';
    else if (location.length > 255) e.location = '255 caractères maximum';
  }
  // EXT-JOB-01: work mode and positions may be omitted on creation (defaults) but never removed.
  if ('workMode' in request && !WORK_MODES.includes(String(request.workMode))) {
    e.workMode = 'Choisissez un mode de travail';
  }
  if ('positions' in request) {
    const n = request.positions;
    if (typeof n !== 'number' || !Number.isInteger(n) || n < 1 || n > 100) e.positions = 'Entre 1 et 100 postes';
  }
  if (request.salaryMin != null && !isSalary(request.salaryMin)) e.salaryMin = 'Un montant entier entre 0 et 1 000 000';
  if (request.salaryMax != null && !isSalary(request.salaryMax)) e.salaryMax = 'Un montant entier entre 0 et 1 000 000';
  const min = 'salaryMin' in request ? request.salaryMin : current?.salaryMin;
  const max = 'salaryMax' in request ? request.salaryMax : current?.salaryMax;
  if (!e.salaryMin && !e.salaryMax && min != null && max != null && min > max) {
    e.salaryMax = 'Le salaire maximum doit être supérieur ou égal au minimum';
  }
  if (request.applicationDeadline != null) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(request.applicationDeadline)) e.applicationDeadline = 'Date invalide';
    else if (request.applicationDeadline < today()) e.applicationDeadline = 'La date limite doit être aujourd’hui ou plus tard';
  }
  if (Object.keys(e).length) throw new HttpError(400, 'Requête invalide', e);
}

const normalize = (s: string) => s.trim().toLowerCase();

/** EXT-JOB-01: keeps offers paying at least `minSalary` (by their maximum, else their minimum). */
const paysAtLeast = (job: Job, minSalary: number) =>
  job.salaryMax != null ? job.salaryMax >= minSalary : job.salaryMin != null && job.salaryMin >= minSalary;

export const mockJobsApi: JobsApi = {
  async recommendations(limit = 10) {
    await delay();
    const account = requireRole('CANDIDATE');
    closeExpiredJobs();
    const profile = findProfile(account.id);
    if (!profile) return [];
    return db.jobs
      .filter((j) => j.status === 'PUBLISHED')
      .map((j) => ({
        jobId: j.id,
        title: j.title,
        companyId: j.companyId,
        companyName: summary(j.id).companyName,
        location: j.location,
        contractType: j.contractType,
        experienceLevel: j.experienceLevel,
        publishedAt: j.publishedAt,
        score: computeMatch(profile, j).score,
      }))
      .sort((a, b) => b.score - a.score)
      .slice(0, limit);
  },

  async search({ keyword, location, skills, contractType, experienceLevel, companyId, status, workMode, minSalary, page = 0, size = 20 }) {
    await delay();
    const account = currentAccount();
    closeExpiredJobs();
    const own = account.role === 'RECRUITER' ? recruiterCompany(account.id) : undefined;
    const mine = companyId === 'me';
    if (mine && account.role !== 'RECRUITER') throw new HttpError(403);
    // Candidates and the default view only see PUBLISHED offers; `me` and admins see every status.
    const seesAll = account.role === 'ADMIN' || mine;
    const pageSize = Math.min(Math.max(size, 1), 50);
    const term = keyword?.trim().toLowerCase();
    const wanted = (skills ?? []).map(normalize).filter(Boolean);
    const salaryFloor = minSalary === '' || minSalary === undefined ? null : Number(minSalary);
    const filtered = db.jobs
      .filter((j) => {
        if (mine ? j.companyId !== own?.id : companyId && j.companyId !== companyId) return false;
        if (!seesAll && j.status !== 'PUBLISHED') return false;
        if (seesAll && status && j.status !== status) return false;
        if (contractType && j.contractType !== contractType) return false;
        if (experienceLevel && j.experienceLevel !== experienceLevel) return false;
        if (workMode && j.workMode !== workMode) return false;
        if (salaryFloor !== null && !paysAtLeast(j, salaryFloor)) return false;
        if (location && !j.location.toLowerCase().includes(location.toLowerCase())) return false;
        if (term && !`${j.title} ${j.description}`.toLowerCase().includes(term)) return false;
        if (wanted.length && !j.requiredSkills.some((s) => wanted.includes(normalize(s)))) return false;
        return true;
      })
      .sort((a, b) => (b.publishedAt ?? '').localeCompare(a.publishedAt ?? ''));
    return {
      content: filtered.slice(page * pageSize, (page + 1) * pageSize).map((j) => summary(j.id, mine)),
      page,
      size: pageSize,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / pageSize),
    };
  },

  async get(jobId) {
    await delay(160);
    const account = currentAccount();
    closeExpiredJobs();
    const job = findJob(jobId);
    const owner = account.role === 'RECRUITER' && recruiterCompany(account.id)?.id === job.companyId;
    if (job.status !== 'PUBLISHED' && account.role !== 'ADMIN' && !owner) throw new HttpError(404, 'Offre introuvable.');
    return summary(jobId);
  },

  async create(request) {
    await delay();
    const account = requireRole('RECRUITER');
    const company = recruiterCompany(account.id);
    if (!company) throw new HttpError(403, 'Créez d’abord votre entreprise.');
    validateJob(request, false);
    const stamp = now();
    const job: Job = {
      title: request.title,
      description: request.description,
      requiredSkills: request.requiredSkills,
      experienceLevel: request.experienceLevel,
      contractType: request.contractType,
      location: request.location,
      workMode: request.workMode ?? 'ON_SITE',
      salaryMin: request.salaryMin ?? null,
      salaryMax: request.salaryMax ?? null,
      applicationDeadline: request.applicationDeadline ?? null,
      positions: request.positions ?? 1,
      id: uuid(),
      companyId: company.id,
      status: 'PUBLISHED',
      publishedAt: stamp,
      updatedAt: stamp,
      moderationReason: null,
    };
    db.jobs.push(job);
    save();
    return clone(job);
  },

  async update(jobId, patch) {
    await delay();
    const account = requireRole('RECRUITER');
    assertOwnsJob(account.id, jobId);
    const job = findJob(jobId);
    if (job.status === 'CLOSED' || job.status === 'MODERATED') {
      throw new HttpError(409, 'Une offre clôturée ou modérée ne peut plus être modifiée.');
    }
    validateJob(patch, true, job);
    Object.assign(job, patch, { updatedAt: now() });
    save();
    return clone(job);
  },

  async updateStatus(jobId, status) {
    await delay();
    const account = requireRole('RECRUITER');
    assertOwnsJob(account.id, jobId);
    const job = findJob(jobId);
    if (job.status === 'MODERATED') throw new HttpError(403, 'Cette offre a été modérée par l’administration.');
    if (!JOB_STATUS_TRANSITIONS[job.status].includes(status)) {
      throw new HttpError(400, `Transition impossible : ${job.status} → ${status}.`);
    }
    if (status === 'PUBLISHED' && deadlinePassed(job.applicationDeadline)) {
      throw new HttpError(
        409,
        job.status === 'CLOSED'
          ? 'La date limite de candidature est dépassée : dupliquez l’offre pour la republier.'
          : 'La date limite de candidature est dépassée : modifiez-la ou supprimez-la avant de publier.',
      );
    }
    job.status = status;
    job.updatedAt = now();
    if (status === 'PUBLISHED' && !job.publishedAt) job.publishedAt = job.updatedAt;
    save();
    return clone(job);
  },

  async duplicate(jobId) {
    await delay();
    const account = requireRole('RECRUITER');
    assertOwnsJob(account.id, jobId);
    const source = findJob(jobId);
    if (source.status === 'MODERATED') throw new HttpError(409, 'Une offre retirée par modération ne peut pas être dupliquée.');
    const copy: Job = {
      ...clone(source),
      id: uuid(),
      title: `${source.title} (copie)`.slice(0, 255),
      status: 'DRAFT',
      publishedAt: null,
      updatedAt: now(),
      applicationDeadline: null,
      moderationReason: null,
    };
    db.jobs.push(copy);
    save();
    return clone(copy);
  },

  async remove(jobId) {
    await delay();
    const account = requireRole('RECRUITER');
    assertOwnsJob(account.id, jobId);
    if (findJob(jobId).status !== 'DRAFT') {
      throw new HttpError(409, 'Seule une offre en brouillon peut être supprimée : clôturez plutôt une offre publiée.');
    }
    db.jobs = db.jobs.filter((j) => j.id !== jobId);
    save();
  },

  async save(jobId) {
    await delay(120);
    const account = requireRole('CANDIDATE');
    closeExpiredJobs();
    const job = db.jobs.find((j) => j.id === jobId);
    if (!job || job.status !== 'PUBLISHED') throw new HttpError(404, 'Offre introuvable.');
    const mine = db.savedJobs.filter((s) => s.candidateId === account.id);
    if (mine.some((s) => s.jobId === jobId)) return;
    if (mine.length >= MAX_SAVED_JOBS) {
      throw new HttpError(409, `Vous avez déjà ${MAX_SAVED_JOBS} offres en favori : retirez-en avant d’en ajouter.`);
    }
    db.savedJobs.push({ candidateId: account.id, jobId, savedAt: now() });
    save();
  },

  async unsave(jobId) {
    await delay(120);
    const account = requireRole('CANDIDATE');
    db.savedJobs = db.savedJobs.filter((s) => !(s.candidateId === account.id && s.jobId === jobId));
    save();
  },

  async saved() {
    await delay();
    const account = requireRole('CANDIDATE');
    closeExpiredJobs();
    return db.savedJobs
      .filter((s) => s.candidateId === account.id)
      .sort((a, b) => b.savedAt.localeCompare(a.savedAt))
      .flatMap((s) => {
        const job = db.jobs.find((j) => j.id === s.jobId);
        return job && (job.status === 'PUBLISHED' || job.status === 'CLOSED') ? [summary(job.id)] : [];
      });
  },
};
