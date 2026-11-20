import { expect, test } from '@playwright/test'
import process from 'node:process'

// Requiere el build de producción (el service worker no se registra con `vite dev`):
//   npm run build && npx vite preview --port 4173
//   $env:E2E_PWA='1'; $env:E2E_BASE_URL='http://localhost:4173'; npx playwright test e2e/pwa.spec.ts
test.skip(process.env.E2E_PWA !== '1', 'Define E2E_PWA=1 y apunta E2E_BASE_URL a un build de producción.')

test('la app es instalable y vuelve a abrir sin conexión', async ({ page, context }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

  const manifestHref = await page.locator('link[rel="manifest"]').getAttribute('href')
  const manifest = await (await page.request.get(manifestHref!)).json()
  expect(manifest.display).toBe('standalone')
  for (const icon of manifest.icons) expect((await page.request.get(icon.src)).ok()).toBe(true)

  await page.evaluate(async () => { await navigator.serviceWorker.ready })
  await page.reload()
  await expect.poll(() => page.evaluate(() => navigator.serviceWorker.controller != null)).toBe(true)
  const cached = await page.evaluate(async () => (await Promise.all((await caches.keys()).map(async key => (await caches.open(key)).keys()))).flat().map(request => new URL(request.url).pathname))
  expect(cached.some(path => path.endsWith('.ttf'))).toBe(true)
  expect(cached.some(path => path.startsWith('/api/'))).toBe(false)

  await context.setOffline(true)
  await page.reload()
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: 'Sin conexión' })).toBeVisible()
  await expect(page.getByLabel('Correo electrónico')).toBeVisible()
  await context.setOffline(false)
})
