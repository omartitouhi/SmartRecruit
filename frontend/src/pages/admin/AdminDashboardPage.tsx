import { dashboardApi } from '../../api/dashboard';
import { ButtonLink } from '../../components/ui/Button';
import { ErrorState, Skeleton } from '../../components/ui/States';
import { formatInt, formatScore, ROLE_LABEL } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { GlobalStats, Role } from '../../types';

const ROLES: Role[] = ['CANDIDATE', 'RECRUITER', 'ADMIN'];

export function AdminDashboardPage() {
  useDocumentTitle('Tableau de bord');
  const stats = useAsync(() => dashboardApi.globalStats(), []);

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Tableau de bord</h1>
          <p className="page-head__text">Activité de la plateforme et outils d’administration.</p>
        </div>
        <div className="detail-actions">
          <ButtonLink to="/admin/users" variant="ghost">
            Utilisateurs
          </ButtonLink>
          <ButtonLink to="/admin/jobs" variant="ghost">
            Modération des offres
          </ButtonLink>
          <ButtonLink to="/admin/audit-log" variant="ghost">
            Journal d’audit
          </ButtonLink>
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
    </div>
  );
}

function Overview({ stats }: { stats: GlobalStats }) {
  const tiles = [
    { label: 'Offres publiées', value: formatInt(stats.activeJobs) },
    { label: 'Candidatures', value: formatInt(stats.totalApplications) },
    { label: 'Utilisateurs', value: formatInt(stats.totalUsers) },
    {
      label: 'Score moyen des candidatures',
      value: stats.averageMatchScore === null ? '—' : formatScore(stats.averageMatchScore),
      unit: stats.averageMatchScore === null ? undefined : '/ 100',
    },
  ];
  const max = Math.max(...ROLES.map((r) => stats.usersByRole[r] ?? 0), 1);

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
      <section className="panel chart" aria-labelledby="roles-title">
        <h2 className="panel__title" id="roles-title">
          Utilisateurs par rôle
        </h2>
        <ul className="hbars">
          {ROLES.map((role) => {
            const n = stats.usersByRole[role] ?? 0;
            return (
              <li key={role} className="hbars__row">
                <span className="hbars__label">{ROLE_LABEL[role]}</span>
                <span className="hbars__track">
                  <span className="hbars__bar" style={{ width: `${(n / max) * 100}%` }} />
                  <span className="hbars__value">{n}</span>
                </span>
              </li>
            );
          })}
        </ul>
      </section>
    </>
  );
}
