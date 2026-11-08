import { HttpError } from '../../lib/errors';
import { JOB_STATUS_TRANSITIONS, type Job } from '../../types';
import type { JobCreateRequest, JobsApi } from '../jobs';
import { computeMatch } from './scoring';
import {
  assertOwnsJob,
  clone,
  currentAccount,
  db,
  delay,
  findJob,
  findProfile,
  now,
  recruiterCompany,
  requireRole,
  save,
  summary,
  uuid,
} from './store';

/** Field rules of JobCreateRequest; a patch only checks the fields it carries. */
function validateJob(request: Partial<JobCreateRequest>, partial: boolean) {
  const e: Record<string, string> = {};
  const { title, description, requiredSkills, location } = request;
  if (!partial || title !== undefined) {
    if (!title?.trim()) e.title = 'L’intitulé est obligatoire';
    else if (title.length > 255) e.title = '255 caractères maximum';
  }
  if (!partial || description !== undefined) {
    if (!description || description.trim().length < 20 || description.length > 20000) {
      e.description = 'La description doit contenir entre 20 et 20 000 caractères';
    }
  }
  if (!partial || requiredSkills !== undefined) {
    if (!requiredSkills?.length || requiredSkills.length > 50) e.requiredSkills = 'Indiquez entre 1 et 50 compétences';
    else if (requiredSkills.some((s) => s.length > 100)) e.requiredSkills = '100 caractères maximum par compétence';
  }
  if (!partial || location !== undefined) {
    if (!location?.trim()) e.location = 'Le lieu est obligatoire';
    else if (location.length > 255) e.location = '255 caractères maximum';
  }
  if (Object.keys(e).length) throw new HttpError(400, 'Requête invalide', e);
}

const normalize = (s: string) => s.trim().toLowerCase();

export const mockJobsApi: JobsApi = {
  async recommendations(limit = 10) {
    await delay();
    const account = requireRole('CANDIDATE');
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

  async search({ keyword, location, skills, contractType, experienceLevel, companyId, status, page = 0, size = 20 }) {
    await delay();
    const account = currentAccount();
    const own = account.role === 'RECRUITER' ? recruiterCompany(account.id) : undefined;
    const mine = companyId === 'me';
    if (mine && account.role !== 'RECRUITER') throw new HttpError(403);
    // Candidates and the default view only see PUBLISHED offers; `me` and admins see every status.
    const seesAll = account.role === 'ADMIN' || mine;
    const pageSize = Math.min(Math.max(size, 1), 50);
    const term = keyword?.trim().toLowerCase();
    const wanted = (skills ?? []).map(normalize).filter(Boolean);
    const filtered = db.jobs
      .filter((j) => {
        if (mine ? j.companyId !== own?.id : companyId && j.companyId !== companyId) return false;
        if (!seesAll && j.status !== 'PUBLISHED') return false;
        if (seesAll && status && j.status !== status) return false;
        if (contractType && j.contractType !== contractType) return false;
        if (experienceLevel && j.experienceLevel !== experienceLevel) return false;
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
      ...request,
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
    validateJob(patch, true);
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
    job.status = status;
    job.updatedAt = now();
    if (status === 'PUBLISHED' && !job.publishedAt) job.publishedAt = job.updatedAt;
    save();
    return clone(job);
  },
};
