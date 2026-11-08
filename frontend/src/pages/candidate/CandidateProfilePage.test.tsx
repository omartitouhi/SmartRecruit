import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { candidatesApi } from '../../api/candidates';
import { HttpError } from '../../lib/errors';
import { makeAccount, makeProfile } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { CandidateProfilePage } from './CandidateProfilePage';

vi.mock('../../api/candidates', async (original) => ({
  ...(await original<typeof import('../../api/candidates')>()),
  candidatesApi: {
    getMyProfile: vi.fn(),
    saveMyProfile: vi.fn(),
    uploadCv: vi.fn(),
    cvSuggestions: vi.fn(),
    downloadCv: vi.fn(),
    deleteMyAccount: vi.fn(),
    getCandidate: vi.fn(),
  },
}));

const api = vi.mocked(candidatesApi);
const account = makeAccount('CANDIDATE', { id: 'candidate-id' });
const show = () => renderAt(<CandidateProfilePage />, { route: '/candidate/profile', account });

beforeEach(() => {
  vi.resetAllMocks();
  api.cvSuggestions.mockRejectedValue(new HttpError(404));
  // jsdom has no object URLs: the upload preview needs them.
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});

describe('profile creation and edition', () => {
  it('invites a new candidate to create the profile and keeps the CV upload disabled until then', async () => {
    api.getMyProfile.mockRejectedValue(new HttpError(404));
    show();
    expect(await screen.findByText('Profil à créer')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Créer mon profil' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Déposer mon CV/)).toBeDisabled();
  });

  it('pre-fills the form with the saved profile and confirms a save with a toast', async () => {
    const user = userEvent.setup();
    api.getMyProfile.mockResolvedValue(makeProfile({ phone: '+216 22 418 730' }));
    api.saveMyProfile.mockImplementation(async (request) => makeProfile({ ...request }));
    show();

    expect(await screen.findByLabelText(/Prénom/)).toHaveValue('Amira');
    expect(screen.getByLabelText(/Nom$/)).toHaveValue('Trabelsi');
    expect(screen.getByLabelText(/Téléphone/)).toHaveValue('+216 22 418 730');

    await user.clear(screen.getByLabelText(/Ville/));
    await user.type(screen.getByLabelText(/Ville/), 'Sfax');
    await user.click(screen.getByRole('button', { name: 'Enregistrer le profil' }));

    await waitFor(() => expect(api.saveMyProfile).toHaveBeenCalledTimes(1));
    expect(api.saveMyProfile).toHaveBeenCalledWith(expect.objectContaining({ firstName: 'Amira', location: 'Sfax' }));
    expect(await screen.findByText('Profil enregistré.')).toBeInTheDocument();
  });

  it('shows the server field errors under the right field', async () => {
    const user = userEvent.setup();
    api.getMyProfile.mockResolvedValue(makeProfile());
    api.saveMyProfile.mockRejectedValue(new HttpError(400, 'Requête invalide', { email: 'Email déjà utilisé' }));
    show();
    await user.click(await screen.findByRole('button', { name: 'Enregistrer le profil' }));
    expect(await screen.findByText('Email déjà utilisé')).toBeInTheDocument();
  });
});

describe('multi-step form and local draft', () => {
  it('moves through the three steps and blocks Suivant on an invalid step', async () => {
    const user = userEvent.setup();
    api.getMyProfile.mockResolvedValue(makeProfile({ firstName: '' }));
    show();
    await screen.findByLabelText(/Prénom/);

    await user.click(screen.getByRole('button', { name: 'Suivant' }));
    expect(screen.getByText('Le prénom est obligatoire.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Informations/ })).toHaveAttribute('aria-current', 'step');

    await user.type(screen.getByLabelText(/Prénom/), 'Amira');
    await user.click(screen.getByRole('button', { name: 'Suivant' }));
    expect(screen.getByRole('heading', { name: 'Expériences' })).toBeInTheDocument();
    expect(screen.queryByLabelText(/Prénom/)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Suivant' }));
    expect(screen.getByRole('heading', { name: 'Compétences et langues' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Suivant' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Précédent' }));
    expect(screen.getByRole('heading', { name: 'Expériences' })).toBeInTheDocument();
  });

  it('keeps the draft across a reload and offers to discard it', async () => {
    const user = userEvent.setup();
    api.getMyProfile.mockResolvedValue(makeProfile());
    const first = show();
    await user.clear(await screen.findByLabelText(/Ville/));
    await user.type(screen.getByLabelText(/Ville/), 'Bizerte');
    await user.click(screen.getByRole('button', { name: /Compétences/ }));
    first.unmount();

    show();
    expect(await screen.findByText('Brouillon restauré')).toBeInTheDocument();
    // The step is restored too.
    expect(screen.getByRole('heading', { name: 'Compétences et langues' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Informations/ }));
    expect(screen.getByLabelText(/Ville/)).toHaveValue('Bizerte');

    await user.click(screen.getByRole('button', { name: 'Repartir du profil enregistré' }));
    expect(screen.queryByText('Brouillon restauré')).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Ville/)).toHaveValue('Tunis');
  });

  it('clears the draft once the profile is saved', async () => {
    const user = userEvent.setup();
    api.getMyProfile.mockResolvedValue(makeProfile());
    api.saveMyProfile.mockImplementation(async (request) => makeProfile({ ...request }));
    show();
    await user.type(await screen.findByLabelText(/Ville/), 'x');
    expect(localStorage.getItem('smartrecruit_profile_draft:candidate-id')).not.toBeNull();
    await user.click(screen.getByRole('button', { name: 'Enregistrer le profil' }));
    await screen.findByText('Profil enregistré.');
    expect(localStorage.getItem('smartrecruit_profile_draft:candidate-id')).toBeNull();
  });

  it('jumps to the faulty step when saving fails validation', async () => {
    const user = userEvent.setup();
    api.getMyProfile.mockResolvedValue(
      makeProfile({ experiences: [{ title: '', company: 'X', startDate: '2022-01', endDate: null, description: '' }] }),
    );
    show();
    await user.click(await screen.findByRole('button', { name: /Compétences/ }));
    await user.click(screen.getByRole('button', { name: 'Enregistrer le profil' }));
    expect(await screen.findByText('L’intitulé est obligatoire.')).toBeInTheDocument();
    expect(api.saveMyProfile).not.toHaveBeenCalled();
  });
});

describe('CV analysis states', () => {
  it('says nothing about analysis when there is no CV', async () => {
    api.getMyProfile.mockResolvedValue(makeProfile());
    show();
    await screen.findByLabelText(/Prénom/);
    expect(screen.queryByText(/Analyse en cours/)).not.toBeInTheDocument();
    expect(screen.queryByText('CV analysé')).not.toBeInTheDocument();
  });

  it('shows PENDING as "Analyse en cours" while polling', async () => {
    api.getMyProfile.mockResolvedValue(makeProfile({ cvUrl: '/cv', cvAnalysisStatus: 'PENDING' }));
    show();
    expect(await screen.findByText('Analyse en cours')).toBeInTheDocument();
  });

  it('stops saying "en cours" and explains a FAILED analysis', async () => {
    api.getMyProfile.mockResolvedValue(makeProfile({ cvUrl: '/cv', cvAnalysisStatus: 'FAILED' }));
    show();
    expect(await screen.findByText('L’analyse du CV a échoué')).toBeInTheDocument();
    expect(screen.queryByText('Analyse en cours')).not.toBeInTheDocument();
    expect(api.cvSuggestions).not.toHaveBeenCalled();
  });

  it('confirms a DONE analysis even when cvParsedAt is the only trace', async () => {
    api.getMyProfile.mockResolvedValue(
      makeProfile({ cvUrl: '/cv', cvAnalysisStatus: 'DONE', cvParsedAt: '2026-10-05T09:00:00Z' }),
    );
    show();
    expect(await screen.findByText('CV analysé')).toBeInTheDocument();
  });

  it('switches from PENDING to DONE when the polled profile settles', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    api.getMyProfile.mockResolvedValueOnce(makeProfile({ cvUrl: '/cv', cvAnalysisStatus: 'PENDING' }));
    api.getMyProfile.mockResolvedValue(
      makeProfile({ cvUrl: '/cv', cvAnalysisStatus: 'DONE', cvParsedAt: '2026-10-05T09:00:00Z' }),
    );
    show();
    expect(await screen.findByText('Analyse en cours')).toBeInTheDocument();
    await vi.advanceTimersByTimeAsync(3100);
    expect(await screen.findByText('CV analysé')).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('stops polling after a while and offers a manual check', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    api.getMyProfile.mockResolvedValue(makeProfile({ cvUrl: '/cv', cvAnalysisStatus: 'PENDING' }));
    show();
    await screen.findByText('Analyse en cours');
    await vi.advanceTimersByTimeAsync(3000 * 40 + 100);
    expect(await screen.findByText('L’analyse prend plus de temps que prévu')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Vérifier maintenant' })).toBeInTheDocument();
    vi.useRealTimers();
  });
});

describe('CV upload', () => {
  const profile = makeProfile();

  it('rejects a non-PDF and a file over 5 Mo without calling the API', async () => {
    const user = userEvent.setup({ applyAccept: false });
    api.getMyProfile.mockResolvedValue(profile);
    show();
    const input = await screen.findByLabelText(/Déposer mon CV/);

    await user.upload(input, new File(['x'], 'cv.docx', { type: 'application/msword' }));
    expect(await screen.findByText('Le CV doit être un fichier PDF.')).toBeInTheDocument();

    await user.upload(input, new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.pdf', { type: 'application/pdf' }));
    expect(await screen.findByText('Le CV ne doit pas dépasser 5 Mo.')).toBeInTheDocument();
    expect(api.uploadCv).not.toHaveBeenCalled();
  });

  it('shows a preview and a progress bar while the PDF is sent, then the analysis starts', async () => {
    const user = userEvent.setup();
    api.getMyProfile.mockResolvedValue(profile);
    let finish: (p: ReturnType<typeof makeProfile>) => void = () => undefined;
    api.uploadCv.mockImplementation((_file, onProgress) => {
      onProgress?.(40);
      return new Promise((resolve) => {
        finish = resolve;
      });
    });
    show();
    await user.upload(await screen.findByLabelText(/Déposer mon CV/), new File(['%PDF'], 'mon-cv.pdf', { type: 'application/pdf' }));

    expect(await screen.findByText('mon-cv.pdf')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Envoi du CV' })).toHaveAttribute('value', '40');
    expect(screen.getByTitle('Aperçu de mon-cv.pdf')).toBeInTheDocument();

    finish(makeProfile({ cvUrl: '/cv', cvAnalysisStatus: 'PENDING' }));
    expect(await screen.findByText('Analyse en cours')).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('shows the API error and drops the preview when the upload fails', async () => {
    const user = userEvent.setup();
    api.getMyProfile.mockResolvedValue(profile);
    api.uploadCv.mockRejectedValue(new HttpError(413));
    show();
    await user.upload(await screen.findByLabelText(/Déposer mon CV/), new File(['%PDF'], 'cv.pdf', { type: 'application/pdf' }));
    expect(await screen.findByText('Le fichier est trop volumineux.')).toBeInTheDocument();
    expect(screen.queryByTitle('Aperçu de cv.pdf')).not.toBeInTheDocument();
  });
});

describe('validation of the data extracted from the CV', () => {
  const analysed = makeProfile({
    cvUrl: '/cv',
    cvAnalysisStatus: 'DONE',
    cvParsedAt: '2026-10-05T09:00:00Z',
    skills: ['Java'],
    languages: ['Français'],
  });
  const extracted = {
    skills: ['Java', 'Kubernetes', 'GraphQL'],
    languages: ['Français', 'Allemand'],
    experiences: [{ title: 'Stagiaire DevOps', company: 'Byrsa Lab', startDate: '2020-06', endDate: '2020-09', description: '' }],
    education: [{ degree: 'Master', institution: 'ESPRIT', year: 2021 }],
  };

  beforeEach(() => {
    api.getMyProfile.mockResolvedValue(analysed);
    api.cvSuggestions.mockResolvedValue(extracted);
  });

  it('proposes only what the profile does not contain yet', async () => {
    show();
    const panel = await screen.findByRole('region', { name: 'Données extraites de votre CV' });
    expect(within(panel).getByLabelText('Kubernetes')).toBeChecked();
    expect(within(panel).getByLabelText('GraphQL')).toBeChecked();
    expect(within(panel).getByLabelText('Allemand')).toBeChecked();
    expect(within(panel).queryByLabelText('Java')).not.toBeInTheDocument();
    expect(within(panel).queryByLabelText('Français')).not.toBeInTheDocument();
    expect(within(panel).getByText('Stagiaire DevOps')).toBeInTheDocument();
  });

  it('applies the ticked parts through the profile endpoint and ignores the unticked ones', async () => {
    const user = userEvent.setup();
    api.saveMyProfile.mockImplementation(async (request) => ({ ...analysed, ...request }));
    show();
    const panel = await screen.findByRole('region', { name: 'Données extraites de votre CV' });
    await user.click(within(panel).getByLabelText('GraphQL'));
    await user.click(within(panel).getByLabelText('Allemand'));
    await user.click(within(panel).getByRole('button', { name: /Appliquer 3 éléments et enregistrer/ }));

    await waitFor(() => expect(api.saveMyProfile).toHaveBeenCalledTimes(1));
    const sent = api.saveMyProfile.mock.calls[0][0];
    expect(sent.skills).toEqual(['Java', 'Kubernetes']);
    expect(sent.languages).toEqual(['Français']);
    expect(sent.experiences).toHaveLength(1);
    expect(sent.education).toHaveLength(1);
    expect(await screen.findByText('Données du CV ajoutées à votre profil.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Données extraites de votre CV' })).not.toBeInTheDocument());
  });

  it('lets the candidate ignore everything, and remembers it for this analysis', async () => {
    const user = userEvent.setup();
    const first = show();
    await user.click(await screen.findByRole('button', { name: 'Tout ignorer' }));
    expect(screen.queryByRole('region', { name: 'Données extraites de votre CV' })).not.toBeInTheDocument();
    expect(api.saveMyProfile).not.toHaveBeenCalled();
    first.unmount();

    show();
    await screen.findByLabelText(/Prénom/);
    expect(screen.queryByRole('region', { name: 'Données extraites de votre CV' })).not.toBeInTheDocument();
  });

  it('disables Appliquer when nothing is ticked', async () => {
    const user = userEvent.setup();
    api.cvSuggestions.mockResolvedValue({ ...extracted, experiences: [], education: [], languages: [], skills: ['Kubernetes'] });
    show();
    const panel = await screen.findByRole('region', { name: 'Données extraites de votre CV' });
    await user.click(within(panel).getByLabelText('Kubernetes'));
    expect(within(panel).getByRole('button', { name: /Appliquer la sélection/ })).toBeDisabled();
  });

  it('shows nothing when the profile already has everything', async () => {
    api.cvSuggestions.mockResolvedValue({ skills: ['Java'], languages: ['Français'], experiences: [], education: [] });
    show();
    await screen.findByLabelText(/Prénom/);
    await waitFor(() => expect(api.cvSuggestions).toHaveBeenCalled());
    expect(screen.queryByRole('region', { name: 'Données extraites de votre CV' })).not.toBeInTheDocument();
  });

  it('offers a retry when the AI service is unavailable (503)', async () => {
    const user = userEvent.setup();
    api.cvSuggestions.mockRejectedValueOnce(new HttpError(503));
    show();
    expect(await screen.findByText('Service indisponible')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByRole('region', { name: 'Données extraites de votre CV' })).toBeInTheDocument();
  });
});
