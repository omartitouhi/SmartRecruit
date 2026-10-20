import type { JobSummary } from '../api/jobs';
import type { Application, CandidateProfile, Company, Invitation, MatchScore, PaginatedResponse } from '../types';

export const makeProfile = (overrides: Partial<CandidateProfile> = {}): CandidateProfile => ({
  id: 'candidate-id',
  firstName: 'Amira',
  lastName: 'Trabelsi',
  email: 'amira@demo.tn',
  phone: null,
  location: 'Tunis',
  skills: ['Java'],
  experiences: [],
  education: [],
  languages: ['Français'],
  cvUrl: null,
  cvParsedAt: null,
  cvAnalysisStatus: 'NONE',
  createdAt: '2026-09-01T09:00:00Z',
  updatedAt: '2026-09-01T09:00:00Z',
  deleted: false,
  ...overrides,
});

export const makeJob = (overrides: Partial<JobSummary> = {}): JobSummary => ({
  id: 'job-1',
  companyId: 'company-1',
  title: 'Développeur backend',
  description: 'Vous construisez nos services.',
  requiredSkills: ['Java', 'Kubernetes'],
  experienceLevel: 'SENIOR',
  contractType: 'CDI',
  location: 'Tunis',
  status: 'PUBLISHED',
  publishedAt: '2026-10-01T09:00:00Z',
  updatedAt: '2026-10-01T09:00:00Z',
  moderationReason: null,
  companyName: 'Carthage Fret',
  applicationsCount: null,
  ...overrides,
});

export const makeMatch = (score = 72.5): MatchScore => ({
  score,
  criteria: [
    { label: 'Compétences techniques', weight: 0.5, contribution: 40, explanation: '4 des 5 compétences requises.' },
    { label: 'Expérience', weight: 0.3, contribution: 22.5, explanation: '4 ans pour un niveau SENIOR.' },
    { label: 'Formation', weight: 0.1, contribution: 10, explanation: 'Master.' },
    { label: 'Langues', weight: 0.1, contribution: 0, explanation: 'Anglais manquant.' },
  ],
});

export const makeApplication = (overrides: Partial<Application> = {}): Application => ({
  id: 'app-1',
  jobId: 'job-1',
  candidateId: 'candidate-id',
  status: 'RECEIVED',
  matchScore: makeMatch(),
  appliedAt: '2026-10-05T09:00:00Z',
  statusHistory: [{ status: 'RECEIVED', changedAt: '2026-10-05T09:00:00Z', changedBy: 'candidate-id' }],
  ...overrides,
});

export const makeCompany = (overrides: Partial<Company> = {}): Company => ({
  id: 'company-1',
  name: 'Carthage Fret',
  sector: 'Logistique',
  description: 'Transitaire.',
  logoUrl: null,
  members: ['recruiter-id'],
  ...overrides,
});

export const makeInvitation = (overrides: Partial<Invitation> = {}): Invitation => ({
  id: 'inv-1',
  email: 'colleague@demo.tn',
  status: 'PENDING',
  expiresAt: '2026-10-27T09:00:00Z',
  createdAt: '2026-10-20T09:00:00Z',
  ...overrides,
});

export const page = <T,>(content: T[], overrides: Partial<PaginatedResponse<T>> = {}): PaginatedResponse<T> => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
  ...overrides,
});
