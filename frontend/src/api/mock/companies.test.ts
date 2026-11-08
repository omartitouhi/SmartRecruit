import { beforeEach, describe, expect, it } from 'vitest';
import { EMAIL, loginAs, loginAsNew, resetDb, statusOf } from '../../test/mockDb';
import { mockCompaniesApi as companies } from './companies';
import { db } from './store';

const acme = { name: 'Acme', sector: 'Logiciel', description: 'Éditeur de logiciels.' };
const seededInvitation = () => db.invitations[0];

beforeEach(resetDb);

describe('mock company creation and reads', () => {
  it('creates the company with the caller as first member', async () => {
    const recruiter = loginAsNew('RECRUITER');
    const created = await companies.create(acme);
    expect(created).toMatchObject({ name: 'Acme', logoUrl: null, members: [recruiter.id] });
    expect(await companies.getMine()).toEqual(created);
  });

  it('answers 409 when the recruiter already has a company, 403 to other roles', async () => {
    loginAs(EMAIL.recruiter);
    expect(await statusOf(companies.create(acme))).toBe(409);
    loginAs(EMAIL.candidate);
    expect(await statusOf(companies.create(acme))).toBe(403);
  });

  it('validates the fields: name, sector, description length and logo scheme', async () => {
    loginAsNew('RECRUITER');
    expect(await statusOf(companies.create({ ...acme, name: '' }))).toBe(400);
    expect(await statusOf(companies.create({ ...acme, sector: ' ' }))).toBe(400);
    expect(await statusOf(companies.create({ ...acme, name: 'x'.repeat(151) }))).toBe(400);
    expect(await statusOf(companies.create({ ...acme, description: 'x'.repeat(5001) }))).toBe(400);
    expect(await statusOf(companies.create({ ...acme, logoUrl: 'ftp://x/logo.png' }))).toBe(400);
    expect((await companies.create({ ...acme, logoUrl: 'https://x.tn/logo.png' })).logoUrl).toBe('https://x.tn/logo.png');
  });

  it('answers 404 for getMine without a company', async () => {
    loginAsNew('RECRUITER');
    expect(await statusOf(companies.getMine())).toBe(404);
  });

  it('shows any signed-in user the public view, without members', async () => {
    const id = db.companies[0].id;
    loginAs(EMAIL.candidate);
    const view = await companies.get(id);
    expect(view.name).toBe(db.companies[0].name);
    expect(view).not.toHaveProperty('members');
    expect(await statusOf(companies.get('00000000-0000-0000-0000-000000000000'))).toBe(404);
  });
});

describe('mock company update', () => {
  it('merges the patch and keeps the other fields', async () => {
    loginAs(EMAIL.recruiter);
    const mine = await companies.getMine();
    const updated = await companies.update(mine.id, { description: 'Nouvelle présentation.' });
    expect(updated).toMatchObject({ name: mine.name, sector: mine.sector, description: 'Nouvelle présentation.' });
  });

  it('answers 403 to a non-member, 404 for an unknown company, 400 for a bad value', async () => {
    const fret = db.companies[0];
    loginAs(EMAIL.otherRecruiter);
    expect(await statusOf(companies.update(fret.id, { name: 'Hack' }))).toBe(403);
    expect(await statusOf(companies.update('00000000-0000-0000-0000-000000000000', { name: 'x' }))).toBe(404);
    loginAs(EMAIL.recruiter);
    expect(await statusOf(companies.update(fret.id, { name: '' }))).toBe(400);
  });
});

describe('mock invitations', () => {
  it('creates a PENDING invitation valid for 7 days, without exposing the token', async () => {
    loginAs(EMAIL.recruiter);
    const company = await companies.getMine();
    const created = await companies.invite(company.id, 'colleague@demo.tn');
    expect(created.status).toBe('PENDING');
    expect(created).not.toHaveProperty('token');
    const days = (new Date(created.expiresAt).getTime() - Date.now()) / 86_400_000;
    expect(days).toBeGreaterThan(6.9);
    expect(days).toBeLessThanOrEqual(7);
    expect((await companies.invitations(company.id)).map((i) => i.email)).toContain('colleague@demo.tn');
  });

  it('answers 409 for a duplicate PENDING invitation, 400 for a bad email, 403 to a non-member', async () => {
    loginAs(EMAIL.recruiter);
    const company = await companies.getMine();
    expect(await statusOf(companies.invite(company.id, seededInvitation().email.toUpperCase()))).toBe(409);
    expect(await statusOf(companies.invite(company.id, 'not-an-email'))).toBe(400);
    loginAs(EMAIL.otherRecruiter);
    expect(await statusOf(companies.invite(company.id, 'x@demo.tn'))).toBe(403);
    expect(await statusOf(companies.invitations(company.id))).toBe(403);
  });

  it('reports an invitation as EXPIRED once past its date and allows inviting again', async () => {
    loginAs(EMAIL.recruiter);
    const company = await companies.getMine();
    seededInvitation().expiresAt = new Date(Date.now() - 1000).toISOString();
    const list = await companies.invitations(company.id);
    expect(list.find((i) => i.id === seededInvitation().id)?.status).toBe('EXPIRED');
    expect((await companies.invite(company.id, seededInvitation().email)).status).toBe('PENDING');
  });

  it('attaches the caller to the company on accept, then refuses to reuse the token', async () => {
    const newcomer = loginAsNew('RECRUITER');
    const company = await companies.acceptInvitation(seededInvitation().token);
    expect(company.members).toContain(newcomer.id);
    expect(seededInvitation().status).toBe('ACCEPTED');
    expect(await statusOf(companies.acceptInvitation(seededInvitation().token))).toBe(400);
  });

  it('answers 400 for an unknown token, 410 for an expired one, 409 when already in a company', async () => {
    loginAsNew('RECRUITER');
    expect(await statusOf(companies.acceptInvitation('nope'))).toBe(400);
    seededInvitation().expiresAt = new Date(Date.now() - 1000).toISOString();
    expect(await statusOf(companies.acceptInvitation(seededInvitation().token))).toBe(410);
    seededInvitation().expiresAt = new Date(Date.now() + 86_400_000).toISOString();
    loginAs(EMAIL.otherRecruiter);
    expect(await statusOf(companies.acceptInvitation(seededInvitation().token))).toBe(409);
  });

  it('is reserved to recruiters', async () => {
    loginAs(EMAIL.candidate);
    expect(await statusOf(companies.acceptInvitation(seededInvitation().token))).toBe(403);
  });
});
