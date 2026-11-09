import type { Receipt } from './billing'
import { date, money, statusLabels } from './billing'

export function ReceiptDetails({ receipt }: { receipt: Receipt }) {
  return <section className="billing-record" aria-label="Comprobante de demostración">
    <h2>Comprobante de demostración</h2><p>Documento de prueba sin valor fiscal. No acredita un cargo bancario.</p>
    <dl><dt>Estado</dt><dd><strong>{statusLabels[receipt.transaction?.status || receipt.order.status]}</strong></dd>
      <dt>Referencia de orden</dt><dd>{receipt.order.id}</dd>
      <dt>Importe simulado</dt><dd>{money(receipt.order.amount, receipt.order.currency)}</dd>
      <dt>Operación</dt><dd>{receipt.order.kind === 'RENEWAL' ? 'Renovación mensual simulada' : 'Suscripción mensual Premium'}</dd>
      <dt>Fecha</dt><dd>{date(receipt.order.createdAt)} · America/La_Paz</dd>
      {receipt.transaction && <><dt>Transacción</dt><dd>{receipt.transaction.id}</dd>
        <dt>Autorización ficticia</dt><dd>{receipt.transaction.authorizationCode || 'No emitida'}</dd>
        <dt>Tarjeta de prueba</dt><dd>{receipt.transaction.scenario}</dd></>}
    </dl>
    <p>{receipt.notice}</p>
    <button className="button button--secondary billing-actions" onClick={() => window.print()}>Imprimir comprobante / PDF</button>
  </section>
}
