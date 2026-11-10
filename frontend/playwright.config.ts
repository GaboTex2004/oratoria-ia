import { defineConfig } from '@playwright/test'
import process from 'node:process'

export default defineConfig({
  testDir: './e2e', workers: 1, fullyParallel: false, timeout: 90000,
  outputDir: '../.tools/browser-results',
  reporter: [['list']],
  grepInvert: process.env.E2E_MODELS === '1' ? undefined : /@models/,
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
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
