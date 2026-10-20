// In-browser stand-in for the gateway, enabled with VITE_USE_MOCKS=true. It mirrors the backend's
// rules (roles, 401/403/404/409, status transitions, CV limits) so screens behave as they will for real.

import { HttpError } from '../../lib/errors';
import {
  APPLICATION_STATUS_TRANSITIONS,
  type Account,
  type Application,
  type ApplicationStatus,
  type AuditLogEntry,
  type CandidateProfile,
  type Role,
} from '../../types';
import type { ApplicationsApi } from '../applications';
import type { AuthApi } from '../auth';
import type { CandidatesApi } from '../candidates';
import { tokenStore } from '../client';
import type { DashboardApi, DashboardStats } from '../dashboard';
import type { CompaniesApi, JobsApi, JobSummary } from '../jobs';
import { computeMatch, missingSkills } from './scoring';
import { createSeed, DB_VERSION, type MockDb } from './seed';

const STORAGE_KEY = 'smartrecruit_mock_db';
const CV_PARSE_DELAY_MS = 4000;

function load(): MockDb {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as MockDb;
      if (parsed.version === DB_VERSION) return parsed;
    }
  } catch {
    // Corrupted or blocked storage: start from the seed.
  }
  return createSeed();
}

const db: MockDb = load();
const cvBlobs = new Map<string, Blob>();
const cvUploadedAt = new Map<string, number>();

function save() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
  } catch {
    // Storage full or blocked: changes live until the next reload.
  }
}

export function resetMockData() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

const delay = (ms = 220 + Math.random() * 260) => new Promise((r) => setTimeout(r, ms));
const now = () => new Date().toISOString();
const uuid = () => crypto.randomUUID();
const clone = <T>(v: T): T => structuredClone(v);

/** With VITE_MOCK_MODULES the auth module may be real: its JWT then identifies the caller (sub, role). */
function accountFromJwt(token: string): Account | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as { sub?: string; role?: Role };
    if (!payload.sub || !payload.role) return null;
    const stamp = now();
    return { id: payload.sub, email: '', role: payload.role, status: 'ACTIVE', createdAt: stamp, updatedAt: stamp };
  } catch {
    return null;
  }
}

function currentAccount(): Account {
  const token = tokenStore.access;
  if (token && !token.startsWith('mock.')) {
    const real = accountFromJwt(token);
    if (real) return real;
  }
  const id = token?.startsWith('mock.') ? token.slice(5) : null;
  const found = db.accounts.find((a) => a.id === id && a.status === 'ACTIVE');
  if (!found) throw new HttpError(401, 'Session invalide ou expirée.');
  const { password: _password, ...account } = found;
  void _password;
  return account;
}

function requireRole(...roles: Role[]) {
  const account = currentAccount();
  if (!roles.includes(account.role)) throw new HttpError(403);
  return account;
}

function audit(action: string, resourceType: string, resourceId: string, actorId: string) {
  const entry: AuditLogEntry = { id: uuid(), action, resourceType, resourceId, actorId, timestamp: now() };
  db.audit.unshift(entry);
}

function findProfile(id: string) {
  return db.profiles.find((p) => p.id === id);
}

function findJob(id: string) {
  const job = db.jobs.find((j) => j.id === id);
  if (!job) throw new HttpError(404, 'Offre introuvable.');
  return job;
}

function summary(jobId: string): JobSummary {
  const job = findJob(jobId);
  return { ...clone(job), companyName: db.companies.find((c) => c.id === job.companyId)?.name ?? null };
}

function recruiterCompany(accountId: string) {
  return db.companies.find((c) => c.members.includes(accountId));
}

/** A recruiter only sees and changes what belongs to their own company. */
function assertOwnsJob(accountId: string, jobId: string) {
  const company = recruiterCompany(accountId);
  if (!company || findJob(jobId).companyId !== company.id) throw new HttpError(403);
}

// ───────────────────────── auth ─────────────────────────

