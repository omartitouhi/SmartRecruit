import { APPLICATION_STATUS_TRANSITIONS, type ApplicationStatus, type Role } from '../../types';
import type { DashboardApi, DashboardStats } from '../dashboard';
import { db, delay, clone, requireRole } from './store';

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
