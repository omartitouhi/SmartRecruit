import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { applicationsApi } from '../../api/applications';
import { candidatesApi } from '../../api/candidates';
import { jobsApi } from '../../api/jobs';
import { downloadCsv } from '../../lib/csv';
import { HttpError } from '../../lib/errors';
import { makeAccount, makeApplication, makeJob, makeMatch, makeNote, makeProfile } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { CandidateRankingPage } from './CandidateRankingPage';

vi.mock('../../lib/csv', async (original) => ({
  ...(await original<typeof import('../../lib/csv')>()),
  downloadCsv: vi.fn(),
}));
vi.mock('../../api/jobs', async (original) => ({
  ...(await original<typeof import('../../api/jobs')>()),
  jobsApi: { get: vi.fn(), search: vi.fn() },
}));
vi.mock('../../api/applications', async (original) => ({
  ...(await original<typeof import('../../api/applications')>()),
  applicationsApi: {
    forJob: vi.fn(),
    updateStatus: vi.fn(),
    scheduleInterview: vi.fn(),
    notes: vi.fn(),
    addNote: vi.fn(),
    deleteNote: vi.fn(),
  },
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
  applications.notes.mockResolvedValue([]);
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

describe('interviews and explained decisions (EXT-APP-01/02)', () => {
  async function select(user: ReturnType<typeof userEvent.setup>, name: string) {
    await user.click(within(await screen.findByRole('table')).getByText(name).closest('tr') as HTMLElement);
    return screen.getByRole('complementary', { name: 'Détail du candidat' });
  }

  it('plans the interview with the status change and sends the message to the candidate', async () => {
    const user = userEvent.setup();
    applications.updateStatus.mockResolvedValue({ ...unscored, status: 'INTERVIEW' });
    show();
    const detail = await select(user, 'Nora None');
    await user.click(within(detail).getByRole('button', { name: 'Proposer un entretien' }));
    fireEvent.change(within(detail).getByLabelText('Date et heure'), { target: { value: '2099-03-15T10:30' } });
    await user.selectOptions(within(detail).getByLabelText('Format'), 'ON_SITE');
    await user.type(within(detail).getByLabelText('Adresse'), 'Les Berges du Lac');
    await user.type(within(detail).getByLabelText('Message au candidat (facultatif)'), 'Rencontrons-nous.');
    await user.click(within(detail).getByRole('button', { name: 'Confirmer' }));

    expect(applications.updateStatus).toHaveBeenCalledWith('none', 'INTERVIEW', {
      message: 'Rencontrons-nous.',
      interview: { scheduledAt: new Date('2099-03-15T10:30').toISOString(), durationMinutes: 60, mode: 'ON_SITE', location: 'Les Berges du Lac' },
    });
  });

  it('refuses an interview in the past without calling the API, and can skip planning', async () => {
    const user = userEvent.setup();
    applications.updateStatus.mockResolvedValue({ ...unscored, status: 'INTERVIEW' });
    show();
    const detail = await select(user, 'Nora None');
    await user.click(within(detail).getByRole('button', { name: 'Proposer un entretien' }));
    fireEvent.change(within(detail).getByLabelText('Date et heure'), { target: { value: '2020-01-01T10:00' } });
    await user.click(within(detail).getByRole('button', { name: 'Confirmer' }));
    expect(within(detail).getByText('L’entretien doit avoir lieu dans le futur.')).toBeInTheDocument();
    expect(applications.updateStatus).not.toHaveBeenCalled();

    await user.click(within(detail).getByLabelText('Planifier l’entretien maintenant'));
    await user.click(within(detail).getByRole('button', { name: 'Confirmer' }));
    expect(applications.updateStatus).toHaveBeenCalledWith('none', 'INTERVIEW');
  });

  it('explains a rejection to the candidate', async () => {
    const user = userEvent.setup();
    applications.updateStatus.mockResolvedValue({ ...interview, status: 'REJECTED' });
    show();
    const detail = await select(user, 'Ines Iv');
    await user.click(within(detail).getByRole('button', { name: 'Ne pas retenir' }));
    expect(within(detail).getByText(/Une décision expliquée est mieux reçue/)).toBeInTheDocument();
    await user.type(within(detail).getByLabelText('Message au candidat (facultatif)'), 'Profil plus senior retenu.');
    await user.click(within(detail).getByRole('button', { name: 'Confirmer' }));
    expect(applications.updateStatus).toHaveBeenCalledWith('iv', 'REJECTED', { message: 'Profil plus senior retenu.' });
  });

  it('shows and reschedules the planned interview', async () => {
    const user = userEvent.setup();
    const planned = {
      ...interview,
      interview: { scheduledAt: '2099-01-10T09:00:00Z', durationMinutes: 45, mode: 'VIDEO' as const, location: 'https://meet.demo.tn/x' },
    };
    applications.forJob.mockResolvedValue([high, planned]);
    applications.scheduleInterview.mockResolvedValue({ ...planned, interview: { ...planned.interview, durationMinutes: 30 } });
    show();
    const detail = await select(user, 'Ines Iv');
    expect(within(detail).getByRole('link', { name: 'https://meet.demo.tn/x' })).toHaveAttribute('href', 'https://meet.demo.tn/x');
    expect(within(detail).getByText('45 min')).toBeInTheDocument();
    await user.click(within(detail).getByRole('button', { name: 'Replanifier' }));
    const duration = within(detail).getByLabelText('Durée (minutes)');
    await user.clear(duration);
    await user.type(duration, '30');
    await user.click(within(detail).getByRole('button', { name: 'Enregistrer l’entretien' }));

    expect(applications.scheduleInterview).toHaveBeenCalledWith('iv', {
      scheduledAt: '2099-01-10T09:00:00.000Z',
      durationMinutes: 30,
      mode: 'VIDEO',
      location: 'https://meet.demo.tn/x',
    });
    expect(await within(detail).findByText('30 min')).toBeInTheDocument();
  });

  it('shows the cover letter and the messages of the history', async () => {
    const user = userEvent.setup();
    applications.forJob.mockResolvedValue([
      {
        ...high,
        coverLetter: 'Passionné de logistique.',
        statusHistory: [{ status: 'RECEIVED', changedAt: '2026-10-05T09:00:00Z', changedBy: 'c-high', message: null }],
      },
      {
        ...accepted,
        statusHistory: [
          { status: 'RECEIVED', changedAt: '2026-10-05T09:00:00Z', changedBy: 'c-ok' },
          { status: 'ACCEPTED', changedAt: '2026-10-09T09:00:00Z', changedBy: 'recruiter-id', message: 'Bienvenue !' },
        ],
      },
    ]);
    show();
    let detail = await screen.findByRole('complementary', { name: 'Détail du candidat' });
    await user.click(within(detail).getByText('Lettre de motivation'));
    expect(within(detail).getByText('Passionné de logistique.')).toBeVisible();
    detail = await select(user, 'Omar Ok');
    expect(within(detail).getByText('Bienvenue !')).toBeInTheDocument();
  });
});

describe('team notes (EXT-APP-03)', () => {
  it('lists the notes, adds one with a rating and updates the team column', async () => {
    const user = userEvent.setup();
    applications.notes.mockResolvedValue([
      makeNote({ id: 'n1', applicationId: 'high', authorId: 'recruiter-id', content: 'Très bon échange.', rating: 5 }),
      makeNote({ id: 'n2', applicationId: 'high', authorId: 'colleague-id', content: 'À challenger sur Kafka.', rating: null }),
    ]);
    applications.addNote.mockResolvedValue(
      makeNote({ id: 'n3', applicationId: 'high', authorId: 'recruiter-id', content: 'Disponible en janvier.', rating: 4 }),
    );
    show();
    const detail = await screen.findByRole('complementary', { name: 'Détail du candidat' });
    const notes = within(await within(detail).findByRole('region', { name: /Notes de l’équipe/ }));
    expect(await notes.findByText('Très bon échange.')).toBeInTheDocument();
    expect(notes.getByText('Vous')).toBeInTheDocument();
    expect(notes.getByText('Un·e collègue')).toBeInTheDocument();
    // Only one's own notes can be deleted.
    expect(notes.getAllByRole('button', { name: 'Supprimer ma note' })).toHaveLength(1);

    await user.type(notes.getByLabelText('Ajouter une note'), 'Disponible en janvier.');
    await user.selectOptions(notes.getByLabelText('Évaluation'), '4');
    await user.click(notes.getByRole('button', { name: 'Ajouter la note' }));

    expect(applications.addNote).toHaveBeenCalledWith('high', { content: 'Disponible en janvier.', rating: 4 });
    expect(await notes.findByText('Disponible en janvier.')).toBeInTheDocument();
    const row = within(screen.getByRole('table')).getByText('Hedi High').closest('tr') as HTMLElement;
    expect(within(row).getByText('3 notes')).toBeInTheDocument();
    expect(within(row).getByRole('img', { name: 'Note : 4,5 sur 5' })).toBeInTheDocument();
  });

  it('deletes one’s own note and refuses an empty one', async () => {
    const user = userEvent.setup();
    applications.notes.mockResolvedValue([makeNote({ id: 'n1', applicationId: 'high', authorId: 'recruiter-id', content: 'À revoir.' })]);
    applications.deleteNote.mockResolvedValue();
    show();
    const detail = await screen.findByRole('complementary', { name: 'Détail du candidat' });
    const notes = within(await within(detail).findByRole('region', { name: /Notes de l’équipe/ }));
    await user.click(await notes.findByRole('button', { name: 'Ajouter la note' }));
    expect(notes.getByText('Écrivez la note avant de l’ajouter.')).toBeInTheDocument();
    expect(applications.addNote).not.toHaveBeenCalled();

    await user.click(notes.getByRole('button', { name: 'Supprimer ma note' }));
    expect(applications.deleteNote).toHaveBeenCalledWith('high', 'n1');
    await waitFor(() => expect(notes.queryByText('À revoir.')).not.toBeInTheDocument());
    expect(notes.getByText('Aucune note pour l’instant.')).toBeInTheDocument();
  });
});

describe('pipeline view and CSV export (EXT-FE)', () => {
  it('exports the ranking as a CSV named after the offer', async () => {
    const user = userEvent.setup();
    show();
    await screen.findByRole('table');
    await user.click(screen.getByRole('button', { name: 'Exporter en CSV' }));
    const [filename, content] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toMatch(/^candidatures-developpeur-backend-\d{4}-\d{2}-\d{2}\.csv$/);
    const lines = content.trim().split('\r\n');
    expect(lines).toHaveLength(6);
    expect(lines[1]).toMatch(/^1;Hedi High;hedi@demo\.tn;Reçue;91,0;/);
  });

  it('switches to the Kanban pipeline and confirms a move in the detail panel', async () => {
    const user = userEvent.setup();
    applications.updateStatus.mockResolvedValue({ ...high, status: 'IN_REVIEW' });
    show();
    await screen.findByRole('table');
    await user.click(screen.getByRole('button', { name: 'Pipeline' }));
    const board = await screen.findByRole('list', { name: 'Pipeline des candidatures' });
    const received = within(board).getByRole('heading', { name: /^Reçue/ }).closest('section') as HTMLElement;
    const hedi = within(received).getByText('Hedi High').closest('li') as HTMLElement;
    await user.click(within(hedi).getByRole('button', { name: 'Passer en revue' }));

    const detail = screen.getByRole('complementary', { name: 'Détail du candidat' });
    expect(within(detail).getByRole('heading', { name: 'Hedi High' })).toBeInTheDocument();
    expect(within(detail).getByText(/Passer la candidature à « En revue » \?/)).toBeInTheDocument();
    await user.click(within(detail).getByRole('button', { name: 'Confirmer' }));
    expect(applications.updateStatus).toHaveBeenCalledWith('high', 'IN_REVIEW');
    const inReview = within(board).getByRole('heading', { name: /^En revue/ }).closest('section') as HTMLElement;
    expect(await within(inReview).findByText('Hedi High')).toBeInTheDocument();
  });
});
