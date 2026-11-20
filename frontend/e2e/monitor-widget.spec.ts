import { expect, test } from '@playwright/test'

// Sin backend: se simula la API para comprobar el botón de monitoreo en cualquier pantalla del administrador.
test('monitoreo visible en toda la plataforma: se abre solo ante una alerta nueva y muestra los pasos', async ({ page }) => {
  let alerts: object[] = []
  await page.route('**/api/**', route => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/users/me')) return route.fulfill({ json: { id: '1', firstName: 'Ana', lastName: 'Admin', email: 'admin@oratoria.test', role: 'ADMIN', plan: 'PREMIUM' } })
    if (url.pathname.endsWith('/admin/monitoring')) return route.fulfill({ json: {
      generatedAt: new Date().toISOString(), status: alerts.length ? 'CRITICO' : 'NORMAL', alerts, metrics: [], events: [] } })
    return route.fulfill({ status: 404, json: { message: 'Sin datos en la prueba' } })
  })
  await page.clock.install()
  await page.addInitScript(() => localStorage.setItem('oratoria.auth-token', 'token-de-prueba'))
  page.on('pageerror', error => { throw error })
  await page.goto('/home')

  const button = page.getByRole('button', { name: 'En orden' })
  await expect(button).toBeVisible()
  await expect(page.getByRole('dialog', { name: 'Monitoreo de la plataforma' })).toHaveCount(0)

  alerts = [{ code: 'SERVICIO_IA_DOWN', severity: 'CRITICO', message: 'Servicio de IA local no responde.' }]
  await page.clock.runFor(16000)
  const panel = page.getByRole('dialog', { name: 'Monitoreo de la plataforma' })
  await expect(panel).toBeVisible()
  await expect(panel.getByText('Servicio de IA local no responde.')).toBeVisible()
  await expect(panel.getByRole('listitem').first()).toContainText('docker compose ps')
  await expect(page.getByRole('button', { name: '1 alerta' })).toBeVisible()
  await page.setViewportSize({ width: 375, height: 800 })
  await page.screenshot({ path: '../.tools/browser-results/monitor-widget.png' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(376)

  await panel.getByRole('link', { name: 'Ver panel completo' }).click()
  await expect(page).toHaveURL(/\/admin\?tab=monitoreo/)
})
