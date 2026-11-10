import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { dashboardApi } from '../../api/dashboard';
import { jobsApi } from '../../api/jobs';
import { HttpError } from '../../lib/errors';
import { makeAccount, makeJob, page } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import type { AuditLogEntry } from '../../types';
import { AdminAuditPage } from './AdminAuditPage';
import { AdminDashboardPage } from './AdminDashboardPage';
import { AdminJobsPage } from './AdminJobsPage';
import { AdminUsersPage } from './AdminUsersPage';

vi.mock('../../api/dashboard', async (original) => ({
  ...(await original<typeof import('../../api/dashboard')>()),
  dashboardApi: {
    globalStats: vi.fn(),
    companyStats: vi.fn(),
    users: vi.fn(),
    setUserStatus: vi.fn(),
    moderateJob: vi.fn(),
    auditLog: vi.fn(),
  },
}));
vi.mock('../../api/jobs', async (original) => ({
  ...(await original<typeof import('../../api/jobs')>()),
  jobsApi: { search: vi.fn() },
}));

const dashboard = vi.mocked(dashboardApi);
const jobs = vi.mocked(jobsApi);
const admin = makeAccount('ADMIN', { id: 'admin-id', email: 'admin@demo.tn' });
const lastCall = <T,>(fn: { mock: { calls: T[][] } }) => fn.mock.calls.at(-1)?.[0];

beforeEach(() => vi.resetAllMocks());

