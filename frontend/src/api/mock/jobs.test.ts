import { beforeEach, describe, expect, it } from 'vitest';
import { EMAIL, jobByTitle, loginAs, loginAsNew, resetDb, statusOf } from '../../test/mockDb';
import type { JobCreateRequest } from '../jobs';
import { mockJobsApi as jobs } from './jobs';
import { db } from './store';

const valid: JobCreateRequest = {
  title: 'Développeur Go',
  description: 'Vous construisez nos services de paiement en Go.',
  requiredSkills: ['Go', 'PostgreSQL'],
  experienceLevel: 'INTERMEDIATE',
  contractType: 'CDI',
  location: 'Tunis',
};

beforeEach(resetDb);

describe('mock job search', () => {
  it('shows candidates only PUBLISHED offers', async () => {
    loginAs(EMAIL.candidate);
    const page = await jobs.search({ size: 50 });
    expect(page.content.length).toBeGreaterThan(0);
    expect(page.content.every((j) => j.status === 'PUBLISHED')).toBe(true);
    expect(page.content.some((j) => j.title.startsWith('Stage data analyst'))).toBe(false);
    expect(page.content.every((j) => j.applicationsCount === null)).toBe(true);
  });

  it('matches the keyword in the title or description only', async () => {
    loginAs(EMAIL.candidate);
    const byTitle = await jobs.search({ keyword: 'react' });
    expect(byTitle.content.map((j) => j.title)).toEqual([expect.stringContaining('React')]);
    const byDescription = await jobs.search({ keyword: 'huile d’olive' });
    expect(byDescription.content.length).toBeGreaterThan(0);
    // Skills and company names are not searched by keyword.
    expect((await jobs.search({ keyword: 'Terraform' })).totalElements).toBe(0);
    expect((await jobs.search({ keyword: 'Carthage Fret' })).totalElements).toBe(0);
  });

  it('keeps offers that need at least one of the requested skills', async () => {
    loginAs(EMAIL.candidate);
    const page = await jobs.search({ skills: ['terraform', 'GraphQL'] });
    expect(page.totalElements).toBeGreaterThan(1);
    expect(page.content.every((j) => j.requiredSkills.some((s) => ['terraform', 'graphql'].includes(s.toLowerCase())))).toBe(true);
  });

  it('filters by location, contract and level', async () => {
    loginAs(EMAIL.candidate);
    const page = await jobs.search({ location: 'sfax', contractType: 'CDI', experienceLevel: 'SENIOR' });
    expect(page.content.map((j) => j.title)).toEqual([expect.stringContaining('Data engineer')]);
  });

  it('caps the page size at 50 and paginates', async () => {
    loginAs(EMAIL.candidate);
    expect((await jobs.search({ size: 500 })).size).toBe(50);
    const first = await jobs.search({ size: 2, page: 0 });
    const second = await jobs.search({ size: 2, page: 1 });
    expect(first.totalPages).toBeGreaterThan(1);
    expect(first.content.map((j) => j.id)).not.toEqual(second.content.map((j) => j.id));
  });

  it('sorts by publication date, newest first', async () => {
    loginAs(EMAIL.candidate);
    const dates = (await jobs.search({ size: 50 })).content.map((j) => j.publishedAt ?? '');
    expect(dates).toEqual([...dates].sort().reverse());
  });

  it('gives a recruiter every status of their company, with application counts, for companyId=me', async () => {
    loginAs(EMAIL.recruiter);
    const page = await jobs.search({ companyId: 'me', size: 50 });
    expect(new Set(page.content.map((j) => j.status))).toEqual(new Set(['PUBLISHED', 'DRAFT', 'MODERATED']));
    const backend = page.content.find((j) => j.title.startsWith('Développeur·se backend'));
    expect(backend?.applicationsCount).toBe(6);
    expect(page.content.every((j) => j.companyId === page.content[0].companyId)).toBe(true);
  });

  it('filters companyId=me by status', async () => {
    loginAs(EMAIL.recruiter);
    const drafts = await jobs.search({ companyId: 'me', status: 'DRAFT' });
    expect(drafts.content.map((j) => j.status)).toEqual(['DRAFT']);
  });

  it('lets an admin see and filter every status, but refuses companyId=me to a candidate', async () => {
    loginAs(EMAIL.admin);
    expect((await jobs.search({ status: 'MODERATED' })).content).toHaveLength(1);
    expect((await jobs.search({ size: 50 })).content.some((j) => j.status === 'DRAFT')).toBe(true);
    loginAs(EMAIL.candidate);
    expect(await statusOf(jobs.search({ companyId: 'me' }))).toBe(403);
  });
});

describe('mock job detail', () => {
  it('hides a draft from candidates (404) but not from its company', async () => {
    const draft = jobByTitle('Stage data analyst');
    loginAs(EMAIL.candidate);
    expect(await statusOf(jobs.get(draft.id))).toBe(404);
    loginAs(EMAIL.otherRecruiter);
    expect(await statusOf(jobs.get(draft.id))).toBe(404);
    loginAs(EMAIL.recruiter);
    expect((await jobs.get(draft.id)).title).toContain('Stage data analyst');
  });

  it('answers 404 for an unknown id', async () => {
    loginAs(EMAIL.candidate);
    expect(await statusOf(jobs.get('00000000-0000-0000-0000-000000000000'))).toBe(404);
  });
});

