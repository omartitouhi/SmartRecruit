import { HttpError } from '../../lib/errors';
import { STATUS_MESSAGE_MAX, validateInterview } from '../../lib/interview';
import {
  APPLICATION_STATUS_TRANSITIONS,
  type Application,
  type ApplicationNote,
  type Interview,
  type InterviewRequest,
  type UpcomingInterview,
} from '../../types';
import type { ApplicationsApi } from '../applications';
import { computeMatch, missingSkills } from './scoring';
import {
  assertOwnsJob,
  audit,
  clone,
  closeExpiredJobs,
  currentAccount,
  db,
  deadlinePassed,
  delay,
  findJob,
  findProfile,
  now,
  recruiterCompany,
  requireRole,
  save,
  uuid,
} from './store';

/** The AI scores an application in the background: the application exists first, its score follows. */
const SCORE_DELAY_MS = 1500;
const COVER_LETTER_MAX = 5000;
const NOTE_MAX = 2000;
const UPCOMING_MAX = 50;

const WITHDRAWABLE = ['RECEIVED', 'IN_REVIEW'];

function findApplication(id: string) {
  const application = db.applications.find((a) => a.id === id);
  if (!application) throw new HttpError(404, 'Candidature introuvable.');
  return application;
}

/** Recruiter view (EXT-APP-03): the team's notes count and average rating, one decimal. */
function forRecruiter(application: Application): Application {
  const notes = db.notes.filter((n) => n.applicationId === application.id);
  const rated = notes.flatMap((n) => (n.rating ? [n.rating] : []));
  return {
    ...clone(application),
    notesCount: notes.length,
    averageRating: rated.length ? Math.round((rated.reduce((a, b) => a + b, 0) / rated.length) * 10) / 10 : null,
  };
}

/** Candidate view: the team's notes never leave the recruiters. */
const forCandidate = (application: Application): Application => ({ ...clone(application), notesCount: null, averageRating: null });

function toInterview(request: InterviewRequest): Interview {
  const errors = validateInterview(request);
  if (Object.keys(errors).length) {
    throw new HttpError(400, 'Requête invalide', Object.fromEntries(Object.entries(errors).map(([k, v]) => [`interview.${k}`, v])));
  }
  return {
    scheduledAt: new Date(request.scheduledAt).toISOString(),
    durationMinutes: request.durationMinutes ?? 60,
    mode: request.mode,
    location: request.location?.trim() || null,
  };
}

/** A recruiter of the owning company may read and write; an admin may only read. */
function canReadNotes(applicationId: string) {
  const account = requireRole('RECRUITER', 'ADMIN');
  const application = findApplication(applicationId);
  if (account.role === 'RECRUITER') assertOwnsJob(account.id, application.jobId);
  return { account, application };
}