describe('admin dashboard', () => {
  it('shows the platform figures and the users by role', async () => {
    dashboard.globalStats.mockResolvedValue({
      activeJobs: 7,
      totalApplications: 42,
      totalUsers: 15,
      usersByRole: { CANDIDATE: 10, RECRUITER: 4, ADMIN: 1 },
      averageMatchScore: 66.4,
      applicationsByStatus: null,
      averageDecisionDays: null,
      jobsClosingSoon: null,
    });
    renderAt(<AdminDashboardPage />, { route: '/admin', account: admin });
    const tile = async (label: string) => (await screen.findByText(label, { selector: '.tile__label' })).closest('.tile') as HTMLElement;
    expect(within(await tile('Offres publiées')).getByText('7')).toBeInTheDocument();
    expect(within(await tile('Candidatures')).getByText('42')).toBeInTheDocument();
    expect(within(await tile('Utilisateurs')).getByText('15')).toBeInTheDocument();
    expect(within(await tile('Score moyen des candidatures')).getByText('66,4')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Journal d’audit' })).toHaveAttribute('href', '/admin/audit-log');
    expect(screen.getByRole('link', { name: 'Utilisateurs' })).toHaveAttribute('href', '/admin/users');
  });

  it('reloads the figures for the chosen period', async () => {
    const user = userEvent.setup();
    dashboard.globalStats.mockResolvedValue({
      activeJobs: 1, totalApplications: 3, totalUsers: 2, usersByRole: { CANDIDATE: 1, RECRUITER: 0, ADMIN: 1 },
      averageMatchScore: null, applicationsByStatus: null, averageDecisionDays: null, jobsClosingSoon: null,
    });
    renderAt(<AdminDashboardPage />, { route: '/admin', account: admin });
    await screen.findByText('Offres publiées', { selector: '.tile__label' });
    expect(dashboard.globalStats).toHaveBeenCalledWith({});
    await user.selectOptions(screen.getByLabelText('Période'), '7');
    const from = new Date(Date.now() - 6 * 86_400_000).toISOString().slice(0, 10);
    await waitFor(() => expect(dashboard.globalStats).toHaveBeenLastCalledWith({ from }));
  });

  it('shows a dash for an unavailable average and an error with retry when the stats fail', async () => {
    dashboard.globalStats.mockResolvedValueOnce({
      activeJobs: 0,
      totalApplications: 0,
      totalUsers: 1,
      usersByRole: { CANDIDATE: 0, RECRUITER: 0, ADMIN: 1 },
      averageMatchScore: null,
      applicationsByStatus: null,
      averageDecisionDays: null,
      jobsClosingSoon: null,
    });
    const { unmount } = renderAt(<AdminDashboardPage />, { route: '/admin', account: admin });
    expect(within(((await screen.findByText('Score moyen des candidatures')).closest('.tile')) as HTMLElement).getByText('—')).toBeInTheDocument();
    unmount();
    dashboard.globalStats.mockRejectedValue(new HttpError(503));
    renderAt(<AdminDashboardPage />, { route: '/admin', account: admin });
    expect(await screen.findByText('Service indisponible')).toBeInTheDocument();
  });
});

describe('admin users', () => {
  const users = [
    makeAccount('CANDIDATE', { id: 'u1', email: 'amira@demo.tn' }),
    makeAccount('RECRUITER', { id: 'u2', email: 'rh@demo.tn', status: 'DISABLED' }),
    admin,
    makeAccount('CANDIDATE', { id: 'u4', email: 'gone@demo.tn', status: 'DELETED' }),
  ];
  const show = () => renderAt(<AdminUsersPage />, { route: '/admin/users', account: admin });
  const rowOf = async (email: string) => (await screen.findByText(email)).closest('tr') as HTMLElement;

  beforeEach(() => {
    dashboard.users.mockResolvedValue(page(users));
  });

  it('lists accounts with role and status', async () => {
    show();
    const row = await rowOf('rh@demo.tn');
    expect(within(row).getByText('Recruteur·se')).toBeInTheDocument();
    expect(within(row).getByText('Désactivé')).toBeInTheDocument();
    expect(within(await rowOf('gone@demo.tn')).getByText('Supprimé')).toBeInTheDocument();
  });

  it('filters by role and by status through the API', async () => {
    const user = userEvent.setup();
    show();
    await screen.findByText('rh@demo.tn');
    await user.selectOptions(screen.getByLabelText('Rôle'), 'RECRUITER');
    await waitFor(() => expect(lastCall(dashboard.users)).toMatchObject({ role: 'RECRUITER', status: '', page: 0 }));
    await user.selectOptions(screen.getByLabelText('Statut'), 'DISABLED');
    await waitFor(() => expect(lastCall(dashboard.users)).toMatchObject({ role: 'RECRUITER', status: 'DISABLED' }));
  });

  it('disables an active account in one click and re-enables a disabled one', async () => {
    const user = userEvent.setup();
    dashboard.setUserStatus.mockImplementation(async (id, status) => ({ ...users.find((u) => u.id === id)!, status }));
    show();
    await user.click(within(await rowOf('amira@demo.tn')).getByRole('button', { name: /Désactiver le compte de amira@demo.tn/ }));
    expect(dashboard.setUserStatus).toHaveBeenCalledWith('u1', 'DISABLED');
    await waitFor(() => expect(within(screen.getByText('amira@demo.tn').closest('tr') as HTMLElement).getByText('Désactivé')).toBeInTheDocument());

    await user.click(within(await rowOf('rh@demo.tn')).getByRole('button', { name: /Activer le compte de rh@demo.tn/ }));
    expect(dashboard.setUserStatus).toHaveBeenLastCalledWith('u2', 'ACTIVE');
    expect(await screen.findByText('Compte de rh@demo.tn réactivé.', { selector: '.toast' })).toBeInTheDocument();
  });

  it('does not let an admin disable themselves and offers nothing for a deleted account', async () => {
    show();
    const own = await rowOf('admin@demo.tn');
    expect(within(own).getByText('(vous)')).toBeInTheDocument();
    expect(within(own).getByRole('button', { name: /Désactiver/ })).toBeDisabled();
    expect(within(await rowOf('gone@demo.tn')).queryByRole('button')).not.toBeInTheDocument();
  });

  it('shows the API message when the change is refused', async () => {
    const user = userEvent.setup();
    dashboard.setUserStatus.mockRejectedValue(new HttpError(400, 'Vous ne pouvez pas désactiver votre propre compte.'));
    show();
    await user.click(within(await rowOf('amira@demo.tn')).getByRole('button', { name: /Désactiver/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('propre compte');
  });

  it('pages through the accounts and says when a filter matches nobody', async () => {
    const user = userEvent.setup();
    dashboard.users.mockResolvedValue(page(users, { totalPages: 2, totalElements: 30 }));
    show();
    await screen.findByText('rh@demo.tn');
    await user.click(screen.getByRole('button', { name: 'Suivant' }));
    await waitFor(() => expect(lastCall(dashboard.users)).toMatchObject({ page: 1 }));
    dashboard.users.mockResolvedValue(page([]));
    await user.selectOptions(screen.getByLabelText('Rôle'), 'ADMIN');
    expect(await screen.findByText('Aucun compte ne correspond')).toBeInTheDocument();
  });
});

describe('admin job moderation', () => {
  const show = () => renderAt(<AdminJobsPage />, { route: '/admin/jobs', account: admin });
  const rowOf = async (title: string) => (await screen.findByText(title)).closest('tr') as HTMLElement;

  beforeEach(() => {
    jobs.search.mockResolvedValue(
      page([
        makeJob({ id: 'j1', title: 'Backend' }),
        makeJob({ id: 'j2', title: 'Offre douteuse', status: 'MODERATED', moderationReason: 'Doublon' }),
      ]),
    );
  });

  it('browses published offers by default, with status and keyword filters', async () => {
    const user = userEvent.setup();
    show();
    await rowOf('Backend');
    expect(lastCall(jobs.search)).toMatchObject({ status: 'PUBLISHED', keyword: '', page: 0 });
    await user.selectOptions(screen.getByLabelText('Statut'), 'MODERATED');
    await waitFor(() => expect(lastCall(jobs.search)).toMatchObject({ status: 'MODERATED' }));
    await user.type(screen.getByLabelText('Recherche'), 'go');
    await user.click(screen.getByRole('button', { name: 'Rechercher' }));
    await waitFor(() => expect(lastCall(jobs.search)).toMatchObject({ keyword: 'go' }));
  });

  it('shows the reason of a moderated offer and no withdraw button for it', async () => {
    show();
    const row = await rowOf('Offre douteuse');
    expect(within(row).getByText('Motif : Doublon')).toBeInTheDocument();
    expect(within(row).queryByRole('button')).not.toBeInTheDocument();
  });

  it('requires a reason before withdrawing an offer', async () => {
    const user = userEvent.setup();
    show();
    await user.click(within(await rowOf('Backend')).getByRole('button', { name: /Retirer l’offre Backend/ }));
    await user.click(screen.getByRole('button', { name: 'Retirer l’offre' }));

    expect(screen.getByText(/Indiquez le motif du retrait/)).toBeInTheDocument();
    expect(dashboard.moderateJob).not.toHaveBeenCalled();
  });

  it('withdraws the offer with the reason and refreshes the list', async () => {
    const user = userEvent.setup();
    dashboard.moderateJob.mockResolvedValue(makeJob({ id: 'j1', status: 'MODERATED' }));
    show();
    await user.click(within(await rowOf('Backend')).getByRole('button', { name: /Retirer l’offre Backend/ }));
    await user.type(screen.getByLabelText('Motif du retrait'), '  Annonce discriminatoire ');
    await user.click(screen.getByRole('button', { name: 'Retirer l’offre' }));

    expect(dashboard.moderateJob).toHaveBeenCalledWith('j1', 'Annonce discriminatoire');
    expect(await screen.findByText('Offre retirée.', { selector: '.toast' })).toBeInTheDocument();
    await waitFor(() => expect(jobs.search).toHaveBeenCalledTimes(2));
    expect(screen.queryByLabelText('Motif du retrait')).not.toBeInTheDocument();
  });

  it('shows a server field error on the reason and lets the admin cancel', async () => {
    const user = userEvent.setup();
    dashboard.moderateJob.mockRejectedValue(new HttpError(400, 'Requête invalide', { reason: 'Motif trop long' }));
    show();
    await user.click(within(await rowOf('Backend')).getByRole('button', { name: /Retirer l’offre Backend/ }));
    await user.type(screen.getByLabelText('Motif du retrait'), 'x');
    await user.click(screen.getByRole('button', { name: 'Retirer l’offre' }));
    expect(await screen.findByText('Motif trop long')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Annuler' }));
    expect(screen.queryByLabelText('Motif du retrait')).not.toBeInTheDocument();
  });
});

describe('admin audit log', () => {
  const entry = (overrides: Partial<AuditLogEntry> = {}): AuditLogEntry => ({
    id: 'e1',
    actorId: 'aaaaaaaa-1111-2222-3333-444444444444',
    action: 'CV_VIEWED',
    resourceType: 'CandidateProfile',
    resourceId: 'bbbbbbbb-1111-2222-3333-444444444444',
    timestamp: '2026-10-14T09:00:00Z',
    ...overrides,
  });
  const show = () => renderAt(<AdminAuditPage />, { route: '/admin/audit-log', account: admin });

  beforeEach(() => {
    dashboard.auditLog.mockResolvedValue(
      page([entry(), entry({ id: 'e2', action: 'JOB_MODERATED', resourceType: 'Job' }), entry({ id: 'e3', action: 'SOMETHING_NEW' })]),
    );
  });

  it('lists events with readable action labels and falls back to the raw action', async () => {
    show();
    expect(await screen.findByText('CV consulté', { selector: 'td' })).toBeInTheDocument();
    expect(screen.getByText('Offre modérée', { selector: 'td' })).toBeInTheDocument();
    expect(screen.getByText('SOMETHING_NEW')).toBeInTheDocument();
    expect(lastCall(dashboard.auditLog)).toMatchObject({ page: 0, size: 20, from: undefined, to: undefined });
  });

  it('filters by date range, user and event type', async () => {
    const user = userEvent.setup();
    show();
    await screen.findByText('CV consulté', { selector: 'td' });
    await user.type(screen.getByLabelText('Du'), '2026-10-12');
    await user.type(screen.getByLabelText('Au'), '2026-10-14');
    await user.type(screen.getByLabelText('Identifiant de l’utilisateur'), ' u-42 ');
    await user.selectOptions(screen.getByLabelText('Type d’événement'), 'JOB_MODERATED');
    await user.click(screen.getByRole('button', { name: 'Filtrer' }));

    await waitFor(() => expect(lastCall(dashboard.auditLog)).toMatchObject({ actorId: 'u-42', action: 'JOB_MODERATED', page: 0 }));
    const filters = lastCall(dashboard.auditLog) as { from: string; to: string };
    expect(new Date(filters.from).getTime()).toBe(new Date('2026-10-12T00:00:00').getTime());
    expect(new Date(filters.to).getTime()).toBe(new Date('2026-10-14T23:59:59.999').getTime());
  });

  it('filters on the author of a row with one click', async () => {
    const user = userEvent.setup();
    show();
    const [authorButton] = await screen.findAllByRole('button', { name: 'aaaaaaaa' });
    await user.click(authorButton);
    await waitFor(() => expect(lastCall(dashboard.auditLog)).toMatchObject({ actorId: 'aaaaaaaa-1111-2222-3333-444444444444' }));
    expect(screen.getByLabelText('Identifiant de l’utilisateur')).toHaveValue('aaaaaaaa-1111-2222-3333-444444444444');
  });

  it('resets the filters and says when nothing matches', async () => {
    const user = userEvent.setup();
    show();
    await screen.findByText('CV consulté', { selector: 'td' });
    dashboard.auditLog.mockResolvedValue(page([]));
    await user.selectOptions(screen.getByLabelText('Type d’événement'), 'USER_STATUS_CHANGED');
    await user.click(screen.getByRole('button', { name: 'Filtrer' }));
    expect(await screen.findByText('Aucun événement ne correspond')).toBeInTheDocument();

    dashboard.auditLog.mockResolvedValue(page([entry()]));
    await user.click(screen.getByRole('button', { name: 'Réinitialiser' }));
    expect(await screen.findByText('CV consulté', { selector: 'td' })).toBeInTheDocument();
    expect(lastCall(dashboard.auditLog)).toMatchObject({ action: '', actorId: '' });
  });

  it('pages through the log and reports errors', async () => {
    const user = userEvent.setup();
    dashboard.auditLog.mockResolvedValue(page([entry()], { totalPages: 3, totalElements: 50 }));
    show();
    expect(await screen.findByText('Page 1 sur 3')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Suivant' }));
    await waitFor(() => expect(lastCall(dashboard.auditLog)).toMatchObject({ page: 1 }));
    dashboard.auditLog.mockRejectedValue(new HttpError(503));
    await user.click(screen.getByRole('button', { name: 'Suivant' }));
    expect(await screen.findByText('Service indisponible')).toBeInTheDocument();
  });
});
