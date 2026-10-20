import { HttpError } from '../../lib/errors';
import type { CandidateProfile, CVParsingResult } from '../../types';
import type { CandidatesApi } from '../candidates';
import { db, cvBlobs, CV_PARSE_DELAY_MS, save, delay, now, uuid, clone, currentAccount, requireRole, audit, findProfile } from './store';

function withParsingState(profile: CandidateProfile) {
  // The AI parses the CV a few seconds after upload; a file whose name contains "fail" or "echec" makes it fail.
  if (profile.cvAnalysisStatus === 'PENDING' && Date.now() - (db.cvUploadedAt[profile.id] ?? 0) > CV_PARSE_DELAY_MS) {
    const failed = /fail|echec/i.test(db.cvFiles[profile.id] ?? '');
    profile.cvAnalysisStatus = failed ? 'FAILED' : 'DONE';
    if (!failed) profile.cvParsedAt = now();
    profile.updatedAt = now();
    save();
  }
  return clone(profile);
}

/** What the AI "read" in the CV: the profile plus a few extras, so the validation screen has something to apply. */
function demoSuggestions(profile: CandidateProfile): CVParsingResult {
  const unique = (items: string[]) => items.filter((v, i) => items.findIndex((x) => x.toLowerCase() === v.toLowerCase()) === i);
  return {
    skills: unique([...profile.skills, 'Kubernetes', 'GraphQL']),
    experiences: [
      ...clone(profile.experiences),
      { title: 'Stagiaire DevOps', company: 'Byrsa Lab', startDate: '2020-06', endDate: '2020-09', description: 'Automatisation des déploiements.' },
    ],
    education: clone(profile.education),
    languages: unique([...profile.languages, 'Allemand']),
  };
}

export const mockCandidatesApi: CandidatesApi = {
  async getMyProfile() {
    await delay();
    const account = requireRole('CANDIDATE');
    const profile = findProfile(account.id);
    if (!profile) throw new HttpError(404, 'Profil candidat introuvable.');
    return withParsingState(profile);
  },

  async saveMyProfile(request) {
    await delay();
    const account = requireRole('CANDIDATE');
    const fieldErrors: Record<string, string> = {};
    if (!request.firstName.trim()) fieldErrors.firstName = 'Le prénom est obligatoire';
    if (!request.lastName.trim()) fieldErrors.lastName = 'Le nom est obligatoire';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(request.email)) fieldErrors.email = 'L’email n’est pas valide';
    if (request.phone && !/^\+?[0-9 .()-]{6,20}$/.test(request.phone)) {
      fieldErrors.phone = 'Le numéro de téléphone n’est pas valide';
    }
    if (Object.keys(fieldErrors).length) throw new HttpError(400, 'Requête invalide', fieldErrors);

    const existing = findProfile(account.id);
    const stamp = now();
    const profile: CandidateProfile = {
      ...request,
      id: account.id,
      cvUrl: existing?.cvUrl ?? null,
      cvParsedAt: existing?.cvParsedAt ?? null,
      cvAnalysisStatus: existing?.cvAnalysisStatus ?? 'NONE',
      createdAt: existing?.createdAt ?? stamp,
      updatedAt: stamp,
      deleted: false,
    };
    db.profiles = [...db.profiles.filter((p) => p.id !== account.id), profile];
    save();
    return clone(profile);
  },

  async uploadCv(file, onProgress) {
    for (const percent of [30, 70, 100]) {
      await delay(200);
      onProgress?.(percent);
    }
    const account = requireRole('CANDIDATE');
    const profile = findProfile(account.id);
    if (!profile) throw new HttpError(404, 'Créez votre profil avant de déposer un CV.');
    if (file.type !== 'application/pdf') throw new HttpError(400, 'Le CV doit être un fichier PDF.');
    if (file.size > 5 * 1024 * 1024) throw new HttpError(413, 'Le CV ne doit pas dépasser 5 Mo.');
    cvBlobs.set(account.id, file);
    db.cvUploadedAt[account.id] = Date.now();
    db.cvFiles[account.id] = file.name;
    profile.cvUrl = `/api/v1/users/candidates/${account.id}/cv`;
    profile.cvParsedAt = null;
    profile.cvAnalysisStatus = 'PENDING';
    profile.updatedAt = now();
    save();
    return clone(profile);
  },

  async downloadCv(candidateId) {
    await delay();
    const account = currentAccount();
    if (account.role === 'CANDIDATE' && account.id !== candidateId) throw new HttpError(403);
    const profile = findProfile(candidateId);
    if (!profile?.cvUrl) throw new HttpError(404, 'Aucun CV pour ce candidat.');
    if (account.role !== 'CANDIDATE') {
      audit('CV_VIEWED', 'CandidateProfile', candidateId, account.id);
      save();
    }
    return cvBlobs.get(candidateId) ?? demoPdf(`${profile.firstName} ${profile.lastName}`, db.cvFiles[candidateId]);
  },

  async cvSuggestions() {
    await delay();
    const account = requireRole('CANDIDATE');
    const stored = findProfile(account.id);
    const profile = stored && withParsingState(stored);
    if (profile?.cvAnalysisStatus !== 'DONE') throw new HttpError(404, 'Aucun CV analysé pour ce profil.');
    return demoSuggestions(profile);
  },

  async deleteMyAccount() {
    await delay();
    const account = requireRole('CANDIDATE');
    db.profiles = db.profiles.filter((p) => p.id !== account.id);
    db.deletedCandidates.push(account.id);
    const stored = db.accounts.find((a) => a.id === account.id);
    if (stored) {
      stored.status = 'DELETED';
      stored.email = `deleted+${account.id}@invalid`;
      stored.password = uuid();
    }
    cvBlobs.delete(account.id);
    delete db.cvFiles[account.id];
    save();
  },

  async getCandidate(candidateId) {
    await delay(160);
    const viewer = requireRole('RECRUITER', 'ADMIN');
    audit('PROFILE_VIEWED', 'CandidateProfile', candidateId, viewer.id);
    save();
    if (db.deletedCandidates.includes(candidateId)) return { id: candidateId, deleted: true };
    const profile = findProfile(candidateId);
    if (!profile) throw new HttpError(404, 'Candidat introuvable.');
    return withParsingState(profile);
  },
};

// ───────────────────────── demo CV ─────────────────────────

/** Minimal one-page PDF so "Voir le CV" opens something real for seeded candidates. */
function demoPdf(name: string, fileName = 'CV.pdf') {
  const ascii = (s: string) =>
    s
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[()\\]/g, '');
  const text = `BT /F1 22 Tf 72 760 Td (${ascii(name)}) Tj /F1 11 Tf 0 -28 Td (${ascii(fileName)} - CV de demonstration SmartRecruit) Tj ET`;
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${text.length} >>\nstream\n${text}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let body = '%PDF-1.4\n';
  const offsets: number[] = [];
  objects.forEach((obj, i) => {
    offsets.push(body.length);
    body += `${i + 1} 0 obj\n${obj}\nendobj\n`;
  });
  const xref = body.length;
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  body += offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('');
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return new Blob([body], { type: 'application/pdf' });
}
