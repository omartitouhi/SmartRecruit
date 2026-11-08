import { HttpError } from '../../lib/errors';
import { APPLICATION_STATUS_TRANSITIONS, type Application } from '../../types';
import type { ApplicationsApi } from '../applications';
import { computeMatch, missingSkills } from './scoring';
import {
  assertOwnsJob,
  audit,
  clone,
  currentAccount,
  db,
  delay,
  findJob,
  findProfile,
  now,
  requireRole,
  save,
  uuid,
} from './store';

/** The AI scores an application in the background: the application exists first, its score follows. */
const SCORE_DELAY_MS = 1500;

const WITHDRAWABLE = ['RECEIVED', 'IN_REVIEW'];

function findApplication(id: string) {
  const application = db.applications.find((a) => a.id === id);
  if (!application) throw new HttpError(404, 'Candidature introuvable.');
  return application;
}

export const mockApplicationsApi: ApplicationsApi = {
  async apply(jobId) {
    await delay(500);
    const account = requireRole('CANDIDATE');
    const job = findJob(jobId);
    if (job.status !== 'PUBLISHED') throw new HttpError(409, 'Cette offre n’accepte plus de candidatures.');
    const profile = findProfile(account.id);
    if (!profile || !profile.cvUrl) {
      throw new HttpError(422, 'Complétez votre profil et déposez votre CV avant de postuler.');
    }
    if (db.applications.some((a) => a.jobId === jobId && a.candidateId === account.id && a.status !== 'WITHDRAWN')) {
      throw new HttpError(409, 'Vous avez déjà postulé à cette offre.');
    }
    const stamp = now();
    const application: Application = {
      id: uuid(),
      jobId,
      candidateId: account.id,
      status: 'RECEIVED',
      matchScore: null,
      appliedAt: stamp,
      statusHistory: [{ status: 'RECEIVED', changedAt: stamp, changedBy: account.id }],
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
    return clone(application);
  },

  async mine() {
    await delay();
    const account = requireRole('CANDIDATE');
    return clone(
      db.applications
        .filter((a) => a.candidateId === account.id)
        .sort((a, b) => b.appliedAt.localeCompare(a.appliedAt)),
    );
  },

  async forJob(jobId) {
    await delay();
    const account = requireRole('RECRUITER', 'ADMIN');
    if (account.role === 'RECRUITER') assertOwnsJob(account.id, jobId);
    findJob(jobId);
    return clone(
      db.applications
        .filter((a) => a.jobId === jobId && a.status !== 'WITHDRAWN')
        .sort((a, b) => (b.matchScore?.score ?? -1) - (a.matchScore?.score ?? -1)),
    );
  },

  async updateStatus(applicationId, status) {
    await delay();
    const account = requireRole('RECRUITER');
    const application = findApplication(applicationId);
    assertOwnsJob(account.id, application.jobId);
    // Withdrawing is reserved to the candidate; every other move follows ALLOWED_TRANSITIONS.
    if (status === 'WITHDRAWN' || !APPLICATION_STATUS_TRANSITIONS[application.status].includes(status)) {
      throw new HttpError(400, `Transition impossible : ${application.status} → ${status}.`);
    }
    application.status = status;
    application.statusHistory.push({ status, changedAt: now(), changedBy: account.id });
    audit('APPLICATION_STATUS_CHANGED', 'Application', application.id, account.id);
    save();
    return clone(application);
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
    application.statusHistory.push({ status: 'WITHDRAWN', changedAt: now(), changedBy: account.id });
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
