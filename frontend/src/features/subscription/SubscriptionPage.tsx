import { useState } from 'react'
import { Link } from 'react-router-dom'
import { WorkspaceLayout } from '../../shared/components/WorkspaceLayout'
import { useResource } from '../../shared/hooks/useResource'
import { apiRequest } from '../../shared/services/api'
import { useAuth } from '../auth/context/useAuth'
import { BillingNotice } from './BillingNotice'
import { date, statusLabels, type Receipt, type Subscription, type Usage } from './billing'

export function SubscriptionPage() {
  const subscription = useResource<Subscription>('/subscriptions/current')
  const [page, setPage] = useState(0)
  const history = useResource<Receipt[]>(`/payments/history?page=${page}`)
  const usage = useResource<Usage>('/subscriptions/current/usage')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const { refresh } = useAuth()
  function reload() { subscription.reload(); history.reload(); usage.reload(); void refresh() }
  async function cancel() {
    setBusy(true); setError('')
    try { await apiRequest('/subscriptions/current/cancel-renewal', { method: 'POST' }); reload() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'No se pudo cancelar la renovación.') }
    finally { setBusy(false) }
  }
  return <WorkspaceLayout><BillingNotice /><header><h1>Mi suscripción</h1><p>Tu historial y tus grabaciones se conservan al cambiar de plan.</p></header>
    <nav className="billing-nav"><Link to="/plans">Comparar planes</Link><button className="button button--secondary" onClick={reload}>Actualizar suscripción</button></nav>
    {(error || subscription.error || history.error || usage.error) && <p role="alert">{error || subscription.error || history.error || usage.error}</p>}
    {subscription.loading && <p role="status">Consultando suscripción…</p>}
    {subscription.data && <section className="billing-record"><h2>Plan {subscription.data.plan}</h2><dl>
      <dt>Estado</dt><dd>{statusLabels[subscription.data.status]}</dd>
      <dt>Inicio</dt><dd>{date(subscription.data.startsAt)}</dd><dt>Vencimiento</dt><dd>{date(subscription.data.endsAt)}</dd>
      <dt>Renovación automática simulada</dt><dd>{subscription.data.autoRenew ? 'Activada' : 'Desactivada'}</dd>
      {subscription.data.graceUntil && <><dt>Fin de gracia</dt><dd>{date(subscription.data.graceUntil)}</dd></>}
      <dt>Reloj de facturación</dt><dd>{date(subscription.data.effectiveNow)} · America/La_Paz</dd>
    </dl>
      {subscription.data.pendingStatus && <p>Tienes una contratación pendiente de pago. Continúa desde el historial.</p>}
      {!subscription.data.simulatorEnabled && <p>El simulador está deshabilitado en este entorno.</p>}
      {subscription.data.autoRenew && subscription.data.demoManaged && subscription.data.simulatorEnabled && <button className="button button--secondary" disabled={busy} onClick={() => void cancel()}>{busy ? 'Cancelando…' : 'Cancelar renovación simulada'}</button>}
      {subscription.data.plan === 'PREMIUM' && !subscription.data.autoRenew && subscription.data.endsAt && <p>Conservarás Premium hasta el vencimiento indicado. Después regresarás a FREE.</p>}
    </section>}
    <section className="billing-record"><h2>Consumo mensual</h2><p>Sesiones creadas en el mes UTC; el reloj de prueba de facturación no reinicia el consumo.</p>
      {usage.data && <ul><li>Prácticas: {usage.data.practices.used} / {usage.data.practices.unlimited ? 'Sin límite' : usage.data.practices.limit}</li><li>Simulaciones: {usage.data.simulations.used} / {usage.data.simulations.unlimited ? 'Sin límite' : usage.data.simulations.limit}</li></ul>}
    </section>
    <section><h2>Historial de pagos de demostración</h2>{history.loading && <p>Cargando historial…</p>}
      {history.data?.length === 0 && <p>Aún no tienes operaciones.</p>}
      {history.data?.map(receipt => <article className="billing-record" key={receipt.order.id}><p>{date(receipt.order.createdAt)} · {statusLabels[receipt.order.status]} · {receipt.order.kind === 'RENEWAL' ? 'Renovación' : 'Contratación'}</p><p className="billing-reference">Orden {receipt.order.id}</p><Link to={`/checkout/${receipt.order.id}`}>{receipt.transaction ? 'Ver comprobante' : 'Continuar checkout'}</Link></article>)}
      <nav className="billing-nav" aria-label="Páginas de pagos"><button className="button button--secondary" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</button><span>Página {page + 1}</span><button className="button button--secondary" disabled={!history.data || history.data.length < 50} onClick={() => setPage(value => value + 1)}>Siguiente</button></nav>
    </section>
  </WorkspaceLayout>
}
