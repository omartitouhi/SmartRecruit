import type { ApplicationNote } from '../types';

export const NOTE_MAX = 2000;

/** Nombre de notes et moyenne des notes chiffrées (1 décimale), comme l'API les calcule (EXT-APP-03). */
export function noteStats(notes: ApplicationNote[]) {
  const rated = notes.flatMap((n) => (n.rating ? [n.rating] : []));
  return {
    notesCount: notes.length,
    averageRating: rated.length ? Math.round((rated.reduce((a, b) => a + b, 0) / rated.length) * 10) / 10 : null,
  };
}
