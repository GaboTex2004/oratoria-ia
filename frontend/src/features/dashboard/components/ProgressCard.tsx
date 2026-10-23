import { Icon } from '../../../shared/components/Icon'
import type { ProgressOverview } from '../../progress/types/progress'

function valueOrDash(value: number | null): string {
  return value == null ? '—' : Number.isInteger(value) ? String(value) : value.toFixed(1)
}

function deltaLabel(value: number | null, inverse = false): { text: string; tone: string } {
  if (value == null) return { text: 'Sin comparación', tone: 'neutral' }
  const good = inverse ? value < 0 : value > 0
  const bad = inverse ? value > 0 : value < 0
  return { text: `${value > 0 ? '+' : ''}${value.toFixed(1)}`, tone: good ? 'good' : bad ? 'bad' : 'neutral' }
}

export function ProgressCard({ overview }: { overview: ProgressOverview }) {
  const scoreDelta = deltaLabel(overview.scoreDelta)
  const fillerDelta = deltaLabel(overview.fillerDelta, true)
  return (
    <section className="panel-card progress-card">
      <h2><Icon name="trending_up" /> Continuar mejorando</h2>
      <div className="score-summary"><div><span>Última puntuación válida</span><strong>{valueOrDash(overview.latestOverallScore)}{overview.latestOverallScore != null && <small>/100</small>}</strong><small className={`dashboard-delta dashboard-delta--${scoreDelta.tone}`}>{scoreDelta.text}</small></div><div className="score-ring"><Icon name="emoji_events" /></div></div>
      <dl className="stat-list">
        <div><dt><Icon name="analytics" /> Promedio</dt><dd>{valueOrDash(overview.averageOverallScore)}</dd></div>
        <div><dt><Icon name="history" /> Sesiones</dt><dd>{overview.totalSessions}</dd></div>
        <div><dt><Icon name="speed" /> Último PPM</dt><dd>{valueOrDash(overview.latestWordsPerMinute)}</dd></div>
        <div><dt><Icon name="record_voice_over" /> Últimas muletillas</dt><dd><span>{valueOrDash(overview.latestFillerWords)}</span> <small className={`dashboard-delta dashboard-delta--${fillerDelta.tone}`}>{fillerDelta.text}</small></dd></div>
      </dl>
    </section>
  )
}
