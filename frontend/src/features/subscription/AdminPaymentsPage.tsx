import { useState } from 'react'
import { Link } from 'react-router-dom'
import { WorkspaceLayout } from '../../shared/components/WorkspaceLayout'
import { useResource } from '../../shared/hooks/useResource'
import { apiRequest } from '../../shared/services/api'
import { BillingNotice } from './BillingNotice'
import { date, money, scenarios, statusLabels, type Receipt, type Subscription } from './billing'

interface UserRow { id: string; email: string; name: string }
export function AdminPaymentsPage() {
  const [page, setPage] = useState(0)
  const [userPage, setUserPage] = useState(0)
  const operations = useResource<Receipt[]>(`/admin/payments?page=${page}`)
  const users = useResource<UserRow[]>(`/admin/users?page=${userPage}`)
  const [target, setTarget] = useState('')
  const [days, setDays] = useState(32)
  const [scenario, setScenario] = useState<string>('DEMO_APPROVED')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  async function execute(path: string, body: object, clock = false) {
    if (busy) return
    setBusy(true); setError(''); setMessage('')
    try {
      const result = await apiRequest<Subscription | Receipt>(path, { method: 'POST', body: JSON.stringify(body) })
      setMessage(clock && 'effectiveNow' in result ? `Reloj actualizado: ${date(result.effectiveNow)}. Plan ${result.plan}; ${statusLabels[result.status]}.` : 'Confirmación registrada por el servidor.')
      operations.reload()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'No se pudo ejecutar la operación de prueba.') }
    finally { setBusy(false) }
  }
  return <WorkspaceLayout><BillingNotice /><header><h1>Administración de pagos simulados</h1><p>Las operaciones se autorizan y auditan en el servidor. El avance temporal solo está permitido en desarrollo/pruebas.</p></header>
    <nav className="billing-nav"><Link to="/admin">Administración general</Link><button className="button button--secondary" onClick={operations.reload}>Actualizar operaciones</button></nav>
    {(error || operations.error || users.error) && <p role="alert">{error || operations.error || users.error}</p>}
    {message && <p role="status">{message}</p>}
    <section className="billing-record"><h2>Simular paso del tiempo</h2><p>Avanza el reloj de facturación del usuario seleccionado. No cambia el reloj del sistema ni borra datos. Las renovaciones automáticas resultantes son ficticias.</p>
      <form className="billing-controls" onSubmit={event => { event.preventDefault(); void execute(`/admin/payments/users/${target}/advance`, { days, renewalScenario: scenario }, true) }}>
        <label>Usuario de prueba<select required value={target} onChange={event => setTarget(event.target.value)}><option value="">Seleccionar usuario</option>{users.data?.map(user => <option key={user.id} value={user.id}>{user.name} · {user.email}</option>)}</select></label>
        <label>Días a avanzar<input type="number" min={1} max={400} required value={days} onChange={event => setDays(Number(event.target.value))} /></label>
        <label>Resultado de renovación<select value={scenario} onChange={event => setScenario(event.target.value)}>{scenarios.map(item => <option key={item.id} value={item.id}>{item.title}</option>)}</select></label>
        <button className="button button--primary" disabled={busy || !target}>Avanzar tiempo de prueba</button>
      </form>
      <div className="billing-nav"><button className="button button--secondary" disabled={userPage === 0} onClick={() => { setTarget(''); setUserPage(value => value - 1) }}>Usuarios anteriores</button><span>Página de usuarios {userPage + 1}</span><button className="button button--secondary" disabled={!users.data || users.data.length < 50} onClick={() => { setTarget(''); setUserPage(value => value + 1) }}>Más usuarios</button></div>
    </section>
    <h2>Operaciones globales</h2>{operations.loading && <p>Cargando operaciones…</p>}
    {operations.data?.length === 0 && <p>No hay operaciones de pago.</p>}
    {operations.data?.map(receipt => <article className="billing-record" key={receipt.order.id}>
      <h3>{statusLabels[receipt.order.status]} · {money(receipt.order.amount, receipt.order.currency)}</h3>
      <p className="billing-reference">Orden {receipt.order.id}</p><p className="billing-reference">Usuario {receipt.order.userId}</p>
      <p>{date(receipt.order.createdAt)} · {receipt.order.kind}</p>
      {receipt.transaction && <p className="billing-reference">Transacción {receipt.transaction.id} · {receipt.transaction.scenario}</p>}
      {receipt.order.status === 'PENDING' && receipt.transaction?.status === 'PENDING' && <div className="billing-nav">
        <button className="button button--primary" disabled={busy} onClick={() => void execute(`/admin/payments/orders/${receipt.order.id}/confirm`, { decision: 'APPROVE' })}>Confirmar pago pendiente</button>
        <button className="button button--secondary" disabled={busy} onClick={() => void execute(`/admin/payments/orders/${receipt.order.id}/confirm`, { decision: 'REJECT' })}>Rechazar pago pendiente</button>
      </div>}
      <details><summary>Eventos de auditoría de la orden</summary><ul>{receipt.events.map((event, index) => <li key={`${event.event_type}-${index}`}>{date(event.created_at)} · {event.event_type}</li>)}</ul></details>
    </article>)}
    <nav className="billing-nav" aria-label="Páginas de operaciones"><button className="button button--secondary" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</button><span>Página {page + 1}</span><button className="button button--secondary" disabled={!operations.data || operations.data.length < 50} onClick={() => setPage(value => value + 1)}>Siguiente</button></nav>
  </WorkspaceLayout>
}
