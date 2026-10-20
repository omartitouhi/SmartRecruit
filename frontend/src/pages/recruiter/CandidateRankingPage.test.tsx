import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { applicationsApi } from '../../api/applications';
import { candidatesApi } from '../../api/candidates';
import { jobsApi } from '../../api/jobs';
import { HttpError } from '../../lib/errors';
import { makeAccount, makeApplication, makeJob, makeMatch, makeProfile } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { CandidateRankingPage } from './CandidateRankingPage';

vi.mock('../../api/jobs', async (original) => ({
  ...(await original<typeof import('../../api/jobs')>()),
  jobsApi: { get: vi.fn(), search: vi.fn() },
}));
vi.mock('../../api/applications', async (original) => ({
  ...(await original<typeof import('../../api/applications')>()),
  applicationsApi: { forJob: vi.fn(), updateStatus: vi.fn() },
}));
vi.mock('../../api/candidates', async (original) => ({
  ...(await original<typeof import('../../api/candidates')>()),
  candidatesApi: { getCandidate: vi.fn(), downloadCv: vi.fn() },
}));

const jobs = vi.mocked(jobsApi);
const applications = vi.mocked(applicationsApi);
const candidates = vi.mocked(candidatesApi);

const low = makeApplication({ id: 'low', candidateId: 'c-low', status: 'RECEIVED', matchScore: makeMatch(30) });
const high = makeApplication({ id: 'high', candidateId: 'c-high', status: 'RECEIVED', matchScore: makeMatch(91) });
const unscored = makeApplication({ id: 'none', candidateId: 'c-none', status: 'IN_REVIEW', matchScore: null });
const interview = makeApplication({ id: 'iv', candidateId: 'c-iv', status: 'INTERVIEW', matchScore: makeMatch(60) });
const accepted = makeApplication({ id: 'ok', candidateId: 'c-ok', status: 'ACCEPTED', matchScore: makeMatch(80) });
const names: Record<string, string> = { 'c-low': 'Lina Low', 'c-high': 'Hedi High', 'c-none': 'Nora None', 'c-iv': 'Ines Iv', 'c-ok': 'Omar Ok' };

const show = (route = '/recruiter/jobs/job-1/candidates') =>
  renderAt(<CandidateRankingPage />, {
    route,
    path: '/recruiter/jobs/:jobId/candidates',
    account: makeAccount('RECRUITER'),
  });

beforeEach(() => {
  vi.resetAllMocks();
  jobs.get.mockResolvedValue(makeJob());
  applications.forJob.mockResolvedValue([low, high, unscored, interview, accepted]);
  candidates.getCandidate.mockImplementation(async (id) => {
    const [firstName, lastName] = names[id].split(' ');
    return makeProfile({ id, firstName, lastName, email: `${firstName.toLowerCase()}@demo.tn` });
  });
});

describe('received applications ranking', () => {
  it('ranks by score, highest first, with a band badge, unscored last', async () => {
    show();
    const rows = (await screen.findAllByRole('row')).slice(1);
    expect(rows.map((r) => within(r).getByText(/Lina|Hedi|Nora|Ines|Omar/).textContent)).toEqual([
      'Hedi High',
      'Omar Ok',
      'Ines Iv',
      'Lina Low',
      'Nora None',
    ]);
    expect(within(rows[0]).getByText('91,0')).toBeInTheDocument();
    expect(within(rows[0]).getByText('Excellent')).toBeInTheDocument();
    expect(within(rows[0]).getByText('1')).toBeInTheDocument();
    expect(within(rows[3]).getByText('Moyen')).toBeInTheDocument();
    expect(within(rows[4]).getByText('—')).toBeInTheDocument();
  });

  it('keeps withdrawn applications out of the list', async () => {
    applications.forJob.mockResolvedValue([high, makeApplication({ id: 'w', candidateId: 'c-low', status: 'WITHDRAWN' })]);
    show();
    await screen.findByRole('table');
    expect(screen.queryByText('Lina Low')).not.toBeInTheDocument();
  });

  it('filters by category and shows the counts', async () => {
    const user = userEvent.setup();
    show();
    await screen.findByRole('table');
    expect(screen.getByRole('tab', { name: /Toutes\s*5/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /À examiner\s*3/ })).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: /Décidées/ }));
    const table = within(screen.getByRole('table'));
    expect(table.getByText('Omar Ok')).toBeInTheDocument();
    expect(table.queryByText('Hedi High')).not.toBeInTheDocument();
  });

  it('shows the breakdown, contact details and history of the selected candidate', async () => {
    show();
    const detail = await screen.findByRole('complementary', { name: 'Détail du candidat' });
    expect(within(detail).getByRole('heading', { name: 'Hedi High' })).toBeInTheDocument();
    expect(within(detail).getByRole('link', { name: 'hedi@demo.tn' })).toHaveAttribute('href', 'mailto:hedi@demo.tn');
    expect(within(detail).getByText('4 des 5 compétences requises.')).toBeInTheDocument();
    expect(within(detail).getByText('Historique')).toBeInTheDocument();
  });

  it('shows "Compte supprimé" for a candidate who exercised the right to be forgotten', async () => {
    candidates.getCandidate.mockImplementation(async (id) => (id === 'c-high' ? { id, deleted: true } : makeProfile({ id })));
    show();
    const detail = await screen.findByRole('complementary', { name: 'Détail du candidat' });
    expect(within(detail).getAllByText('Compte supprimé').length).toBeGreaterThan(0);
    expect(within(detail).queryByRole('link', { name: /@/ })).not.toBeInTheDocument();
  });
});

