import { useState, type FormEvent } from 'react'
import { apiRequest } from '../../../shared/services/api'
import { errorText } from '../types'

interface SendSummary { devices: number; delivered: number; removed: number; failed: number }

/** Notificación push para todos los dispositivos suscritos; queda registrada en la bitácora. */
export function AdminBroadcastCard() {
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState('')
  const [error, setError] = useState('')

  async function send(event: FormEvent) {
    event.preventDefault()
    if (!window.confirm('¿Enviar esta notificación a todos los dispositivos suscritos?')) return
    setBusy(true); setError(''); setResult('')
    try {
      const summary = await apiRequest<SendSummary>('/admin/notifications/broadcast', { method: 'POST', body: JSON.stringify({ title, body }) })
      setResult(`Enviada a ${summary.delivered} de ${summary.devices} dispositivos.${summary.removed ? ` ${summary.removed} suscripciones vencidas se eliminaron.` : ''}`)
      setTitle(''); setBody('')
    } catch (reason) { setError(errorText(reason, 'No se pudo enviar la notificación.')) }
    finally { setBusy(false) }
  }

  return <form className="panel-card workspace-form" onSubmit={send}>
    <h2>Notificación push a todos</h2>
    <p>Llega a quienes activaron las notificaciones, aunque no tengan la app abierta.</p>
    {error && <p role="alert" className="workspace-notice">{error}</p>}
    {result && <p role="status" className="workspace-notice">{result}</p>}
    <label>Título<input required maxLength={60} value={title} onChange={event => setTitle(event.target.value)} placeholder="Mantenimiento programado" /></label>
    <label>Mensaje<textarea required maxLength={200} value={body} onChange={event => setBody(event.target.value)} placeholder="Hoy a las 22:00 la plataforma estará en mantenimiento por 15 minutos." /></label>
    <div className="workspace-actions"><button className="button button--primary" disabled={busy}>{busy ? 'Enviando…' : 'Enviar notificación'}</button></div>
  </form>
}
