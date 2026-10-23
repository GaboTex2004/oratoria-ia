import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { Icon } from '../../../shared/components/Icon'
import { ApiError } from '../../../shared/services/api'
import { useAuth } from '../../auth/context/useAuth'
import { Sidebar } from '../../dashboard/components/Sidebar'
import { Topbar } from '../../dashboard/components/Topbar'
import '../../dashboard/components/dashboard.css'
import {
  ANALYSIS_STATUS_META,
  EVALUATION_STATUS_META,
  SESSION_STATUS_META,
  TRAINING_TYPE_META,
  formatDuration,
} from '../../training/types/training'
import { MetricChart } from '../components/MetricChart'
import '../components/progress.css'
import { progressService } from '../services/progressService'
import { CRITERION_META, type ProgressSummary } from '../types/progress'
import {
  buildHistorySeries,
  compareSessions,
  formatMetricValue,
  formatSignedDifference,
} from '../utils/progressMetrics'

function formatDate(iso: string | null): string {
  if (!iso) return 'Sin fecha'
  return new Intl.DateTimeFormat('es-BO', { dateStyle: 'medium' }).format(new Date(iso))
}

function numberOrDash(value: number | null, suffix = ''): string {
  if (value == null) return '—'
  return `${Number.isInteger(value) ? value : value.toFixed(2)}${suffix}`
}

function Delta({ value, inverse = false }: { value: number | null; inverse?: boolean }) {
  if (value == null) return <span className="metric-delta metric-delta--neutral">Sin comparación</span>
  const favorable = inverse ? value < 0 : value > 0
  const unfavorable = inverse ? value > 0 : value < 0
  return (
    <span className={`metric-delta ${favorable ? 'metric-delta--good' : unfavorable ? 'metric-delta--bad' : 'metric-delta--neutral'}`}>
      {value > 0 ? '+' : ''}{value.toFixed(2)} respecto a la anterior
    </span>
  )
}

