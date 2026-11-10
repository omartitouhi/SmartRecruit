import { expect, test as setup } from '@playwright/test';
import { ACCOUNTS, stateFile } from './accounts';

// Vraie connexion par l'interface pour chacun des 3 rôles ; la session est sauvegardée pour les autres tests.
for (const [role, account] of Object.entries(ACCOUNTS)) {
  setup(`connexion ${role} par le formulaire`, async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(account.email);
    await page.getByLabel('Mot de passe').fill(account.password);
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(page).toHaveURL(new RegExp(`${account.home}$`));
    await page.context().storageState({ path: stateFile(role) });
  });
}

setup('mot de passe incorrect : message clair, pas de connexion', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill(ACCOUNTS.candidate.email);
  await page.getByLabel('Mot de passe').fill('mauvais-mot-de-passe');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByText('Email ou mot de passe incorrect.')).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});
