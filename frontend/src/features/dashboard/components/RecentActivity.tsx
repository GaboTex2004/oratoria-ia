import { Icon } from '../../../shared/components/Icon'
import { recentSessions } from '../data/dashboardMock'

export function RecentActivity() {
  return (
    <section className="panel-card recent-card">
      <div className="panel-card__heading"><h2>Actividad reciente</h2><button type="button">Ver todo</button></div>
      <div className="recent-list">
        {recentSessions.map((session) => (
          <article key={session.id}><span className="recent-list__icon"><Icon name={session.icon} /></span><div><strong>{session.title}</strong><small>{session.date}</small></div><b>{session.score}</b></article>
        ))}
      </div>
    </section>
  )
}
