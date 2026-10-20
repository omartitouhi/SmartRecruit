import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// Recent Node versions ship an experimental global localStorage that shadows jsdom's and has no clear():
// fall back to an in-memory Storage so the tests behave the same on every Node.
if (typeof localStorage.clear !== 'function') {
  const data = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => void data.set(key, String(value)),
    removeItem: (key: string) => void data.delete(key),
    clear: () => data.clear(),
    key: (index: number) => [...data.keys()][index] ?? null,
    get length() {
      return data.size;
    },
  } satisfies Storage);
}

afterEach(() => {
  cleanup();
  localStorage.clear();
});
