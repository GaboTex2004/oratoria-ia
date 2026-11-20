import assert from 'node:assert/strict'
import test from 'node:test'
import { findGuide, guideForScreen, guidesFor, GUIDES } from '../src/features/assistant/guides.ts'

const screens = ['/home', '/training', '/sessions', '/results', '/progress', '/simulations', '/profile', '/plans', '/subscription',
  '/body', '/recommendations', '/styles', '/reports']
const adminTabs = ['resumen', 'monitoreo', 'usuarios', 'catalogo', 'planes', 'reportes', 'bitacora', 'respaldos', 'sistema']

test('cada módulo tiene su propia guía', () => {
  for (const screen of screens) assert.equal(guideForScreen(screen, '', 'USER').screen, screen, screen)
  for (const tab of adminTabs) assert.equal(guideForScreen('/admin', `?tab=${tab}`, 'ADMIN').screen, `/admin?tab=${tab}`, tab)
  assert.equal(guideForScreen('/admin', '', 'ADMIN').id, 'admin-resumen')
  assert.equal(guideForScreen('/admin/payments', '', 'ADMIN').id, 'admin-pagos')
  assert.equal(guideForScreen('/checkout/abc', '', 'USER').id, 'checkout')
  assert.equal(guideForScreen('/reports/abc', '', 'USER').id, 'reporte')
  assert.equal(guideForScreen('/login', '', 'GUEST').id, 'ingresar')
  assert.equal(guideForScreen('/register', '', 'GUEST').id, 'registro')
})

test('responde preguntas escritas de forma natural, con o sin tildes', () => {
  assert.equal(findGuide('¿Cómo grabo mi discurso?', 'USER')?.id, 'practica')
  assert.equal(findGuide('donde veo mi nota', 'USER')?.id, 'resultados')
  assert.equal(findGuide('QUIERO PASAR A PREMIUM', 'USER')?.id, 'premium')
  assert.equal(findGuide('como cancelo la suscripcion', 'USER')?.id, 'suscripcion')
  assert.equal(findGuide('olvidé mi contraseña', 'GUEST')?.id, 'acceso')
  assert.equal(findGuide('¿Cómo creo una cuenta?', 'GUEST')?.id, 'registro')
  assert.equal(findGuide('cómo hago un respaldo', 'ADMIN')?.id, 'admin-respaldos')
  assert.equal(findGuide('xyz', 'USER'), null)
})

test('cada cuenta ve solo las guías que le corresponden', () => {
  assert.ok(guidesFor('USER').every(guide => guide.audience !== 'admin' && guide.audience !== 'guest'))
  assert.ok(guidesFor('GUEST').every(guide => guide.audience === 'guest' || guide.audience === 'all'))
  assert.equal(findGuide('cómo hago un respaldo', 'USER'), null)
  assert.equal(guidesFor('ADMIN').length, GUIDES.filter(guide => guide.audience !== 'guest').length)
})

test('todas las guías tienen pasos y palabras clave sin tildes ni mayúsculas', () => {
  for (const guide of GUIDES) {
    assert.ok(guide.steps.length >= 2, guide.id)
    for (const keyword of guide.keywords) assert.equal(keyword, keyword.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase(), guide.id)
  }
})
