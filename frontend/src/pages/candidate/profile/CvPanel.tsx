import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { candidatesApi, CV_MAX_BYTES } from '../../../api/candidates';
import { Button } from '../../../components/ui/Button';
import { Alert } from '../../../components/ui/States';
import { useToast } from '../../../components/ui/toast';
import { toUiError } from '../../../lib/errors';
import { formatDateTime, formatSize } from '../../../lib/format';
import type { CandidateProfile } from '../../../types';

const POLL_MS = 3000;
/** About two minutes of polling, then the panel stops waiting and offers a manual refresh. */
const MAX_POLLS = 40;

export function CvPanel({ profile, onChange }: { profile: CandidateProfile | null; onChange: (p: CandidateProfile) => void }) {
  const notify = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [picked, setPicked] = useState<{ file: File; url: string } | null>(null);
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [stalled, setStalled] = useState(false);
  const [round, setRound] = useState(0);
  const status = profile?.cvAnalysisStatus ?? 'NONE';

  // PENDING: the AI is reading the CV. Poll until the status settles, but not forever.
  useEffect(() => {
    if (status !== 'PENDING') return;
    let polls = 0;
    const timer = setInterval(() => {
      candidatesApi.getMyProfile().then((p) => p.cvAnalysisStatus !== 'PENDING' && onChange(p), () => undefined);
      if (++polls >= MAX_POLLS) {
        setStalled(true);
        clearInterval(timer);
      }
    }, POLL_MS);
    return () => clearInterval(timer);
  }, [status, round, onChange]);

  useEffect(() => {
    if (picked) return () => URL.revokeObjectURL(picked.url);
  }, [picked]);

  async function upload(file: File | undefined) {
    if (!file) return;
    setError(null);
    if (file.type !== 'application/pdf') return setError('Le CV doit être un fichier PDF.');
    if (file.size > CV_MAX_BYTES) return setError('Le CV ne doit pas dépasser 5 Mo.');
    setPicked({ file, url: URL.createObjectURL(file) });
    setProgress(0);
    setStalled(false);
    setUploading(true);
    try {
      onChange(await candidatesApi.uploadCv(file, setProgress));
      notify('CV déposé. Analyse en cours.');
    } catch (err) {
      setPicked(null);
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

      {status !== 'NONE' && (
        <div className="cv-status">
          {status === 'PENDING' && (
            <Alert tone="warning" title={stalled ? 'L’analyse prend plus de temps que prévu' : 'Analyse en cours'}>
              {stalled
                ? 'Votre CV est bien enregistré. Vous pouvez continuer et revenir plus tard, ou vérifier maintenant.'
                : 'L’IA lit votre CV pour en extraire compétences et expériences. Cela prend quelques secondes.'}
            </Alert>
          )}
          {status === 'DONE' && (
            <Alert tone="success" title="CV analysé">
              Dernière analyse le {formatDateTime(profile?.cvParsedAt)}. Les données extraites sont proposées en haut de page.
            </Alert>
          )}
          {status === 'FAILED' && (
            <Alert tone="error" title="L’analyse du CV a échoué">
              Votre fichier est enregistré, mais l’IA n’a pas pu le lire. Complétez votre profil à la main ou déposez
              une autre version (PDF avec du texte sélectionnable).
            </Alert>
          )}
          <div className="detail-actions">
            <Button variant="ghost" loading={opening} onClick={open}>
              Voir mon CV
            </Button>
            {stalled && (
              <Button
                variant="ghost"
                onClick={() => {
                  setStalled(false);
                  setRound((r) => r + 1);
                }}
              >
                Vérifier maintenant
              </Button>
            )}
          </div>
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
          if (profile && !uploading) upload(e.dataTransfer.files[0]);
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

      {picked && (
        <div className="upload">
          <p className="upload__name">
            <span className="cell-strong">{picked.file.name}</span>
            <span className="muted"> · {formatSize(picked.file.size)}</span>
          </p>
          {uploading ? (
            <progress className="upload__bar" max={100} value={progress} aria-label="Envoi du CV">
              {progress} %
            </progress>
          ) : (
            <p className="muted">Envoyé.</p>
          )}
          <iframe className="upload__preview" src={picked.url} title={`Aperçu de ${picked.file.name}`} />
        </div>
      )}

      {error && (
        <div style={{ marginTop: 12 }}>
          <Alert tone="error">{error}</Alert>
        </div>
      )}
    </section>
  );
}
