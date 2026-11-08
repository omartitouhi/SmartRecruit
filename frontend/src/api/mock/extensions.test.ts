import { beforeEach, describe, expect, it } from 'vitest';
import { EMAIL, jobByTitle, loginAs, statusOf, resetDb } from '../../test/mockDb';
import { mockApplicationsApi as applications } from './applications';
import { mockJobsApi as jobs } from './jobs';
import { db } from './store';

const day = (offset: number) => new Date(Date.now() + offset * 86400000).toISOString().slice(0, 10);
const inDays = (offset: number) => new Date(Date.now() + offset * 86400000).toISOString();

const offer = {
  title: 'Développeur Go',
  description: 'Vous construisez nos services de paiement.',
  requiredSkills: ['Go'],
  experienceLevel: 'SENIOR' as const,
  contractType: 'CDI' as const,
  location: 'Tunis',
};

beforeEach(resetDb);

describe('EXT-JOB-01 enriched offers (mock)', () => {
  it('applies the defaults of the optional fields on creation', async () => {
    loginAs(EMAIL.recruiter);
    const created = await jobs.create(offer);
    expect(created).toMatchObject({ workMode: 'ON_SITE', salaryMin: null, salaryMax: null, applicationDeadline: null, positions: 1 });
  });

  it('rejects an inverted salary range, a past deadline and a bad number of positions (400 with fieldErrors)', async () => {
    loginAs(EMAIL.recruiter);
    const error = await jobs.create({ ...offer, salaryMin: 4000, salaryMax: 3000, applicationDeadline: day(-1), positions: 0 }).catch((e) => e);
    expect(error.status).toBe(400);
    expect(Object.keys(error.fieldErrors).sort()).toEqual(['applicationDeadline', 'positions', 'salaryMax']);
  });

  it('checks the salary order of a patch against the saved bound and lets an optional field be removed', async () => {
    loginAs(EMAIL.recruiter);
    const backend = jobByTitle('Développeur·se backend');
    expect(await statusOf(jobs.update(backend.id, { salaryMax: 1000 }))).toBe(400);
    const updated = await jobs.update(backend.id, { salaryMin: null, applicationDeadline: null });
    expect(updated).toMatchObject({ salaryMin: null, applicationDeadline: null, salaryMax: 4200 });
  });

  it('filters by work mode and minimum monthly salary', async () => {
    loginAs(EMAIL.candidate);
    const remote = await jobs.search({ workMode: 'REMOTE' });
    expect(remote.content.length).toBeGreaterThan(0);
    expect(remote.content.every((j) => j.workMode === 'REMOTE')).toBe(true);
    const wellPaid = await jobs.search({ minSalary: 4500 });
    expect(wellPaid.content.map((j) => j.title).sort()).toEqual(['Chef·fe de projet SI', 'Data engineer']);
  });

  it('closes a published offer once its deadline has passed and refuses to reopen it', async () => {
    const backend = jobByTitle('Développeur·se backend');
    backend.applicationDeadline = day(-1);
    loginAs(EMAIL.candidate);
    expect((await jobs.search({})).content.some((j) => j.id === backend.id)).toBe(false);
    expect(backend.status).toBe('CLOSED');
    loginAs(EMAIL.recruiter);
    const error = await jobs.updateStatus(backend.id, 'PUBLISHED').catch((e) => e);
    expect(error.status).toBe(409);
    expect(error.message).toMatch(/dupliquez/);
  });

  it('duplicates an offer as a draft without deadline, but not a moderated one', async () => {
    loginAs(EMAIL.recruiter);
    const source = jobByTitle('Développeur·se backend');
    const copy = await jobs.duplicate(source.id);
    expect(copy).toMatchObject({
      title: 'Développeur·se backend Java (copie)',
      status: 'DRAFT',
      publishedAt: null,
      applicationDeadline: null,
      salaryMin: source.salaryMin,
      positions: source.positions,
    });
    expect(copy.id).not.toBe(source.id);
    expect(await statusOf(jobs.duplicate(jobByTitle('Responsable exploitation').id))).toBe(409);
    loginAs(EMAIL.otherRecruiter);
    expect(await statusOf(jobs.duplicate(source.id))).toBe(403);
  });
});

describe('EXT-SAVED-01 favourites (mock)', () => {
  it('saves and unsaves idempotently, flags the offers and lists the newest first', async () => {
    loginAs(EMAIL.candidate);
    const frontend = jobByTitle('Développeur·se front-end');
    expect((await jobs.get(frontend.id)).saved).toBe(false);
    await jobs.save(frontend.id);
    await jobs.save(frontend.id);
    expect((await jobs.get(frontend.id)).saved).toBe(true);
    const saved = await jobs.saved();
    expect(saved[0].id).toBe(frontend.id);
    expect(saved.every((j) => j.saved)).toBe(true);
    await jobs.unsave(frontend.id);
    await jobs.unsave(frontend.id);
    expect((await jobs.saved()).some((j) => j.id === frontend.id)).toBe(false);
  });

  it('answers 404 for an unpublished offer, keeps closed favourites and hides the flag from other roles', async () => {
    loginAs(EMAIL.candidate);
    expect(await statusOf(jobs.save(jobByTitle('Stage data analyst').id))).toBe(404);
    const backend = jobByTitle('Développeur·se backend');
    backend.status = 'CLOSED';
    expect((await jobs.saved()).find((j) => j.id === backend.id)?.status).toBe('CLOSED');
    loginAs(EMAIL.recruiter);
    expect((await jobs.get(backend.id)).saved).toBeNull();
    expect(await statusOf(jobs.save(backend.id))).toBe(403);
  });
});

