import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { candidatesApi, CV_MAX_BYTES, type CandidateProfileRequest } from '../../api/candidates';
import { useAuth } from '../../auth/context';
import { Button } from '../../components/ui/Button';
import { TextAreaField, TextField } from '../../components/ui/Field';
import { Alert, ErrorState, PageSkeleton } from '../../components/ui/States';
import { TagInput } from '../../components/ui/TagInput';
import { useToast } from '../../components/ui/toast';
import { toUiError } from '../../lib/errors';
import { formatDate, formatDateTime } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/useAsync';
import type { CandidateProfile, Education, Experience } from '../../types';

const EMPTY: CandidateProfileRequest = {
  firstName: '',
  lastName: '',
  email: '',
  phone: null,
  location: null,
  skills: [],
  experiences: [],
  education: [],
  languages: [],
};

const toForm = (p: CandidateProfile): CandidateProfileRequest => ({
  firstName: p.firstName,
  lastName: p.lastName,
  email: p.email,
  phone: p.phone,
  location: p.location,
  skills: p.skills,
  experiences: p.experiences,
  education: p.education,
  languages: p.languages,
});

function validate(form: CandidateProfileRequest) {
  const e: Record<string, string> = {};
  if (!form.firstName.trim()) e.firstName = 'Le prénom est obligatoire.';
  if (!form.lastName.trim()) e.lastName = 'Le nom est obligatoire.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) e.email = 'Saisissez un email valide.';
  if (form.phone && !/^\+?[0-9 .()-]{6,20}$/.test(form.phone)) e.phone = 'Format attendu : +216 22 418 730.';
  form.experiences.forEach((x, i) => {
    if (!x.title.trim()) e[`experiences[${i}].title`] = 'L’intitulé est obligatoire.';
    if (!x.company.trim()) e[`experiences[${i}].company`] = 'L’entreprise est obligatoire.';
    if (!x.startDate) e[`experiences[${i}].startDate`] = 'La date de début est obligatoire.';
    if (x.startDate && x.endDate && x.endDate < x.startDate) e[`experiences[${i}].endDate`] = 'La fin précède le début.';
  });
  form.education.forEach((x, i) => {
    if (!x.degree.trim()) e[`education[${i}].degree`] = 'Le diplôme est obligatoire.';
    if (!x.institution.trim()) e[`education[${i}].institution`] = 'L’établissement est obligatoire.';
    if (!x.year || x.year < 1950 || x.year > 2100) e[`education[${i}].year`] = 'Année entre 1950 et 2100.';
  });
  return e;
}

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

