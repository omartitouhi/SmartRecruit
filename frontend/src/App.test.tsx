import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { tokenStore } from './api/client';
import { db } from './api/mock/store';
import { AuthProvider } from './auth/AuthProvider';
import { ToastProvider } from './components/ui/ToastProvider';
import { EMAIL, jobByTitle, loginAs, resetDb } from './test/mockDb';

// Runs before the imports above are evaluated: the whole app talks to the in-browser mock backend,
// exactly like `VITE_USE_MOCKS=true npm run dev`.
vi.hoisted(() => {
  import.meta.env.VITE_USE_MOCKS = 'true';
});

const renderApp = (route: string) =>
  render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <ToastProvider>
          <App />
        </ToastProvider>
      </AuthProvider>
    </MemoryRouter>,
  );

beforeEach(() => {
  resetDb();
  window.scrollTo = vi.fn();
});

describe('demo mode end to end', () => {
  it.each([
    ['Candidat·e', 'Candidatures actives'],
    ['Recruteur·se', 'Carthage Fret'],
    ['Admin', 'Utilisateurs par rôle'],
  ])('logs in with one click on the %s demo account and lands on its dashboard', async (button, expected) => {
    const user = userEvent.setup();
    renderApp('/login');
    await user.click(await screen.findByRole('button', { name: button }));
    expect(await screen.findByText(expected, {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: /Tableau de bord|Carthage Fret/ })).toBeInTheDocument();
  });

  it('refuses a wrong password with the generic message', async () => {
    const user = userEvent.setup();
    renderApp('/login');
    await user.type(await screen.findByLabelText('Email'), EMAIL.candidate);
    await user.type(screen.getByLabelText('Mot de passe'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));
    expect(await screen.findByText('Email ou mot de passe incorrect.', {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('registers, goes to the login page with a confirmation, then logs in as the new account', async () => {
    const user = userEvent.setup();
    renderApp('/register');
    await user.type(await screen.findByLabelText('Email'), 'new.candidate@demo.tn');
    await user.type(screen.getByLabelText('Mot de passe'), 'motdepasse1');
    await user.click(screen.getByRole('button', { name: 'Créer mon compte' }));

    expect(await screen.findByText('Compte créé', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveValue('new.candidate@demo.tn');
    await user.type(screen.getByLabelText('Mot de passe'), 'motdepasse1');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));
    expect(await screen.findByText('Créez votre profil', {}, { timeout: 5000 })).toBeInTheDocument();
  });
});

describe('route guards with a restored session', () => {
  it('sends an anonymous visitor to /login', async () => {
    renderApp('/candidate/profile');
    expect(await screen.findByRole('heading', { level: 1, name: 'Connexion' })).toBeInTheDocument();
  });

  it('shows a candidate the 403 page on an admin route', async () => {
    loginAs(EMAIL.candidate);
    renderApp('/admin');
    expect(await screen.findByText('403', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByText('Utilisateurs par rôle')).not.toBeInTheDocument();
  });

  it('keeps a recruiter out of the candidate space', async () => {
    loginAs(EMAIL.recruiter);
    renderApp('/candidate/jobs');
    expect(await screen.findByText('403', {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('drops an invalid session and asks to log in again', async () => {
    tokenStore.set({ accessToken: 'mock.unknown', refreshToken: 'r' });
    renderApp('/recruiter');
    expect(await screen.findByRole('heading', { level: 1, name: 'Connexion' }, { timeout: 5000 })).toBeInTheDocument();
    expect(tokenStore.access).toBeNull();
  });

  it('bounces a signed-in user away from /login to their own space', async () => {
    loginAs(EMAIL.admin);
    renderApp('/login');
    expect(await screen.findByText('Utilisateurs par rôle', {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('shows the 404 page for an unknown route', async () => {
    renderApp('/nope');
    expect(await screen.findByText('Cette page n’existe pas.')).toBeInTheDocument();
  });
});

describe('journeys on the mock backend', () => {
  it('lets the demo candidate open an offer and apply, then find it in "Mes candidatures"', async () => {
    const user = userEvent.setup();
    const job = jobByTitle('Développeur·se backend');
    loginAs(EMAIL.candidate);
    renderApp(`/candidate/jobs/${job.id}`);

    await user.click(await screen.findByRole('button', { name: 'Postuler' }, { timeout: 5000 }));
    expect(await screen.findByRole('button', { name: 'Candidature envoyée' }, { timeout: 5000 })).toBeDisabled();
    const mine = db.applications.filter((a) => a.jobId === job.id && a.status === 'RECEIVED' && a.candidateId === db.accounts[0].id);
    expect(mine).toHaveLength(1);
  });

  it('lets the demo recruiter publish an offer that then shows up in "Mes offres"', async () => {
    const user = userEvent.setup();
    loginAs(EMAIL.recruiter);
    renderApp('/recruiter/jobs/new');

    await user.type(await screen.findByLabelText('Intitulé du poste', {}, { timeout: 5000 }), 'Architecte cloud');
    await user.type(screen.getByLabelText('Lieu'), 'Tunis');
    await user.type(screen.getByLabelText('Compétences demandées'), 'AWS{Enter}');
    await user.type(screen.getByLabelText('Description du poste'), 'Vous concevez notre plateforme cloud.');
    await user.click(screen.getByRole('button', { name: 'Publier l’offre' }));

    expect(await screen.findByText('Architecte cloud', {}, { timeout: 5000 })).toBeInTheDocument();
    await waitFor(() => expect(db.jobs.some((j) => j.title === 'Architecte cloud' && j.status === 'PUBLISHED')).toBe(true));
  });
});
