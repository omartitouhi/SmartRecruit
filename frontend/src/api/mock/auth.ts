import { HttpError } from '../../lib/errors';
import type { AuthApi } from '../auth';
import { db, save, delay, now, uuid, currentAccount } from './store';

export const mockAuthApi: AuthApi = {
  async register({ email, password, role }) {
    await delay();
    const fieldErrors: Record<string, string> = {};
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) fieldErrors.email = 'L’email n’est pas valide';
    if (password.length < 8 || password.length > 72) {
      fieldErrors.password = 'Le mot de passe doit contenir entre 8 et 72 caractères';
    }
    if (Object.keys(fieldErrors).length) throw new HttpError(400, 'Requête invalide', fieldErrors);
    if (db.accounts.some((a) => a.email.toLowerCase() === email.toLowerCase())) {
      throw new HttpError(409, 'Un compte existe déjà avec cet email.');
    }
    const stamp = now();
    const account = { id: uuid(), email, role, status: 'ACTIVE' as const, createdAt: stamp, updatedAt: stamp, password };
    db.accounts.push(account);
    save();
    const { password: _p, ...publicAccount } = account;
    void _p;
    return publicAccount;
  },

  async login(email, password) {
    await delay();
    const account = db.accounts.find((a) => a.email.toLowerCase() === email.trim().toLowerCase());
    if (!account || account.password !== password || account.status !== 'ACTIVE') {
      throw new HttpError(401, 'Email ou mot de passe incorrect.');
    }
    return { accessToken: `mock.${account.id}`, refreshToken: `mock-refresh.${uuid()}`, tokenType: 'Bearer', expiresIn: 3600 };
  },

  async me() {
    await delay(120);
    return currentAccount();
  },

  async logout() {
    await delay(80);
  },

  async forgotPassword() {
    await delay();
  },

  async resetPassword(token, newPassword) {
    await delay();
    if (newPassword.length < 8) {
      throw new HttpError(400, 'Requête invalide', { newPassword: 'Le mot de passe doit contenir entre 8 et 72 caractères' });
    }
    if (!token) throw new HttpError(400, 'Lien de réinitialisation invalide ou expiré.');
  },
};
