import type {
  ContractType,
  ExperienceLevel,
  Job,
  JobStatus,
  PaginatedResponse,
  RecommendedJob,
} from '../types';
import { apiClient, isMocked } from './client';
import { mockJobsApi } from './mock/handlers';

/**
 * Offre enrichie du nom de l'entreprise, comme RecommendedJob (§4.7).
 * Contrat à confirmer avec job-service : seul GET /jobs/recommendations existe aujourd'hui.
 */
export interface JobSummary extends Job {
  companyName: string | null;
}

export interface JobSearchParams {
  q?: string;
  location?: string;
  contractType?: ContractType | '';
  experienceLevel?: ExperienceLevel | '';
  companyId?: string;
  page?: number;
  size?: number;
}

/** Corps de POST /jobs (JobCreateRequest) — le statut part toujours en DRAFT. */
export interface JobCreateRequest {
  companyId: string;
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
  updateStatus(jobId: string, status: JobStatus): Promise<Job>;
}

const clean = (params: object) =>
  Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined && v !== null));

const httpJobsApi: JobsApi = {
  // Existe (BE-REC-01).
  async recommendations(limit = 10) {
    return (await apiClient.get<RecommendedJob[]>('/jobs/recommendations', { params: { limit } })).data;
  },
  // Contrat à confirmer.
  async search(params) {
    return (await apiClient.get<PaginatedResponse<JobSummary>>('/jobs', { params: clean(params) })).data;
  },
  // Contrat à confirmer.
  async get(jobId) {
    return (await apiClient.get<JobSummary>(`/jobs/${jobId}`)).data;
  },
  // DTO JobCreateRequest présent dans job-service, contrôleur à venir.
  async create(request) {
    return (await apiClient.post<Job>('/jobs', request)).data;
  },
  // Contrat à confirmer.
  async updateStatus(jobId, status) {
    return (await apiClient.patch<Job>(`/jobs/${jobId}/status`, { status })).data;
  },
};

export const jobsApi: JobsApi = isMocked('jobs') ? mockJobsApi : httpJobsApi;
