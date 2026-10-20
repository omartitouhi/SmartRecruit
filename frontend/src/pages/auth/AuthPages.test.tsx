import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import { HttpError } from '../../lib/errors';
import { makeAccount, renderAt } from '../../test/render';
import { LoginPage } from './LoginPage';
import { RegisterPage } from './RegisterPage';

const axiosStatus = (status: number) =>
  new AxiosError('x', 'ERR', undefined, null, { status, data: {}, statusText: '', headers: {}, config: {} as never });

describe('RegisterPage', () => {
  it('shows no error before a field is visited, then validates it live', async () => {
    const user = userEvent.setup();
    renderAt(<RegisterPage />, { route: '/register' });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    await user.type(screen.getByLabelText('Email'), 'pas-un-email');
    await user.tab();
    expect(screen.getByText(/Saisissez un email valide/)).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');

    // The error clears as soon as the value is fixed, without waiting for another blur or submit.
    await user.type(screen.getByLabelText('Email'), '@mail.tn');
    expect(screen.queryByText(/Saisissez un email valide/)).not.toBeInTheDocument();
  });

  it('flags a password that is too short or too long', async () => {
    const user = userEvent.setup();
    renderAt(<RegisterPage />, { route: '/register' });
    await user.type(screen.getByLabelText('Mot de passe'), 'court');
    await user.tab();
    expect(screen.getByText('Au moins 8 caractères.')).toBeInTheDocument();
    await user.clear(screen.getByLabelText('Mot de passe'));
    await user.type(screen.getByLabelText('Mot de passe'), 'x'.repeat(73));
    expect(screen.getByText('72 caractères au maximum.')).toBeInTheDocument();
  });

  it('does not call the API while the form is invalid, and shows every error on submit', async () => {
    const user = userEvent.setup();
    const { auth } = renderAt(<RegisterPage />, { route: '/register' });
    await user.click(screen.getByRole('button', { name: 'Créer mon compte' }));
    expect(auth.register).not.toHaveBeenCalled();
    expect(screen.getAllByRole('alert')).toHaveLength(2);
  });

  it('redirects to the login page with a confirmation once the account is created', async () => {
    const user = userEvent.setup();
    const { auth } = renderAt(<RegisterPage />, { route: '/register' });
    vi.mocked(auth.register).mockResolvedValue(makeAccount('RECRUITER'));
    await user.click(screen.getByRole('radio', { name: /Recruteur/ }));
    await user.type(screen.getByLabelText('Email'), 'rh@mail.tn');
    await user.type(screen.getByLabelText('Mot de passe'), 'motdepasse1');
    await user.click(screen.getByRole('button', { name: 'Créer mon compte' }));

    expect(auth.register).toHaveBeenCalledWith({ email: 'rh@mail.tn', password: 'motdepasse1', role: 'RECRUITER' });
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login?registered=1'));
  });

  it('explains a duplicate email and keeps the form usable', async () => {
    const user = userEvent.setup();
    const { auth } = renderAt(<RegisterPage />, { route: '/register' });
    vi.mocked(auth.register).mockRejectedValue(new HttpError(409));
    await user.type(screen.getByLabelText('Email'), 'dup@mail.tn');
    await user.type(screen.getByLabelText('Mot de passe'), 'motdepasse1');
    await user.click(screen.getByRole('button', { name: 'Créer mon compte' }));

    expect(await screen.findByText(/Un compte existe déjà/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Créer mon compte' })).toBeEnabled();
  });
});

describe('LoginPage', () => {
  async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('Email'), 'a@mail.tn');
    await user.type(screen.getByLabelText('Mot de passe'), 'motdepasse1');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));
  }

  it.each([
    ['CANDIDATE', '/candidate'],
    ['RECRUITER', '/recruiter'],
    ['ADMIN', '/admin'],
  ] as const)('sends a %s to %s after login', async (role, home) => {
    const user = userEvent.setup();
    const { auth } = renderAt(<LoginPage />, { route: '/login' });
    vi.mocked(auth.login).mockResolvedValue(makeAccount(role));
    await fillAndSubmit(user);
    expect(auth.login).toHaveBeenCalledWith('a@mail.tn', 'motdepasse1');
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(home));
  });

  it('goes back to the page the user came from, but only for internal paths', async () => {
    const user = userEvent.setup();
    const { auth } = renderAt(<LoginPage />, { route: '/login?next=%2Fcandidate%2Fjobs%2F42' });
    vi.mocked(auth.login).mockResolvedValue(makeAccount('CANDIDATE'));
    await fillAndSubmit(user);
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/candidate/jobs/42'));
  });

  it('ignores an external next target', async () => {
    const user = userEvent.setup();
    const { auth } = renderAt(<LoginPage />, { route: '/login?next=%2F%2Fevil.example' });
    vi.mocked(auth.login).mockResolvedValue(makeAccount('CANDIDATE'));
    await fillAndSubmit(user);
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/candidate'));
  });

  it('shows one generic message for bad credentials and leaves the form usable', async () => {
    const user = userEvent.setup();
    const { auth } = renderAt(<LoginPage />, { route: '/login' });
    vi.mocked(auth.login).mockRejectedValue(axiosStatus(401));
    await fillAndSubmit(user);

    expect(await screen.findByText('Email ou mot de passe incorrect.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Se connecter' })).toBeEnabled();
    expect(screen.getByLabelText('Email')).toHaveValue('a@mail.tn');
  });

  it('tells a locked-out user to wait', async () => {
    const user = userEvent.setup();
    const { auth } = renderAt(<LoginPage />, { route: '/login' });
    vi.mocked(auth.login).mockRejectedValue(axiosStatus(429));
    await fillAndSubmit(user);
    expect(await screen.findByText(/bloqué 15 minutes/)).toBeInTheDocument();
  });

  it('asks for both fields without calling the API', async () => {
    const user = userEvent.setup();
    const { auth } = renderAt(<LoginPage />, { route: '/login' });
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));
    expect(screen.getByText('Renseignez votre email et votre mot de passe.')).toBeInTheDocument();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('confirms the account creation after registration', () => {
    renderAt(<LoginPage />, { route: '/login?registered=1' });
    expect(screen.getByText('Compte créé')).toBeInTheDocument();
  });

  it('sends an already signed-in visitor to their space', () => {
    renderAt(<LoginPage />, { route: '/login', account: makeAccount('ADMIN') });
    expect(screen.getByTestId('location')).toHaveTextContent('/admin');
  });
});
