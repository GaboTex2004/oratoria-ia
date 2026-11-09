import { Link } from 'react-router-dom'
import { WorkspaceLayout } from '../../../shared/components/WorkspaceLayout'
import { useResource } from '../../../shared/hooks/useResource'
import type { Exercise } from '../../training/types/training'

interface Recommendation { category: string; suggestedDifficulty: string; reason: string; ruleDescription: string; exercises: Exercise[] }

export function RecommendationsPage() {
  const { data, loading, error, reload } = useResource<Recommendation>('/recommendations')
  return <WorkspaceLayout><header><span className="eyebrow">Tu siguiente práctica</span><h1>Entrenamiento adaptativo</h1></header>
    {loading && <p role="status">Consultando tus resultados…</p>}
    {error && <div role="alert"><p>{error}</p><button className="button button--primary" onClick={reload}>Reintentar</button></div>}
    {data && <><section className="workspace-notice"><p>{data.reason}</p><p>Nivel sugerido: {data.suggestedDifficulty}. Área: {data.category}.</p><small>{data.ruleDescription}</small></section>
      <div className="training-grid">{data.exercises.map(exercise => <article className="panel-card" key={exercise.id}><h2>{exercise.title}</h2><p>{exercise.description}</p><Link className="button button--primary" to={`/training?exerciseId=${exercise.id}`}>Preparar práctica</Link></article>)}</div>
      {!data.exercises.length && <p>No hay ejercicios activos para esta área. Consulta el catálogo general.</p>}
    </>}
  </WorkspaceLayout>
}