function ExperienceEditor({ items, onChange, errors }: {
  items: Experience[];
  onChange: (items: Experience[]) => void;
  errors: Record<string, string>;
}) {
  const update = (i: number, patch: Partial<Experience>) => onChange(items.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  return (
    <section className="panel form" aria-labelledby="exp-title">
      <div className="panel__head" style={{ marginBottom: 0 }}>
        <h2 className="panel__title" id="exp-title">
          Expériences
        </h2>
        <Button
          variant="ghost"
          onClick={() => onChange([...items, { title: '', company: '', startDate: '', endDate: null, description: '' }])}
        >
          Ajouter une expérience
        </Button>
      </div>
      {items.length === 0 && <p className="muted">Aucune expérience. Un stage ou une alternance compte aussi.</p>}
      {items.map((x, i) => (
        <fieldset className="repeat" key={i}>
          <legend className="visually-hidden">Expérience {i + 1}</legend>
          <div className="form-row">
            <TextField label="Intitulé du poste" value={x.title} onChange={(e) => update(i, { title: e.target.value })} error={errors[`experiences[${i}].title`]} />
            <TextField label="Entreprise" value={x.company} onChange={(e) => update(i, { company: e.target.value })} error={errors[`experiences[${i}].company`]} />
          </div>
          <div className="form-row">
            <TextField label="Début" type="month" value={x.startDate} onChange={(e) => update(i, { startDate: e.target.value })} error={errors[`experiences[${i}].startDate`]} />
            <TextField
              label="Fin"
              type={x.endDate === null ? 'text' : 'month'}
              value={x.endDate ?? 'Aujourd’hui'}
              disabled={x.endDate === null}
              onChange={(e) => update(i, { endDate: e.target.value || null })}
              hint={
                <label className="check">
                  <input type="checkbox" checked={x.endDate === null} onChange={(e) => update(i, { endDate: e.target.checked ? null : x.startDate || '' })} />
                  Poste actuel
                </label>
              }
              error={errors[`experiences[${i}].endDate`]}
            />
          </div>
          <TextAreaField label="Description" value={x.description} onChange={(e) => update(i, { description: e.target.value })} maxLength={2000} rows={3} />
          <div>
            <Button variant="quiet" onClick={() => onChange(items.filter((_, j) => j !== i))}>
              Retirer cette expérience
            </Button>
          </div>
        </fieldset>
      ))}
    </section>
  );
}

function EducationEditor({ items, onChange, errors }: {
  items: Education[];
  onChange: (items: Education[]) => void;
  errors: Record<string, string>;
}) {
  const update = (i: number, patch: Partial<Education>) => onChange(items.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  return (
    <section className="panel form" aria-labelledby="edu-title">
      <div className="panel__head" style={{ marginBottom: 0 }}>
        <h2 className="panel__title" id="edu-title">
          Formation
        </h2>
        <Button variant="ghost" onClick={() => onChange([...items, { degree: '', institution: '', year: new Date().getFullYear() }])}>
          Ajouter une formation
        </Button>
      </div>
      {items.length === 0 && <p className="muted">Aucune formation renseignée.</p>}
      {items.map((x, i) => (
        <fieldset className="repeat" key={i}>
          <legend className="visually-hidden">Formation {i + 1}</legend>
          <TextField label="Diplôme" placeholder="Master Génie Logiciel" value={x.degree} onChange={(e) => update(i, { degree: e.target.value })} error={errors[`education[${i}].degree`]} />
          <div className="form-row">
            <TextField label="Établissement" placeholder="ESPRIT" value={x.institution} onChange={(e) => update(i, { institution: e.target.value })} error={errors[`education[${i}].institution`]} />
            <TextField label="Année" type="number" min={1950} max={2100} value={x.year || ''} onChange={(e) => update(i, { year: Number(e.target.value) })} error={errors[`education[${i}].year`]} />
          </div>
          <div>
            <Button variant="quiet" onClick={() => onChange(items.filter((_, j) => j !== i))}>
              Retirer cette formation
            </Button>
          </div>
        </fieldset>
      ))}
    </section>
  );
}

function CvPanel({ profile, onChange }: { profile: CandidateProfile | null; onChange: (p: CandidateProfile) => void }) {
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

function DeleteAccountPanel() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const notify = useToast();
  const [open, setOpen] = useState(false);
  const [typed, setTyped] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onDelete() {
    setDeleting(true);
    setError(null);
    try {
      await candidatesApi.deleteMyAccount();
      await logout();
      notify('Votre compte et vos données ont été supprimés.');
      navigate('/', { replace: true });
    } catch (err) {
      setError(toUiError(err).message);
      setDeleting(false);
    }
  }

  return (
    <section className="panel" aria-labelledby="delete-title">
      <h2 className="panel__title" id="delete-title">
        Supprimer mon compte
      </h2>
      <p className="panel__sub" style={{ marginBottom: 16 }}>
        Profil, CV, scores et analyses sont effacés immédiatement (RGPD). Les recruteurs verront « Compte supprimé ».
      </p>
      {open ? (
        <div className="form">
          <TextField
            label="Tapez SUPPRIMER pour confirmer"
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            autoComplete="off"
          />
          <div className="detail-actions">
            <Button variant="danger" disabled={typed !== 'SUPPRIMER'} loading={deleting} onClick={onDelete}>
              Supprimer définitivement
            </Button>
            <Button variant="ghost" onClick={() => (setOpen(false), setTyped(''))}>
              Annuler
            </Button>
          </div>
          {error && <Alert tone="error">{error}</Alert>}
        </div>
      ) : (
        <Button variant="ghost" onClick={() => setOpen(true)}>
          Supprimer mon compte
        </Button>
      )}
    </section>
  );
}