describe('mock job creation', () => {
  it('publishes immediately under the recruiter company', async () => {
    const recruiter = loginAs(EMAIL.recruiter);
    const created = await jobs.create(valid);
    expect(created.status).toBe('PUBLISHED');
    expect(created.publishedAt).not.toBeNull();
    expect(created.companyId).toBe(db.companies.find((c) => c.members.includes(recruiter.id))?.id);
    loginAs(EMAIL.candidate);
    expect((await jobs.search({ keyword: 'Go' })).content.map((j) => j.id)).toContain(created.id);
  });

  it('answers 403 when the recruiter has no company', async () => {
    loginAsNew('RECRUITER');
    expect(await statusOf(jobs.create(valid))).toBe(403);
  });

  it('answers 403 to a candidate', async () => {
    loginAs(EMAIL.candidate);
    expect(await statusOf(jobs.create(valid))).toBe(403);
  });

  it('answers 400 with a message per invalid field', async () => {
    loginAs(EMAIL.recruiter);
    const error = await jobs
      .create({ ...valid, title: ' ', description: 'trop court', requiredSkills: [], location: '' })
      .catch((e) => e as { status: number; fieldErrors: Record<string, string> });
    expect(error).toMatchObject({ status: 400 });
    expect(Object.keys((error as { fieldErrors: object }).fieldErrors).sort()).toEqual([
      'description',
      'location',
      'requiredSkills',
      'title',
    ]);
  });

  it('rejects more than 50 skills and skills over 100 characters', async () => {
    loginAs(EMAIL.recruiter);
    expect(await statusOf(jobs.create({ ...valid, requiredSkills: Array.from({ length: 51 }, (_, i) => `s${i}`) }))).toBe(400);
    expect(await statusOf(jobs.create({ ...valid, requiredSkills: ['x'.repeat(101)] }))).toBe(400);
  });
});

describe('mock job update', () => {
  it('patches only the given fields and bumps updatedAt', async () => {
    loginAs(EMAIL.recruiter);
    const job = jobByTitle('Développeur·se backend');
    const before = job.updatedAt;
    const updated = await jobs.update(job.id, { title: 'Backend Java confirmé' });
    expect(updated.title).toBe('Backend Java confirmé');
    expect(updated.location).toBe(job.location);
    expect(updated.updatedAt > before).toBe(true);
  });

  it('answers 403 for another company, 409 for a closed or moderated offer, 400 for a bad field', async () => {
    const job = jobByTitle('Développeur·se backend');
    loginAs(EMAIL.otherRecruiter);
    expect(await statusOf(jobs.update(job.id, { title: 'x' }))).toBe(403);
    loginAs(EMAIL.recruiter);
    expect(await statusOf(jobs.update(job.id, { description: 'court' }))).toBe(400);
    await jobs.updateStatus(job.id, 'CLOSED');
    expect(await statusOf(jobs.update(job.id, { title: 'x' }))).toBe(409);
    expect(await statusOf(jobs.update(jobByTitle('Responsable exploitation').id, { title: 'x' }))).toBe(409);
  });
});

describe('mock job status', () => {
  it('allows DRAFT→PUBLISHED, PUBLISHED→CLOSED and CLOSED→PUBLISHED', async () => {
    loginAs(EMAIL.recruiter);
    const draft = jobByTitle('Stage data analyst');
    expect((await jobs.updateStatus(draft.id, 'PUBLISHED')).publishedAt).not.toBeNull();
    expect((await jobs.updateStatus(draft.id, 'CLOSED')).status).toBe('CLOSED');
    expect((await jobs.updateStatus(draft.id, 'PUBLISHED')).status).toBe('PUBLISHED');
  });

  it('removes a closed offer from candidate search', async () => {
    loginAs(EMAIL.recruiter);
    const job = jobByTitle('Développeur·se front-end');
    await jobs.updateStatus(job.id, 'CLOSED');
    loginAs(EMAIL.candidate);
    expect((await jobs.search({ size: 50 })).content.map((j) => j.id)).not.toContain(job.id);
  });

  it('answers 400 for any other transition', async () => {
    loginAs(EMAIL.recruiter);
    const published = jobByTitle('Développeur·se backend');
    expect(await statusOf(jobs.updateStatus(published.id, 'DRAFT'))).toBe(400);
    expect(await statusOf(jobs.updateStatus(published.id, 'PUBLISHED'))).toBe(400);
    expect(await statusOf(jobs.updateStatus(published.id, 'MODERATED'))).toBe(400);
  });

  it('answers 403 for a moderated offer, another company or a non-recruiter', async () => {
    loginAs(EMAIL.recruiter);
    expect(await statusOf(jobs.updateStatus(jobByTitle('Responsable exploitation').id, 'PUBLISHED'))).toBe(403);
    loginAs(EMAIL.otherRecruiter);
    expect(await statusOf(jobs.updateStatus(jobByTitle('Développeur·se backend').id, 'CLOSED'))).toBe(403);
    loginAs(EMAIL.admin);
    expect(await statusOf(jobs.updateStatus(jobByTitle('Développeur·se backend').id, 'CLOSED'))).toBe(403);
  });
});
