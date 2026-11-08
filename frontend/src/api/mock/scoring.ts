// Stand-in for ai-service's matching (§4.1) so the demo shows real explained scores.
// Weights mirror ai-service (scoring.py): skills 50 %, experience 30 %, education 20 %.

import type { CandidateProfile, ExperienceLevel, Job, MatchCriterion, MatchScore } from '../../types';

const LEVEL_YEARS: Record<ExperienceLevel, { min: number; max: number; label: string }> = {
  JUNIOR: { min: 0, max: 2, label: 'Junior (0 à 2 ans)' },
  INTERMEDIATE: { min: 2, max: 4, label: 'Confirmé (2 à 4 ans)' },
  SENIOR: { min: 3, max: 5, label: 'Senior (3 à 5 ans)' },
  LEAD: { min: 6, max: 40, label: 'Lead (6 ans et plus)' },
};

const norm = (s: string) =>
  s
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .trim();

const round1 = (n: number) => Math.round(n * 10) / 10;

function yearsOfExperience(profile: CandidateProfile, today = new Date()) {
  let months = 0;
  for (const exp of profile.experiences) {
    const [sy, sm] = exp.startDate.split('-').map(Number);
    const end = exp.endDate ? exp.endDate.split('-').map(Number) : [today.getFullYear(), today.getMonth() + 1];
    months += Math.max(0, (end[0] - sy) * 12 + (end[1] - sm));
  }
  return months / 12;
}

function list(items: string[]) {
  if (items.length <= 1) return items.join('');
  return `${items.slice(0, -1).join(', ')} et ${items[items.length - 1]}`;
}

export function missingSkills(profile: CandidateProfile, job: Job) {
  const have = new Set(profile.skills.map(norm));
  return job.requiredSkills.filter((s) => !have.has(norm(s)));
}

export function computeMatch(profile: CandidateProfile, job: Job): MatchScore {
  const criteria: MatchCriterion[] = [];

  // Skills — 50 %
  const missing = missingSkills(profile, job);
  const required = job.requiredSkills.length || 1;
  const matched = job.requiredSkills.length - missing.length;
  const present = job.requiredSkills.filter((s) => !missing.includes(s));
  criteria.push({
    label: 'Compétences techniques',
    weight: 0.5,
    contribution: round1((50 * matched) / required),
    explanation:
      `${matched} des ${job.requiredSkills.length} compétences requises sont dans le profil` +
      (present.length ? ` (${present.slice(0, 6).join(', ')}${present.length > 6 ? '…' : ''})` : '') +
      '.' +
      (missing.length ? ` ${missing.length > 1 ? 'Manquent' : 'Manque'} : ${list(missing)}.` : ''),
  });

  // Experience — 30 %
  const years = yearsOfExperience(profile);
  const level = LEVEL_YEARS[job.experienceLevel];
  let ratio: number;
  if (years < level.min) ratio = level.min === 0 ? 1 : Math.max(0.15, years / level.min);
  else if (years > level.max) ratio = 0.9;
  else ratio = 1;
  const yearsLabel = years < 1 ? 'moins d’un an' : `${Math.floor(years)} an${Math.floor(years) > 1 ? 's' : ''}`;
  criteria.push({
    label: 'Expérience',
    weight: 0.3,
    contribution: round1(30 * ratio),
    explanation:
      `${yearsLabel[0].toUpperCase()}${yearsLabel.slice(1)} d’expérience pour un niveau ${level.label}.` +
      (ratio > 0.99 ? '' : years < level.min ? ' En dessous du niveau demandé.' : ' Profil plus expérimenté que demandé.'),
  });

  // Education — 20 %
  const best = [...profile.education].sort((a, b) => b.year - a.year)[0];
  let eduRatio = 0;
  let eduText = 'Aucune formation renseignée dans le profil.';
  if (best) {
    const d = norm(best.degree);
    eduRatio = /master|ingenieur|doctorat|mba/.test(d) ? 1 : /licence|bachelor/.test(d) ? 0.7 : 0.45;
    eduText = `${best.degree}, ${best.institution}, ${best.year}.` + (eduRatio === 1 ? ' Bac+5 ou équivalent.' : ' En dessous d’un bac+5.');
  }
  criteria.push({ label: 'Formation', weight: 0.2, contribution: round1(20 * eduRatio), explanation: eduText });

  const score = round1(criteria.reduce((sum, c) => sum + c.contribution, 0));
  return { score, criteria };
}
