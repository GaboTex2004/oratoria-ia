import { useSyncExternalStore } from 'react'
import { getInstallMode, promptInstall, subscribeInstall } from './pwa'

export function useInstallApp() {
  const mode = useSyncExternalStore(subscribeInstall, getInstallMode, () => 'unavailable' as const)
  return { mode, install: promptInstall }
}
