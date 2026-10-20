import { beforeEach, describe, expect, it } from 'vitest';
import { tokenStore } from '../client';
import { EMAIL, resetDb, statusOf } from '../../test/mockDb';
import { mockAuthApi as auth } from './auth';
import { computeMatch, missingSkills } from './scoring';
import { db } from './store';

beforeEach(resetDb);

describe('mock auth', () => {
  it('registers an account with the chosen role and never exposes the password', async () => {
    const account = await auth.register({ email: 'new@demo.tn', password: 'secret123', role: 'RECRUITER' });
    expect(account).toMatchObject({ email: 'new@demo.tn', role: 'RECRUITER', status: 'ACTIVE' });
    expect(account).not.toHaveProperty('password');
  });

  it('answers 409 for an email already used (case-insensitive) and 400 with field errors', async () => {
    expect(await statusOf(auth.register({ email: EMAIL.candidate.toUpperCase(), password: 'secret123', role: 'CANDIDATE' }))).toBe(409);
    const error = await auth
      .register({ email: 'bad', password: 'short', role: 'CANDIDATE' })
      .catch((e) => e as { status: number; fieldErrors: Record<string, string> });
    expect(error).toMatchObject({ status: 400, fieldErrors: { email: expect.any(String), password: expect.any(String) } });
  });

  it('logs in with the demo password and resolves the account behind the token', async () => {
    const tokens = await auth.login(EMAIL.recruiter, 'demo1234');
    expect(tokens).toMatchObject({ tokenType: 'Bearer', expiresIn: 3600 });
    tokenStore.set(tokens);
    expect(await auth.me()).toMatchObject({ email: EMAIL.recruiter, role: 'RECRUITER' });
  });

  it('answers 401 for a wrong password, an unknown email and a disabled account', async () => {
    expect(await statusOf(auth.login(EMAIL.recruiter, 'wrong'))).toBe(401);
    expect(await statusOf(auth.login('ghost@demo.tn', 'demo1234'))).toBe(401);
    db.accounts.find((a) => a.email === EMAIL.recruiter)!.status = 'DISABLED';
    expect(await statusOf(auth.login(EMAIL.recruiter, 'demo1234'))).toBe(401);
  });

  it('answers 401 for me() without a valid token', async () => {
    expect(await statusOf(auth.me())).toBe(401);
    tokenStore.set({ accessToken: 'mock.unknown', refreshToken: 'r' });
    expect(await statusOf(auth.me())).toBe(401);
  });

  it('reads the caller from a real JWT when auth is not simulated', async () => {
    const payload = btoa(JSON.stringify({ sub: 'abc', role: 'ADMIN' })).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    tokenStore.set({ accessToken: `header.${payload}.sig`, refreshToken: 'r' });
    const { mockDashboardApi } = await import('./dashboard');
    expect((await mockDashboardApi.globalStats()).totalApplications).toBe(db.applications.length);
  });

  it('validates the password reset like the backend', async () => {
    expect(await statusOf(auth.resetPassword('tok', 'short'))).toBe(400);
    expect(await statusOf(auth.resetPassword('', 'longenough1'))).toBe(400);
    await expect(auth.resetPassword('tok', 'longenough1')).resolves.toBeUndefined();
  });
});

describe('mock scoring', () => {
  const amira = () => db.profiles.find((p) => p.firstName === 'Amira')!;
  const backend = () => db.jobs.find((j) => j.title.startsWith('Développeur·se backend'))!;

  it('explains the score criterion by criterion with weights summing to 1', () => {
    const match = computeMatch(amira(), backend());
    expect(match.criteria.map((c) => c.label)).toEqual(['Compétences techniques', 'Expérience', 'Formation', 'Langues']);
    expect(match.criteria.reduce((sum, c) => sum + c.weight, 0)).toBeCloseTo(1);
    expect(match.score).toBeCloseTo(match.criteria.reduce((sum, c) => sum + c.contribution, 0), 1);
    expect(match.score).toBeLessThanOrEqual(100);
    expect(match.criteria.every((c) => c.contribution <= c.weight * 100)).toBe(true);
  });

  it('scores 100 for a profile covering everything', () => {
    const job = { ...backend(), requiredSkills: ['Java'], experienceLevel: 'INTERMEDIATE' as const };
    const profile = {
      ...amira(),
      skills: ['java'],
      languages: ['Français', 'Anglais'],
      education: [{ degree: 'Master Génie Logiciel', institution: 'ESPRIT', year: 2021 }],
      experiences: [{ title: 'Dev', company: 'X', startDate: '2022-01', endDate: '2025-01', description: '' }],
    };
    expect(computeMatch(profile, job).score).toBe(100);
  });

  it('lists missing skills ignoring case and accents', () => {
    const job = { ...backend(), requiredSkills: ['JAVA', 'Kubernetes', 'Gestion d’équipe'] };
    const profile = { ...amira(), skills: ['java', 'gestion d’equipe'] };
    expect(missingSkills(profile, job)).toEqual(['Kubernetes']);
  });
});
