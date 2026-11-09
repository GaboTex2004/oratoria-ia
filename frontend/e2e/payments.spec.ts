import { expect, test, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import process from 'node:process'

const api = 'http://localhost:8081/api'
async function register(page: Page) {
  const response = await page.request.post(`${api}/auth/register`, { data: {
    firstName: 'PagoSintetico', lastName: 'Prueba', email: `payment-${randomUUID()}@example.test`, password: `Test-${randomUUID()}`,
  } })
  expect(response.status()).toBe(201)
  const account = await response.json()
  await page.addInitScript(token => localStorage.setItem('oratoria.auth-token', token), account.token)
  return account as { token: string; user: { id: string } }
}
async function startCheckout(page: Page) {
  await page.goto('/plans')
  await expect(page.getByLabel('Modo demostración')).toBeVisible()
  await page.getByRole('button', { name: 'Seleccionar Premium' }).click()
  await expect(page).toHaveURL(/checkout\//)
  await expect(page.getByRole('heading', { name: 'Pago de prueba' })).toBeVisible()
}
async function administrator(page: Page) {
  const response = await page.request.post(`${api}/auth/login`, { data: { email: process.env.E2E_ADMIN_EMAIL, password: process.env.E2E_ADMIN_PASSWORD } })
  expect(response.status()).toBe(200)
  return (await response.json()).token as string
}

test('pago aprobado, consumo Premium, cancelación, vencimiento y datos conservados', async ({ page }) => {
  test.skip(!process.env.E2E_ADMIN_EMAIL || !process.env.E2E_ADMIN_PASSWORD, 'Requires explicit development administrator credentials.')
  await page.goto('/register')
  const password = `Test-${randomUUID()}`
  await page.getByLabel('Nombre', { exact: true }).fill('CompraSintetica')
  await page.getByLabel('Apellido', { exact: true }).fill('Demo')
  await page.getByLabel('Correo electrónico').fill(`payment-ui-${randomUUID()}@example.test`)
  await page.locator('#registerPassword').fill(password)
  await page.locator('#confirmPassword').fill(password)
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Crear mi cuenta' }).click()
  await expect(page).toHaveURL(/home/)
  const token = await page.evaluate(() => localStorage.getItem('oratoria.auth-token'))
  const headers = { Authorization: `Bearer ${token}` }
  const me = await (await page.request.get(`${api}/users/me`, { headers })).json()
  await page.goto('/subscription')
  await expect(page.getByRole('heading', { name: 'Plan FREE', exact: true })).toBeVisible()
  await startCheckout(page)
  await page.getByRole('radio', { name: 'Aprobada', exact: true }).check()
  await page.getByRole('button', { name: 'Confirmar pago simulado' }).click()
  await expect(page.getByRole('status')).toContainText('aprobado')
  await expect(page.getByRole('heading', { name: 'Comprobante de demostración' })).toBeVisible()
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: '../.tools/payment-evidence/approved-receipt.png', fullPage: true })
  const receiptPdf = await page.pdf({ path: '../.tools/payment-evidence/synthetic-receipt.pdf', format: 'A4', printBackground: true })
  expect(receiptPdf.subarray(0, 5).toString()).toBe('%PDF-')
  await page.goto('/subscription')
  await expect(page.getByRole('heading', { name: 'Plan PREMIUM', exact: true })).toBeVisible()
  // Sixth creation verifies the real Premium quota, beyond FREE's five practices.
  for (let index = 0; index < 6; index++) {
    expect((await page.request.post(`${api}/sessions`, { headers, data: { trainingType: 'PRESENTATION' } })).status()).toBe(201)
  }
  await page.getByRole('button', { name: 'Actualizar suscripción' }).click()
  await expect(page.getByText('Prácticas: 6 / Sin límite', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Ver comprobante' })).toHaveCount(1)
  await page.getByRole('button', { name: 'Cancelar renovación simulada' }).click()
  await expect(page.getByText('Conservarás Premium hasta el vencimiento indicado. Después regresarás a FREE.')).toBeVisible()
  const adminToken = await administrator(page)
  const advance = await page.request.post(`${api}/admin/payments/users/${me.id}/advance`, { headers: { Authorization: `Bearer ${adminToken}` }, data: { days: 40, renewalScenario: 'DEMO_APPROVED' } })
  expect(advance.status()).toBe(200)
  expect((await advance.json()).plan).toBe('FREE')
  await page.getByRole('button', { name: 'Actualizar suscripción' }).click()
  await expect(page.getByRole('heading', { name: 'Plan FREE', exact: true })).toBeVisible()
  const sessions = await (await page.request.get(`${api}/sessions`, { headers })).json()
  expect(sessions).toHaveLength(6)
  expect((await page.request.post(`${api}/sessions`, { headers, data: { trainingType: 'PRESENTATION' } })).status()).toBe(400)
})

test('tarjeta rechazada conserva FREE y muestra comprobante sin autorización', async ({ page }) => {
  const account = await register(page)
  await startCheckout(page)
  await page.getByRole('radio', { name: 'Rechazada', exact: true }).check()
  await page.getByRole('button', { name: 'Confirmar pago simulado' }).click()
  await expect(page.getByRole('status')).toContainText('rechazado')
  await expect(page.getByText('No emitida', { exact: true })).toBeVisible()
  const subscription = await (await page.request.get(`${api}/subscriptions/current`, { headers: { Authorization: `Bearer ${account.token}` } })).json()
  expect(subscription.plan).toBe('FREE')
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: '../.tools/payment-evidence/declined-receipt.png', fullPage: true })
})

