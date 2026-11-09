import { useState, type FormEvent } from 'react'
import { useAuth } from '../context/useAuth'
import { apiRequest } from '../../../shared/services/api'
import { WorkspaceLayout } from '../../../shared/components/WorkspaceLayout'

export function ProfilePage() {
  const { user, refresh, logout } = useAuth()
  const [firstName, setFirstName] = useState(user?.firstName || '')
  const [lastName, setLastName] = useState(user?.lastName || '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function save(event: FormEvent, password = false) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('')
    try {
      if (password) {
        await apiRequest('/users/me/password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) })
        logout()
      } else {
        await apiRequest('/users/me', { method: 'PUT', body: JSON.stringify({ firstName, lastName }) })
        await refresh(); setMessage('Perfil actualizado.')
      }
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'No se pudo guardar.') }
    finally { setBusy(false) }
  }
  return <WorkspaceLayout>
    <header><span className="eyebrow">Tu cuenta</span><h1>Perfil y seguridad</h1><p>{user?.email} · {user?.role} · Plan {user?.plan}</p></header>
    {error && <p className="training-alert training-alert--error" role="alert">{error}</p>}
    {message && <p className="workspace-notice" role="status">{message}</p>}
    <form className="panel-card workspace-form" onSubmit={save}>
      <h2>Datos personales</h2>
      <label>Nombre<input autoComplete="given-name" required maxLength={80} value={firstName} onChange={e => setFirstName(e.target.value)} /></label>
      <label>Apellido<input autoComplete="family-name" required maxLength={80} value={lastName} onChange={e => setLastName(e.target.value)} /></label>
      <button className="button button--primary" disabled={busy}>Guardar perfil</button>
    </form>
    <form className="panel-card workspace-form" onSubmit={e => save(e, true)}>
      <h2>Cambiar contraseña</h2><p>Al cambiarla se cerrarán tus sesiones. Inicia sesión con la nueva contraseña.</p>
      <label>Contraseña actual<input type="password" autoComplete="current-password" required value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} /></label>
      <label>Nueva contraseña<input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={newPassword} onChange={e => setNewPassword(e.target.value)} /></label>
      <button className="button button--primary" disabled={busy}>Cambiar contraseña</button>
    </form>
  </WorkspaceLayout>
}
