import { useState, type FormEvent } from 'react'
import { useResource } from '../../../shared/hooks/useResource'
import { dateTime } from './adminDownload'

interface AuditEntry {
  id: string; occurredAt: string; actorEmail: string | null; action: string; actionLabel: string
  entityType: string | null; entityId: string | null; detail: string | null; ipAddress: string | null
}
interface AuditPage { items: AuditEntry[]; total: number; page: number; size: number }
interface Filters { actor: string; action: string; from: string; to: string }

const EMPTY: Filters = { actor: '', action: '', from: '', to: '' }

function query(filters: Filters, page: number) {
  const params = new URLSearchParams({ page: String(page) })
  if (filters.actor.trim()) params.set('actor', filters.actor.trim())
  if (filters.action) params.set('action', filters.action)
  // Date inputs are local days; the end date is inclusive, so the range closes at the start of the next day.
  if (filters.from) params.set('from', new Date(`${filters.from}T00:00:00`).toISOString())
  if (filters.to) { const end = new Date(`${filters.to}T00:00:00`); end.setDate(end.getDate() + 1); params.set('to', end.toISOString()) }
  return `/admin/audit?${params}`
}

export function AdminAuditTab() {
  const actions = useResource<{ code: string; label: string }[]>('/admin/audit/actions')
  const [draft, setDraft] = useState<Filters>(EMPTY)
  const [applied, setApplied] = useState<Filters>(EMPTY)
  const [page, setPage] = useState(0)
  const log = useResource<AuditPage>(query(applied, page))
  const pages = log.data ? Math.max(1, Math.ceil(log.data.total / log.data.size)) : 1

  function apply(event: FormEvent) { event.preventDefault(); setPage(0); setApplied(draft) }
  function clear() { setDraft(EMPTY); setApplied(EMPTY); setPage(0) }

  return <>
    <form className="panel-card admin-filters" onSubmit={apply}>
      <h2>Bitácora de acciones</h2>
      <p className="admin-muted">Inicios de sesión, registros, cambios de contraseña y toda acción administrativa exitosa. Los registros no se borran al restaurar un respaldo.</p>
      <div className="admin-filters__grid">
        <label>Usuario (correo)<input type="email" value={draft.actor} onChange={event => setDraft({ ...draft, actor: event.target.value })} placeholder="usuario@correo.com" /></label>
        <label>Acción<select value={draft.action} onChange={event => setDraft({ ...draft, action: event.target.value })}>
          <option value="">Todas</option>{actions.data?.map(action => <option key={action.code} value={action.code}>{action.label}</option>)}
        </select></label>
        <label>Desde<input type="date" value={draft.from} onChange={event => setDraft({ ...draft, from: event.target.value })} /></label>
        <label>Hasta<input type="date" value={draft.to} onChange={event => setDraft({ ...draft, to: event.target.value })} /></label>
      </div>
      <div className="workspace-actions"><button className="button button--primary">Filtrar</button><button type="button" className="button button--ghost" onClick={clear}>Limpiar</button></div>
    </form>
    {log.error && <p role="alert" className="workspace-notice">{log.error}<button className="button button--ghost" onClick={log.reload}>Reintentar</button></p>}
    <section className="panel-card">
      {log.loading && <p role="status">Cargando bitácora…</p>}
      {log.data && <>
        <p className="admin-muted">{log.data.total} registros</p>
        {log.data.items.length === 0 ? <p>No hay registros con estos filtros.</p> :
          <div className="workspace-table-wrap"><table className="workspace-table"><thead><tr><th>Fecha</th><th>Usuario</th><th>Acción</th><th>Objeto</th><th>Detalle</th><th>IP</th></tr></thead>
            <tbody>{log.data.items.map(entry => <tr key={entry.id}>
              <td>{dateTime(entry.occurredAt)}</td><td>{entry.actorEmail ?? '—'}</td>
              <td><span className={`admin-badge${entry.action === 'LOGIN_FAILED' || entry.action === 'BACKUP_RESTORED' ? ' admin-badge--warn' : ''}`}>{entry.actionLabel}</span></td>
              <td>{entry.entityType ?? '—'}{entry.entityId ? <><br /><small className="admin-muted">{entry.entityId}</small></> : null}</td>
              <td><small>{entry.detail ?? '—'}</small></td><td><small>{entry.ipAddress ?? '—'}</small></td>
            </tr>)}</tbody></table></div>}
        <nav className="workspace-actions" aria-label="Páginas de la bitácora">
          <button className="button button--ghost" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</button>
          <span>Página {page + 1} de {pages}</span>
          <button className="button button--ghost" disabled={page + 1 >= pages} onClick={() => setPage(value => value + 1)}>Siguiente</button>
        </nav>
      </>}
    </section>
  </>
}
