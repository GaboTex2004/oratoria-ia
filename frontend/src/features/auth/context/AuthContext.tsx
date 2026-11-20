import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { authService } from '../services/authService'
import { ApiError } from '../../../shared/services/api'
import { forgetPushOnServer } from '../../../shared/pwa/push'
import type { LoginRequest, RegisterRequest, User } from '../types/auth'
import { tokenStorage } from '../../../shared/services/tokenStorage'
import { AuthContext } from './authContextValue'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const hasStoredToken = tokenStorage.get() !== null
  const [loading, setLoading] = useState(hasStoredToken)

  useEffect(() => {
    if (!hasStoredToken) return
    authService.me().then(setUser).catch(error => {
      // Solo un token rechazado cierra la sesión; sin red o con el servidor caído se usa el último perfil conocido.
      if (isRejectedSession(error)) authService.logout()
      else setUser(authService.cachedUser())
    }).finally(() => setLoading(false))
  }, [hasStoredToken])

  useEffect(() => {
    function revalidate() {
      if (!tokenStorage.get()) return
      authService.me().then(setUser).catch(error => {
        if (isRejectedSession(error)) { authService.logout(); setUser(null) }
      })
    }
    window.addEventListener('online', revalidate)
    return () => window.removeEventListener('online', revalidate)
  }, [])

  async function login(request: LoginRequest) {
    const session = await authService.login(request); setUser(session.user); return session.user
  }
  async function register(request: RegisterRequest) {
    const session = await authService.register(request); setUser(session.user); return session.user
  }
  function logout() { forgetPushOnServer(tokenStorage.get()); authService.logout(); setUser(null) }
  async function refresh() { setUser(await authService.me()) }

  return <AuthContext.Provider value={{ user, loading, login, register, logout, refresh }}>{children}</AuthContext.Provider>
}

function isRejectedSession(error: unknown) {
  return error instanceof ApiError && (error.status === 401 || error.status === 403)
}