export const mockAuthApi: AuthApi = {
  async register({ email, password, role }) {
    await delay();
    const fieldErrors: Record<string, string> = {};
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) fieldErrors.email = 'L’email n’est pas valide';
    if (password.length < 8 || password.length > 72) {
      fieldErrors.password = 'Le mot de passe doit contenir entre 8 et 72 caractères';
    }
    if (Object.keys(fieldErrors).length) throw new HttpError(400, 'Requête invalide', fieldErrors);
    if (db.accounts.some((a) => a.email.toLowerCase() === email.toLowerCase())) {
      throw new HttpError(409, 'Un compte existe déjà avec cet email.');
    }
    const stamp = now();
    const account = { id: uuid(), email, role, status: 'ACTIVE' as const, createdAt: stamp, updatedAt: stamp, password };
    db.accounts.push(account);
    audit('USER_REGISTERED', 'Account', account.id, account.id);
    save();
    const { password: _p, ...publicAccount } = account;
    void _p;
    return publicAccount;
  },

  async login(email, password) {
    await delay();
    const account = db.accounts.find((a) => a.email.toLowerCase() === email.trim().toLowerCase());
    if (!account || account.password !== password || account.status !== 'ACTIVE') {
      throw new HttpError(401, 'Email ou mot de passe incorrect.');
    }
    return { accessToken: `mock.${account.id}`, refreshToken: `mock-refresh.${uuid()}`, tokenType: 'Bearer', expiresIn: 3600 };
  },

  async me() {
    await delay(120);
    return currentAccount();
  },

  async logout() {
    await delay(80);
  },

  async forgotPassword() {
    await delay();
  },

  async resetPassword(token, newPassword) {
    await delay();
    if (newPassword.length < 8) {
      throw new HttpError(400, 'Requête invalide', { newPassword: 'Le mot de passe doit contenir entre 8 et 72 caractères' });
    }
    if (!token) throw new HttpError(400, 'Lien de réinitialisation invalide ou expiré.');
  },
};

// ───────────────────────── candidates ─────────────────────────

function withParsingState(profile: CandidateProfile) {
  // The AI parses the CV a few seconds after upload (cvParsedAt stays null until then).
  const uploaded = cvUploadedAt.get(profile.id);
  if (profile.cvUrl && !profile.cvParsedAt && uploaded && Date.now() - uploaded > CV_PARSE_DELAY_MS) {
    profile.cvParsedAt = now();
    profile.updatedAt = profile.cvParsedAt;
    save();
  }
  return clone(profile);
}

export const mockCandidatesApi: CandidatesApi = {
  async getMyProfile() {
    await delay();
    const account = requireRole('CANDIDATE');
    const profile = findProfile(account.id);
    if (!profile) throw new HttpError(404, 'Profil candidat introuvable.');
    return withParsingState(profile);
  },

  async saveMyProfile(request) {
    await delay();
    const account = requireRole('CANDIDATE');
    const fieldErrors: Record<string, string> = {};
    if (!request.firstName.trim()) fieldErrors.firstName = 'Le prénom est obligatoire';
    if (!request.lastName.trim()) fieldErrors.lastName = 'Le nom est obligatoire';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(request.email)) fieldErrors.email = 'L’email n’est pas valide';
    if (request.phone && !/^\+?[0-9 .()-]{6,20}$/.test(request.phone)) {
      fieldErrors.phone = 'Le numéro de téléphone n’est pas valide';
    }
    if (Object.keys(fieldErrors).length) throw new HttpError(400, 'Requête invalide', fieldErrors);

    const existing = findProfile(account.id);
    const stamp = now();
    const profile: CandidateProfile = {
      ...request,
      id: account.id,
      cvUrl: existing?.cvUrl ?? null,
      cvParsedAt: existing?.cvParsedAt ?? null,
      createdAt: existing?.createdAt ?? stamp,
      updatedAt: stamp,
      deleted: false,
    };
    db.profiles = [...db.profiles.filter((p) => p.id !== account.id), profile];
    save();
    return clone(profile);
  },

  async uploadCv(file) {
    await delay(700);
    const account = requireRole('CANDIDATE');
    const profile = findProfile(account.id);
    if (!profile) throw new HttpError(404, 'Créez votre profil avant de déposer un CV.');
    if (file.type !== 'application/pdf') throw new HttpError(400, 'Le CV doit être un fichier PDF.');
    if (file.size > 5 * 1024 * 1024) throw new HttpError(413, 'Le CV ne doit pas dépasser 5 Mo.');
    cvBlobs.set(account.id, file);
    cvUploadedAt.set(account.id, Date.now());
    db.cvFiles[account.id] = file.name;
    profile.cvUrl = `/api/v1/users/candidates/${account.id}/cv`;
    profile.cvParsedAt = null;
    profile.updatedAt = now();
    audit('CV_UPLOADED', 'CandidateProfile', account.id, account.id);
    save();
    return clone(profile);
  },

  async downloadCv(candidateId) {
    await delay();
    const account = currentAccount();
    if (account.role === 'CANDIDATE' && account.id !== candidateId) throw new HttpError(403);
    const profile = findProfile(candidateId);
    if (!profile?.cvUrl) throw new HttpError(404, 'Aucun CV pour ce candidat.');
    if (account.role !== 'CANDIDATE') {
      audit('CV_VIEWED', 'CandidateProfile', candidateId, account.id);
      save();
    }
    return cvBlobs.get(candidateId) ?? demoPdf(`${profile.firstName} ${profile.lastName}`, db.cvFiles[candidateId]);
  },

  async deleteMyAccount() {
    await delay();
    const account = requireRole('CANDIDATE');
    db.profiles = db.profiles.filter((p) => p.id !== account.id);
    db.deletedCandidates.push(account.id);
    const stored = db.accounts.find((a) => a.id === account.id);
    if (stored) {
      stored.status = 'DELETED';
      stored.email = `deleted+${account.id}@invalid`;
      stored.password = uuid();
    }
    cvBlobs.delete(account.id);
    delete db.cvFiles[account.id];
    audit('USER_DELETED', 'Account', account.id, account.id);
    save();
  },

  async getCandidate(candidateId) {
    await delay(160);
    requireRole('RECRUITER', 'ADMIN');
    if (db.deletedCandidates.includes(candidateId)) return { id: candidateId, deleted: true };
    const profile = findProfile(candidateId);
    if (!profile) throw new HttpError(404, 'Candidat introuvable.');
    return withParsingState(profile);
  },
};

