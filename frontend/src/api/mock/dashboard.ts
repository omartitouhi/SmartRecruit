import { HttpError } from '../../lib/errors';
import { FUNNEL_STATUSES, type Account, type Application, type ApplicationsByStatus, type Job, type PaginatedResponse, type Role } from '../../types';
import type { DashboardApi } from '../dashboard';
import { audit, clone, closeExpiredJobs, db, delay, findJob, now, recruiterCompany, requireRole, save, today } from './store';

function paginate<T>(rows: T[], page = 0, size = 20): PaginatedResponse<T> {
  const pageSize = Math.min(Math.max(size, 1), 50);
  return {
    content: clone(rows.slice(page * pageSize, (page + 1) * pageSize)),
    page,
    size: pageSize,
    totalElements: rows.length,
    totalPages: Math.ceil(rows.length / pageSize),
  };
}

const publicAccount = ({ password: _password, ...account }: Account & { password: string }): Account => {
  void _password;
  return account;
};

const average = (scores: number[]) =>
  scores.length ? Math.round((scores.reduce((a, b) => a + b, 0) / scores.length) * 10) / 10 : null;

const DAY_MS = 86400000;

/** EXT-DASH-01: the funnel, every non-withdrawn application by status. */
function byStatus(applications: Application[]): ApplicationsByStatus {
  const counts = Object.fromEntries(FUNNEL_STATUSES.map((s) => [s, 0])) as ApplicationsByStatus;
  applications.forEach((a) => {
    if (a.status !== 'WITHDRAWN') counts[a.status] += 1;
  });
  return counts;
}

/** Mean days between applying and the first ACCEPTED/REJECTED entry of the history, one decimal. */
function averageDecisionDays(applications: Application[]) {
  const days = applications.flatMap((a) => {
    const decision = a.statusHistory.find((h) => h.status === 'ACCEPTED' || h.status === 'REJECTED');
    return decision ? [(new Date(decision.changedAt).getTime() - new Date(a.appliedAt).getTime()) / DAY_MS] : [];
  });
  return average(days);
}

/** Published offers whose deadline falls within the next 7 days, today included. */
function closingSoon(jobs: Job[]) {
  const from = today();
  const to = new Date(Date.now() + 7 * DAY_MS).toISOString().slice(0, 10);
  return jobs.filter((j) => j.status === 'PUBLISHED' && j.applicationDeadline && j.applicationDeadline >= from && j.applicationDeadline <= to).length;
}

export const mockDashboardApi: DashboardApi = {
  async globalStats() {
    await delay();
    requireRole('ADMIN');
    closeExpiredJobs();
    const users = db.accounts.filter((a) => a.status !== 'DELETED');
    const count = (role: Role) => users.filter((a) => a.role === role).length;
    return {
      activeJobs: db.jobs.filter((j) => j.status === 'PUBLISHED').length,
      totalApplications: db.applications.length,
      totalUsers: users.length,
      usersByRole: { CANDIDATE: count('CANDIDATE'), RECRUITER: count('RECRUITER'), ADMIN: count('ADMIN') },
      averageMatchScore: average(db.applications.flatMap((a) => (a.matchScore ? [a.matchScore.score] : []))),
      applicationsByStatus: byStatus(db.applications),
      averageDecisionDays: averageDecisionDays(db.applications),
      jobsClosingSoon: closingSoon(db.jobs),
    };
  },

  async companyStats(companyId) {
    await delay();
    const account = requireRole('RECRUITER', 'ADMIN');
    if (account.role === 'RECRUITER' && recruiterCompany(account.id)?.id !== companyId) throw new HttpError(403);
    closeExpiredJobs();
    const jobIds = new Set(db.jobs.filter((j) => j.companyId === companyId).map((j) => j.id));
    const received = db.applications.filter((a) => jobIds.has(a.jobId) && a.status !== 'WITHDRAWN');
    const accepted = received.filter((a) => a.status === 'ACCEPTED').length;
    const decided = accepted + received.filter((a) => a.status === 'REJECTED').length;
    return {
      publishedJobs: db.jobs.filter((j) => j.companyId === companyId && j.status === 'PUBLISHED').length,
      applicationsReceived: received.length,
      acceptedApplications: accepted,
      acceptanceRate: decided ? accepted / decided : null,
      applicationsByStatus: byStatus(received),
      averageDecisionDays: averageDecisionDays(received),
      upcomingInterviews: received.filter(
        (a) => a.status === 'INTERVIEW' && a.interview && new Date(a.interview.scheduledAt).getTime() >= Date.now(),
      ).length,
      jobsClosingSoon: closingSoon(db.jobs.filter((j) => j.companyId === companyId)),
    };
  },

  async users({ role, status, page, size } = {}) {
    await delay();
    requireRole('ADMIN');
    const rows = db.accounts
      .filter((a) => (!role || a.role === role) && (!status || a.status === status))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(publicAccount);
    return paginate(rows, page, size);
  },

  async setUserStatus(accountId, status) {
    await delay();
    const admin = requireRole('ADMIN');
    if (status !== 'ACTIVE' && status !== 'DISABLED') throw new HttpError(400, 'Statut invalide.');
    const target = db.accounts.find((a) => a.id === accountId);
    if (!target) throw new HttpError(404, 'Compte introuvable.');
    if (target.id === admin.id) throw new HttpError(400, 'Vous ne pouvez pas désactiver votre propre compte.');
    if (target.status === 'DELETED') throw new HttpError(409, 'Ce compte a été supprimé.');
    target.status = status;
    target.updatedAt = now();
    audit('USER_STATUS_CHANGED', 'Account', target.id, admin.id);
    save();
    return clone(publicAccount(target));
  },

  async moderateJob(jobId, reason) {
    await delay();
    const admin = requireRole('ADMIN');
    const job = findJob(jobId);
    if (!reason.trim() || reason.length > 500) {
      throw new HttpError(400, 'Requête invalide', { reason: 'Le motif est obligatoire (500 caractères maximum)' });
    }
    job.status = 'MODERATED';
    job.moderationReason = reason.trim();
    job.updatedAt = now();
    audit('JOB_MODERATED', 'Job', job.id, admin.id);
    save();
    return clone(job);
  },

  async auditLog({ from, to, actorId, action, page, size } = {}) {
    await delay();
    requireRole('ADMIN');
    const rows = db.audit
      .filter((e) => {
        const t = new Date(e.timestamp).getTime();
        if (from && t < new Date(from).getTime()) return false;
        if (to && t > new Date(to).getTime()) return false;
        return (!actorId || e.actorId === actorId) && (!action || e.action === action);
      })
      .sort((a, b) => b.timestamp.localeCompare(a.timestamp));
    return paginate(rows, page, size);
  },
};
