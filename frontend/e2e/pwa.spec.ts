import { expect, test, type BrowserContext } from '@playwright/test'
import process from 'node:process'

// Requiere el build de producción: con E2E_PWA=1 Playwright levanta `vite preview` en el puerto 4173.
//   npm run build   (con VITE_API_URL=/api)
//   $env:E2E_PWA='1'; npx playwright test e2e/pwa.spec.ts
test.skip(process.env.E2E_PWA !== '1', 'Define E2E_PWA=1 y compila antes el build de producción.')

const user = { id: '1', firstName: 'Prueba', lastName: 'Pwa', email: 'pwa@example.test', role: 'USER', plan: 'FREE' }

/** API simulada que, como la red real, deja de responder cuando el contexto está sin conexión. */
async function mockApi(context: BrowserContext, state: { offline: boolean }) {
  await context.route('**/api/**', route => {
    if (state.offline) return route.abort('internetdisconnected')
    return route.request().url().endsWith('/users/me')
      ? route.fulfill({ json: user })
      : route.fulfill({ status: 404, json: { message: 'No disponible en la prueba' } })
  })
}

async function waitForServiceWorker(page: import('@playwright/test').Page) {
  await page.evaluate(async () => { await navigator.serviceWorker.ready })
  await page.reload()
  await expect.poll(() => page.evaluate(() => navigator.serviceWorker.controller != null)).toBe(true)
}

test('la app es instalable y vuelve a abrir sin conexión', async ({ page, context }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

  const manifestHref = await page.locator('link[rel="manifest"]').getAttribute('href')
  const manifest = await (await page.request.get(manifestHref!)).json()
  expect(manifest.display).toBe('standalone')
  for (const icon of manifest.icons) expect((await page.request.get(icon.src)).ok()).toBe(true)

  await waitForServiceWorker(page)
  const cached = await page.evaluate(async () => (await Promise.all((await caches.keys()).map(async key => (await caches.open(key)).keys()))).flat().map(request => new URL(request.url).pathname))
  expect(cached.some(path => path.endsWith('.ttf'))).toBe(true)

  await context.setOffline(true)
  await page.reload()
  await expect(page.getByLabel('Correo electrónico')).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: 'Sin conexión' })).toBeVisible()
  await context.setOffline(false)
})

test('con sesión iniciada, abrir la app sin conexión conserva la sesión y no guarda /api', async ({ page, context }) => {
  const state = { offline: false }
  await mockApi(context, state)
  await page.addInitScript(() => localStorage.setItem('oratoria.auth-token', 'token-de-prueba'))
  await page.goto('/home')
  await expect(page.getByRole('heading', { name: /Prueba/ })).toBeVisible()
  await waitForServiceWorker(page)

  state.offline = true
  await context.setOffline(true)
  await page.reload()
  await expect(page).toHaveURL(/\/home$/)
  await expect(page.getByRole('heading', { name: /Prueba/ })).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: 'Sin conexión' })).toBeVisible()
  await expect(page.locator('.install-banner')).toHaveCount(0)
  expect(await page.evaluate(() => localStorage.getItem('oratoria.auth-token'))).toBe('token-de-prueba')

  const cached = await page.evaluate(async () => (await Promise.all((await caches.keys()).map(async key => (await caches.open(key)).keys()))).flat().map(request => new URL(request.url).pathname))
  expect(cached.some(path => path.startsWith('/api/'))).toBe(false)

  state.offline = false
  await context.setOffline(false)
})

test('a 320 px la tarjeta de instalar no desborda y se puede descartar', async ({ browser }) => {
  const context = await browser.newContext({
    viewport: { width: 320, height: 640 },
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1',
  })
  await mockApi(context, { offline: false })
  const page = await context.newPage()
  await page.addInitScript(() => localStorage.setItem('oratoria.auth-token', 'token-de-prueba'))
  await page.goto('/home')
  const banner = page.getByRole('complementary', { name: 'Instalar aplicación' })
  await expect(banner).toContainText('Agregar a inicio')
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320)
  await banner.getByRole('button', { name: 'Entendido' }).click()
  await expect(banner).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', { name: /Prueba/ })).toBeVisible()
  await expect(banner).toHaveCount(0)
  await page.getByLabel('Abrir menú').click()
  await page.getByRole('button', { name: 'Instalar aplicación' }).click()
  await expect(page.locator('.install-sidebar-steps')).toContainText('Agregar a inicio')
  await context.close()
})
