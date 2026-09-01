import { useState } from 'react'
import { useAuth } from '../../auth/context/useAuth'
import { BackendStatus } from '../components/BackendStatus'
import { ProgressCard } from '../components/ProgressCard'
import { RecentActivity } from '../components/RecentActivity'
import { Sidebar } from '../components/Sidebar'
import { Topbar } from '../components/Topbar'
import { TrainingGrid } from '../components/TrainingGrid'
import '../components/dashboard.css'

export function DashboardPage() {
  const [menuOpen, setMenuOpen] = useState(false)
  const firstName = useAuth().user?.firstName || ''
  return (
    <div className="dashboard-layout">
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
      <main className="dashboard-main">
        <Topbar firstName={firstName} onMenuOpen={() => setMenuOpen(true)} />
        <div className="dashboard-content">
          <BackendStatus />
          <div className="dashboard-grid"><TrainingGrid /><aside className="dashboard-aside"><ProgressCard /><RecentActivity /></aside></div>
        </div>
      </main>
    </div>
  )
}
