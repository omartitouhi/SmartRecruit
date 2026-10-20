import type {
  ContractType,
  ExperienceLevel,
  Job,
  JobStatus,
  PaginatedResponse,
  RecommendedJob,
} from '../types';
import { apiClient, isMocked } from './client';
import { mockJobsApi } from './mock/jobs';

/** JobResponse enrichi (API_CONTRACTS.md §2). `applicationsCount` n'est renseigné qu'avec `companyId=me`. */
export interface JobSummary extends Job {
  companyName: string | null;
  applicationsCount: number | null;
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
};

export const jobsApi: JobsApi = isMocked('jobs') ? mockJobsApi : httpJobsApi;
