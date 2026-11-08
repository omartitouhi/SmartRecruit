import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { applicationsApi } from '../../api/applications';
import { candidatesApi } from '../../api/candidates';
import { companiesApi } from '../../api/companies';
import { dashboardApi } from '../../api/dashboard';
import { jobsApi } from '../../api/jobs';
import { HttpError } from '../../lib/errors';
import { makeAccount, makeCompany, makeCompanyStats, makeGlobalStats, makeJob, makeProfile, page } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { AdminDashboardPage } from '../admin/AdminDashboardPage';
import { NewJobPage } from './NewJobPage';
import { RecruiterDashboardPage } from './RecruiterDashboardPage';
import { RecruiterJobsPage } from './RecruiterJobsPage';

vi.mock('../../api/jobs', async (original) => ({
  ...(await original<typeof import('../../api/jobs')>()),
  jobsApi: { search: vi.fn(), get: vi.fn(), create: vi.fn(), updateStatus: vi.fn(), duplicate: vi.fn() },
}));
vi.mock('../../api/applications', async (original) => ({
  ...(await original<typeof import('../../api/applications')>()),
  applicationsApi: { forJob: vi.fn(), upcomingInterviews: vi.fn() },
}));
vi.mock('../../api/companies', async (original) => ({
  ...(await original<typeof import('../../api/companies')>()),
  companiesApi: { getMine: vi.fn() },
}));
vi.mock('../../api/candidates', async (original) => ({
  ...(await original<typeof import('../../api/candidates')>()),
  candidatesApi: { getCandidate: vi.fn() },
}));
vi.mock('../../api/dashboard', async (original) => ({
  ...(await original<typeof import('../../api/dashboard')>()),
  dashboardApi: { companyStats: vi.fn(), globalStats: vi.fn() },
}));

const jobs = vi.mocked(jobsApi);
const applications = vi.mocked(applicationsApi);
const companies = vi.mocked(companiesApi);
const candidates = vi.mocked(candidatesApi);
const dashboard = vi.mocked(dashboardApi);
const recruiter = makeAccount('RECRUITER', { id: 'recruiter-id' });
const day = (offset: number) => new Date(Date.now() + offset * 86400000).toISOString().slice(0, 10);

beforeEach(() => {
  vi.resetAllMocks();
  companies.getMine.mockResolvedValue(makeCompany());
  applications.upcomingInterviews.mockResolvedValue([]);
  applications.forJob.mockResolvedValue([]);
});

describe('job form extras (EXT-JOB-01)', () => {
  it('sends work mode, salary range, deadline and positions', async () => {
    const user = userEvent.setup();
    jobs.create.mockResolvedValue(makeJob());
    renderAt(<NewJobPage />, { route: '/recruiter/jobs/new', account: recruiter });
    await user.type(await screen.findByLabelText('Intitulé du poste'), 'Développeur Go');
    await user.type(screen.getByLabelText('Lieu'), 'Tunis');
    await user.type(screen.getByLabelText('Compétences demandées'), 'Go{Enter}');
    await user.type(screen.getByLabelText('Description du poste'), 'Vous construisez nos services de paiement.');
    await user.selectOptions(screen.getByLabelText('Mode de travail'), 'HYBRID');
    const positions = screen.getByLabelText('Nombre de postes');
    await user.clear(positions);
    await user.type(positions, '3');
    await user.type(screen.getByLabelText('Salaire minimum (TND brut / mois)'), '2500');
    await user.type(screen.getByLabelText('Salaire maximum (TND brut / mois)'), '3500');
    await user.type(screen.getByLabelText('Date limite de candidature'), day(10));
    await user.click(screen.getByRole('button', { name: 'Publier l’offre' }));

    expect(jobs.create).toHaveBeenCalledWith(
      expect.objectContaining({ workMode: 'HYBRID', positions: 3, salaryMin: 2500, salaryMax: 3500, applicationDeadline: day(10) }),
    );
  });

  it('refuses an inverted salary range and a past deadline before calling the API', async () => {
    const user = userEvent.setup();
    renderAt(<NewJobPage />, { route: '/recruiter/jobs/new', account: recruiter });
    await user.type(await screen.findByLabelText('Salaire minimum (TND brut / mois)'), '4000');
    await user.type(screen.getByLabelText('Salaire maximum (TND brut / mois)'), '3000');
    await user.type(screen.getByLabelText('Date limite de candidature'), day(-2));
    await user.click(screen.getByRole('button', { name: 'Publier l’offre' }));
    expect(screen.getByText('Le maximum doit être supérieur ou égal au minimum.')).toBeInTheDocument();
    expect(screen.getByText('Choisissez aujourd’hui ou une date future.')).toBeInTheDocument();
    expect(jobs.create).not.toHaveBeenCalled();
  });
});

