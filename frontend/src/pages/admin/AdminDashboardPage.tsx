import { useState } from 'react';
import { dashboardApi, type DashboardStats } from '../../api/dashboard';
import { Button } from '../../components/ui/Button';
import { EmptyState, ErrorState, Skeleton, SkeletonRows } from '../../components/ui/States';
import { APPLICATION_STATUS, formatDateShort, formatDateTime, formatInt, formatScore } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { ApplicationStatus } from '../../types';

const AUDIT_PAGE_SIZE = 10;

/** Libellés lisibles des actions journalisées (BE-ADMIN-03) ; inconnues : affichées telles quelles. */
const ACTION_LABEL: Record<string, string> = {
  USER_REGISTERED: 'Inscription',
  USER_DELETED: 'Suppression de compte (RGPD)',
  USER_DISABLED: 'Compte désactivé',
  CV_UPLOADED: 'CV déposé',
  CV_VIEWED: 'CV consulté',
  JOB_CREATED: 'Offre créée',
  JOB_PUBLISHED: 'Offre publiée',
  JOB_CLOSED: 'Offre clôturée',
  JOB_MODERATED: 'Offre modérée',
  APPLICATION_CREATED: 'Candidature envoyée',
  APPLICATION_STATUS_CHANGED: 'Statut de candidature modifié',
};

