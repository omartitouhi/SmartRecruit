// Entités par service — DATA_MODEL.md §3. Un type TypeScript par entité exposée par
// l'API ; les champs jamais exposés (ex. passwordHash) sont volontairement absents.

import type {
  AccountStatus,
  ApplicationStatus,
  ContractType,
  ExperienceLevel,
  JobStatus,
  Role,
} from './enums';
import type { MatchScore } from './contracts';

// 3.1 auth-service
export interface Account {
  id: string;
  email: string;
  role: Role;
  status: AccountStatus;
  createdAt: string;
  updatedAt: string;
}

// 3.2 user-service
export interface Experience {
  title: string;
  company: string;
  startDate: string;
  endDate: string | null;
  description: string;
}

export interface Education {
  degree: string;
  institution: string;
  year: number;
}

export interface CandidateProfile {
  id: string;
  firstName: string;
  lastName: string;
  /** Email de contact (visible par les recruteurs), pas forcément celui du compte. */
  email: string;
  phone: string | null;
  location: string | null;
  skills: string[];
  experiences: Experience[];
  education: Education[];
  languages: string[];
  cvUrl: string | null;
  cvParsedAt: string | null;
  createdAt: string;
  updatedAt: string;
  deleted: false;
}

/** Réponse renvoyée à la place d'un profil après exercice du droit à l'oubli (BE-USER-04). */
export interface DeletedCandidate {
  id: string;
  deleted: true;
}

/** Profil tel que vu par un recruteur ou un admin : il peut avoir été supprimé. */
export type CandidateView = CandidateProfile | DeletedCandidate;

export interface Company {
  id: string;
  name: string;
  sector: string;
  description: string;
  logoUrl: string | null;
  members: string[];
}

// 3.3 job-service
export interface Job {
  id: string;
  companyId: string;
  title: string;
  description: string;
  requiredSkills: string[];
  experienceLevel: ExperienceLevel;
  contractType: ContractType;
  location: string;
  status: JobStatus;
  publishedAt: string | null;
}

// 3.4 application-service
export interface StatusHistoryEntry {
  status: ApplicationStatus;
  changedAt: string;
  changedBy: string;
}

export interface Application {
  id: string;
  jobId: string;
  candidateId: string;
  status: ApplicationStatus;
  matchScore: MatchScore | null;
  appliedAt: string;
  statusHistory: StatusHistoryEntry[];
}

// 3.5 dashboard-service
export interface AuditLogEntry {
  id: string;
  actorId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  timestamp: string;
}
