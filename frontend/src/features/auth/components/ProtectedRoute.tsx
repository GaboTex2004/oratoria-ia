import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/useAuth'
import type { User } from '../types/auth'

export function ProtectedRoute({ children, role }: { children: ReactNode; role?: User['role'] }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <main className="route-loading">Cargando sesión…</main>
  if (!user) return <Navigate to="/login" state={{ from: location }} replace />
  if (role && user.role !== role) return <Navigate to="/home" replace />
  return children
}
