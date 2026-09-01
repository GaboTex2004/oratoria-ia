import { createContext } from 'react'
import type { LoginRequest, RegisterRequest, User } from '../types/auth'

export interface AuthContextValue {
  user: User | null
  loading: boolean
  login: (request: LoginRequest) => Promise<User>
  register: (request: RegisterRequest) => Promise<User>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
