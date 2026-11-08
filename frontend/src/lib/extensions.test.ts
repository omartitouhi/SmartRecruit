import { describe, expect, it } from 'vitest';
import { buildRankingCsv } from '../pages/recruiter/rankingCsv';
import { makeApplication, makeMatch, makeProfile } from '../test/fixtures';
import { CSV_BOM, toCsv } from './csv';
import { deadlineLabel, formatDays, formatDuration, formatSalary, isoDay } from './format';
import { draftErrors, draftToRequest, interviewDraft, isLink, validateInterview } from './interview';
import { EMPTY_JOB, toFormState, validateJobForm } from './jobForm';
import { boardColumns, canMove, movesFrom } from './kanban';
import { noteStats } from './notes';
import type { ApplicationStatus } from '../types';

const NOW = new Date('2026-11-08T10:00:00Z');

describe('EXT-JOB-01 formatting', () => {
  it('formats a monthly salary range, a floor or a ceiling, and nothing without salary', () => {
    expect(formatSalary(2500, 3500)).toBe('2 500 – 3 500 TND / mois');
    expect(formatSalary(900, 900)).toBe('900 TND / mois');
    expect(formatSalary(4500, null)).toBe('À partir de 4 500 TND / mois');
    expect(formatSalary(null, 3000)).toBe('Jusqu’à 3 000 TND / mois');
    expect(formatSalary(null, null)).toBeNull();
  });

  it('labels a deadline by how close it is', () => {
    expect(deadlineLabel(null, NOW)).toBeNull();
    expect(deadlineLabel('2026-11-07', NOW)).toEqual({ label: 'Date limite dépassée', tone: 'red' });
    expect(deadlineLabel('2026-11-08', NOW)).toEqual({ label: 'Dernier jour pour postuler', tone: 'yellow' });
    expect(deadlineLabel('2026-11-11', NOW)).toEqual({ label: 'Clôture dans 3 jours', tone: 'yellow' });
    expect(deadlineLabel('2026-12-20', NOW)?.tone).toBe('grey');
    expect(isoDay(2, NOW)).toBe('2026-11-10');
  });

  it('formats days and durations', () => {
    expect(formatDays(6.5)).toBe('6,5 jours');
    expect(formatDays(1)).toBe('1,0 jour');
    expect(formatDuration(45)).toBe('45 min');
    expect(formatDuration(60)).toBe('1 h');
    expect(formatDuration(75)).toBe('1 h 15');
  });
});

describe('EXT-JOB-01 job form rules', () => {
  const valid = { ...toFormState(EMPTY_JOB), title: 'Dev', location: 'Tunis', description: 'x'.repeat(30), requiredSkills: ['Go'] };

  it('accepts a complete offer without the optional fields', () => {
    expect(validateJobForm(valid, null, '2026-11-08')).toEqual({});
  });

  it('checks the salary bounds and their order, and the number of positions', () => {
    expect(validateJobForm({ ...valid, salaryMin: '3000', salaryMax: '2000' }, null, '2026-11-08')).toEqual({
      salaryMax: 'Le maximum doit être supérieur ou égal au minimum.',
    });
    expect(validateJobForm({ ...valid, salaryMin: '-5' }, null, '2026-11-08').salaryMin).toBeDefined();
    expect(validateJobForm({ ...valid, salaryMax: '12.5' }, null, '2026-11-08').salaryMax).toBeDefined();
    expect(validateJobForm({ ...valid, positions: '0' }, null, '2026-11-08').positions).toBe('Entre 1 et 100 postes.');
    expect(validateJobForm({ ...valid, positions: '101' }, null, '2026-11-08').positions).toBeDefined();
  });

  it('refuses a new past deadline but keeps an unchanged one', () => {
    expect(validateJobForm({ ...valid, applicationDeadline: '2026-11-07' }, null, '2026-11-08').applicationDeadline).toBeDefined();
    expect(validateJobForm({ ...valid, applicationDeadline: '2026-11-08' }, null, '2026-11-08')).toEqual({});
    expect(validateJobForm({ ...valid, applicationDeadline: '2026-11-07' }, '2026-11-07', '2026-11-08')).toEqual({});
  });
});

