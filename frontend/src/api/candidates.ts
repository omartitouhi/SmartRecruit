import type { CandidateProfile, CandidateView, CVParsingResult, Education, Experience } from '../types';
import { apiClient, isMocked } from './client';
import { mockCandidatesApi } from './mock/handlers';

/** Corps de PUT /users/candidates/me — cvUrl et cvParsedAt sont gérés par l'upload et l'IA. */
export interface CandidateProfileRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  location: string | null;
  skills: string[];
  experiences: Experience[];
  education: Education[];
  languages: string[];
}

export const CV_MAX_BYTES = 5 * 1024 * 1024;

export interface CandidatesApi {
  /** 404 tant que le candidat n'a pas créé son profil. */
  getMyProfile(): Promise<CandidateProfile>;
  saveMyProfile(request: CandidateProfileRequest): Promise<CandidateProfile>;
  /** `onProgress` reçoit un pourcentage (0-100) pendant l'envoi. L'upload remet cvAnalysisStatus à PENDING. */
  uploadCv(file: File, onProgress?: (percent: number) => void): Promise<CandidateProfile>;
  /** Données extraites du dernier CV analysé, à valider ; 404 sans CV analysé, 503 si l'IA est indisponible. */
  cvSuggestions(): Promise<CVParsingResult>;
  downloadCv(candidateId: string): Promise<Blob>;
  deleteMyAccount(): Promise<void>;
  /** Vue recruteur / admin d'un candidat. */
  getCandidate(candidateId: string): Promise<CandidateView>;
}

// user-service — CandidateProfileController, CandidateCvController, CandidateDirectoryController.
export const httpCandidatesApi: CandidatesApi = {
  async getMyProfile() {
    return (await apiClient.get<CandidateProfile>('/users/candidates/me')).data;
  },
  async saveMyProfile(request) {
    return (await apiClient.put<CandidateProfile>('/users/candidates/me', request)).data;
  },
  async uploadCv(file, onProgress) {
    const form = new FormData();
    form.append('file', file);
    return (
      await apiClient.post<CandidateProfile>('/users/candidates/me/cv', form, {
        onUploadProgress: (e) => e.total && onProgress?.(Math.round((e.loaded / e.total) * 100)),
      })
    ).data;
  },
  async cvSuggestions() {
    return (await apiClient.get<CVParsingResult>('/users/candidates/me/cv-suggestions')).data;
  },
  async downloadCv(candidateId) {
    return (await apiClient.get<Blob>(`/users/candidates/${candidateId}/cv`, { responseType: 'blob' })).data;
  },
  async deleteMyAccount() {
    await apiClient.delete('/users/candidates/me', { params: { confirm: true } });
  },
  async getCandidate(candidateId) {
    return (await apiClient.get<CandidateView>(`/users/candidates/${candidateId}`)).data;
  },
};

export const candidatesApi: CandidatesApi = isMocked('candidates') ? mockCandidatesApi : httpCandidatesApi;

export function fullName(candidate: CandidateView) {
  return candidate.deleted ? 'Compte supprimé' : `${candidate.firstName} ${candidate.lastName}`;
}
