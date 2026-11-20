import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ApiError, apiRequest } from '../../../shared/services/api'
import { Icon } from '../../../shared/components/Icon'
import { useAuth } from '../../auth/context/useAuth'
import { stepsFor } from '../monitoringSteps'
import './monitorWidget.css'

type Severity = 'NORMAL' | 'ATENCION' | 'CRITICO'
interface Alert { code: string; severity: Severity; message: string }
interface Snapshot { generatedAt: string; status: Severity; alerts: Alert[] }

const REFRESH_MS = 15000
const STATUS_TEXT: Record<Severity, string> = { NORMAL: 'Todo en orden', ATENCION: 'Requiere atención', CRITICO: 'Problema crítico' }

/** Monitoreo siempre visible para el administrador: consulta el estado en segundo plano y, si aparece una alerta nueva, se abre con los pasos a seguir. */
export function MonitorWidget() {
  const { user } = useAuth()
  const { pathname, search } = useLocation()
  const isAdmin = user?.role === 'ADMIN'
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null)
  const [offline, setOffline] = useState(false)
  const [open, setOpen] = useState(false)
  const seen = useRef<Set<string> | null>(null)

  useEffect(() => {
    if (!isAdmin) return
    let timer = 0
    let active = true
    const tick = async () => {
      if (document.visibilityState === 'visible') {
        try {
          const next = await apiRequest<Snapshot>('/admin/monitoring')
          if (!active) return
          setSnapshot(next); setOffline(false)
          const codes = new Set(next.alerts.map(alert => alert.code))
          // La primera lectura solo registra lo que ya había; después, cualquier alerta nueva abre el panel.
          if (seen.current && [...codes].some(code => !seen.current?.has(code))) setOpen(true)
          seen.current = codes
        } catch (reason) {
          if (!active) return
          if (reason instanceof ApiError && (reason.status === 401 || reason.status === 403)) return
          setOffline(true)
        }
      }
      if (active) timer = window.setTimeout(tick, REFRESH_MS)
    }
    timer = window.setTimeout(tick, 0)
    return () => { active = false; window.clearTimeout(timer) }
  }, [isAdmin])

  if (!isAdmin) return null
  const status = offline ? 'CRITICO' : snapshot?.status ?? 'NORMAL'
  const alerts = snapshot?.alerts ?? []

  return <div className="monitor-widget no-print">
    {open && <section className="monitor-widget__panel" role="dialog" aria-label="Monitoreo de la plataforma">
      <header>
        <strong>{offline ? 'Sin conexión con el servidor' : snapshot ? STATUS_TEXT[snapshot.status] : 'Revisando…'}</strong>
        <button type="button" aria-label="Cerrar" onClick={() => setOpen(false)}><Icon name="close" /></button>
      </header>
      {offline && <ol><li>Revisa tu conexión a internet.</li><li>Si tienes conexión, revisa que el backend esté encendido (docker compose ps).</li></ol>}
      {!offline && snapshot && alerts.length === 0 && <p>Todos los servicios funcionan. Se revisa solo cada {REFRESH_MS / 1000} segundos.</p>}
      {!offline && alerts.map(alert => <div key={alert.code} className={`monitor-widget__alert monitor-widget__alert--${alert.severity.toLowerCase()}`}>
        <p>{alert.message}</p>
        <ol>{stepsFor(alert.code).map(step => <li key={step}>{step}</li>)}</ol>
      </div>)}
      {!(pathname === '/admin' && new URLSearchParams(search).get('tab') === 'monitoreo')
        ? <Link className="monitor-widget__link" to="/admin?tab=monitoreo" onClick={() => setOpen(false)}>Ver panel completo</Link> : null}
    </section>}
    <button type="button" className={`monitor-widget__button monitor-widget__button--${status.toLowerCase()}`}
      onClick={() => setOpen(value => !value)} aria-expanded={open} title="Monitoreo de la plataforma">
      <Icon name="monitor_heart" />
      <span>{offline ? 'Sin conexión' : alerts.length > 0 ? `${alerts.length} ${alerts.length === 1 ? 'alerta' : 'alertas'}` : 'En orden'}</span>
    </button>
  </div>
}
