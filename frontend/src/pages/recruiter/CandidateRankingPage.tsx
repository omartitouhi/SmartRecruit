import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { applicationsApi, type StatusChangeOptions } from '../../api/applications';
import { candidatesApi, fullName } from '../../api/candidates';
import { jobsApi } from '../../api/jobs';
import { InterviewFields } from '../../components/domain/InterviewFields';
import { InterviewSummary } from '../../components/domain/InterviewSummary';
import { KanbanBoard } from '../../components/domain/KanbanBoard';
import { NotesPanel, Stars } from '../../components/domain/NotesPanel';
import { StatusTimeline } from '../../components/domain/StatusTimeline';
import { Button, ButtonLink } from '../../components/ui/Button';
import { TextAreaField } from '../../components/ui/Field';
import { ScoreBreakdown, ScoreChip } from '../../components/ui/ScoreBreakdown';
import { Alert, EmptyState, ErrorState, PageSkeleton, SkeletonRows } from '../../components/ui/States';
import { ApplicationStatusTag, JobStatusTag, ScoreBandTag, Tag } from '../../components/ui/Tag';
import { useToast } from '../../components/ui/toast';
import { downloadCsv, slugify } from '../../lib/csv';
import { toUiError } from '../../lib/errors';
import {
  APPLICATION_STATUS,
  CONTRACT_LABEL,
  formatDateShort,
  formatScore,
  formatYearMonth,
  isoDay,
  LEVEL_LABEL,
  pluralize,
  STATUS_ACTION,
} from '../../lib/format';
import { draftErrors, draftToRequest, interviewDraft, STATUS_MESSAGE_MAX, type InterviewDraft } from '../../lib/interview';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import { APPLICATION_STATUS_TRANSITIONS, type Application, type ApplicationStatus } from '../../types';
import { buildRankingCsv, type RankingRow } from './rankingCsv';

type Row = RankingRow;

async function loadRows(jobId: string): Promise<Row[]> {
  const applications = await applicationsApi.forJob(jobId);
  const candidates = await Promise.all(applications.map((a) => candidatesApi.getCandidate(a.candidateId).catch(() => null)));
  return applications
    .map((application, i) => ({ application, candidate: candidates[i] }))
    .sort((a, b) => (b.application.matchScore?.score ?? -1) - (a.application.matchScore?.score ?? -1));
}

type Filter = 'ALL' | 'TODO' | 'INTERVIEW' | 'DONE';
type View = 'list' | 'board';

const FILTERS: { id: Filter; label: string; match: (s: ApplicationStatus) => boolean }[] = [
  { id: 'ALL', label: 'Toutes', match: (s) => s !== 'WITHDRAWN' },
  { id: 'TODO', label: 'À examiner', match: (s) => s === 'RECEIVED' || s === 'IN_REVIEW' },
  { id: 'INTERVIEW', label: 'Entretien', match: (s) => s === 'INTERVIEW' },
  { id: 'DONE', label: 'Décidées', match: (s) => s === 'ACCEPTED' || s === 'REJECTED' },
];

/** Note moyenne et nombre de notes de l'équipe (EXT-APP-03), vide tant que personne n'a rien écrit. */
function TeamRating({ application }: { application: Application }) {
  if (!application.notesCount) return null;
  return (
    <span className="team-rating">
      {application.averageRating !== null && <Stars value={application.averageRating} />}
      <span className="muted">{pluralize(application.notesCount, 'note')}</span>
    </span>
  );
}

