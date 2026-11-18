import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/context/useAuth'
import { useResource } from '../../../shared/hooks/useResource'
import { apiRequest } from '../../../shared/services/api'
import { errorText } from '../types'
import { dateTime, downloadFile, formatBytes } from './adminDownload'

interface Backup {
  id: string; createdAt: string; createdBy: string | null; fileName: string; sizeBytes: number
  tables: number; rows: number; schemaVersion: string; note: string | null
}
interface RestoreResult { tables: number; rows: number }

const CONFIRMATION = 'RESTAURAR'

export function AdminBackupsTab() {
  const backups = useResource<Backup[]>('/admin/backups')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [restoring, setRestoring] = useState<Backup | 'file' | null>(null)
  const [confirm, setConfirm] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [restored, setRestored] = useState(false)
  const { logout } = useAuth()
  const navigate = useNavigate()

  async function run(action: () => Promise<string>) {
    setBusy(true); setError(''); setMessage('')
    try { const text = await action(); setMessage(text); if (!text.startsWith('Restauración')) backups.reload() }
    catch (reason) { setError(errorText(reason, 'No se pudo completar la operación.')) }
    finally { setBusy(false) }
  }

  function create(event: FormEvent) {
    event.preventDefault()
    return run(async () => {
      const backup = await apiRequest<Backup>('/admin/backups', { method: 'POST', body: JSON.stringify({ note }) })
      setNote('')
      return `Respaldo generado: ${backup.rows} filas de ${backup.tables} tablas.`
    })
  }

  function remove(backup: Backup) {
    if (!window.confirm(`¿Eliminar el respaldo del ${dateTime(backup.createdAt)}? No se puede deshacer.`)) return
    return run(async () => { await apiRequest(`/admin/backups/${backup.id}`, { method: 'DELETE' }); return 'Respaldo eliminado.' })
  }

  function openRestore(target: Backup | 'file') { setRestoring(target); setConfirm(''); setFile(null); setError(''); setMessage('') }

  function restore(event: FormEvent) {
    event.preventDefault()
    if (!restoring || restored) return
    return run(async () => {
      let result: RestoreResult
      if (restoring === 'file') {
        if (!file) throw new Error('Selecciona un archivo de respaldo.')
        const form = new FormData(); form.append('file', file); form.append('confirm', confirm)
        result = await apiRequest<RestoreResult>('/admin/backups/restore', { method: 'POST', body: form })
      } else {
        result = await apiRequest<RestoreResult>(`/admin/backups/${restoring.id}/restore`, { method: 'POST', body: JSON.stringify({ confirm }) })
      }
      setRestoring(null)
      setRestored(true)
      return `Restauración completa: ${result.rows} filas en ${result.tables} tablas. Antes se guardó un respaldo automático del estado anterior. Por seguridad se cerraron todas las sesiones, incluida la tuya.`
    })
  }

  return <>
    <section className="panel-card">
      <h2>Respaldos de la base de datos</h2>
      <p className="admin-muted">Un respaldo guarda usuarios, suscripciones, ejercicios, sesiones, evaluaciones y configuración. Las grabaciones de audio se guardan aparte, en el almacenamiento de archivos, y no se incluyen: al restaurar, los audios de prácticas posteriores al respaldo quedan en el servidor sin su sesión.</p>
      <form className="workspace-form" onSubmit={create}>
        <label>Nota (opcional)<input maxLength={200} value={note} onChange={event => setNote(event.target.value)} placeholder="Ej.: antes de la presentación" /></label>
        <div className="workspace-actions">
          <button className="button button--primary" disabled={busy}>{busy && !restoring ? 'Procesando…' : 'Generar respaldo ahora'}</button>
          <button type="button" className="button button--ghost" disabled={busy} onClick={() => openRestore('file')}>Restaurar desde un archivo</button>
        </div>
      </form>
    </section>
    {error && <p role="alert" className="workspace-notice">{error}</p>}
    {message && <div role="status" className="workspace-notice">{message}
      {restored && <div className="workspace-actions"><button className="button button--primary" onClick={() => { logout(); navigate('/login') }}>Iniciar sesión de nuevo</button></div>}</div>}

    {restoring && <form className="panel-card admin-danger workspace-form" onSubmit={restore}>
      <h2>Restaurar {restoring === 'file' ? 'desde un archivo' : `el respaldo del ${dateTime(restoring.createdAt)}`}</h2>
      <p><strong>Se reemplazarán todos los datos actuales</strong> por los del respaldo. Antes de restaurar se generará automáticamente un respaldo del estado actual, por si necesitas volver atrás.</p>
      {restoring === 'file' && <label>Archivo de respaldo (.json)<input type="file" accept="application/json,.json" required onChange={event => setFile(event.target.files?.[0] ?? null)} /></label>}
      <label>Escribe {CONFIRMATION} para confirmar<input value={confirm} onChange={event => setConfirm(event.target.value)} autoComplete="off" /></label>
      <div className="workspace-actions">
        <button className="button button--danger" disabled={busy || confirm !== CONFIRMATION}>{busy ? 'Restaurando…' : 'Restaurar'}</button>
        <button type="button" className="button button--ghost" disabled={busy} onClick={() => setRestoring(null)}>Cancelar</button>
      </div>
    </form>}

    <section className="panel-card">
      <h2>Respaldos guardados</h2>
      {backups.loading && <p role="status">Cargando respaldos…</p>}
      {backups.error && <p role="alert">{backups.error}</p>}
      {backups.data?.length === 0 && <p>Todavía no hay respaldos.</p>}
      {!!backups.data?.length && <div className="workspace-table-wrap"><table className="workspace-table"><thead><tr><th>Fecha</th><th>Creado por</th><th>Nota</th><th>Contenido</th><th>Acciones</th></tr></thead>
        <tbody>{backups.data.map(backup => <tr key={backup.id}>
          <td>{dateTime(backup.createdAt)}</td><td>{backup.createdBy ?? '—'}</td><td>{backup.note ?? '—'}</td>
          <td><small>{backup.rows} filas · {backup.tables} tablas · {formatBytes(backup.sizeBytes)} · esquema v{backup.schemaVersion}</small></td>
          <td><div className="admin-row-actions">
            <button className="button button--ghost" disabled={busy} onClick={() => run(async () => { await downloadFile(`/admin/backups/${backup.id}/download`, backup.fileName); return 'Descarga iniciada.' })}>Descargar</button>
            <button className="button button--ghost" disabled={busy} onClick={() => openRestore(backup)}>Restaurar</button>
            <button className="button button--ghost" disabled={busy} onClick={() => void remove(backup)}>Eliminar</button>
          </div></td>
        </tr>)}</tbody></table></div>}
    </section>
  </>
}
