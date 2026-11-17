import { useState, type FormEvent } from 'react'
import { useResource } from '../../../shared/hooks/useResource'
import { apiRequest } from '../../../shared/services/api'
import { TRAINING_TYPE_META, type TrainingType } from '../../training/types/training'
import { errorText, type CategoryDraft, type CategoryRow, type ExerciseDraft, type ExerciseRow } from '../types'

const emptyExercise: ExerciseDraft = { code: '', title: '', description: '', prompt: '', objective: '', category: 'ESTRUCTURA', difficulty: 'BASIC', duration: 60, trainingType: 'PRESENTATION', active: true }
const emptyCategory: CategoryDraft = { code: '', name: '', description: '', active: true }

export function AdminCatalogTab() {
  const exercises = useResource<ExerciseRow[]>('/admin/exercises')
  const categories = useResource<CategoryRow[]>('/admin/categories')
  const [draft, setDraft] = useState<ExerciseDraft>(emptyExercise)
  const [editing, setEditing] = useState<string | null>(null)
  const [categoryDraft, setCategoryDraft] = useState<CategoryDraft>(emptyCategory)
  const [editingCategory, setEditingCategory] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const categoryName = (code: string) => categories.data?.find(category => category.code === code)?.name || code
  const originalCategory = editing ? exercises.data?.find(exercise => exercise.id === editing)?.category : undefined
  const selectableCategories = (categories.data || []).filter(category => category.active || category.code === originalCategory)

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true); setError(''); setMessage('')
    try { await action(); setMessage(success) } catch (reason) { setError(errorText(reason, 'No se pudo guardar.')) } finally { setBusy(false) }
  }
  function field<K extends keyof ExerciseDraft>(key: K, value: ExerciseDraft[K]) { setDraft(previous => ({ ...previous, [key]: value })) }
  function saveExercise(event: FormEvent) {
    event.preventDefault()
    return run(async () => {
      await apiRequest(`/admin/exercises${editing ? `/${editing}` : ''}`, { method: editing ? 'PUT' : 'POST', body: JSON.stringify(draft) })
      exercises.reload(); categories.reload(); setDraft(emptyExercise); setEditing(null)
    }, 'Ejercicio guardado.')
  }
  function saveCategory(event: FormEvent) {
    event.preventDefault()
    return run(async () => {
      await apiRequest(`/admin/categories${editingCategory ? `/${editingCategory}` : ''}`, { method: editingCategory ? 'PUT' : 'POST', body: JSON.stringify(categoryDraft) })
      categories.reload(); setCategoryDraft(emptyCategory); setEditingCategory(null)
    }, 'Categoría guardada.')
  }

  return <>
    {(error || exercises.error || categories.error) && <div role="alert" className="workspace-notice">{error || exercises.error || categories.error}<button className="button button--ghost" onClick={() => { exercises.reload(); categories.reload() }}>Reintentar carga</button></div>}
    {message && <p role="status" className="workspace-notice">{message}</p>}
    <section className="panel-card"><h2>Categorías de entrenamiento</h2>{categories.loading && <p>Cargando categorías…</p>}
      <div className="workspace-table-wrap"><table className="workspace-table"><thead><tr><th>Categoría</th><th>Ejercicios</th><th>Estado</th><th>Acción</th></tr></thead><tbody>{categories.data?.map(category => <tr key={category.id}>
        <td>{category.name}<br /><small>{category.code}{category.description ? ` · ${category.description}` : ''}</small></td><td>{category.exercises}</td>
        <td>{category.active ? 'Activa' : 'Inactiva'}</td>
        <td><button className="button button--ghost" disabled={busy} onClick={() => { setEditingCategory(category.id); setCategoryDraft({ code: category.code, name: category.name, description: category.description || '', active: category.active }) }}>Editar</button></td>
      </tr>)}</tbody></table></div>
    </section>
    <form className="panel-card workspace-form" onSubmit={saveCategory}><h2>{editingCategory ? 'Editar categoría' : 'Crear categoría'}</h2>
      <label>Código<input required pattern="[A-Z0-9_]{3,40}" title="Mayúsculas, números y guion bajo (3 a 40)" disabled={!!editingCategory} value={categoryDraft.code} onChange={event => setCategoryDraft(previous => ({ ...previous, code: event.target.value.toUpperCase() }))} /></label>
      <label>Nombre<input required maxLength={80} value={categoryDraft.name} onChange={event => setCategoryDraft(previous => ({ ...previous, name: event.target.value }))} /></label>
      <label>Descripción<textarea maxLength={300} value={categoryDraft.description} onChange={event => setCategoryDraft(previous => ({ ...previous, description: event.target.value }))} /></label>
      <label className="terms-field"><input type="checkbox" checked={categoryDraft.active} onChange={event => setCategoryDraft(previous => ({ ...previous, active: event.target.checked }))} />Disponible para nuevos ejercicios</label>
      <div className="workspace-actions"><button className="button button--primary" disabled={busy}>Guardar categoría</button>{editingCategory && <button className="button button--ghost" type="button" onClick={() => { setEditingCategory(null); setCategoryDraft(emptyCategory) }}>Cancelar edición</button>}</div>
    </form>
    <section className="panel-card"><h2>Ejercicios</h2>{exercises.loading && <p>Cargando ejercicios…</p>}
      <div className="workspace-table-wrap"><table className="workspace-table"><thead><tr><th>Ejercicio</th><th>Categoría / nivel</th><th>Estado</th><th>Acción</th></tr></thead><tbody>{exercises.data?.map(exercise => <tr key={exercise.id}>
        <td>{exercise.title}</td><td>{categoryName(exercise.category)} / {exercise.difficulty}</td><td>{exercise.active ? 'Activo' : 'Inactivo'}</td>
        <td><button className="button button--ghost" disabled={busy} onClick={() => { setEditing(exercise.id); setDraft(exercise) }}>Editar</button></td>
      </tr>)}</tbody></table></div>
    </section>
    <form className="panel-card workspace-form" onSubmit={saveExercise}><h2>{editing ? 'Editar ejercicio' : 'Crear ejercicio'}</h2>
      <label>Código<input required pattern="[A-Z0-9_]{3,60}" disabled={!!editing} value={draft.code} onChange={event => field('code', event.target.value.toUpperCase())} /></label>
      <label>Título<input required maxLength={120} value={draft.title} onChange={event => field('title', event.target.value)} /></label>
      <label>Descripción<textarea required maxLength={400} value={draft.description} onChange={event => field('description', event.target.value)} /></label>
      <label>Objetivo<textarea required maxLength={400} value={draft.objective} onChange={event => field('objective', event.target.value)} /></label>
      <label>Instrucciones<textarea required maxLength={1000} value={draft.prompt} onChange={event => field('prompt', event.target.value)} /></label>
      <label>Categoría<select value={draft.category} onChange={event => field('category', event.target.value)}>{selectableCategories.map(category => <option key={category.code} value={category.code}>{category.name}{category.active ? '' : ' (inactiva)'}</option>)}</select></label>
      <label>Nivel<select value={draft.difficulty} onChange={event => field('difficulty', event.target.value)}>{['BASIC', 'INTERMEDIATE', 'ADVANCED'].map(level => <option key={level}>{level}</option>)}</select></label>
      <label>Modalidad<select disabled={!!editing} value={draft.trainingType} onChange={event => field('trainingType', event.target.value as TrainingType)}>{Object.entries(TRAINING_TYPE_META).map(([code, meta]) => <option key={code} value={code}>{meta.label}</option>)}</select></label>
      <label>Duración sugerida (segundos)<input required type="number" min={15} max={300} value={draft.duration} onChange={event => field('duration', Number(event.target.value))} /></label>
      <label className="terms-field"><input type="checkbox" checked={draft.active} onChange={event => field('active', event.target.checked)} />Disponible para practicar</label>
      <div className="workspace-actions"><button className="button button--primary" disabled={busy}>Guardar</button>{editing && <button className="button button--ghost" type="button" onClick={() => { setEditing(null); setDraft(emptyExercise) }}>Cancelar edición</button>}</div>
    </form>
  </>
}
