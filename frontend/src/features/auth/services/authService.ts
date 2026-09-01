import type { AuthSession, LoginRequest, RegisterRequest } from '../types/auth'
import { apiRequest } from '../../../shared/services/api'
import { tokenStorage } from '../../../shared/services/tokenStorage'

export const authService = {
  async login(request: LoginRequest): Promise<AuthSession> {
    const session = await apiRequest<AuthSession>('/auth/login', { method: 'POST', body: JSON.stringify(request) })
    tokenStorage.set(session.token)
    return session
  },

  async register(request: RegisterRequest): Promise<AuthSession> {
    const session = await apiRequest<AuthSession>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ firstName: request.firstName, lastName: request.lastName, email: request.email, password: request.password }),
    })
    tokenStorage.set(session.token)
    return session
  },

  me: () => apiRequest<AuthSession['user']>('/users/me'),

  logout() {
    tokenStorage.clear()
  },
}