describe('EXT-APP-01/02 cover letters, messages and interviews (mock)', () => {
  it('stores the cover letter and refuses an offer past its deadline', async () => {
    loginAs(EMAIL.candidate);
    const created = await applications.apply(jobByTitle('Développeur·se backend').id, '  Motivée par la logistique.  ');
    expect(created.coverLetter).toBe('Motivée par la logistique.');
    const frontend = jobByTitle('Développeur·se front-end');
    frontend.applicationDeadline = day(-1);
    expect(await statusOf(applications.apply(frontend.id))).toBe(409);
  });

  it('records the message and the interview of a status change, and audits the interview', async () => {
    const recruiter = loginAs(EMAIL.recruiter);
    const nour = db.applications.find((a) => a.status === 'IN_REVIEW' && a.jobId === jobByTitle('Développeur·se backend').id)!;
    const updated = await applications.updateStatus(nour.id, 'INTERVIEW', {
      message: 'Rencontrons-nous.',
      interview: { scheduledAt: inDays(3), mode: 'ON_SITE', location: 'Les Berges du Lac' },
    });
    expect(updated.statusHistory.at(-1)).toMatchObject({ status: 'INTERVIEW', message: 'Rencontrons-nous.', changedBy: recruiter.id });
    expect(updated.interview).toMatchObject({ mode: 'ON_SITE', durationMinutes: 60, location: 'Les Berges du Lac' });
    expect(db.audit[0]).toMatchObject({ action: 'INTERVIEW_SCHEDULED', resourceId: nour.id });
  });

  it('refuses an interview with another status (400), a past interview (400) and a reschedule outside INTERVIEW (409)', async () => {
    loginAs(EMAIL.recruiter);
    const received = db.applications.find((a) => a.status === 'RECEIVED' && a.jobId === jobByTitle('Développeur·se backend').id)!;
    expect(await statusOf(applications.updateStatus(received.id, 'IN_REVIEW', { interview: { scheduledAt: inDays(2), mode: 'PHONE' } }))).toBe(400);
    expect(await statusOf(applications.scheduleInterview(received.id, { scheduledAt: inDays(2), mode: 'PHONE' }))).toBe(409);
    const interview = db.applications.find((a) => a.status === 'INTERVIEW' && a.jobId === jobByTitle('Développeur·se backend').id)!;
    expect(await statusOf(applications.scheduleInterview(interview.id, { scheduledAt: inDays(-1), mode: 'PHONE' }))).toBe(400);
    const moved = await applications.scheduleInterview(interview.id, { scheduledAt: inDays(5), durationMinutes: 30, mode: 'PHONE' });
    expect(moved.interview).toMatchObject({ durationMinutes: 30, mode: 'PHONE', location: null });
  });

  it('lists the upcoming interviews of the recruiter’s company only, soonest first', async () => {
    loginAs(EMAIL.recruiter);
    const upcoming = await applications.upcomingInterviews();
    const fretJobs = new Set(db.jobs.filter((j) => j.companyId === db.companies[0].id).map((j) => j.id));
    expect(upcoming.length).toBeGreaterThan(0);
    expect(upcoming.every((i) => fretJobs.has(i.jobId))).toBe(true);
    loginAs(EMAIL.candidate);
    expect(await statusOf(applications.upcomingInterviews())).toBe(403);
  });
});

describe('EXT-APP-03 team notes (mock)', () => {
  const skanderOnBackend = () => db.applications.find((a) => a.status === 'INTERVIEW' && a.jobId === jobByTitle('Développeur·se backend').id)!;

  it('lets the team read, add and delete its own notes, with the count and average in the recruiter view', async () => {
    const recruiter = loginAs(EMAIL.recruiter);
    const application = skanderOnBackend();
    expect((await applications.notes(application.id)).map((n) => n.rating)).toEqual([5, 4]);
    const note = await applications.addNote(application.id, { content: 'Disponible en janvier.', rating: 3 });
    expect(note).toMatchObject({ authorId: recruiter.id, rating: 3 });
    const row = (await applications.forJob(application.jobId)).find((a) => a.id === application.id)!;
    expect(row).toMatchObject({ notesCount: 3, averageRating: 4 });
    await applications.deleteNote(application.id, note.id);
    expect(await applications.notes(application.id)).toHaveLength(2);
  });

  it('validates the note, protects others’ notes and other companies, and never shows notes to the candidate', async () => {
    loginAs(EMAIL.recruiter);
    const application = skanderOnBackend();
    expect(await statusOf(applications.addNote(application.id, { content: ' ' }))).toBe(400);
    expect(await statusOf(applications.addNote(application.id, { content: 'ok', rating: 6 }))).toBe(400);
    const theirs = db.notes.find((n) => n.applicationId === application.id)!;
    theirs.authorId = 'someone-else';
    expect(await statusOf(applications.deleteNote(application.id, theirs.id))).toBe(403);
    loginAs(EMAIL.otherRecruiter);
    expect(await statusOf(applications.notes(application.id))).toBe(403);
    loginAs(EMAIL.admin);
    expect(await applications.notes(application.id)).toHaveLength(2);
    expect(await statusOf(applications.addNote(application.id, { content: 'admin' }))).toBe(403);
    loginAs(EMAIL.skander);
    expect(await statusOf(applications.notes(application.id))).toBe(403);
    const mine = (await applications.mine()).find((a) => a.id === application.id)!;
    expect(mine).toMatchObject({ notesCount: null, averageRating: null });
    expect(mine.interview).not.toBeNull();
  });
});
