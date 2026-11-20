export type InstallMode = 'installed' | 'prompt' | 'ios' | 'unavailable'

export interface InstallEnvironment {
  standalone: boolean
  hasPrompt: boolean
  userAgent: string
  maxTouchPoints: number
}

/** iPhone/iPad no emiten `beforeinstallprompt`: se instala desde Safari con «Agregar a inicio». */
export function isIos(userAgent: string, maxTouchPoints: number) {
  if (/iPad|iPhone|iPod/.test(userAgent)) return true
  // iPadOS se presenta como Mac de escritorio, pero tiene pantalla táctil.
  return /Macintosh/.test(userAgent) && maxTouchPoints > 1
}

export function installMode({ standalone, hasPrompt, userAgent, maxTouchPoints }: InstallEnvironment): InstallMode {
  if (standalone) return 'installed'
  if (hasPrompt) return 'prompt'
  if (isIos(userAgent, maxTouchPoints)) return 'ios'
  return 'unavailable'
}
