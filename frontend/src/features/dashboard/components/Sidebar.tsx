import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/context/useAuth'
import { Brand } from '../../../shared/components/Brand'
import { Icon } from '../../../shared/components/Icon'
import { subscriptionSummary } from '../data/dashboardMock'

const navigation = [
  ['/home', 'home', 'Inicio'], ['/training', 'fitness_center', 'Entrenamientos'],
  ['/sessions', 'history', 'Mis sesiones'], ['/results', 'analytics', 'Resultados'],
  ['/progress', 'query_stats', 'Progreso'], ['/simulations', 'psychology', 'Simulaciones'],
  ['/profile', 'person', 'Perfil'],
]

interface SidebarProps { open: boolean; onClose: () => void }

export function Sidebar({ open, onClose }: SidebarProps) {
  const navigate = useNavigate()
  const { user, logout: endSession } = useAuth()
  const usage = (subscriptionSummary.sessionsUsed / subscriptionSummary.sessionsLimit) * 100

  function logout() {
    endSession()
    navigate('/login')
  }

  return (
    <>
      <button className={`sidebar-backdrop ${open ? 'is-open' : ''}`} onClick={onClose} aria-label="Cerrar menú" />
      <aside className={`sidebar ${open ? 'is-open' : ''}`}>
        <div className="sidebar__brand"><Brand /></div>
        <nav className="sidebar__nav" aria-label="Navegación principal">
          {navigation.map(([path, icon, label]) => (
            <NavLink key={path} to={path} onClick={onClose} className={({ isActive }) => `sidebar-link ${isActive ? 'is-active' : ''}`}>
              <Icon name={icon} filled={path === '/home'} /><span>{label}</span>
            </NavLink>
          ))}
        </nav>
        {user?.role === 'ADMIN' && <NavLink to="/admin" onClick={onClose} className={({ isActive }) => `sidebar-link ${isActive ? 'is-active' : ''}`}><Icon name="admin_panel_settings" /><span>Administración</span></NavLink>}
        <div className="sidebar__footer">
          <section className="plan-summary">
            <div><strong><Icon name="auto_awesome" /> Plan {subscriptionSummary.plan}</strong><span>{subscriptionSummary.sessionsUsed}/{subscriptionSummary.sessionsLimit} sesiones</span></div>
            <div className="progress-track"><span style={{ width: `${usage}%` }} /></div>
            <button type="button">Ver planes</button>
          </section>
          <button className="sidebar-link sidebar-link--button" type="button"><Icon name="settings" /><span>Configuración</span></button>
          <button className="sidebar-link sidebar-link--button sidebar-link--danger" type="button" onClick={logout}><Icon name="logout" /><span>Cerrar sesión</span></button>
        </div>
      </aside>
    </>
  )
}
