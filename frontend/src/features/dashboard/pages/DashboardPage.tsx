import { useEffect, useState } from 'react'
import { ApiError } from '../../../shared/services/api'
import { useAuth } from '../../auth/context/useAuth'
import { progressService } from '../../progress/services/progressService'
import type { ProgressSummary } from '../../progress/types/progress'
import { trainingService } from '../../training/services/trainingService'
import type { Exercise } from '../../training/types/training'
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
  const [summary, setSummary] = useState<ProgressSummary | null>(null)
  const [exercises, setExercises] = useState<Exercise[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadCount, setReloadCount] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([progressService.getSummary(), trainingService.listExercises()])
      .then(([progress, catalog]) => {
        if (!active) return
        setSummary(progress)
        setExercises(catalog)
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof ApiError ? reason.message : 'No se pudo cargar el dashboard.')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [reloadCount])

  function retry() {
    setLoading(true)
    setError('')
    setReloadCount((value) => value + 1)
  }

  return (
    <div className="dashboard-layout">
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} subscription={summary?.subscription} />
      <main className="dashboard-main">
        <Topbar firstName={firstName} onMenuOpen={() => setMenuOpen(true)} />
        <div className="dashboard-content">
          <BackendStatus />
          {error && <div className="dashboard-load-error" role="alert"><span>{error}</span><button type="button" onClick={retry}>Reintentar</button></div>}
          {loading ? <div className="dashboard-skeleton" aria-label="Cargando dashboard"><span /><span /><span /><span /></div> : summary && !error && (
            <div className="dashboard-grid"><TrainingGrid exercises={exercises} /><aside className="dashboard-aside"><ProgressCard overview={summary.overview} /><RecentActivity history={summary.history} /></aside></div>
          )}
        </div>
      </main>
    </div>
  )
}
