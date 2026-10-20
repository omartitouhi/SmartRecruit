import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockJobsApi } from '../../api/mock/jobs';
import { db } from '../../api/mock/store';
import { EMAIL, jobByTitle, loginAs, resetDb } from '../../test/mockDb';
import { makeAccount } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { EditJobPage } from './EditJobPage';

// Demo mode: the page talks to the in-browser gateway instead of the HTTP API.
vi.mock('../../api/jobs', async (original) => ({
  ...(await original<typeof import('../../api/jobs')>()),
  jobsApi: (await import('../../api/mock/jobs')).mockJobsApi,
}));

const show = (jobId: string) =>
  renderAt(<EditJobPage />, {
    route: `/recruiter/jobs/${jobId}/edit`,
    path: '/recruiter/jobs/:jobId/edit',
    account: makeAccount('RECRUITER'),
  });

beforeEach(() => {
  resetDb();
  loginAs(EMAIL.recruiter);
});

describe('edit page on the simulated gateway', () => {
  it('saves a change into the in-browser database', async () => {
    const job = jobByTitle('Développeur·se backend');
    const user = userEvent.setup();
    show(job.id);
    const title = await screen.findByLabelText('Intitulé du poste');
    expect(title).toHaveValue(job.title);
    await user.clear(title);
    await user.type(title, 'Backend Java confirmé');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText('Offre mise à jour.', { selector: '.toast' })).toBeInTheDocument();
    expect(db.jobs.find((j) => j.id === job.id)).toMatchObject({ title: 'Backend Java confirmé', location: job.location });
  });

  it('shows the simulated 400 under the field', async () => {
    const user = userEvent.setup();
    show(jobByTitle('Développeur·se backend').id);
    // A 101-character skill passes the form but not the gateway.
    await user.type(await screen.findByLabelText('Compétences demandées'), `${'x'.repeat(101)}{Enter}`);
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(await screen.findByText('100 caractères maximum par compétence')).toBeInTheDocument();
  });

  it('refuses an offer closed meanwhile with the 409 message', async () => {
    const job = jobByTitle('Développeur·se backend');
    const user = userEvent.setup();
    show(job.id);
    await user.type(await screen.findByLabelText('Lieu'), ' Nord');
    await mockJobsApi.updateStatus(job.id, 'CLOSED');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Offre clôturée ou retirée : non modifiable.');
  });

  it('does not open a moderated offer for edition', async () => {
    show(jobByTitle('Responsable exploitation').id);
    expect(await screen.findByRole('alert')).toHaveTextContent('non modifiable');
    expect(screen.queryByLabelText('Intitulé du poste')).not.toBeInTheDocument();
  });
});
