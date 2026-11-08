// Entités par service — DATA_MODEL.md §3. Un type TypeScript par entité exposée par
// l'API ; les champs jamais exposés (ex. passwordHash) sont volontairement absents.

import type {
  AccountStatus,
  ApplicationStatus,
  ContractType,
  CvAnalysisStatus,
  ExperienceLevel,
  FunnelStatus,
  InterviewMode,
  InvitationStatus,
  JobStatus,
  Role,
  WorkMode,
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
  /** Remplace l'ancienne heuristique « cvUrl sans cvParsedAt ». */
  cvAnalysisStatus: CvAnalysisStatus;
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

/** Vue d'une entreprise par un tiers (GET /users/companies/{id}) : sans la liste des membres. */
export type CompanyPublic = Omit<Company, 'members'>;

/** Invitation d'un recruteur dans une entreprise ; le jeton n'est jamais renvoyé par l'API. */
export interface Invitation {
  id: string;
  email: string;
  status: InvitationStatus;
  expiresAt: string;
  createdAt?: string;
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
  updatedAt: string;
  /** Motif de modération, visible du propriétaire et de l'admin seulement. */
  moderationReason: string | null;
  /** EXT-JOB-01 — jamais null dans les réponses (ON_SITE par défaut). */
  workMode: WorkMode;
  /** Salaire brut mensuel en TND. */
  salaryMin: number | null;
  salaryMax: number | null;
  /** Dernier jour pour postuler, `YYYY-MM-DD` (inclus, UTC) ; l'offre est clôturée le lendemain. */
  applicationDeadline: string | null;
  /** Nombre de postes, 1 par défaut. */
  positions: number;
}

// 3.4 application-service
export interface StatusHistoryEntry {
  status: ApplicationStatus;
  changedAt: string;
  changedBy: string;
  /** Explication adressée au candidat par le recruteur (EXT-APP-01). */
  message?: string | null;
}

/** Entretien planifié (EXT-APP-02). `location` = adresse ou lien de visio. */
export interface Interview {
  scheduledAt: string;
  durationMinutes: number;
  mode: InterviewMode;
  location: string | null;
}

/** Corps de planification d'un entretien ; `durationMinutes` vaut 60 par défaut. */
export interface InterviewRequest {
  scheduledAt: string;
  durationMinutes?: number;
  mode: InterviewMode;
  location?: string | null;
}

/** GET /applications/interviews/upcoming (recruteur). */
export interface UpcomingInterview extends Interview {
  applicationId: string;
  jobId: string;
  candidateId: string;
}

/** Note interne d'équipe sur une candidature (EXT-APP-03), jamais visible du candidat. */
export interface ApplicationNote {
  id: string;
  applicationId: string;
  authorId: string;
  content: string;
  rating: number | null;
  createdAt: string;
}

export interface Application {
  id: string;
  jobId: string;
  candidateId: string;
  status: ApplicationStatus;
  matchScore: MatchScore | null;
  appliedAt: string;
  statusHistory: StatusHistoryEntry[];
  /** Lettre de motivation facultative (EXT-APP-01). */
  coverLetter: string | null;
  interview: Interview | null;
  /** Vue recruteur/admin seulement : null pour le candidat (EXT-APP-03). */
  notesCount: number | null;
  averageRating: number | null;
}

// 3.5 dashboard-service
/** Actions journalisées — API_CONTRACTS.md §5. */
export const AUDIT_ACTIONS = [
  'CV_VIEWED',
  'PROFILE_VIEWED',
  'APPLICATION_STATUS_CHANGED',
  'USER_STATUS_CHANGED',
  'JOB_MODERATED',
  'INTERVIEW_SCHEDULED',
] as const;

/** Candidatures par statut, hors retirées : l'entonnoir (EXT-DASH-01). */
export type ApplicationsByStatus = Record<FunnelStatus, number>;

/** GET /dashboard/stats/global. Une source indisponible donne null (ou 0 pour un compteur). */
export interface GlobalStats {
  activeJobs: number;
  totalApplications: number;
  totalUsers: number;
  usersByRole: Record<Role, number>;
  averageMatchScore: number | null;
  applicationsByStatus: ApplicationsByStatus | null;
  /** Jours entre la candidature et la décision (acceptée ou non retenue), en moyenne. */
  averageDecisionDays: number | null;
  /** Offres publiées dont la date limite tombe dans les 7 jours. */
  jobsClosingSoon: number | null;
}

/** GET /dashboard/stats/company/{id}. `acceptanceRate` : part des candidatures décidées qui sont acceptées (0 à 1). */
export interface CompanyStats {
  publishedJobs: number;
  applicationsReceived: number;
  acceptedApplications: number;
  acceptanceRate: number | null;
  applicationsByStatus: ApplicationsByStatus | null;
  averageDecisionDays: number | null;
  upcomingInterviews: number | null;
  jobsClosingSoon: number | null;
}

export interface AuditLogEntry {
  id: string;
  actorId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  timestamp: string;
}
