import { Button } from './Button';

/** Pagination précédent / suivant ; `page` commence à 0. Ne s'affiche que s'il y a plusieurs pages. */
export function Pager({ page, totalPages, onPage, label = 'Pagination' }: {
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
  label?: string;
}) {
  if (totalPages <= 1) return null;
  return (
    <nav className="pager" aria-label={label}>
      <Button variant="ghost" disabled={page === 0} onClick={() => onPage(page - 1)}>
        Précédent
      </Button>
      <span className="muted">
        Page {page + 1} sur {totalPages}
      </span>
      <Button variant="ghost" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        Suivant
      </Button>
    </nav>
  );
}
