import { tokenStore } from '../api/client';
import { createSeed, DEMO_ACCOUNTS } from '../api/mock/seed';
import { cvBlobs, db } from '../api/mock/store';
import type { Role } from '../types';

export const EMAIL = {
  ...DEMO_ACCOUNTS,
  otherRecruiter: 'rh@kerkennah-data.demo.tn',
  skander: 'skander.jaziri@demo.tn',
} as const;

/** Back to the demo seed, signed out. The mock `delay` is stubbed in the test setup. */
export function resetDb() {
  Object.assign(db, createSeed());
  cvBlobs.clear();
  tokenStore.clear();
}

/** Signs in as a seeded account (the mock token is `mock.<accountId>`). */
export function loginAs(email: string) {
  const account = db.accounts.find((a) => a.email === email);
  if (!account) throw new Error(`no seeded account ${email}`);
  tokenStore.set({ accessToken: `mock.${account.id}`, refreshToken: 'refresh' });
  return account;
}

/** Adds a fresh account with no profile and no company, and signs in as it. */
export function loginAsNew(role: Role, email = `new-${role.toLowerCase()}@demo.tn`) {
  const stamp = new Date().toISOString();
  db.accounts.push({ id: crypto.randomUUID(), email, role, status: 'ACTIVE', createdAt: stamp, updatedAt: stamp, password: 'demo1234' });
  return loginAs(email);
}

export const jobByTitle = (title: string) => {
  const job = db.jobs.find((j) => j.title.startsWith(title));
  if (!job) throw new Error(`no seeded job ${title}`);
  return job;
};

/** Rejects with the HttpError status, for concise `await expectStatus(promise, 409)`. */
export async function statusOf(promise: Promise<unknown>) {
  try {
    await promise;
    return null;
  } catch (error) {
    return (error as { status?: number }).status ?? -1;
  }
}
