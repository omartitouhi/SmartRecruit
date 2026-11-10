import { beforeEach, describe, expect, it } from 'vitest';
import { EMAIL, jobByTitle, loginAs, resetDb, statusOf } from '../../test/mockDb';
import { mockAuthApi as auth } from './auth';
import { mockDashboardApi as dashboard } from './dashboard';
import { mockJobsApi as jobs } from './jobs';
import { db } from './store';

beforeEach(resetDb);

describe('mock stats', () => {
  it('aggregates the platform for an admin only', async () => {
    loginAs(EMAIL.admin);
    const stats = await dashboard.globalStats();
    expect(stats.activeJobs).toBe(db.jobs.filter((j) => j.status === 'PUBLISHED').length);
    expect(stats.totalApplications).toBe(db.applications.length);
    expect(stats.usersByRole.ADMIN).toBe(1);
    // The deleted account is not counted.
    expect(stats.totalUsers).toBe(db.accounts.length - 1);
    expect(stats.averageMatchScore).toBeGreaterThan(0);
    loginAs(EMAIL.recruiter);
    expect(await statusOf(dashboard.globalStats())).toBe(403);
  });

  it('keeps only the applications made in the period', async () => {
    loginAs(EMAIL.admin);
    const dates = db.applications.map((a) => a.appliedAt.slice(0, 10)).sort();
    const day = dates[Math.floor(dates.length / 2)];
    const stats = await dashboard.globalStats({ from: day, to: day });
    expect(stats.totalApplications).toBe(dates.filter((d) => d === day).length);
    expect((await dashboard.globalStats({ from: '2999-01-01' })).totalApplications).toBe(0);
    expect((await dashboard.globalStats({})).totalApplications).toBe(db.applications.length);
  });

  it('computes company figures for a member and an admin, 403 for anyone else', async () => {
    const fret = db.companies[0];
    loginAs(EMAIL.recruiter);
    expect(await dashboard.companyStats(fret.id)).toEqual({
      publishedJobs: 2,
      applicationsReceived: 8,
      acceptedApplications: 1,
      acceptanceRate: 1,
      // EXT-DASH-01: the funnel, the decision delay (4 steps, 2.5 days apart), the planned interview and the offer
      // closing within 7 days.
      applicationsByStatus: { RECEIVED: 3, IN_REVIEW: 3, INTERVIEW: 1, ACCEPTED: 1, REJECTED: 0 },
      averageDecisionDays: 7.5,
      upcomingInterviews: 1,
      jobsClosingSoon: 1,
    });
    loginAs(EMAIL.admin);
    expect((await dashboard.companyStats(fret.id)).applicationsReceived).toBe(8);
    loginAs(EMAIL.otherRecruiter);
    expect(await statusOf(dashboard.companyStats(fret.id))).toBe(403);
    loginAs(EMAIL.candidate);
    expect(await statusOf(dashboard.companyStats(fret.id))).toBe(403);
  });

  it('gives a null acceptance rate when nothing is decided yet', async () => {
    const fret = db.companies[0];
    db.applications.forEach((a) => a.status === 'ACCEPTED' && (a.status = 'RECEIVED'));
    loginAs(EMAIL.recruiter);
    expect((await dashboard.companyStats(fret.id)).acceptanceRate).toBeNull();
  });
});

describe('mock admin users', () => {
  it('filters by role and status and pages the result', async () => {
    loginAs(EMAIL.admin);
    const recruiters = await dashboard.users({ role: 'RECRUITER' });
    expect(recruiters.content.every((u) => u.role === 'RECRUITER')).toBe(true);
    expect(recruiters.content[0]).not.toHaveProperty('password');
    const deleted = await dashboard.users({ status: 'DELETED' });
    expect(deleted.content).toHaveLength(1);
    const small = await dashboard.users({ size: 3, page: 1 });
    expect(small).toMatchObject({ page: 1, size: 3 });
    expect(small.content).toHaveLength(3);
  });

  it('is reserved to admins', async () => {
    loginAs(EMAIL.candidate);
    expect(await statusOf(dashboard.users())).toBe(403);
  });

  it('disables an account, audits it, and then refuses its login', async () => {
    const admin = loginAs(EMAIL.admin);
    const target = db.accounts.find((a) => a.email === EMAIL.skander)!;
    const updated = await dashboard.setUserStatus(target.id, 'DISABLED');
    expect(updated.status).toBe('DISABLED');
    expect(updated).not.toHaveProperty('password');
    expect(db.audit[0]).toMatchObject({ action: 'USER_STATUS_CHANGED', resourceId: target.id, actorId: admin.id });
    expect(await statusOf(auth.login(EMAIL.skander, 'demo1234'))).toBe(401);
    expect((await dashboard.setUserStatus(target.id, 'ACTIVE')).status).toBe('ACTIVE');
    expect((await auth.login(EMAIL.skander, 'demo1234')).tokenType).toBe('Bearer');
  });

  it('answers 400 when an admin disables themselves, 404 for an unknown account, 409 for a deleted one', async () => {
    const admin = loginAs(EMAIL.admin);
    expect(await statusOf(dashboard.setUserStatus(admin.id, 'DISABLED'))).toBe(400);
    expect(await statusOf(dashboard.setUserStatus('00000000-0000-0000-0000-000000000000', 'DISABLED'))).toBe(404);
    const deleted = db.accounts.find((a) => a.status === 'DELETED')!;
    expect(await statusOf(dashboard.setUserStatus(deleted.id, 'ACTIVE'))).toBe(409);
  });
});

