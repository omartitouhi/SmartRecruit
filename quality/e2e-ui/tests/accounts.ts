import path from 'node:path';

// Comptes créés par backend/seed/seed_demo.py ; l'administrateur vient de .env (ADMIN_EMAIL / ADMIN_PASSWORD).
export const ACCOUNTS = {
  candidate: { email: 'candidat@smartrecruit.test', password: 'Motdepasse123', home: '/candidate' },
  recruiter: { email: 'recruteur@smartrecruit.test', password: 'Motdepasse123', home: '/recruiter' },
  admin: {
    email: process.env.ADMIN_EMAIL ?? 'admin@smartrecruit.local',
    password: process.env.ADMIN_PASSWORD ?? 'Admin-Dev-2026!',
    home: '/admin',
  },
} as const;

export type Role = keyof typeof ACCOUNTS;

export const stateFile = (role: string) => path.join(__dirname, '..', '.auth', `${role}.json`);
