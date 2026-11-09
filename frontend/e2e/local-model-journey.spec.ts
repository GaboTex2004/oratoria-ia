import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import process from 'node:process'

test('registro, grabación sintética, modelos locales, simulación, cámara sintética y PDF @models', async ({ page, context }) => {
  test.skip(process.env.E2E_MODELS !== '1' || !process.env.E2E_SYNTHETIC_AUDIO, 'Requires prepared real local models and an explicitly synthetic WAV.')
  test.setTimeout(1200000)
  await context.grantPermissions(['microphone', 'camera'])
  await page.setViewportSize({ width: 1440, height: 1000 })
  const email = `integration-${randomUUID()}@example.test`
  const password = `Test-${randomUUID()}`
  await page.goto('/register')
  await page.getByLabel('Nombre', { exact: true }).fill('IntegracionSintetica')
  await page.getByLabel('Apellido', { exact: true }).fill('ModelosLocales')
  await page.getByLabel('Correo electrónico').fill(email)
  await page.locator('#registerPassword').fill(password)
  await page.locator('#confirmPassword').fill(password)
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Crear mi cuenta' }).click()
  await expect(page).toHaveURL(/home/)
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click()
  await page.getByLabel('Correo electrónico').fill(email)
  await page.locator('#password').fill(password)
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click()
  await expect(page).toHaveURL(/home/)
  const token = await page.evaluate(() => localStorage.getItem('oratoria.auth-token'))
  const headers = { Authorization: `Bearer ${token}` }
  const api = 'http://localhost:8081/api'

  await page.goto('/training?type=PRESENTATION')
  await page.getByRole('button', { name: /Practicar ahora/ }).first().click()
  await page.getByRole('button', { name: /Comenzar grabación/ }).click()
  await page.getByRole('button', { name: /Detener grabación/ }).waitFor()
  await page.waitForTimeout(22000) // Real MediaRecorder time, using only the synthetic input file.
  await page.getByRole('button', { name: /Detener grabación/ }).click()
  const completion = page.waitForResponse(response => /\/sessions\/[^/]+\/complete$/.test(response.url()), { timeout: 60000 })
  await page.getByRole('button', { name: /Finalizar y guardar sesión/ }).click()
  const completed = await completion
  expect(completed.status()).toBe(200)
  const session = await completed.json()
  let finalEvaluation: { status: string; overallScore: number | null } | null = null
  await expect.poll(async () => {
    const response = await page.request.get(`${api}/sessions/${session.id}/evaluation`, { headers })
    if (!response.ok()) return 'WAITING'
    finalEvaluation = await response.json()
    if (finalEvaluation?.status === 'FAILED') throw new Error(JSON.stringify(finalEvaluation))
    return finalEvaluation?.status
  }, { timeout: 900000, intervals: [3000] }).toBe('COMPLETED')
  const analysisResponse = await page.request.get(`${api}/sessions/${session.id}/analysis`, { headers })
  const analysis = await analysisResponse.json()
  expect(analysis.provider).toBe('faster-whisper')
  expect(analysis.transcriptText.toLowerCase()).toContain('plataforma')
  expect(analysis.metrics.acousticMetrics.rmsAmplitude).toBeGreaterThan(0)
  await page.goto(`/results?sessionId=${session.id}`)
  await expect(page.getByText(/plataforma para mejorar/i).first()).toBeVisible({ timeout: 30000 })
  await page.screenshot({ path: '../.tools/browser-results/real-local-results.png', fullPage: true })
  await page.goto(`/reports/${session.id}`)
  await expect(page.getByRole('button', { name: 'Imprimir / guardar PDF' })).toBeEnabled()
  await page.evaluate(() => document.fonts.ready)
  await page.pdf({ path: '../.tools/browser-results/synthetic-local-report.pdf', format: 'A4', printBackground: true })
  await page.goto('/progress')
  await expect(page.getByRole('heading', { name: 'Tu progreso en oratoria' })).toBeVisible()
  await page.waitForLoadState('networkidle')
  await page.pdf({ path: '../.tools/browser-results/synthetic-progress-report.pdf', format: 'A4', printBackground: true })

  await page.goto('/simulations')
  const scenario = page.locator('.scenario-card').filter({ has: page.getByRole('heading', { name: 'Entrevista laboral', exact: true }) })
  await scenario.getByRole('button', { name: 'Iniciar', exact: true }).click()
  for (let turn = 0; turn < 2; turn++) {
    await page.getByRole('button', { name: /Grabar respuesta/ }).click()
    await page.getByRole('button', { name: /Detener/, exact: false }).waitFor()
    await page.waitForTimeout(22000)
    await page.getByRole('button', { name: /Detener/, exact: false }).click()
    const turnResult = page.waitForResponse(response => /\/simulations\/[^/]+\/respond$/.test(response.url()), { timeout: 900000 })
    await page.getByRole('button', { name: /Enviar turno/ }).click()
    expect((await turnResult).status()).toBe(200)
  }
  const finish = page.waitForResponse(response => /\/simulations\/[^/]+\/finish$/.test(response.url()), { timeout: 900000 })
  await page.getByRole('button', { name: /Finalizar y evaluar conversación/ }).click()
  const finishResponse = await finish
  expect(finishResponse.status()).toBe(200)
  const simulation = await finishResponse.json()
  expect(simulation.feedback.sufficientEvidence).toBe(true)
  await expect(page.getByRole('heading', { name: 'Resultado conversacional' })).toBeVisible()
  await page.screenshot({ path: '../.tools/browser-results/real-local-simulation.png', fullPage: true })
  const summary = await (await page.request.get(`${api}/progress/summary`, { headers })).json()
  expect(summary.overview.evaluatedSessions).toBe(1)

  await page.goto('/body')
  await page.getByLabel('Práctica', { exact: true }).selectOption(session.id)
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Activar cámara' }).click()
  await page.getByRole('button', { name: 'Capturar y analizar' }).click()
  await expect(page.getByRole('heading', { name: 'Indicadores observados' })).toBeVisible({ timeout: 120000 })
  await page.screenshot({ path: '../.tools/browser-results/synthetic-camera-result.png', fullPage: true })

  expect((await page.request.delete(`${api}/sessions/${session.id}/data`, { headers })).status()).toBe(202)
  expect((await page.request.get(`${api}/sessions/${session.id}/audio`, { headers })).status()).toBe(404)
  expect((await page.request.delete(`${api}/simulations/${simulation.id}/data`, { headers })).status()).toBe(202)
})
