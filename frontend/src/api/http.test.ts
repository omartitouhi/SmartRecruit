import { describe, expect, it } from 'vitest';
import { mockHttp, sentBody } from '../test/http';
import { httpApplicationsApi } from './applications';
import { httpAuthApi } from './auth';
import { httpCandidatesApi } from './candidates';
import { httpCompaniesApi } from './companies';
import { httpDashboardApi } from './dashboard';
import { httpJobsApi } from './jobs';

/** The real HTTP modules against API_CONTRACTS.md: method, path, params and body of each call. */
const summary = (calls: { method?: string; url?: string }[]) => calls.map((c) => `${c.method} ${c.url}`);

describe('auth-service calls', () => {
  it('sends register, login, me, logout and the password flow to /auth', async () => {
    const calls = mockHttp();
    await httpAuthApi.register({ email: 'a@b.tn', password: 'secret123', role: 'RECRUITER' });
    await httpAuthApi.login('a@b.tn', 'secret123');
    await httpAuthApi.me();
    await httpAuthApi.logout('r1');
    await httpAuthApi.forgotPassword('a@b.tn');
    await httpAuthApi.resetPassword('tok', 'newsecret1');

    expect(summary(calls)).toEqual([
      'post /auth/register',
      'post /auth/login',
      'get /auth/me',
      'post /auth/logout',
      'post /auth/forgot-password',
      'post /auth/reset-password',
    ]);
    expect(sentBody(calls[0])).toEqual({ email: 'a@b.tn', password: 'secret123', role: 'RECRUITER' });
    expect(sentBody(calls[3])).toEqual({ refreshToken: 'r1' });
    expect(sentBody(calls[5])).toEqual({ token: 'tok', newPassword: 'newsecret1' });
  });
});

describe('user-service candidate calls', () => {
  it('reads and saves the profile and fetches the CV suggestions', async () => {
    const calls = mockHttp(() => ({ data: { skills: ['Java'], experiences: [], education: [], languages: [] } }));
    await httpCandidatesApi.getMyProfile();
    await httpCandidatesApi.saveMyProfile({
      firstName: 'A',
      lastName: 'B',
      email: 'a@b.tn',
      phone: null,
      location: null,
      skills: [],
      experiences: [],
      education: [],
      languages: [],
    });
    const suggestions = await httpCandidatesApi.cvSuggestions();

    expect(summary(calls)).toEqual([
      'get /users/candidates/me',
      'put /users/candidates/me',
      'get /users/candidates/me/cv-suggestions',
    ]);
    expect(suggestions.skills).toEqual(['Java']);
  });

  it('uploads the CV as multipart field "file" and reports progress', async () => {
    const calls = mockHttp();
    const progress: number[] = [];
    const file = new File(['%PDF'], 'cv.pdf', { type: 'application/pdf' });
    await httpCandidatesApi.uploadCv(file, (p) => progress.push(p));

    expect(calls[0].method).toBe('post');
    expect(calls[0].url).toBe('/users/candidates/me/cv');
    expect((calls[0].data as FormData).get('file')).toBeInstanceOf(File);
    calls[0].onUploadProgress?.({ loaded: 50, total: 200, bytes: 50, lengthComputable: true } as never);
    expect(progress).toEqual([25]);
  });

  it('downloads a CV as a blob and deletes the account with confirm=true', async () => {
    const calls = mockHttp();
    await httpCandidatesApi.downloadCv('c1');
    await httpCandidatesApi.deleteMyAccount();
    await httpCandidatesApi.getCandidate('c1');

    expect(calls[0]).toMatchObject({ method: 'get', url: '/users/candidates/c1/cv', responseType: 'blob' });
    expect(calls[1]).toMatchObject({ method: 'delete', url: '/users/candidates/me', params: { confirm: true } });
    expect(calls[2]).toMatchObject({ method: 'get', url: '/users/candidates/c1' });
  });
});

describe('user-service company calls', () => {
  it('creates, reads, patches and invites per contract', async () => {
    const calls = mockHttp();
    await httpCompaniesApi.create({ name: 'Acme', sector: 'IT', description: '' });
    await httpCompaniesApi.getMine();
    await httpCompaniesApi.get('c1');
    await httpCompaniesApi.update('c1', { description: 'new' });
    await httpCompaniesApi.invite('c1', 'x@y.tn');
    await httpCompaniesApi.invitations('c1');
    await httpCompaniesApi.acceptInvitation('tok');

    expect(summary(calls)).toEqual([
      'post /users/companies',
      'get /users/companies/me',
      'get /users/companies/c1',
      'patch /users/companies/c1',
      'post /users/companies/c1/invitations',
      'get /users/companies/c1/invitations',
      'post /users/companies/invitations/accept',
    ]);
    expect(sentBody(calls[3])).toEqual({ description: 'new' });
    expect(sentBody(calls[4])).toEqual({ email: 'x@y.tn' });
    expect(sentBody(calls[6])).toEqual({ token: 'tok' });
  });
});

