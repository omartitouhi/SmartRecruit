// Mirrors docs/DATA_MODEL.md §2. Keep these union types in sync
// with the Java enums (backend) and the Python Enum/Literal types (ai-service).

export type Role = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';

export type AccountStatus = 'ACTIVE' | 'DISABLED' | 'DELETED';

/** Avancement de l'analyse IA du CV (user-service, API_CONTRACTS.md §1). */
export type CvAnalysisStatus = 'NONE' | 'PENDING' | 'DONE' | 'FAILED';

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'EXPIRED';

export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'MODERATED';

// Transitions de statut d'une offre voulues par le recruteur — API_CONTRACTS.md §2. MODERATED n'en a aucune.
export const JOB_STATUS_TRANSITIONS: Record<JobStatus, JobStatus[]> = {
  DRAFT: ['PUBLISHED'],
  PUBLISHED: ['CLOSED'],
  CLOSED: ['PUBLISHED'],
  MODERATED: [],
};

export type ContractType = 'CDI' | 'CDD' | 'STAGE' | 'ALTERNANCE' | 'FREELANCE';

export type ExperienceLevel = 'JUNIOR' | 'INTERMEDIATE' | 'SENIOR' | 'LEAD';

/** Présentiel, hybride ou télétravail (EXT-JOB-01, API_CONTRACTS.md §7.1). */
export type WorkMode = 'ON_SITE' | 'HYBRID' | 'REMOTE';

/** Format d'un entretien planifié (EXT-APP-02, API_CONTRACTS.md §7.3). */
export type InterviewMode = 'VIDEO' | 'ON_SITE' | 'PHONE';

export type ApplicationStatus =
  | 'RECEIVED'
  | 'IN_REVIEW'
  | 'INTERVIEW'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'WITHDRAWN';

// Transitions autorisées pour ApplicationStatus — DATA_MODEL.md §2 (BE-APP-04).
export const APPLICATION_STATUS_TRANSITIONS: Record<ApplicationStatus, ApplicationStatus[]> = {
  RECEIVED: ['IN_REVIEW', 'WITHDRAWN'],
  IN_REVIEW: ['INTERVIEW', 'WITHDRAWN'],
  INTERVIEW: ['ACCEPTED', 'REJECTED'],
  ACCEPTED: [],
  REJECTED: [],
  WITHDRAWN: [],
};

/** Statuts suivis par l'entonnoir de recrutement (EXT-DASH-01) : tout sauf WITHDRAWN. */
export const FUNNEL_STATUSES = ['RECEIVED', 'IN_REVIEW', 'INTERVIEW', 'ACCEPTED', 'REJECTED'] as const;
export type FunnelStatus = (typeof FUNNEL_STATUSES)[number];
