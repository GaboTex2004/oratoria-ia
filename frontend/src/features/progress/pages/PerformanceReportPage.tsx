import { useState } from 'react'
import { Link } from 'react-router-dom'
import { WorkspaceLayout } from '../../../shared/components/WorkspaceLayout'
import { useResource } from '../../../shared/hooks/useResource'
import { REPORT_PERIODS, periodLabel } from '../../../shared/personalization'
import { apiBlobRequest } from '../../../shared/services/api'
import { TRAINING_TYPE_META, type TrainingType } from '../../training/types/training'
import '../../../shared/components/personalization.css'

interface PerformanceReport {
  userName: string; plan: string; periodDays: number; periodStart: string | null; generatedAt: string
  totalSessions: number; evaluatedSessions: number; practiceMinutes: number; simulations: number
  averageScore: number | null; bestScore: number | null; scoreChange: number | null
  criteria: { code: string; label: string; average: number; evaluations: number }[]
  strengths: string[]; areasToImprove: string[]
  sessions: { createdAt: string; exercise: string | null; trainingType: string | null; durationSeconds: number | null; score: number | null; wordsPerMinute: number | null; fillerWords: number | null }[]
  nextStep: string
}

const score = (value: number | null) => value == null ? '—' : Math.round(value).toString()

export function PerformanceReportPage() {
  const [days, setDays] = useState(30)
  const report = useResource<PerformanceReport>(`/reports/performance?days=${days}`)
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState('')

  async function download() {
    setDownloading(true); setDownloadError('')
    try {
      const blob = await apiBlobRequest(`/reports/performance.pdf?days=${days}`)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `reporte-oratoria-${new Date().toISOString().slice(0, 10)}.pdf`
      link.click()
      URL.revokeObjectURL(url)
    } catch (reason) { setDownloadError(reason instanceof Error ? reason.message : 'No se pudo generar el PDF.') }
    finally { setDownloading(false) }
  }

  const data = report.data
  return <WorkspaceLayout>
    <header><span className="eyebrow">Reporte de desempeño</span><h1>Tu progreso en un documento</h1>
      <p>Consolida tus prácticas, métricas y evaluaciones del periodo elegido. Descárgalo en PDF para compartirlo.</p></header>
    <section className="panel-card workspace-actions report-period">
      <label className="workspace-form">Periodo<select className="workspace-select" value={days} onChange={event => setDays(Number(event.target.value))}>
        {REPORT_PERIODS.map(period => <option key={period} value={period}>{periodLabel(period)}</option>)}
      </select></label>
      <button className="button button--primary" disabled={downloading || !data} onClick={() => void download()}>{downloading ? 'Generando PDF…' : 'Descargar PDF'}</button>
    </section>
    {report.loading && <p role="status">Preparando el reporte…</p>}
    {(report.error || downloadError) && <div role="alert" className="workspace-notice">{report.error || downloadError}{report.error && <button className="button button--ghost" onClick={report.reload}>Reintentar</button>}</div>}
    {data && <>
      <section className="panel-card"><h2>Resumen · {periodLabel(data.periodDays)}</h2>
        <div className="kpi-grid">
          <div><span>Prácticas</span><strong>{data.totalSessions}</strong></div>
          <div><span>Evaluadas</span><strong>{data.evaluatedSessions}</strong></div>
          <div><span>Minutos de práctica</span><strong>{data.practiceMinutes}</strong></div>
          <div><span>Simulaciones</span><strong>{data.simulations}</strong></div>
          <div><span>Puntaje promedio</span><strong>{score(data.averageScore)}</strong></div>
          <div><span>Mejor puntaje</span><strong>{score(data.bestScore)}</strong></div>
          <div><span>Evolución</span><strong>{data.scoreChange == null ? '—' : `${data.scoreChange > 0 ? '+' : ''}${data.scoreChange}`}</strong></div>
        </div>
      </section>
      <section className="panel-card"><h2>Criterios evaluados</h2>
        {data.criteria.length === 0 ? <p>Todavía no hay evaluaciones con evidencia suficiente en este periodo.</p> : <>
          <ul className="criteria-bars">{data.criteria.map(criterion => <li key={criterion.code}>
            <div className="criteria-bars__head"><span>{criterion.label}</span><strong>{criterion.average}/100</strong></div>
            <div className={`meter ${criterion.average < 60 ? 'meter--low' : criterion.average >= 75 ? 'meter--ok' : ''}`}><span style={{ width: `${Math.min(100, criterion.average)}%` }} /></div>
            <small>{criterion.evaluations} evaluaciones</small>
          </li>)}</ul>
          <p><strong>Fortalezas:</strong> {data.strengths.join(', ') || '—'} · <strong>Aspectos a mejorar:</strong> {data.areasToImprove.join(', ') || '—'}</p>
        </>}
      </section>
      <section className="panel-card"><h2>Sesiones del periodo</h2>
        {data.sessions.length === 0 ? <p>No hay prácticas en este periodo. <Link to="/training">Empieza una</Link>.</p> :
          <div className="workspace-table-wrap"><table className="workspace-table"><thead><tr><th>Fecha</th><th>Ejercicio</th><th>Tipo</th><th>Puntaje</th><th>PPM</th><th>Muletillas</th></tr></thead>
            <tbody>{data.sessions.map((session, index) => <tr key={index}>
              <td>{new Date(session.createdAt).toLocaleDateString('es-BO')}</td><td>{session.exercise ?? 'Práctica libre'}</td>
              <td>{session.trainingType ? TRAINING_TYPE_META[session.trainingType as TrainingType]?.label ?? session.trainingType : '—'}</td>
              <td>{score(session.score)}</td><td>{session.wordsPerMinute == null ? '—' : Math.round(session.wordsPerMinute)}</td><td>{session.fillerWords ?? '—'}</td>
            </tr>)}</tbody></table></div>}
      </section>
      <section className="panel-card"><h2>Siguiente paso recomendado</h2><p>{data.nextStep}</p><Link to="/recommendations">Ver recomendaciones</Link></section>
    </>}
  </WorkspaceLayout>
}
