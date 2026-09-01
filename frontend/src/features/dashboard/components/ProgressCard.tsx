import { Icon } from '../../../shared/components/Icon'
import { dashboardStats } from '../data/dashboardMock'

export function ProgressCard() {
  return (
    <section className="panel-card progress-card">
      <h2><Icon name="trending_up" /> Continuar mejorando</h2>
      <div className="score-summary"><div><span>Última puntuación</span><strong>{dashboardStats.score}<small>/100</small></strong></div><div className="score-ring"><Icon name="emoji_events" /></div></div>
      <dl className="stat-list">
        <div><dt><Icon name="history" /> Sesiones</dt><dd>{dashboardStats.sessions}</dd></div>
        <div><dt><Icon name="speed" /> PPM (Ritmo)</dt><dd>{dashboardStats.wordsPerMinute}</dd></div>
        <div><dt><Icon name="record_voice_over" /> Muletillas</dt><dd className="danger">{dashboardStats.fillerWords} <Icon name="arrow_downward" /></dd></div>
      </dl>
    </section>
  )
}
