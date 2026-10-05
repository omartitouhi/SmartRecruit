import { useCallback, useEffect, useRef, useState, type DependencyList } from 'react';
import { toUiError, type UiError } from './errors';

export interface AsyncState<T> {
  data: T | undefined;
  error: UiError | null;
  loading: boolean;
  reload(): void;
  /** Remplace la donnée localement après une mutation, sans recharger. */
  setData(update: T | ((previous: T | undefined) => T)): void;
}

/** Charge une ressource au montage et à chaque changement de `deps`. Ignore les réponses périmées. */
export function useAsync<T>(load: () => Promise<T>, deps: DependencyList): AsyncState<T> {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<UiError | null>(null);
  const [loading, setLoading] = useState(true);
  const [nonce, setNonce] = useState(0);
  const loadRef = useRef(load);
  loadRef.current = load;

  useEffect(() => {
    let current = true;
    setLoading(true);
    setError(null);
    loadRef
      .current()
      .then((result) => {
        if (current) setData(result);
      })
      .catch((e: unknown) => {
        if (current) setError(toUiError(e));
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce]);

  const reload = useCallback(() => setNonce((n) => n + 1), []);
  return { data, error, loading, reload, setData };
}

export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} · SmartRecruit` : 'SmartRecruit';
  }, [title]);
}
