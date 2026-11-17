import { useResource } from '../../../shared/hooks/useResource'
import type { AdminStats } from '../types'

export function AdminOverviewTab() {
  const stats = useResource<AdminStats>('/admin/stats')
  if (stats.loading) return <p>Cargando estadísticas…</p>
  if (stats.error || !stats.data) return <div role="alert" className="workspace-notice">{stats.error || 'Sin datos.'}<button className="button button--ghost" onClick={stats.reload}>Reintentar</button></div>
  const data = stats.data
  const peak = Math.max(1, ...data.lastSixMonths.map(month => month.practices + month.simulations))
  const kpis: [string, number][] = [['Usuarios registrados', data.users], ['Activos (30 días)', data.activeUsers], ['Con plan Premium', data.premiumUsers], ['Prácticas este mes', data.practicesThisMonth], ['Simulaciones este mes', data.simulationsThisMonth]]
  return <>
    <div className="admin-kpis">{kpis.map(([label, value]) => <article className="panel-card admin-kpi" key={label}><span>{label}</span><strong>{value}</strong></article>)}</div>
    <section className="panel-card"><h2>Uso de los últimos 6 meses</h2>
      <div className="admin-chart" role="img" aria-label="Prácticas y simulaciones por mes">
        {data.lastSixMonths.map(month => <div className="admin-chart__col" key={month.month}>
          <div className="admin-chart__bars">
            <span className="admin-chart__bar admin-chart__bar--practice" style={{ height: `${month.practices / peak * 100}%` }} title={`${month.practices} prácticas`} />
            <span className="admin-chart__bar admin-chart__bar--simulation" style={{ height: `${month.simulations / peak * 100}%` }} title={`${month.simulations} simulaciones`} />
          </div>
          <small>{month.month}</small><small className="admin-muted">{month.practices} / {month.simulations}</small>
        </div>)}
      </div>
      <p className="admin-legend"><span className="admin-dot admin-dot--practice" /> Prácticas <span className="admin-dot admin-dot--simulation" /> Simulaciones</p>
    </section>
    <div className="admin-two-col">
      <section className="panel-card"><h2>Ejercicios más usados</h2>
        {data.topExercises.length === 0 ? <p className="admin-muted">Aún no hay prácticas registradas.</p> :
          <ol className="admin-rank">{data.topExercises.map(item => <li key={item.title}><span>{item.title}</span><strong>{item.sessions}</strong></li>)}</ol>}
      </section>
      <section className="panel-card"><h2>Dificultades frecuentes</h2>
        {data.frequentDifficulties.length === 0 ? <p className="admin-muted">Aún no hay evaluaciones completadas.</p> :
          <ol className="admin-rank">{data.frequentDifficulties.map(item => <li key={item.code}><span>{item.label}<small className="admin-muted"> · {item.evaluations} evaluaciones</small></span><strong>{item.averageScore}</strong></li>)}</ol>}
        <p className="admin-muted">Criterios de evaluación con el promedio más bajo entre todos los usuarios.</p>
      </section>
    </div>
  </>
}
