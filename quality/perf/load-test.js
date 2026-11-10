// Test de charge k6 (ISO 25010 – efficacité de performance).
// Exigence : 20 utilisateurs simultanés, p95 du temps de réponse < 500 ms, moins de 1 % d'erreurs.
// Lancement (stack docker compose + données de démo) : voir quality/README.md.
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE = (__ENV.BASE_URL || 'http://host.docker.internal:8080') + '/api/v1';
const PASSWORD = 'Motdepasse123';

export const options = {
  scenarios: {
    utilisateurs_simultanes: { executor: 'constant-vus', vus: 20, duration: '1m' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'max'],
};

function login(email, password) {
  const r = http.post(`${BASE}/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
  });
  if (r.status !== 200) throw new Error(`connexion ${email} : ${r.status}`);
  return { headers: { Authorization: `Bearer ${r.json('accessToken')}` } };
}

// 3 connexions seulement (la connexion est limitée à 10 / 5 min par IP) ; les jetons sont partagés par les VU.
export function setup() {
  const candidate = login('candidat@smartrecruit.test', PASSWORD);
  const recruiter = login('recruteur@smartrecruit.test', PASSWORD);
  const admin = login(__ENV.ADMIN_EMAIL || 'admin@smartrecruit.local', __ENV.ADMIN_PASSWORD || 'Admin-Dev-2026!');
  const jobs = http.get(`${BASE}/jobs?size=20`, candidate).json('content');
  const company = http.get(`${BASE}/users/companies/me`, recruiter).json('id');
  return { candidate, recruiter, admin, jobIds: jobs.map((j) => j.id), company };
}

// Filtre de période du tableau de bord : les 30 derniers jours.
function monthAgo() {
  return new Date(Date.now() - 29 * 86400000).toISOString().slice(0, 10);
}

export default function (data) {
  const jobId = data.jobIds[Math.floor(Math.random() * data.jobIds.length)];

  group('candidat : recherche et consultation', () => {
    const search = http.get(`${BASE}/jobs?keyword=developpeur&location=Tunis&page=0&size=20`, data.candidate);
    check(search, { 'recherche 200': (r) => r.status === 200 });
    const detail = http.get(`${BASE}/jobs/${jobId}`, data.candidate);
    check(detail, { 'détail offre 200': (r) => r.status === 200 });
    const mine = http.get(`${BASE}/applications/me`, data.candidate);
    check(mine, { 'mes candidatures 200': (r) => r.status === 200 });
  });

  group('recruteur : tableau de bord', () => {
    const stats = http.get(`${BASE}/dashboard/stats/company/${data.company}?from=${monthAgo()}`, data.recruiter);
    check(stats, { 'stats entreprise 200': (r) => r.status === 200 });
    const myJobs = http.get(`${BASE}/jobs?companyId=me`, data.recruiter);
    check(myJobs, { 'offres du recruteur 200': (r) => r.status === 200 });
  });

  group('administrateur : audit', () => {
    const audit = http.get(`${BASE}/dashboard/admin/audit-log?page=0&size=20`, data.admin);
    check(audit, { 'journal audit 200': (r) => r.status === 200 });
  });

  sleep(1);
}

export function handleSummary(data) {
  delete data.setup_data; // contient les jetons JWT des comptes de démo : jamais dans le rapport versionné
  return {
    '/reports/k6-summary.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data),
  };
}

function textSummary(data) {
  const d = data.metrics.http_req_duration.values;
  const f = data.metrics.http_req_failed.values;
  const lines = [
    `requêtes : ${data.metrics.http_reqs.values.count} (${data.metrics.http_reqs.values.rate.toFixed(1)}/s)`,
    `durée (ms) : moy ${d.avg.toFixed(1)} | médiane ${d.med.toFixed(1)} | p90 ${d['p(90)'].toFixed(1)} | p95 ${d['p(95)'].toFixed(1)} | max ${d.max.toFixed(1)}`,
    `taux d'erreur HTTP : ${(f.rate * 100).toFixed(2)} %`,
    `checks réussis : ${(data.metrics.checks.values.rate * 100).toFixed(2)} %`,
  ];
  for (const [name, t] of Object.entries(data.metrics).filter(([, m]) => m.thresholds)) {
    for (const [expr, res] of Object.entries(t.thresholds)) lines.push(`seuil ${name} ${expr} : ${res.ok ? 'OK' : 'ÉCHEC'}`);
  }
  return lines.join('\n') + '\n';
}
