import { useResource } from '../../shared/hooks/useResource'
import { WorkspaceLayout } from '../../shared/components/WorkspaceLayout'

interface Plan { code: string; name: string; entitlements: { code: string; enabled: boolean; limit: number | null; unlimited: boolean }[] }
const labels: Record<string, string> = { SESSIONS_PER_MONTH: 'Prácticas por mes', SIMULATIONS_PER_MONTH: 'Simulaciones por mes', HISTORY_DAYS: 'Días de historial configurados' }

export function PlansPage() {
  const { data, loading, error, reload } = useResource<Plan[]>('/plans')
  return <WorkspaceLayout>
    <header><span className="eyebrow">Suscripciones</span><h1>Planes disponibles</h1><p>Las cuotas se calculan por sesiones creadas durante el mes UTC.</p></header>
    {loading && <p role="status">Cargando planes…</p>}
    {error && <div role="alert"><p>{error}</p><button className="button button--primary" onClick={reload}>Reintentar</button></div>}
    <div className="training-grid">{data?.map(plan => <article className="panel-card" key={plan.code}>
      <h2>{plan.name}</h2><ul>{plan.entitlements.filter(item => item.code in labels && item.code !== 'HISTORY_DAYS').map(item =>
        <li key={item.code}>{labels[item.code]}: {item.unlimited ? 'Sin límite' : item.limit ?? 'No disponible'}</li>)}</ul>
    </article>)}</div>
    <p className="workspace-notice">Esta versión académica no realiza cobros. El administrador gestiona la asignación de planes. El historial se conserva hasta que solicites eliminar sus datos.</p>
  </WorkspaceLayout>
}
