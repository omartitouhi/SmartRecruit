import { companiesApi } from '../api/companies';
import { toUiError } from './errors';
import { useAsync } from './useAsync';

/** Entreprise du recruteur connecté ; `null` (et non une erreur) tant qu'il n'en a pas créé. */
export function useMyCompany() {
  return useAsync(
    () => companiesApi.getMine().catch((e) => (toUiError(e).status === 404 ? null : Promise.reject(e))),
    [],
  );
}