describe('job-service calls', () => {
  it('searches with keyword and comma-joined skills, dropping empty filters', async () => {
    const calls = mockHttp();
    await httpJobsApi.search({
      keyword: 'java',
      location: '',
      skills: ['Java', 'React'],
      contractType: '',
      experienceLevel: 'SENIOR',
      companyId: 'me',
      page: 2,
      size: 20,
    });

    expect(calls[0]).toMatchObject({ method: 'get', url: '/jobs' });
    expect(calls[0].params).toEqual({
      keyword: 'java',
      skills: 'Java,React',
      experienceLevel: 'SENIOR',
      companyId: 'me',
      page: 2,
      size: 20,
    });
  });

  it('omits an empty skills list', async () => {
    const calls = mockHttp();
    await httpJobsApi.search({ skills: [] });
    expect(calls[0].params).toEqual({});
  });

  it('creates without a companyId, patches, changes status and reads one job', async () => {
    const calls = mockHttp();
    await httpJobsApi.create({
      title: 'Dev',
      description: 'x'.repeat(20),
      requiredSkills: ['Java'],
      experienceLevel: 'JUNIOR',
      contractType: 'CDI',
      location: 'Tunis',
    });
    await httpJobsApi.update('j1', { title: 'Dev 2' });
    await httpJobsApi.updateStatus('j1', 'CLOSED');
    await httpJobsApi.get('j1');
    await httpJobsApi.recommendations(5);

    expect(summary(calls)).toEqual([
      'post /jobs',
      'patch /jobs/j1',
      'patch /jobs/j1/status',
      'get /jobs/j1',
      'get /jobs/recommendations',
    ]);
    expect(sentBody(calls[0])).not.toHaveProperty('companyId');
    expect(sentBody(calls[2])).toEqual({ status: 'CLOSED' });
    expect(calls[4].params).toEqual({ limit: 5 });
  });
});

describe('application-service calls', () => {
  it('applies, lists, changes status and withdraws per contract', async () => {
    const calls = mockHttp();
    await httpApplicationsApi.apply('j1');
    await httpApplicationsApi.mine();
    await httpApplicationsApi.forJob('j1');
    await httpApplicationsApi.updateStatus('a1', 'IN_REVIEW');
    await httpApplicationsApi.withdraw('a1');

    expect(summary(calls)).toEqual([
      'post /applications',
      'get /applications/me',
      'get /applications',
      'patch /applications/a1/status',
      'delete /applications/a1',
    ]);
    expect(sentBody(calls[0])).toEqual({ jobId: 'j1' });
    expect(calls[2].params).toEqual({ jobId: 'j1' });
    expect(sentBody(calls[3])).toEqual({ status: 'IN_REVIEW' });
  });

  it('asks application-service for the match preview and ai-service for the skill gap', async () => {
    const calls = mockHttp();
    await httpApplicationsApi.matchPreview('j1');
    await httpApplicationsApi.skillGap('c1', 'j1');

    expect(calls[0]).toMatchObject({ method: 'get', url: '/applications/match-preview', params: { jobId: 'j1' } });
    expect(calls[1]).toMatchObject({ method: 'get', url: '/ai/skill-gap/candidates/c1/jobs/j1' });
  });
});

describe('dashboard-service calls', () => {
  it('reads global and company stats', async () => {
    const calls = mockHttp();
    await httpDashboardApi.globalStats();
    await httpDashboardApi.companyStats('c1');
    expect(summary(calls)).toEqual(['get /dashboard/stats/global', 'get /dashboard/stats/company/c1']);
  });

  it('lists users with filters and changes a status', async () => {
    const calls = mockHttp();
    await httpDashboardApi.users({ role: 'RECRUITER', status: '', page: 1, size: 20 });
    await httpDashboardApi.setUserStatus('u1', 'DISABLED');

    expect(calls[0]).toMatchObject({
      method: 'get',
      url: '/dashboard/admin/users',
      params: { role: 'RECRUITER', page: 1, size: 20 },
    });
    expect(calls[1]).toMatchObject({ method: 'patch', url: '/dashboard/admin/users/u1/status' });
    expect(sentBody(calls[1])).toEqual({ status: 'DISABLED' });
  });

  it('moderates a job with a reason and filters the audit log', async () => {
    const calls = mockHttp();
    await httpDashboardApi.moderateJob('j1', 'Doublon');
    await httpDashboardApi.auditLog({
      from: '2026-10-01T00:00:00.000Z',
      actorId: '',
      action: 'CV_VIEWED',
      page: 0,
      size: 20,
    });

    expect(calls[0]).toMatchObject({ method: 'patch', url: '/dashboard/admin/jobs/j1/moderation' });
    expect(sentBody(calls[0])).toEqual({ reason: 'Doublon' });
    expect(calls[1]).toMatchObject({ method: 'get', url: '/dashboard/admin/audit-log' });
    expect(calls[1].params).toEqual({ from: '2026-10-01T00:00:00.000Z', action: 'CV_VIEWED', page: 0, size: 20 });
  });
});
