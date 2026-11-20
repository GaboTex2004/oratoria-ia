import { useSearchParams } from 'react-router-dom'
import { WorkspaceLayout } from '../../../shared/components/WorkspaceLayout'
import { AdminCatalogTab } from '../../admin/components/AdminCatalogTab'
import { AdminOverviewTab } from '../../admin/components/AdminOverviewTab'
import { AdminPlansTab } from '../../admin/components/AdminPlansTab'
import { AdminSystemTab } from '../../admin/components/AdminSystemTab'
import { AdminUsersTab } from '../../admin/components/AdminUsersTab'
import { AdminReportsTab } from '../../admin/components/AdminReportsTab'
import { AdminAuditTab } from '../../admin/components/AdminAuditTab'
import { AdminBackupsTab } from '../../admin/components/AdminBackupsTab'
import { AdminMonitoringTab } from '../../admin/components/AdminMonitoringTab'
import '../../admin/components/admin.css'

const TABS = [
  { id: 'resumen', label: 'Resumen', render: () => <AdminOverviewTab /> },
  { id: 'monitoreo', label: 'Monitoreo', render: () => <AdminMonitoringTab /> },
  { id: 'usuarios', label: 'Usuarios', render: () => <AdminUsersTab /> },
  { id: 'catalogo', label: 'Ejercicios y categorías', render: () => <AdminCatalogTab /> },
  { id: 'planes', label: 'Planes', render: () => <AdminPlansTab /> },
  { id: 'reportes', label: 'Reportes', render: () => <AdminReportsTab /> },
  { id: 'bitacora', label: 'Bitácora', render: () => <AdminAuditTab /> },
  { id: 'respaldos', label: 'Respaldos', render: () => <AdminBackupsTab /> },
  { id: 'sistema', label: 'Sistema', render: () => <AdminSystemTab /> },
] as const

export function AdminManagementPage() {
  const [params, setParams] = useSearchParams()
  const active = TABS.find(tab => tab.id === params.get('tab')) ?? TABS[0]
  return <WorkspaceLayout>
    <header><span className="eyebrow">Administración</span><h1>Gestión de la plataforma</h1><p>Usuarios, catálogo de entrenamiento, planes, reportes, bitácora, respaldos y parámetros generales de OratoriaAI. Los cambios de plan son administrativos; no se realizan cobros.</p></header>
    <nav className="admin-tabs" role="tablist" aria-label="Secciones de administración">
      {TABS.map(tab => <button key={tab.id} type="button" role="tab" aria-selected={tab.id === active.id} className={`admin-tabs__tab${tab.id === active.id ? ' admin-tabs__tab--active' : ''}`} onClick={() => setParams({ tab: tab.id })}>{tab.label}</button>)}
    </nav>
    <div role="tabpanel" className="admin-panel">{active.render()}</div>
  </WorkspaceLayout>
}
