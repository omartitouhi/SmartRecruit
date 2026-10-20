import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthContext, type AuthContextValue } from '../auth/context';
import { ToastProvider } from '../components/ui/ToastProvider';
import type { Account } from '../types';

/** Shows where the router ended up, so tests can assert redirects. */
export function LocationProbe() {
  const { pathname, search, state } = useLocation();
  return (
    <div>
      <span data-testid="location">{pathname + search}</span>
      <span data-testid="state">{JSON.stringify(state)}</span>
    </div>
  );
}

interface Options {
  /** Initial URL, e.g. `/candidate/jobs?page=1`. */
  route?: string;
  /** Route pattern the element is mounted on (default: the route's own path). */
  path?: string;
  /** Signed-in account; `null` = anonymous. */
  account?: Account | null;
  status?: AuthContextValue['status'];
}

export const makeAccount = (role: Account['role'] = 'CANDIDATE', overrides: Partial<Account> = {}): Account => ({
  id: `${role.toLowerCase()}-id`,
  email: `${role.toLowerCase()}@demo.tn`,
  role,
  status: 'ACTIVE',
  createdAt: '2026-09-01T09:00:00Z',
  updatedAt: '2026-09-01T09:00:00Z',
  ...overrides,
});

/**
 * Renders `ui` under a router, a stubbed session and the toast provider. Any other URL lands on a
 * LocationProbe, so a redirect is visible as the probe's text.
 */
export function renderAt(ui: ReactElement, { route = '/', path, account = null, status }: Options = {}) {
  const auth: AuthContextValue = {
    status: status ?? (account ? 'authenticated' : 'anonymous'),
    account,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  };
  const view = render(
    <MemoryRouter initialEntries={[route]}>
      <AuthContext.Provider value={auth}>
        <ToastProvider>
          <Routes>
            <Route path={path ?? route.split('?')[0]} element={ui} />
            <Route path="*" element={<LocationProbe />} />
          </Routes>
        </ToastProvider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
  return { auth, ...view };
}
