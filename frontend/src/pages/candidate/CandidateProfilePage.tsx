import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { candidatesApi, type CandidateProfileRequest } from '../../api/candidates';
import { useAuth } from '../../auth/context';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { Alert, ErrorState, PageSkeleton } from '../../components/ui/States';
import { TagInput } from '../../components/ui/TagInput';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { formatDate } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import { storage } from '../../lib/storage';
import type { CandidateProfile, CVParsingResult } from '../../types';
import { CvPanel } from './profile/CvPanel';
import { DeleteAccountPanel } from './profile/DeleteAccountPanel';
import { CvSuggestions } from './profile/CvSuggestions';
import { EducationEditor, ExperienceEditor } from './profile/Editors';
import { applySuggestions, EMPTY, newSuggestions, toForm, validate } from './profile/form';

export function CandidateProfilePage() {
  useDocumentTitle('Mon profil');
  const profile = useAsync(
    () => candidatesApi.getMyProfile().catch((e) => (toUiError(e).status === 404 ? null : Promise.reject(e))),
    [],
  );

  if (profile.error) {
    return (
      <div className="page page--doc">
        <ErrorState error={profile.error} onRetry={profile.reload} />
      </div>
    );
  }
  if (profile.loading) return <PageSkeleton />;
  // Mounted once the profile is known, so the form starts from it without syncing state in an effect.
  return <ProfileEditor existing={profile.data ?? null} onSaved={profile.setData} />;
}

const STEPS = ['Informations', 'Expérience', 'Compétences'];

/** Étape qui contient le champ en erreur (clés de validate() : « experiences[0].title », « email »…). */
const stepOf = (key: string) => (/^(experiences|education)/.test(key) ? 1 : /^(skills|languages)/.test(key) ? 2 : 0);

function readDraft(key: string): { form: CandidateProfileRequest; step: number } | null {
  try {
    return JSON.parse(storage.get(key) ?? 'null');
  } catch {
    return null;
  }
}

