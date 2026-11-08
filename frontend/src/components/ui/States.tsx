import type { CSSProperties, ReactNode } from 'react';
import type { UiError } from '../../lib/errors';
import { Button } from './Button';

export function Skeleton({ width = '100%', height = 14, style }: { width?: number | string; height?: number; style?: CSSProperties }) {
  return <span className="skeleton" style={{ width, height, ...style }} aria-hidden="true" />;
}

/** Squelette de page complète : titre puis lignes de contenu. */
export function PageSkeleton() {
  return (
    <div className="page" aria-busy="true" aria-label="Chargement">
      <Skeleton width={120} height={12} />
      <Skeleton width="45%" height={40} style={{ marginTop: 20 }} />
      <Skeleton width="60%" style={{ marginTop: 16 }} />
      <div className="panel" style={{ marginTop: 48 }}>
        <SkeletonRows rows={5} />
      </div>
    </div>
  );
}

export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div style={{ display: 'grid', gap: 18 }} aria-hidden="true">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr 80px', gap: 24 }}>
          <Skeleton width={`${70 - i * 7}%`} />
          <Skeleton />
        </div>
      ))}
    </div>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <p className="empty__title">{title}</p>
      {children && <div className="empty__text">{children}</div>}
      {action}
    </div>
  );
}

type AlertTone = 'error' | 'success' | 'info' | 'warning' | 'neutral';

export function Alert({ tone = 'neutral', title, children, action }: {
  tone?: AlertTone;
  title?: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className={`alert${tone === 'neutral' ? '' : ` alert--${tone}`}`} role={tone === 'error' ? 'alert' : 'status'}>
      <div className="alert__body">
        {title && <p className="alert__title">{title}</p>}
        {children && <div>{children}</div>}
      </div>
      {action}
    </div>
  );
}

/** Erreur de chargement d'une ressource, avec possibilité de réessayer. */
export function ErrorState({ error, onRetry }: { error: UiError; onRetry?: () => void }) {
  return (
    <Alert
      tone="error"
      title={error.status === 503 ? 'Service indisponible' : 'Chargement impossible'}
      action={
        onRetry && (
          <Button variant="ghost" onClick={onRetry}>
            Réessayer
          </Button>
        )
      }
    >
      {error.message}
    </Alert>
  );
}
