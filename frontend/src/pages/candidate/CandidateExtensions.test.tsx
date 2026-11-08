import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { applicationsApi } from '../../api/applications';
import { candidatesApi } from '../../api/candidates';
import { companiesApi } from '../../api/companies';
import { jobsApi } from '../../api/jobs';
import { HttpError } from '../../lib/errors';
import { makeAccount, makeApplication, makeCompany, makeJob, makeProfile, page } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { ApplicationsPage } from './ApplicationsPage';
import { CandidateDashboardPage } from './CandidateDashboardPage';
import { CompanyPage } from './CompanyPage';
import { JobDetailPage } from './JobDetailPage';
import { JobSearchPage } from './JobSearchPage';
import { SavedJobsPage } from './SavedJobsPage';

vi.mock('../../api/jobs', async (original) => ({
  ...(await original<typeof import('../../api/jobs')>()),
  jobsApi: { search: vi.fn(), get: vi.fn(), recommendations: vi.fn(), saved: vi.fn(), save: vi.fn(), unsave: vi.fn() },
}));
vi.mock('../../api/applications', async (original) => ({
  ...(await original<typeof import('../../api/applications')>()),
  applicationsApi: { mine: vi.fn(), apply: vi.fn(), withdraw: vi.fn() },
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
const future = (days: number) => new Date(Date.now() + days * 86400000).toISOString();
const futureDay = (days: number) => future(days).slice(0, 10);

const enriched = makeJob({
  workMode: 'REMOTE',
  salaryMin: 3500,
  salaryMax: 5000,
  applicationDeadline: futureDay(3),
  positions: 2,
  saved: false,
});

beforeEach(() => {
  vi.resetAllMocks();
  applications.mine.mockResolvedValue([]);
  jobs.get.mockResolvedValue(enriched);
  jobs.saved.mockResolvedValue([]);
  jobs.recommendations.mockResolvedValue([]);
  companies.get.mockResolvedValue(makeCompany());
  candidates.getMyProfile.mockResolvedValue(makeProfile({ cvUrl: '/cv', cvAnalysisStatus: 'DONE' }));
});

describe('enriched offer (EXT-JOB-01, EXT-SAVED-01)', () => {
  it('shows work mode, salary, positions and deadline, links the company page and saves the offer', async () => {
    const user = userEvent.setup();
    jobs.save.mockResolvedValue();
    renderAt(<JobDetailPage />, { route: '/candidate/jobs/job-1', path: '/candidate/jobs/:jobId', account });
    const props = (await screen.findByText('Mode de travail')).closest('dl') as HTMLElement;
    expect(within(props).getByText('Télétravail')).toBeInTheDocument();
    expect(within(props).getByText(/3.500 – 5.000 TND \/ mois/)).toBeInTheDocument();
    expect(within(props).getByText('2 postes ouverts')).toBeInTheDocument();
    expect(within(props).getByText('Clôture dans 3 jours')).toBeInTheDocument();
    expect(within(props).getByRole('link', { name: 'Carthage Fret' })).toHaveAttribute('href', '/candidate/companies/company-1');

    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(jobs.save).toHaveBeenCalledWith('job-1');
    expect(await screen.findByRole('button', { name: 'Enregistrée' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('filters the search by work mode and minimum salary, kept in the URL', async () => {
    const user = userEvent.setup();
    jobs.search.mockResolvedValue(page([enriched]));
    renderAt(<JobSearchPage />, { route: '/candidate/jobs?workMode=HYBRID&minSalary=3000', account });
    await screen.findByText('1 offre');
    expect(jobs.search).toHaveBeenCalledWith(expect.objectContaining({ workMode: 'HYBRID', minSalary: 3000 }));
    expect(screen.getByLabelText('Mode de travail')).toHaveValue('HYBRID');

    await user.selectOptions(screen.getByLabelText('Mode de travail'), 'REMOTE');
    await waitFor(() => expect(jobs.search).toHaveBeenLastCalledWith(expect.objectContaining({ workMode: 'REMOTE', minSalary: 3000 })));
    const row = screen.getByRole('link', { name: /Développeur backend/ }).closest('li') as HTMLElement;
    expect(within(row).getByText('Télétravail')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Ajouter aux favoris : Développeur backend' })).toBeInTheDocument();
  });
});

describe('saved offers page (EXT-SAVED-01)', () => {
  it('lists favourites, keeps closed ones without a link and removes one', async () => {
    const user = userEvent.setup();
    jobs.saved.mockResolvedValue([
      makeJob({ id: 'open', title: 'Data engineer', saved: true }),
      makeJob({ id: 'closed', title: 'Ancienne offre', status: 'CLOSED', saved: true }),
    ]);
    jobs.unsave.mockResolvedValue();
    renderAt(<SavedJobsPage />, { route: '/candidate/saved', account });
    expect(await screen.findByRole('link', { name: /Data engineer/ })).toHaveAttribute('href', '/candidate/jobs/open');
    expect(screen.queryByRole('link', { name: /Ancienne offre/ })).not.toBeInTheDocument();
    expect(screen.getByText('Clôturée')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retirer des favoris : Data engineer' }));
    expect(jobs.unsave).toHaveBeenCalledWith('open');
    await waitFor(() => expect(screen.queryByText('Data engineer')).not.toBeInTheDocument());
  });

  it('invites to browse offers when there is no favourite', async () => {
    renderAt(<SavedJobsPage />, { route: '/candidate/saved', account });
    expect(await screen.findByText('Aucune offre enregistrée')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Parcourir les offres' })).toHaveAttribute('href', '/candidate/jobs');
  });
});

describe('public company page', () => {
  const show = () =>
    renderAt(<CompanyPage />, { route: '/candidate/companies/company-1', path: '/candidate/companies/:companyId', account });

  it('presents the company and its published offers, paginated', async () => {
    const user = userEvent.setup();
    jobs.search.mockResolvedValue(page([makeJob({ title: 'Développeur Go' })], { totalElements: 11, totalPages: 2 }));
    show();
    expect(await screen.findByRole('heading', { name: 'Carthage Fret', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('Transitaire.')).toBeInTheDocument();
    expect(await screen.findByText('11 offres publiées')).toBeInTheDocument();
    expect(jobs.search).toHaveBeenCalledWith({ companyId: 'company-1', page: 0, size: 10 });
    await user.click(screen.getByRole('button', { name: 'Suivant' }));
    await waitFor(() => expect(jobs.search).toHaveBeenLastCalledWith({ companyId: 'company-1', page: 1, size: 10 }));
  });

  it('says when the company does not exist', async () => {
    companies.get.mockRejectedValue(new HttpError(404));
    jobs.search.mockResolvedValue(page([]));
    show();
    expect(await screen.findByText('Entreprise introuvable')).toBeInTheDocument();
  });
});

describe('candidate follow-up (EXT-APP-01/02)', () => {
  const planned = makeApplication({
    id: 'app-iv',
    status: 'INTERVIEW',
    coverLetter: 'Ma lettre.',
    interview: { scheduledAt: future(2), durationMinutes: 45, mode: 'VIDEO', location: 'https://meet.demo.tn/abc' },
    statusHistory: [
      { status: 'RECEIVED', changedAt: '2026-10-05T09:00:00Z', changedBy: 'candidate-id' },
      { status: 'IN_REVIEW', changedAt: '2026-10-06T09:00:00Z', changedBy: 'recruiter-id', message: null },
      { status: 'INTERVIEW', changedAt: '2026-10-07T09:00:00Z', changedBy: 'recruiter-id', message: 'Entretien avec le lead.' },
    ],
  });

  it('shows the interview, the recruiter’s message and the cover letter of an application', async () => {
    applications.mine.mockResolvedValue([planned]);
    renderAt(<ApplicationsPage />, { route: '/candidate/applications', account });
    const detail = await screen.findByRole('complementary', { name: 'Détail de la candidature' });
    expect(within(detail).getByText('Votre entretien')).toBeInTheDocument();
    expect(within(detail).getByText('Visioconférence')).toBeInTheDocument();
    expect(within(detail).getByRole('link', { name: 'https://meet.demo.tn/abc' })).toBeInTheDocument();
    expect(within(detail).getByText('Entretien avec le lead.')).toBeInTheDocument();
    expect(within(detail).getByText('Ma lettre de motivation')).toBeInTheDocument();
  });

  it('lists upcoming interviews and counts favourites on the dashboard', async () => {
    applications.mine.mockResolvedValue([planned, makeApplication({ id: 'other', status: 'IN_REVIEW' })]);
    jobs.saved.mockResolvedValue([makeJob({ saved: true }), makeJob({ id: 'job-2', saved: true })]);
    renderAt(<CandidateDashboardPage />, { route: '/candidate', account });
    const section = (await screen.findByRole('heading', { name: 'Entretiens à venir' })).closest('section') as HTMLElement;
    expect(await within(section).findByText('Développeur backend')).toBeInTheDocument();
    expect(within(section).getByRole('link', { name: 'Rejoindre la visio' })).toHaveAttribute('href', 'https://meet.demo.tn/abc');
    expect(within(section).getByRole('link', { name: 'Voir la candidature' })).toHaveAttribute('href', '/candidate/applications?id=app-iv');
    const tile = (await screen.findByText('Offres en favori')).closest('.tile') as HTMLElement;
    expect(await within(tile).findByText('2')).toBeInTheDocument();
  });
});
