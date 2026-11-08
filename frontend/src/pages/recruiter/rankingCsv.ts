import { fullName } from '../../api/candidates';
import { toCsv } from '../../lib/csv';
import { APPLICATION_STATUS, formatScore } from '../../lib/format';
import type { Application, CandidateView } from '../../types';

export interface RankingRow {
  application: Application;
  candidate: CandidateView | null;
}

export const RANKING_CSV_HEADER = ['Rang', 'Candidat·e', 'Email', 'Statut', 'Score / 100', 'Candidature reçue le', 'Note moyenne / 5'];

const day = (iso: string) => iso.slice(0, 10);

/**
 * Classement exporté en CSV (EXT-FE) : une ligne par candidature non retirée, dans l'ordre du classement.
 * Un candidat supprimé n'expose ni nom ni email ; les nombres sont écrits à la française (virgule).
 */
export function buildRankingCsv(rows: RankingRow[]) {
  const ranked = rows.filter((r) => r.application.status !== 'WITHDRAWN');
  const lines = ranked.map(({ application, candidate }, i) => {
    const live = candidate && !candidate.deleted ? candidate : null;
    return [
      i + 1,
      candidate ? fullName(candidate) : 'Profil indisponible',
      live?.email ?? '',
      APPLICATION_STATUS[application.status].label,
      application.matchScore ? formatScore(application.matchScore.score) : '',
      day(application.appliedAt),
      application.averageRating != null ? formatScore(application.averageRating) : '',
    ];
  });
  return toCsv([RANKING_CSV_HEADER, ...lines]);
}
