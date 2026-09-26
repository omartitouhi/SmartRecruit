// Contrats inter-services critiques — DATA_MODEL.md §4. Ce sont les réponses de
// smartrecruit-ia, consommées telles quelles par le frontend (via application-service
// pour MatchScore, directement pour les autres).

import type { ExperienceLevel } from './enums';

export interface MatchCriterion {
  label: string;
  weight: number;
  contribution: number;
  explanation: string;
}

/** Réponse de POST /api/v1/ai/matching/score — porte l'explicabilité du score (slide 7). */
export interface MatchScore {
  score: number;
  criteria: MatchCriterion[];
}

export interface ParsedExperience {
  title: string;
  company: string;
  startDate: string;
  endDate: string | null;
  description: string;
}

export interface ParsedEducation {
  degree: string;
  institution: string;
  year: number;
}

/** Réponse de POST /api/v1/ai/cv-parsing/parse */
export interface CVParsingResult {
  skills: string[];
  experiences: ParsedExperience[];
  education: ParsedEducation[];
  languages: string[];
}

/** Réponse de POST /api/v1/ai/job-analysis/analyze */
export interface JobAnalysisResult {
  requiredSkills: string[];
  experienceLevel: ExperienceLevel;
  seniority: ExperienceLevel;
}

/** Réponse de GET /api/v1/ai/skill-gap/candidates/{id}/jobs/{jobId} */
export interface SkillGapResult {
  missingSkills: string[];
}
