import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import type { RegisterRequest } from '../../api/auth';
import { useAuth } from '../../auth/context';
import { GuestOnly } from '../../auth/RequireRole';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { Alert } from '../../components/ui/States';
import { toUiError } from '../../lib/errors';
import { useDocumentTitle } from '../../lib/useAsync';
import { AuthShell } from './AuthShell';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function RegisterPage() {
  useDocumentTitle('Créer un compte');
  const { register } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [role, setRole] = useState<RegisterRequest['role']>(params.get('role') === 'recruiter' ? 'RECRUITER' : 'CANDIDATE');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState({ email: false, password: false });
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Recomputed on every keystroke: an error shows once its field was visited, and clears as soon as it is fixed.
  const problems: Record<string, string> = {};
  if (!EMAIL.test(email.trim())) problems.email = 'Saisissez un email valide, par exemple prenom.nom@mail.tn.';
  if (password.length < 8) problems.password = 'Au moins 8 caractères.';
  else if (password.length > 72) problems.password = '72 caractères au maximum.';
  const errors = {
    email: touched.email ? (problems.email ?? serverErrors.email) : undefined,
    password: touched.password ? (problems.password ?? serverErrors.password) : undefined,
  };

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    setTouched({ email: true, password: true });
    if (Object.keys(problems).length) return;
    setSubmitting(true);
    try {
      await register({ email, password, role });
      navigate('/login?registered=1', { replace: true, state: { email: email.trim() } });
    } catch (err) {
      const ui = toUiError(err);
      setServerErrors(ui.fieldErrors);
      setFormError(ui.status === 409 ? 'Un compte existe déjà avec cet email. Connectez-vous plutôt.' : ui.message);
      setSubmitting(false);
    }
  }

  return (
    <GuestOnly busy={submitting}>
      <AuthShell
        title="Créer un compte"
        intro="Deux minutes pour commencer. Vous compléterez votre profil juste après."
        aside={
          <>
            Déjà inscrit·e ?{' '}
            <Link className="text-link" to="/login">
              Se connecter
            </Link>
          </>
        }
      >
        <form className="form" onSubmit={onSubmit} noValidate>
          {formError && <Alert tone="error">{formError}</Alert>}
          <fieldset className="fieldset">
            <legend className="field__label">Vous êtes</legend>
            <div className="choice-group">
              <label className="choice">
                <input type="radio" name="role" checked={role === 'CANDIDATE'} onChange={() => setRole('CANDIDATE')} />
                <span className="choice__title">Candidat·e</span>
                <span className="choice__text">Je cherche un poste et je veux comprendre mon score.</span>
              </label>
              <label className="choice">
                <input type="radio" name="role" checked={role === 'RECRUITER'} onChange={() => setRole('RECRUITER')} />
                <span className="choice__title">Recruteur·se</span>
                <span className="choice__text">Je publie des offres et je classe les candidatures.</span>
              </label>
            </div>
          </fieldset>
          <TextField
            label="Email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            onBlur={() => setTouched((t) => ({ ...t, email: true }))}
            error={errors.email}
            required
          />
          <TextField
            label="Mot de passe"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onBlur={() => setTouched((t) => ({ ...t, password: true }))}
            hint="Entre 8 et 72 caractères."
            error={errors.password}
            required
          />
          <Button type="submit" size="lg" block loading={submitting}>
            Créer mon compte
          </Button>
          <p className="field__hint">
            Vos données sont traitées selon le RGPD. Vous pouvez supprimer votre compte à tout moment depuis votre profil.
          </p>
        </form>
      </AuthShell>
    </GuestOnly>
  );
}
