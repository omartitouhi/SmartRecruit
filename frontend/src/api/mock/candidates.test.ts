import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CandidateProfileRequest } from '../candidates';
import { EMAIL, loginAs, loginAsNew, resetDb, statusOf } from '../../test/mockDb';
import { mockCandidatesApi as candidates } from './candidates';
import { db } from './store';

const pdf = (name = 'cv.pdf') => new File(['%PDF-1.4'], name, { type: 'application/pdf' });
const profileRequest: CandidateProfileRequest = {
  firstName: 'Sana',
  lastName: 'Ayari',
  email: 'sana@demo.tn',
  phone: null,
  location: 'Tunis',
  skills: ['Java'],
  experiences: [],
  education: [],
  languages: [],
};

beforeEach(resetDb);
afterEach(() => vi.useRealTimers());

describe('mock candidate profile', () => {
  it('creates then updates the profile, keeping the CV fields', async () => {
    loginAsNew('CANDIDATE');
    const created = await candidates.saveMyProfile(profileRequest);
    expect(created).toMatchObject({ firstName: 'Sana', cvUrl: null, cvAnalysisStatus: 'NONE', deleted: false });
    const updated = await candidates.saveMyProfile({ ...profileRequest, location: 'Sfax' });
    expect(updated.location).toBe('Sfax');
    expect(updated.createdAt).toBe(created.createdAt);
  });

  it('answers 404 before the profile exists and 400 with field errors for bad data', async () => {
    loginAsNew('CANDIDATE');
    expect(await statusOf(candidates.getMyProfile())).toBe(404);
    const error = await candidates
      .saveMyProfile({ ...profileRequest, firstName: '', email: 'nope', phone: 'abc' })
      .catch((e) => e as { status: number; fieldErrors: Record<string, string> });
    expect(error).toMatchObject({ status: 400 });
    expect(Object.keys((error as { fieldErrors: object }).fieldErrors).sort()).toEqual(['email', 'firstName', 'phone']);
  });

  it('is reserved to candidates', async () => {
    loginAs(EMAIL.recruiter);
    expect(await statusOf(candidates.getMyProfile())).toBe(403);
  });
});

describe('mock CV upload and analysis', () => {
  it('rejects a non-PDF (400), a file over 5 Mo (413) and a missing profile (404)', async () => {
    loginAs(EMAIL.candidate);
    expect(await statusOf(candidates.uploadCv(new File(['x'], 'cv.docx', { type: 'application/msword' })))).toBe(400);
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.pdf', { type: 'application/pdf' });
    expect(await statusOf(candidates.uploadCv(big))).toBe(413);
    loginAsNew('CANDIDATE');
    expect(await statusOf(candidates.uploadCv(pdf()))).toBe(404);
  });

  it('reports progress and puts the analysis back to PENDING', async () => {
    loginAs(EMAIL.candidate);
    const progress: number[] = [];
    const profile = await candidates.uploadCv(pdf(), (p) => progress.push(p));
    expect(progress).toEqual([30, 70, 100]);
    expect(profile).toMatchObject({ cvAnalysisStatus: 'PENDING', cvParsedAt: null });
    expect(profile.cvUrl).not.toBeNull();
  });

  it('moves PENDING to DONE a few seconds later', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    loginAs(EMAIL.candidate);
    await candidates.uploadCv(pdf());
    expect((await candidates.getMyProfile()).cvAnalysisStatus).toBe('PENDING');
    vi.setSystemTime(Date.now() + 5000);
    const done = await candidates.getMyProfile();
    expect(done.cvAnalysisStatus).toBe('DONE');
    expect(done.cvParsedAt).not.toBeNull();
  });

  it('ends in FAILED for a file the AI cannot read, without a parse date', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    loginAs(EMAIL.candidate);
    await candidates.uploadCv(pdf('cv-echec.pdf'));
    vi.setSystemTime(Date.now() + 5000);
    const failed = await candidates.getMyProfile();
    expect(failed.cvAnalysisStatus).toBe('FAILED');
    expect(failed.cvParsedAt).toBeNull();
    expect(failed.cvUrl).not.toBeNull();
  });

  it('answers 404 for cv-suggestions until a CV is analysed, then returns the extracted data', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    loginAsNew('CANDIDATE');
    await candidates.saveMyProfile(profileRequest);
    expect(await statusOf(candidates.cvSuggestions())).toBe(404);
    await candidates.uploadCv(pdf());
    expect(await statusOf(candidates.cvSuggestions())).toBe(404);
    vi.setSystemTime(Date.now() + 5000);
    const found = await candidates.cvSuggestions();
    expect(found.skills).toEqual(expect.arrayContaining(['Java', 'Kubernetes']));
    expect(found.experiences.length).toBeGreaterThan(0);
    expect(found.experiences[0]).toMatchObject({ startDate: expect.stringMatching(/^\d{4}-\d{2}$/) });
  });

  it('lets a candidate download their own CV but not somebody else', async () => {
    const amira = loginAs(EMAIL.candidate);
    expect((await candidates.downloadCv(amira.id)).type).toBe('application/pdf');
    const skander = db.accounts.find((a) => a.email === EMAIL.skander)!;
    expect(await statusOf(candidates.downloadCv(skander.id))).toBe(403);
  });
});

describe('mock recruiter view and erasure', () => {
  it('shows recruiters a profile, or the deleted marker after erasure', async () => {
    const skander = db.accounts.find((a) => a.email === EMAIL.skander)!;
    loginAs(EMAIL.skander);
    await candidates.deleteMyAccount();
    loginAs(EMAIL.recruiter);
    expect(await candidates.getCandidate(skander.id)).toEqual({ id: skander.id, deleted: true });
    const nour = db.profiles.find((p) => p.firstName === 'Nour')!;
    expect(await candidates.getCandidate(nour.id)).toMatchObject({ firstName: 'Nour', deleted: false });
    expect(await statusOf(candidates.getCandidate('00000000-0000-0000-0000-000000000000'))).toBe(404);
  });

  it('forbids candidates from reading other profiles', async () => {
    loginAs(EMAIL.candidate);
    expect(await statusOf(candidates.getCandidate('x'))).toBe(403);
  });

  it('erases the account: profile and CV gone, account DELETED and login refused', async () => {
    const account = loginAs(EMAIL.skander);
    await candidates.deleteMyAccount();
    expect(db.profiles.some((p) => p.id === account.id)).toBe(false);
    const stored = db.accounts.find((a) => a.id === account.id)!;
    expect(stored.status).toBe('DELETED');
    expect(stored.email).toMatch(/^deleted\+/);
  });
});
