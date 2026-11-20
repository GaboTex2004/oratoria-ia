import { useEffect, useState } from 'react'
import { apiRequest } from '../../shared/services/api'
import { disablePush, enablePush, isEnabledHere, pushSupport } from '../../shared/pwa/push'
import '../../shared/pwa/pwa.css'

interface Settings { practiceReady: boolean; dailyReminder: boolean; reminderHour: number; timeZone: string; devices: number }
interface SendSummary { devices: number; delivered: number; removed: number; failed: number }

const SUPPORT_TEXT = {
  'needs-install': 'En iPhone y iPad las notificaciones llegan solo a la app instalada: toca Compartir → «Agregar a inicio» y ábrela desde el ícono.',
  unsupported: 'Este navegador no admite notificaciones push. Prueba con Chrome, Edge, Firefox o Safari actualizados.',
  'no-worker': 'Las notificaciones push funcionan en la versión compilada de la app (Docker o el despliegue), no en el servidor de desarrollo.',
  denied: 'Bloqueaste las notificaciones para este sitio. Habilítalas desde el candado de la barra de direcciones y recarga la página.',
} as const

function message(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}

export function NotificationSettings() {
  const [support] = useState(pushSupport)
  const [settings, setSettings] = useState<Settings | null>(null)
  const [enabledHere, setEnabledHere] = useState(false)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    apiRequest<Settings>('/notifications/settings').then(value => { if (active) setSettings(value) })
      .catch(reason => { if (active) setError(message(reason, 'No se pudo cargar la configuración de notificaciones.')) })
    if (support === 'ready') isEnabledHere().then(value => { if (active) setEnabledHere(value) }).catch(() => undefined)
    return () => { active = false }
  }, [support])

  async function run(action: () => Promise<string>) {
    setBusy(true); setError(''); setNotice('')
    try { setNotice(await action()) }
    catch (reason) { setError(message(reason, 'No se pudo completar la acción.')) }
    finally { setBusy(false) }
  }

  const toggleDevice = () => run(async () => {
    if (enabledHere) { await disablePush(); setEnabledHere(false) }
    else { await enablePush(); setEnabledHere(true) }
    setSettings(await apiRequest<Settings>('/notifications/settings'))
    return enabledHere ? 'Este dispositivo ya no recibirá notificaciones.' : 'Notificaciones activadas en este dispositivo.'
  })

  const save = (next: Settings) => run(async () => {
    setSettings(next)
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || next.timeZone
    setSettings(await apiRequest<Settings>('/notifications/settings', { method: 'PUT', body: JSON.stringify({ ...next, timeZone }) }))
    return 'Preferencias de notificaciones guardadas.'
  })

  const sendTest = () => run(async () => {
    const result = await apiRequest<SendSummary>('/notifications/test', { method: 'POST' })
    return result.delivered > 0
      ? `Notificación enviada a ${result.delivered} dispositivo${result.delivered === 1 ? '' : 's'}. Puede tardar unos segundos.`
      : 'No se pudo entregar la notificación. Desactiva y vuelve a activar las notificaciones en este dispositivo.'
  })

  return <section className="panel-card workspace-form notification-settings" aria-labelledby="notification-settings-title">
    <h2 id="notification-settings-title">Notificaciones</h2>
    <p>Recibe avisos en tu celular o computadora aunque OratoriaAI esté cerrada. Solo viaja el texto del aviso; tus audios y resultados no salen del servidor.</p>
    {error && <p className="training-alert training-alert--error" role="alert">{error}</p>}
    {notice && <p className="workspace-notice" role="status">{notice}</p>}

    {support !== 'ready'
      ? <p className="workspace-notice">{SUPPORT_TEXT[support]}</p>
      : <div className="notification-settings__device">
          <div><strong>Este dispositivo</strong><span>{enabledHere ? 'Recibe notificaciones' : 'Sin activar'}</span></div>
          <button type="button" className={`button ${enabledHere ? 'button--ghost' : 'button--primary'}`} disabled={busy} onClick={toggleDevice}>
            {enabledHere ? 'Desactivar' : 'Activar notificaciones'}
          </button>
        </div>}

    {settings && <fieldset className="notification-settings__options" disabled={busy}>
      <legend>Qué avisos quieres recibir</legend>
      <label className="terms-field"><input type="checkbox" checked={settings.practiceReady} onChange={event => save({ ...settings, practiceReady: event.target.checked })} />
        Cuando el resultado de una práctica esté listo</label>
      <label className="terms-field"><input type="checkbox" checked={settings.dailyReminder} onChange={event => save({ ...settings, dailyReminder: event.target.checked })} />
        Recordatorio diario para practicar (si ese día todavía no practicaste)</label>
      {settings.dailyReminder && <label>Hora del recordatorio
        <select value={settings.reminderHour} onChange={event => save({ ...settings, reminderHour: Number(event.target.value) })}>
          {Array.from({ length: 24 }, (_, hour) => <option key={hour} value={hour}>{String(hour).padStart(2, '0')}:00</option>)}
        </select></label>}
      <p className="notification-settings__devices">Dispositivos con notificaciones activas en tu cuenta: {settings.devices}</p>
    </fieldset>}

    {support === 'ready' && enabledHere && <div className="workspace-actions">
      <button type="button" className="button button--ghost" disabled={busy} onClick={sendTest}>Enviar notificación de prueba</button>
    </div>}
  </section>
}
