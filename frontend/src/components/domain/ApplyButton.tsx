import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { applicationsApi } from '../../api/applications';
import { toUiError } from '../../lib/errors';
import { formatInt } from '../../lib/format';
import { useAsync } from '../../lib/useAsync';
import { Button } from '../ui/Button';
import { TextAreaField } from '../ui/Field';
import { ApplicationStatusTag } from '../ui/Tag';
import { useToast } from '../ui/toast';

export const COVER_LETTER_MAX = 5000;

/**
 * « Postuler » : envoie la candidature en un clic, avec une lettre de motivation facultative (EXT-APP-01),
 * puis devient « Candidature envoyée » avec un lien de suivi.
 */
export function ApplyButton({ jobId }: { jobId: string }) {
  const notify = useToast();
  const navigate = useNavigate();
  const mine = useAsync(() => applicationsApi.mine(), [jobId]);
  const [applying, setApplying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [writing, setWriting] = useState(false);
  const [letter, setLetter] = useState('');
  const existing = mine.data?.find((a) => a.jobId === jobId && a.status !== 'WITHDRAWN');

  async function apply() {
    setApplying(true);
    setError(null);
    try {
      const text = letter.trim();
      const created = text ? await applicationsApi.apply(jobId, text) : await applicationsApi.apply(jobId);
      mine.setData((list) => [created, ...(list ?? [])]);
      setWriting(false);
      setLetter('');
      notify('Candidature envoyée.');
    } catch (err) {
      const ui = toUiError(err);
      if (ui.status === 422) {
        // Profile incomplete or no CV: send the candidate where it can be fixed, with the reason.
        navigate('/candidate/profile', {
          state: { notice: 'Complétez votre profil et déposez votre CV pour pouvoir postuler.' },
        });
        return;
      }
      setError(ui.fieldErrors.coverLetter ?? ui.message);
      if (ui.status === 409) mine.reload();
    } finally {
      setApplying(false);
    }
  }

  if (existing) {
    return (
      <span className="applied">
        <Button disabled>Candidature envoyée</Button>
        <ApplicationStatusTag status={existing.status} />
        <Link className="text-link" to={`/candidate/applications?id=${existing.id}`}>
          Suivre ma candidature
        </Link>
      </span>
    );
  }

  return (
    <span className="applied apply-box">
      {writing && (
        <span className="apply-box__letter">
          <TextAreaField
            label="Lettre de motivation (facultative)"
            rows={6}
            maxLength={COVER_LETTER_MAX}
            value={letter}
            onChange={(e) => setLetter(e.target.value)}
            hint={`Quelques lignes sur ce qui vous attire dans le poste. ${formatInt(letter.length)} / ${formatInt(COVER_LETTER_MAX)} caractères.`}
            autoFocus
          />
        </span>
      )}
      <span className="apply-box__actions">
        <Button onClick={apply} loading={applying} disabled={mine.loading}>
          {writing && letter.trim() ? 'Postuler avec ma lettre' : 'Postuler'}
        </Button>
        {writing ? (
          <Button
            variant="quiet"
            disabled={applying}
            onClick={() => {
              setWriting(false);
              setLetter('');
            }}
          >
            Retirer la lettre
          </Button>
        ) : (
          <Button variant="quiet" disabled={mine.loading} onClick={() => setWriting(true)}>
            Joindre une lettre de motivation
          </Button>
        )}
      </span>
      {error && (
        <span className="field__error" role="alert">
          {error}
        </span>
      )}
    </span>
  );
}
