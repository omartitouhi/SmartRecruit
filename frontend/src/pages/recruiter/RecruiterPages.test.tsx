import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { applicationsApi } from '../../api/applications';
import { candidatesApi } from '../../api/candidates';
import { companiesApi } from '../../api/companies';
import { dashboardApi } from '../../api/dashboard';
import { jobsApi } from '../../api/jobs';
import { HttpError } from '../../lib/errors';
import { makeAccount, makeApplication, makeCompany, makeInvitation, makeJob, makeMatch, makeProfile, page } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { AcceptInvitationPage } from './AcceptInvitationPage';
import { CompanyProfilePage } from './CompanyProfilePage';
import { EditJobPage } from './EditJobPage';
import { NewJobPage } from './NewJobPage';
import { RecruiterDashboardPage } from './RecruiterDashboardPage';
import { RecruiterJobsPage } from './RecruiterJobsPage';
import { TeamPage } from './TeamPage';

vi.mock('../../api/jobs', async (original) => ({
  ...(await original<typeof import('../../api/jobs')>()),
  jobsApi: {
    search: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    updateStatus: vi.fn(),
    recommendations: vi.fn(),
    duplicate: vi.fn(),
  },
}));
vi.mock('../../api/applications', async (original) => ({
  ...(await original<typeof import('../../api/applications')>()),
  applicationsApi: { forJob: vi.fn(), upcomingInterviews: vi.fn() },
}));
vi.mock('../../api/companies', async (original) => ({
  ...(await original<typeof import('../../api/companies')>()),
  companiesApi: {
    create: vi.fn(),
    getMine: vi.fn(),
    get: vi.fn(),
    update: vi.fn(),
    invite: vi.fn(),
    invitations: vi.fn(),
    acceptInvitation: vi.fn(),
  },
}));
vi.mock('../../api/candidates', async (original) => ({
  ...(await original<typeof import('../../api/candidates')>()),
  candidatesApi: { getCandidate: vi.fn() },
}));
vi.mock('../../api/dashboard', async (original) => ({
  ...(await original<typeof import('../../api/dashboard')>()),
  dashboardApi: { companyStats: vi.fn() },
}));

const candidates = vi.mocked(candidatesApi);
const jobs = vi.mocked(jobsApi);
const applications = vi.mocked(applicationsApi);
const companies = vi.mocked(companiesApi);
const dashboard = vi.mocked(dashboardApi);
const account = makeAccount('RECRUITER', { id: 'recruiter-id' });
const NO_EXTRA_STATS = { applicationsByStatus: null, averageDecisionDays: null, upcomingInterviews: null, jobsClosingSoon: null };

beforeEach(() => {
  vi.resetAllMocks();
  companies.getMine.mockResolvedValue(makeCompany());
  applications.upcomingInterviews.mockResolvedValue([]);
});

const noCompany = () => companies.getMine.mockRejectedValue(new HttpError(404));

