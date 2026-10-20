import { HttpError } from '../../lib/errors';
import { APPLICATION_STATUS_TRANSITIONS, type Application, type ApplicationStatus } from '../../types';
import type { ApplicationsApi } from '../applications';
import { computeMatch, missingSkills } from './scoring';
import { db, save, delay, now, uuid, clone, currentAccount, requireRole, audit, findProfile, findJob, assertOwnsJob } from './store';

export const mockApplicationsApi: ApplicationsApi = {
  async apply(jobId) {
    await delay(500);
    const account = requireRole('CANDIDATE');
    const profile = findProfile(account.id);
    if (!profile) throw new HttpError(400, 'Créez votre profil avant de postuler.');
    const job = findJob(jobId);
    if (job.status !== 'PUBLISHED') throw new HttpError(400, 'Cette offre n’accepte plus de candidatures.');
    if (db.applications.some((a) => a.jobId === jobId && a.candidateId === account.id && a.status !== 'WITHDRAWN')) {
      throw new HttpError(409, 'Vous avez déjà postulé à cette offre.');
    }
    const stamp = now();
    const application: Application = {
      id: uuid(),
      jobId,
      candidateId: account.id,
      status: 'RECEIVED',
      matchScore: computeMatch(profile, job),
      appliedAt: stamp,
      statusHistory: [{ status: 'RECEIVED', changedAt: stamp, changedBy: account.id }],
    };
    db.applications.push(application);
    audit('APPLICATION_CREATED', 'Application', application.id, account.id);
    save();
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
    return clone(db.applications.filter((a) => a.jobId === jobId));
  },

  async updateStatus(applicationId, status: ApplicationStatus) {
    await delay();
    const account = requireRole('CANDIDATE', 'RECRUITER');
    const application = db.applications.find((a) => a.id === applicationId);
    if (!application) throw new HttpError(404, 'Candidature introuvable.');
    if (account.role === 'CANDIDATE') {
      // A candidate may only withdraw their own application.
      if (application.candidateId !== account.id || status !== 'WITHDRAWN') throw new HttpError(403);
    } else {
      assertOwnsJob(account.id, application.jobId);
      if (status === 'WITHDRAWN') throw new HttpError(403);
    }
    if (!APPLICATION_STATUS_TRANSITIONS[application.status].includes(status)) {
      throw new HttpError(400, `Transition impossible : ${application.status} → ${status}.`);
    }
    application.status = status;
    application.statusHistory.push({ status, changedAt: now(), changedBy: account.id });
    audit('APPLICATION_STATUS_CHANGED', 'Application', application.id, account.id);
    save();
    return clone(application);
  },

  async matchPreview(candidateId, jobId) {
    await delay(400);
    currentAccount();
    const profile = findProfile(candidateId);
    if (!profile) throw new HttpError(404, 'Profil candidat introuvable.');
    return computeMatch(profile, findJob(jobId));
  },

  async skillGap(candidateId, jobId) {
    await delay(200);
    currentAccount();
    const profile = findProfile(candidateId);
    if (!profile) throw new HttpError(404, 'Profil candidat introuvable.');
    return { missingSkills: missingSkills(profile, findJob(jobId)) };
  },
};
