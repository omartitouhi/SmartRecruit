import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { candidatesApi, CV_MAX_BYTES } from '../../../api/candidates';
import { Button } from '../../../components/ui/Button';
import { Alert } from '../../../components/ui/States';
import { useToast } from '../../../components/ui/toast';
import { toUiError } from '../../../lib/errors';
import { formatDateTime } from '../../../lib/format';
import type { CandidateProfile } from '../../../types';

export function CvPanel({ profile, onChange }: { profile: CandidateProfile | null; onChange: (p: CandidateProfile) => void }) {
  const notify = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const parsing = Boolean(profile?.cvUrl && !profile.cvParsedAt);

  // While the AI parses the CV, poll the profile until cvParsedAt is set.
  useEffect(() => {
    if (!parsing) return;
    const timer = setInterval(() => {
      candidatesApi.getMyProfile().then((p) => p.cvParsedAt && onChange(p), () => undefined);
    }, 3000);
    return () => clearInterval(timer);
  }, [parsing, onChange]);

  async function upload(file: File | undefined) {
    if (!file) return;
    setError(null);
    if (file.type !== 'application/pdf') return setError('Le CV doit être un fichier PDF.');
    if (file.size > CV_MAX_BYTES) return setError('Le CV ne doit pas dépasser 5 Mo.');
    setUploading(true);
    try {
      onChange(await candidatesApi.uploadCv(file));
      notify('CV déposé. Analyse en cours.');
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  }

  async function open() {
    if (!profile) return;
    setOpening(true);
    try {
      const blob = await candidatesApi.downloadCv(profile.id);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setOpening(false);
    }
  }

  return (
    <section className="panel" aria-labelledby="cv-title">
      <h2 className="panel__title" id="cv-title">
        CV
      </h2>
      <p className="panel__sub" style={{ marginBottom: 16 }}>
        PDF, 5 Mo maximum. Stocké chiffré, visible seulement par vous et les recruteurs.
      </p>

      {profile?.cvUrl && (
        <div className="cv-status">
          {parsing ? (
            <Alert tone="warning" title="Analyse en cours">
              L’IA lit votre CV pour en extraire compétences et expériences. Cela prend quelques secondes.
            </Alert>
          ) : (
            <Alert tone="success" title="CV analysé">
              Dernière analyse le {formatDateTime(profile.cvParsedAt)}.
            </Alert>
          )}
          <Button variant="ghost" loading={opening} onClick={open}>
            Voir mon CV
          </Button>
        </div>
      )}

      <label
        className="dropzone"
        data-dragging={dragging}
        data-disabled={!profile}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          if (profile) upload(e.dataTransfer.files[0]);
        }}
      >
        <input
          ref={inputRef}
          type="file"
          accept="application/pdf"
          className="visually-hidden"
          disabled={!profile || uploading}
          onChange={(e: ChangeEvent<HTMLInputElement>) => upload(e.target.files?.[0])}
        />
        <span className="dropzone__title">
          {uploading ? 'Envoi…' : profile?.cvUrl ? 'Remplacer le CV' : 'Déposer mon CV'}
        </span>
        <span className="muted">{profile ? 'Glissez le fichier ici ou cliquez pour choisir.' : 'Enregistrez d’abord votre profil.'}</span>
      </label>
      {error && (
        <div style={{ marginTop: 12 }}>
          <Alert tone="error">{error}</Alert>
        </div>
      )}
    </section>
  );
}