export function AdminDashboardPage() {
  useDocumentTitle('Tableau de bord');
  const stats = useAsync(() => dashboardApi.stats(), []);
  const [page, setPage] = useState(0);
  const audit = useAsync(() => dashboardApi.auditLog(page, AUDIT_PAGE_SIZE), [page]);

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Tableau de bord</h1>
          <p className="page-head__text">Activité de la plateforme et journal d’audit.</p>
        </div>
      </header>

      {stats.error ? (
        <ErrorState error={stats.error} onRetry={stats.reload} />
      ) : stats.loading || !stats.data ? (
        <div className="tiles">
          {Array.from({ length: 4 }, (_, i) => (
            <div className="panel tile" key={i}>
              <Skeleton width="50%" />
              <Skeleton width="40%" height={36} style={{ marginTop: 12 }} />
            </div>
          ))}
        </div>
      ) : (
        <Overview stats={stats.data} />
      )}

      <section className="panel panel--flush audit" aria-labelledby="audit-title">
        <div className="panel__head audit__head">
          <div>
            <h2 className="panel__title" id="audit-title">
              Journal d’audit
            </h2>
            <p className="panel__sub">Actions sensibles : consultations de CV, changements de statut, suppressions.</p>
          </div>
        </div>
        {audit.error ? (
          <div style={{ padding: 24 }}>
            <ErrorState error={audit.error} onRetry={audit.reload} />
          </div>
        ) : audit.loading || !audit.data ? (
          <div style={{ padding: 24 }}>
            <SkeletonRows rows={6} />
          </div>
        ) : audit.data.content.length === 0 ? (
          <div style={{ padding: 24 }}>
            <EmptyState title="Journal vide">Aucune action enregistrée pour l’instant.</EmptyState>
          </div>
        ) : (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Action</th>
                    <th>Ressource</th>
                    <th>Auteur</th>
                  </tr>
                </thead>
                <tbody>
                  {audit.data.content.map((entry) => (
                    <tr key={entry.id}>
                      <td className="muted num">{formatDateTime(entry.timestamp)}</td>
                      <td className="cell-strong">{ACTION_LABEL[entry.action] ?? entry.action}</td>
                      <td>
                        {entry.resourceType} <span className="muted id">{entry.resourceId.slice(0, 8)}</span>
                      </td>
                      <td className="muted id">{entry.actorId.slice(0, 8)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <nav className="pager audit__pager" aria-label="Pagination du journal">
              <Button variant="ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Plus récentes
              </Button>
              <span className="muted">
                Page {audit.data.page + 1} sur {audit.data.totalPages} · {formatInt(audit.data.totalElements)} entrées
              </span>
              <Button variant="ghost" disabled={page + 1 >= audit.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Plus anciennes
              </Button>
            </nav>
          </>
        )}
      </section>
    </div>
  );
}

function Overview({ stats }: { stats: DashboardStats }) {
  const totalApplications = Object.values(stats.applicationsByStatus).reduce((a, b) => a + b, 0);
  const tiles = [
    { label: 'Candidats inscrits', value: formatInt(stats.accountsByRole.CANDIDATE) },
    { label: 'Recruteurs', value: formatInt(stats.accountsByRole.RECRUITER) },
    { label: 'Offres publiées', value: formatInt(stats.publishedJobs) },
    {
      label: 'Score moyen des candidatures',
      value: stats.averageMatchScore === null ? '—' : formatScore(stats.averageMatchScore),
      unit: stats.averageMatchScore === null ? undefined : '/ 100',
    },
  ];

  return (
    <>
      <div className="tiles">
        {tiles.map((t) => (
          <div className="panel tile" key={t.label}>
            <p className="tile__label">{t.label}</p>
            <p className="tile__value">
              {t.value}
              {t.unit && <span className="tile__unit"> {t.unit}</span>}
            </p>
          </div>
        ))}
      </div>
      <div className="split split--even charts">
        <WeeklyChart weeks={stats.applicationsPerWeek} />
        <Pipeline byStatus={stats.applicationsByStatus} total={totalApplications} />
      </div>
    </>
  );
}

/** Colonnes : une seule série (candidatures par semaine), info-bulle au survol et au clavier. */
function WeeklyChart({ weeks }: { weeks: DashboardStats['applicationsPerWeek'] }) {
  const [active, setActive] = useState<number | null>(null);
  const max = Math.max(...weeks.map((w) => w.count), 1);
  // Clean axis: round the top up to a multiple of 2, 5 or 10.
  const step = max <= 4 ? 1 : max <= 10 ? 2 : max <= 25 ? 5 : 10;
  const top = Math.ceil(max / step) * step;
  const ticks = Array.from({ length: top / step + 1 }, (_, i) => i * step);
  const peak = weeks.reduce((best, w, i) => (w.count > weeks[best].count ? i : best), 0);
  const last = weeks.length - 1;

  return (
    <section className="panel chart" aria-labelledby="weekly-title">
      <h2 className="panel__title" id="weekly-title">
        Candidatures par semaine
      </h2>
      <p className="panel__sub">Les 8 dernières semaines</p>
      <div className="chart__plot" role="img" aria-label={`Candidatures par semaine, de ${weeks[0] ? formatDateShort(weeks[0].weekStart) : ''} à aujourd’hui. Détail dans le tableau ci-dessous.`}>
        <div className="chart__grid" aria-hidden="true">
          {ticks.map((t) => (
            <div key={t} className="chart__gridline" style={{ bottom: `${(t / top) * 100}%` }}>
              <span>{t}</span>
            </div>
          ))}
        </div>
        <div className="chart__cols">
          {weeks.map((w, i) => (
            <div
              key={w.weekStart}
              className="chart__col"
              tabIndex={0}
              aria-label={`Semaine du ${formatDateShort(w.weekStart)} : ${w.count} candidatures`}
              onPointerEnter={() => setActive(i)}
              onPointerLeave={() => setActive(null)}
              onFocus={() => setActive(i)}
              onBlur={() => setActive(null)}
            >
              <div className="chart__bar" data-active={active === i} style={{ height: `${(w.count / top) * 100}%` }}>
                {(i === peak || i === last) && w.count > 0 && <span className="chart__value">{w.count}</span>}
              </div>
              {active === i && (
                <div className="chart__tip" role="tooltip">
                  <span className="muted">Semaine du {formatDateShort(w.weekStart)}</span>
                  <b>{pluralizeApplications(w.count)}</b>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
      <div className="chart__x" aria-hidden="true">
        {weeks.map((w, i) => (
          <span key={w.weekStart}>{i % 2 === last % 2 ? formatDateShort(w.weekStart) : ''}</span>
        ))}
      </div>
      <details className="chart__table">
        <summary>Voir les données</summary>
        <table className="table">
          <thead>
            <tr>
              <th>Semaine du</th>
              <th>Candidatures</th>
            </tr>
          </thead>
          <tbody>
            {weeks.map((w) => (
              <tr key={w.weekStart}>
                <td>{formatDateShort(w.weekStart)}</td>
                <td className="cell-num">{w.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </section>
  );
}

const pluralizeApplications = (n: number) => `${formatInt(n)} candidature${n > 1 ? 's' : ''}`;

const PIPELINE: ApplicationStatus[] = ['RECEIVED', 'IN_REVIEW', 'INTERVIEW', 'ACCEPTED', 'REJECTED', 'WITHDRAWN'];

/** Barres horizontales : valeur directement en bout de barre, une seule teinte. */
function Pipeline({ byStatus, total }: { byStatus: Record<ApplicationStatus, number>; total: number }) {
  const max = Math.max(...PIPELINE.map((s) => byStatus[s] ?? 0), 1);
  return (
    <section className="panel chart" aria-labelledby="pipeline-title">
      <h2 className="panel__title" id="pipeline-title">
        Candidatures par statut
      </h2>
      <p className="panel__sub">{pluralizeApplications(total)} au total</p>
      <ul className="hbars">
        {PIPELINE.map((s) => {
          const n = byStatus[s] ?? 0;
          return (
            <li key={s} className="hbars__row">
              <span className="hbars__label">{APPLICATION_STATUS[s].label}</span>
              <span className="hbars__track">
                <span className="hbars__bar" style={{ width: `${(n / max) * 100}%` }} />
                <span className="hbars__value">{n}</span>
              </span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
