import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EMAIL, jobByTitle, loginAs, loginAsNew, resetDb, statusOf } from '../../test/mockDb';
import { mockApplicationsApi as applications } from './applications';
import { db } from './store';

const backend = () => jobByTitle('Développeur·se backend');

beforeEach(resetDb);
afterEach(() => vi.useRealTimers());

describe('mock apply', () => {
  it('creates a RECEIVED application with its history and no score yet', async () => {
    const candidate = loginAs(EMAIL.candidate);
    const created = await applications.apply(backend().id);
    expect(created).toMatchObject({ status: 'RECEIVED', candidateId: candidate.id, matchScore: null });
    expect(created.statusHistory).toEqual([expect.objectContaining({ status: 'RECEIVED', changedBy: candidate.id })]);
  });

  it('scores the application in the background', async () => {
    vi.useFakeTimers();
    loginAs(EMAIL.candidate);
    const created = await applications.apply(backend().id);
    await vi.advanceTimersByTimeAsync(2000);
    const [mine] = await applications.mine();
    expect(mine.id).toBe(created.id);
    expect(mine.matchScore?.score).toBeGreaterThan(0);
    expect(mine.matchScore?.criteria.length).toBeGreaterThan(0);
  });

  it('answers 404 for an unknown offer and 409 for an offer that is not PUBLISHED', async () => {
    loginAs(EMAIL.candidate);
    expect(await statusOf(applications.apply('00000000-0000-0000-0000-000000000000'))).toBe(404);
    expect(await statusOf(applications.apply(jobByTitle('Stage data analyst').id))).toBe(409);
  });

  it('answers 422 without a profile or without a CV', async () => {
    loginAsNew('CANDIDATE');
    expect(await statusOf(applications.apply(backend().id))).toBe(422);
    const amira = loginAs(EMAIL.candidate);
    const profile = db.profiles.find((p) => p.id === amira.id);
    if (profile) profile.cvUrl = null;
    expect(await statusOf(applications.apply(backend().id))).toBe(422);
  });

  it('answers 409 for a duplicate, but allows applying again after a withdrawal', async () => {
    loginAs(EMAIL.candidate);
    const created = await applications.apply(backend().id);
    expect(await statusOf(applications.apply(backend().id))).toBe(409);
    await applications.withdraw(created.id);
    expect((await applications.apply(backend().id)).status).toBe('RECEIVED');
  });

  it('is reserved to candidates', async () => {
    loginAs(EMAIL.recruiter);
    expect(await statusOf(applications.apply(backend().id))).toBe(403);
  });
});

describe('mock applications lists', () => {
  it('lists the candidate applications newest first, withdrawn ones included', async () => {
    loginAs(EMAIL.candidate);
    const created = await applications.apply(backend().id);
    await applications.withdraw(created.id);
    const mine = await applications.mine();
    expect(mine[0].id).toBe(created.id);
    expect(mine[0].status).toBe('WITHDRAWN');
    expect(mine.map((a) => a.appliedAt)).toEqual([...mine.map((a) => a.appliedAt)].sort().reverse());
  });

  it('ranks the received applications by score, unscored last, without withdrawn ones', async () => {
    const job = backend();
    const [first] = db.applications.filter((a) => a.jobId === job.id);
    first.matchScore = null;
    db.applications.find((a) => a.jobId === job.id && a.id !== first.id)!.status = 'WITHDRAWN';
    loginAs(EMAIL.recruiter);
    const list = await applications.forJob(job.id);
    expect(list.some((a) => a.status === 'WITHDRAWN')).toBe(false);
    const firstUnscored = list.findIndex((a) => !a.matchScore);
    expect(list.slice(firstUnscored).every((a) => !a.matchScore)).toBe(true);
    expect(list.some((a) => a.id === first.id)).toBe(true);
    const scores = list.slice(0, firstUnscored).map((a) => a.matchScore?.score ?? 0);
    expect(scores).toEqual([...scores].sort((a, b) => b - a));
  });

  it('answers 403 to another company and 404 for an unknown offer, and lets an admin read', async () => {
    loginAs(EMAIL.otherRecruiter);
    expect(await statusOf(applications.forJob(backend().id))).toBe(403);
    loginAs(EMAIL.admin);
    expect((await applications.forJob(backend().id)).length).toBeGreaterThan(0);
    expect(await statusOf(applications.forJob('00000000-0000-0000-0000-000000000000'))).toBe(404);
    loginAs(EMAIL.candidate);
    expect(await statusOf(applications.forJob(backend().id))).toBe(403);
  });
});

