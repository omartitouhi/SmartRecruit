import type { Application, ApplicationStatus, MatchScore, SkillGapResult } from '../types';
import { apiClient, USE_MOCKS } from './client';
import { mockApplicationsApi } from './mock/handlers';

export interface ApplicationsApi {
  apply(jobId: string): Promise<Application>;
  mine(): Promise<Application[]>;
  forJob(jobId: string): Promise<Application[]>;
  /** Transitions autorisées : APPLICATION_STATUS_TRANSITIONS (sinon 400). */
  updateStatus(applicationId: string, status: ApplicationStatus): Promise<Application>;
  /** Score expliqué d'un candidat pour une offre, avant de postuler. */
  matchPreview(candidateId: string, jobId: string): Promise<MatchScore>;
  skillGap(candidateId: string, jobId: string): Promise<SkillGapResult>;
}

const httpApplicationsApi: ApplicationsApi = {
  // application-service — contrats à confirmer (DTO ApplicationResponse présent, contrôleur à venir).
  async apply(jobId) {
    return (await apiClient.post<Application>('/applications', { jobId })).data;
  },
  async mine() {
    return (await apiClient.get<Application[]>('/applications/me')).data;
  },
  async forJob(jobId) {
    return (await apiClient.get<Application[]>('/applications', { params: { jobId } })).data;
  },
  async updateStatus(applicationId, status) {
    return (await apiClient.patch<Application>(`/applications/${applicationId}/status`, { status })).data;
  },
  // ai-service — DATA_MODEL.md §4.1 (corps de requête à confirmer) et §4.4.
  async matchPreview(candidateId, jobId) {
    return (await apiClient.post<MatchScore>('/ai/matching/score', { candidateId, jobId })).data;
  },
  async skillGap(candidateId, jobId) {
    return (await apiClient.get<SkillGapResult>(`/ai/skill-gap/candidates/${candidateId}/jobs/${jobId}`)).data;
  },
};

export const applicationsApi: ApplicationsApi = USE_MOCKS ? mockApplicationsApi : httpApplicationsApi;
