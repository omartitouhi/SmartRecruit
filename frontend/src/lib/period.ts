/** Période des statistiques : filtre les candidatures sur leur date de dépôt (`from`/`to` inclus, AAAA-MM-JJ). */
export interface StatsPeriod {
  from?: string;
  to?: string;
}

/** `''` = depuis le début ; sinon les `days` derniers jours, aujourd'hui compris. */
export function periodOf(days: string): StatsPeriod {
  if (!days) return {};
  const from = new Date(Date.now() - (Number(days) - 1) * 86_400_000);
  return { from: from.toISOString().slice(0, 10) };
}
