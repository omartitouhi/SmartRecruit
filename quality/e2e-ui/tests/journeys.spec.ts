import { expect, test } from '@playwright/test';
import { stateFile } from './accounts';

// Parcours utilisateurs réels, exécutés sur chaque navigateur (compatibilité) et sur mobile.

test.describe('Visiteur non connecté', () => {
  test('une page protégée renvoie vers la connexion', async ({ page }) => {
    await page.goto('/candidate/jobs');
    await expect(page).toHaveURL(/\/login/);
  });

  test('page inconnue : 404 lisible', async ({ page }) => {
    await page.goto('/cette-page-nexiste-pas');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});

test.describe('Candidat', () => {
  test.use({ storageState: stateFile('candidate') });

  test('recherche d’offres filtrée par ville', async ({ page }) => {
    await page.goto('/candidate/jobs');
    await expect(page.getByRole('heading', { name: 'Offres', level: 1 })).toBeVisible();
    await page.getByLabel('Ville').fill('Tunis');
    await page.getByLabel('Ville').press('Enter');
    await expect(page).toHaveURL(/Tunis/);
    await expect(page.locator('main a[href^="/candidate/jobs/"]').first()).toBeVisible();
  });

  test('détail d’une offre et score de compatibilité', async ({ page }) => {
    await page.goto('/candidate/jobs');
    const firstJob = page.locator('main a[href^="/candidate/jobs/"]').first();
    await firstJob.click();
    await expect(page).toHaveURL(/\/candidate\/jobs\/[0-9a-f-]+$/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  test('suivi de ses candidatures (workflow à statuts)', async ({ page }) => {
    await page.goto('/candidate/applications');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  test('accès refusé à l’espace administrateur', async ({ page }) => {
    await page.goto('/admin/audit-log');
    await expect(page).toHaveURL(/\/forbidden/);
  });
});

test.describe('Recruteur', () => {
  test.use({ storageState: stateFile('recruiter') });

  test('tableau de bord avec indicateurs', async ({ page }) => {
    await page.goto('/recruiter');
    await expect(page.getByRole('heading', { name: 'Acme Tech', level: 1 })).toBeVisible();
  });

  test('liste de ses offres', async ({ page }) => {
    await page.goto('/recruiter/jobs');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.locator('main a[href*="/candidates"]').first()).toBeVisible();
  });

  test('classement des candidats d’une offre', async ({ page }) => {
    await page.goto('/recruiter/jobs');
    await page.locator('main a[href*="/candidates"]').first().click();
    await expect(page).toHaveURL(/\/recruiter\/jobs\/[0-9a-f-]+\/candidates/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});

test.describe('Administrateur', () => {
  test.use({ storageState: stateFile('admin') });

  test('journal d’audit : qui a fait quoi, quand', async ({ page }) => {
    await page.goto('/admin/audit-log');
    await expect(page.getByRole('heading', { name: 'Journal d’audit' })).toBeVisible();
    await expect(page.getByText(/entrées$/)).toBeVisible();
  });

  test('gestion des utilisateurs', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.locator('table tbody tr').first()).toBeVisible();
    await expect(page.getByRole('button', { name: /le compte de / }).first()).toBeVisible();
  });
});
