import { defineConfig, devices } from '@playwright/test';

// Tests E2E de l'interface (ISO 25010 : aptitude fonctionnelle, facilité d'utilisation, compatibilité).
// Cible : la stack docker compose démarrée avec les données de démo (backend/seed/seed_demo.py).
// La connexion est limitée à 10 essais / 5 min par IP (BE-SEC-05) : le projet "setup" se connecte une fois
// par rôle par l'interface et les autres projets réutilisent la session (storageState).
export default defineConfig({
  testDir: './tests',
  outputDir: '../reports/e2e-ui/artifacts',
  timeout: 30_000,
  retries: 0,
  workers: 2,
  reporter: [
    ['list'],
    ['html', { outputFolder: '../reports/e2e-ui/html', open: 'never' }],
    ['junit', { outputFile: '../reports/e2e-ui/junit.xml' }],
  ],
  use: {
    baseURL: process.env.E2E_WEB ?? 'http://localhost:5173',
    screenshot: 'on',
    trace: 'retain-on-failure',
    locale: 'fr-FR',
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/, use: { ...devices['Desktop Chrome'] } },
    { name: 'chromium', dependencies: ['setup'], use: { ...devices['Desktop Chrome'] } },
    { name: 'edge', dependencies: ['setup'], use: { ...devices['Desktop Edge'], channel: 'msedge' } },
    // Firefox de Playwright ne démarre pas sur le poste de l'équipe (exécutable bloqué par Windows) : PW_FIREFOX=1 l'active.
    ...(process.env.PW_FIREFOX ? [{ name: 'firefox', dependencies: ['setup'], use: { ...devices['Desktop Firefox'] } }] : []),
    { name: 'webkit', dependencies: ['setup'], use: { ...devices['Desktop Safari'] } },
    { name: 'mobile-chrome', dependencies: ['setup'], use: { ...devices['Pixel 7'] } },
  ],
});