// ───────────────────────── jobs & companies ─────────────────────────

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
    const job = { ...request, id: uuid(), status: 'DRAFT' as const, publishedAt: null };
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

export const mockCompaniesApi: CompaniesApi = {
  async getMine() {
    await delay();
    const account = requireRole('RECRUITER');
    const company = recruiterCompany(account.id);
    if (!company) throw new HttpError(404, 'Aucune entreprise rattachée à ce compte.');
    return clone(company);
  },

  async saveMine(request) {
    await delay();
    const account = requireRole('RECRUITER');
    const fieldErrors: Record<string, string> = {};
    if (!request.name.trim()) fieldErrors.name = 'Le nom est obligatoire';
    if (!request.sector.trim()) fieldErrors.sector = 'Le secteur est obligatoire';
    if (Object.keys(fieldErrors).length) throw new HttpError(400, 'Requête invalide', fieldErrors);
    let company = recruiterCompany(account.id);
    if (company) Object.assign(company, request);
    else {
      company = { ...request, id: uuid(), members: [account.id] };
      db.companies.push(company);
    }
    save();
    return clone(company);
  },
};

// ───────────────────────── applications ─────────────────────────

export const mockApplicationsApi: ApplicationsApi = {
  async apply(jobId) {
    await delay(500);
    const account = requireRole('CANDIDATE');
    const profile = findProfile(account.id);
    if (!profile) throw new HttpError(400, 'Créez votre profil avant de postuler.');
    const job = findJob(jobId);
    if (job.status !== 'PUBLISHED') throw new HttpError(400, 'Cette offre n’accepte plus de candidatures.');
    if (db.applications.some((a) => a.jobId === jobId && a.candidateId === account.id && a.status !== 'WITHDRAWN')) {
      throw new HttpError(409, 'Vous avez déjà postulé à cette offre.');
    }
    const stamp = now();
    const application: Application = {
      id: uuid(),
      jobId,
      candidateId: account.id,
      status: 'RECEIVED',
      matchScore: computeMatch(profile, job),
      appliedAt: stamp,
      statusHistory: [{ status: 'RECEIVED', changedAt: stamp, changedBy: account.id }],
    };
    db.applications.push(application);
    audit('APPLICATION_CREATED', 'Application', application.id, account.id);
    save();
    return clone(application);
  },

  async mine() {
    await delay();
    const account = requireRole('CANDIDATE');
    return clone(
      db.applications
        .filter((a) => a.candidateId === account.id)
        .sort((a, b) => b.appliedAt.localeCompare(a.appliedAt)),
    );
  },

  async forJob(jobId) {
    await delay();
    const account = requireRole('RECRUITER', 'ADMIN');
    if (account.role === 'RECRUITER') assertOwnsJob(account.id, jobId);
    return clone(db.applications.filter((a) => a.jobId === jobId));
  },

  async updateStatus(applicationId, status: ApplicationStatus) {
    await delay();
    const account = requireRole('CANDIDATE', 'RECRUITER');
    const application = db.applications.find((a) => a.id === applicationId);
    if (!application) throw new HttpError(404, 'Candidature introuvable.');
    if (account.role === 'CANDIDATE') {
      // A candidate may only withdraw their own application.
      if (application.candidateId !== account.id || status !== 'WITHDRAWN') throw new HttpError(403);
    } else {
      assertOwnsJob(account.id, application.jobId);
      if (status === 'WITHDRAWN') throw new HttpError(403);
    }
    if (!APPLICATION_STATUS_TRANSITIONS[application.status].includes(status)) {
      throw new HttpError(400, `Transition impossible : ${application.status} → ${status}.`);
    }
    application.status = status;
    application.statusHistory.push({ status, changedAt: now(), changedBy: account.id });
    audit('APPLICATION_STATUS_CHANGED', 'Application', application.id, account.id);
    save();
    return clone(application);
  },

  async matchPreview(candidateId, jobId) {
    await delay(400);
    currentAccount();
    const profile = findProfile(candidateId);
    if (!profile) throw new HttpError(404, 'Profil candidat introuvable.');
    return computeMatch(profile, findJob(jobId));
  },

  async skillGap(candidateId, jobId) {
    await delay(200);
    currentAccount();
    const profile = findProfile(candidateId);
    if (!profile) throw new HttpError(404, 'Profil candidat introuvable.');
    return { missingSkills: missingSkills(profile, findJob(jobId)) };
  },
};

