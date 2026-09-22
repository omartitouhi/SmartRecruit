import { Link, Outlet } from "react-router-dom";
import "../../App.css";

export function AppLayout() {
  return (
    <div>
      <header>
        <strong>SmartRecruit</strong>
        <nav>
          <Link to="/login">Connexion</Link>
          <Link to="/register">Inscription</Link>
          <Link to="/candidate/profile">Profil candidat</Link>
          <Link to="/candidate/jobs">Offres</Link>
          <Link to="/recruiter/company">Espace recruteur</Link>
          <Link to="/admin">Administration</Link>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
