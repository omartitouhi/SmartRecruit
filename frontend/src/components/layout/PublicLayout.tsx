import { NavLink, Outlet } from 'react-router-dom';
import { HOME_BY_ROLE, useAuth } from '../../auth/context';
import { ButtonLink } from '../ui/Button';
import { Brand, DemoBanner, Footer } from './Brand';

export function PublicLayout() {
  const { account } = useAuth();
  return (
    <>
      <a className="skip-link" href="#main">
        Aller au contenu
      </a>
      <DemoBanner />
      <header className="bar">
        <div className="bar__in">
          <Brand />
          <nav className="nav nav--public" aria-label="Navigation principale">
            <a className="nav__link" href="/#candidats">
              Candidats
            </a>
            <a className="nav__link" href="/#recruteurs">
              Recruteurs
            </a>
            <a className="nav__link" href="/#score">
              Comment on note
            </a>
          </nav>
          <div className="bar__actions">
            {account ? (
              <ButtonLink to={HOME_BY_ROLE[account.role]}>Mon espace</ButtonLink>
            ) : (
              <>
                <NavLink className="nav__link" to="/login">
                  Connexion
                </NavLink>
                <ButtonLink to="/register" className="bar__register">
                  Créer un compte
                </ButtonLink>
              </>
            )}
          </div>
        </div>
      </header>
      <main id="main" className="main">
        <Outlet />
      </main>
      <Footer />
    </>
  );
}