describe('status changes', () => {
  async function select(user: ReturnType<typeof userEvent.setup>, name: string) {
    await user.click(within(await screen.findByRole('table')).getByText(name).closest('tr') as HTMLElement);
    return screen.getByRole('complementary', { name: 'Détail du candidat' });
  }

  it('proposes only the next allowed step for a received application', async () => {
    show();
    const detail = await screen.findByRole('complementary', { name: 'Détail du candidat' });
    expect(within(detail).getByRole('button', { name: 'Passer en revue' })).toBeInTheDocument();
    expect(within(detail).queryByRole('button', { name: 'Proposer un entretien' })).not.toBeInTheDocument();
    expect(within(detail).queryByRole('button', { name: 'Accepter' })).not.toBeInTheDocument();
    expect(within(detail).queryByRole('button', { name: 'Retirer ma candidature' })).not.toBeInTheDocument();
  });

  it('offers acceptance or rejection after an interview and nothing once decided', async () => {
    const user = userEvent.setup();
    show();
    let detail = await select(user, 'Ines Iv');
    expect(within(detail).getByRole('button', { name: 'Accepter' })).toBeInTheDocument();
    expect(within(detail).getByRole('button', { name: 'Ne pas retenir' })).toBeInTheDocument();
    expect(within(detail).queryByRole('button', { name: 'Passer en revue' })).not.toBeInTheDocument();

    detail = await select(user, 'Omar Ok');
    expect(within(detail).queryByRole('button', { name: /Accepter|Ne pas retenir|Passer en revue|entretien/ })).not.toBeInTheDocument();
    expect(within(detail).getByText(/Décision prise/)).toBeInTheDocument();
  });

  it('asks for a confirmation, then updates the row without reloading', async () => {
    const user = userEvent.setup();
    applications.updateStatus.mockResolvedValue({ ...high, status: 'IN_REVIEW' });
    show();
    const detail = await screen.findByRole('complementary', { name: 'Détail du candidat' });
    await user.click(within(detail).getByRole('button', { name: 'Passer en revue' }));

    expect(within(detail).getByText(/Passer la candidature à « En revue » \?/)).toBeInTheDocument();
    expect(applications.updateStatus).not.toHaveBeenCalled();
    await user.click(within(detail).getByRole('button', { name: 'Confirmer' }));

    expect(applications.updateStatus).toHaveBeenCalledWith('high', 'IN_REVIEW');
    expect(await screen.findByText('Candidature passée à « En revue ».', { selector: '.toast' })).toBeInTheDocument();
    expect(applications.forJob).toHaveBeenCalledTimes(1);
    const row = within(screen.getByRole('table')).getByText('Hedi High').closest('tr') as HTMLElement;
    expect(within(row).getByText('En revue')).toBeInTheDocument();
    // The next step is now InterView.
    expect(within(detail).getByRole('button', { name: 'Proposer un entretien' })).toBeInTheDocument();
  });

  it('does nothing when the confirmation is cancelled', async () => {
    const user = userEvent.setup();
    show();
    const detail = await screen.findByRole('complementary', { name: 'Détail du candidat' });
    await user.click(within(detail).getByRole('button', { name: 'Passer en revue' }));
    await user.click(within(detail).getByRole('button', { name: 'Annuler' }));
    expect(applications.updateStatus).not.toHaveBeenCalled();
    expect(within(detail).getByRole('button', { name: 'Passer en revue' })).toBeInTheDocument();
  });

  it('shows the API message when the transition is refused (400)', async () => {
    const user = userEvent.setup();
    applications.updateStatus.mockRejectedValue(new HttpError(400, 'Transition impossible : RECEIVED → IN_REVIEW.'));
    show();
    const detail = await screen.findByRole('complementary', { name: 'Détail du candidat' });
    await user.click(within(detail).getByRole('button', { name: 'Passer en revue' }));
    await user.click(within(detail).getByRole('button', { name: 'Confirmer' }));
    expect(await within(detail).findByRole('alert')).toHaveTextContent('Transition impossible');
  });
});

describe('loading and errors', () => {
  it('shows an empty state when nobody applied yet', async () => {
    applications.forJob.mockResolvedValue([]);
    show();
    expect(await screen.findByText('Aucune candidature pour l’instant')).toBeInTheDocument();
  });

  it('says the offer is not found for a 403 or 404 on the offer', async () => {
    jobs.get.mockRejectedValue(new HttpError(403));
    show();
    expect(await screen.findByText('Offre introuvable')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Retour à mes offres' })).toHaveAttribute('href', '/recruiter/jobs');
  });

  it('retries a failed applications load', async () => {
    const user = userEvent.setup();
    applications.forJob.mockRejectedValueOnce(new HttpError(503));
    show();
    await user.click(await screen.findByRole('button', { name: 'Réessayer' }));
    expect(await within(await screen.findByRole('table')).findByText('Hedi High')).toBeInTheDocument();
  });
});
