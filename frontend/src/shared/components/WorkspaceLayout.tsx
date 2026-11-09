import { useState, type ReactNode } from 'react'
import { useAuth } from '../../features/auth/context/useAuth'
import { Sidebar } from '../../features/dashboard/components/Sidebar'
import { Topbar } from '../../features/dashboard/components/Topbar'
import '../../features/dashboard/components/dashboard.css'
import '../../features/training/components/training.css'
import './workspace.css'

export function WorkspaceLayout({ children }: { children: ReactNode }) {
  const [menu, setMenu] = useState(false)
  const { user } = useAuth()
  return <div className="dashboard-layout">
    <Sidebar open={menu} onClose={() => setMenu(false)} />
    <main className="dashboard-main">
      <Topbar firstName={user?.firstName || ''} onMenuOpen={() => setMenu(true)} />
      <div className="dashboard-content workspace-content">{children}</div>
    </main>
  </div>
}
