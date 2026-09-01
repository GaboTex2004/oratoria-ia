import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { authService } from '../services/authService'
import type { LoginRequest, RegisterRequest, User } from '../types/auth'
import { tokenStorage } from '../../../shared/services/tokenStorage'
import { AuthContext } from './authContextValue'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const hasStoredToken = tokenStorage.get() !== null
  const [loading, setLoading] = useState(hasStoredToken)

  useEffect(() => {
    if (!hasStoredToken) return
    authService.me().then(setUser).catch(() => authService.logout()).finally(() => setLoading(false))
  }, [hasStoredToken])

  async function login(request: LoginRequest) {
    const session = await authService.login(request); setUser(session.user); return session.user
  }
  async function register(request: RegisterRequest) {
    const session = await authService.register(request); setUser(session.user); return session.user
  }
  function logout() { authService.logout(); setUser(null) }

  return <AuthContext.Provider value={{ user, loading, login, register, logout }}>{children}</AuthContext.Provider>
}
