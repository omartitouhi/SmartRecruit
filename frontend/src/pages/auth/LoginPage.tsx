import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { isMocked } from '../../api/client';
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from '../../api/mock/seed';
import { HOME_BY_ROLE, useAuth } from '../../auth/context';
import { GuestOnly } from '../../auth/RequireRole';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { Alert } from '../../components/ui/States';
import { toUiError } from '../../lib/errors';
import { useDocumentTitle } from '../../lib/useAsync';
import { AuthShell } from './AuthShell';

/** Only internal paths are accepted as a post-login destination. */
function safeNext(next: string | null) {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : null;
}

export function LoginPage() {
  useDocumentTitle('Connexion');
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const registeredEmail = (useLocation().state as { email?: string } | null)?.email;
  const [email, setEmail] = useState(registeredEmail ?? '');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!email.trim() || !password) {
      setError('Renseignez votre email et votre mot de passe.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const account = await login(email, password);
      navigate(safeNext(params.get('next')) ?? HOME_BY_ROLE[account.role], { replace: true });
    } catch (err) {
      const ui = toUiError(err);
      setError(
        ui.status === 401
          ? 'Email ou mot de passe incorrect.'
          : ui.status === 429
            ? 'Trop de tentatives sur ce compte. Il est bloqué 15 minutes.'
            : ui.message,
      );
      setSubmitting(false);
    }
  }

  return (
    <GuestOnly busy={submitting}>
      <AuthShell
        title="Connexion"
        intro="Retrouvez vos offres, vos candidatures et l’explication de chaque score."
        aside={
          <>
            Pas encore de compte ?{' '}
            <Link className="text-link" to="/register">
              Créer un compte
            </Link>
          </>
        }
      >
        <form className="form" onSubmit={onSubmit} noValidate>
          {params.get('registered') && !error && (
            <Alert tone="success" title="Compte créé">
              Connectez-vous avec votre email et votre mot de passe pour commencer.
            </Alert>
          )}
          {error && <Alert tone="error">{error}</Alert>}
          <TextField
            label="Email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoFocus
            required
          />
          <TextField
            label="Mot de passe"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <div className="auth__row">
            <Link className="text-link" to="/forgot-password">
              Mot de passe oublié ?
            </Link>
          </div>
          <Button type="submit" size="lg" block loading={submitting}>
            Se connecter
          </Button>
        </form>

        {isMocked('auth') && (
          <div className="demo-accounts">
            <p className="muted">Comptes de démonstration</p>
            <div className="demo-accounts__list">
              {Object.entries(DEMO_ACCOUNTS).map(([role, demoEmail]) => (
                <button
                  key={role}
                  type="button"
                  className="btn btn--ghost"
                  onClick={() => {
                    setEmail(demoEmail);
                    setPassword(DEMO_PASSWORD);
                  }}
                >
                  {role === 'candidate' ? 'Candidat·e' : role === 'recruiter' ? 'Recruteur·se' : 'Admin'}
                </button>
              ))}
            </div>
          </div>
        )}
      </AuthShell>
    </GuestOnly>
  );
}
