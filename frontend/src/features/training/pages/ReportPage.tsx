import { Link, useParams } from 'react-router-dom'
import { WorkspaceLayout } from '../../../shared/components/WorkspaceLayout'
import { useResource } from '../../../shared/hooks/useResource'
import type { SpeechAnalysis, SpeechEvaluation, TrainingSession } from '../types/training'

export function ReportPage() {
  const { id } = useParams()
  const session = useResource<TrainingSession>(`/sessions/${id}`)
  const analysis = useResource<SpeechAnalysis>(`/sessions/${id}/analysis`)
  const evaluation = useResource<SpeechEvaluation>(`/sessions/${id}/evaluation`)
  const busy = session.loading || analysis.loading || evaluation.loading
  return <WorkspaceLayout><article className="panel-card report-content">
    <header><span className="eyebrow">OratoriaAI · Informe individual</span><h1>{session.data?.exerciseTitle || 'Resultados de práctica'}</h1>
      {session.data && <p>{new Date(session.data.createdAt).toLocaleString('es-BO')} · {session.data.trainingType}</p>}
    </header>
    <div className="workspace-actions no-print"><button disabled={busy || !session.data} className="button button--primary" onClick={() => window.print()}>Imprimir / guardar PDF</button><Link className="button button--ghost" to="/progress">Ver evolución</Link></div>
    {busy && <p role="status">Cargando informe…</p>}
    {session.error && <p role="alert">{session.error}</p>}
    <h2>Transcripción y métricas</h2>
    {analysis.error && <p role="alert">{analysis.error}</p>}
    {analysis.data?.status !== 'COMPLETED' ? <p>El análisis no está completado.</p> : <>
      <blockquote>{analysis.data.transcriptText || 'Sin palabras reconocidas.'}</blockquote>
      <p>Palabras: {analysis.data.metrics?.wordCount ?? '—'} · WPM: {analysis.data.metrics?.wordsPerMinute ?? '—'} · Muletillas: {analysis.data.metrics?.fillerWordsTotal ?? '—'}</p>
      <p>Duración decodificada: {analysis.data.metrics?.recordingDurationSeconds ?? '—'} s.</p>
    </>}
    <h2>Evaluación del discurso</h2>
    {evaluation.error && <p role="alert">{evaluation.error}</p>}
    {evaluation.data?.status === 'COMPLETED' ? <>
      <p>{evaluation.data.executiveSummary}</p><p>Puntuación: {evaluation.data.overallScore ?? 'Sin evidencia suficiente'} / 100</p>
      {evaluation.data.criteriaScores.map(criterion => <section key={criterion.code}><h3>{criterion.label}: {criterion.score ?? '—'}</h3><p>{criterion.justification}</p></section>)}
      <h3>Recomendaciones</h3><ul>{evaluation.data.recommendations.map(item => <li key={item.title}><strong>{item.title}.</strong> {item.description}</li>)}</ul>
    </> : <p>La evaluación no está completada.</p>}
    <p>Indicadores educativos orientativos. La transcripción y la evaluación automática pueden contener errores; revisa la evidencia antes de interpretar las puntuaciones.</p>
  </article></WorkspaceLayout>
}
