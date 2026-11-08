import { APPLICATION_STATUS_TRANSITIONS, FUNNEL_STATUSES, type Application, type ApplicationStatus, type FunnelStatus } from '../types';

/** A recruiter may move a card only along the allowed transitions; withdrawing is the candidate's. */
export function canMove(from: ApplicationStatus, to: ApplicationStatus) {
  return to !== 'WITHDRAWN' && APPLICATION_STATUS_TRANSITIONS[from].includes(to);
}

/** Statuses a card may move to from `from`, in the order of the transition table. */
export const movesFrom = (from: ApplicationStatus) => APPLICATION_STATUS_TRANSITIONS[from].filter((to) => canMove(from, to));

/** Cards grouped by column, in the order received (already ranked by score); withdrawn ones are left out. */
export function boardColumns<T extends { application: Application }>(items: T[]): Record<FunnelStatus, T[]> {
  const columns = Object.fromEntries(FUNNEL_STATUSES.map((s) => [s, [] as T[]])) as Record<FunnelStatus, T[]>;
  items.forEach((item) => {
    const status = item.application.status;
    if (status !== 'WITHDRAWN') columns[status].push(item);
  });
  return columns;
}
