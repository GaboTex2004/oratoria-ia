import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { WorkspaceLayout } from '../../../shared/components/WorkspaceLayout'
import { useResource } from '../../../shared/hooks/useResource'
import { apiRequest } from '../../../shared/services/api'
import { TRAINING_TYPE_META, type TrainingType } from '../../training/types/training'

interface UserRow { id: string; name: string; email: string; role: string; enabled: boolean; plan: string }
interface ExerciseDraft { code: string; title: string; description: string; prompt: string; objective: string; category: string; difficulty: string; duration: number; trainingType: TrainingType; active: boolean }
interface ExerciseRow extends ExerciseDraft { id: string }
const emptyDraft: ExerciseDraft = { code: '', title: '', description: '', prompt: '', objective: '', category: 'ESTRUCTURA', difficulty: 'BASIC', duration: 60, trainingType: 'PRESENTATION', active: true }
const categories = ['CLARIDAD', 'RITMO', 'ENTONACION', 'FLUIDEZ', 'ESTRUCTURA', 'ARGUMENTACION', 'IMPROVISACION', 'NO_VERBAL', 'ESTILOS']

export function AdminManagementPage() {
  const overview = useResource<Record<string, number>>('/admin/overview')
  const [page, setPage] = useState(0)
  const users = useResource<UserRow[]>(`/admin/users?page=${page}`)
  const exercises = useResource<ExerciseRow[]>('/admin/exercises')
  const [draft, setDraft] = useState<ExerciseDraft>(emptyDraft)
  const [editing, setEditing] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [resetUserId, setResetUserId] = useState('')
  const [resetPasswordValue, setResetPasswordValue] = useState('')
  async function resetPassword(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('')
    try {
      await apiRequest(`/admin/users/${resetUserId}/password`, { method: 'POST', body: JSON.stringify({ newPassword: resetPasswordValue }) })
      setResetPasswordValue(''); setMessage('Contraseña restablecida. Las sesiones anteriores quedaron revocadas.')
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'No se pudo restablecer.') }
    finally { setBusy(false) }
  }
  function field<K extends keyof ExerciseDraft>(key: K, value: ExerciseDraft[K]) { setDraft(previous => ({ ...previous, [key]: value })) }
  async function updateUser(user: UserRow, values: Partial<UserRow>) {
    setBusy(true); setError(''); setMessage('')
    try { await apiRequest(`/admin/users/${user.id}`, { method: 'PUT', body: JSON.stringify({ enabled: user.enabled, plan: user.plan, ...values }) }); users.reload(); setMessage('Cuenta actualizada.') }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'No se pudo actualizar.') }
    finally { setBusy(false) }
  }
  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('')
    try {
      await apiRequest(`/admin/exercises${editing ? `/${editing}` : ''}`, { method: editing ? 'PUT' : 'POST', body: JSON.stringify(draft) })
      exercises.reload(); overview.reload(); setDraft(emptyDraft); setEditing(null); setMessage('Ejercicio guardado.')
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'No se pudo guardar.') }
    finally { setBusy(false) }
  }
  const loadError = overview.error || users.error || exercises.error
  return <WorkspaceLayout>
    <header><span className="eyebrow">Administración</span><h1>Gestión de la plataforma</h1><p>Los cambios de planes son administrativos; no se realizan cobros.</p><Link to="/admin/payments">Administrar pagos simulados</Link></header>
    {(error || loadError) && <div role="alert" className="workspace-notice">{error || loadError}<button className="button button--ghost" onClick={() => { overview.reload(); users.reload(); exercises.reload() }}>Reintentar carga</button></div>}
    {message && <p role="status" className="workspace-notice">{message}</p>}
    {overview.loading ? <p>Cargando estadísticas…</p> : overview.data && <div className="sessions-kpi-grid">{Object.entries(overview.data).map(([key, value]) => <article className="panel-card" key={key}><h2>{({ users: 'Usuarios', exercises: 'Ejercicios', sessions: 'Sesiones' })[key] || key}</h2><strong>{value}</strong></article>)}</div>}
    <section className="panel-card"><h2>Usuarios</h2>{users.loading && <p>Cargando usuarios…</p>}
      <div className="workspace-table-wrap"><table className="workspace-table"><thead><tr><th>Nombre / correo</th><th>Rol</th><th>Plan</th><th>Cuenta</th></tr></thead><tbody>{users.data?.map(user => <tr key={user.id}>
        <td>{user.name}<br /><small>{user.email}</small></td><td>{user.role}</td>
        <td><select aria-label={`Plan de ${user.name}`} disabled={busy} value={user.plan || ''} onChange={event => updateUser(user, { plan: event.target.value })}><option value="FREE">FREE</option><option value="PREMIUM">PREMIUM</option></select></td>
        <td><button className="button button--ghost" disabled={busy || user.role === 'ADMIN'} onClick={() => updateUser(user, { enabled: !user.enabled })}>{user.enabled ? 'Deshabilitar' : 'Habilitar'}</button></td>
      </tr>)}</tbody></table></div>
      <div className="workspace-actions"><button className="button button--ghost" disabled={page === 0 || busy} onClick={() => setPage(value => value - 1)}>Anterior</button><span>Página {page + 1}</span><button className="button button--ghost" disabled={(users.data?.length ?? 0) < 50 || busy} onClick={() => setPage(value => value + 1)}>Siguiente</button></div>
    </section>
    <section className="panel-card"><h2>Catálogo</h2>{exercises.loading && <p>Cargando ejercicios…</p>}<div className="workspace-table-wrap"><table className="workspace-table"><thead><tr><th>Ejercicio</th><th>Categoría / nivel</th><th>Estado</th><th>Acción</th></tr></thead><tbody>{exercises.data?.map(exercise => <tr key={exercise.id}><td>{exercise.title}</td><td>{exercise.category} / {exercise.difficulty}</td><td>{exercise.active ? 'Activo' : 'Inactivo'}</td><td><button className="button button--ghost" disabled={busy} onClick={() => { setEditing(exercise.id); setDraft(exercise) }}>Editar</button></td></tr>)}</tbody></table></div></section>
    <form className="panel-card workspace-form" onSubmit={resetPassword}>
      <h2>Restablecer acceso de un usuario</h2><p>Verifica la identidad del solicitante antes de restablecer su acceso.</p>
      <label>Usuario<select required value={resetUserId} onChange={event => setResetUserId(event.target.value)}><option value="">Selecciona una cuenta</option>{users.data?.filter(user => user.role === 'USER').map(user => <option key={user.id} value={user.id}>{user.name} · {user.email}</option>)}</select></label>
      <label>Nueva contraseña temporal<input type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={resetPasswordValue} onChange={event => setResetPasswordValue(event.target.value)} /></label>
      <button className="button button--primary" disabled={busy || !resetUserId}>Restablecer contraseña</button>
    </form>
    <form className="panel-card workspace-form" onSubmit={save}><h2>{editing ? 'Editar ejercicio' : 'Crear ejercicio'}</h2>
      <label>Código<input required pattern="[A-Z0-9_]{3,60}" disabled={!!editing} value={draft.code} onChange={event => field('code', event.target.value)} /></label>
      <label>Título<input required maxLength={120} value={draft.title} onChange={event => field('title', event.target.value)} /></label>
      <label>Descripción<textarea required maxLength={400} value={draft.description} onChange={event => field('description', event.target.value)} /></label>
      <label>Objetivo<textarea required maxLength={400} value={draft.objective} onChange={event => field('objective', event.target.value)} /></label>
      <label>Instrucciones<textarea required maxLength={1000} value={draft.prompt} onChange={event => field('prompt', event.target.value)} /></label>
      <label>Categoría<select value={draft.category} onChange={event => field('category', event.target.value)}>{categories.map(category => <option key={category}>{category}</option>)}</select></label>
      <label>Nivel<select value={draft.difficulty} onChange={event => field('difficulty', event.target.value)}>{['BASIC', 'INTERMEDIATE', 'ADVANCED'].map(level => <option key={level}>{level}</option>)}</select></label>
      <label>Modalidad<select disabled={!!editing} value={draft.trainingType} onChange={event => field('trainingType', event.target.value as TrainingType)}>{Object.entries(TRAINING_TYPE_META).map(([code, meta]) => <option key={code} value={code}>{meta.label}</option>)}</select></label>
      <label>Duración sugerida (segundos)<input required type="number" min={15} max={300} value={draft.duration} onChange={event => field('duration', Number(event.target.value))} /></label>
      <label className="terms-field"><input type="checkbox" checked={draft.active} onChange={event => field('active', event.target.checked)} />Disponible para practicar</label>
      <div className="workspace-actions"><button className="button button--primary" disabled={busy}>Guardar</button>{editing && <button className="button button--ghost" type="button" onClick={() => { setEditing(null); setDraft(emptyDraft) }}>Cancelar edición</button>}</div>
    </form>
  </WorkspaceLayout>
}
