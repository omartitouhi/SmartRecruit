import type { ButtonHTMLAttributes } from 'react';
import { Link, type LinkProps } from 'react-router-dom';

type Variant = 'primary' | 'ghost' | 'quiet' | 'danger';
type Size = 'md' | 'lg';

function classes(variant: Variant, size: Size, block?: boolean, extra?: string) {
  return [
    'btn',
    variant !== 'primary' && `btn--${variant}`,
    size === 'lg' && 'btn--lg',
    block && 'btn--block',
    extra,
  ]
    .filter(Boolean)
    .join(' ');
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  block?: boolean;
  /** Affiche un indicateur et désactive le bouton pendant une action. */
  loading?: boolean;
}

export function Button({
  variant = 'primary',
  size = 'md',
  block,
  loading,
  disabled,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={classes(variant, size, block, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <span className="btn__spinner" aria-hidden="true" />}
      {children}
    </button>
  );
}

interface ButtonLinkProps extends LinkProps {
  variant?: Variant;
  size?: Size;
  block?: boolean;
}

export function ButtonLink({ variant = 'primary', size = 'md', block, className, ...rest }: ButtonLinkProps) {
  return <Link className={classes(variant, size, block, className)} {...rest} />;
}
