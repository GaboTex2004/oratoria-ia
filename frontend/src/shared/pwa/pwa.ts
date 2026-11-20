import { installMode, type InstallMode } from './installState'

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

let deferredPrompt: BeforeInstallPromptEvent | null = null
let installedNow = false
const listeners = new Set<() => void>()
const notify = () => listeners.forEach(listener => listener())

function isStandalone() {
  return installedNow
    || window.matchMedia('(display-mode: standalone)').matches
    || (navigator as Navigator & { standalone?: boolean }).standalone === true
}

/** Se llama antes de montar React: `beforeinstallprompt` puede llegar antes que cualquier componente. */
export function setupPwa() {
  window.addEventListener('beforeinstallprompt', event => {
    event.preventDefault()
    deferredPrompt = event as BeforeInstallPromptEvent
    notify()
  })
  window.addEventListener('appinstalled', () => {
    deferredPrompt = null
    installedNow = true
    notify()
  })
  window.matchMedia('(display-mode: standalone)').addEventListener('change', notify)
  if (import.meta.env.PROD && 'serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch(() => { /* la app sigue funcionando como sitio web */ })
    })
  }
}

export function getInstallMode(): InstallMode {
  return installMode({
    standalone: isStandalone(), hasPrompt: deferredPrompt != null,
    userAgent: navigator.userAgent, maxTouchPoints: navigator.maxTouchPoints || 0,
  })
}

export function subscribeInstall(listener: () => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

/** Abre el diálogo nativo de instalación. Devuelve `true` si la persona aceptó. */
export async function promptInstall() {
  const prompt = deferredPrompt
  if (!prompt) return false
  deferredPrompt = null
  try {
    await prompt.prompt()
    const { outcome } = await prompt.userChoice
    return outcome === 'accepted'
  } catch {
    return false
  } finally {
    notify()
  }
}
