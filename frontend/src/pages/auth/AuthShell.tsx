import type { ReactNode } from 'react';

/** Colonne étroite façon document pour les formulaires d'authentification. */
export function AuthShell({ title, intro, children, aside }: {
  title: string;
  intro?: ReactNode;
  children: ReactNode;
  aside?: ReactNode;
}) {
  return (
    <div className="auth">
      <div className="auth__col reveal">
        <h1 className="auth__title serif">{title}</h1>
        {intro && <p className="auth__intro">{intro}</p>}
        <div className="panel auth__panel">{children}</div>
        {aside && <div className="auth__aside">{aside}</div>}
      </div>
    </div>
  );
}
