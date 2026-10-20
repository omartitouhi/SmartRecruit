import { AxiosError } from 'axios';
import { describe, expect, it } from 'vitest';
import { HttpError, isStatus, toUiError } from './errors';

const axiosError = (status: number | null, data?: unknown) =>
  new AxiosError(
    'failed',
    'ERR',
    undefined,
    null,
    status === null ? undefined : { status, data, statusText: '', headers: {}, config: {} as never },
  );

describe('toUiError', () => {
  it('uses the API message and field errors of a 400', () => {
    const ui = toUiError(axiosError(400, { message: 'Requête invalide', fieldErrors: { title: 'obligatoire' } }));
    expect(ui).toEqual({ status: 400, message: 'Requête invalide', fieldErrors: { title: 'obligatoire' } });
  });

  it('falls back to a French message when the body is empty (gateway down)', () => {
    expect(toUiError(axiosError(502)).message).toMatch(/injoignable/);
    expect(toUiError(axiosError(410)).message).toMatch(/plus valide/);
    expect(toUiError(axiosError(422)).message).toMatch(/Action impossible/);
    expect(toUiError(axiosError(418)).message).toBe('Une erreur est survenue. Réessayez.');
  });

  it('reports a network failure with a null status', () => {
    const ui = toUiError(axiosError(null));
    expect(ui.status).toBeNull();
    expect(ui.message).toMatch(/Connexion au serveur impossible/);
  });

  it('reads the status and message of a mock HttpError', () => {
    const ui = toUiError(new HttpError(409, 'Déjà postulé'));
    expect(ui).toEqual({ status: 409, message: 'Déjà postulé', fieldErrors: {} });
    expect(new HttpError(404).message).toBe('Ressource introuvable.');
  });

  it('handles anything else generically', () => {
    expect(toUiError(new Error('boom'))).toEqual({
      status: null,
      message: 'Une erreur est survenue. Réessayez.',
      fieldErrors: {},
    });
  });
});

describe('isStatus', () => {
  it('compares the normalised status', () => {
    expect(isStatus(axiosError(404), 404)).toBe(true);
    expect(isStatus(new HttpError(403), 404)).toBe(false);
  });
});
