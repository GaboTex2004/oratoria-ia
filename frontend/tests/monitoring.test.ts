import assert from 'node:assert/strict'
import test from 'node:test'
import { stepsFor } from '../src/features/admin/monitoringSteps.ts'

const codes = ['SERVICIO_BASE_DATOS_DOWN', 'DATABASE_QUERY_FAILED', 'SERVICIO_IA_DOWN', 'ALMACENAMIENTO_AUDIO_DOWN',
  'ANALISIS_STUCK', 'EVALUACIONES_STUCK', 'ANALISIS_FAILING', 'EVALUACIONES_FAILING', 'ANALISIS_QUEUE', 'EVALUACIONES_QUEUE',
  'LOGIN_FAILURES', 'MEDIA_DELETION_DELAYED', 'PUSH_FAILING', 'BACKEND_MEMORY']
const fallback = stepsFor('DESCONOCIDA')

test('cada alerta que calcula el backend tiene sus propios pasos', () => {
  for (const code of codes) {
    const steps = stepsFor(code)
    assert.ok(steps.length >= 2, code)
    assert.notDeepEqual(steps, fallback, code)
  }
})

test('una alerta desconocida remite al panel de Monitoreo', () => {
  assert.equal(fallback.length, 1)
  assert.match(fallback[0], /Monitoreo/)
})
