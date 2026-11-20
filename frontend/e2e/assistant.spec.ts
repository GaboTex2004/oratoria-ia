import { expect, test, type Page } from '@playwright/test'

// Sin backend: se simula la API para probar el asistente con cada tipo de cuenta.
async function mockApi(page: Page, role: 'USER' | 'ADMIN' | null, state: { alerts: object[] }) {
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/users/me')) return role
      ? route.fulfill({ json: { id: role, firstName: 'Ana', lastName: 'Prueba', email: 'ana@oratoria.test', role, plan: 'FREE' } })
      : route.fulfill({ status: 401, json: { message: 'Sin sesión' } })
    if (path.endsWith('/admin/monitoring')) return route.fulfill({ json: {
      generatedAt: new Date().toISOString(), status: state.alerts.length ? 'CRITICO' : 'NORMAL', alerts: state.alerts, metrics: [], events: [] } })
    return route.fulfill({ status: 404, json: { message: 'Sin datos en la prueba' } })
  })
  if (role) await page.addInitScript(() => localStorage.setItem('oratoria.auth-token', 'token-de-prueba'))
  page.on('pageerror', error => { throw error })
}

test('visitante: el asistente explica cómo entrar y crear una cuenta', async ({ page }) => {
  await mockApi(page, null, { alerts: [] })
  await page.goto('/login')
  await page.getByRole('button', { name: 'Asistente' }).click()
  const panel = page.getByRole('dialog', { name: 'Asistente de OratoriaAI' })
  await expect(panel.getByText('¿Cómo entro a mi cuenta?')).toBeVisible()
  await panel.getByRole('button', { name: '¿Cómo creo una cuenta?' }).click()
  await expect(panel.getByText('Acepta los términos y toca «Crear mi cuenta».', { exact: false })).toBeVisible()
})

test('usuario: guía de la pantalla, pregunta escrita y cambio de módulo', async ({ page }) => {
  await mockApi(page, 'USER', { alerts: [] })
  await page.goto('/training')
  await page.getByRole('button', { name: 'Asistente' }).click()
  const panel = page.getByRole('dialog', { name: 'Asistente de OratoriaAI' })
  await expect(panel.getByText('Hola, Ana. Así se usa esta pantalla:')).toBeVisible()
  await expect(panel.getByText('Toca «Comenzar grabación» y permite el uso del micrófono.')).toBeVisible()
  await expect(panel.getByRole('button', { name: /Plataforma/ })).toHaveCount(0)

  await panel.getByLabel('Escribe tu pregunta').fill('donde veo mi nota')
  await panel.getByRole('button', { name: 'Enviar pregunta' }).click()
  await expect(panel.getByText('donde veo mi nota')).toBeVisible()
  await expect(panel.getByText('Abre Resultados en el menú y elige la sesión.')).toBeVisible()
  await panel.getByRole('link', { name: 'Ir a Resultados →' }).click()
  await expect(page).toHaveURL(/\/results$/)

  await panel.getByRole('button', { name: 'Esta pantalla' }).click()
  await expect(panel.getByText('Así se usa esta pantalla:').last()).toBeVisible()
  await panel.getByLabel('Escribe tu pregunta').fill('como hago un respaldo')
  await panel.getByRole('button', { name: 'Enviar pregunta' }).click()
  await expect(panel.getByText('No encontré una guía para eso.', { exact: false })).toBeVisible()

  await page.setViewportSize({ width: 375, height: 800 })
  await page.screenshot({ path: '../.tools/browser-results/asistente-usuario.png' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(376)
})

test('administrador: guía de cada pestaña y aviso automático de alertas con pasos', async ({ page }) => {
  const state = { alerts: [] as object[] }
  await mockApi(page, 'ADMIN', state)
  await page.clock.install()
  await page.goto('/admin?tab=respaldos')
  await page.getByRole('button', { name: 'Asistente' }).click()
  const panel = page.getByRole('dialog', { name: 'Asistente de OratoriaAI' })
  await expect(panel.getByText('Toca «Generar respaldo ahora» (puedes agregar una nota).')).toBeVisible()
  await expect(panel.getByRole('button', { name: 'Plataforma: Todo en orden' })).toBeVisible()
  await panel.getByRole('button', { name: 'Cerrar asistente' }).click()

  state.alerts = [{ code: 'SERVICIO_IA_DOWN', severity: 'CRITICO', message: 'Servicio de IA local no responde.' }]
  await page.clock.runFor(16000)
  await expect(panel).toBeVisible()
  await expect(panel.getByText('Apareció una alerta en la plataforma.', { exact: false })).toBeVisible()
  await expect(panel.getByText('docker compose up -d ai-service ollama', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: '1 alerta' })).toBeVisible()
  await page.setViewportSize({ width: 375, height: 800 })
  await page.screenshot({ path: '../.tools/browser-results/asistente-admin.png' })
})
