import { useCallback, useState, type ReactNode } from 'react';
import { ToastContext } from './toast';

interface Toast {
  id: number;
  message: string;
}

let nextId = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const notify = useCallback((message: string) => {
    const id = ++nextId;
    setToasts((list) => [...list.slice(-2), { id, message }]);
    setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), 3600);
  }, []);

  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div className="toast-region" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div className="toast" key={t.id}>
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
