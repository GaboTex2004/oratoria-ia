import { expect, test } from '@playwright/test'

for (const bodyFailsOnce of [false, true]) {
  test(`captura audiovisual conserva el audio y ${bodyFailsOnce ? 'reintenta solo el cuerpo' : 'muestra ambos resultados'}`, async ({ page }) => {
    let creations = 0
    let uploads = 0
    let bodyRequests = 0
    await page.addInitScript(() => {
      localStorage.setItem('oratoria.auth-token', 'synthetic-test')
      navigator.mediaDevices.getUserMedia = async () => {
        const canvas = document.createElement('canvas')
        canvas.width = 640; canvas.height = 480
        const context = canvas.getContext('2d')!
        const interval = window.setInterval(() => { context.fillStyle = 'blue'; context.fillRect(0, 0, 640, 480) }, 50)
        const videoStream = canvas.captureStream(10)
        const audioContext = new AudioContext()
        const oscillator = audioContext.createOscillator()
        const destination = audioContext.createMediaStreamDestination()
        oscillator.connect(destination); oscillator.start()
        const stream = new MediaStream([...videoStream.getVideoTracks(), ...destination.stream.getAudioTracks()])
        const stop = stream.getVideoTracks()[0].stop.bind(stream.getVideoTracks()[0])
        stream.getVideoTracks()[0].stop = () => { window.clearInterval(interval); void audioContext.close(); stop() }
        return stream
      }
    })
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname.replace('/api', '')
      let data: unknown = {}
      if (path === '/system/announcement') data = { message: '' }
      if (path === '/users/me') data = { id: 'user', firstName: 'Prueba', lastName: 'Sintética', role: 'USER', plan: 'PREMIUM' }
      if (path === '/subscriptions/current/features') data = { plan: 'PREMIUM', features: [{ code: 'CAMERA_ANALYSIS', enabled: true }] }
      if (path === '/system/limits') data = { audioMaxDurationSeconds: 15 }
      if (path === '/exercises') data = [{ id: 'exercise', title: 'Exposición sintética', prompt: 'Presenta tu idea.', trainingType: 'PRESENTATION' }]
      if (path === '/sessions') {
        if (route.request().method() === 'POST') { creations += 1; data = { id: 'new-session' } }
        else data = []
      }
      if (path === '/sessions/new-session/complete') {
        uploads += 1
        expect(route.request().headers()['content-type']).toContain('multipart/form-data')
        expect(route.request().postDataBuffer()!.length).toBeGreaterThan(100)
        data = { id: 'new-session', status: 'COMPLETED' }
      }
      if (path === '/sessions/new-session/body-analysis') {
        bodyRequests += 1
        const payload = route.request().postDataJSON()
        expect(payload.consent).toBe(true)
        expect(payload.frames.length).toBeGreaterThan(1)
        expect(payload.frames.length).toBeLessThanOrEqual(12)
        if (bodyFailsOnce && bodyRequests === 1) {
          await route.fulfill({ status: 503, json: { message: 'Cuerpo temporalmente no disponible' } }); return
        }
        data = { framesAnalyzed: payload.frames.length, framesWithPerson: 0, limitations: ['Captura sintética de prueba.'] }
      }
      if (path === '/sessions/new-session/analysis') data = {
        status: 'COMPLETED', transcriptText: 'Esta es una práctica audiovisual sintética.',
        metrics: { wordsPerMinute: 120, fillerWordsTotal: 2, estimatedPauseCount: 1 },
      }
      if (path === '/sessions/new-session/evaluation') data = { status: 'COMPLETED', executiveSummary: 'Evaluación sintética lista.' }
      await route.fulfill({ json: data })
    })
    await page.goto('/body')
    const panel = page.getByRole('region', { name: 'Práctica con audio y cámara' })
    await panel.getByLabel('Ejercicio audiovisual').selectOption('exercise')
    await panel.getByRole('checkbox').check()
    await panel.getByRole('button', { name: 'Grabar audio y cámara', exact: true }).click()
    await expect(panel.getByRole('button', { name: 'Detener audio y cámara' })).toBeVisible()
    await page.waitForTimeout(1400)
    await panel.getByRole('button', { name: 'Detener audio y cámara' }).click()
    await panel.getByRole('button', { name: 'Guardar y analizar audio y cuerpo' }).click()
    await expect(panel.getByText('Esta es una práctica audiovisual sintética.')).toBeVisible()
    if (bodyFailsOnce) {
      await expect(panel.getByText(/Cuerpo temporalmente no disponible/)).toBeVisible()
      await panel.getByRole('button', { name: 'Reintentar pendientes' }).click()
    }
    await expect(panel.getByText('Captura sintética de prueba.')).toBeVisible()
    await expect(panel.getByText('Evaluación sintética lista.', { exact: false })).toBeVisible()
    expect(creations).toBe(1)
    expect(uploads).toBe(1)
    expect(bodyRequests).toBe(bodyFailsOnce ? 2 : 1)
    expect(await page.evaluate(() => Array.from(document.querySelectorAll('video')).every(video =>
      !(video.srcObject instanceof MediaStream) || video.srcObject.getTracks().every(track => track.readyState === 'ended')))).toBe(true)
  })
}