export function ProgressPage() {
  const [menuOpen, setMenuOpen] = useState(false)
  const firstName = useAuth().user?.firstName || ''
  const [summary, setSummary] = useState<ProgressSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadCount, setReloadCount] = useState(0)
  const [leftId, setLeftId] = useState('')
  const [rightId, setRightId] = useState('')

  useEffect(() => {
    let active = true
    progressService.getSummary()
      .then((data) => {
        if (!active) return
        setSummary(data)
        if (data.history.length >= 2) {
          setLeftId((current) => current || data.history.at(-2)?.sessionId || '')
          setRightId((current) => current || data.history.at(-1)?.sessionId || '')
        } else if (data.history.length === 1) {
          setLeftId(data.history[0].sessionId)
        }
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof ApiError ? reason.message : 'No se pudo cargar tu progreso.')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [reloadCount])

  const leftSession = summary?.history.find((session) => session.sessionId === leftId) || null
  const rightSession = summary?.history.find((session) => session.sessionId === rightId) || null
  const comparison = useMemo(
    () => leftSession && rightSession ? compareSessions(leftSession, rightSession) : [],
    [leftSession, rightSession],
  )

  function retry() {
    setLoading(true)
    setError('')
    setReloadCount((value) => value + 1)
  }

  const history = summary?.history || []
  const scoreSeries = buildHistorySeries(history, (item) => item.overallScore)
  const wpmSeries = buildHistorySeries(history, (item) => item.wordsPerMinute)
  const fillerSeries = buildHistorySeries(history, (item) => item.fillerWordsTotal)

  return (
    <div className="dashboard-layout">
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} subscription={summary?.subscription} />
      <main className="dashboard-main">
        <Topbar firstName={firstName} onMenuOpen={() => setMenuOpen(true)} />
        <div className="dashboard-content progress-page">
          <header className="progress-hero">
            <div><span className="eyebrow">Analítica personal</span><h2>Tu progreso en oratoria</h2><p>Explora tendencias reales de tus prácticas, habilidades y comunicación verbal.</p></div>
            <Link className="button button--primary" to="/training"><Icon name="mic" /> Nueva práctica</Link>
          </header>

          {error && <div className="progress-alert" role="alert"><Icon name="cloud_off" /><span>{error}</span><button type="button" onClick={retry}>Reintentar</button></div>}
          {loading && <div className="progress-skeleton" aria-label="Cargando progreso">{Array.from({ length: 6 }, (_, index) => <span key={index} />)}</div>}

          {!loading && !error && summary && summary.overview.totalSessions === 0 && (
            <section className="panel-card progress-empty">
              <Icon name="query_stats" /><h3>Tu progreso comenzará con tu primera práctica</h3>
              <p>Graba una sesión para obtener métricas de ritmo, muletillas y evaluación por habilidades.</p>
              <Link className="button button--primary" to="/training">Comenzar entrenamiento</Link>
            </section>
          )}

          {!loading && !error && summary && summary.overview.totalSessions > 0 && (
            <>
              <section className="progress-kpis" aria-label="Resumen general">
                <article><Icon name="library_books" /><span>Sesiones</span><strong>{summary.overview.totalSessions}</strong><small>{summary.overview.completedSessions} completadas</small></article>
                <article><Icon name="timer" /><span>Tiempo practicado</span><strong>{formatDuration(summary.overview.totalPracticeSeconds)}</strong><small>duración acumulada</small></article>
                <article><Icon name="workspace_premium" /><span>Última puntuación</span><strong>{numberOrDash(summary.overview.latestOverallScore, '/100')}</strong><Delta value={summary.overview.scoreDelta} /></article>
                <article><Icon name="analytics" /><span>Promedio válido</span><strong>{numberOrDash(summary.overview.averageOverallScore, '/100')}</strong><small>{summary.overview.evaluatedSessions} evaluaciones terminadas</small></article>
                <article><Icon name="speed" /><span>WPM promedio</span><strong>{numberOrDash(summary.overview.averageWordsPerMinute)}</strong><small>palabras por minuto</small></article>
                <article><Icon name="record_voice_over" /><span>Muletillas promedio</span><strong>{numberOrDash(summary.overview.averageFillerWords)}</strong><Delta value={summary.overview.fillerDelta} inverse /></article>
              </section>

              <section className="progress-chart-grid">
                <article className="panel-card progress-chart-card progress-chart-card--wide">
                  <header><div><span className="eyebrow">Rendimiento</span><h3>Evolución de puntuación</h3></div><strong>{scoreSeries.length}/{history.length} sesiones evaluables</strong></header>
                  <MetricChart points={scoreSeries} unit="/100" emptyMessage="Aún no hay evaluaciones con evidencia suficiente." />
                </article>
                <article className="panel-card progress-chart-card">
                  <header><div><span className="eyebrow">Ritmo</span><h3>Palabras por minuto</h3></div></header>
                  <MetricChart points={wpmSeries} unit=" PPM" color="#7c3aed" emptyMessage="Aún no existen análisis de ritmo completos." />
                </article>
                <article className="panel-card progress-chart-card">
                  <header><div><span className="eyebrow">Fluidez</span><h3>Muletillas detectadas</h3></div></header>
                  <MetricChart points={fillerSeries} color="#d05a00" emptyMessage="Aún no existen análisis de muletillas completos." />
                </article>
              </section>

              <section className="progress-section">
                <header><span className="eyebrow">Habilidades</span><h2>Evolución por criterio</h2></header>
                <div className="skills-grid">
                  {summary.criteriaEvolution.map((criterion) => {
                    const meta = CRITERION_META[criterion.code]
                    return <article className="panel-card skill-card" key={criterion.code} style={{ '--skill-color': meta.color } as CSSProperties}>
                      <header><div><span>{meta.label}</span><strong>{numberOrDash(criterion.latestValue, '/100')}</strong></div><Delta value={criterion.delta} /></header>
                      <p>Promedio histórico: <b>{numberOrDash(criterion.averageValue, '/100')}</b></p>
                      <MetricChart points={criterion.series.map((point) => ({ sessionId: point.sessionId, label: formatDate(point.completedAt), value: point.score }))} color={meta.color} unit="/100" emptyMessage="Sin datos suficientes." />
                    </article>
                  })}
                </div>
              </section>

              <section className="insights-grid">
                <article className="panel-card insight-card"><h2><Icon name="verified" /> Fortalezas recientes</h2>{summary.insights.recentStrengths.length ? <ul>{summary.insights.recentStrengths.map((item) => <li key={item}>{item}</li>)}</ul> : <p>Sin fortalezas evaluadas todavía.</p>}</article>
                <article className="panel-card insight-card"><h2><Icon name="target" /> Oportunidades de mejora</h2>{summary.insights.recentAreasForImprovement.length ? <ul>{summary.insights.recentAreasForImprovement.map((item) => <li key={item}>{item}</li>)}</ul> : <p>Sin oportunidades evaluadas todavía.</p>}</article>
                <article className="panel-card insight-card"><h2><Icon name="record_voice_over" /> Muletillas frecuentes</h2>{summary.insights.frequentFillers.length ? <div className="filler-cloud">{summary.insights.frequentFillers.map((item) => <span key={item.filler}>{item.filler}<b>{item.count}</b></span>)}</div> : <p>No se detectaron muletillas acumuladas.</p>}</article>
              </section>

              <section className="panel-card comparator-card">
                <header><div><span className="eyebrow">Comparador</span><h2>Sesión frente a sesión</h2><p>La diferencia se calcula como sesión B menos sesión A. El ritmo se muestra sin juzgarlo como mejora.</p></div></header>
                {history.length < 2 ? <div className="comparison-empty"><Icon name="compare_arrows" /><p>Realiza otra práctica para comparar tu evolución.</p></div> : <>
                  <div className="comparison-selectors">
                    <label>Sesión A<select value={leftId} onChange={(event) => setLeftId(event.target.value)}>{history.map((session) => <option key={session.sessionId} value={session.sessionId} disabled={session.sessionId === rightId}>{session.exerciseTitle || TRAINING_TYPE_META[session.trainingType].label} · {formatDate(session.createdAt)}</option>)}</select></label>
                    <Icon name="compare_arrows" />
                    <label>Sesión B<select value={rightId} onChange={(event) => setRightId(event.target.value)}>{history.map((session) => <option key={session.sessionId} value={session.sessionId} disabled={session.sessionId === leftId}>{session.exerciseTitle || TRAINING_TYPE_META[session.trainingType].label} · {formatDate(session.createdAt)}</option>)}</select></label>
                  </div>
                  <div className="comparison-table" role="table" aria-label="Comparación de sesiones">
                    <div className="comparison-row comparison-row--head" role="row"><span>Métrica</span><span>Sesión A</span><span>Sesión B</span><span>Diferencia</span></div>
                    {comparison.map((item) => <div className="comparison-row" role="row" key={item.key}><strong>{item.label}</strong><span>{formatMetricValue(item.left, item.unit)}</span><span>{formatMetricValue(item.right, item.unit)}</span><span className={`comparison-difference comparison-difference--${item.trend}`}>{formatSignedDifference(item.difference, item.unit)}</span></div>)}
                  </div>
                </>}
              </section>

              <section className="progress-section history-section">
                <header><div><span className="eyebrow">Cronología</span><h2>Historial analítico</h2></div>{summary.historyTruncated && <p>Mostrando las últimas {summary.historyLimit} sesiones; los totales generales incluyen todo el historial.</p>}</header>
                <div className="progress-history-list">
                  {[...history].reverse().map((session) => {
                    const typeMeta = TRAINING_TYPE_META[session.trainingType]
                    const sessionMeta = SESSION_STATUS_META[session.sessionStatus]
                    const analysisMeta = session.analysisStatus ? ANALYSIS_STATUS_META[session.analysisStatus] : null
                    const evaluationMeta = session.evaluationStatus ? EVALUATION_STATUS_META[session.evaluationStatus] : null
                    return <article className="panel-card progress-history-item" key={session.sessionId}>
                      <span className="training-card__icon"><Icon name={typeMeta.icon} /></span>
                      <div><div className="history-badges"><span className={`status-pill status-pill--${sessionMeta.tone}`}>{sessionMeta.label}</span>{analysisMeta && <span className={`status-pill status-pill--${analysisMeta.tone}`}>{analysisMeta.label}</span>}{evaluationMeta && <span className={`status-pill status-pill--${evaluationMeta.tone}`}>{evaluationMeta.label}</span>}</div><h3>{session.exerciseTitle || typeMeta.label}</h3><p>{formatDate(session.createdAt)} · {formatDuration(session.durationSeconds)} · {session.overallScore == null ? 'Sin puntuación válida' : `${session.overallScore.toFixed(1)}/100`}</p></div>
                      <Link className="button button--primary" to={`/results?sessionId=${encodeURIComponent(session.sessionId)}`}>Ver resultados</Link>
                    </article>
                  })}
                </div>
              </section>
            </>
          )}
        </div>
      </main>
    </div>
  )
}
