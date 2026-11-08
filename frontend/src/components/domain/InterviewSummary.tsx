import { useState } from 'react';
import { formatDateTimeLong, formatDuration, INTERVIEW_MODE_LABEL } from '../../lib/format';
import { isLink } from '../../lib/interview';
import type { Interview } from '../../types';

/** Date, durée, format et lieu (ou lien cliquable) d'un entretien planifié. */
export function InterviewSummary({ interview }: { interview: Interview }) {
  // Read once per mount: the label does not need to flip while the page is open.
  const [mountedAt] = useState(() => Date.now());
  const past = new Date(interview.scheduledAt).getTime() < mountedAt;
  return (
    <dl className="props interview-summary">
      <dt>Date</dt>
      <dd>
        <span className="cell-strong">{formatDateTimeLong(interview.scheduledAt)}</span>
        {past && <span className="muted"> · passé</span>}
      </dd>
      <dt>Durée</dt>
      <dd>{formatDuration(interview.durationMinutes)}</dd>
      <dt>Format</dt>
      <dd>{INTERVIEW_MODE_LABEL[interview.mode]}</dd>
      {interview.location && (
        <>
          <dt>{interview.mode === 'VIDEO' ? 'Lien' : 'Lieu'}</dt>
          <dd className="interview-summary__location">
            {isLink(interview.location) ? (
              <a className="text-link" href={interview.location.trim()} target="_blank" rel="noopener noreferrer">
                {interview.location}
              </a>
            ) : (
              interview.location
            )}
          </dd>
        </>
      )}
    </dl>
  );
}
