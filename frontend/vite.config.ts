import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      // The gateway has no CORS config: the browser talks to Vite, which forwards /api to it.
      proxy: {
        '/api': { target: env.VITE_PROXY_TARGET || 'http://localhost:8080', changeOrigin: true },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      // A developer's .env (VITE_USE_MOCKS=true…) must not change what the tests exercise.
      env: { VITE_USE_MOCKS: '', VITE_MOCK_MODULES: '' },
    },
  }
})
