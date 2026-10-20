import { HttpError } from '../../lib/errors';
import type { JobsApi } from '../jobs';
import { computeMatch } from './scoring';
import { db, save, delay, now, uuid, clone, currentAccount, requireRole, audit, findProfile, findJob, summary, recruiterCompany, assertOwnsJob } from './store';

export const mockJobsApi: JobsApi = {
  async recommendations(limit = 10) {
    await delay();
    const account = requireRole('CANDIDATE');
    const profile = findProfile(account.id);
    if (!profile) return [];
    return db.jobs
      .filter((j) => j.status === 'PUBLISHED')
      .map((j) => {
        const s = summary(j.id);
        return {
          jobId: j.id,
          title: j.title,
          companyId: j.companyId,
          companyName: s.companyName,
          location: j.location,
          contractType: j.contractType,
          experienceLevel: j.experienceLevel,
          publishedAt: j.publishedAt,
          score: computeMatch(profile, j).score,
        };
      })
      .sort((a, b) => b.score - a.score)
      .slice(0, limit);
  },

  async search({ q, location, contractType, experienceLevel, companyId, page = 0, size = 20 }) {
    await delay();
    const account = currentAccount();
    const ownCompany = account.role === 'RECRUITER' ? recruiterCompany(account.id) : undefined;
    const term = q?.trim().toLowerCase();
    const filtered = db.jobs
      .filter((j) => {
        // Drafts and closed offers are only visible to their own company and to admins.
        const visible = j.status === 'PUBLISHED' || account.role === 'ADMIN' || j.companyId === ownCompany?.id;
        if (!visible) return false;
        if (companyId && j.companyId !== companyId) return false;
        if (contractType && j.contractType !== contractType) return false;
        if (experienceLevel && j.experienceLevel !== experienceLevel) return false;
        if (location && !j.location.toLowerCase().includes(location.toLowerCase())) return false;
        if (term) {
          const company = db.companies.find((c) => c.id === j.companyId)?.name ?? '';
          const haystack = [j.title, j.description, company, ...j.requiredSkills].join(' ').toLowerCase();
          if (!haystack.includes(term)) return false;
        }
        return true;
      })
      .sort((a, b) => (b.publishedAt ?? '9999').localeCompare(a.publishedAt ?? '9999'));
    return {
      content: filtered.slice(page * size, page * size + size).map((j) => summary(j.id)),
      page,
      size,
      totalElements: filtered.length,
      totalPages: Math.max(1, Math.ceil(filtered.length / size)),
    };
  },

  async get(jobId) {
    await delay(160);
    currentAccount();
    return summary(jobId);
  },

  async create(request) {
    await delay();
    const account = requireRole('RECRUITER');
    const company = recruiterCompany(account.id);
    if (!company || company.id !== request.companyId) throw new HttpError(403);
    const fieldErrors: Record<string, string> = {};
    if (!request.title.trim()) fieldErrors.title = 'L’intitulé est obligatoire';
    if (!request.description.trim()) fieldErrors.description = 'La description est obligatoire';
    if (!request.location.trim()) fieldErrors.location = 'Le lieu est obligatoire';
    if (!request.requiredSkills.length) fieldErrors.requiredSkills = 'Ajoutez au moins une compétence';
    if (Object.keys(fieldErrors).length) throw new HttpError(400, 'Requête invalide', fieldErrors);
    const stamp = now();
    const job = { ...request, id: uuid(), status: 'DRAFT' as const, publishedAt: null, updatedAt: stamp, moderationReason: null };
    db.jobs.push(job);
    audit('JOB_CREATED', 'Job', job.id, account.id);
    save();
    return clone(job);
  },

  async updateStatus(jobId, status) {
    await delay();
    const account = requireRole('RECRUITER', 'ADMIN');
    if (account.role === 'RECRUITER') assertOwnsJob(account.id, jobId);
    if (status === 'MODERATED' && account.role !== 'ADMIN') throw new HttpError(403);
    const job = findJob(jobId);
    job.status = status;
    if (status === 'PUBLISHED' && !job.publishedAt) job.publishedAt = now();
    audit(`JOB_${status}`, 'Job', jobId, account.id);
    save();
    return clone(job);
  },
};
