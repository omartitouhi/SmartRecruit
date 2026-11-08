import type { Application, ApplicationStatus, MatchScore, SkillGapResult } from '../types';
import { apiClient, isMocked } from './client';
import { mockApplicationsApi } from './mock/applications';

export interface ApplicationsApi {
  apply(jobId: string): Promise<Application>;
  mine(): Promise<Application[]>;
  /** Triées par score décroissant (sans score en dernier), sans les candidatures retirées. */
  forJob(jobId: string): Promise<Application[]>;
  /** Recruteur propriétaire. Transitions autorisées : APPLICATION_STATUS_TRANSITIONS, jamais WITHDRAWN (sinon 400). */
  updateStatus(applicationId: string, status: ApplicationStatus): Promise<Application>;
  /** Le candidat retire sa candidature (204) ; seulement depuis RECEIVED ou IN_REVIEW, sinon 409. */
  withdraw(applicationId: string): Promise<void>;
  /** Score expliqué du candidat connecté pour une offre, avant de postuler ; 422 sans CV analysé. */
  matchPreview(jobId: string): Promise<MatchScore>;
  /** Compétences manquantes (ai-service). */
  skillGap(candidateId: string, jobId: string): Promise<SkillGapResult>;
}

// application-service — API_CONTRACTS.md §3.
export const httpApplicationsApi: ApplicationsApi = {
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
  async withdraw(applicationId) {
    await apiClient.delete(`/applications/${applicationId}`);
  },
  async matchPreview(jobId) {
    return (await apiClient.get<MatchScore>('/applications/match-preview', { params: { jobId } })).data;
  },
  // ai-service — DATA_MODEL.md §4.4.
  async skillGap(candidateId, jobId) {
    return (await apiClient.get<SkillGapResult>(`/ai/skill-gap/candidates/${candidateId}/jobs/${jobId}`)).data;
  },
};

export const applicationsApi: ApplicationsApi = isMocked('applications') ? mockApplicationsApi : httpApplicationsApi;