describe('"Mes offres"', () => {
  const show = () => renderAt(<RecruiterJobsPage />, { route: '/recruiter/jobs', account });
  const rowOf = async (title: string) => (await screen.findByText(title)).closest('tr') as HTMLElement;

  beforeEach(() => {
    jobs.search.mockResolvedValue(
      page([
        makeJob({ id: 'j1', title: 'Backend', status: 'PUBLISHED', applicationsCount: 6 }),
        makeJob({ id: 'j2', title: 'Brouillon stage', status: 'DRAFT', publishedAt: null }),
        makeJob({ id: 'j3', title: 'Ancienne offre', status: 'CLOSED', applicationsCount: 2 }),
        makeJob({ id: 'j4', title: 'Offre retirée', status: 'MODERATED', moderationReason: 'Doublon', applicationsCount: 0 }),
      ]),
    );
  });

  it('lists my offers (companyId=me) with status, publication date and application count', async () => {
    show();
    const published = await rowOf('Backend');
    expect(jobs.search).toHaveBeenCalledWith({ companyId: 'me', size: 50 });
    expect(within(published).getByText('Publiée')).toBeInTheDocument();
    expect(within(published).getByText('6 candidatures')).toBeInTheDocument();
    expect(within(published).getByText(/1 oct/)).toBeInTheDocument();
    expect(within(await rowOf('Brouillon stage')).getByText('Brouillon')).toBeInTheDocument();
  });

  it('links an offer to its received applications', async () => {
    show();
    expect(within(await rowOf('Backend')).getByRole('link', { name: 'Backend' })).toHaveAttribute('href', '/recruiter/jobs/j1/candidates');
    expect(within(await rowOf('Backend')).getByRole('link', { name: '6 candidatures' })).toHaveAttribute('href', '/recruiter/jobs/j1/candidates');
  });

  it('proposes the right status action for each status and none for a moderated offer', async () => {
    show();
    expect(within(await rowOf('Backend')).getByRole('button', { name: 'Clôturer' })).toBeInTheDocument();
    expect(within(await rowOf('Brouillon stage')).getByRole('button', { name: 'Publier' })).toBeInTheDocument();
    expect(within(await rowOf('Ancienne offre')).getByRole('button', { name: 'Rouvrir' })).toBeInTheDocument();
    const moderated = await rowOf('Offre retirée');
    expect(within(moderated).queryByRole('button')).not.toBeInTheDocument();
    expect(within(moderated).getByText('Motif : Doublon')).toBeInTheDocument();
  });

  it('offers "Modifier" on a draft or published offer only, linking to its edit page', async () => {
    show();
    const published = within(await rowOf('Backend')).getByRole('link', { name: 'Modifier' });
    expect(published).toHaveAttribute('href', '/recruiter/jobs/j1/edit');
    expect(within(await rowOf('Brouillon stage')).getByRole('link', { name: 'Modifier' })).toHaveAttribute('href', '/recruiter/jobs/j2/edit');
    expect(within(await rowOf('Ancienne offre')).queryByRole('link', { name: 'Modifier' })).not.toBeInTheDocument();
    expect(within(await rowOf('Offre retirée')).queryByRole('link', { name: 'Modifier' })).not.toBeInTheDocument();
  });

  it('changes the status in place and confirms', async () => {
    const user = userEvent.setup();
    jobs.updateStatus.mockResolvedValue(makeJob({ id: 'j1', title: 'Backend', status: 'CLOSED' }));
    show();
    await user.click(within(await rowOf('Backend')).getByRole('button', { name: 'Clôturer' }));

    expect(jobs.updateStatus).toHaveBeenCalledWith('j1', 'CLOSED');
    expect(await screen.findByText('Offre clôturée.', { selector: '.toast' })).toBeInTheDocument();
    const row = await rowOf('Backend');
    expect(within(row).getByText('Clôturée')).toBeInTheDocument();
    expect(within(row).getByText('6 candidatures')).toBeInTheDocument();
    expect(jobs.search).toHaveBeenCalledTimes(1);
  });

  it('shows the API message when the change is refused', async () => {
    const user = userEvent.setup();
    jobs.updateStatus.mockRejectedValue(new HttpError(403, 'Cette offre a été modérée par l’administration.'));
    show();
    await user.click(within(await rowOf('Backend')).getByRole('button', { name: 'Clôturer' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('modérée par l’administration');
  });

  it('asks a recruiter without a company to create it first', async () => {
    noCompany();
    show();
    expect(await screen.findByText('Présentez d’abord votre entreprise')).toBeInTheDocument();
    expect(jobs.search).not.toHaveBeenCalled();
  });

  it('shows an empty state and an error state', async () => {
    jobs.search.mockResolvedValueOnce(page([]));
    const { unmount } = show();
    expect(await screen.findByText('Aucune offre pour l’instant')).toBeInTheDocument();
    unmount();
    jobs.search.mockRejectedValue(new HttpError(503));
    show();
    expect(await screen.findByText('Service indisponible')).toBeInTheDocument();
  });
});

describe('publication form', () => {
  const show = () => renderAt(<NewJobPage />, { route: '/recruiter/jobs/new', account });
  const fill = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.type(await screen.findByLabelText('Intitulé du poste'), 'Développeur Go');
    await user.type(screen.getByLabelText('Lieu'), 'Tunis');
    await user.type(screen.getByLabelText('Compétences demandées'), 'Go{Enter}PostgreSQL{Enter}');
    await user.type(screen.getByLabelText('Description du poste'), 'Vous construisez nos services de paiement.');
  };

  it('blocks the submission and names each empty required field', async () => {
    const user = userEvent.setup();
    show();
    await user.click(await screen.findByRole('button', { name: 'Publier l’offre' }));
    expect(screen.getByText('L’intitulé est obligatoire.')).toBeInTheDocument();
    expect(screen.getByText('Le lieu est obligatoire.')).toBeInTheDocument();
    expect(screen.getByText(/Ajoutez au moins une compétence/)).toBeInTheDocument();
    expect(screen.getByText(/20 caractères au minimum/)).toBeInTheDocument();
    expect(jobs.create).not.toHaveBeenCalled();
  });

  it('publishes without a companyId and lands on "Mes offres"', async () => {
    const user = userEvent.setup();
    jobs.create.mockResolvedValue(makeJob());
    show();
    await fill(user);
    await user.selectOptions(screen.getByLabelText('Niveau'), 'SENIOR');
    await user.click(screen.getByRole('button', { name: 'Publier l’offre' }));

    await waitFor(() => expect(jobs.create).toHaveBeenCalledTimes(1));
    expect(jobs.create).toHaveBeenCalledWith({
      title: 'Développeur Go',
      description: 'Vous construisez nos services de paiement.',
      requiredSkills: ['Go', 'PostgreSQL'],
      experienceLevel: 'SENIOR',
      contractType: 'CDI',
      location: 'Tunis',
      workMode: 'ON_SITE',
      salaryMin: null,
      salaryMax: null,
      applicationDeadline: null,
      positions: 1,
    });
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/recruiter/jobs'));
  });

  it('shows the server field errors under the right field', async () => {
    const user = userEvent.setup();
    jobs.create.mockRejectedValue(new HttpError(400, 'Requête invalide', { title: 'Intitulé trop long' }));
    show();
    await fill(user);
    await user.click(screen.getByRole('button', { name: 'Publier l’offre' }));
    expect(await screen.findByText('Intitulé trop long')).toBeInTheDocument();
    expect(screen.getByLabelText('Intitulé du poste')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('button', { name: 'Publier l’offre' })).toBeEnabled();
  });

  it('asks for the company first when the recruiter has none', async () => {
    noCompany();
    show();
    expect(await screen.findByText('Présentez d’abord votre entreprise')).toBeInTheDocument();
  });
});

describe('edit form', () => {
  const show = () => renderAt(<EditJobPage />, { route: '/recruiter/jobs/job-1/edit', path: '/recruiter/jobs/:jobId/edit', account });
  const save = (user: ReturnType<typeof userEvent.setup>) => user.click(screen.getByRole('button', { name: 'Enregistrer' }));
  const retitle = async (user: ReturnType<typeof userEvent.setup>, title: string) => {
    const field = await screen.findByLabelText('Intitulé du poste');
    await user.clear(field);
    await user.type(field, title);
  };

  beforeEach(() => {
    jobs.get.mockResolvedValue(makeJob({ requiredSkills: ['Java', 'Kubernetes'] }));
  });

  it('pre-fills the form with the saved offer', async () => {
    show();
    expect(await screen.findByLabelText('Intitulé du poste')).toHaveValue('Développeur backend');
    expect(screen.getByLabelText('Lieu')).toHaveValue('Tunis');
    expect(screen.getByLabelText('Contrat')).toHaveValue('CDI');
    expect(screen.getByLabelText('Niveau')).toHaveValue('SENIOR');
    expect(screen.getByLabelText('Description du poste')).toHaveValue('Vous construisez nos services.');
    expect(screen.getByText('Kubernetes')).toBeInTheDocument();
    expect(jobs.get).toHaveBeenCalledWith('job-1');
  });

  it('sends only the changed fields, then confirms with a link back to "Mes offres"', async () => {
    const user = userEvent.setup();
    jobs.update.mockResolvedValue(makeJob({ title: 'Backend Java', requiredSkills: ['Java', 'Kubernetes'] }));
    show();
    await retitle(user, 'Backend Java');
    await user.selectOptions(screen.getByLabelText('Contrat'), 'CDD');
    await save(user);

    await waitFor(() => expect(jobs.update).toHaveBeenCalledTimes(1));
    expect(jobs.update).toHaveBeenCalledWith('job-1', { title: 'Backend Java', contractType: 'CDD' });
    expect(await screen.findByText('Offre mise à jour.', { selector: '.toast' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Retour à Mes offres' })).toHaveAttribute('href', '/recruiter/jobs');
  });

  it('compares the next save with the last saved values', async () => {
    const user = userEvent.setup();
    jobs.update.mockResolvedValue(makeJob({ title: 'Backend Java' }));
    show();
    await retitle(user, 'Backend Java');
    await save(user);
    await user.type(screen.getByLabelText('Lieu'), ' Nord');
    await save(user);
    await waitFor(() => expect(jobs.update).toHaveBeenCalledTimes(2));
    expect(jobs.update).toHaveBeenLastCalledWith('job-1', { location: 'Tunis Nord' });
  });

  it('does not call the API when nothing changed', async () => {
    const user = userEvent.setup();
    show();
    await screen.findByLabelText('Intitulé du poste');
    await save(user);
    expect(await screen.findByText('Aucune modification à enregistrer.', { selector: '.toast' })).toBeInTheDocument();
    expect(jobs.update).not.toHaveBeenCalled();
  });

  it('blocks an emptied required field before calling the API', async () => {
    const user = userEvent.setup();
    show();
    await user.clear(await screen.findByLabelText('Intitulé du poste'));
    await save(user);
    expect(screen.getByText('L’intitulé est obligatoire.')).toBeInTheDocument();
    expect(jobs.update).not.toHaveBeenCalled();
  });

  it('shows the 400 field errors under the right field', async () => {
    const user = userEvent.setup();
    jobs.update.mockRejectedValue(new HttpError(400, 'Requête invalide', { title: 'Intitulé trop long' }));
    show();
    await retitle(user, 'Autre titre');
    await save(user);
    expect(await screen.findByText('Intitulé trop long')).toBeInTheDocument();
    expect(screen.getByLabelText('Intitulé du poste')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeEnabled();
  });

  it.each([403, 409])('explains a %i as a closed or withdrawn offer', async (status) => {
    const user = userEvent.setup();
    jobs.update.mockRejectedValue(new HttpError(status));
    show();
    await retitle(user, 'Autre titre');
    await save(user);
    expect(await screen.findByRole('alert')).toHaveTextContent('Offre clôturée ou retirée : non modifiable.');
  });

  it.each(['CLOSED', 'MODERATED'] as const)('shows no form for a %s offer', async (status) => {
    jobs.get.mockResolvedValue(makeJob({ status }));
    show();
    expect(await screen.findByRole('alert')).toHaveTextContent('Offre clôturée ou retirée : non modifiable.');
    expect(screen.queryByLabelText('Intitulé du poste')).not.toBeInTheDocument();
  });

  it('shows an error state when the offer cannot be loaded', async () => {
    jobs.get.mockRejectedValue(new HttpError(404, 'Offre introuvable.'));
    show();
    expect(await screen.findByText('Offre introuvable.')).toBeInTheDocument();
  });
});

describe('company page', () => {
  const show = () => renderAt(<CompanyProfilePage />, { route: '/recruiter/company', account });

  it('creates the company then goes to the recruiter dashboard', async () => {
    const user = userEvent.setup();
    noCompany();
    companies.create.mockResolvedValue(makeCompany({ name: 'Acme' }));
    show();
    await user.type(await screen.findByLabelText(/Nom de l’entreprise/), 'Acme');
    await user.type(screen.getByLabelText(/Secteur/), 'Logiciel');
    await user.type(screen.getByLabelText('Présentation'), 'Éditeur.');
    await user.click(screen.getByRole('button', { name: 'Créer mon entreprise' }));

    await waitFor(() => expect(companies.create).toHaveBeenCalledWith({ name: 'Acme', sector: 'Logiciel', description: 'Éditeur.' }));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/recruiter'));
  });

  it('requires a name and a sector', async () => {
    const user = userEvent.setup();
    noCompany();
    show();
    await user.click(await screen.findByRole('button', { name: 'Créer mon entreprise' }));
    expect(screen.getByText('Le nom est obligatoire.')).toBeInTheDocument();
    expect(screen.getByText('Le secteur est obligatoire.')).toBeInTheDocument();
    expect(companies.create).not.toHaveBeenCalled();
  });

  it('pre-fills the existing company and saves with PATCH', async () => {
    const user = userEvent.setup();
    companies.update.mockImplementation(async (_id, patch) => makeCompany(patch));
    show();
    expect(await screen.findByLabelText(/Nom de l’entreprise/)).toHaveValue('Carthage Fret');
    expect(screen.getByLabelText(/Secteur/)).toHaveValue('Logistique');
    await user.clear(screen.getByLabelText('Présentation'));
    await user.type(screen.getByLabelText('Présentation'), 'Nouveau texte.');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    await waitFor(() => expect(companies.update).toHaveBeenCalledTimes(1));
    expect(companies.update).toHaveBeenCalledWith('company-1', { name: 'Carthage Fret', sector: 'Logistique', description: 'Nouveau texte.' });
    expect(await screen.findByText('Entreprise enregistrée.', { selector: '.toast' })).toBeInTheDocument();
    expect(screen.queryByTestId('location')).not.toBeInTheDocument();
  });

  it('explains a 409 when the recruiter already has a company', async () => {
    const user = userEvent.setup();
    noCompany();
    companies.create.mockRejectedValue(new HttpError(409, 'Vous avez déjà une entreprise.'));
    show();
    await user.type(await screen.findByLabelText(/Nom de l’entreprise/), 'Acme');
    await user.type(screen.getByLabelText(/Secteur/), 'IT');
    await user.click(screen.getByRole('button', { name: 'Créer mon entreprise' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Vous avez déjà une entreprise.');
  });
});

describe('team page', () => {
  const show = () => renderAt(<TeamPage />, { route: '/recruiter/team', account });

  beforeEach(() => {
    companies.getMine.mockResolvedValue(makeCompany({ members: ['recruiter-id', 'abcdef12-0000-0000-0000-000000000000'] }));
    companies.invitations.mockResolvedValue([makeInvitation({ id: 'i1', email: 'old@demo.tn', status: 'ACCEPTED' })]);
  });

  it('lists the members, marking the current user, and the invitations with their status', async () => {
    show();
    const members = (await screen.findByRole('heading', { name: 'Membres' })).closest('section') as HTMLElement;
    expect(within(members).getByText('Vous')).toBeInTheDocument();
    expect(within(members).getByText('abcdef12')).toBeInTheDocument();
    const list = await screen.findByRole('list', { name: 'Invitations envoyées' });
    expect(within(list).getByText('old@demo.tn')).toBeInTheDocument();
    expect(within(list).getByText('Acceptée')).toBeInTheDocument();
  });

  it('sends an invitation and shows it as "En attente"', async () => {
    const user = userEvent.setup();
    companies.invite.mockResolvedValue(makeInvitation({ id: 'i2', email: 'new@demo.tn' }));
    show();
    await user.type(await screen.findByLabelText('Email du recruteur'), ' new@demo.tn ');
    await user.click(screen.getByRole('button', { name: 'Envoyer l’invitation' }));

    expect(companies.invite).toHaveBeenCalledWith('company-1', 'new@demo.tn');
    const list = await screen.findByRole('list', { name: 'Invitations envoyées' });
    const row = within(list).getByText('new@demo.tn').closest('li') as HTMLElement;
    expect(within(row).getByText('En attente')).toBeInTheDocument();
    expect(screen.getByLabelText('Email du recruteur')).toHaveValue('');
  });

  it('rejects a malformed email without calling the API', async () => {
    const user = userEvent.setup();
    show();
    await user.type(await screen.findByLabelText('Email du recruteur'), 'pas-un-email');
    await user.click(screen.getByRole('button', { name: 'Envoyer l’invitation' }));
    expect(screen.getByText(/Saisissez un email valide/)).toBeInTheDocument();
    expect(companies.invite).not.toHaveBeenCalled();
  });

  it('shows the 409 message for a duplicate pending invitation', async () => {
    const user = userEvent.setup();
    companies.invite.mockRejectedValue(new HttpError(409, 'Une invitation est déjà en attente pour cet email.'));
    show();
    await user.type(await screen.findByLabelText('Email du recruteur'), 'dup@demo.tn');
    await user.click(screen.getByRole('button', { name: 'Envoyer l’invitation' }));
    expect(await screen.findByText('Une invitation est déjà en attente pour cet email.')).toBeInTheDocument();
  });

  it('shows an expired invitation as such', async () => {
    companies.invitations.mockResolvedValue([makeInvitation({ status: 'EXPIRED' })]);
    show();
    expect(await screen.findByText('Expirée')).toBeInTheDocument();
  });

  it('asks for the company first when there is none', async () => {
    noCompany();
    show();
    expect(await screen.findByText('Présentez d’abord votre entreprise')).toBeInTheDocument();
  });
});

describe('accept-invitation page', () => {
  const show = (query = '?token=abc123') =>
    renderAt(<AcceptInvitationPage />, { route: `/accept-invitation${query}`, path: '/accept-invitation', account });

  it('says the link is incomplete when the token is missing', () => {
    show('');
    expect(screen.getByText('Lien d’invitation incomplet')).toBeInTheDocument();
    expect(companies.acceptInvitation).not.toHaveBeenCalled();
  });

  it('accepts the invitation from ?token= only after a click, then goes to the dashboard', async () => {
    const user = userEvent.setup();
    companies.acceptInvitation.mockResolvedValue(makeCompany({ name: 'Acme' }));
    show();
    expect(companies.acceptInvitation).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Accepter l’invitation' }));

    expect(companies.acceptInvitation).toHaveBeenCalledWith('abc123');
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/recruiter'));
  });

  it.each([
    [410, /a expiré/],
    [400, /pas valide ou a déjà été utilisée/],
    [409, /déjà rattaché à une entreprise/],
  ])('explains a %s answer', async (status, message) => {
    const user = userEvent.setup();
    companies.acceptInvitation.mockRejectedValue(new HttpError(status));
    show();
    await user.click(screen.getByRole('button', { name: 'Accepter l’invitation' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByRole('button', { name: 'Accepter l’invitation' })).toBeEnabled();
  });
});

describe('recruiter dashboard', () => {
  const show = () => renderAt(<RecruiterDashboardPage />, { route: '/recruiter', account });

  beforeEach(() => {
    dashboard.companyStats.mockResolvedValue({ publishedJobs: 2, applicationsReceived: 8, acceptedApplications: 1, acceptanceRate: 0.5, ...NO_EXTRA_STATS });
    jobs.search.mockResolvedValue(
      page([
        makeJob({ id: 'j1', title: 'Backend', applicationsCount: 5 }),
        makeJob({ id: 'j2', title: 'Front', applicationsCount: 3 }),
        makeJob({ id: 'j3', title: 'Vide', applicationsCount: 0 }),
      ]),
    );
    candidates.getCandidate.mockImplementation(async (id) =>
      id === 'c-deleted' ? { id, deleted: true } : makeProfile({ id, firstName: 'Sami', lastName: 'Ben Ali' }),
    );
    applications.forJob.mockImplementation(async (id) =>
      id === 'j1'
        ? [makeApplication({ id: 'a1', matchScore: makeMatch(70) }), makeApplication({ id: 'a2', matchScore: null })]
        : [makeApplication({ id: 'a3', jobId: 'j2', matchScore: makeMatch(92), status: 'IN_REVIEW' })],
    );
  });

  it('shows the key figures of the company', async () => {
    show();
    const tile = async (label: string) => ((await screen.findByText(label)).closest('.tile') as HTMLElement);
    expect(within(await tile('Offres publiées')).getByText('2')).toBeInTheDocument();
    expect(dashboard.companyStats).toHaveBeenCalledWith('company-1');
    expect(within(await tile('Candidatures reçues')).getByText('8')).toBeInTheDocument();
    expect(within(await tile('Candidatures acceptées')).getByText('1')).toBeInTheDocument();
    expect(within(await tile('Taux d’acceptation')).getByText('50 %')).toBeInTheDocument();
  });

  it('shows a dash instead of a rate when nothing is decided yet', async () => {
    dashboard.companyStats.mockResolvedValue({ publishedJobs: 1, applicationsReceived: 0, acceptedApplications: 0, acceptanceRate: null, ...NO_EXTRA_STATS });
    show();
    expect(within(((await screen.findByText('Taux d’acceptation')).closest('.tile')) as HTMLElement).getByText('—')).toBeInTheDocument();
  });

  it('links the best-scored applications, highest first, to their ranking page', async () => {
    show();
    const rows = (await screen.findAllByRole('row')).slice(1);
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText('Front')).toBeInTheDocument();
    expect(within(rows[0]).getByText('Sami Ben Ali')).toBeInTheDocument();
    expect(within(rows[0]).getByText('92,0')).toBeInTheDocument();
    expect(within(rows[0]).getByRole('link', { name: 'Examiner' })).toHaveAttribute('href', '/recruiter/jobs/j2/candidates?id=a3');
    expect(within(rows[1]).getByText('70,0')).toBeInTheDocument();
    // An offer without applications is not scanned.
    expect(applications.forJob).not.toHaveBeenCalledWith('j3');
  });

  it('shows an error when the stats are forbidden, and the company prompt without a company', async () => {
    dashboard.companyStats.mockRejectedValue(new HttpError(403));
    const { unmount } = show();
    expect(await screen.findByText('Vous n’avez pas accès à cette ressource.')).toBeInTheDocument();
    unmount();
    noCompany();
    show();
    expect(await screen.findByText('Présentez d’abord votre entreprise')).toBeInTheDocument();
  });
});
