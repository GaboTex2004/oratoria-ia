import assert from 'node:assert/strict'
import test from 'node:test'
import { REPORT_PERIODS, lockedFeatureFor, periodLabel, toggleLimited, weeklyPercent } from '../src/shared/personalization.ts'

const base = { trainingType: 'PRESENTATION', communicationStyle: null } as const

test('un ejercicio común nunca se bloquea', () => {
  assert.equal(lockedFeatureFor(base, () => false), null)
})

test('improvisación y estilos se bloquean sin la función del plan', () => {
  assert.equal(lockedFeatureFor({ ...base, trainingType: 'IMPROVISATION' }, () => false), 'IMPROVISATION')
  assert.equal(lockedFeatureFor({ ...base, communicationStyle: 'NARRATIVO' }, () => false), 'COMMUNICATION_STYLES')
  assert.equal(lockedFeatureFor({ ...base, communicationStyle: 'NARRATIVO' }, () => true), null)
})

test('la selección limitada agrega, quita y respeta el máximo', () => {
  assert.deepEqual(toggleLimited(['A'], 'B', 3), ['A', 'B'])
  assert.deepEqual(toggleLimited(['A', 'B'], 'A', 3), ['B'])
  assert.deepEqual(toggleLimited(['A', 'B', 'C'], 'D', 3), ['A', 'B', 'C'])
})

test('el avance semanal se limita a 100 %', () => {
  assert.equal(weeklyPercent(1, 4), 25)
  assert.equal(weeklyPercent(6, 4), 100)
  assert.equal(weeklyPercent(0, 0), 0)
})

test('los periodos del reporte coinciden con los que acepta el backend', () => {
  assert.deepEqual(REPORT_PERIODS, [7, 30, 90, 365, 0])
  assert.equal(periodLabel(30), 'Últimos 30 días')
  assert.equal(periodLabel(0), 'Todo el historial')
})
