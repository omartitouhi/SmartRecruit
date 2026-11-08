import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { applicationsApi } from '../../api/applications';
import { candidatesApi } from '../../api/candidates';
import { companiesApi } from '../../api/companies';
import { jobsApi } from '../../api/jobs';
import { HttpError } from '../../lib/errors';
import { makeAccount, makeApplication, makeJob, makeMatch, makeProfile } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { ApplicationsPage } from './ApplicationsPage';
import { CandidateDashboardPage } from './CandidateDashboardPage';
import { JobDetailPage } from './JobDetailPage';
import { MatchScorePage } from './MatchScorePage';
import { RecommendationsPage } from './RecommendationsPage';

vi.mock('../../api/jobs', async (original) => ({
  ...(await original<typeof import('../../api/jobs')>()),
  jobsApi: { search: vi.fn(), get: vi.fn(), recommendations: vi.fn(), create: vi.fn(), update: vi.fn(), updateStatus: vi.fn() },
}));
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
vi.mock('../../api/candidates', async (original) => ({
  ...(await original<typeof import('../../api/candidates')>()),
  candidatesApi: { getMyProfile: vi.fn() },
}));
vi.mock('../../api/companies', async (original) => ({
  ...(await original<typeof import('../../api/companies')>()),
  companiesApi: { get: vi.fn() },
}));

const jobs = vi.mocked(jobsApi);
const applications = vi.mocked(applicationsApi);
const candidates = vi.mocked(candidatesApi);
const companies = vi.mocked(companiesApi);
const account = makeAccount('CANDIDATE', { id: 'candidate-id' });
const analysed = makeProfile({ cvUrl: '/cv', cvAnalysisStatus: 'DONE', cvParsedAt: '2026-10-05T09:00:00Z' });

beforeEach(() => {
  vi.resetAllMocks();
  applications.mine.mockResolvedValue([]);
  applications.skillGap.mockResolvedValue({ missingSkills: [] });
  candidates.getMyProfile.mockResolvedValue(analysed);
  jobs.get.mockResolvedValue(makeJob());
});

describe('job detail page', () => {
  const show = () => renderAt(<JobDetailPage />, { route: '/candidate/jobs/job-1', path: '/candidate/jobs/:jobId', account });

  it('shows the description, skills, company presentation and the apply button', async () => {
    companies.get.mockResolvedValue({ id: 'company-1', name: 'Carthage Fret', sector: 'Logistique', description: 'Transitaire basé aux Berges du Lac.', logoUrl: null });
    show();
    expect(await screen.findByRole('heading', { level: 1, name: 'Développeur backend' })).toBeInTheDocument();
    expect(screen.getByText('Vous construisez nos services.')).toBeInTheDocument();
    expect(screen.getByText('Kubernetes')).toBeInTheDocument();
    expect(await screen.findByText('À propos de Carthage Fret')).toBeInTheDocument();
    expect(screen.getByText('Transitaire basé aux Berges du Lac.')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Postuler' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voir mon score expliqué' })).toHaveAttribute('href', '/candidate/match/job-1');
  });

  it('stays usable when the company presentation cannot be loaded', async () => {
    companies.get.mockRejectedValue(new HttpError(503));
    show();
    expect(await screen.findByRole('heading', { level: 1, name: 'Développeur backend' })).toBeInTheDocument();
    expect(screen.queryByText(/À propos de/)).not.toBeInTheDocument();
  });

  it('explains a 404 (closed or removed offer)', async () => {
    jobs.get.mockRejectedValue(new HttpError(404));
    show();
    expect(await screen.findByText('Offre introuvable')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Retour aux offres' })).toHaveAttribute('href', '/candidate/jobs');
  });
});

