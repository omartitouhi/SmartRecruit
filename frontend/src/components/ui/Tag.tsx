import type { ReactNode } from 'react';
import { APPLICATION_STATUS, JOB_STATUS, type Tone } from '../../lib/format';
import type { ApplicationStatus, JobStatus } from '../../types';

export function Tag({ tone = 'grey', children, onRemove, removeLabel }: {
  tone?: Tone;
  children: ReactNode;
  onRemove?: () => void;
  removeLabel?: string;
}) {
  return (
    <span className={`tag tag--${tone}`}>
      {children}
      {onRemove && (
        <button type="button" className="tag__remove" onClick={onRemove} aria-label={removeLabel ?? 'Retirer'}>
          <svg width="8" height="8" viewBox="0 0 8 8" aria-hidden="true">
            <path d="M1 1l6 6M7 1L1 7" stroke="currentColor" strokeWidth="1.5" />
          </svg>
        </button>
      )}
    </span>
  );
}

export function ApplicationStatusTag({ status }: { status: ApplicationStatus }) {
  const s = APPLICATION_STATUS[status];
  return <Tag tone={s.tone}>{s.label}</Tag>;
}

export function JobStatusTag({ status }: { status: JobStatus }) {
  const s = JOB_STATUS[status];
  return <Tag tone={s.tone}>{s.label}</Tag>;
}
