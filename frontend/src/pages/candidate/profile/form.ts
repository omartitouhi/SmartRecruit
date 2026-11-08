import type { CandidateProfileRequest } from '../../../api/candidates';
import type { CandidateProfile, CVParsingResult, Education, Experience } from '../../../types';

export const EMPTY: CandidateProfileRequest = {
  firstName: '',
  lastName: '',
  email: '',
  phone: null,
  location: null,
  skills: [],
  experiences: [],
  education: [],
  languages: [],
};

export const toForm = (p: CandidateProfile): CandidateProfileRequest => ({
  firstName: p.firstName,
  lastName: p.lastName,
  email: p.email,
  phone: p.phone,
  location: p.location,
  skills: p.skills,
  experiences: p.experiences,
  education: p.education,
  languages: p.languages,
});

export function validate(form: CandidateProfileRequest) {
  const e: Record<string, string> = {};
  if (!form.firstName.trim()) e.firstName = 'Le prénom est obligatoire.';
  if (!form.lastName.trim()) e.lastName = 'Le nom est obligatoire.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) e.email = 'Saisissez un email valide.';
  if (form.phone && !/^\+?[0-9 .()-]{6,20}$/.test(form.phone)) e.phone = 'Format attendu : +216 22 418 730.';
  form.experiences.forEach((x, i) => {
    if (!x.title.trim()) e[`experiences[${i}].title`] = 'L’intitulé est obligatoire.';
    if (!x.company.trim()) e[`experiences[${i}].company`] = 'L’entreprise est obligatoire.';
    if (!x.startDate) e[`experiences[${i}].startDate`] = 'La date de début est obligatoire.';
    if (x.startDate && x.endDate && x.endDate < x.startDate) e[`experiences[${i}].endDate`] = 'La fin précède le début.';
  });
  form.education.forEach((x, i) => {
    if (!x.degree.trim()) e[`education[${i}].degree`] = 'Le diplôme est obligatoire.';
    if (!x.institution.trim()) e[`education[${i}].institution`] = 'L’établissement est obligatoire.';
    if (!x.year || x.year < 1950 || x.year > 2100) e[`education[${i}].year`] = 'Année entre 1950 et 2100.';
  });
  return e;
}

const same = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase();
const experienceKey = (x: Experience) => `${x.title}|${x.company}|${x.startDate}`.toLowerCase();
const educationKey = (x: Education) => `${x.degree}|${x.institution}|${x.year}`.toLowerCase();

/** What the CV analysis found that the profile does not contain yet. */
export function newSuggestions(form: CandidateProfileRequest, found: CVParsingResult): CVParsingResult {
  const unique = (items: string[], known: string[]) =>
    items.filter((v, i) => !known.some((k) => same(k, v)) && items.findIndex((x) => same(x, v)) === i);
  return {
    skills: unique(found.skills, form.skills),
    languages: unique(found.languages, form.languages),
    experiences: found.experiences.filter((x) => !form.experiences.some((y) => experienceKey(y) === experienceKey(x))),
    education: found.education.filter((x) => !form.education.some((y) => educationKey(y) === educationKey(x))),
  };
}

/** Appends the parts the candidate accepted; the form keeps everything they already had. */
export function applySuggestions(form: CandidateProfileRequest, picked: CVParsingResult): CandidateProfileRequest {
  return {
    ...form,
    skills: [...form.skills, ...picked.skills],
    languages: [...form.languages, ...picked.languages],
    experiences: [...form.experiences, ...picked.experiences],
    education: [...form.education, ...picked.education],
  };
}