function ProfileEditor({ existing, onSaved }: {
  existing: CandidateProfile | null;
  onSaved: (profile: CandidateProfile) => void;
}) {
  const notify = useToast();
  const [params] = useSearchParams();
  const { account } = useAuth();
  const baseline = existing ? toForm(existing) : EMPTY;
  const baselineJson = JSON.stringify(baseline);
  // A draft survives reloads and step changes until the profile is saved.
  const draftKey = `smartrecruit_profile_draft:${account?.id}`;
  const [restored] = useState(() => readDraft(draftKey));
  const [discarded, setDiscarded] = useState(false);
  const [form, setForm] = useState<CandidateProfileRequest>(() => restored?.form ?? baseline);
  const [step, setStep] = useState(restored?.step ?? 0);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (JSON.stringify(form) === baselineJson) storage.remove(draftKey);
    else storage.set(draftKey, JSON.stringify({ form, step }));
  }, [form, step, baselineJson, draftKey]);

  const set = <K extends keyof CandidateProfileRequest>(key: K, value: CandidateProfileRequest[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  async function persist(next: CandidateProfileRequest, message: string) {
    setFormError(null);
    const found = validate(next);
    setErrors(found);
    const first = Object.keys(found)[0];
    if (first) {
      setStep(stepOf(first));
      setFormError('Certains champs sont à corriger.');
      return;
    }
    setSaving(true);
    try {
      const saved = await candidatesApi.saveMyProfile({
        ...next,
        phone: next.phone?.trim() || null,
        location: next.location?.trim() || null,
      });
      storage.remove(draftKey);
      onSaved(saved);
      notify(message);
    } catch (err) {
      const ui = toUiError(err);
      setErrors(ui.fieldErrors);
      setFormError(ui.message);
    } finally {
      setSaving(false);
    }
  }

  function next() {
    const here = Object.entries(validate(form)).filter(([key]) => stepOf(key) === step);
    setErrors(Object.fromEntries(here));
    if (here.length === 0) setStep(step + 1);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    return persist(form, existing ? 'Profil enregistré.' : 'Profil créé. Vous pouvez maintenant déposer votre CV.');
  }

  // The AI's reading of the CV, offered for validation once the analysis is DONE.
  const analysed = existing?.cvAnalysisStatus === 'DONE';
  const suggestions = useAsync(
    () =>
      analysed
        ? candidatesApi.cvSuggestions().catch((e) => (toUiError(e).status === 404 ? null : Promise.reject(e)))
        : Promise.resolve(null),
    [analysed, existing?.cvParsedAt],
  );
  const dismissKey = `smartrecruit_cv_suggestions_dismissed:${existing?.cvParsedAt}`;
  const [dismissedNow, setDismissedNow] = useState<string | null>(null);
  const dismissed = dismissedNow === dismissKey || storage.get(dismissKey) === '1';
  const found = suggestions.data ? newSuggestions(form, suggestions.data) : null;
  const hasNew = found !== null && Object.values(found).some((items) => items.length > 0);

  async function applyPicked(picked: CVParsingResult) {
    const merged = applySuggestions(form, picked);
    setForm(merged);
    await persist(merged, 'Données du CV ajoutées à votre profil.');
  }

  function dismissSuggestions() {
    storage.set(dismissKey, '1');
    setDismissedNow(dismissKey);
  }

  const fieldError = (key: string) => errors[key];

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Mon profil</h1>
          <p className="page-head__text">
            Ce que vous renseignez ici sert à calculer vos scores. Les recruteurs voient votre profil quand vous postulez.
          </p>
        </div>
        {existing && <p className="muted">Mis à jour le {formatDate(existing.updatedAt)}</p>}
      </header>

      {!existing && (
        <div style={{ marginBottom: 'var(--space-6)' }}>
          <Alert tone="info" title={params.get('welcome') ? 'Bienvenue sur SmartRecruit' : 'Profil à créer'}>
            Commencez par votre identité et vos compétences, puis déposez votre CV : l’analyse complétera votre profil.
          </Alert>
        </div>
      )}

      {suggestions.error ? (
        <div style={{ marginBottom: 'var(--space-6)' }}>
          <ErrorState error={suggestions.error} onRetry={suggestions.reload} />
        </div>
      ) : (
        found &&
        hasNew &&
        !dismissed && (
          <div style={{ marginBottom: 'var(--space-6)' }}>
            <CvSuggestions found={found} busy={saving} onApply={applyPicked} onDismiss={dismissSuggestions} />
          </div>
        )
      )}

      <div className="split">
        <form className="stack stack--lg" onSubmit={onSubmit} noValidate>
          <nav className="steps" aria-label="Étapes du profil">
            <ol className="steps__list">
              {STEPS.map((label, i) => (
                <li key={label}>
                  <button
                    type="button"
                    className="steps__item"
                    aria-current={i === step ? 'step' : undefined}
                    onClick={() => setStep(i)}
                  >
                    <span className="steps__num" aria-hidden="true">
                      {i + 1}
                    </span>
                    {label}
                  </button>
                </li>
              ))}
            </ol>
          </nav>

          {restored && !discarded && JSON.stringify(restored.form) !== baselineJson && (
            <Alert
              tone="info"
              title="Brouillon restauré"
              action={
                <Button
                  variant="ghost"
                  onClick={() => {
                    setForm(baseline);
                    setStep(0);
                    setDiscarded(true);
                  }}
                >
                  Repartir du profil enregistré
                </Button>
              }
            >
              Vos modifications non enregistrées ont été récupérées sur cet appareil.
            </Alert>
          )}

          {step === 0 && (
          <section className="panel form" aria-labelledby="id-title">
            <h2 className="panel__title" id="id-title">
              Identité
            </h2>
            <div className="form-row">
              <TextField label="Prénom" autoComplete="given-name" value={form.firstName} onChange={(e) => set('firstName', e.target.value)} error={fieldError('firstName')} required />
              <TextField label="Nom" autoComplete="family-name" value={form.lastName} onChange={(e) => set('lastName', e.target.value)} error={fieldError('lastName')} required />
            </div>
            <TextField
              label="Email de contact"
              type="email"
              autoComplete="email"
              value={form.email}
              onChange={(e) => set('email', e.target.value)}
              hint="Visible par les recruteurs. Il peut être différent de l’email de votre compte."
              error={fieldError('email')}
              required
            />
            <div className="form-row">
              <TextField label="Téléphone" type="tel" autoComplete="tel" placeholder="+216 22 418 730" value={form.phone ?? ''} onChange={(e) => set('phone', e.target.value)} error={fieldError('phone')} />
              <TextField label="Ville" autoComplete="address-level2" placeholder="Tunis" value={form.location ?? ''} onChange={(e) => set('location', e.target.value)} error={fieldError('location')} />
            </div>
          </section>
          )}

          {step === 2 && (
          <section className="panel form" aria-labelledby="skills-title">
            <h2 className="panel__title" id="skills-title">
              Compétences et langues
            </h2>
            <TagInput
              label="Compétences"
              values={form.skills}
              onChange={(v) => set('skills', v)}
              placeholder="Java, Spring Boot, SQL…"
              hint="Entrée ou virgule pour ajouter. Comptent pour 50 % du score."
              error={fieldError('skills')}
            />
            <TagInput
              label="Langues"
              values={form.languages}
              onChange={(v) => set('languages', v)}
              placeholder="Français, Anglais…"
              max={30}
              error={fieldError('languages')}
            />
          </section>
          )}

          {step === 1 && (
            <>
              <ExperienceEditor items={form.experiences} onChange={(v) => set('experiences', v)} errors={errors} />
              <EducationEditor items={form.education} onChange={(v) => set('education', v)} errors={errors} />
            </>
          )}

          <div className="form-footer">
            {formError && <Alert tone="error">{formError}</Alert>}
            <div className="detail-actions">
              {step > 0 && (
                <Button variant="ghost" onClick={() => setStep(step - 1)}>
                  Précédent
                </Button>
              )}
              {step < STEPS.length - 1 && (
                <Button variant="ghost" onClick={next}>
                  Suivant
                </Button>
              )}
              <Button type="submit" size="lg" loading={saving}>
                {existing ? 'Enregistrer le profil' : 'Créer mon profil'}
              </Button>
            </div>
          </div>
        </form>

        <aside className="stack sticky">
          <CvPanel profile={existing} onChange={onSaved} />
          {existing && <DeleteAccountPanel />}
        </aside>
      </div>
    </div>
  );
}
