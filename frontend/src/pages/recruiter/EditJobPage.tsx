import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { jobsApi, type JobSummary } from '../../api/jobs';
import { JobForm } from '../../components/domain/JobForm';
import type { JobFormValues } from '../../lib/jobForm';
import type { Job } from '../../types';
import { Alert, ErrorState, PageSkeleton } from '../../components/ui/States';
import { useToast } from '../../components/ui/toast';
import { HttpError, toUiError } from '../../lib/errors';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';

const LOCKED = 'Offre clôturée ou retirée : non modifiable.';

const FIELDS: (keyof JobFormValues)[] = [
  'title',
  'description',
  'requiredSkills',
  'experienceLevel',
  'contractType',
  'location',
  'workMode',
  'salaryMin',
  'salaryMax',
  'applicationDeadline',
  'positions',
];

/** An offer saved before EXT-JOB-01 may lack the new fields: they read as their defaults. */
const toDraft = (job: Job): JobFormValues => ({
  title: job.title,
  description: job.description,
  requiredSkills: job.requiredSkills,
  experienceLevel: job.experienceLevel,
  contractType: job.contractType,
  location: job.location,
  workMode: job.workMode ?? 'ON_SITE',
  salaryMin: job.salaryMin ?? null,
  salaryMax: job.salaryMax ?? null,
  applicationDeadline: job.applicationDeadline ?? null,
  positions: job.positions ?? 1,
});

/** Merge patch (API_CONTRACTS.md §2, §7.1): only the fields that differ from the saved offer; null removes an optional one. */
const changes = (saved: JobFormValues, next: JobFormValues) =>
  Object.fromEntries(FIELDS.filter((k) => JSON.stringify(saved[k]) !== JSON.stringify(next[k])).map((k) => [k, next[k]]));

/** Modification d'une offre PUBLISHED ou DRAFT ; une offre CLOSED ou MODERATED n'est plus modifiable. */
export function EditJobPage() {
  useDocumentTitle('Modifier une offre');
  const { jobId = '' } = useParams();
  const loaded = useAsync(() => jobsApi.get(jobId), [jobId]);

  return (
    <div className="page page--doc">
      <nav className="crumbs" aria-label="Fil d’Ariane">
        <Link to="/recruiter/jobs">Mes offres</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">Modifier l’offre</span>
      </nav>
      {loaded.error ? (
        <ErrorState error={loaded.error} onRetry={loaded.reload} />
      ) : loaded.loading || !loaded.data ? (
        <PageSkeleton />
      ) : (
        <Editor job={loaded.data} />
      )}
    </div>
  );
}

function Editor({ job }: { job: JobSummary }) {
  const notify = useToast();
  const [saved, setSaved] = useState<JobFormValues>(toDraft(job));
  const [done, setDone] = useState(false);

  if (job.status === 'CLOSED' || job.status === 'MODERATED') {
    return <Alert tone="error">{LOCKED}</Alert>;
  }

  async function save(values: JobFormValues) {
    const patch = changes(saved, values);
    if (!Object.keys(patch).length) {
      notify('Aucune modification à enregistrer.');
      return;
    }
    try {
      const updated = await jobsApi.update(job.id, patch);
      setSaved(toDraft(updated));
      setDone(true);
      notify('Offre mise à jour.');
    } catch (err) {
      const ui = toUiError(err);
      // 403: another company's offer; 409: closed or moderated since the page was opened.
      if (ui.status === 403 || ui.status === 409) throw new HttpError(ui.status, LOCKED);
      throw err;
    }
  }

  return (
    <>
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Modifier l’offre</h1>
          <p className="page-head__text">{job.title}</p>
        </div>
      </header>
      {done && (
        <Alert tone="success" action={<Link className="text-link" to="/recruiter/jobs">Retour à Mes offres</Link>}>
          Les modifications sont enregistrées.
        </Alert>
      )}
      <JobForm initial={saved} submitLabel="Enregistrer" cancelTo="/recruiter/jobs" onSubmit={save} />
    </>
  );
}
