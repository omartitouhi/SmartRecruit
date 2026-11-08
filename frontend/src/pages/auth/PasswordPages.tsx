import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { authApi } from '../../api/auth';
import { Button, ButtonLink } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { Alert } from '../../components/ui/States';
import { toUiError } from '../../lib/errors';
import { useDocumentTitle } from '../../lib/useAsync';
import { AuthShell } from './AuthShell';

export function ForgotPasswordPage() {
  useDocumentTitle('Mot de passe oublié');
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!email.trim()) {
      setError('Renseignez votre email.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await authApi.forgotPassword(email.trim());
      setSent(true);
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell
      title="Mot de passe oublié"
      intro="Nous vous envoyons un lien pour en choisir un nouveau."
      aside={
        <Link className="text-link" to="/login">
          Retour à la connexion
        </Link>
      }
    >
      {sent ? (
        // Same message whether or not the account exists (the backend never says).
        <Alert tone="success" title="Vérifiez votre boîte mail">
          Si un compte existe pour {email.trim()}, un lien de réinitialisation vient d’y être envoyé. Il est valable une heure.
        </Alert>
      ) : (
        <form className="form" onSubmit={onSubmit} noValidate>
          {error && <Alert tone="error">{error}</Alert>}
          <TextField label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <Button type="submit" size="lg" block loading={submitting}>
            Envoyer le lien
          </Button>
        </form>
      )}
    </AuthShell>
  );
}

export function ResetPasswordPage() {
  useDocumentTitle('Nouveau mot de passe');
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (password.length < 8 || password.length > 72) next.newPassword = 'Entre 8 et 72 caractères.';
    if (confirm !== password) next.confirm = 'Les deux mots de passe ne correspondent pas.';
    setErrors(next);
    if (Object.keys(next).length) return;
    setSubmitting(true);
    setFormError(null);
    try {
      await authApi.resetPassword(token, password);
      setDone(true);
    } catch (err) {
      const ui = toUiError(err);
      setErrors(ui.fieldErrors);
      setFormError(ui.status === 400 && !Object.keys(ui.fieldErrors).length ? 'Ce lien est invalide ou a expiré. Demandez-en un nouveau.' : ui.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (!token) {
    return (
      <AuthShell title="Lien incomplet">
        <div className="form">
          <Alert tone="warning">Ce lien de réinitialisation ne contient pas de jeton. Demandez-en un nouveau.</Alert>
          <ButtonLink to="/forgot-password" size="lg" block>
            Demander un nouveau lien
          </ButtonLink>
        </div>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Nouveau mot de passe">
      {done ? (
        <div className="form">
          <Alert tone="success" title="Mot de passe modifié">
            Toutes vos sessions ont été fermées. Connectez-vous avec votre nouveau mot de passe.
          </Alert>
          <ButtonLink to="/login" size="lg" block>
            Se connecter
          </ButtonLink>
        </div>
      ) : (
        <form className="form" onSubmit={onSubmit} noValidate>
          {formError && <Alert tone="error">{formError}</Alert>}
          <TextField
            label="Nouveau mot de passe"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            hint="Entre 8 et 72 caractères."
            error={errors.newPassword}
          />
          <TextField
            label="Confirmer le mot de passe"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            error={errors.confirm}
          />
          <Button type="submit" size="lg" block loading={submitting}>
            Enregistrer
          </Button>
        </form>
      )}
    </AuthShell>
  );
}
