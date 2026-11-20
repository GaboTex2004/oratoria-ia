import { defineConfig } from '@playwright/test'
import process from 'node:process'

const pwa = process.env.E2E_PWA === '1'

export default defineConfig({
  testDir: './e2e', workers: 1, fullyParallel: false, timeout: 90000,
  outputDir: '../.tools/browser-results',
  reporter: [['list']],
  grepInvert: process.env.E2E_MODELS === '1' ? undefined : /@models/,
  webServer: process.env.E2E_PROXY_UPSTREAM ? {
    command: 'node e2e/local-proxy.cjs',
    url: 'http://127.0.0.1:15173/healthz',
    timeout: 30000,
    reuseExistingServer: false,
  } : pwa ? {
    // El service worker solo se registra en el build de producción.
    command: 'npx vite preview --port 4173 --strictPort',
    url: 'http://localhost:4173/manifest.webmanifest',
    timeout: 30000,
    reuseExistingServer: false,
  } : undefined,
  use: {
    baseURL: process.env.E2E_BASE_URL || (pwa ? 'http://localhost:4173' : 'http://localhost:5173'),
    // Fuera de las pruebas PWA el service worker se bloquea para que no tome la página a mitad de prueba.
    serviceWorkers: pwa ? 'allow' : 'block',
    channel: process.env.PLAYWRIGHT_CHANNEL || 'msedge',
    headless: true,
    screenshot: 'only-on-failure',
    actionTimeout: 30000,
    navigationTimeout: 30000,
    launchOptions: { args: process.env.E2E_SYNTHETIC_AUDIO ? [
      '--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream',
      `--use-file-for-fake-audio-capture=${process.env.E2E_SYNTHETIC_AUDIO}`,
    ] : [] },
  },
})