describe('mock job moderation', () => {
  it('withdraws the offer with its reason, audits it and hides it from candidates', async () => {
    const job = jobByTitle('Développeur·se front-end');
    const admin = loginAs(EMAIL.admin);
    const moderated = await dashboard.moderateJob(job.id, '  Annonce discriminatoire  ');
    expect(moderated).toMatchObject({ status: 'MODERATED', moderationReason: 'Annonce discriminatoire' });
    expect(db.audit[0]).toMatchObject({ action: 'JOB_MODERATED', resourceId: job.id, actorId: admin.id });
    loginAs(EMAIL.candidate);
    expect((await jobs.search({ size: 50 })).content.map((j) => j.id)).not.toContain(job.id);
    loginAs(EMAIL.recruiter);
    expect((await jobs.get(job.id)).moderationReason).toBe('Annonce discriminatoire');
  });

  it('requires a reason of at most 500 characters', async () => {
    const job = jobByTitle('Développeur·se front-end');
    loginAs(EMAIL.admin);
    const error = await dashboard.moderateJob(job.id, '   ').catch((e) => e as { status: number; fieldErrors: object });
    expect(error).toMatchObject({ status: 400, fieldErrors: { reason: expect.any(String) } });
    expect(await statusOf(dashboard.moderateJob(job.id, 'x'.repeat(501)))).toBe(400);
    expect(job.status).toBe('PUBLISHED');
  });

  it('answers 404 for an unknown offer and 403 to non-admins', async () => {
    loginAs(EMAIL.admin);
    expect(await statusOf(dashboard.moderateJob('00000000-0000-0000-0000-000000000000', 'x'))).toBe(404);
    loginAs(EMAIL.recruiter);
    expect(await statusOf(dashboard.moderateJob(jobByTitle('Développeur·se front-end').id, 'x'))).toBe(403);
  });
});

describe('mock audit log', () => {
  it('lists the most recent events first', async () => {
    loginAs(EMAIL.admin);
    const { content } = await dashboard.auditLog();
    expect(content.map((e) => e.timestamp)).toEqual([...content.map((e) => e.timestamp)].sort().reverse());
  });

  it('filters by action, actor and period', async () => {
    loginAs(EMAIL.admin);
    const cv = await dashboard.auditLog({ action: 'CV_VIEWED' });
    expect(cv.content.length).toBeGreaterThan(0);
    expect(cv.content.every((e) => e.action === 'CV_VIEWED')).toBe(true);

    const recruiter = db.accounts.find((a) => a.email === EMAIL.recruiter)!;
    const byActor = await dashboard.auditLog({ actorId: recruiter.id });
    expect(byActor.content.every((e) => e.actorId === recruiter.id)).toBe(true);

    const window = await dashboard.auditLog({ from: '2026-10-12T00:00:00Z', to: '2026-10-14T23:59:59Z' });
    expect(window.content.length).toBeGreaterThan(0);
    expect(window.content.every((e) => e.timestamp >= '2026-10-12' && e.timestamp <= '2026-10-15')).toBe(true);

    expect((await dashboard.auditLog({ action: 'JOB_MODERATED', actorId: recruiter.id })).totalElements).toBe(0);
  });

  it('paginates and is reserved to admins', async () => {
    loginAs(EMAIL.admin);
    const page = await dashboard.auditLog({ size: 5, page: 1 });
    expect(page).toMatchObject({ page: 1, size: 5 });
    expect(page.totalPages).toBe(Math.ceil(page.totalElements / 5));
    loginAs(EMAIL.candidate);
    expect(await statusOf(dashboard.auditLog())).toBe(403);
  });

  it('records recruiter reads of a profile and a CV', async () => {
    const { mockCandidatesApi: candidates } = await import('./candidates');
    const recruiter = loginAs(EMAIL.recruiter);
    const skander = db.accounts.find((a) => a.email === EMAIL.skander)!;
    await candidates.getCandidate(skander.id);
    await candidates.downloadCv(skander.id);
    const actions = db.audit.slice(0, 2).map((e) => [e.action, e.actorId]);
    expect(actions).toEqual([
      ['CV_VIEWED', recruiter.id],
      ['PROFILE_VIEWED', recruiter.id],
    ]);
  });
});
