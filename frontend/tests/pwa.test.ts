import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { installMode, isIos } from '../src/shared/pwa/installState.ts'

const iphone = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1'
const ipadOs = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/18.0 Safari/605.1.15'
const android = 'Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 Chrome/142.0 Mobile Safari/537.36'

test('detecta iPhone e iPadOS (que se presenta como Mac táctil), pero no una Mac ni Android', () => {
  assert.equal(isIos(iphone, 5), true)
  assert.equal(isIos(ipadOs, 5), true)
  assert.equal(isIos(ipadOs, 0), false)
  assert.equal(isIos(android, 5), false)
})

test('el modo de instalación prioriza la app ya instalada y luego el diálogo nativo', () => {
  assert.equal(installMode({ standalone: true, hasPrompt: true, userAgent: android, maxTouchPoints: 5 }), 'installed')
  assert.equal(installMode({ standalone: false, hasPrompt: true, userAgent: android, maxTouchPoints: 5 }), 'prompt')
  assert.equal(installMode({ standalone: false, hasPrompt: false, userAgent: iphone, maxTouchPoints: 5 }), 'ios')
  assert.equal(installMode({ standalone: false, hasPrompt: false, userAgent: android, maxTouchPoints: 5 }), 'unavailable')
})

test('el manifest cumple lo que exigen los navegadores para instalar', () => {
  const manifest = JSON.parse(readFileSync(new URL('../public/manifest.webmanifest', import.meta.url), 'utf8'))
  assert.equal(manifest.display, 'standalone')
  assert.equal(manifest.start_url, '/')
  assert.ok(manifest.name && manifest.short_name)
  const sizes = manifest.icons.map((icon: { sizes: string }) => icon.sizes)
  assert.ok(sizes.includes('192x192') && sizes.includes('512x512'))
  assert.ok(manifest.icons.some((icon: { purpose?: string }) => icon.purpose === 'maskable'))
})

test('el service worker nunca guarda en caché las llamadas a /api', () => {
  const worker = readFileSync(new URL('../public/sw.js', import.meta.url), 'utf8')
  assert.match(worker, /url\.pathname\.startsWith\('\/api\/'\)[^\n]*return/)
})
