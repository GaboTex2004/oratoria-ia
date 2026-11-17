import { useState } from 'react'
import { useResource } from '../../../shared/hooks/useResource'
import { apiRequest } from '../../../shared/services/api'
import { ENTITLEMENT_LABELS, errorText, type EntitlementRow, type PlanAdminView } from '../types'

function PlanEditor({ plan, onSaved }: { plan: PlanAdminView; onSaved: (code: string) => void }) {
  const [draft, setDraft] = useState(plan)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  function entitlement(code: string, values: Partial<EntitlementRow>) {
    setDraft(previous => ({ ...previous, entitlements: previous.entitlements.map(item => item.code === code ? { ...item, ...values } : item) }))
  }
  async function save() {
    setBusy(true); setError('')
    try {
      await apiRequest(`/admin/plans/${plan.code}`, { method: 'PUT', body: JSON.stringify({
        name: draft.name, price: draft.price, active: draft.active,
        entitlements: draft.entitlements.map(({ code, enabled, limit, unlimited }) => ({ code, enabled, limit, unlimited })),
      }) })
      onSaved(plan.code)
    } catch (reason) { setError(errorText(reason, 'No se pudo guardar el plan.')) } finally { setBusy(false) }
  }
  const limits = draft.entitlements.filter(item => item.kind === 'LIMIT')
  const features = draft.entitlements.filter(item => item.kind === 'FEATURE')
  return <section className="panel-card workspace-form admin-plan">
    <h2>Plan {plan.code}</h2>
    {error && <p role="alert" className="workspace-notice">{error}</p>}
    <label>Nombre visible<input required maxLength={100} value={draft.name} onChange={event => setDraft({ ...draft, name: event.target.value })} /></label>
    <label>Precio mensual ({draft.currency})<input type="number" min={0} step="0.01" disabled={plan.code === 'FREE'} value={draft.price} onChange={event => setDraft({ ...draft, price: Number(event.target.value) })} /></label>
    {plan.code !== 'FREE' && <label className="terms-field"><input type="checkbox" checked={draft.active} onChange={event => setDraft({ ...draft, active: event.target.checked })} />Plan disponible para contratar</label>}
    <h3>Límites de uso</h3>
    {limits.map(item => <div className="admin-limit" key={item.code}>
      <span>{ENTITLEMENT_LABELS[item.code] || item.code}</span>
      <input type="number" min={0} aria-label={`Valor de ${ENTITLEMENT_LABELS[item.code] || item.code}`} disabled={item.unlimited} value={item.limit ?? ''} onChange={event => entitlement(item.code, { limit: event.target.value === '' ? null : Number(event.target.value) })} />
      <label className="terms-field"><input type="checkbox" checked={item.unlimited} onChange={event => entitlement(item.code, { unlimited: event.target.checked, limit: event.target.checked ? null : item.limit ?? 0 })} />Ilimitado</label>
    </div>)}
    <h3>Funciones incluidas</h3>
    <div className="admin-features">{features.map(item => <label className="terms-field" key={item.code}><input type="checkbox" checked={item.enabled} onChange={event => entitlement(item.code, { enabled: event.target.checked })} />{ENTITLEMENT_LABELS[item.code] || item.code}</label>)}</div>
    <div className="workspace-actions"><button className="button button--primary" type="button" disabled={busy} onClick={save}>Guardar plan</button><button className="button button--ghost" type="button" disabled={busy} onClick={() => setDraft(plan)}>Descartar cambios</button></div>
  </section>
}

export function AdminPlansTab() {
  const plans = useResource<PlanAdminView[]>('/admin/plans')
  const [saved, setSaved] = useState('')
  if (!plans.data && plans.loading) return <p>Cargando planes…</p>
  if (plans.error) return <div role="alert" className="workspace-notice">{plans.error}<button className="button button--ghost" onClick={plans.reload}>Reintentar</button></div>
  return <>
    {saved && <p role="status" className="workspace-notice">Plan {saved} guardado.</p>}
    <p className="workspace-notice">Los cambios se aplican de inmediato a todos los usuarios del plan. Las prácticas ya creadas este mes siguen contando para la cuota.</p>
    <div className="admin-two-col">{plans.data?.map(plan => <PlanEditor key={JSON.stringify(plan)} plan={plan} onSaved={code => { setSaved(code); plans.reload() }} />)}</div>
  </>
}
