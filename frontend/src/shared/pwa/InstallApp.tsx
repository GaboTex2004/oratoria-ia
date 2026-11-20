import { useState } from 'react'
import { createPortal } from 'react-dom'
import { Icon } from '../components/Icon'
import { useInstallApp } from './useInstallApp'
import { useOnline } from './useOnline'
import './pwa.css'

const DISMISS_KEY = 'oratoria.install-dismissed'

function readDismissed() {
  try { return localStorage.getItem(DISMISS_KEY) === '1' } catch { return false }
}

function saveDismissed() {
  try { localStorage.setItem(DISMISS_KEY, '1') } catch { /* sin almacenamiento: se vuelve a ofrecer */ }
}

function IosSteps() {
  return <ol className="install-steps">
    <li>Abre OratoriaAI en <strong>Safari</strong>.</li>
    <li>Toca <Icon name="ios_share" /> <strong>Compartir</strong>.</li>
    <li>Elige <strong>«Agregar a inicio»</strong> y confirma.</li>
  </ol>
}

/** Tarjeta flotante que ofrece instalar la app una vez; se puede descartar. */
export function InstallAppBanner() {
  const { mode, install } = useInstallApp()
  const online = useOnline()
  const [dismissed, setDismissed] = useState(readDismissed)
  if (dismissed || !online || (mode !== 'prompt' && mode !== 'ios')) return null
  const close = () => { saveDismissed(); setDismissed(true) }
  const installNow = async () => { if (!await install()) close() }
  // Se monta al final de <body> para que con teclado se recorra después del contenido de la página.
  return createPortal(<aside className="install-banner" aria-label="Instalar aplicación">
    <img src="/icons/icon-192.png" alt="" width="44" height="44" />
    <div className="install-banner__copy">
      <strong>Instala OratoriaAI</strong>
      {mode === 'prompt'
        ? <span>Ábrela desde tu escritorio o pantalla de inicio, en su propia ventana.</span>
        : <span>En Safari toca <Icon name="ios_share" /> <strong>Compartir</strong> y luego <strong>«Agregar a inicio»</strong>.</span>}
    </div>
    <div className="install-banner__actions">
      {mode === 'prompt' && <button className="button button--primary" type="button" onClick={() => { void installNow() }}>Instalar</button>}
      <button className="install-banner__later" type="button" onClick={close}>{mode === 'prompt' ? 'Ahora no' : 'Entendido'}</button>
    </div>
  </aside>, document.body)
}

/** Botón del menú lateral: sigue disponible aunque se haya descartado la tarjeta. */
export function InstallAppButton() {
  const { mode, install } = useInstallApp()
  const [showSteps, setShowSteps] = useState(false)
  if (mode !== 'prompt' && mode !== 'ios') return null
  return <>
    <button className="sidebar-link sidebar-link--button" type="button" aria-expanded={mode === 'ios' ? showSteps : undefined}
      onClick={() => mode === 'prompt' ? void install() : setShowSteps(value => !value)}>
      <Icon name="install_mobile" /><span>Instalar aplicación</span>
    </button>
    {mode === 'ios' && showSteps && <div className="install-sidebar-steps"><IosSteps /></div>}
  </>
}

export function OfflineBanner() {
  const online = useOnline()
  // La región viva queda siempre montada: los lectores de pantalla anuncian el cambio de texto.
  return <div className={`offline-banner ${online ? '' : 'is-visible'}`} role="status">
    {!online && <><Icon name="cloud_off" /> Sin conexión: practicar y guardar necesitan internet.</>}
  </div>
}