describe('explained score page', () => {
  const show = () => renderAt(<MatchScorePage />, { route: '/candidate/match/job-1', path: '/candidate/match/:jobId', account });

  it('shows the total, each criterion with its explanation and the skills to develop', async () => {
    applications.matchPreview.mockResolvedValue(makeMatch(72.5));
    applications.skillGap.mockResolvedValue({ missingSkills: ['Kubernetes'] });
    show();

    expect(await screen.findByText('72,5')).toBeInTheDocument();
    expect(applications.matchPreview).toHaveBeenCalledWith('job-1');
    expect(screen.getByText('Compétences techniques')).toBeInTheDocument();
    expect(screen.getByText('4 des 5 compétences requises.')).toBeInTheDocument();
    const gap = await screen.findByRole('region', { name: 'Compétences à développer' });
    expect(within(gap).getByText('Kubernetes')).toBeInTheDocument();
  });

  it('says the profile covers everything when no skill is missing', async () => {
    applications.matchPreview.mockResolvedValue(makeMatch());
    show();
    expect(await screen.findByText('Votre profil couvre toutes les compétences demandées.')).toBeInTheDocument();
  });

  it.each([
    ['NONE', 'Déposez votre CV pour voir votre score'],
    ['PENDING', 'Votre CV est en cours d’analyse'],
    ['FAILED', 'L’analyse de votre CV a échoué'],
  ] as const)('does not ask for a score while the CV analysis is %s', async (cvAnalysisStatus, title) => {
    candidates.getMyProfile.mockResolvedValue(makeProfile({ cvAnalysisStatus, cvUrl: cvAnalysisStatus === 'NONE' ? null : '/cv' }));
    show();
    expect(await screen.findByText(title)).toBeInTheDocument();
    expect(applications.matchPreview).not.toHaveBeenCalled();
  });

  it('invites a candidate without a profile to create one', async () => {
    candidates.getMyProfile.mockRejectedValue(new HttpError(404));
    show();
    expect(await screen.findByText('Créez votre profil pour voir votre score')).toBeInTheDocument();
  });

  it('handles a 422 from match-preview with a message instead of a crash', async () => {
    applications.matchPreview.mockRejectedValue(new HttpError(422));
    show();
    expect(await screen.findByText('Votre CV doit être analysé')).toBeInTheDocument();
  });

  it('offers a retry when the AI is unavailable', async () => {
    const user = userEvent.setup();
    applications.matchPreview.mockRejectedValueOnce(new HttpError(503)).mockResolvedValue(makeMatch(60));
    show();
    await user.click(await screen.findByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText('60,0')).toBeInTheDocument();
  });

  it('hides the apply button and warns when the offer is no longer open', async () => {
    jobs.get.mockResolvedValue(makeJob({ status: 'CLOSED' }));
    applications.matchPreview.mockResolvedValue(makeMatch());
    show();
    expect(await screen.findByText('Cette offre n’accepte plus de candidatures')).toBeInTheDocument();
    await screen.findByText('72,5');
    expect(screen.queryByRole('button', { name: 'Postuler' })).not.toBeInTheDocument();
  });
});

