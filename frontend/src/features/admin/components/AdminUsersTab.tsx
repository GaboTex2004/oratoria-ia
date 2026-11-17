import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useResource } from '../../../shared/hooks/useResource'
import { apiRequest } from '../../../shared/services/api'
import { errorText, type UserRow } from '../types'

export function AdminUsersTab() {
  const [page, setPage] = useState(0)
  const users = useResource<UserRow[]>(`/admin/users?page=${page}`)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [resetUserId, setResetUserId] = useState('')
  const [resetPasswordValue, setResetPasswordValue] = useState('')

  async function run(action: () => Promise<unknown>, success: string, fallback: string) {
    setBusy(true); setError(''); setMessage('')
    try { await action(); setMessage(success) } catch (reason) { setError(errorText(reason, fallback)) } finally { setBusy(false) }
  }
  function updateUser(user: UserRow, values: Partial<UserRow>) {
    return run(async () => { await apiRequest(`/admin/users/${user.id}`, { method: 'PUT', body: JSON.stringify({ enabled: user.enabled, plan: user.plan, ...values }) }); users.reload() }, 'Cuenta actualizada.', 'No se pudo actualizar.')
  }
  function resetPassword(event: FormEvent) {
    event.preventDefault()
    return run(async () => { await apiRequest(`/admin/users/${resetUserId}/password`, { method: 'POST', body: JSON.stringify({ newPassword: resetPasswordValue }) }); setResetPasswordValue('') },
      'Contraseña restablecida. Las sesiones anteriores quedaron revocadas.', 'No se pudo restablecer.')
  }

  return <>
    {(error || users.error) && <div role="alert" className="workspace-notice">{error || users.error}<button className="button button--ghost" onClick={users.reload}>Reintentar carga</button></div>}
    {message && <p role="status" className="workspace-notice">{message}</p>}
    <section className="panel-card"><div className="panel-card__heading"><h2>Usuarios</h2><Link to="/admin/payments">Administrar pagos simulados</Link></div>
      {users.loading && <p>Cargando usuarios…</p>}
      <div className="workspace-table-wrap"><table className="workspace-table"><thead><tr><th>Nombre / correo</th><th>Rol</th><th>Plan</th><th>Cuenta</th></tr></thead><tbody>{users.data?.map(user => <tr key={user.id}>
        <td>{user.name}<br /><small>{user.email}</small></td><td>{user.role}</td>
        <td><select aria-label={`Plan de ${user.name}`} disabled={busy} value={user.plan || ''} onChange={event => updateUser(user, { plan: event.target.value })}><option value="FREE">FREE</option><option value="PREMIUM">PREMIUM</option></select></td>
        <td><button className="button button--ghost" disabled={busy || user.role === 'ADMIN'} onClick={() => updateUser(user, { enabled: !user.enabled })}>{user.enabled ? 'Deshabilitar' : 'Habilitar'}</button></td>
      </tr>)}</tbody></table></div>
      <div className="workspace-actions"><button className="button button--ghost" disabled={page === 0 || busy} onClick={() => setPage(value => value - 1)}>Anterior</button><span>Página {page + 1}</span><button className="button button--ghost" disabled={(users.data?.length ?? 0) < 50 || busy} onClick={() => setPage(value => value + 1)}>Siguiente</button></div>
    </section>
    <form className="panel-card workspace-form" onSubmit={resetPassword}>
      <h2>Restablecer acceso de un usuario</h2><p>Verifica la identidad del solicitante antes de restablecer su acceso.</p>
      <label>Usuario<select required value={resetUserId} onChange={event => setResetUserId(event.target.value)}><option value="">Selecciona una cuenta</option>{users.data?.filter(user => user.role === 'USER').map(user => <option key={user.id} value={user.id}>{user.name} · {user.email}</option>)}</select></label>
      <label>Nueva contraseña temporal<input type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={resetPasswordValue} onChange={event => setResetPasswordValue(event.target.value)} /></label>
      <button className="button button--primary" disabled={busy || !resetUserId}>Restablecer contraseña</button>
    </form>
  </>
}
