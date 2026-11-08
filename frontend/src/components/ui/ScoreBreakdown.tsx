import type { CSSProperties, ReactNode } from 'react';
import { formatScore } from '../../lib/format';
import type { MatchScore } from '../../types';

/**
 * Le cœur de SmartRecruit : un score toujours accompagné de son explication, critère par critère
 * (DATA_MODEL.md §4.1). Chaque barre montre la contribution rapportée au poids du critère.
 */
export function ScoreBreakdown({ match, compact = false, showExplanations = true }: {
  match: MatchScore;
  compact?: boolean;
  showExplanations?: boolean;
}) {
  return (
    <div className={`score-breakdown${compact ? ' score-breakdown--compact' : ''}`}>
      {match.criteria.map((c, i) => {
        const max = c.weight * 100;
        const ratio = max > 0 ? Math.min(1, c.contribution / max) : 0;
        return (
          <div className="criterion" key={c.label}>
            <div>
              <div className="criterion__label">{c.label}</div>
              <div className="criterion__weight">Poids {Math.round(c.weight * 100)} %</div>
            </div>
            <div
              className="meter"
              role="meter"
              aria-label={`${c.label} : ${formatScore(c.contribution)} sur ${formatScore(max)}`}
              aria-valuemin={0}
              aria-valuemax={max}
              aria-valuenow={c.contribution}
            >
              <div className="meter__fill" style={{ width: `${ratio * 100}%`, '--i': i } as CSSProperties} />
            </div>
            <div className="criterion__points">
              {formatScore(c.contribution)}
              {!compact && <small> / {Math.round(max)}</small>}
            </div>
            {showExplanations && <p className="criterion__text">{c.explanation}</p>}
          </div>
        );
      })}
    </div>
  );
}

/** Ligne du score total : « 78,5 / 100 · libellé » avec une action optionnelle à droite. */
export function ScoreTotal({ score, label, action }: { score: number; label: ReactNode; action?: ReactNode }) {
  return (
    <div className="score-total">
      <span className="score-total__value">{formatScore(score)}</span>
      <span className="score-total__label">/ 100 · {label}</span>
      {action && <span className="score-total__action">{action}</span>}
    </div>
  );
}

/** Score compact pour les tableaux et listes. */
export function ScoreChip({ score }: { score: number | null | undefined }) {
  if (score === null || score === undefined) return <span className="muted">—</span>;
  return (
    <span className="score-chip">
      {formatScore(score)}
      <small>/100</small>
    </span>
  );
}
