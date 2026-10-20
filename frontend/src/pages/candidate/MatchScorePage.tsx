import { type CSSProperties } from 'react';
import { Link, useParams } from 'react-router-dom';
import { applicationsApi } from '../../api/applications';
import { candidatesApi } from '../../api/candidates';
import { jobsApi } from '../../api/jobs';
import { useAuth } from '../../auth/context';
import { ApplyButton } from '../../components/domain/ApplyButton';
import { ButtonLink } from '../../components/ui/Button';
import { ScoreBreakdown, ScoreTotal } from '../../components/ui/ScoreBreakdown';
import { Alert, EmptyState, ErrorState, PageSkeleton, Skeleton } from '../../components/ui/States';
import { Tag } from '../../components/ui/Tag';
import { toUiError } from '../../lib/errors';
import { CONTRACT_LABEL, formatDate, LEVEL_LABEL } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { CvAnalysisStatus } from '../../types';

/** Pourquoi le score n'est pas disponible tant que le CV n'est pas analysé. */
const WAITING: Record<Exclude<CvAnalysisStatus, 'DONE'>, { title: string; text: string }> = {
  NONE: {
    title: 'Déposez votre CV pour voir votre score',
    text: 'Le score s’appuie sur les données extraites de votre CV. Déposez-le dans votre profil : l’analyse prend quelques secondes.',
  },
  PENDING: {
    title: 'Votre CV est en cours d’analyse',
    text: 'Revenez dans un instant : le score s’affichera dès que l’analyse sera terminée.',
  },
  FAILED: {
    title: 'L’analyse de votre CV a échoué',
    text: 'Sans analyse, impossible de calculer un score. Déposez une autre version de votre CV (PDF avec du texte sélectionnable).',
  },
};

export function MatchScorePage() {
  const { jobId = '' } = useParams();
  const { account } = useAuth();
  const candidateId = account?.id ?? '';

  const job = useAsync(() => jobsApi.get(jobId), [jobId]);
  // The profile tells whether a score can be computed: without an analysed CV the API answers 422.
  const profile = useAsync(
    () => candidatesApi.getMyProfile().catch((e) => (toUiError(e).status === 404 ? null : Promise.reject(e))),
    [],
  );
  const status: CvAnalysisStatus = profile.data?.cvAnalysisStatus ?? 'NONE';
  const scorable = status === 'DONE';
  const match = useAsync(
    () => (scorable ? applicationsApi.matchPreview(jobId) : Promise.resolve(null)),
    [jobId, scorable],
  );
  const gap = useAsync(
    () => (profile.data ? applicationsApi.skillGap(candidateId, jobId).catch(() => null) : Promise.resolve(null)),
    [jobId, candidateId, Boolean(profile.data)],
  );

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
  const open = offer.status === 'PUBLISHED';
  const missing = gap.data?.missingSkills ?? [];
  const waiting = WAITING[status === 'DONE' ? 'NONE' : status];

  return (
    <article className="page page--doc">
      <nav className="crumbs" aria-label="Fil d’Ariane">
        <Link to="/candidate/jobs">Offres</Link>
        <span aria-hidden="true">/</span>
        <Link to={`/candidate/jobs/${offer.id}`}>{offer.title}</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">Votre score</span>
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
        {profile.error ? (
          <ErrorState error={profile.error} onRetry={profile.reload} />
        ) : profile.loading ? (
          <Skeleton height={96} />
        ) : !profile.data ? (
          <EmptyState
            title="Créez votre profil pour voir votre score"
            action={<ButtonLink to="/candidate/profile">Créer mon profil</ButtonLink>}
          >
            Le score compare vos compétences, votre expérience et votre formation aux exigences de l’offre.
          </EmptyState>
        ) : !scorable ? (
          <EmptyState title={waiting.title} action={<ButtonLink to="/candidate/profile">Ouvrir mon profil</ButtonLink>}>
            {waiting.text}
          </EmptyState>
        ) : match.error ? (
          match.error.status === 422 ? (
            <EmptyState title="Votre CV doit être analysé" action={<ButtonLink to="/candidate/profile">Ouvrir mon profil</ButtonLink>}>
              L’analyse de votre CV n’est pas disponible pour le moment.
            </EmptyState>
          ) : (
            <ErrorState error={match.error} onRetry={match.reload} />
          )
        ) : match.loading || !match.data ? (
          <div className="stack">
            <Skeleton height={96} />
            <Skeleton height={64} />
            <Skeleton height={64} />
          </div>
        ) : (
          <>
            <ScoreTotal
              score={match.data.score}
              label="compatibilité avec votre profil"
              action={open ? <ApplyButton jobId={offer.id} /> : undefined}
            />
            <ScoreBreakdown match={match.data} />
          </>
        )}
      </section>

      {profile.data && gap.data && (
        <section className="skill-gap" aria-labelledby="gap-title">
          <h2 className="section-title" id="gap-title">
            Compétences à développer
          </h2>
          {missing.length > 0 ? (
            <>
              <p className="muted">Demandées par cette offre et absentes de votre profil :</p>
              <div className="tag-list">
                {missing.map((s) => (
                  <Tag key={s} tone="red">
                    {s}
                  </Tag>
                ))}
              </div>
            </>
          ) : (
            <p className="muted">Votre profil couvre toutes les compétences demandées.</p>
          )}
        </section>
      )}

      <section className="job-description" aria-labelledby="desc-title">
        <h2 className="section-title" id="desc-title">
          Le poste
        </h2>
        <p>{offer.description}</p>
        <h3 className="job-description__sub">Compétences demandées</h3>
        <div className="tag-list">
          {offer.requiredSkills.map((s) => (
            <Tag key={s} tone={gap.data ? (missing.includes(s) ? 'red' : 'green') : 'grey'}>
              {s}
            </Tag>
          ))}
        </div>
      </section>
    </article>
  );
}