export function CandidateRankingPage() {
  const { jobId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const job = useAsync(() => jobsApi.get(jobId), [jobId]);
  const rows = useAsync(() => loadRows(jobId), [jobId]);
  // A move asked from the Kanban board: the detail panel confirms it like any other status change.
  const [requested, setRequested] = useState<{ id: string; status: ApplicationStatus; nonce: number } | null>(null);
  useDocumentTitle(job.data ? `Candidatures · ${job.data.title}` : 'Candidatures');

  const filter = (params.get('filter') as Filter) || 'ALL';
  const view: View = params.get('view') === 'board' ? 'board' : 'list';
  const all = rows.data ?? [];
  const active = all.filter((r) => r.application.status !== 'WITHDRAWN');
  const visible = view === 'board' ? active : all.filter((r) => FILTERS.find((f) => f.id === filter)!.match(r.application.status));
  const selectedId = params.get('id') ?? visible[0]?.application.id;
  const selected = all.find((r) => r.application.id === selectedId);
  const rank = new Map(all.map((r, i) => [r.application.id, i + 1]));

  function update(next: Record<string, string>) {
    const p = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k)));
    setParams(p, { replace: true });
  }

  function replaceApplication(updated: Application) {
    rows.setData((list) => (list ?? []).map((r) => (r.application.id === updated.id ? { ...r, application: updated } : r)));
  }

  function exportCsv() {
    if (!job.data) return;
    downloadCsv(`candidatures-${slugify(job.data.title)}-${isoDay()}.csv`, buildRankingCsv(all));
  }

  if (job.error) {
    return (
      <div className="page">
        {job.error.status === 404 || job.error.status === 403 ? (
          <EmptyState title="Offre introuvable" action={<ButtonLink to="/recruiter/jobs">Retour à mes offres</ButtonLink>}>
            Cette offre n’existe pas ou n’appartient pas à votre entreprise.
          </EmptyState>
        ) : (
          <ErrorState error={job.error} onRetry={job.reload} />
        )}
      </div>
    );
  }
  if (job.loading || !job.data) return <PageSkeleton />;

  const detail = selected && (
    <CandidateDetail
      key={`${selected.application.id}:${requested?.id === selected.application.id ? requested.nonce : 0}`}
      row={selected}
      requestedStatus={requested?.id === selected.application.id ? requested.status : null}
      onUpdated={replaceApplication}
    />
  );

  return (
    <div className="page">
      <nav className="crumbs" aria-label="Fil d’Ariane">
        <Link to="/recruiter/jobs">Mes offres</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{job.data.title}</span>
      </nav>
      <header className="page-head">
        <div>
          <h1 className="page-head__title">{job.data.title}</h1>
          <p className="page-head__text">
            {job.data.location} · {CONTRACT_LABEL[job.data.contractType]} · {LEVEL_LABEL[job.data.experienceLevel]} ·{' '}
            <JobStatusTag status={job.data.status} />
          </p>
        </div>
        <div className="detail-actions">
          {rows.data && <p className="muted">{pluralize(active.length, 'candidature')}, triées par score</p>}
          <Button variant="ghost" disabled={!active.length} onClick={exportCsv}>
            Exporter en CSV
          </Button>
        </div>
      </header>

      <div className="ranking-bar">
        {view === 'list' ? (
          <div className="segmented" role="tablist" aria-label="Filtrer les candidatures">
            {FILTERS.map((f) => {
              const count = all.filter((r) => f.match(r.application.status)).length;
              return (
                <button
                  key={f.id}
                  type="button"
                  role="tab"
                  aria-selected={filter === f.id}
                  className="segmented__item"
                  onClick={() => update({ filter: f.id === 'ALL' ? '' : f.id, id: '' })}
                >
                  {f.label}
                  <span className="segmented__count">{count}</span>
                </button>
              );
            })}
          </div>
        ) : (
          <p className="muted ranking-bar__hint">
            Glissez une carte vers la colonne suivante, ou utilisez ses boutons. Seules les étapes autorisées sont possibles.
          </p>
        )}
        <div className="segmented" role="group" aria-label="Affichage">
          <button type="button" className="segmented__item" aria-pressed={view === 'list'} onClick={() => update({ view: '' })}>
            Liste
          </button>
          <button
            type="button"
            className="segmented__item"
            aria-pressed={view === 'board'}
            onClick={() => update({ view: 'board', filter: '' })}
          >
            Pipeline
          </button>
        </div>
      </div>

      {rows.error ? (
        <ErrorState error={rows.error} onRetry={rows.reload} />
      ) : rows.loading ? (
        <div className="panel">
          <SkeletonRows rows={5} />
        </div>
      ) : visible.length === 0 ? (
        <EmptyState title={all.length ? 'Rien dans cette catégorie' : 'Aucune candidature pour l’instant'}>
          {all.length
            ? 'Choisissez un autre filtre pour voir les autres candidatures.'
            : 'Les candidatures apparaîtront ici dès leur envoi, déjà classées par score.'}
        </EmptyState>
      ) : view === 'board' ? (
        <div className="stack">
          <KanbanBoard
            items={visible}
            selectedId={selectedId}
            onSelect={(id) => update({ id })}
            onMove={(id, status) => {
              setRequested({ id, status, nonce: Date.now() });
              update({ id });
            }}
            renderCard={({ application, candidate }) => (
              <>
                <span className="board__name">
                  <span className="muted num">#{rank.get(application.id)}</span>{' '}
                  <span className={candidate?.deleted ? 'muted' : 'cell-strong'}>{candidate ? fullName(candidate) : 'Profil indisponible'}</span>
                </span>
                <span className="board__meta">
                  <ScoreChip score={application.matchScore?.score} />
                  <TeamRating application={application} />
                </span>
              </>
            )}
          />
          {detail}
        </div>
      ) : (
        <div className="split">
          <div className="panel panel--flush">
            <div className="table-wrap">
              <table className="table table--interactive">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Candidat·e</th>
                    <th>Score</th>
                    <th>Statut</th>
                    <th>Équipe</th>
                    <th>Reçue le</th>
                  </tr>
                </thead>
                <tbody>
                  {visible.map(({ application, candidate }) => (
                    <tr
                      key={application.id}
                      aria-selected={application.id === selectedId}
                      tabIndex={0}
                      onClick={() => update({ id: application.id })}
                      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), update({ id: application.id }))}
                    >
                      <td className="muted num">{rank.get(application.id)}</td>
                      <td>
                        <span className={candidate?.deleted ? 'muted' : 'cell-strong'}>{candidate ? fullName(candidate) : 'Profil indisponible'}</span>
                      </td>
                      <td>
                        <ScoreChip score={application.matchScore?.score} /> <ScoreBandTag score={application.matchScore?.score} />
                      </td>
                      <td>
                        <ApplicationStatusTag status={application.status} />
                      </td>
                      <td>
                        <TeamRating application={application} />
                      </td>
                      <td className="muted">{formatDateShort(application.appliedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          {detail}
        </div>
      )}
    </div>
  );
}

function CandidateDetail({ row, requestedStatus, onUpdated }: {
  row: Row;
  requestedStatus: ApplicationStatus | null;
  onUpdated: (a: Application) => void;
}) {
  const notify = useToast();
  const { application, candidate } = row;
  const [busy, setBusy] = useState<ApplicationStatus | 'cv' | 'interview' | null>(null);
  // A status change is confirmed before it is sent: decisions cannot be undone.
  const [pending, setPending] = useState<ApplicationStatus | null>(requestedStatus);
  const [message, setMessage] = useState('');
  const [planNow, setPlanNow] = useState(true);
  const [draft, setDraft] = useState<InterviewDraft>(() => interviewDraft(application.interview));
  const [draftIssues, setDraftIssues] = useState<Record<string, string>>({});
  const [rescheduling, setRescheduling] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Recruiters move applications forward; only the candidate may withdraw.
  const transitions = APPLICATION_STATUS_TRANSITIONS[application.status].filter((s) => s !== 'WITHDRAWN');

  function choose(status: ApplicationStatus | null) {
    setPending(status);
    setMessage('');
    setDraftIssues({});
    setError(null);
  }

  async function move(status: ApplicationStatus) {
    const options: StatusChangeOptions = {};
    if (message.trim()) options.message = message.trim();
    if (status === 'INTERVIEW' && planNow) {
      const issues = draftErrors(draft);
      setDraftIssues(issues);
      if (Object.keys(issues).length) return;
      options.interview = draftToRequest(draft);
    }
    setBusy(status);
    setError(null);
    try {
      const updated = Object.keys(options).length
        ? await applicationsApi.updateStatus(application.id, status, options)
        : await applicationsApi.updateStatus(application.id, status);
      onUpdated({ ...application, ...updated, notesCount: updated.notesCount ?? application.notesCount, averageRating: updated.averageRating ?? application.averageRating });
      setPending(null);
      setMessage('');
      notify(`Candidature passée à « ${APPLICATION_STATUS[status].label} ».`);
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setBusy(null);
    }
  }

  async function schedule() {
    const issues = draftErrors(draft);
    setDraftIssues(issues);
    if (Object.keys(issues).length) return;
    setBusy('interview');
    setError(null);
    try {
      const updated = await applicationsApi.scheduleInterview(application.id, draftToRequest(draft));
      onUpdated({ ...application, ...updated, notesCount: updated.notesCount ?? application.notesCount, averageRating: updated.averageRating ?? application.averageRating });
      setRescheduling(false);
      notify('Entretien enregistré : le candidat le voit dans son espace.');
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setBusy(null);
    }
  }

  async function openCv() {
    setBusy('cv');
    setError(null);
    try {
      const url = URL.createObjectURL(await candidatesApi.downloadCv(application.candidateId));
      window.open(url, '_blank', 'noopener');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setBusy(null);
    }
  }

  const live = candidate && !candidate.deleted ? candidate : null;
  const decision = pending === 'ACCEPTED' || pending === 'REJECTED';

  return (
    <aside className="panel sticky detail" aria-label="Détail du candidat">
      <div className="panel__head">
        <div>
          <h2 className="panel__title">{candidate ? fullName(candidate) : 'Profil indisponible'}</h2>
          <p className="panel__sub">
            {application.matchScore ? `Score ${formatScore(application.matchScore.score)} / 100 · ` : ''}
            <ApplicationStatusTag status={application.status} />
          </p>
        </div>
      </div>

      {candidate?.deleted && (
        <Alert tone="neutral" title="Compte supprimé">
          Ce candidat a exercé son droit à l’oubli. Ses données personnelles ne sont plus disponibles.
        </Alert>
      )}

      {live && (
        <dl className="props detail__props">
          <dt>Email</dt>
          <dd>
            <a className="text-link" href={`mailto:${live.email}`}>
              {live.email}
            </a>
          </dd>
          {live.phone && (
            <>
              <dt>Téléphone</dt>
              <dd>{live.phone}</dd>
            </>
          )}
          {live.location && (
            <>
              <dt>Ville</dt>
              <dd>{live.location}</dd>
            </>
          )}
        </dl>
      )}

      {application.matchScore && <ScoreBreakdown match={application.matchScore} compact />}

      {application.coverLetter && (
        <details className="cover-letter">
          <summary>Lettre de motivation</summary>
          <p className="cover-letter__text">{application.coverLetter}</p>
        </details>
      )}

      {application.status === 'INTERVIEW' && (
        <>
          <h3 className="detail-sub">Entretien</h3>
          {rescheduling ? (
            <div className="stack">
              <InterviewFields value={draft} onChange={setDraft} errors={draftIssues} />
              <span className="detail-actions">
                <Button loading={busy === 'interview'} onClick={schedule}>
                  Enregistrer l’entretien
                </Button>
                <Button variant="ghost" disabled={busy !== null} onClick={() => setRescheduling(false)}>
                  Annuler
                </Button>
              </span>
            </div>
          ) : (
            <>
              {application.interview ? (
                <InterviewSummary interview={application.interview} />
              ) : (
                <p className="muted">Aucun entretien planifié pour l’instant.</p>
              )}
              <Button
                variant="ghost"
                disabled={busy !== null || pending !== null}
                onClick={() => {
                  setDraft(interviewDraft(application.interview));
                  setDraftIssues({});
                  setRescheduling(true);
                }}
              >
                {application.interview ? 'Replanifier' : 'Planifier l’entretien'}
              </Button>
            </>
          )}
        </>
      )}

      {live && live.experiences.length > 0 && (
        <>
          <h3 className="detail-sub">Parcours</h3>
          <ul className="career">
            {live.experiences.map((x, i) => (
              <li key={i}>
                <span className="cell-strong">{x.title}</span> · {x.company}
                <span className="muted">
                  {' '}
                  · {formatYearMonth(x.startDate)} – {formatYearMonth(x.endDate)}
                </span>
              </li>
            ))}
            {live.education.map((x, i) => (
              <li key={`e${i}`}>
                <span className="cell-strong">{x.degree}</span> · {x.institution}
                <span className="muted"> · {x.year}</span>
              </li>
            ))}
          </ul>
          <div className="tag-list" style={{ marginTop: 12 }}>
            {live.skills.map((s) => (
              <Tag key={s}>{s}</Tag>
            ))}
          </div>
        </>
      )}

      <h3 className="detail-sub">Historique</h3>
      <StatusTimeline history={application.statusHistory} />

      <div className="detail-actions">
        {pending ? (
          <div className="status-change">
            <p className="cell-strong">Passer la candidature à « {APPLICATION_STATUS[pending].label} » ?</p>
            {pending === 'INTERVIEW' && (
              <>
                <label className="check">
                  <input type="checkbox" checked={planNow} onChange={(e) => setPlanNow(e.target.checked)} />
                  Planifier l’entretien maintenant
                </label>
                {planNow && <InterviewFields value={draft} onChange={setDraft} errors={draftIssues} />}
              </>
            )}
            <TextAreaField
              label="Message au candidat (facultatif)"
              rows={3}
              maxLength={STATUS_MESSAGE_MAX}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              hint={
                decision
                  ? 'Une décision expliquée est mieux reçue : le candidat lit ce message dans son espace.'
                  : 'Le candidat le lit dans l’historique de sa candidature.'
              }
            />
            <span className="confirm-inline">
              <Button loading={busy === pending} onClick={() => move(pending)}>
                Confirmer
              </Button>
              <Button variant="ghost" disabled={busy !== null} onClick={() => choose(null)}>
                Annuler
              </Button>
            </span>
          </div>
        ) : (
          transitions.map((status, i) => (
            <Button
              key={status}
              variant={i === 0 && status !== 'REJECTED' ? 'primary' : 'ghost'}
              disabled={busy !== null || rescheduling}
              onClick={() => choose(status)}
            >
              {STATUS_ACTION[status]}
            </Button>
          ))
        )}
        {live?.cvUrl && !pending && (
          <Button variant="ghost" loading={busy === 'cv'} onClick={openCv}>
            Voir le CV
          </Button>
        )}
      </div>
      {transitions.length === 0 && (
        <p className="field__hint">Décision prise : cette candidature ne peut plus changer de statut.</p>
      )}
      {error && <Alert tone="error">{error}</Alert>}

      <NotesPanel
        applicationId={application.id}
        onStatsChange={(stats) => onUpdated({ ...application, ...stats })}
      />
    </aside>
  );
}
