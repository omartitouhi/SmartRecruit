import { describe, expect, it } from 'vitest';
import {
  formatDate,
  formatRate,
  formatScore,
  formatSize,
  formatYearMonth,
  initials,
  pluralize,
  scoreBand,
} from './format';

// Intl uses a narrow no-break space as the French thousands separator: normalise it for readable assertions.
const plain = (s: string) => s.replace(/[  ]/g, ' ');

describe('format helpers', () => {
  it('formats a score with one decimal and a comma', () => {
    expect(formatScore(78.5)).toBe('78,5');
    expect(formatScore(80)).toBe('80,0');
  });

  it('formats file sizes in o, Ko and Mo', () => {
    expect(formatSize(512)).toBe('512 o');
    expect(formatSize(2048)).toBe('2 Ko');
    expect(plain(formatSize(1.5 * 1024 * 1024))).toBe('1,5 Mo');
  });

  it('formats an acceptance rate given as a fraction or a percentage', () => {
    expect(plain(formatRate(0.5))).toBe('50 %');
    expect(plain(formatRate(50))).toBe('50 %');
    expect(plain(formatRate(1))).toBe('100 %');
  });

  it('maps scores to bands at 25, 50 and 75', () => {
    expect(scoreBand(90).label).toBe('Excellent');
    expect(scoreBand(75).label).toBe('Excellent');
    expect(scoreBand(74.9).label).toBe('Bon');
    expect(scoreBand(50).label).toBe('Bon');
    expect(scoreBand(49).label).toBe('Moyen');
    expect(scoreBand(24.9)).toEqual({ label: 'Faible', tone: 'red' });
  });

  it('formats year-month and handles an open end date', () => {
    expect(formatYearMonth('2022-01')).toMatch(/janv\.? 2022/);
    expect(formatYearMonth(null)).toBe('aujourd’hui');
  });

  it('shows a dash for a missing date', () => {
    expect(formatDate(null)).toBe('—');
    expect(formatDate('2026-10-14T09:00:00Z')).toMatch(/14 octobre 2026/);
  });

  it('builds initials from an email or a name', () => {
    expect(initials('amira.trabelsi@demo.tn')).toBe('AT');
    expect(initials('nour@demo.tn')).toBe('N');
    expect(initials('')).toBe('?');
  });

  it('pluralizes with a French thousands separator', () => {
    expect(pluralize(1, 'offre')).toBe('1 offre');
    expect(pluralize(3, 'offre')).toBe('3 offres');
    expect(pluralize(2, 'offre ouverte', 'offres ouvertes')).toBe('2 offres ouvertes');
  });
});
