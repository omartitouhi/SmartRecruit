/** Séparateur attendu par Excel en français. */
export const CSV_SEPARATOR = ';';
/** BOM UTF-8 : sans lui, Excel lit le fichier en Windows-1252 et abîme les accents. */
export const CSV_BOM = '﻿';

type Cell = string | number | null | undefined;

function escapeCell(cell: Cell) {
  if (cell === null || cell === undefined) return '';
  const text = String(cell);
  // Formula injection: a cell starting with = + - @ would run as a formula in Excel.
  const safe = /^[=+\-@\t\r]/.test(text) ? `'${text}` : text;
  return /[";\r\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
}

/** Lignes → texte CSV (séparateur `;`, fins de ligne CRLF, BOM UTF-8 en tête). */
export function toCsv(rows: Cell[][]) {
  return CSV_BOM + rows.map((row) => row.map(escapeCell).join(CSV_SEPARATOR)).join('\r\n') + '\r\n';
}

/** Propose le fichier au téléchargement depuis le navigateur. */
export function downloadCsv(filename: string, content: string) {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** « Développeur·se backend Java » → « developpeur-se-backend-java » pour un nom de fichier. */
export function slugify(text: string) {
  return (
    text
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'export'
  );
}
