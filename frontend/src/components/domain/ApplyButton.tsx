import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { applicationsApi } from '../../api/applications';
import { toUiError } from '../../lib/errors';
import { useAsync } from '../../lib/useAsync';
import { Button } from '../ui/Button';
import { ApplicationStatusTag } from '../ui/Tag';
import { useToast } from '../ui/toast';

/** « Postuler » : envoie la candidature, puis devient « Candidature envoyée » avec un lien de suivi. */
export function ApplyButton({ jobId }: { jobId: string }) {
  const notify = useToast();
  const navigate = useNavigate();
  const mine = useAsync(() => applicationsApi.mine(), [jobId]);
  const [applying, setApplying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const existing = mine.data?.find((a) => a.jobId === jobId && a.status !== 'WITHDRAWN');

  async function apply() {
    setApplying(true);
    setError(null);
    try {
      const created = await applicationsApi.apply(jobId);
      mine.setData((list) => [created, ...(list ?? [])]);
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
      setError(ui.message);
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
    <span className="applied">
      <Button onClick={apply} loading={applying} disabled={mine.loading}>
        Postuler
      </Button>
      {error && (
        <span className="field__error" role="alert">
          {error}
        </span>
      )}
    </span>
  );
}