test('pendiente solo se activa con confirmación del administrador y panel real', async ({ page, browser }) => {
  test.skip(!process.env.E2E_ADMIN_EMAIL || !process.env.E2E_ADMIN_PASSWORD, 'Requires explicit development administrator credentials.')
  const account = await register(page)
  await startCheckout(page)
  const orderId = page.url().split('/').at(-1)!
  await page.getByRole('radio', { name: 'Pendiente', exact: true }).check()
  await page.getByRole('button', { name: 'Confirmar pago simulado' }).click()
  await expect(page.getByRole('status')).toContainText('Pago pendiente')
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: '../.tools/payment-evidence/pending-receipt.png', fullPage: true })
  const headers = { Authorization: `Bearer ${account.token}` }
  expect((await (await page.request.get(`${api}/subscriptions/current`, { headers })).json()).plan).toBe('FREE')
  expect((await page.request.post(`${api}/admin/payments/orders/${orderId}/confirm`, { headers, data: { decision: 'APPROVE' } })).status()).toBe(403)
  const adminToken = await administrator(page)
  const adminContext = await browser.newContext()
  await adminContext.addInitScript(token => localStorage.setItem('oratoria.auth-token', token), adminToken)
  const adminPage = await adminContext.newPage()
  await adminPage.goto('/admin/payments')
  const card = adminPage.locator('article').filter({ hasText: orderId })
  await card.getByRole('button', { name: 'Confirmar pago pendiente' }).click()
  await expect(adminPage.getByRole('status')).toContainText('Confirmación registrada')
  await adminContext.close()
  await page.getByRole('button', { name: 'Actualizar estado' }).click()
  await expect(page.getByRole('status')).toContainText('aprobado')
  expect((await (await page.request.get(`${api}/subscriptions/current`, { headers })).json()).plan).toBe('PREMIUM')
})

test('checkout y suscripción adaptables a 320px sin campos financieros', async ({ page }) => {
  await register(page)
  await page.setViewportSize({ width: 320, height: 800 })
  await startCheckout(page)
  await expect(page.locator('input')).toHaveCount(4) // Three scenario radios and the topbar search.
  for (const path of [page.url(), '/subscription', '/plans']) {
    await page.goto(path)
    await expect(page.locator('.workspace-content').getByRole('heading', { level: 1 })).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true)
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
    await expect(page.getByLabel('Modo demostración')).toBeInViewport()
  }
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: '../.tools/payment-evidence/mobile-plans-320.png', fullPage: true })
})
