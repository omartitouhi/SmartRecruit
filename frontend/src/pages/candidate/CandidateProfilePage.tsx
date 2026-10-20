import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { candidatesApi, type CandidateProfileRequest } from '../../api/candidates';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { Alert, ErrorState, PageSkeleton } from '../../components/ui/States';
import { TagInput } from '../../components/ui/TagInput';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { formatDate } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { CandidateProfile } from '../../types';
import { CvPanel } from './profile/CvPanel';
import { DeleteAccountPanel } from './profile/DeleteAccountPanel';
import { EducationEditor, ExperienceEditor } from './profile/Editors';
import { EMPTY, toForm, validate } from './profile/form';

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

function ProfileEditor({ existing, onSaved }: {
  existing: CandidateProfile | null;
  onSaved: (profile: CandidateProfile) => void;
}) {
  const notify = useToast();
  const [params] = useSearchParams();
  const [form, setForm] = useState<CandidateProfileRequest>(() => (existing ? toForm(existing) : EMPTY));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const set = <K extends keyof CandidateProfileRequest>(key: K, value: CandidateProfileRequest[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length) {
      setFormError('Certains champs sont à corriger.');
      return;
    }
    setSaving(true);
    try {
      const saved = await candidatesApi.saveMyProfile({
        ...form,
        phone: form.phone?.trim() || null,
        location: form.location?.trim() || null,
      });
      onSaved(saved);
      notify(existing ? 'Profil enregistré.' : 'Profil créé. Vous pouvez maintenant déposer votre CV.');
    } catch (err) {
      const ui = toUiError(err);
      setErrors(ui.fieldErrors);
      setFormError(ui.message);
    } finally {
      setSaving(false);
    }
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

      <div className="split">
        <form className="stack stack--lg" onSubmit={onSubmit} noValidate>
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

          <ExperienceEditor items={form.experiences} onChange={(v) => set('experiences', v)} errors={errors} />
          <EducationEditor items={form.education} onChange={(v) => set('education', v)} errors={errors} />

          <div className="form-footer">
            {formError && <Alert tone="error">{formError}</Alert>}
            <Button type="submit" size="lg" loading={saving}>
              {existing ? 'Enregistrer le profil' : 'Créer mon profil'}
            </Button>
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
