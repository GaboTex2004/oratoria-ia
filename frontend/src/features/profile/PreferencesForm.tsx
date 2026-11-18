import { useState, type FormEvent } from 'react'
import { PremiumBadge } from '../../shared/components/PremiumLock'
import { usePlanFeatures } from '../../shared/hooks/usePlanFeatures'
import { useResource } from '../../shared/hooks/useResource'
import { LEVEL_LABELS } from '../../shared/labels'
import { toggleLimited, weeklyPercent } from '../../shared/personalization'
import { apiRequest } from '../../shared/services/api'
import { TRAINING_TYPE_META, type TrainingType } from '../training/types/training'
import '../../shared/components/personalization.css'

interface Option { code: string; label: string }
export interface Preferences {
  goal: string | null; experienceLevel: string; prioritySkills: string[]; preferredTrainingTypes: string[]
  preferredStyle: string | null; weeklyGoal: number; practicesThisWeek: number; updatedAt: string | null
  options: { goals: Option[]; levels: string[]; skills: Option[]; trainingTypes: string[]; styles: Option[] }
}

const MAX_SKILLS = 3
const MAX_TYPES = 4

export function PreferencesSection() {
  const { data, loading, error, reload } = useResource<Preferences>('/users/me/preferences')
  const [message, setMessage] = useState('')
  if (loading) return <section className="panel-card"><p role="status">Cargando tus objetivos…</p></section>
  if (error || !data) return <section className="panel-card" role="alert"><p>{error || 'No se pudieron cargar tus objetivos.'}</p><button className="button button--ghost" onClick={reload}>Reintentar</button></section>
  // Remount the editor after each save so it starts from the stored values.
  return <PreferencesEditor key={data.updatedAt ?? 'new'} initial={data} message={message}
    onStart={() => setMessage('')} onSaved={() => { setMessage('Objetivos y preferencias guardados.'); reload() }} />
}

function PreferencesEditor({ initial, message, onStart, onSaved }: { initial: Preferences; message: string; onStart: () => void; onSaved: () => void }) {
  const [draft, setDraft] = useState(initial)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const stylesAllowed = usePlanFeatures().has('COMMUNICATION_STYLES')
  const percent = weeklyPercent(initial.practicesThisWeek, initial.weeklyGoal)

  async function save(event: FormEvent) {
    event.preventDefault(); onStart(); setBusy(true); setError('')
    try {
      await apiRequest('/users/me/preferences', { method: 'PUT', body: JSON.stringify({
        goal: draft.goal, experienceLevel: draft.experienceLevel, prioritySkills: draft.prioritySkills,
        // A user who lost Premium keeps the rest of the profile editable; the unusable style is cleared.
        preferredTrainingTypes: draft.preferredTrainingTypes, preferredStyle: stylesAllowed ? draft.preferredStyle : null, weeklyGoal: draft.weeklyGoal,
      }) })
      onSaved()
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'No se pudieron guardar tus objetivos.') }
    finally { setBusy(false) }
  }

  return <form className="panel-card workspace-form" onSubmit={save}>
    <h2>Objetivos y preferencias de entrenamiento</h2>
    <p className="muted">Usamos estos datos para elegir tus ejercicios recomendados.</p>
    {error && <p className="training-alert training-alert--error" role="alert">{error}</p>}
    {message && <p className="workspace-notice" role="status">{message}</p>}
    <div>
      <strong>Meta de esta semana</strong>
      <p>{initial.practicesThisWeek} de {initial.weeklyGoal} prácticas</p>
      <div className={`meter ${percent >= 100 ? 'meter--ok' : ''}`} role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100} aria-label="Avance de la meta semanal"><span style={{ width: `${percent}%` }} /></div>
    </div>
    <label>Objetivo principal<select value={draft.goal ?? ''} onChange={event => setDraft({ ...draft, goal: event.target.value || null })}>
      <option value="">Sin definir</option>{initial.options.goals.map(goal => <option key={goal.code} value={goal.code}>{goal.label}</option>)}
    </select></label>
    <label>Nivel de experiencia<select value={draft.experienceLevel} onChange={event => setDraft({ ...draft, experienceLevel: event.target.value })}>
      {initial.options.levels.map(level => <option key={level} value={level}>{LEVEL_LABELS[level] ?? level}</option>)}
    </select></label>
    <fieldset className="chip-group"><legend>Habilidades prioritarias (hasta {MAX_SKILLS})</legend>
      {initial.options.skills.map(skill => {
        const checked = draft.prioritySkills.includes(skill.code)
        return <label key={skill.code} className="chip-option"><input type="checkbox" checked={checked}
          disabled={!checked && draft.prioritySkills.length >= MAX_SKILLS}
          onChange={() => setDraft({ ...draft, prioritySkills: toggleLimited(draft.prioritySkills, skill.code, MAX_SKILLS) })} /><span>{skill.label}</span></label>
      })}
    </fieldset>
    <fieldset className="chip-group"><legend>Tipos de entrenamiento preferidos (hasta {MAX_TYPES})</legend>
      {initial.options.trainingTypes.map(type => {
        const checked = draft.preferredTrainingTypes.includes(type)
        return <label key={type} className="chip-option"><input type="checkbox" checked={checked}
          disabled={!checked && draft.preferredTrainingTypes.length >= MAX_TYPES}
          onChange={() => setDraft({ ...draft, preferredTrainingTypes: toggleLimited(draft.preferredTrainingTypes, type, MAX_TYPES) })} /><span>{TRAINING_TYPE_META[type as TrainingType]?.label ?? type}</span></label>
      })}
    </fieldset>
    <label>Estilo de comunicación {!stylesAllowed && <PremiumBadge />}
      <select disabled={!stylesAllowed} value={draft.preferredStyle ?? ''} onChange={event => setDraft({ ...draft, preferredStyle: event.target.value || null })}>
        <option value="">Ninguno</option>{initial.options.styles.map(style => <option key={style.code} value={style.code}>{style.label}</option>)}
      </select></label>
    <label>Meta semanal de prácticas<input type="number" required min={1} max={14} value={draft.weeklyGoal}
      onChange={event => setDraft({ ...draft, weeklyGoal: Number(event.target.value) })} /></label>
    <button className="button button--primary" disabled={busy}>{busy ? 'Guardando…' : 'Guardar objetivos'}</button>
  </form>
}
