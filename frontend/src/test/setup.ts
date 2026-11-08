import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// Recent Node versions ship an experimental global localStorage that shadows jsdom's: without a storage file it is
// undefined (Node 26) or has no clear() (Node 22-25).
// fall back to an in-memory Storage so the tests behave the same on every Node.
if (typeof globalThis.localStorage?.clear !== 'function') {
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

// The mock gateway waits 200-500 ms per call to feel real; tests do not.
vi.mock('../api/mock/store', async (original) => ({
  ...(await original<typeof import('../api/mock/store')>()),
  delay: () => Promise.resolve(),
}));

afterEach(() => {
  cleanup();
  localStorage.clear();
});
