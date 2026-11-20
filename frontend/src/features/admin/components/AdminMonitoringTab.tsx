import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, apiRequest } from '../../../shared/services/api'
import { errorText } from '../types'
import './monitoring.css'

type Severity = 'NORMAL' | 'ATENCION' | 'CRITICO'
interface Metric { key: string; label: string; value: number | string | boolean; unit: string | null; group: string }
interface Alert { code: string; severity: Severity; message: string }
interface Event { occurredAt: string; action: string; detail: string | null }
interface Snapshot { generatedAt: string; status: Severity; metrics: Metric[]; alerts: Alert[]; events: Event[] }
interface Finding { title: string; detail: string; evidence: string[] }
interface Analysis { status: Severity; summary: string; findings: Finding[]; recommendations: string[]; answer: string | null; summaryFromRules: boolean; provider: string; model: string }
interface AnalysisResponse { snapshotAt: string; ruleStatus: Severity; analysis: Analysis; metricsUsed: Record<string, Metric['value']> }

const REFRESH_MS = 10000
const STATUS_TEXT: Record<Severity, string> = { NORMAL: 'Todo en orden', ATENCION: 'Requiere atención', CRITICO: 'Problema crítico' }
const time = (iso: string) => new Date(iso).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' })

function formatValue(metric: Metric) {
  if (metric.value === 'UP') return 'Operativo'
  if (metric.value === 'DOWN') return 'Sin respuesta'
  return `${metric.value}${metric.unit ? ` ${metric.unit}` : ''}`
}

