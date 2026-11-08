import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { candidatesApi } from '../../../api/candidates';
import { useAuth } from '../../../auth/context';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/Field';
import { Alert } from '../../../components/ui/States';
import { useToast } from '../../../components/ui/toast';
import { toUiError } from '../../../lib/errors';

export function DeleteAccountPanel() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const notify = useToast();
  const [open, setOpen] = useState(false);
  const [typed, setTyped] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onDelete() {
    setDeleting(true);
    setError(null);
    try {
      await candidatesApi.deleteMyAccount();
      await logout();
      notify('Votre compte et vos données ont été supprimés.');
      navigate('/', { replace: true });
    } catch (err) {
      setError(toUiError(err).message);
      setDeleting(false);
    }
  }

  return (
    <section className="panel" aria-labelledby="delete-title">
      <h2 className="panel__title" id="delete-title">
        Supprimer mon compte
      </h2>
      <p className="panel__sub" style={{ marginBottom: 16 }}>
        Profil, CV, scores et analyses sont effacés immédiatement (RGPD). Les recruteurs verront « Compte supprimé ».
      </p>
      {open ? (
        <div className="form">
          <TextField
            label="Tapez SUPPRIMER pour confirmer"
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            autoComplete="off"
          />
          <div className="detail-actions">
            <Button variant="danger" disabled={typed !== 'SUPPRIMER'} loading={deleting} onClick={onDelete}>
              Supprimer définitivement
            </Button>
            <Button variant="ghost" onClick={() => (setOpen(false), setTyped(''))}>
              Annuler
            </Button>
          </div>
          {error && <Alert tone="error">{error}</Alert>}
        </div>
      ) : (
        <Button variant="ghost" onClick={() => setOpen(true)}>
          Supprimer mon compte
        </Button>
      )}
    </section>
  );
}
