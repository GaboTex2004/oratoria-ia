import { expect, test } from '@playwright/test'

test('entrenador temporal adapta el tiempo, repite con karaoke y descarta al cerrar', async ({ page }) => {
  let analyses = 0
  let sessions = 0
  await page.addInitScript(() => {
    localStorage.setItem('oratoria.auth-token', 'synthetic-voice-test')
    navigator.mediaDevices.getUserMedia = async () => {
      const context = new AudioContext()
      const oscillator = context.createOscillator()
      const destination = context.createMediaStreamDestination()
      oscillator.connect(destination); oscillator.start()
      const track = destination.stream.getAudioTracks()[0]
      const original = track.stop.bind(track)
      track.stop = () => { original(); void context.close() }
      return destination.stream
    }
  })
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api', '')
    let data: unknown = {}
    if (path === '/users/me') data = { firstName: 'Prueba', role: 'USER', plan: 'FREE' }
    if (path === '/system/announcement') data = { message: '' }
    if (path === '/exercises') data = []
    if (path === '/subscriptions/current/features') data = { features: [] }
    if (path === '/system/limits') data = { audioMaxDurationSeconds: 30 }
    if (path.startsWith('/sessions')) sessions += 1
    if (path === '/voice-coach/analyze') {
      analyses += 1
      expect(route.request().postDataBuffer()!.length).toBeGreaterThan(100)
      data = {
        transcriptText: 'Una idea clara ayuda a comunicar mejor.',
        words: [{ word: 'Una', startSeconds: 0, endSeconds: 0.5 }, { word: 'idea', startSeconds: 0.5, endSeconds: 1.4 }, { word: 'clara', startSeconds: 1.4, endSeconds: 2.5 }],
        metrics: { wordCount: 7, wordsPerMinute: 210, fillerWordsTotal: 0, fillerWordCounts: {}, longestPauseSeconds: 0.4 },
      }
    }
    await route.fulfill({ json: data })
  })
  await page.goto('/training')
  await page.getByRole('button', { name: 'Ajusta tu voz y entonación' }).click()
  const modal = page.getByRole('dialog', { name: 'Ajusta tu voz y entonación' })
  await modal.getByRole('checkbox').check()
  await modal.getByRole('button', { name: 'Grabar discurso inicial' }).click()
  await expect(modal.getByRole('button', { name: 'Detener grabación' })).toBeVisible()
  await page.waitForTimeout(2100)
  await modal.getByRole('button', { name: 'Detener grabación' }).click()
  await expect(modal.getByText('Una idea clara ayuda a comunicar mejor.', { exact: true })).toBeVisible()
  await modal.getByRole('button', { name: 'Ajustar y practicar otra vez' }).click()
  const duration = modal.getByLabel('Duración objetivo en segundos')
  await modal.getByLabel('Ritmo objetivo').selectOption('110')
  expect(Number(await duration.inputValue())).toBeGreaterThan(2)
  await duration.fill('2')
  await modal.getByLabel('Palabras a enfatizar').fill('idea')
  await modal.getByRole('button', { name: 'Ir al teleprompter' }).click()
  await expect(modal.locator('.is-emphasis')).toHaveText('idea ')
  await modal.getByRole('button', { name: 'Grabar nuevo intento' }).click()
  await page.waitForTimeout(600)
  await expect(modal.locator('.voice-coach__prompter .is-current')).toHaveCount(1)
  const progress = () => modal.locator('.voice-coach__prompter .is-current').evaluate(element => parseFloat((element as HTMLElement).style.getPropertyValue('--word-progress')))
  const firstProgress = await progress()
  expect(firstProgress).toBeGreaterThan(0)
  expect(firstProgress).toBeLessThan(100)
  await expect(modal.locator('.voice-coach__prompter .is-read').first()).toBeVisible()
  await modal.getByRole('button', { name: 'Pausar guion (el audio sigue grabándose)' }).click()
  await page.waitForTimeout(150)
  const pausedProgress = await progress()
  await page.waitForTimeout(200)
  expect(await progress()).toBeCloseTo(pausedProgress, 1)
  await modal.getByRole('button', { name: 'Continuar guion' }).click()
  await modal.getByRole('button', { name: 'Detener grabación' }).click()
  await expect(modal.getByText(/Comparación de ritmo/)).toBeVisible()
  await expect(modal.locator('.voice-coach__wave path')).toHaveCount(2)
  expect(analyses).toBe(2)
  expect(sessions).toBe(0)
  page.on('dialog', dialog => void dialog.accept())
  await modal.getByRole('button', { name: 'Cerrar', exact: true }).click()
  await expect(modal).toHaveCount(0)
  await page.getByRole('button', { name: 'Ajusta tu voz y entonación' }).click()
  await expect(page.getByRole('button', { name: 'Grabar discurso inicial' })).toBeVisible()
  await expect(page.getByLabel('Escuchar último intento')).toHaveCount(0)
})
