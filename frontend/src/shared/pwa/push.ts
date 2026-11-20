import { env } from '../config/env'
import { apiRequest } from '../services/api'
import { isIos } from './installState'

export type PushSupport = 'unsupported' | 'needs-install' | 'no-worker' | 'denied' | 'ready'

function standalone() {
  return window.matchMedia('(display-mode: standalone)').matches
    || (navigator as Navigator & { standalone?: boolean }).standalone === true
}

/** Qué puede hacer este navegador con las notificaciones push, sin pedir permisos. */
export function pushSupport(): PushSupport {
  const ios = isIos(navigator.userAgent, navigator.maxTouchPoints || 0)
  // En iPhone/iPad Safari solo ofrece push a la app instalada en la pantalla de inicio (iOS 16.4+).
  if (ios && !standalone()) return 'needs-install'
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) return 'unsupported'
  if (!import.meta.env.PROD) return 'no-worker'
  if (Notification.permission === 'denied') return 'denied'
  return 'ready'
}

function keyBytes(base64Url: string) {
  const base64 = (base64Url + '='.repeat((4 - base64Url.length % 4) % 4)).replace(/-/g, '+').replace(/_/g, '/')
  return Uint8Array.from(atob(base64), char => char.charCodeAt(0))
}

async function registration() {
  return navigator.serviceWorker.ready
}

export async function currentSubscription() {
  if (pushSupport() !== 'ready') return null
  return (await registration()).pushManager.getSubscription()
}

async function sendToServer(subscription: PushSubscription) {
  const data = subscription.toJSON()
  await apiRequest('/notifications/subscriptions', {
    method: 'POST',
    body: JSON.stringify({ endpoint: data.endpoint, keys: { p256dh: data.keys?.p256dh, auth: data.keys?.auth } }),
  })
}

/** Pide permiso (solo cuando la persona lo activa) y registra este dispositivo en el servidor. */
export async function enablePush() {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') throw new Error(permission === 'denied'
    ? 'El navegador bloqueó las notificaciones. Habilítalas en la configuración del sitio.'
    : 'No se concedió el permiso de notificaciones.')
  const { publicKey } = await apiRequest<{ publicKey: string }>('/notifications/config')
  const worker = await registration()
  let subscription = await worker.pushManager.getSubscription()
  // Una suscripción hecha con otra clave del servidor ya no sirve: se renueva.
  const currentKey = subscription?.options.applicationServerKey
  if (subscription && currentKey && !sameKey(new Uint8Array(currentKey), keyBytes(publicKey))) {
    await subscription.unsubscribe()
    subscription = null
  }
  try {
    subscription ??= await worker.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(publicKey) })
  } catch {
    // Chrome y Edge no permiten push en ventanas de incógnito, y algunos navegadores lo bloquean por política.
    throw new Error('El navegador no permitió registrar las notificaciones. Si estás en una ventana de incógnito, abre la app en una ventana normal o instálala.')
  }
  await sendToServer(subscription)
  return subscription
}

export async function disablePush() {
  const subscription = await currentSubscription()
  if (!subscription) return
  try {
    await apiRequest('/notifications/subscriptions', { method: 'DELETE', body: JSON.stringify({ endpoint: subscription.endpoint }) })
  } finally {
    await subscription.unsubscribe()
  }
}

/** Si este navegador está registrado para recibir los avisos de la cuenta con sesión iniciada. */
export async function isEnabledHere() {
  const subscription = await currentSubscription().catch(() => null)
  if (!subscription || Notification.permission !== 'granted') return false
  const { registered } = await apiRequest<{ registered: boolean }>(`/notifications/subscriptions/current?endpoint=${encodeURIComponent(subscription.endpoint)}`)
  return registered
}

function sameKey(a: Uint8Array, b: Uint8Array) {
  return a.length === b.length && a.every((value, index) => value === b[index])
}

/**
 * Al cerrar sesión, el servidor deja de enviar a este dispositivo los avisos de esa cuenta. Recibe el token antes
 * de que se borre; la suscripción del navegador se conserva y se reasigna al próximo inicio de sesión.
 */
export function forgetPushOnServer(token: string | null) {
  if (!token || !('serviceWorker' in navigator) || !import.meta.env.PROD) return
  void navigator.serviceWorker.getRegistration().then(async worker => {
    const subscription = await worker?.pushManager.getSubscription()
    if (!subscription) return
    await fetch(`${env.apiUrl}/notifications/subscriptions`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ endpoint: subscription.endpoint }),
    })
  }).catch(() => undefined)
}
