import { createContext, useContext } from 'react';

export const ToastContext = createContext<(message: string) => void>(() => undefined);

/** Message bref de confirmation (« Profil enregistré »), annoncé aux lecteurs d'écran. */
export const useToast = () => useContext(ToastContext);