describe('duplicating an offer (EXT-JOB-01)', () => {
  it('adds the draft copy at the top of the list', async () => {
    const user = userEvent.setup();
    jobs.search.mockResolvedValue(
      page([makeJob({ id: 'j1', title: 'Backend', applicationDeadline: day(2), positions: 2, applicationsCount: 4 }), makeJob({ id: 'm', title: 'Retirée', status: 'MODERATED' })]),
    );
    jobs.duplicate.mockResolvedValue(makeJob({ id: 'copy', title: 'Backend (copie)', status: 'DRAFT', publishedAt: null }));
    renderAt(<RecruiterJobsPage />, { route: '/recruiter/jobs', account: recruiter });
    const row = (await screen.findByRole('link', { name: 'Backend' })).closest('tr') as HTMLElement;
    expect(within(row).getByText('Clôture dans 2 jours')).toBeInTheDocument();
    expect(within(row).getByText(/2 postes/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Dupliquer l’offre Retirée' })).not.toBeInTheDocument();

    await user.click(within(row).getByRole('button', { name: 'Dupliquer l’offre Backend' }));
    expect(jobs.duplicate).toHaveBeenCalledWith('j1');
    const rows = await screen.findAllByRole('row');
    expect(within(rows[1]).getByText('Backend (copie)')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Brouillon')).toBeInTheDocument();
    expect(screen.getByText(/Brouillon « Backend \(copie\) » créé/, { selector: '.toast' })).toBeInTheDocument();
  });

  it('shows why a duplication is refused', async () => {
    const user = userEvent.setup();
    jobs.search.mockResolvedValue(page([makeJob({ id: 'j1', title: 'Backend' })]));
    jobs.duplicate.mockRejectedValue(new HttpError(409, 'Une offre retirée par modération ne peut pas être dupliquée.'));
    renderAt(<RecruiterJobsPage />, { route: '/recruiter/jobs', account: recruiter });
    await user.click(await screen.findByRole('button', { name: 'Dupliquer l’offre Backend' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('ne peut pas être dupliquée');
  });
});

describe('recruiter dashboard pace (EXT-DASH-01)', () => {
  it('shows the funnel, the decision delay, the next interviews and the offers closing soon', async () => {
    dashboard.companyStats.mockResolvedValue(makeCompanyStats());
    jobs.search.mockResolvedValue(
      page([
        makeJob({ id: 'soon', title: 'Front', applicationDeadline: day(3), applicationsCount: 2 }),
        makeJob({ id: 'later', title: 'Data', applicationDeadline: day(30) }),
      ]),
    );
    applications.upcomingInterviews.mockResolvedValue([
      { applicationId: 'a1', jobId: 'soon', candidateId: 'c1', scheduledAt: new Date(Date.now() + 86400000).toISOString(), durationMinutes: 45, mode: 'VIDEO', location: null },
    ]);
    jobs.get.mockResolvedValue(makeJob({ id: 'soon', title: 'Front' }));
    candidates.getCandidate.mockResolvedValue(makeProfile({ id: 'c1', firstName: 'Sami', lastName: 'Ben Ali' }));
    renderAt(<RecruiterDashboardPage />, { route: '/recruiter', account: recruiter });

    const tile = async (label: string) => (await screen.findByText(label)).closest('.tile') as HTMLElement;
    expect(within(await tile('Délai moyen de décision')).getByText('6,5 jours')).toBeInTheDocument();
    expect(within(await tile('Entretiens à venir')).getByText('1')).toBeInTheDocument();
    const funnel = (await screen.findByRole('heading', { name: 'Entonnoir de recrutement' })).closest('section') as HTMLElement;
    expect(within(funnel).getByText('Non retenue')).toBeInTheDocument();

    const agenda = (await screen.findByRole('heading', { name: 'Prochains entretiens' })).closest('section') as HTMLElement;
    expect(await within(agenda).findByText('Sami Ben Ali')).toBeInTheDocument();
    expect(within(agenda).getByRole('link', { name: 'Préparer' })).toHaveAttribute('href', '/recruiter/jobs/soon/candidates?id=a1');

    const closing = (await screen.findByRole('heading', { name: 'Clôture dans les 7 jours' })).closest('section') as HTMLElement;
    expect(await within(closing).findByText('Front')).toBeInTheDocument();
    expect(within(closing).queryByText('Data')).not.toBeInTheDocument();
    expect(within(closing).getByRole('link', { name: 'Prolonger' })).toHaveAttribute('href', '/recruiter/jobs/soon/edit');
  });

  it('shows dashes when the new figures are unavailable', async () => {
    dashboard.companyStats.mockResolvedValue(
      makeCompanyStats({ applicationsByStatus: null, averageDecisionDays: null, upcomingInterviews: null, jobsClosingSoon: null }),
    );
    jobs.search.mockResolvedValue(page([]));
    renderAt(<RecruiterDashboardPage />, { route: '/recruiter', account: recruiter });
    const tile = (await screen.findByText('Délai moyen de décision')).closest('.tile') as HTMLElement;
    expect(within(tile).getByText('—')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Entonnoir de recrutement' })).not.toBeInTheDocument();
    expect(await screen.findByText('Aucun entretien planifié')).toBeInTheDocument();
  });
});

describe('admin dashboard pace (EXT-DASH-01)', () => {
  it('shows the platform funnel, decision delay and offers closing soon', async () => {
    dashboard.globalStats.mockResolvedValue(makeGlobalStats());
    renderAt(<AdminDashboardPage />, { route: '/admin', account: makeAccount('ADMIN') });
    const tile = async (label: string) => (await screen.findByText(label)).closest('.tile') as HTMLElement;
    expect(within(await tile('Délai moyen de décision')).getByText('7,5 jours')).toBeInTheDocument();
    expect(within(await tile('Offres clôturées sous 7 jours')).getByText('2')).toBeInTheDocument();
    const funnel = (await screen.findByRole('heading', { name: 'Entonnoir des candidatures (plateforme)' })).closest('section') as HTMLElement;
    expect(within(funnel).getAllByRole('listitem')).toHaveLength(5);
  });
});
