import { APPLICATION_STATUS, formatDateTime } from '../../lib/format';
import type { StatusHistoryEntry } from '../../types';

/** Historique d'une candidature, avec le message du recruteur quand il en a laissé un (EXT-APP-01). */
export function StatusTimeline({ history, messageLabel = 'Message au candidat' }: {
  history: StatusHistoryEntry[];
  messageLabel?: string;
}) {
  return (
    <ol className="timeline">
      {history.map((h, i) => (
        <li key={i}>
          <span className="timeline__dot" aria-hidden="true" />
          <span>
            <span className="cell-strong">{APPLICATION_STATUS[h.status].label}</span>
            <span className="muted"> · {formatDateTime(h.changedAt)}</span>
            {h.message && (
              <blockquote className="timeline__message">
                <span className="visually-hidden">{messageLabel} : </span>
                {h.message}
              </blockquote>
            )}
          </span>
        </li>
      ))}
    </ol>
  );
}
