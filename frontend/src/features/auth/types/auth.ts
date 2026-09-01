export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  firstName: string
  lastName: string
  email: string
  password: string
  confirmPassword: string
  acceptedTerms: boolean
}

export interface User {
  id: string
  firstName: string
  lastName: string
  email: string
  role: 'USER' | 'ADMIN'
  plan: 'FREE' | 'PREMIUM'
}

export interface AuthSession {
  token: string
  user: User
}
