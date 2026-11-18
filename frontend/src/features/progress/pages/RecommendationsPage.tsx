import { Link } from 'react-router-dom'
import { WorkspaceLayout } from '../../../shared/components/WorkspaceLayout'
import { useResource } from '../../../shared/hooks/useResource'
import { LEVEL_LABELS, STYLE_NAMES } from '../../../shared/labels'
import { formatDuration, type Exercise } from '../../training/types/training'
import '../../../shared/components/personalization.css'

interface FocusArea { code: string; label: string; average: number; lowScores: number; evaluations: number }
interface Recommendation {
  ruleVersion: string; adaptive: boolean; category: string; categoryName: string; suggestedDifficulty: string
  reason: string; ruleDescription: string; basedOn: string[]; focusAreas: FocusArea[]; exercises: Exercise[]
  styleExercise: Exercise | null; upgradeHint: string | null
}

function ExerciseCard({ exercise, note }: { exercise: Exercise; note?: string }) {
  return <article className="panel-card style-card">
    {note && <span className="eyebrow">{note}</span>}
    <h2>{exercise.title}</h2>
    <p className="muted">{exercise.category} · {LEVEL_LABELS[exercise.difficulty] ?? exercise.difficulty} · {formatDuration(exercise.recommendedDurationSeconds)}</p>
    <p>{exercise.description}</p>
    <Link className="button button--primary" to={`/training?exerciseId=${exercise.id}`}>Preparar práctica</Link>
  </article>
}

export function RecommendationsPage() {
  const { data, loading, error, reload } = useResource<Recommendation>('/recommendations')
  return <WorkspaceLayout><header><span className="eyebrow">Tu siguiente práctica</span><h1>Entrenamiento adaptativo</h1>
    <p>Las recomendaciones se recalculan con cada evaluación nueva y con los objetivos de tu <Link to="/profile">perfil</Link>.</p></header>
    {loading && <p role="status">Consultando tus resultados…</p>}
    {error && <div role="alert"><p>{error}</p><button className="button button--primary" onClick={reload}>Reintentar</button></div>}
    {data && <>
      {!data.adaptive && data.upgradeHint && <section className="premium-lock" role="note"><span className="material-symbols-outlined" aria-hidden="true">workspace_premium</span>
        <div><strong>Estás viendo la recomendación básica</strong><p>{data.upgradeHint}</p></div><Link className="button button--primary" to="/plans">Ver planes</Link></section>}
      <section className="panel-card">
        <div className="kpi-grid">
          <div><span>Área sugerida</span><strong>{data.categoryName}</strong></div>
          <div><span>Nivel sugerido</span><strong>{LEVEL_LABELS[data.suggestedDifficulty] ?? data.suggestedDifficulty}</strong></div>
        </div>
        <h2>Por qué te lo recomendamos</h2>
        <ul className="reason-list">{data.basedOn.map(item => <li key={item}>{item}</li>)}</ul>
      </section>
      {data.focusAreas.length > 0 && <section className="panel-card"><h2>Tus criterios en las últimas evaluaciones</h2>
        <ul className="criteria-bars">{data.focusAreas.map(area => <li key={area.code}>
          <div className="criteria-bars__head"><span>{area.label}</span><strong>{Math.round(area.average)}/100</strong></div>
          <div className={`meter ${area.average < 60 ? 'meter--low' : area.average >= 75 ? 'meter--ok' : ''}`}><span style={{ width: `${Math.min(100, area.average)}%` }} /></div>
          <small>{area.lowScores > 0 ? `Por debajo de 60 en ${area.lowScores} de ${area.evaluations} evaluaciones.` : `${area.evaluations} evaluaciones sin puntajes bajos.`}</small>
        </li>)}</ul>
      </section>}
      <div className="card-grid">
        {data.exercises.map((exercise, index) => <ExerciseCard key={exercise.id} exercise={exercise} note={index === 0 ? 'Recomendado primero' : undefined} />)}
        {data.styleExercise && <ExerciseCard exercise={data.styleExercise}
          note={`Para tu estilo ${STYLE_NAMES[data.styleExercise.communicationStyle ?? ''] ?? ''}`.trim()} />}
      </div>
      {!data.exercises.length && <p>No hay ejercicios activos para esta área. Consulta el <Link to="/training">catálogo general</Link>.</p>}
      <small className="muted">{data.ruleDescription} (reglas {data.ruleVersion})</small>
    </>}
  </WorkspaceLayout>
}