export const mockApplicationsApi: ApplicationsApi = {
  async apply(jobId, coverLetter) {
    await delay(500);
    const account = requireRole('CANDIDATE');
    closeExpiredJobs();
    const job = findJob(jobId);
    if (job.status !== 'PUBLISHED') throw new HttpError(409, 'Cette offre n’accepte plus de candidatures.');
    if (deadlinePassed(job.applicationDeadline)) throw new HttpError(409, 'La date limite de candidature est dépassée.');
    const profile = findProfile(account.id);
    if (!profile || !profile.cvUrl) {
      throw new HttpError(422, 'Complétez votre profil et déposez votre CV avant de postuler.');
    }
    if (db.applications.some((a) => a.jobId === jobId && a.candidateId === account.id && a.status !== 'WITHDRAWN')) {
      throw new HttpError(409, 'Vous avez déjà postulé à cette offre.');
    }
    const letter = coverLetter?.trim() || null;
    if (letter && letter.length > COVER_LETTER_MAX) {
      throw new HttpError(400, 'Requête invalide', { coverLetter: `${COVER_LETTER_MAX} caractères maximum` });
    }
    const stamp = now();
    const application: Application = {
      id: uuid(),
      jobId,
      candidateId: account.id,
      status: 'RECEIVED',
      matchScore: null,
      appliedAt: stamp,
      statusHistory: [{ status: 'RECEIVED', changedAt: stamp, changedBy: account.id, message: null }],
      coverLetter: letter,
      interview: null,
      notesCount: null,
      averageRating: null,
    };
    db.applications.push(application);
    save();
    setTimeout(() => {
      const stored = db.applications.find((a) => a.id === application.id);
      if (stored && !stored.matchScore) {
        stored.matchScore = computeMatch(profile, job);
        save();
      }
    }, SCORE_DELAY_MS);
    return forCandidate(application);
  },

  async mine() {
    await delay();
    const account = requireRole('CANDIDATE');
    return db.applications
      .filter((a) => a.candidateId === account.id)
      .sort((a, b) => b.appliedAt.localeCompare(a.appliedAt))
      .map(forCandidate);
  },

  async forJob(jobId) {
    await delay();
    const account = requireRole('RECRUITER', 'ADMIN');
    if (account.role === 'RECRUITER') assertOwnsJob(account.id, jobId);
    findJob(jobId);
    return db.applications
      .filter((a) => a.jobId === jobId && a.status !== 'WITHDRAWN')
      .sort((a, b) => (b.matchScore?.score ?? -1) - (a.matchScore?.score ?? -1))
      .map(forRecruiter);
  },

  async updateStatus(applicationId, status, options = {}) {
    await delay();
    const account = requireRole('RECRUITER');
    const application = findApplication(applicationId);
    assertOwnsJob(account.id, application.jobId);
    // Withdrawing is reserved to the candidate; every other move follows ALLOWED_TRANSITIONS.
    if (status === 'WITHDRAWN' || !APPLICATION_STATUS_TRANSITIONS[application.status].includes(status)) {
      throw new HttpError(400, `Transition impossible : ${application.status} → ${status}.`);
    }
    if (options.interview && status !== 'INTERVIEW') {
      throw new HttpError(400, 'Un entretien ne peut être fourni qu’avec le statut INTERVIEW.');
    }
    const message = options.message?.trim() || null;
    if (message && message.length > STATUS_MESSAGE_MAX) {
      throw new HttpError(400, 'Requête invalide', { message: `${STATUS_MESSAGE_MAX} caractères maximum` });
    }
    const interview = options.interview ? toInterview(options.interview) : null;
    application.status = status;
    application.statusHistory.push({ status, changedAt: now(), changedBy: account.id, message });
    if (interview) application.interview = interview;
    audit('APPLICATION_STATUS_CHANGED', 'Application', application.id, account.id);
    if (interview) audit('INTERVIEW_SCHEDULED', 'Application', application.id, account.id);
    save();
    return forRecruiter(application);
  },

  async scheduleInterview(applicationId, request) {
    await delay();
    const account = requireRole('RECRUITER');
    const application = findApplication(applicationId);
    assertOwnsJob(account.id, application.jobId);
    if (application.status !== 'INTERVIEW') {
      throw new HttpError(409, 'Un entretien ne se planifie que pour une candidature au stade « Entretien ».');
    }
    application.interview = toInterview(request);
    audit('INTERVIEW_SCHEDULED', 'Application', application.id, account.id);
    save();
    return forRecruiter(application);
  },

  async upcomingInterviews() {
    await delay();
    const account = requireRole('RECRUITER');
    const company = recruiterCompany(account.id);
    if (!company) return [];
    const jobIds = new Set(db.jobs.filter((j) => j.companyId === company.id).map((j) => j.id));
    const from = Date.now();
    return db.applications
      .filter((a) => jobIds.has(a.jobId) && a.status === 'INTERVIEW' && a.interview && new Date(a.interview.scheduledAt).getTime() >= from)
      .sort((a, b) => a.interview!.scheduledAt.localeCompare(b.interview!.scheduledAt))
      .slice(0, UPCOMING_MAX)
      .map<UpcomingInterview>((a) => ({ applicationId: a.id, jobId: a.jobId, candidateId: a.candidateId, ...clone(a.interview!) }));
  },

  async notes(applicationId) {
    await delay(180);
    canReadNotes(applicationId);
    return clone(db.notes.filter((n) => n.applicationId === applicationId).sort((a, b) => a.createdAt.localeCompare(b.createdAt)));
  },

  async addNote(applicationId, { content, rating }) {
    await delay();
    const { account } = canReadNotes(applicationId);
    if (account.role !== 'RECRUITER') throw new HttpError(403);
    const errors: Record<string, string> = {};
    if (!content?.trim() || content.length > NOTE_MAX) errors.content = `La note doit contenir entre 1 et ${NOTE_MAX} caractères`;
    if (rating != null && (!Number.isInteger(rating) || rating < 1 || rating > 5)) errors.rating = 'Une note entre 1 et 5';
    if (Object.keys(errors).length) throw new HttpError(400, 'Requête invalide', errors);
    const note: ApplicationNote = {
      id: uuid(),
      applicationId,
      authorId: account.id,
      content: content.trim(),
      rating: rating ?? null,
      createdAt: now(),
    };
    db.notes.push(note);
    save();
    return clone(note);
  },

  async updateNote(applicationId, noteId, { content, rating }) {
    await delay();
    const account = requireRole('RECRUITER');
    const note = db.notes.find((n) => n.id === noteId && n.applicationId === applicationId);
    if (!note) throw new HttpError(404, 'Note introuvable.');
    if (note.authorId !== account.id) throw new HttpError(403, 'Seul l’auteur d’une note peut la modifier.');
    if (!content?.trim() || content.length > NOTE_MAX) {
      throw new HttpError(400, 'Requête invalide', { content: `La note doit contenir entre 1 et ${NOTE_MAX} caractères` });
    }
    note.content = content.trim();
    note.rating = rating ?? null;
    save();
    return clone(note);
  },

  async deleteNote(applicationId, noteId) {
    await delay();
    const account = requireRole('RECRUITER');
    const application = findApplication(applicationId);
    assertOwnsJob(account.id, application.jobId);
    const note = db.notes.find((n) => n.id === noteId && n.applicationId === applicationId);
    if (!note) throw new HttpError(404, 'Note introuvable.');
    if (note.authorId !== account.id) throw new HttpError(403, 'Seul l’auteur d’une note peut la supprimer.');
    db.notes = db.notes.filter((n) => n.id !== noteId);
    save();
  },

  async withdraw(applicationId) {
    await delay();
    const account = requireRole('CANDIDATE');
    const application = findApplication(applicationId);
    if (application.candidateId !== account.id) throw new HttpError(403);
    if (!WITHDRAWABLE.includes(application.status)) {
      throw new HttpError(409, 'Cette candidature ne peut plus être retirée.');
    }
    application.status = 'WITHDRAWN';
    application.statusHistory.push({ status: 'WITHDRAWN', changedAt: now(), changedBy: account.id, message: null });
    save();
  },

  async matchPreview(jobId) {
    await delay(400);
    const account = requireRole('CANDIDATE');
    const job = findJob(jobId);
    const profile = findProfile(account.id);
    if (profile?.cvAnalysisStatus !== 'DONE') {
      throw new HttpError(422, 'Votre CV doit être analysé pour calculer un score.');
    }
    return computeMatch(profile, job);
  },

  async skillGap(candidateId, jobId) {
    await delay(200);
    currentAccount();
    const profile = findProfile(candidateId);
    if (!profile) throw new HttpError(404, 'Profil candidat introuvable.');
    return { missingSkills: missingSkills(profile, findJob(jobId)) };
  },
};
