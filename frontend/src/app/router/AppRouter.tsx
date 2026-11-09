import { Navigate, Route, Routes } from 'react-router-dom'
import type { ReactNode } from 'react'
import { LoginPage } from '../../features/auth/pages/LoginPage'
import { RegisterPage } from '../../features/auth/pages/RegisterPage'
import { ProtectedRoute } from '../../features/auth/components/ProtectedRoute'
import { useAuth } from '../../features/auth/context/useAuth'
import { AdminPage } from '../../features/auth/pages/AdminPage'
import { DashboardPage } from '../../features/dashboard/pages/DashboardPage'
import { ResultsPage } from '../../features/training/pages/ResultsPage'
import { SessionsPage } from '../../features/training/pages/SessionsPage'
import { TrainingPage } from '../../features/training/pages/TrainingPage'
import { ProgressPage } from '../../features/progress/pages/ProgressPage'
import { SimulationsPage } from '../../features/simulations/pages/SimulationsPage'
import { ProfilePage } from '../../features/auth/pages/ProfilePage'
import { PlansPage } from '../../features/subscription/PlansPage'
import { BodyPage } from '../../features/body/BodyPage'
import { RecommendationsPage } from '../../features/progress/pages/RecommendationsPage'
import { ReportPage } from '../../features/training/pages/ReportPage'
import { AccountInformationPage } from '../../features/auth/pages/AccountInformationPage'

export function AppRouter() {
  const { user, loading } = useAuth()
  if (loading) return <main className="route-loading">Cargando sesión…</main>
  const startPath = user ? '/home' : '/login'
  const protectedPage = (page: ReactNode) => <ProtectedRoute>{page}</ProtectedRoute>

  return (
    <Routes>
      <Route path="/" element={<Navigate to={startPath} replace />} />
      <Route path="/login" element={user ? <Navigate to="/home" replace /> : <LoginPage />} />
      <Route path="/register" element={user ? <Navigate to="/home" replace /> : <RegisterPage />} />
      <Route path="/terms" element={<AccountInformationPage terms />} />
      <Route path="/account-help" element={<AccountInformationPage />} />
      <Route path="/home" element={protectedPage(<DashboardPage />)} />
      <Route path="/training" element={protectedPage(<TrainingPage />)} />
      <Route path="/sessions" element={protectedPage(<SessionsPage />)} />
      <Route path="/results" element={protectedPage(<ResultsPage />)} />
      <Route path="/progress" element={protectedPage(<ProgressPage />)} />
      <Route path="/simulations" element={protectedPage(<SimulationsPage />)} />
      <Route path="/profile" element={protectedPage(<ProfilePage />)} />
      <Route path="/plans" element={protectedPage(<PlansPage />)} />
      <Route path="/body" element={protectedPage(<BodyPage />)} />
      <Route path="/recommendations" element={protectedPage(<RecommendationsPage />)} />
      <Route path="/reports/:id" element={protectedPage(<ReportPage />)} />
      <Route path="/admin" element={<ProtectedRoute role="ADMIN"><AdminPage /></ProtectedRoute>} />
      <Route path="*" element={<Navigate to={startPath} replace />} />
    </Routes>
  )
}
