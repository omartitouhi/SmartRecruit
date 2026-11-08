import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { applicationsApi } from '../../api/applications';
import { HttpError } from '../../lib/errors';
import { makeAccount, makeApplication } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { ApplyButton } from './ApplyButton';

vi.mock('../../api/applications', async (original) => ({
  ...(await original<typeof import('../../api/applications')>()),
  applicationsApi: {
    apply: vi.fn(),
    mine: vi.fn(),
    forJob: vi.fn(),
    updateStatus: vi.fn(),
    withdraw: vi.fn(),
    matchPreview: vi.fn(),
    skillGap: vi.fn(),
  },
}));

const api = vi.mocked(applicationsApi);
const show = () =>
  renderAt(<ApplyButton jobId="job-1" />, { route: '/candidate/jobs/job-1', account: makeAccount('CANDIDATE') });

beforeEach(() => {
  vi.resetAllMocks();
  api.mine.mockResolvedValue([]);
});

describe('ApplyButton', () => {
  it('sends the application, confirms with a toast and becomes "Candidature envoyée"', async () => {
    const user = userEvent.setup();
    api.apply.mockResolvedValue(makeApplication({ id: 'new-app' }));
    show();
    await user.click(await screen.findByRole('button', { name: 'Postuler' }));

    expect(api.apply).toHaveBeenCalledWith('job-1');
    expect(await screen.findByRole('button', { name: 'Candidature envoyée' })).toBeDisabled();
    expect(screen.getByText('Candidature envoyée.', { selector: '.toast' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Suivre ma candidature' })).toHaveAttribute('href', '/candidate/applications?id=new-app');
    expect(screen.queryByRole('button', { name: 'Postuler' })).not.toBeInTheDocument();
  });

  it('shows "Candidature envoyée" straight away when the candidate already applied', async () => {
    api.mine.mockResolvedValue([makeApplication({ status: 'IN_REVIEW' })]);
    show();
    expect(await screen.findByRole('button', { name: 'Candidature envoyée' })).toBeDisabled();
    expect(screen.getByText('En revue')).toBeInTheDocument();
  });

  it('offers to apply again after a withdrawal', async () => {
    api.mine.mockResolvedValue([makeApplication({ status: 'WITHDRAWN' })]);
    show();
    expect(await screen.findByRole('button', { name: 'Postuler' })).toBeEnabled();
  });

  it('ignores applications to other offers', async () => {
    api.mine.mockResolvedValue([makeApplication({ jobId: 'other-job' })]);
    show();
    expect(await screen.findByRole('button', { name: 'Postuler' })).toBeEnabled();
  });

  it('explains a 409 (already applied or closed) and refreshes the state', async () => {
    const user = userEvent.setup();
    api.apply.mockRejectedValue(new HttpError(409, 'Vous avez déjà postulé à cette offre.'));
    show();
    await user.click(await screen.findByRole('button', { name: 'Postuler' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Vous avez déjà postulé à cette offre.');
    await waitFor(() => expect(api.mine).toHaveBeenCalledTimes(2));
  });

  it('redirects to the profile with an explanation on a 422 (profile incomplete or no CV)', async () => {
    const user = userEvent.setup();
    api.apply.mockRejectedValue(new HttpError(422));
    show();
    await user.click(await screen.findByRole('button', { name: 'Postuler' }));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/candidate/profile'));
    expect(screen.getByTestId('state')).toHaveTextContent('Complétez votre profil et déposez votre CV');
  });

  it('shows any other failure inline and lets the candidate retry', async () => {
    const user = userEvent.setup();
    api.apply.mockRejectedValueOnce(new HttpError(503));
    show();
    await user.click(await screen.findByRole('button', { name: 'Postuler' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('momentanément indisponible');
    expect(screen.getByRole('button', { name: 'Postuler' })).toBeEnabled();
  });
});

describe('ApplyButton with a cover letter (EXT-APP-01)', () => {
  it('sends the optional letter with the application', async () => {
    const user = userEvent.setup();
    api.apply.mockResolvedValue(makeApplication({ id: 'new-app', coverLetter: 'Motivée.' }));
    show();
    await user.click(await screen.findByRole('button', { name: 'Joindre une lettre de motivation' }));
    await user.type(screen.getByLabelText('Lettre de motivation (facultative)'), '  Motivée.  ');
    await user.click(screen.getByRole('button', { name: 'Postuler avec ma lettre' }));

    expect(api.apply).toHaveBeenCalledWith('job-1', 'Motivée.');
    expect(await screen.findByRole('button', { name: 'Candidature envoyée' })).toBeDisabled();
  });

  it('applies without a letter once it is removed, and counts the characters', async () => {
    const user = userEvent.setup();
    api.apply.mockResolvedValue(makeApplication());
    show();
    await user.click(await screen.findByRole('button', { name: 'Joindre une lettre de motivation' }));
    await user.type(screen.getByLabelText('Lettre de motivation (facultative)'), 'Bonjour');
    expect(screen.getByText(/7 \/ 5.000 caractères/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retirer la lettre' }));
    expect(screen.queryByLabelText('Lettre de motivation (facultative)')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Postuler' }));
    expect(api.apply).toHaveBeenCalledWith('job-1');
  });

  it('shows a 409 for an offer past its deadline', async () => {
    const user = userEvent.setup();
    api.apply.mockRejectedValue(new HttpError(409, 'La date limite de candidature est dépassée.'));
    show();
    await user.click(await screen.findByRole('button', { name: 'Postuler' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('La date limite de candidature est dépassée.');
  });
});
