import { expect, test } from '@playwright/test'
import process from 'node:process'
import { randomUUID } from 'node:crypto'

test('administración real: acceso y creación de ejercicio inactivo', async ({ page }) => {
  test.skip(!process.env.E2E_ADMIN_EMAIL || !process.env.E2E_ADMIN_PASSWORD, 'Provide explicit development administrator credentials through environment variables.')
  const response = await page.request.post('http://localhost:8081/api/auth/login', { data: {
    email: process.env.E2E_ADMIN_EMAIL, password: process.env.E2E_ADMIN_PASSWORD,
  } })
  expect(response.status()).toBe(200)
  const token = (await response.json()).token as string
  await page.addInitScript(value => localStorage.setItem('oratoria.auth-token', value), token)
  await page.goto('/admin')
  await expect(page.getByRole('heading', { name: 'Gestión de la plataforma' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Usuarios', exact: true }).first()).toBeVisible()
  const code = `BROWSER_TEST_${randomUUID().replaceAll('-', '').toUpperCase()}`
  const title = `Prueba inactiva ${code.slice(-8)}`
  await page.getByLabel('Código', { exact: true }).fill(code)
  await page.getByLabel('Título', { exact: true }).fill(title)
  await page.getByLabel('Descripción', { exact: true }).fill('Ejercicio inactivo creado por una prueba de integración de interfaz.')
  await page.getByLabel('Objetivo', { exact: true }).fill('Comprobar la administración del catálogo sin habilitar contenido de prueba.')
  await page.getByLabel('Instrucciones', { exact: true }).fill('Describe una idea con apertura, ejemplo y conclusión.')
  await page.getByLabel('Disponible para practicar').uncheck()
  const creation = page.waitForResponse(result => result.url().endsWith('/admin/exercises') && result.request().method() === 'POST')
  await page.getByRole('button', { name: 'Guardar', exact: true }).click()
  expect((await creation).status()).toBe(201)
  await expect(page.getByRole('status')).toContainText('Ejercicio guardado')
  await expect(page.getByRole('cell', { name: title, exact: true })).toBeVisible()
  await page.setViewportSize({ width: 375, height: 900 })
  const width = await page.evaluate(() => document.documentElement.scrollWidth)
  expect(width).toBeLessThanOrEqual(376)
  await page.screenshot({ path: '../.tools/browser-results/admin-real.png', fullPage: true })
})
