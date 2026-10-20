import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ForbiddenPage } from '../pages/ForbiddenPage';
import { LocationProbe, makeAccount } from '../test/render';
import { AuthContext, type AuthContextValue } from './context';
import { RequireRole } from './RequireRole';

function renderGuarded(route: string, auth: Partial<AuthContextValue>, roles: ('CANDIDATE' | 'RECRUITER' | 'ADMIN')[] = ['ADMIN']) {
  const value: AuthContextValue = {
    status: 'anonymous',
    account: null,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    ...auth,
  };
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthContext.Provider value={value}>
        <Routes>
          <Route path="/admin" element={<RequireRole roles={roles}>secret admin page</RequireRole>} />
          <Route path="/forbidden" element={<ForbiddenPage />} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

describe('RequireRole', () => {
  it('sends an anonymous visitor to /login, remembering where they were going', () => {
    renderGuarded('/admin?tab=users', {});
    expect(screen.getByTestId('location')).toHaveTextContent('/login?next=%2Fadmin%3Ftab%3Dusers');
    expect(screen.queryByText('secret admin page')).not.toBeInTheDocument();
  });

  it('shows the 403 page to a signed-in user of another role', () => {
    renderGuarded('/admin', { status: 'authenticated', account: makeAccount('CANDIDATE') });
    expect(screen.getByText('403')).toBeInTheDocument();
    expect(screen.getByText(/Vous n’avez pas accès à cette page/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Retour à mon espace' })).toHaveAttribute('href', '/candidate');
    expect(screen.queryByText('secret admin page')).not.toBeInTheDocument();
  });

  it('renders the page for an allowed role', () => {
    renderGuarded('/admin', { status: 'authenticated', account: makeAccount('ADMIN') });
    expect(screen.getByText('secret admin page')).toBeInTheDocument();
  });

  it('accepts any of several roles', () => {
    renderGuarded('/admin', { status: 'authenticated', account: makeAccount('RECRUITER') }, ['RECRUITER', 'ADMIN']);
    expect(screen.getByText('secret admin page')).toBeInTheDocument();
  });

  it('shows a loading skeleton while the session is restored, not a redirect', () => {
    renderGuarded('/admin', { status: 'loading' });
    expect(screen.getByLabelText('Chargement')).toBeInTheDocument();
    expect(screen.queryByTestId('location')).not.toBeInTheDocument();
  });
});