// ───────────────────────── dashboard ─────────────────────────

export const mockDashboardApi: DashboardApi = {
  async stats(): Promise<DashboardStats> {
    await delay();
    requireRole('ADMIN');
    const active = db.accounts.filter((a) => a.status !== 'DELETED');
    const count = (role: Role) => active.filter((a) => a.role === role).length;
    const byStatus = Object.fromEntries(
      Object.keys(APPLICATION_STATUS_TRANSITIONS).map((s) => [s, db.applications.filter((a) => a.status === s).length]),
    ) as Record<ApplicationStatus, number>;
    const scores = db.applications.flatMap((a) => (a.matchScore ? [a.matchScore.score] : []));

    // Last 8 weeks, Monday-based.
    const monday = new Date();
    monday.setUTCHours(0, 0, 0, 0);
    monday.setUTCDate(monday.getUTCDate() - ((monday.getUTCDay() + 6) % 7));
    const weeks = Array.from({ length: 8 }, (_, i) => {
      const start = new Date(monday.getTime() - (7 - i) * 7 * 86400000);
      const end = start.getTime() + 7 * 86400000;
      return {
        weekStart: start.toISOString(),
        count: db.applications.filter((a) => {
          const t = new Date(a.appliedAt).getTime();
          return t >= start.getTime() && t < end;
        }).length,
      };
    });

    return {
      accountsByRole: { CANDIDATE: count('CANDIDATE'), RECRUITER: count('RECRUITER'), ADMIN: count('ADMIN') },
      publishedJobs: db.jobs.filter((j) => j.status === 'PUBLISHED').length,
      applicationsByStatus: byStatus,
      averageMatchScore: scores.length ? Math.round((scores.reduce((a, b) => a + b, 0) / scores.length) * 10) / 10 : null,
      applicationsPerWeek: weeks,
    };
  },

  async auditLog(page = 0, size = 20) {
    await delay();
    requireRole('ADMIN');
    const sorted = [...db.audit].sort((a, b) => b.timestamp.localeCompare(a.timestamp));
    return {
      content: clone(sorted.slice(page * size, page * size + size)),
      page,
      size,
      totalElements: sorted.length,
      totalPages: Math.max(1, Math.ceil(sorted.length / size)),
    };
  },
};

// ───────────────────────── demo CV ─────────────────────────

/** Minimal one-page PDF so "Voir le CV" opens something real for seeded candidates. */
function demoPdf(name: string, fileName = 'CV.pdf') {
  const ascii = (s: string) =>
    s
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[()\\]/g, '');
  const text = `BT /F1 22 Tf 72 760 Td (${ascii(name)}) Tj /F1 11 Tf 0 -28 Td (${ascii(fileName)} - CV de demonstration SmartRecruit) Tj ET`;
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${text.length} >>\nstream\n${text}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let body = '%PDF-1.4\n';
  const offsets: number[] = [];
  objects.forEach((obj, i) => {
    offsets.push(body.length);
    body += `${i + 1} 0 obj\n${obj}\nendobj\n`;
  });
  const xref = body.length;
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  body += offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('');
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return new Blob([body], { type: 'application/pdf' });
}
