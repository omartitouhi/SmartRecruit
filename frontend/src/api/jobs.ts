import type {
  ContractType,
  ExperienceLevel,
  Job,
  JobStatus,
  PaginatedResponse,
  RecommendedJob,
  WorkMode,
} from '../types';
import { apiClient, isMocked } from './client';
import { mockJobsApi } from './mock/jobs';

/** JobResponse enrichi (API_CONTRACTS.md §2). `applicationsCount` n'est renseigné qu'avec `companyId=me`. */
export interface JobSummary extends Job {
  companyName: string | null;
  applicationsCount: number | null;
  /** EXT-SAVED-01 : true/false pour un candidat (offre en favori), null pour les autres rôles. */
  saved: boolean | null;
}

export interface JobSearchParams {
  /** Cherche dans le titre et la description. */
  keyword?: string;
  location?: string;
  /** Au moins une des compétences doit être demandée par l'offre. */
  skills?: string[];
  contractType?: ContractType | '';
  experienceLevel?: ExperienceLevel | '';
  /** UUID d'une entreprise, ou `me` (recruteur : toutes les offres de son entreprise, tous statuts). */
  companyId?: string;
  /** Réservé à l'admin et à `companyId=me`. */
  status?: JobStatus | '';
  workMode?: WorkMode | '';
  /** Salaire mensuel minimum (TND) : une offre sans salaire est exclue. */
  minSalary?: number | '';
  page?: number;
  size?: number;
}

/** Corps de POST /jobs : l'entreprise est déduite du recruteur, l'offre part PUBLISHED. */
export interface JobCreateRequest {
  title: string;
  description: string;
  requiredSkills: string[];
  experienceLevel: ExperienceLevel;
  contractType: ContractType;
  location: string;
  /** EXT-JOB-01, facultatifs à la création — ON_SITE par défaut côté serveur. */
  workMode?: WorkMode;
  /** Salaire brut mensuel en TND, min ≤ max ; null = non communiqué. */
  salaryMin?: number | null;
  salaryMax?: number | null;
  /** `YYYY-MM-DD`, aujourd'hui ou plus tard ; null = pas de date limite. */
  applicationDeadline?: string | null;
  /** 1..100, 1 par défaut. */
  positions?: number;
}

export interface JobsApi {
  recommendations(limit?: number): Promise<RecommendedJob[]>;
  search(params: JobSearchParams): Promise<PaginatedResponse<JobSummary>>;
  get(jobId: string): Promise<JobSummary>;
  create(request: JobCreateRequest): Promise<Job>;
  /** Merge patch ; 403 autre entreprise, 409 si l'offre est CLOSED ou MODERATED. */
  update(jobId: string, patch: Partial<JobCreateRequest>): Promise<Job>;
  /** DRAFT→PUBLISHED, PUBLISHED→CLOSED, CLOSED→PUBLISHED ; autre transition 400, offre MODERATED 403. */
  updateStatus(jobId: string, status: JobStatus): Promise<Job>;
  /** Copie en brouillon (« (copie) »), sans date limite ; 409 si l'offre est MODERATED. */
  duplicate(jobId: string): Promise<Job>;
  /** Candidat : ajoute l'offre (PUBLISHED) à ses favoris, idempotent ; 404 sinon, 409 au-delà de 200. */
  save(jobId: string): Promise<void>;
  /** Candidat : retire l'offre de ses favoris, idempotent. */
  unsave(jobId: string): Promise<void>;
  /** Candidat : favoris encore PUBLISHED ou CLOSED, plus récemment ajoutés d'abord. */
  saved(): Promise<JobSummary[]>;
}

const clean = (params: object) =>
  Object.fromEntries(
    Object.entries(params)
      .map(([k, v]) => [k, Array.isArray(v) ? v.join(',') : v])
      .filter(([, v]) => v !== '' && v !== undefined && v !== null),
  );

// job-service — API_CONTRACTS.md §2.
export const httpJobsApi: JobsApi = {
  async recommendations(limit = 10) {
    return (await apiClient.get<RecommendedJob[]>('/jobs/recommendations', { params: { limit } })).data;
  },
  async search(params) {
    return (await apiClient.get<PaginatedResponse<JobSummary>>('/jobs', { params: clean(params) })).data;
  },
  async get(jobId) {
    return (await apiClient.get<JobSummary>(`/jobs/${jobId}`)).data;
  },
  async create(request) {
    return (await apiClient.post<Job>('/jobs', request)).data;
  },
  async update(jobId, patch) {
    return (await apiClient.patch<Job>(`/jobs/${jobId}`, patch)).data;
  },
  async updateStatus(jobId, status) {
    return (await apiClient.patch<Job>(`/jobs/${jobId}/status`, { status })).data;
  },
  // EXT-JOB-01 / EXT-SAVED-01 — API_CONTRACTS.md §7.1, §7.2.
  async duplicate(jobId) {
    return (await apiClient.post<Job>(`/jobs/${jobId}/duplicate`)).data;
  },
  async save(jobId) {
    await apiClient.put(`/jobs/${jobId}/saved`);
  },
  async unsave(jobId) {
    await apiClient.delete(`/jobs/${jobId}/saved`);
  },
  async saved() {
    return (await apiClient.get<JobSummary[]>('/jobs/saved')).data;
  },
};

export const jobsApi: JobsApi = isMocked('jobs') ? mockJobsApi : httpJobsApi;
