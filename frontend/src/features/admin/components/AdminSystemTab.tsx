import { useState, type FormEvent } from 'react'
import { useResource } from '../../../shared/hooks/useResource'
import { apiRequest } from '../../../shared/services/api'
import { MODULE_LABELS, errorText, type ModuleStatus } from '../types'

type Settings = Record<string, string>

function SettingsForm({ initial, onSaved }: { initial: Settings; onSaved: () => void }) {
  const [draft, setDraft] = useState<Settings>(initial)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try { await apiRequest('/admin/settings', { method: 'PUT', body: JSON.stringify(draft) }); onSaved() }
    catch (reason) { setError(errorText(reason, 'No se pudieron guardar los parámetros.')) }
    finally { setBusy(false) }
  }

  return <form className="panel-card workspace-form" onSubmit={save}><h2>Parámetros generales</h2>
    {error && <p role="alert" className="workspace-notice">{error}</p>}
    <label className="terms-field"><input type="checkbox" checked={draft['registration.enabled'] === 'true'} onChange={event => setDraft({ ...draft, 'registration.enabled': String(event.target.checked) })} />Permitir el registro de nuevas cuentas</label>
    <label>Duración máxima de una grabación (segundos)<input type="number" min={15} max={600} required value={draft['audio.max-duration-seconds'] || ''} onChange={event => setDraft({ ...draft, 'audio.max-duration-seconds': event.target.value })} /></label>
    <label>Aviso para todos los usuarios<textarea maxLength={300} placeholder="Déjalo vacío para no mostrar ningún aviso." value={draft['announcement.message'] || ''} onChange={event => setDraft({ ...draft, 'announcement.message': event.target.value })} /></label>
    <div className="workspace-actions"><button className="button button--primary" disabled={busy}>Guardar parámetros</button></div>
  </form>
}

export function AdminSystemTab() {
  const health = useResource<ModuleStatus[]>('/admin/system/health')
  const settings = useResource<Settings>('/admin/settings')
  const [saved, setSaved] = useState(false)

  return <>
    <section className="panel-card"><div className="panel-card__heading"><h2>Estado de los módulos</h2><button type="button" onClick={health.reload} disabled={health.loading}>Actualizar</button></div>
      {health.loading ? <p>Comprobando módulos…</p> : health.error ? <p role="alert">{health.error}</p> :
        <ul className="admin-health">{health.data?.map(item => <li key={item.module} className={`admin-health__item admin-health__item--${item.status.toLowerCase()}`}>
          <strong>{MODULE_LABELS[item.module] || item.module}</strong>
          <span className="admin-health__badge">{item.status === 'UP' ? 'Operativo' : 'Sin respuesta'}</span>
          <small>{item.detail} · {item.latencyMs} ms</small>
        </li>)}</ul>}
    </section>
    {saved && <p role="status" className="workspace-notice">Parámetros guardados.</p>}
    {!settings.data && settings.loading ? <p>Cargando parámetros…</p> : settings.error ? <div role="alert" className="workspace-notice">{settings.error}<button className="button button--ghost" onClick={settings.reload}>Reintentar</button></div>
      : settings.data && <SettingsForm key={JSON.stringify(settings.data)} initial={settings.data} onSaved={() => { setSaved(true); settings.reload() }} />}
  </>
}
