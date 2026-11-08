// Shared state and helpers of the in-browser gateway stand-in (VITE_USE_MOCKS / VITE_MOCK_MODULES). It mirrors the
// backend's rules (roles, 401/403/404/409, status transitions, CV limits) so screens behave as they will for real.

import { HttpError } from '../../lib/errors';
import { type Account, type AuditLogEntry, type Role } from '../../types';
import type { JobSummary } from '../jobs';
import { tokenStore } from '../client';
import { createSeed, DB_VERSION, type MockDb } from './seed';

const STORAGE_KEY = 'smartrecruit_mock_db';
export const CV_PARSE_DELAY_MS = 4000;

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

export const db: MockDb = load();
export const cvBlobs = new Map<string, Blob>();

export function save() {
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

export const delay = (ms = 220 + Math.random() * 260) => new Promise((r) => setTimeout(r, ms));
export const now = () => new Date().toISOString();
export const uuid = () => crypto.randomUUID();
export const clone = <T>(v: T): T => structuredClone(v);

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

export function currentAccount(): Account {
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

export function requireRole(...roles: Role[]) {
  const account = currentAccount();
  if (!roles.includes(account.role)) throw new HttpError(403);
  return account;
}

export function audit(action: string, resourceType: string, resourceId: string, actorId: string) {
  const entry: AuditLogEntry = { id: uuid(), action, resourceType, resourceId, actorId, timestamp: now() };
  db.audit.unshift(entry);
}

export function findProfile(id: string) {
  return db.profiles.find((p) => p.id === id);
}

export function findJob(id: string) {
  const job = db.jobs.find((j) => j.id === id);
  if (!job) throw new HttpError(404, 'Offre introuvable.');
  return job;
}

/** The caller, or null outside a session (no role-dependent field then). */
function viewer(): Account | null {
  try {
    return currentAccount();
  } catch {
    return null;
  }
}

export function summary(jobId: string, withCount = false): JobSummary {
  const job = findJob(jobId);
  const caller = viewer();
  return {
    ...clone(job),
    companyName: db.companies.find((c) => c.id === job.companyId)?.name ?? null,
    applicationsCount: withCount ? db.applications.filter((a) => a.jobId === jobId && a.status !== 'WITHDRAWN').length : null,
    // EXT-SAVED-01: only candidates get a saved flag.
    saved: caller?.role === 'CANDIDATE' ? db.savedJobs.some((s) => s.candidateId === caller.id && s.jobId === jobId) : null,
  };
}

/** `YYYY-MM-DD` of today (UTC), the reference of application deadlines. */
export const today = () => new Date().toISOString().slice(0, 10);

/** A deadline is the last day one may apply: it is passed from the next day on. */
export const deadlinePassed = (deadline: string | null) => Boolean(deadline && deadline < today());

/** What the scheduled job of job-service does every 5 minutes: close published offers past their deadline. */
export function closeExpiredJobs() {
  let changed = false;
  for (const job of db.jobs) {
    if (job.status === 'PUBLISHED' && deadlinePassed(job.applicationDeadline)) {
      job.status = 'CLOSED';
      job.updatedAt = now();
      changed = true;
    }
  }
  if (changed) save();
}

export function recruiterCompany(accountId: string) {
  return db.companies.find((c) => c.members.includes(accountId));
}

/** A recruiter only sees and changes what belongs to their own company. */
export function assertOwnsJob(accountId: string, jobId: string) {
  const company = recruiterCompany(accountId);
  if (!company || findJob(jobId).companyId !== company.id) throw new HttpError(403);
}
