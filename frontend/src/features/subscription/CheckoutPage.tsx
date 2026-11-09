import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { WorkspaceLayout } from '../../shared/components/WorkspaceLayout'
import { useResource } from '../../shared/hooks/useResource'
import { apiRequest } from '../../shared/services/api'
import { useAuth } from '../auth/context/useAuth'
import { BillingNotice } from './BillingNotice'
import { ReceiptDetails } from './ReceiptDetails'
import { scenarios, money, type Receipt } from './billing'

export function CheckoutPage() {
  const { id } = useParams()
  const request = useResource<Receipt>(`/payments/orders/${id}`)
  const [result, setResult] = useState<Receipt | null>(null)
  const [scenario, setScenario] = useState<string>('DEMO_APPROVED')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const { refresh } = useAuth()
  const receipt = result?.order.id === id ? result : request.data?.order.id === id ? request.data : null
  async function pay() {
    if (busy) return
    setBusy(true); setError('')
    try {
      const response = await apiRequest<Receipt>(`/payments/orders/${id}/process`, { method: 'POST', body: JSON.stringify({ scenario }) })
      setResult(response); await refresh()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'No se pudo procesar el pago de prueba.') }
    finally { setBusy(false) }
  }
  return <WorkspaceLayout><BillingNotice />
    <header><span className="eyebrow">Checkout académico</span><h1>Pago de prueba</h1><p>No se solicitan números completos de tarjeta, vencimiento ni CVV.</p></header>
    <nav className="billing-nav"><Link to="/plans">Planes</Link><Link to="/subscription">Mi suscripción e historial</Link></nav>
    {(error || request.error) && <p role="alert">{error || request.error}</p>}
    {request.loading && <p role="status">Consultando orden…</p>}
    {receipt && <>
      {!receipt.transaction ? <section className="panel-card"><h2>Premium mensual</h2>
        <p className="billing-price">{money(receipt.order.amount, receipt.order.currency)} / mes</p>
        <p>Renovación automática simulada. Puedes cancelarla y conservar Premium hasta el vencimiento del periodo pagado.</p>
        <fieldset className="billing-cards" disabled={busy}><legend>Selecciona una tarjeta ficticia</legend>
          {scenarios.map(card => <label className="billing-card" key={card.id}>
            <span><input aria-label={card.title} type="radio" name="scenario" value={card.id} checked={scenario === card.id} onChange={() => setScenario(card.id)} /> {card.title}</span>
            <strong>VISA ···· {card.suffix}</strong><span>{card.detail}</span>
          </label>)}
        </fieldset>
        <button className="button button--primary" disabled={busy} onClick={() => void pay()}>{busy ? 'Procesando en el servidor…' : 'Confirmar pago simulado'}</button>
      </section> : <>
        <p role="status">{receipt.transaction.status === 'APPROVED' ? 'Pago simulado aprobado. Premium fue activado por el servidor.' : receipt.transaction.status === 'REJECTED' ? 'Pago simulado rechazado. No se activó Premium.' : 'Pago pendiente. Premium no se activa hasta la confirmación de un administrador de pruebas.'}</p>
        <ReceiptDetails receipt={receipt} />
        <button className="button button--secondary billing-actions" disabled={busy} onClick={() => { setResult(null); request.reload(); void refresh() }}>Actualizar estado</button>
      </>}
    </>}
  </WorkspaceLayout>
}
