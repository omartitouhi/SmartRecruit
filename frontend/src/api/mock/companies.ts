import { HttpError } from '../../lib/errors';
import type { Company, Invitation } from '../../types';
import type { CompaniesApi, CompanyRequest } from '../companies';
import type { MockDb } from './seed';
import { db, save, delay, now, uuid, clone, currentAccount, requireRole, recruiterCompany } from './store';

function validateCompany(request: Partial<CompanyRequest>) {
  const e: Record<string, string> = {};
  const { name, sector, description, logoUrl } = request;
  if (name !== undefined && (!name.trim() || name.length > 150)) e.name = 'Le nom est obligatoire (150 caractères maximum)';
  if (sector !== undefined && (!sector.trim() || sector.length > 100)) e.sector = 'Le secteur est obligatoire (100 caractères maximum)';
  if (description !== undefined && description.length > 5000) e.description = '5000 caractères maximum';
  if (logoUrl && (!/^https?:\/\//.test(logoUrl) || logoUrl.length > 500)) e.logoUrl = 'L’URL du logo doit commencer par http ou https';
  if (Object.keys(e).length) throw new HttpError(400, 'Requête invalide', e);
}

function memberCompany(accountId: string, companyId: string) {
  const company = db.companies.find((c) => c.id === companyId);
  if (!company) throw new HttpError(404, 'Entreprise introuvable.');
  if (!company.members.includes(accountId)) throw new HttpError(403);
  return company;
}

const INVITATION_TTL_MS = 7 * 86400000;
const publicInvitation = ({ companyId: _c, token: _t, ...invitation }: MockDb['invitations'][number]): Invitation => {
  void _c;
  void _t;
  const expired = invitation.status === 'PENDING' && new Date(invitation.expiresAt).getTime() < Date.now();
  return { ...invitation, status: expired ? 'EXPIRED' : invitation.status };
};

export const mockCompaniesApi: CompaniesApi = {
  async create(request) {
    await delay();
    const account = requireRole('RECRUITER');
    if (recruiterCompany(account.id)) throw new HttpError(409, 'Vous avez déjà une entreprise.');
    validateCompany({ ...request, name: request.name ?? '', sector: request.sector ?? '' });
    const company: Company = {
      id: uuid(),
      name: request.name,
      sector: request.sector,
      description: request.description ?? '',
      logoUrl: request.logoUrl || null,
      members: [account.id],
    };
    db.companies.push(company);
    save();
    return clone(company);
  },

  async getMine() {
    await delay();
    const account = requireRole('RECRUITER');
    const company = recruiterCompany(account.id);
    if (!company) throw new HttpError(404, 'Aucune entreprise rattachée à ce compte.');
    return clone(company);
  },

  async get(companyId) {
    await delay(120);
    currentAccount();
    const company = db.companies.find((c) => c.id === companyId);
    if (!company) throw new HttpError(404, 'Entreprise introuvable.');
    const { members: _members, ...publicView } = company;
    void _members;
    return clone(publicView);
  },

  async update(companyId, patch) {
    await delay();
    const account = requireRole('RECRUITER');
    const company = memberCompany(account.id, companyId);
    validateCompany(patch);
    const { logoUrl, ...fields } = patch;
    Object.assign(company, fields);
    if (logoUrl !== undefined) company.logoUrl = logoUrl || null;
    save();
    return clone(company);
  },

  async invite(companyId, email) {
    await delay();
    const account = requireRole('RECRUITER');
    memberCompany(account.id, companyId);
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      throw new HttpError(400, 'Requête invalide', { email: 'L’email n’est pas valide' });
    }
    const duplicate = db.invitations.some(
      (i) => i.companyId === companyId && i.email.toLowerCase() === email.toLowerCase() && publicInvitation(i).status === 'PENDING',
    );
    if (duplicate) throw new HttpError(409, 'Une invitation est déjà en attente pour cet email.');
    const stamp = now();
    const invitation = {
      id: uuid(),
      companyId,
      token: uuid(),
      email,
      status: 'PENDING' as const,
      createdAt: stamp,
      expiresAt: new Date(Date.now() + INVITATION_TTL_MS).toISOString(),
    };
    db.invitations.push(invitation);
    save();
    // No mail in demo mode: the link a real recipient would receive goes to the console.
    console.info(`[mock] invitation pour ${email} : ${location.origin}/accept-invitation?token=${invitation.token}`);
    return publicInvitation(invitation);
  },

  async invitations(companyId) {
    await delay();
    const account = requireRole('RECRUITER');
    memberCompany(account.id, companyId);
    return db.invitations
      .filter((i) => i.companyId === companyId)
      .map(publicInvitation)
      .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
  },

  async acceptInvitation(token) {
    await delay();
    const account = requireRole('RECRUITER');
    const invitation = db.invitations.find((i) => i.token === token);
    if (!invitation || invitation.status === 'ACCEPTED') throw new HttpError(400, 'Invitation invalide.');
    if (publicInvitation(invitation).status === 'EXPIRED') throw new HttpError(410, 'Cette invitation a expiré.');
    if (recruiterCompany(account.id)) throw new HttpError(409, 'Vous avez déjà une entreprise.');
    const company = db.companies.find((c) => c.id === invitation.companyId);
    if (!company) throw new HttpError(404, 'Entreprise introuvable.');
    company.members.push(account.id);
    invitation.status = 'ACCEPTED';
    save();
    return clone(company);
  },
};
