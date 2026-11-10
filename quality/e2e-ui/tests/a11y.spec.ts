import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { stateFile } from './accounts';

// Accessibilité de base (ISO 25010 – facilité d'utilisation) : axe-core, règles WCAG 2.1 A/AA.
// Échec sur toute violation "critical" ou "serious" ; le détail complet est écrit dans reports/a11y/.
const PAGES: { name: string; url: string; role?: string }[] = [
  { name: 'connexion', url: '/login' },
  { name: 'inscription', url: '/register' },
  { name: 'recherche-offres', url: '/candidate/jobs', role: 'candidate' },
  { name: 'candidatures', url: '/candidate/applications', role: 'candidate' },
  { name: 'tableau-de-bord-recruteur', url: '/recruiter', role: 'recruiter' },
  { name: 'journal-audit', url: '/admin/audit-log', role: 'admin' },
];

for (const p of PAGES) {
  test.describe(p.name, () => {
    if (p.role) test.use({ storageState: stateFile(p.role) });

    test(`accessibilité WCAG A/AA : ${p.name}`, async ({ page }, info) => {
      test.skip(info.project.name !== 'chromium', 'audit axe exécuté une fois (chromium)');
      await page.goto(p.url);
      await page.getByRole('heading', { level: 1 }).first().waitFor();
      const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      const dir = path.join(__dirname, '..', '..', 'reports', 'a11y');
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(path.join(dir, `${p.name}.json`), JSON.stringify(result.violations, null, 2));
      const blocking = result.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious');
      expect(blocking.map((v) => `${v.id} (${v.impact}) x${v.nodes.length}`)).toEqual([]);
    });
  });
}
