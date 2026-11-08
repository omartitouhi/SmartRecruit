import { useState, type DragEvent, type ReactNode } from 'react';
import { APPLICATION_STATUS, STATUS_ACTION } from '../../lib/format';
import { boardColumns, canMove, movesFrom } from '../../lib/kanban';
import { FUNNEL_STATUSES, type Application, type ApplicationStatus, type FunnelStatus } from '../../types';

const DRAG_TYPE = 'application/x-smartrecruit-application';

interface Props<T extends { application: Application }> {
  items: T[];
  selectedId?: string;
  onSelect: (applicationId: string) => void;
  /** Asks for a move (the page confirms it before calling the API). Only allowed moves are ever requested. */
  onMove: (applicationId: string, to: ApplicationStatus) => void;
  renderCard: (item: T) => ReactNode;
}

/**
 * Pipeline Kanban d'une offre (EXT-FE) : une colonne par statut. Glisser-déposer une carte vers une colonne
 * autorisée, ou utiliser les boutons de la carte (clavier, écran tactile).
 */
export function KanbanBoard<T extends { application: Application }>({ items, selectedId, onSelect, onMove, renderCard }: Props<T>) {
  const columns = boardColumns(items);
  const [dragged, setDragged] = useState<Application | null>(null);
  const [over, setOver] = useState<FunnelStatus | null>(null);

  function onDrop(e: DragEvent, to: FunnelStatus) {
    e.preventDefault();
    const id = e.dataTransfer.getData(DRAG_TYPE) || dragged?.id;
    const application = items.find((i) => i.application.id === id)?.application;
    setDragged(null);
    setOver(null);
    if (application && canMove(application.status, to)) onMove(application.id, to);
  }

  return (
    <div className="board" role="list" aria-label="Pipeline des candidatures">
      {FUNNEL_STATUSES.map((status) => {
        const allowed = dragged ? canMove(dragged.status, status) : false;
        const cards = columns[status];
        return (
          <section
            key={status}
            role="listitem"
            className="board__column"
            aria-labelledby={`board-${status}`}
            data-drop={dragged ? (allowed ? (over === status ? 'over' : 'allowed') : 'blocked') : undefined}
            onDragOver={(e) => {
              if (!allowed) return;
              e.preventDefault();
              e.dataTransfer.dropEffect = 'move';
              setOver(status);
            }}
            onDragLeave={() => setOver((current) => (current === status ? null : current))}
            onDrop={(e) => onDrop(e, status)}
          >
            <h3 className="board__title" id={`board-${status}`}>
              {APPLICATION_STATUS[status].label}
              <span className="segmented__count">{cards.length}</span>
            </h3>
            <ul className="board__cards">
              {cards.map((item) => {
                const { application } = item;
                const moves = movesFrom(application.status);
                return (
                  <li
                    key={application.id}
                    className="board__card"
                    aria-current={application.id === selectedId || undefined}
                    draggable={moves.length > 0}
                    onDragStart={(e) => {
                      e.dataTransfer.setData(DRAG_TYPE, application.id);
                      e.dataTransfer.effectAllowed = 'move';
                      setDragged(application);
                    }}
                    onDragEnd={() => {
                      setDragged(null);
                      setOver(null);
                    }}
                  >
                    <button type="button" className="board__open" onClick={() => onSelect(application.id)}>
                      {renderCard(item)}
                    </button>
                    {moves.length > 0 && (
                      <span className="board__moves">
                        {moves.map((to) => (
                          <button key={to} type="button" className="board__move" onClick={() => onMove(application.id, to)}>
                            {STATUS_ACTION[to]}
                          </button>
                        ))}
                      </span>
                    )}
                  </li>
                );
              })}
              {cards.length === 0 && <li className="board__empty muted">Aucune candidature</li>}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