describe('mock application status', () => {
  const find = (status: string) => db.applications.find((a) => a.jobId === backend().id && a.status === status)!;

  it('follows the allowed transitions and records the history and an audit event', async () => {
    const target = find('RECEIVED');
    const recruiter = loginAs(EMAIL.recruiter);
    const updated = await applications.updateStatus(target.id, 'IN_REVIEW');
    expect(updated.status).toBe('IN_REVIEW');
    expect(updated.statusHistory.at(-1)).toMatchObject({ status: 'IN_REVIEW', changedBy: recruiter.id });
    expect(db.audit[0]).toMatchObject({ action: 'APPLICATION_STATUS_CHANGED', resourceId: target.id, actorId: recruiter.id });
  });

  it('answers 400 for a skipped step, a decided application or WITHDRAWN', async () => {
    loginAs(EMAIL.recruiter);
    expect(await statusOf(applications.updateStatus(find('RECEIVED').id, 'INTERVIEW'))).toBe(400);
    expect(await statusOf(applications.updateStatus(find('ACCEPTED').id, 'REJECTED'))).toBe(400);
    expect(await statusOf(applications.updateStatus(find('RECEIVED').id, 'WITHDRAWN'))).toBe(400);
  });

  it('answers 403 to candidates and other companies, 404 for an unknown application', async () => {
    const target = find('RECEIVED');
    loginAs(EMAIL.candidate);
    expect(await statusOf(applications.updateStatus(target.id, 'IN_REVIEW'))).toBe(403);
    loginAs(EMAIL.otherRecruiter);
    expect(await statusOf(applications.updateStatus(target.id, 'IN_REVIEW'))).toBe(403);
    expect(await statusOf(applications.updateStatus('00000000-0000-0000-0000-000000000000', 'IN_REVIEW'))).toBe(404);
  });
});

describe('mock withdraw', () => {
  it('withdraws from RECEIVED or IN_REVIEW and records it', async () => {
    const amira = loginAs(EMAIL.candidate);
    const received = db.applications.find((a) => a.candidateId === amira.id && a.status === 'RECEIVED')!;
    await applications.withdraw(received.id);
    expect(received.status).toBe('WITHDRAWN');
    expect(received.statusHistory.at(-1)?.status).toBe('WITHDRAWN');
  });

  it('answers 409 once the application is further along', async () => {
    const amira = loginAs(EMAIL.candidate);
    const interview = db.applications.find((a) => a.candidateId === amira.id && a.status === 'INTERVIEW')!;
    expect(await statusOf(applications.withdraw(interview.id))).toBe(409);
  });

  it('answers 403 for somebody else application and 404 for an unknown one', async () => {
    loginAs(EMAIL.skander);
    const amira = db.accounts.find((a) => a.email === EMAIL.candidate)!;
    const hers = db.applications.find((a) => a.candidateId === amira.id)!;
    expect(await statusOf(applications.withdraw(hers.id))).toBe(403);
    expect(await statusOf(applications.withdraw('00000000-0000-0000-0000-000000000000'))).toBe(404);
  });
});

describe('mock match preview', () => {
  it('returns the explained score once the CV is analysed', async () => {
    loginAs(EMAIL.candidate);
    const match = await applications.matchPreview(backend().id);
    expect(match.score).toBeGreaterThan(0);
    expect(match.criteria.map((c) => c.label)).toContain('Compétences techniques');
  });

  it('answers 422 until the CV is analysed, 404 for an unknown offer', async () => {
    const amira = loginAs(EMAIL.candidate);
    const profile = db.profiles.find((p) => p.id === amira.id)!;
    profile.cvAnalysisStatus = 'PENDING';
    expect(await statusOf(applications.matchPreview(backend().id))).toBe(422);
    profile.cvAnalysisStatus = 'DONE';
    expect(await statusOf(applications.matchPreview('00000000-0000-0000-0000-000000000000'))).toBe(404);
    loginAsNew('CANDIDATE');
    expect(await statusOf(applications.matchPreview(backend().id))).toBe(422);
  });

  it('lists the skills the candidate lacks', async () => {
    const amira = loginAs(EMAIL.candidate);
    const gap = await applications.skillGap(amira.id, backend().id);
    expect(gap.missingSkills).toEqual(expect.arrayContaining(['Kubernetes', 'GraphQL']));
    expect(gap.missingSkills).not.toContain('Java');
  });
});
