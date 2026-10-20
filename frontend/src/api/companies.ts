import type { Company, CompanyPublic, Invitation } from '../types';
import { apiClient, isMocked } from './client';
import { mockCompaniesApi } from './mock/handlers';

export interface CompanyRequest {
  name: string;
  sector: string;
  description: string;
  logoUrl?: string | null;
}

export interface CompaniesApi {
  /** Le créateur devient premier membre ; 409 s'il a déjà une entreprise. */
  create(request: CompanyRequest): Promise<Company>;
  /** Entreprise du recruteur connecté — 404 s'il n'en a pas encore. */
  getMine(): Promise<Company>;
  /** Vue publique (sans les membres), pour tout utilisateur connecté. */
  get(companyId: string): Promise<CompanyPublic>;
  /** Merge patch : seuls les champs fournis changent. 403 pour un non-membre. */
  update(companyId: string, patch: Partial<CompanyRequest>): Promise<Company>;
  /** 201 avec une invitation PENDING valable 7 jours ; 409 si une invitation PENDING existe déjà pour cet email. */
  invite(companyId: string, email: string): Promise<Invitation>;
  invitations(companyId: string): Promise<Invitation[]>;
  /** Rattache l'appelant à l'entreprise ; 409 s'il en a déjà une, 400/410 jeton invalide ou expiré. */
  acceptInvitation(token: string): Promise<Company>;
}

// user-service — API_CONTRACTS.md §1.
export const httpCompaniesApi: CompaniesApi = {
  async create(request) {
    return (await apiClient.post<Company>('/users/companies', request)).data;
  },
  async getMine() {
    return (await apiClient.get<Company>('/users/companies/me')).data;
  },
  async get(companyId) {
    return (await apiClient.get<CompanyPublic>(`/users/companies/${companyId}`)).data;
  },
  async update(companyId, patch) {
    return (await apiClient.patch<Company>(`/users/companies/${companyId}`, patch)).data;
  },
  async invite(companyId, email) {
    return (await apiClient.post<Invitation>(`/users/companies/${companyId}/invitations`, { email })).data;
  },
  async invitations(companyId) {
    return (await apiClient.get<Invitation[]>(`/users/companies/${companyId}/invitations`)).data;
  },
  async acceptInvitation(token) {
    return (await apiClient.post<Company>('/users/companies/invitations/accept', { token })).data;
  },
};

export const companiesApi: CompaniesApi = isMocked('companies') ? mockCompaniesApi : httpCompaniesApi;
