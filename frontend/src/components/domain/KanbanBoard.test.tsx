import { createEvent, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { makeApplication } from '../../test/fixtures';
import { KanbanBoard } from './KanbanBoard';

const items = [
  { application: makeApplication({ id: 'r', status: 'RECEIVED' }), name: 'Rania' },
  { application: makeApplication({ id: 'i', status: 'INTERVIEW' }), name: 'Ilyes' },
  { application: makeApplication({ id: 'a', status: 'ACCEPTED' }), name: 'Amel' },
  { application: makeApplication({ id: 'w', status: 'WITHDRAWN' }), name: 'Walid' },
];

function show() {
  const onMove = vi.fn();
  const onSelect = vi.fn();
  render(<KanbanBoard items={items} onMove={onMove} onSelect={onSelect} renderCard={(item) => item.name} />);
  return { onMove, onSelect };
}

const column = (title: RegExp) => screen.getByRole('heading', { name: title }).closest('section') as HTMLElement;

/** jsdom has no DataTransfer: a minimal store is enough for the board. */
function dataTransfer() {
  const data = new Map<string, string>();
  return { setData: (k: string, v: string) => data.set(k, v), getData: (k: string) => data.get(k) ?? '', dropEffect: 'none', effectAllowed: 'all' };
}

function drop(target: HTMLElement, transfer: ReturnType<typeof dataTransfer>) {
  const over = createEvent.dragOver(target);
  Object.defineProperty(over, 'dataTransfer', { value: transfer });
  fireEvent(target, over);
  const dropped = createEvent.drop(target);
  Object.defineProperty(dropped, 'dataTransfer', { value: transfer });
  fireEvent(target, dropped);
}

describe('Kanban pipeline', () => {
  it('shows one column per status with its count, without withdrawn applications', () => {
    show();
    expect(within(column(/^Reçue/)).getByText('Rania')).toBeInTheDocument();
    expect(within(column(/^Acceptée/)).getByText('Amel')).toBeInTheDocument();
    expect(screen.queryByText('Walid')).not.toBeInTheDocument();
    expect(within(column(/^En revue/)).getByText('Aucune candidature')).toBeInTheDocument();
  });

  it('offers only the allowed moves as buttons, and nothing once decided', async () => {
    const user = userEvent.setup();
    const { onMove } = show();
    const received = within(column(/^Reçue/));
    expect(received.getByRole('button', { name: 'Passer en revue' })).toBeInTheDocument();
    expect(received.queryByRole('button', { name: 'Accepter' })).not.toBeInTheDocument();
    const interview = within(column(/^Entretien/));
    expect(interview.getByRole('button', { name: 'Accepter' })).toBeInTheDocument();
    expect(interview.getByRole('button', { name: 'Ne pas retenir' })).toBeInTheDocument();
    expect(within(column(/^Acceptée/)).queryAllByRole('button')).toHaveLength(1); // the card itself
    await user.click(interview.getByRole('button', { name: 'Ne pas retenir' }));
    expect(onMove).toHaveBeenCalledWith('i', 'REJECTED');
  });

  it('accepts a drop on an allowed column and ignores one on a forbidden column', () => {
    const { onMove } = show();
    const card = screen.getByText('Rania').closest('li') as HTMLElement;
    const transfer = dataTransfer();
    const start = createEvent.dragStart(card);
    Object.defineProperty(start, 'dataTransfer', { value: transfer });
    fireEvent(card, start);

    drop(column(/^Acceptée/), transfer);
    expect(onMove).not.toHaveBeenCalled();

    fireEvent(card, start);
    drop(column(/^En revue/), transfer);
    expect(onMove).toHaveBeenCalledWith('r', 'IN_REVIEW');
  });

  it('opens a card on click', async () => {
    const user = userEvent.setup();
    const { onSelect } = show();
    await user.click(screen.getByRole('button', { name: 'Ilyes' }));
    expect(onSelect).toHaveBeenCalledWith('i');
  });
});
