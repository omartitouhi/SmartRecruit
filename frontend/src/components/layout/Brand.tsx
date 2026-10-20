import { Link } from 'react-router-dom';
import { USE_MOCKS } from '../../api/client';
import { resetMockData } from '../../api/mock/store';

export function Brand({ to = '/' }: { to?: string }) {
  return (
    <Link to={to} className="brand" aria-label="SmartRecruit, accueil">
      <span className="brand__mark" aria-hidden="true" />
      SmartRecruit
    </Link>
  );
}

/** Rappel visible que les données affichées sont fictives (VITE_USE_MOCKS=true). */
export function DemoBanner() {
  if (!USE_MOCKS) return null;
  return (
    <div className="demo-banner">
      <span>
        Mode démonstration : données fictives, aucun backend appelé. Comptes <b>candidat@demo.tn</b>,{' '}
        <b>recruteur@demo.tn</b>, <b>admin@demo.tn</b> · mot de passe <b>demo1234</b>
      </span>
      <button
        type="button"
        className="demo-banner__reset"
        onClick={() => {
          resetMockData();
          window.location.reload();
        }}
      >
        Réinitialiser les données
      </button>
    </div>
  );
}

export function Footer() {
  return (
    <footer className="footer">
      <div className="footer__in">
        <span className="footer__spacer">SmartRecruit · plateforme de recrutement, Tunis</span>
        <a href="/#confidentialite">Confidentialité et RGPD</a>
        <a href="/#conditions">Conditions d’utilisation</a>
        <a href="mailto:contact@smartrecruit.tn">contact@smartrecruit.tn</a>
      </div>
    </footer>
  );
}
