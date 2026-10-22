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
import { PlaceholderPage } from '../../shared/components/PlaceholderPage'

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
      <Route path="/home" element={protectedPage(<DashboardPage />)} />
      <Route path="/training" element={protectedPage(<TrainingPage />)} />
      <Route path="/sessions" element={protectedPage(<SessionsPage />)} />
      <Route path="/results" element={protectedPage(<ResultsPage />)} />
      <Route path="/progress" element={protectedPage(<PlaceholderPage title="Progreso" />)} />
      <Route path="/simulations" element={protectedPage(<PlaceholderPage title="Simulaciones" />)} />
      <Route path="/profile" element={protectedPage(<PlaceholderPage title="Perfil" />)} />
      <Route path="/admin" element={<ProtectedRoute role="ADMIN"><AdminPage /></ProtectedRoute>} />
      <Route path="*" element={<Navigate to={startPath} replace />} />
    </Routes>
  )
}
