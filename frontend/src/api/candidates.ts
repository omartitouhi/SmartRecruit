import type { CandidateProfile, CandidateView, Education, Experience } from '../types';
import { apiClient, USE_MOCKS } from './client';
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
  uploadCv(file: File): Promise<CandidateProfile>;
  downloadCv(candidateId: string): Promise<Blob>;
  deleteMyAccount(): Promise<void>;
  /** Vue recruteur / admin d'un candidat. */
  getCandidate(candidateId: string): Promise<CandidateView>;
}

// user-service — CandidateProfileController, CandidateCvController, CandidateDirectoryController.
const httpCandidatesApi: CandidatesApi = {
  async getMyProfile() {
    return (await apiClient.get<CandidateProfile>('/users/candidates/me')).data;
  },
  async saveMyProfile(request) {
    return (await apiClient.put<CandidateProfile>('/users/candidates/me', request)).data;
  },
  async uploadCv(file) {
    const form = new FormData();
    form.append('file', file);
    return (await apiClient.post<CandidateProfile>('/users/candidates/me/cv', form)).data;
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

export const candidatesApi: CandidatesApi = USE_MOCKS ? mockCandidatesApi : httpCandidatesApi;

export function fullName(candidate: CandidateView) {
  return candidate.deleted ? 'Compte supprimé' : `${candidate.firstName} ${candidate.lastName}`;
}
