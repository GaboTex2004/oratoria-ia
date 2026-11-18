import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PremiumLock } from '../../shared/components/PremiumLock'
import { WorkspaceLayout } from '../../shared/components/WorkspaceLayout'
import { useResource } from '../../shared/hooks/useResource'
import { LEVEL_LABELS } from '../../shared/labels'
import { apiRequest } from '../../shared/services/api'
import { formatDuration, type Exercise } from '../training/types/training'
import '../../shared/components/personalization.css'

interface StyleView {
  code: string; name: string; summary: string; traits: string[]; guidance: string
  minWordsPerMinute: number; maxWordsPerMinute: number; exercises: Exercise[]
}
interface StylesResponse { locked: boolean; selected: string | null; disclaimer: string; styles: StyleView[] }

export function StylesPage() {
  const { data, loading, error, reload } = useResource<StylesResponse>('/styles')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [saveError, setSaveError] = useState('')

  async function select(style: string | null) {
    setBusy(true); setMessage(''); setSaveError('')
    try {
      await apiRequest('/styles/selected', { method: 'PUT', body: JSON.stringify({ style }) })
      setMessage(style ? 'Estilo guardado en tu perfil. Tus recomendaciones lo tendrán en cuenta.' : 'Quitaste el estilo de tu perfil.')
      reload()
    } catch (reason) { setSaveError(reason instanceof Error ? reason.message : 'No se pudo guardar el estilo.') }
    finally { setBusy(false) }
  }

  return <WorkspaceLayout>
    <header><span className="eyebrow">Entrenamiento por estilo</span><h1>Estilos de comunicación</h1>
      <p>Elige un estilo de referencia y practícalo con ejercicios propios. Al terminar, verás una retroalimentación según los rasgos de ese estilo.</p>
    </header>
    {data?.locked && <PremiumLock feature="El entrenamiento por estilos">Puedes explorar los estilos, pero para practicarlos y elegir uno necesitas Premium.</PremiumLock>}
    {loading && <p role="status">Cargando estilos…</p>}
    {(error || saveError) && <div role="alert" className="workspace-notice">{error || saveError}{error && <button className="button button--ghost" onClick={reload}>Reintentar</button>}</div>}
    {message && <p role="status" className="workspace-notice">{message}</p>}
    {data && <p className="muted">{data.disclaimer}</p>}
    <div className="card-grid">{data?.styles.map(style => {
      const selected = data.selected === style.code
      return <article key={style.code} className={`panel-card style-card ${selected ? 'style-card--selected' : ''}`}>
        <h2>{style.name}{selected && <span className="badge badge--blue">Tu estilo</span>}</h2>
        <p>{style.summary}</p>
        <div><strong>Rasgos que se entrenan</strong><ul>{style.traits.map(trait => <li key={trait}>{trait}</li>)}</ul></div>
        <p><strong>Cómo practicarlo:</strong> {style.guidance}</p>
        <p className="muted">Ritmo sugerido: {style.minWordsPerMinute}–{style.maxWordsPerMinute} palabras por minuto.</p>
        <ul className="style-card__exercises">{style.exercises.map(exercise => <li key={exercise.id}>
          <span>{exercise.title}<small>{LEVEL_LABELS[exercise.difficulty] ?? exercise.difficulty} · {formatDuration(exercise.recommendedDurationSeconds)}</small></span>
          {data.locked ? <span className="muted">Premium</span> : <Link className="button button--ghost" to={`/training?exerciseId=${exercise.id}`}>Practicar</Link>}
        </li>)}</ul>
        {!data.locked && <div className="workspace-actions">
          {selected
            ? <button className="button button--ghost" disabled={busy} onClick={() => void select(null)}>Quitar de mi perfil</button>
            : <button className="button button--primary" disabled={busy} onClick={() => void select(style.code)}>Elegir como mi estilo</button>}
        </div>}
      </article>
    })}</div>
  </WorkspaceLayout>
}