describe('my applications page', () => {
  const show = () => renderAt(<ApplicationsPage />, { route: '/candidate/applications', account });
  const received = makeApplication({ id: 'app-1', jobId: 'job-1', status: 'RECEIVED' });
  const interview = makeApplication({ id: 'app-2', jobId: 'job-2', status: 'INTERVIEW', matchScore: makeMatch(88) });

  beforeEach(() => {
    jobs.get.mockImplementation(async (id) => makeJob({ id, title: id === 'job-1' ? 'Développeur backend' : 'Data engineer' }));
    applications.mine.mockResolvedValue([received, interview]);
  });

  it('lists each application with its status and score and a link to the detail', async () => {
    show();
    const rows = await screen.findAllByRole('row');
    expect(within(rows[1]).getByText('Développeur backend')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Reçue')).toBeInTheDocument();
    expect(within(rows[1]).getByText('72,5')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Entretien')).toBeInTheDocument();
    expect(within(rows[2]).getByText('88,0')).toBeInTheDocument();
    expect(within(rows[2]).getByRole('link', { name: 'Voir le détail' })).toHaveAttribute('href', '/candidate/applications?id=app-2');
  });

  it('opens the explained detail of the selected application', async () => {
    const user = userEvent.setup();
    show();
    const rows = await screen.findAllByRole('row');
    await user.click(rows[2]);
    const detail = await screen.findByRole('complementary', { name: 'Détail de la candidature' });
    expect(within(detail).getByText('Data engineer')).toBeInTheDocument();
    expect(within(detail).getByText('4 des 5 compétences requises.')).toBeInTheDocument();
    expect(within(detail).getByRole('link', { name: 'Voir l’offre' })).toHaveAttribute('href', '/candidate/jobs/job-2');
  });

  it('withdraws after a confirmation, only while the application is early enough', async () => {
    const user = userEvent.setup();
    applications.withdraw.mockResolvedValue();
    show();
    const detail = await screen.findByRole('complementary', { name: 'Détail de la candidature' });
    await user.click(within(detail).getByRole('button', { name: 'Retirer ma candidature' }));
    expect(applications.withdraw).not.toHaveBeenCalled();
    await user.click(within(detail).getByRole('button', { name: 'Oui, retirer' }));

    expect(applications.withdraw).toHaveBeenCalledWith('app-1');
    expect(await screen.findByText('Candidature retirée.', { selector: '.toast' })).toBeInTheDocument();
    expect(within(detail).queryByRole('button', { name: 'Retirer ma candidature' })).not.toBeInTheDocument();
  });

  it('offers no withdrawal once an interview is scheduled', async () => {
    const user = userEvent.setup();
    show();
    const rows = await screen.findAllByRole('row');
    await user.click(rows[2]);
    const detail = await screen.findByRole('complementary', { name: 'Détail de la candidature' });
    expect(within(detail).queryByRole('button', { name: 'Retirer ma candidature' })).not.toBeInTheDocument();
  });

  it('shows the API message when the withdrawal is refused (409)', async () => {
    const user = userEvent.setup();
    applications.withdraw.mockRejectedValue(new HttpError(409, 'Cette candidature ne peut plus être retirée.'));
    show();
    const detail = await screen.findByRole('complementary', { name: 'Détail de la candidature' });
    await user.click(within(detail).getByRole('button', { name: 'Retirer ma candidature' }));
    await user.click(within(detail).getByRole('button', { name: 'Oui, retirer' }));
    expect(await within(detail).findByRole('alert')).toHaveTextContent('ne peut plus être retirée');
  });

  it('says the score is being computed when it has not arrived yet', async () => {
    applications.mine.mockResolvedValue([makeApplication({ matchScore: null })]);
    show();
    expect(await screen.findByText(/Le score est en cours de calcul/)).toBeInTheDocument();
  });

  it('shows an empty state with a link to the offers', async () => {
    applications.mine.mockResolvedValue([]);
    show();
    expect(await screen.findByText('Aucune candidature pour l’instant')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voir les offres' })).toHaveAttribute('href', '/candidate/jobs');
  });

  it('labels an offer that can no longer be loaded instead of failing', async () => {
    jobs.get.mockRejectedValue(new HttpError(404));
    show();
    expect((await screen.findAllByText('Offre indisponible')).length).toBeGreaterThan(0);
  });
});

describe('recommendations page', () => {
  const show = () => renderAt(<RecommendationsPage />, { route: '/candidate/recommendations', account });
  const recommended = {
    jobId: 'job-9',
    title: 'Data Engineer',
    companyId: 'c1',
    companyName: 'Globex',
    location: 'Tunis',
    contractType: 'CDI' as const,
    experienceLevel: 'SENIOR' as const,
    publishedAt: '2026-10-01T09:00:00Z',
    score: 91,
  };

  it('lists the recommended offers with the estimated score', async () => {
    jobs.recommendations.mockResolvedValue([recommended]);
    show();
    const link = await screen.findByRole('link', { name: /Data Engineer/ });
    expect(link).toHaveAttribute('href', '/candidate/match/job-9');
    expect(within(link).getByText('91,0')).toBeInTheDocument();
    expect(jobs.recommendations).toHaveBeenCalledWith(10);
  });

  it('points to the profile when there is nothing to recommend yet', async () => {
    jobs.recommendations.mockResolvedValue([]);
    show();
    expect(await screen.findByText('Pas encore de recommandations')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Compléter mon profil' })).toHaveAttribute('href', '/candidate/profile');
  });

  it('shows the unavailable service (503) with a retry', async () => {
    jobs.recommendations.mockRejectedValue(new HttpError(503));
    show();
    expect(await screen.findByText('Service indisponible')).toBeInTheDocument();
  });
});

describe('candidate dashboard', () => {
  const show = () => renderAt(<CandidateDashboardPage />, { route: '/candidate', account });

  it('summarises active applications, the latest score and the recommendations', async () => {
    applications.mine.mockResolvedValue([
      makeApplication({ id: 'a1', status: 'INTERVIEW', matchScore: makeMatch(81) }),
      makeApplication({ id: 'a2', status: 'RECEIVED', matchScore: makeMatch(40) }),
      makeApplication({ id: 'a3', status: 'REJECTED' }),
      makeApplication({ id: 'a4', status: 'WITHDRAWN' }),
    ]);
    jobs.recommendations.mockResolvedValue([
      { jobId: 'j9', title: 'Data Engineer', companyId: 'c', companyName: 'Globex', location: 'Tunis', contractType: 'CDI', experienceLevel: 'SENIOR', publishedAt: null, score: 91 },
    ]);
    show();

    const active = (await screen.findByText('Candidatures actives')).closest('.tile') as HTMLElement;
    await waitFor(() => expect(within(active).getByText('2')).toBeInTheDocument());
    const last = screen.getByText('Dernier score').closest('.tile') as HTMLElement;
    expect(within(last).getByText('81,0')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: /Data Engineer/ })).toHaveAttribute('href', '/candidate/jobs/j9');
    expect(jobs.recommendations).toHaveBeenCalledWith(3);
  });

  it('shows dashes and invites the candidate to create a profile when there is nothing yet', async () => {
    candidates.getMyProfile.mockRejectedValue(new HttpError(404));
    jobs.recommendations.mockResolvedValue([]);
    show();
    expect(await screen.findByText('Créez votre profil')).toBeInTheDocument();
    const last = screen.getByText('Dernier score').closest('.tile') as HTMLElement;
    await waitFor(() => expect(within(last).getByText('—')).toBeInTheDocument());
  });

  it('asks for a CV when the profile exists without one', async () => {
    candidates.getMyProfile.mockResolvedValue(makeProfile({ cvAnalysisStatus: 'NONE' }));
    jobs.recommendations.mockResolvedValue([]);
    show();
    expect(await screen.findByText('Déposez votre CV')).toBeInTheDocument();
  });
});

