import { useState, type FormEvent } from 'react';
import { applicationsApi } from '../../api/applications';
import { useAuth } from '../../auth/context';
import { toUiError } from '../../lib/errors';
import { formatDateTime, formatInt, formatScore, pluralize } from '../../lib/format';
import { NOTE_MAX, noteStats } from '../../lib/notes';
import { useAsync } from '../../lib/useAsync';
import type { ApplicationNote } from '../../types';
import { Button } from '../ui/Button';
import { SelectField, TextAreaField } from '../ui/Field';
import { Alert, SkeletonRows } from '../ui/States';

/** « ★★★★☆ » avec un libellé lisible par les lecteurs d'écran. */
export function Stars({ value }: { value: number }) {
  const rounded = Math.round(value);
  return (
    <span className="stars" role="img" aria-label={`Note : ${formatScore(value).replace(/,0$/, '')} sur 5`}>
      {'★'.repeat(rounded)}
      <span className="stars__off">{'★'.repeat(5 - rounded)}</span>
    </span>
  );
}

/**
 * Notes internes de l'équipe sur une candidature (EXT-APP-03) : jamais visibles du candidat. Chaque recruteur de
 * l'entreprise peut en ajouter, avec une note de 1 à 5 facultative, et supprimer les siennes.
 */
export function NotesPanel({ applicationId, readOnly, onStatsChange }: {
  applicationId: string;
  /** Admin : lecture seule. */
  readOnly?: boolean;
  onStatsChange?: (stats: ReturnType<typeof noteStats>) => void;
}) {
  const { account } = useAuth();
  const notes = useAsync(() => applicationsApi.notes(applicationId), [applicationId]);
  const [content, setContent] = useState('');
  const [rating, setRating] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [editing, setEditing] = useState<ApplicationNote | null>(null);

  function replace(next: ApplicationNote[]) {
    notes.setData(next);
    onStatsChange?.(noteStats(next));
  }

  /** Le formulaire sert à ajouter une note ou, après « Modifier », à corriger l'une des siennes. */
  function startEdit(note: ApplicationNote) {
    setEditing(note);
    setContent(note.content);
    setRating(note.rating ? String(note.rating) : '');
    setFieldError(undefined);
  }

  function resetForm() {
    setEditing(null);
    setContent('');
    setRating('');
  }

  async function add(e: FormEvent) {
    e.preventDefault();
    if (!content.trim()) {
      setFieldError('Écrivez la note avant de l’ajouter.');
      return;
    }
    setBusy('add');
    setError(null);
    setFieldError(undefined);
    try {
      const request = { content: content.trim(), rating: rating ? Number(rating) : null };
      if (editing) {
        const updated = await applicationsApi.updateNote(applicationId, editing.id, request);
        replace((notes.data ?? []).map((n) => (n.id === updated.id ? updated : n)));
      } else {
        const created = await applicationsApi.addNote(applicationId, request);
        replace([...(notes.data ?? []), created]);
      }
      resetForm();
    } catch (err) {
      const ui = toUiError(err);
      setFieldError(ui.fieldErrors.content);
      setError(ui.fieldErrors.content ? null : ui.message);
    } finally {
      setBusy(null);
    }
  }

  async function remove(note: ApplicationNote) {
    setBusy(note.id);
    setError(null);
    try {
      await applicationsApi.deleteNote(applicationId, note.id);
      replace((notes.data ?? []).filter((n) => n.id !== note.id));
      if (editing?.id === note.id) resetForm();
    } catch (err) {
      setError(toUiError(err).message);
    } finally {
      setBusy(null);
    }
  }

  const list = notes.data ?? [];
  const { averageRating } = noteStats(list);

  return (
    <section className="notes" aria-labelledby={`notes-${applicationId}`}>
      <h3 className="detail-sub" id={`notes-${applicationId}`}>
        Notes de l’équipe
        {list.length > 0 && (
          <span className="muted notes__summary">
            {' '}
            · {pluralize(list.length, 'note')}
            {averageRating !== null && (
              <>
                {' '}
                · <Stars value={averageRating} /> {formatScore(averageRating)}
              </>
            )}
          </span>
        )}
      </h3>
      <p className="field__hint">Visibles seulement par les recruteurs de votre entreprise, jamais par le candidat.</p>

      {notes.error ? (
        <Alert tone="error" action={<Button variant="ghost" onClick={notes.reload}>Réessayer</Button>}>
          {notes.error.message}
        </Alert>
      ) : notes.loading && !notes.data ? (
        <SkeletonRows rows={2} />
      ) : list.length === 0 ? (
        <p className="muted">Aucune note pour l’instant.</p>
      ) : (
        <ul className="notes__list">
          {list.map((note) => {
            const mine = note.authorId === account?.id;
            return (
              <li key={note.id} className="notes__item">
                <p className="notes__meta">
                  <span className="cell-strong">{mine ? 'Vous' : 'Un·e collègue'}</span>
                  <span className="muted"> · {formatDateTime(note.createdAt)}</span>
                  {note.rating && (
                    <>
                      {' '}
                      · <Stars value={note.rating} />
                    </>
                  )}
                </p>
                <p className="notes__content">{note.content}</p>
                {mine && !readOnly && (
                  <>
                    <Button variant="quiet" disabled={busy !== null} onClick={() => startEdit(note)}>
                      Modifier ma note
                    </Button>
                    <Button variant="quiet" loading={busy === note.id} disabled={busy !== null} onClick={() => remove(note)}>
                      Supprimer ma note
                    </Button>
                  </>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {!readOnly && (
        <form className="notes__form" onSubmit={add} noValidate>
          <TextAreaField
            label={editing ? 'Modifier ma note' : 'Ajouter une note'}
            rows={3}
            maxLength={NOTE_MAX}
            value={content}
            onChange={(e) => {
              setContent(e.target.value);
              setFieldError(undefined);
            }}
            placeholder="Impressions d’entretien, points à vérifier…"
            hint={content ? `${formatInt(content.length)} / ${formatInt(NOTE_MAX)} caractères` : undefined}
            error={fieldError}
          />
          <div className="notes__form-row">
            <SelectField
              label="Évaluation"
              value={rating}
              onChange={(e) => setRating(e.target.value)}
              placeholder="Sans évaluation"
              options={[5, 4, 3, 2, 1].map((n) => ({ value: String(n), label: `${'★'.repeat(n)} (${n}/5)` }))}
            />
            <Button type="submit" variant="ghost" loading={busy === 'add'} disabled={busy !== null && busy !== 'add'}>
              {editing ? 'Enregistrer la note' : 'Ajouter la note'}
            </Button>
            {editing && (
              <Button variant="quiet" disabled={busy !== null} onClick={resetForm}>
                Annuler
              </Button>
            )}
          </div>
        </form>
      )}
      {error && <Alert tone="error">{error}</Alert>}
    </section>
  );
}
