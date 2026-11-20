/* Service worker de OratoriaAI: instala la app y conserva la interfaz para abrirla sin conexión.
   Nunca guarda respuestas de /api: audio, transcripciones, resultados y datos personales siempre
   van directo al servidor. Si cambias la estrategia de caché, sube VERSION. */
const VERSION = 'v4'
const SHELL_CACHE = `oratoria-shell-${VERSION}`
const ASSET_CACHE = `oratoria-assets-${VERSION}`
const SHELL = ['/index.html', '/manifest.webmanifest', '/favicon.svg', '/icons/icon-192.png', '/icons/icon-512.png']

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const shell = await caches.open(SHELL_CACHE)
    await shell.addAll(SHELL.map(url => new Request(url, { cache: 'reload' })))
    // Precarga lo que usa index.html (JS, CSS y fuentes) para que la app abra sin conexión desde la primera visita.
    const assetCache = await caches.open(ASSET_CACHE)
    await assetCache.addAll(await referencedAssets(await (await shell.match('/index.html')).text(), assetCache))
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

  if (request.mode === 'navigate') event.respondWith(networkFirstPage(event))
  else if (url.pathname.startsWith('/assets/')) event.respondWith(cacheFirst(event))
  else if (SHELL.includes(url.pathname) || url.pathname.startsWith('/icons/')) event.respondWith(staleWhileRevalidate(event))
})

// Notificaciones push: el servidor manda {title, body, url, tag} cifrado; aquí se muestra aunque la app esté cerrada.
self.addEventListener('push', event => {
  let data = {}
  try { data = event.data ? event.data.json() : {} } catch { data = { body: event.data ? event.data.text() : '' } }
  // Solo rutas internas de la app ("/algo"), nunca "//otro-sitio".
  const url = typeof data.url === 'string' && /^\/(?!\/)/.test(data.url) ? data.url : '/home'
  event.waitUntil(self.registration.showNotification(data.title || 'OratoriaAI', {
    body: data.body || '',
    icon: '/icons/icon-192.png',
    badge: '/icons/icon-192.png',
    tag: data.tag || undefined,
    lang: 'es',
    data: { url },
  }))
})

// Al tocarla se abre la pantalla del aviso, reutilizando una ventana de la app si ya hay una abierta.
self.addEventListener('notificationclick', event => {
  event.notification.close()
  const target = new URL(event.notification.data?.url || '/home', self.location.origin).href
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const existing = windows.find(client => new URL(client.url).origin === self.location.origin)
    // Primero se intenta navegar (no consume el permiso del clic); si la ventana no lo admite, se abre una nueva.
    const navigated = existing ? await existing.navigate(target).catch(() => null) : null
    if (navigated) return navigated.focus()
    return self.clients.openWindow(target)
  })())
})

/** Rutas /assets/ que necesita un index.html: las del HTML y las fuentes que referencia su CSS. */
async function referencedAssets(html, assetCache) {
  const assets = [...html.matchAll(/(?:src|href)="(\/assets\/[^"]+)"/g)].map(match => match[1])
  const fonts = []
  for (const css of assets.filter(asset => asset.endsWith('.css'))) {
    const response = (await assetCache.match(css)) || await fetch(css)
    if (!response.ok) continue
    const text = await response.clone().text()
    if (!(await assetCache.match(css))) await assetCache.put(css, response)
    fonts.push(...[...text.matchAll(/url\((\/assets\/[^)"']+)\)/g)].map(match => match[1]))
  }
  return [...new Set([...assets, ...fonts])]
}

const isHtml = response => (response.headers.get('content-type') || '').includes('text/html')

async function networkFirstPage(event) {
  const shell = await caches.open(SHELL_CACHE)
  try {
    const response = await fetch(event.request)
    if (response.ok && isHtml(response)) {
      const copy = response.clone()
      event.waitUntil((async () => {
        await shell.put('/index.html', copy.clone())
        await pruneAssets(await copy.text())
      })())
    }
    return response
  } catch {
    return (await shell.match('/index.html')) || offlineResponse()
  }
}

/** Tras cada despliegue borra los archivos con hash que ya no usa el index.html vigente. */
async function pruneAssets(html) {
  const assetCache = await caches.open(ASSET_CACHE)
  const keep = new Set(await referencedAssets(html, assetCache))
  for (const request of await assetCache.keys()) {
    if (!keep.has(new URL(request.url).pathname)) await assetCache.delete(request)
  }
}

async function cacheFirst(event) {
  const cache = await caches.open(ASSET_CACHE)
  const cached = await cache.match(event.request)
  if (cached) return cached
  const response = await fetch(event.request)
  // Un HTML bajo /assets/ es el fallback del servidor para un archivo inexistente: nunca se guarda.
  if (response.ok && !isHtml(response)) event.waitUntil(cache.put(event.request, response.clone()))
  return response
}

async function staleWhileRevalidate(event) {
  const cache = await caches.open(SHELL_CACHE)
  const cached = await cache.match(event.request)
  const network = fetch(event.request).then(response => {
    if (response.ok) return cache.put(event.request, response.clone()).then(() => response)
    return response
  })
  if (cached) {
    event.waitUntil(network.catch(() => undefined))
    return cached
  }
  return network
}

function offlineResponse() {
  return new Response('<!doctype html><meta charset="utf-8"><title>OratoriaAI</title><p style="font-family:sans-serif;padding:24px">Sin conexión. Conéctate a internet para abrir OratoriaAI.</p>', {
    status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' },
  })
}
