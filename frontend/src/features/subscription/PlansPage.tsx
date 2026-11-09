import { useResource } from '../../shared/hooks/useResource'
import { WorkspaceLayout } from '../../shared/components/WorkspaceLayout'
import { Link, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { apiRequest } from '../../shared/services/api'
import { useAuth } from '../auth/context/useAuth'
import { BillingNotice } from './BillingNotice'
import { money, type Plan, type Receipt, type Subscription } from './billing'

const labels: Record<string, string> = { SESSIONS_PER_MONTH: 'Prácticas por mes', SIMULATIONS_PER_MONTH: 'Simulaciones por mes', HISTORY_DAYS: 'Días de historial configurados' }

export function PlansPage() {
  const { data, loading, error, reload } = useResource<Plan[]>('/plans')
  const current = useResource<Subscription>('/subscriptions/current')
  const [busy, setBusy] = useState(false)
  const [checkoutError, setCheckoutError] = useState('')
  const [key] = useState(() => crypto.randomUUID())
  const navigate = useNavigate()
  const { user } = useAuth()
  async function checkout() {
    if (busy) return
    setBusy(true); setCheckoutError('')
    try {
      const order = await apiRequest<Receipt>('/payments/orders', { method: 'POST', headers: { 'Idempotency-Key': key } })
      navigate(`/checkout/${order.order.id}`)
    } catch (cause) { setCheckoutError(cause instanceof Error ? cause.message : 'No se pudo crear la orden.') }
    finally { setBusy(false) }
  }
  return <WorkspaceLayout>
    <BillingNotice />
    <header><span className="eyebrow">Suscripciones</span><h1>Planes disponibles</h1><p>Las cuotas se calculan por sesiones creadas durante el mes UTC.</p></header>
    {loading && <p role="status">Cargando planes…</p>}
    {error && <div role="alert"><p>{error}</p><button className="button button--primary" onClick={reload}>Reintentar</button></div>}
    {(checkoutError || current.error) && <p role="alert">{checkoutError || current.error}</p>}
    <nav className="billing-nav"><Link to="/subscription">Mi suscripción e historial</Link>{user?.role === 'ADMIN' && <Link to="/admin/payments">Administrar simulador</Link>}</nav>
    <div className="training-grid">{data?.map(plan => <article className="panel-card" key={plan.code}>
      <h2>{plan.name}</h2><p className="billing-price">{money(plan.price, plan.currency)}{plan.code === 'PREMIUM' ? ' / mes' : ''}</p><ul>{plan.entitlements.filter(item => item.code in labels && item.code !== 'HISTORY_DAYS').map(item =>
        <li key={item.code}>{labels[item.code]}: {item.unlimited ? 'Sin límite' : item.limit ?? 'No disponible'}</li>)}</ul>
      {plan.code === 'PREMIUM' && <button className="button button--primary" disabled={busy || !current.data?.simulatorEnabled || current.data?.plan === 'PREMIUM' || !!current.data?.pendingStatus} onClick={() => void checkout()}>{busy ? 'Creando orden…' : current.data?.plan === 'PREMIUM' ? 'Tu plan actual' : 'Seleccionar Premium'}</button>}
    </article>)}</div>
    {!current.loading && !current.data?.simulatorEnabled && <p>El checkout simulado está deshabilitado en este entorno.</p>}
    {current.data?.pendingStatus && <p>Hay un checkout pendiente. Puedes continuarlo desde Mi suscripción e historial.</p>}
    <p className="workspace-notice">La renovación y los pagos son simulados. La cancelación conserva el periodo pagado, el historial y las grabaciones.</p>
  </WorkspaceLayout>
}
