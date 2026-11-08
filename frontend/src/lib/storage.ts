/** localStorage qui ne casse jamais la page : navigation privée ou stockage bloqué = on ignore. */
export const storage = {
  get(key: string) {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  },
  set(key: string, value: string) {
    try {
      localStorage.setItem(key, value);
    } catch {
      // Nothing to do: the value just won't survive a reload.
    }
  },
  remove(key: string) {
    try {
      localStorage.removeItem(key);
    } catch {
      // Same as set.
    }
  },
};
