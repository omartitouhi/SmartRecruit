// Mirrors DATA_MODEL.md §2 (smartrecruit-backend). Keep these union types in sync
// with the Java enums (backend) and the Python Enum/Literal types (smartrecruit-ia).

export type Role = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';

export type AccountStatus = 'ACTIVE' | 'DISABLED' | 'DELETED';

export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'MODERATED';

export type ContractType = 'CDI' | 'CDD' | 'STAGE' | 'ALTERNANCE' | 'FREELANCE';

export type ExperienceLevel = 'JUNIOR' | 'INTERMEDIATE' | 'SENIOR' | 'LEAD';

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