describe('EXT-APP-02 interview rules', () => {
  const at = '2026-11-10T09:00:00.000Z';

  it('accepts a future interview with defaults', () => {
    expect(validateInterview({ scheduledAt: at, mode: 'VIDEO' }, NOW)).toEqual({});
  });

  it('requires a future date, a 15 to 480 minute duration, a mode and a short location', () => {
    expect(validateInterview({ mode: 'PHONE' }, NOW).scheduledAt).toBeDefined();
    expect(validateInterview({ scheduledAt: '2026-11-08T09:00:00Z', mode: 'PHONE' }, NOW).scheduledAt).toBe(
      'L’entretien doit avoir lieu dans le futur.',
    );
    expect(validateInterview({ scheduledAt: at, mode: 'PHONE', durationMinutes: 10 }, NOW).durationMinutes).toBeDefined();
    expect(validateInterview({ scheduledAt: at, mode: 'PHONE', durationMinutes: 481 }, NOW).durationMinutes).toBeDefined();
    expect(validateInterview({ scheduledAt: at }, NOW).mode).toBeDefined();
    expect(validateInterview({ scheduledAt: at, mode: 'ON_SITE', location: 'x'.repeat(501) }, NOW).location).toBeDefined();
  });

  it('converts a draft both ways and validates it', () => {
    const fresh = interviewDraft(null, NOW);
    expect(fresh).toMatchObject({ duration: '60', mode: 'VIDEO', location: '' });
    expect(draftErrors(fresh, NOW)).toEqual({});
    const request = draftToRequest({ ...fresh, location: '  https://meet.demo.tn/x  ' });
    expect(request).toMatchObject({ durationMinutes: 60, mode: 'VIDEO', location: 'https://meet.demo.tn/x' });
    expect(new Date(request.scheduledAt).getTime()).toBeGreaterThan(NOW.getTime());
    const again = interviewDraft({ scheduledAt: request.scheduledAt, durationMinutes: 45, mode: 'PHONE', location: null });
    expect(draftToRequest(again)).toEqual({ scheduledAt: request.scheduledAt, durationMinutes: 45, mode: 'PHONE', location: null });
    expect(draftErrors({ ...fresh, at: '' }, NOW).scheduledAt).toBeDefined();
  });

  it('tells a visio link from an address', () => {
    expect(isLink('https://meet.demo.tn/a')).toBe(true);
    expect(isLink('Les Berges du Lac, 3e étage')).toBe(false);
  });
});

describe('Kanban moves', () => {
  it('only allows the forward transitions, never a withdrawal', () => {
    expect(canMove('RECEIVED', 'IN_REVIEW')).toBe(true);
    expect(canMove('RECEIVED', 'INTERVIEW')).toBe(false);
    expect(canMove('RECEIVED', 'WITHDRAWN')).toBe(false);
    expect(canMove('INTERVIEW', 'ACCEPTED')).toBe(true);
    expect(canMove('INTERVIEW', 'REJECTED')).toBe(true);
    expect(canMove('ACCEPTED', 'REJECTED')).toBe(false);
    expect(movesFrom('IN_REVIEW')).toEqual(['INTERVIEW']);
    expect(movesFrom('REJECTED')).toEqual([]);
  });

  it('groups cards by status in their ranking order, withdrawn ones left out', () => {
    const item = (id: string, status: ApplicationStatus) => ({ application: makeApplication({ id, status }) });
    const columns = boardColumns([item('a', 'RECEIVED'), item('b', 'WITHDRAWN'), item('c', 'RECEIVED'), item('d', 'ACCEPTED')]);
    expect(columns.RECEIVED.map((i) => i.application.id)).toEqual(['a', 'c']);
    expect(columns.ACCEPTED.map((i) => i.application.id)).toEqual(['d']);
    expect(Object.keys(columns)).toEqual(['RECEIVED', 'IN_REVIEW', 'INTERVIEW', 'ACCEPTED', 'REJECTED']);
  });
});

describe('team notes', () => {
  it('counts notes and averages the rated ones to one decimal', () => {
    const note = (rating: number | null) => ({ id: 'n', applicationId: 'a', authorId: 'r', content: 'x', rating, createdAt: '' });
    expect(noteStats([])).toEqual({ notesCount: 0, averageRating: null });
    expect(noteStats([note(5), note(4), note(null), note(4)])).toEqual({ notesCount: 4, averageRating: 4.3 });
  });
});

describe('CSV export', () => {
  it('writes a UTF-8 BOM, semicolons, CRLF and escapes quotes, separators and formulas', () => {
    const csv = toCsv([
      ['a;b', 'say "hi"', 'line\nbreak'],
      ['=SUM(A1)', 3, null],
    ]);
    expect(csv.startsWith(CSV_BOM)).toBe(true);
    expect(csv.slice(1)).toBe('"a;b";"say ""hi""";"line\nbreak"\r\n\'=SUM(A1);3;\r\n');
  });

  it('exports the ranking in order with French numbers, without the personal data of a deleted account', () => {
    const csv = buildRankingCsv([
      {
        application: makeApplication({ id: 'a', status: 'INTERVIEW', matchScore: makeMatch(91), averageRating: 4.5, appliedAt: '2026-10-02T09:00:00Z' }),
        candidate: makeProfile({ firstName: 'Hedi', lastName: 'High', email: 'hedi@demo.tn' }),
      },
      { application: makeApplication({ id: 'w', status: 'WITHDRAWN' }), candidate: makeProfile() },
      { application: makeApplication({ id: 'b', matchScore: null, averageRating: null }), candidate: { id: 'gone', deleted: true } },
    ]);
    const lines = csv.slice(1).trim().split('\r\n');
    expect(lines[0]).toBe('Rang;Candidat·e;Email;Statut;Score / 100;Candidature reçue le;Note moyenne / 5');
    expect(lines[1]).toBe('1;Hedi High;hedi@demo.tn;Entretien;91,0;2026-10-02;4,5');
    expect(lines[2]).toBe('2;Compte supprimé;;Reçue;;2026-10-05;');
    expect(lines).toHaveLength(3);
  });
});
