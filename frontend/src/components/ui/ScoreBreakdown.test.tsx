import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { makeMatch } from '../../test/fixtures';
import { ScoreBandTag } from './Tag';
import { ScoreBreakdown, ScoreChip, ScoreTotal } from './ScoreBreakdown';

describe('ScoreBreakdown', () => {
  it('shows every criterion with its weight, points and explanation', () => {
    render(<ScoreBreakdown match={makeMatch()} />);
    expect(screen.getByText('Compétences techniques')).toBeInTheDocument();
    expect(screen.getByText('Poids 50 %')).toBeInTheDocument();
    expect(screen.getByText('4 des 5 compétences requises.')).toBeInTheDocument();
    expect(screen.getByText('Anglais manquant.')).toBeInTheDocument();
  });

  it('exposes each contribution as an accessible meter relative to its weight', () => {
    render(<ScoreBreakdown match={makeMatch()} />);
    const meter = screen.getByRole('meter', { name: /Compétences techniques : 40,0 sur 50,0/ });
    expect(meter).toHaveAttribute('aria-valuenow', '40');
    expect(meter).toHaveAttribute('aria-valuemax', '50');
    expect(within(meter).getByText((_, el) => el?.className === 'meter__fill')).toHaveStyle({ width: '80%' });
    // A criterion that contributed nothing is an empty bar, not a broken one.
    expect(screen.getByRole('meter', { name: /Langues : 0,0 sur 10,0/ })).toHaveAttribute('aria-valuenow', '0');
  });

  it('can hide the explanations and the maximum in compact lists', () => {
    render(<ScoreBreakdown match={makeMatch()} compact showExplanations={false} />);
    expect(screen.queryByText('Anglais manquant.')).not.toBeInTheDocument();
    expect(screen.queryByText(/\/ 50/)).not.toBeInTheDocument();
  });

  it('caps the bar at 100 % when a contribution exceeds its weight', () => {
    const match = { score: 60, criteria: [{ label: 'Trop', weight: 0.5, contribution: 80, explanation: '' }] };
    render(<ScoreBreakdown match={match} />);
    expect(screen.getByRole('meter')).toHaveAttribute('aria-valuenow', '80');
    expect(document.querySelector('.meter__fill')).toHaveStyle({ width: '100%' });
  });
});

describe('score summaries', () => {
  it('formats the total with its label and optional action', () => {
    render(<ScoreTotal score={78.5} label="compatibilité" action={<button>Postuler</button>} />);
    expect(screen.getByText('78,5')).toBeInTheDocument();
    expect(screen.getByText('/ 100 · compatibilité')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Postuler' })).toBeInTheDocument();
  });

  it('shows a dash for a missing score', () => {
    render(<ScoreChip score={null} />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('labels the band of a score and renders nothing without one', () => {
    const { rerender, container } = render(<ScoreBandTag score={80} />);
    expect(screen.getByText('Excellent')).toBeInTheDocument();
    rerender(<ScoreBandTag score={30} />);
    expect(screen.getByText('Moyen')).toBeInTheDocument();
    rerender(<ScoreBandTag score={undefined} />);
    expect(container).toBeEmptyDOMElement();
  });
});
