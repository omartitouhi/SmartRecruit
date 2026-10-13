import { useState, type CSSProperties } from 'react';
import { Link, useParams } from 'react-router-dom';
import { applicationsApi } from '../../api/applications';
import { candidatesApi } from '../../api/candidates';
import { jobsApi } from '../../api/jobs';
import { useAuth } from '../../auth/context';
import { Button, ButtonLink } from '../../components/ui/Button';
import { ScoreBreakdown, ScoreTotal } from '../../components/ui/ScoreBreakdown';
import { Alert, EmptyState, ErrorState, PageSkeleton, Skeleton } from '../../components/ui/States';
import { ApplicationStatusTag, Tag } from '../../components/ui/Tag';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { CONTRACT_LABEL, formatDate, LEVEL_LABEL } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { Application } from '../../types';

export function MatchScorePage() {
  const { jobId = '' } = useParams();
  const { account } = useAuth();
  const notify = useToast();
  const candidateId = account?.id ?? '';

  const job = useAsync(() => jobsApi.get(jobId), [jobId]);
  // The profile is needed for a score: without one, the page invites the candidate to create it.
  const profile = useAsync(() => candidatesApi.getMyProfile().then((p) => p, (e) => (toUiError(e).status === 404 ? null : Promise.reject(e))), []);
  const hasProfile = Boolean(profile.data);
  const match = useAsync(
    () => (hasProfile ? applicationsApi.matchPreview(candidateId, jobId) : Promise.resolve(null)),
    [jobId, candidateId, hasProfile],
  );
  const gap = useAsync(
    () => (hasProfile ? applicationsApi.skillGap(candidateId, jobId) : Promise.resolve(null)),
    [jobId, candidateId, hasProfile],
  );
  const mine = useAsync(() => applicationsApi.mine(), []);
  const [applying, setApplying] = useState(false);
  const [applyError, setApplyError] = useState<string | null>(null);

  useDocumentTitle(job.data?.title ?? 'Offre');

  if (job.error) {
    return (
      <div className="page page--doc">
        {job.error.status === 404 ? (
          <EmptyState title="Offre introuvable" action={<ButtonLink to="/candidate/jobs">Retour aux offres</ButtonLink>}>
            Cette offre a peut-être été retirée par l’entreprise.
          </EmptyState>
        ) : (
          <ErrorState error={job.error} onRetry={job.reload} />
        )}
      </div>
    );
  }
  if (job.loading || !job.data) return <PageSkeleton />;

  const offer = job.data;
  const existing: Application | undefined = mine.data?.find((a) => a.jobId === jobId && a.status !== 'WITHDRAWN');
  const open = offer.status === 'PUBLISHED';

  async function onApply() {
    setApplying(true);
    setApplyError(null);
    try {
      const created = await applicationsApi.apply(jobId);
      mine.setData((list) => [created, ...(list ?? [])]);
      notify('Candidature envoyée. Votre explication de score y est jointe.');
    } catch (err) {
      setApplyError(toUiError(err).message);
    } finally {
      setApplying(false);
    }
  }

  const action = existing ? (
    <span className="applied">
      <ApplicationStatusTag status={existing.status} />
      <Link className="text-link" to={`/candidate/applications?id=${existing.id}`}>
        Suivre ma candidature
      </Link>
    </span>
  ) : open && hasProfile ? (
    <Button onClick={onApply} loading={applying}>
      Postuler
    </Button>
  ) : null;

  return (
    <article className="page page--doc">
      <nav className="crumbs" aria-label="Fil d’Ariane">
        <Link to="/candidate/jobs">Offres</Link>
        <span aria-hidden="true">/</span>
        <span>{offer.companyName ?? 'Entreprise'}</span>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{offer.title}</span>
      </nav>

      <h1 className="page-head__title reveal">{offer.title}</h1>

      <dl className="props reveal" style={{ margin: '28px 0 40px', '--i': 1 } as CSSProperties}>
        <dt>Entreprise</dt>
        <dd>{offer.companyName ?? '—'}</dd>
        <dt>Lieu</dt>
        <dd>{offer.location}</dd>
        <dt>Contrat</dt>
        <dd>
          <Tag tone="blue">{CONTRACT_LABEL[offer.contractType]}</Tag>
        </dd>
        <dt>Niveau</dt>
        <dd>{LEVEL_LABEL[offer.experienceLevel]}</dd>
        <dt>Publiée le</dt>
        <dd>{formatDate(offer.publishedAt)}</dd>
      </dl>

      {!open && (
        <Alert tone="warning" title="Cette offre n’accepte plus de candidatures">
          Vous pouvez encore consulter votre score pour vous situer.
        </Alert>
      )}

      <section aria-labelledby="score-title" className="score-section">
        <h2 id="score-title" className="visually-hidden">
          Votre compatibilité
        </h2>
        {profile.loading ? (
          <Skeleton height={96} />
        ) : !hasProfile ? (
          <EmptyState
            title="Créez votre profil pour voir votre score"
            action={<ButtonLink to="/candidate/profile">Créer mon profil</ButtonLink>}
          >
            Le score compare vos compétences, votre expérience, votre formation et vos langues aux exigences de l’offre.
          </EmptyState>
        ) : match.error ? (
          <ErrorState error={match.error} onRetry={match.reload} />
        ) : match.loading || !match.data ? (
          <div className="stack">
            <Skeleton height={96} />
            <Skeleton height={64} />
            <Skeleton height={64} />
          </div>
        ) : (
          <>
            <ScoreTotal score={match.data.score} label="compatibilité avec votre profil" action={action} />
            {applyError && (
              <div style={{ marginTop: 16 }}>
                <Alert tone="error">{applyError}</Alert>
              </div>
            )}
            <ScoreBreakdown match={match.data} />
            {gap.data && gap.data.missingSkills.length > 0 && (
              <p className="gap-line">
                <span>Compétences à acquérir pour cette offre :</span>
                <span className="tag-list">
                  {gap.data.missingSkills.map((s) => (
                    <Tag key={s} tone="red">
                      {s}
                    </Tag>
                  ))}
                </span>
              </p>
            )}
            {profile.data && !profile.data.cvParsedAt && (
              <p className="field__hint" style={{ marginTop: 12 }}>
                Votre CV n’a pas encore été analysé : le score repose sur les informations saisies dans votre profil.
              </p>
            )}
          </>
        )}
      </section>

      <section className="job-description" aria-labelledby="desc-title">
        <h2 className="section-title" id="desc-title">
          Le poste
        </h2>
        <p>{offer.description}</p>
        <h3 className="job-description__sub">Compétences demandées</h3>
        <div className="tag-list">
          {offer.requiredSkills.map((s) => {
            const missing = gap.data?.missingSkills.includes(s);
            return (
              <Tag key={s} tone={missing ? 'red' : gap.data ? 'green' : 'grey'}>
                {s}
              </Tag>
            );
          })}
        </div>
      </section>
    </article>
  );
}
