import type {
  Application,
  ApplicationNote,
  ApplicationStatus,
  InterviewRequest,
  MatchScore,
  SkillGapResult,
  UpcomingInterview,
} from '../types';
import { apiClient, isMocked } from './client';
import { mockApplicationsApi } from './mock/applications';

/** Options d'un changement de statut (EXT-APP-01/02) : `interview` seulement vers INTERVIEW. */
export interface StatusChangeOptions {
  /** Explication adressée au candidat, 2000 caractères au plus. */
  message?: string;
  interview?: InterviewRequest;
}

export interface NoteRequest {
  content: string;
  /** 1..5, facultative. */
  rating?: number | null;
}

export interface ApplicationsApi {
  /** Lettre de motivation facultative (5000 caractères) ; 409 si la date limite de l'offre est passée. */
  apply(jobId: string, coverLetter?: string): Promise<Application>;
  mine(): Promise<Application[]>;
  /** Triées par score décroissant (sans score en dernier), sans les candidatures retirées. */
  forJob(jobId: string): Promise<Application[]>;
  /** Recruteur propriétaire. Transitions autorisées : APPLICATION_STATUS_TRANSITIONS, jamais WITHDRAWN (sinon 400). */
  updateStatus(applicationId: string, status: ApplicationStatus, options?: StatusChangeOptions): Promise<Application>;
  /** Planifie ou replanifie l'entretien ; 409 si la candidature n'est pas au statut INTERVIEW. */
  scheduleInterview(applicationId: string, interview: InterviewRequest): Promise<Application>;
  /** Recruteur : entretiens à venir de son entreprise, plus proches d'abord (50 au plus). */
  upcomingInterviews(): Promise<UpcomingInterview[]>;
  /** Notes d'équipe, de la plus ancienne à la plus récente (recruteur membre ou admin). */
  notes(applicationId: string): Promise<ApplicationNote[]>;
  addNote(applicationId: string, note: NoteRequest): Promise<ApplicationNote>;
  /** Remplace le contenu et l'évaluation ; seulement par l'auteur de la note (403 sinon). */
  updateNote(applicationId: string, noteId: string, note: NoteRequest): Promise<ApplicationNote>;
  /** Seulement par l'auteur de la note (403 sinon). */
  deleteNote(applicationId: string, noteId: string): Promise<void>;
  /** Le candidat retire sa candidature (204) ; seulement depuis RECEIVED ou IN_REVIEW, sinon 409. */
  withdraw(applicationId: string): Promise<void>;
  /** Score expliqué du candidat connecté pour une offre, avant de postuler ; 422 sans CV analysé. */
  matchPreview(jobId: string): Promise<MatchScore>;
  /** Compétences manquantes (ai-service). */
  skillGap(candidateId: string, jobId: string): Promise<SkillGapResult>;
}

// application-service — API_CONTRACTS.md §3.
export const httpApplicationsApi: ApplicationsApi = {
  async apply(jobId, coverLetter) {
    const body = coverLetter?.trim() ? { jobId, coverLetter: coverLetter.trim() } : { jobId };
    return (await apiClient.post<Application>('/applications', body)).data;
  },
  async mine() {
    return (await apiClient.get<Application[]>('/applications/me')).data;
  },
  async forJob(jobId) {
    return (await apiClient.get<Application[]>('/applications', { params: { jobId } })).data;
  },
  async updateStatus(applicationId, status, options = {}) {
    const body = {
      status,
      ...(options.message?.trim() ? { message: options.message.trim() } : {}),
      ...(options.interview ? { interview: options.interview } : {}),
    };
    return (await apiClient.patch<Application>(`/applications/${applicationId}/status`, body)).data;
  },
  // EXT-APP-02 / EXT-APP-03 — API_CONTRACTS.md §7.3, §7.4.
  async scheduleInterview(applicationId, interview) {
    return (await apiClient.put<Application>(`/applications/${applicationId}/interview`, interview)).data;
  },
  async upcomingInterviews() {
    return (await apiClient.get<UpcomingInterview[]>('/applications/interviews/upcoming')).data;
  },
  async notes(applicationId) {
    return (await apiClient.get<ApplicationNote[]>(`/applications/${applicationId}/notes`)).data;
  },
  async addNote(applicationId, note) {
    return (await apiClient.post<ApplicationNote>(`/applications/${applicationId}/notes`, note)).data;
  },
  async updateNote(applicationId, noteId, note) {
    return (await apiClient.put<ApplicationNote>(`/applications/${applicationId}/notes/${noteId}`, note)).data;
  },
  async deleteNote(applicationId, noteId) {
    await apiClient.delete(`/applications/${applicationId}/notes/${noteId}`);
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