/** Panel en tiempo real: cifras calculadas por el backend y, a pedido, un diagnóstico de la IA local (Qwen). */
export function AdminMonitoringTab() {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null)
  const [error, setError] = useState('')
  const [live, setLive] = useState(true)
  const [question, setQuestion] = useState('')
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(null)
  const [analysisError, setAnalysisError] = useState('')
  const [thinking, setThinking] = useState(false)

  const latest = useRef(0)

  // Solo se aplica la respuesta más reciente: un refresco viejo nunca pisa a uno nuevo.
  const load = useCallback(async () => {
    const id = ++latest.current
    try {
      const next = await apiRequest<Snapshot>('/admin/monitoring')
      if (id === latest.current) { setSnapshot(next); setError('') }
    } catch (reason) {
      if (id !== latest.current) return
      setError(errorText(reason, 'No se pudo obtener el estado de la plataforma.'))
      // Sesión vencida o sin permiso: no tiene sentido seguir consultando.
      if (reason instanceof ApiError && (reason.status === 401 || reason.status === 403)) setLive(false)
    }
  }, [])

  useEffect(() => {
    // La siguiente consulta se programa al terminar la anterior: nunca se acumulan peticiones colgadas.
    let timer = 0
    let active = true
    const tick = async () => {
      if (document.visibilityState === 'visible') await load()
      if (active && live) timer = window.setTimeout(tick, REFRESH_MS)
    }
    timer = window.setTimeout(tick, 0)
    const onVisible = () => { if (document.visibilityState === 'visible' && live) { window.clearTimeout(timer); timer = window.setTimeout(tick, 0) } }
    document.addEventListener('visibilitychange', onVisible)
    return () => { active = false; window.clearTimeout(timer); document.removeEventListener('visibilitychange', onVisible) }
  }, [live, load])

  async function analyze(event: FormEvent) {
    event.preventDefault()
    setThinking(true); setAnalysisError('')
    try {
      setAnalysis(await apiRequest<AnalysisResponse>('/admin/monitoring/analysis', {
        method: 'POST', body: JSON.stringify({ question: question.trim() || null }),
      }))
    } catch (reason) { setAnalysisError(errorText(reason, 'El modelo local no pudo generar el análisis.')) }
    finally { setThinking(false) }
  }

  const groups = snapshot ? [...new Set(snapshot.metrics.map(metric => metric.group))] : []
  const labelOf = (key: string) => snapshot?.metrics.find(metric => metric.key === key)?.label ?? key

  return <>
    <section className={`panel-card monitor-status monitor-status--${(snapshot?.status ?? 'NORMAL').toLowerCase()}`}>
      <div>
        <span className="eyebrow">Monitoreo en tiempo real</span>
        <h2 aria-live="polite">{snapshot ? STATUS_TEXT[snapshot.status] : 'Cargando estado…'}</h2>
        <p className="admin-muted">{snapshot ? `Actualizado a las ${time(snapshot.generatedAt)}` : ''}{live ? ` · se actualiza cada ${REFRESH_MS / 1000} s` : ' · pausado'}</p>
      </div>
      <div className="workspace-actions">
        <button type="button" className="button button--ghost" onClick={() => setLive(value => !value)}>{live ? 'Pausar' : 'Reanudar'}</button>
        <button type="button" className="button button--ghost" onClick={() => { void load() }}>Actualizar ahora</button>
      </div>
    </section>
    {error && <p role="alert" className="workspace-notice">{error}</p>}

    {snapshot && <section className="panel-card">
      <h2>Alertas</h2>
      {snapshot.alerts.length === 0
        ? <p className="admin-muted">Sin alertas: todas las reglas están dentro de lo normal.</p>
        : <ul className="monitor-alerts">{snapshot.alerts.map(alert => <li key={alert.code} className={`monitor-alert monitor-alert--${alert.severity.toLowerCase()}`}>
            <strong>{alert.severity === 'CRITICO' ? 'Crítico' : 'Atención'}</strong><span>{alert.message}</span>
          </li>)}</ul>}
    </section>}

    <form className="panel-card workspace-form monitor-ai" onSubmit={analyze}>
      <div className="panel-card__heading"><h2>Asistente de monitoreo (IA local)</h2></div>
      <p className="admin-muted">El modelo local de la plataforma lee estas mismas cifras y explica qué pasa y qué revisar. No usa servicios en la nube ni recibe datos personales. Puede tardar unos segundos y se ejecuta solo cuando lo pides, para no competir con las prácticas.</p>
      <label>Pregunta (opcional)<input maxLength={300} value={question} onChange={event => setQuestion(event.target.value)} placeholder="¿Por qué hay análisis trabados?" /></label>
      <div className="workspace-actions"><button className="button button--primary" disabled={thinking}>{thinking ? 'Analizando con la IA local…' : 'Analizar estado'}</button></div>
      {analysisError && <p role="alert" className="workspace-notice">{analysisError}</p>}
      {analysis && <div className={`monitor-analysis monitor-analysis--${analysis.analysis.status.toLowerCase()}`} role="status">
        <p className="monitor-analysis__meta">{STATUS_TEXT[analysis.analysis.status]} · datos de las {time(analysis.snapshotAt)} · modelo {analysis.analysis.model}</p>
        <p>{analysis.analysis.summary}</p>
        {analysis.analysis.summaryFromRules && <p className="monitor-analysis__meta">El modelo minimizó las alertas activas, así que este resumen se armó directamente con ellas.</p>}
        {analysis.analysis.answer && <p className="monitor-analysis__answer"><strong>Respuesta:</strong> {analysis.analysis.answer}</p>}
        {analysis.analysis.findings.length > 0 && <><h3>Hallazgos</h3><ul className="monitor-findings">{analysis.analysis.findings.map((finding, index) => <li key={index}>
          <strong>{finding.title}</strong><span>{finding.detail}</span>
          <small>Basado en: {finding.evidence.map(key => `${labelOf(key)} = ${String(analysis.metricsUsed[key])}`).join(' · ')}</small>
        </li>)}</ul></>}
        {analysis.analysis.recommendations.length > 0 && <><h3>Qué revisar</h3><ol>{analysis.analysis.recommendations.map((item, index) => <li key={index}>{item}</li>)}</ol></>}
      </div>}
    </form>

    {snapshot && <div className="monitor-groups">{groups.map(group => <section key={group} className="panel-card">
      <h2>{group}</h2>
      <dl className="monitor-metrics">{snapshot.metrics.filter(metric => metric.group === group).map(metric => <div key={metric.key}
        className={metric.value === 'DOWN' ? 'monitor-metric monitor-metric--down' : 'monitor-metric'}>
        <dt>{metric.label}</dt><dd>{formatValue(metric)}</dd>
      </div>)}</dl>
    </section>)}</div>}

    {snapshot && <section className="panel-card">
      <h2>Eventos recientes</h2>
      {snapshot.events.length === 0 ? <p className="admin-muted">Sin eventos relevantes.</p>
        : <ul className="admin-rank">{snapshot.events.map(item => <li key={`${item.occurredAt}-${item.action}`}><span>{item.action}</span><strong>{time(item.occurredAt)}</strong></li>)}</ul>}
    </section>}
  </>
}
