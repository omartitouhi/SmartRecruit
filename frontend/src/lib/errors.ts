import { isAxiosError } from 'axios';
import type { ApiError } from '../types';

/** Erreur normalisée affichée par l'interface, quelle que soit sa source. */
export interface UiError {
  status: number | null;
  message: string;
  fieldErrors: Record<string, string>;
}

const FALLBACK: Record<number, string> = {
  400: 'La requête contient des erreurs.',
  401: 'Votre session a expiré. Reconnectez-vous.',
  403: 'Vous n’avez pas accès à cette ressource.',
  404: 'Ressource introuvable.',
  409: 'Cette ressource existe déjà.',
  410: 'Ce lien n’est plus valide.',
  413: 'Le fichier est trop volumineux.',
  422: 'Action impossible : des informations sont manquantes.',
  429: 'Trop de tentatives. Réessayez dans quelques minutes.',
  503: 'Ce service est momentanément indisponible.',
  // The dev proxy / nginx answer 502–504 with an empty body when the gateway is down.
  502: 'Le serveur est injoignable. Vérifiez que l’API (gateway) est démarrée.',
  504: 'Le serveur met trop de temps à répondre. Réessayez.',
};

export function toUiError(error: unknown): UiError {
  if (isAxiosError<ApiError>(error)) {
    const status = error.response?.status ?? null;
    if (status === null) {
      return {
        status,
        message: 'Connexion au serveur impossible. Vérifiez que l’API est démarrée.',
        fieldErrors: {},
      };
    }
    const body = error.response?.data;
    return {
      status,
      message: body?.message || FALLBACK[status] || 'Une erreur est survenue. Réessayez.',
      fieldErrors: body?.fieldErrors ?? {},
    };
  }
  if (error instanceof HttpError) {
    return { status: error.status, message: error.message, fieldErrors: error.fieldErrors };
  }
  return { status: null, message: 'Une erreur est survenue. Réessayez.', fieldErrors: {} };
}

/** Levée par la couche de démonstration pour imiter une réponse d'erreur de l'API. */
export class HttpError extends Error {
  status: number;
  fieldErrors: Record<string, string>;

  constructor(status: number, message?: string, fieldErrors: Record<string, string> = {}) {
    super(message || FALLBACK[status] || 'Erreur');
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

export const isStatus = (error: unknown, status: number) => toUiError(error).status === status;
