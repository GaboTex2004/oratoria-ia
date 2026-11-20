/* Service worker de OratoriaAI: instala la app y conserva la interfaz para abrirla sin conexión.
   Nunca guarda respuestas de /api: audio, transcripciones, resultados y datos personales siempre
   van directo al servidor. */
const VERSION = 'v1'
const SHELL_CACHE = `oratoria-shell-${VERSION}`
const ASSET_CACHE = `oratoria-assets-${VERSION}`
const MAX_ASSETS = 80
const SHELL = ['/index.html', '/manifest.webmanifest', '/favicon.svg', '/icons/icon-192.png', '/icons/icon-512.png']

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const shell = await caches.open(SHELL_CACHE)
    await shell.addAll(SHELL.map(url => new Request(url, { cache: 'reload' })))
    // Precarga los archivos que referencia index.html para que la app abra sin conexión desde la primera visita.
    const html = await (await shell.match('/index.html')).text()
    const assets = [...html.matchAll(/(?:src|href)="(\/assets\/[^"]+)"/g)].map(match => match[1])
    const assetCache = await caches.open(ASSET_CACHE)
    await assetCache.addAll(assets)
    // Las fuentes (Inter e íconos) se referencian desde el CSS, no desde index.html.
    for (const css of assets.filter(asset => asset.endsWith('.css'))) {
      const text = await (await assetCache.match(css)).text()
      const fonts = [...text.matchAll(/url\((\/assets\/[^)"']+)\)/g)].map(match => match[1])
      await assetCache.addAll(fonts)
    }
    await self.skipWaiting()
  })())
})

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keep = new Set([SHELL_CACHE, ASSET_CACHE])
    for (const key of await caches.keys()) if (!keep.has(key)) await caches.delete(key)
    await self.clients.claim()
  })())
})

self.addEventListener('fetch', event => {
  const { request } = event
  if (request.method !== 'GET' || request.headers.has('range')) return
  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return
  if (url.pathname.startsWith('/api/') || url.pathname === '/healthz' || url.pathname === '/sw.js') return

  if (request.mode === 'navigate') event.respondWith(networkFirstPage(request))
  else if (url.pathname.startsWith('/assets/')) event.respondWith(cacheFirst(request))
  else if (SHELL.includes(url.pathname) || url.pathname.startsWith('/icons/')) event.respondWith(staleWhileRevalidate(request))
})

async function networkFirstPage(request) {
  const shell = await caches.open(SHELL_CACHE)
  try {
    const response = await fetch(request)
    if (response.ok && (response.headers.get('content-type') || '').includes('text/html')) {
      await shell.put('/index.html', response.clone())
    }
    return response
  } catch {
    return (await shell.match('/index.html')) || offlineResponse()
  }
}

async function cacheFirst(request) {
  const cache = await caches.open(ASSET_CACHE)
  const cached = await cache.match(request)
  if (cached) return cached
  const response = await fetch(request)
  if (response.ok) {
    await cache.put(request, response.clone())
    trimAssets(cache)
  }
  return response
}

async function staleWhileRevalidate(request) {
  const cache = await caches.open(SHELL_CACHE)
  const cached = await cache.match(request)
  const network = fetch(request)
    .then(response => { if (response.ok) cache.put(request, response.clone()); return response })
    .catch(() => cached)
  return cached || network
}

async function trimAssets(cache) {
  const keys = await cache.keys()
  for (const key of keys.slice(0, Math.max(0, keys.length - MAX_ASSETS))) await cache.delete(key)
}

function offlineResponse() {
  return new Response('<!doctype html><meta charset="utf-8"><title>OratoriaAI</title><p style="font-family:sans-serif;padding:24px">Sin conexión. Conéctate a internet para abrir OratoriaAI.</p>', {
    status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' },
  })
}
