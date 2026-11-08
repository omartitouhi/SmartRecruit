import { APPLICATION_STATUS, formatInt } from '../../lib/format';
import { FUNNEL_STATUSES, type ApplicationsByStatus } from '../../types';

/**
 * Entonnoir de recrutement (EXT-DASH-01) : candidatures par statut, de la réception à la décision.
 * Chaque barre est proportionnelle au plus grand effectif ; le chiffre reste lisible sans la barre.
 */
export function Funnel({ byStatus, title = 'Entonnoir de recrutement', id = 'funnel-title' }: {
  byStatus: ApplicationsByStatus;
  title?: string;
  id?: string;
}) {
  const max = Math.max(...FUNNEL_STATUSES.map((s) => byStatus[s] ?? 0), 1);
  const total = FUNNEL_STATUSES.reduce((sum, s) => sum + (byStatus[s] ?? 0), 0);
  return (
    <section className="panel chart" aria-labelledby={id}>
      <h2 className="panel__title" id={id}>
        {title}
      </h2>
      <p className="panel__sub">{total ? `${formatInt(total)} candidatures actives ou décidées, hors retirées.` : 'Aucune candidature pour l’instant.'}</p>
      <ul className="hbars">
        {FUNNEL_STATUSES.map((status) => {
          const n = byStatus[status] ?? 0;
          return (
            <li key={status} className="hbars__row">
              <span className="hbars__label">{APPLICATION_STATUS[status].label}</span>
              <span className="hbars__track">
                <span className={`hbars__bar hbars__bar--${APPLICATION_STATUS[status].tone}`} style={{ width: `${(n / max) * 100}%` }} />
                <span className="hbars__value">{formatInt(n)}</span>
              </span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
