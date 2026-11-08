import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../../api/auth';
import { HttpError } from '../../lib/errors';
import { renderAt } from '../../test/render';
import { ForgotPasswordPage, ResetPasswordPage } from './PasswordPages';

vi.mock('../../api/auth', () => ({
  authApi: { register: vi.fn(), login: vi.fn(), me: vi.fn(), logout: vi.fn(), forgotPassword: vi.fn(), resetPassword: vi.fn() },
}));

const api = vi.mocked(authApi);

beforeEach(() => vi.resetAllMocks());

describe('forgot password', () => {
  it('confirms with a generic message that does not reveal whether the account exists', async () => {
    const user = userEvent.setup();
    api.forgotPassword.mockResolvedValue();
    renderAt(<ForgotPasswordPage />, { route: '/forgot-password' });
    await user.type(screen.getByLabelText('Email'), 'inconnu@mail.tn');
    await user.click(screen.getByRole('button', { name: 'Envoyer le lien' }));

    expect(api.forgotPassword).toHaveBeenCalledWith('inconnu@mail.tn');
    expect(await screen.findByText('Vérifiez votre boîte mail')).toBeInTheDocument();
    expect(screen.getByText(/Si un compte existe pour inconnu@mail.tn/)).toBeInTheDocument();
  });

  it('asks for the email first', async () => {
    const user = userEvent.setup();
    renderAt(<ForgotPasswordPage />, { route: '/forgot-password' });
    await user.click(screen.getByRole('button', { name: 'Envoyer le lien' }));
    expect(screen.getByText('Renseignez votre email.')).toBeInTheDocument();
    expect(api.forgotPassword).not.toHaveBeenCalled();
  });
});

describe('reset password', () => {
  const show = (query = '?token=tok123') => renderAt(<ResetPasswordPage />, { route: `/reset-password${query}` });

  it('shows the new-password form when the link carries a token, and sends it', async () => {
    const user = userEvent.setup();
    api.resetPassword.mockResolvedValue();
    show();
    await user.type(screen.getByLabelText('Nouveau mot de passe'), 'nouveaumdp1');
    await user.type(screen.getByLabelText('Confirmer le mot de passe'), 'nouveaumdp1');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(api.resetPassword).toHaveBeenCalledWith('tok123', 'nouveaumdp1');
    expect(await screen.findByText('Mot de passe modifié')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Se connecter' })).toHaveAttribute('href', '/login');
  });

  it('checks the length and the confirmation before calling the API', async () => {
    const user = userEvent.setup();
    show();
    await user.type(screen.getByLabelText('Nouveau mot de passe'), 'court');
    await user.type(screen.getByLabelText('Confirmer le mot de passe'), 'autre');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(screen.getByText('Entre 8 et 72 caractères.')).toBeInTheDocument();
    expect(screen.getByText('Les deux mots de passe ne correspondent pas.')).toBeInTheDocument();
    expect(api.resetPassword).not.toHaveBeenCalled();
  });

  it('explains an invalid or expired link', async () => {
    const user = userEvent.setup();
    api.resetPassword.mockRejectedValue(new HttpError(400));
    show();
    await user.type(screen.getByLabelText('Nouveau mot de passe'), 'nouveaumdp1');
    await user.type(screen.getByLabelText('Confirmer le mot de passe'), 'nouveaumdp1');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(await screen.findByText(/invalide ou a expiré/)).toBeInTheDocument();
  });

  it('asks for a new link when the token is missing', () => {
    show('');
    expect(screen.getByText('Lien incomplet')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Demander un nouveau lien' })).toHaveAttribute('href', '/forgot-password');
  });
});
