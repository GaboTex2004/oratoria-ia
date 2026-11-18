import { useResource } from '../../shared/hooks/useResource'
import '../../shared/components/personalization.css'

interface StyleFeedback {
  style: string; styleName: string; available: boolean; message: string | null; score: number | null
  observations: string[]; tips: string[]; traits: string[]
}

/** Feedback of a practice read through its communication style (CU-21). Render only for styled sessions. */
export function StyleFeedbackCard({ sessionId }: { sessionId: string }) {
  const { data, loading, error, reload } = useResource<StyleFeedback>(`/sessions/${sessionId}/style-feedback`)
  if (loading) return <section className="panel-card style-feedback"><p role="status">Cargando retroalimentación del estilo…</p></section>
  if (error) return <section className="panel-card style-feedback"><p role="alert">{error}</p><button className="button button--ghost" onClick={reload}>Reintentar</button></section>
  if (!data) return null
  return <section className="panel-card style-feedback" aria-label="Retroalimentación del estilo">
    <span className="eyebrow">Estilo de comunicación</span>
    <h2>Estilo {data.styleName}</h2>
    {!data.available ? <p>{data.message}</p> : <>
      {data.score != null && <p><span className="style-feedback__score">{Math.round(data.score)}</span> / 100 según los criterios que más pesan en este estilo</p>}
      {data.observations.length > 0 && <div><strong>Lo que observamos</strong><ul className="reason-list">{data.observations.map(item => <li key={item}>{item}</li>)}</ul></div>}
      <div><strong>Para acercarte al estilo</strong><ul className="reason-list">{data.tips.map(item => <li key={item}>{item}</li>)}</ul></div>
    </>}
    <small className="muted">Rasgos del estilo: {data.traits.join(' · ')}.</small>
  </section>
}
