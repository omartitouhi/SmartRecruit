import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { HOME_BY_ROLE, useAuth } from '../../auth/context';
import { initials, ROLE_LABEL } from '../../lib/format';
import type { Role } from '../../types';
import { Brand, DemoBanner } from './Brand';

const NAV: Record<Role, { to: string; label: string; end?: boolean }[]> = {
  CANDIDATE: [
    { to: '/candidate', label: 'Tableau de bord', end: true },
    { to: '/candidate/jobs', label: 'Offres' },
    { to: '/candidate/recommendations', label: 'Recommandées' },
    { to: '/candidate/saved', label: 'Favoris' },
    { to: '/candidate/applications', label: 'Mes candidatures' },
    { to: '/candidate/profile', label: 'Mon profil' },
  ],
  RECRUITER: [
    { to: '/recruiter', label: 'Tableau de bord', end: true },
    { to: '/recruiter/jobs', label: 'Mes offres', end: true },
    { to: '/recruiter/jobs/new', label: 'Publier une offre' },
    { to: '/recruiter/company', label: 'Entreprise' },
    { to: '/recruiter/team', label: 'Équipe' },
  ],
  ADMIN: [
    { to: '/admin', label: 'Tableau de bord', end: true },
    { to: '/admin/users', label: 'Utilisateurs' },
    { to: '/admin/jobs', label: 'Offres' },
    { to: '/admin/audit-log', label: 'Journal d’audit' },
  ],
};

export function AppLayout() {
  const { account, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [navOpen, setNavOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  // Close menus on navigation (state adjusted during render, not in an effect).
  const [path, setPath] = useState(location.pathname);
  if (path !== location.pathname) {
    setPath(location.pathname);
    setNavOpen(false);
    setMenuOpen(false);
  }

  useEffect(() => {
    if (!menuOpen) return;
    const onPointer = (e: PointerEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) setMenuOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setMenuOpen(false);
    document.addEventListener('pointerdown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [menuOpen]);

  if (!account) return null;

  async function onLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <>
      <a className="skip-link" href="#main">
        Aller au contenu
      </a>
      <DemoBanner />
      <header className="bar" data-open={navOpen}>
        <div className="bar__in">
          <Brand to={HOME_BY_ROLE[account.role]} />
          <nav className="nav" id="app-nav" aria-label="Navigation de l’espace">
            {NAV[account.role].map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className="nav__link">
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="bar__actions">
            <div className="account" ref={menuRef}>
              <button
                type="button"
                className="account__button"
                aria-expanded={menuOpen}
                aria-haspopup="menu"
                onClick={() => setMenuOpen((o) => !o)}
              >
                <span className="avatar" aria-hidden="true">
                  {initials(account.email)}
                </span>
                <span className="account__label">{ROLE_LABEL[account.role]}</span>
              </button>
              {menuOpen && (
                <div className="account__menu" role="menu">
                  <div className="account__meta">
                    <div className="cell-strong" style={{ color: 'var(--strong)', fontWeight: 500 }}>
                      {account.email}
                    </div>
                    <div className="muted">{ROLE_LABEL[account.role]}</div>
                  </div>
                  {account.role === 'CANDIDATE' && (
                    <NavLink role="menuitem" className="account__item" to="/candidate/profile">
                      Mon profil
                    </NavLink>
                  )}
                  <NavLink role="menuitem" className="account__item" to="/">
                    Page d’accueil
                  </NavLink>
                  <button role="menuitem" type="button" className="account__item" onClick={onLogout}>
                    Se déconnecter
                  </button>
                </div>
              )}
            </div>
            <button
              type="button"
              className="btn btn--quiet menu-toggle"
              aria-expanded={navOpen}
              aria-controls="app-nav"
              onClick={() => setNavOpen((o) => !o)}
            >
              {navOpen ? 'Fermer' : 'Menu'}
            </button>
          </div>
        </div>
      </header>
      <main id="main" className="main">
        <Outlet />
      </main>
    </>
  );
}
