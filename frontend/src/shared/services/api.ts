import { env } from '../config/env'
import { tokenStorage } from './tokenStorage'

interface ApiErrorBody { message?: string; fieldErrors?: Record<string, string> }

export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  const token = tokenStorage.get()
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${env.apiUrl}${path}`, { ...options, headers })
  if (!response.ok) {
    let body: ApiErrorBody = {}
    try { body = await response.json() as ApiErrorBody } catch { /* non-JSON response */ }
    throw new ApiError(response.status, body.message || `Error HTTP ${response.status}`, body.fieldErrors)
  }
  if (response.status === 204) return undefined as T
  const contentType = response.headers.get('content-type') || ''
  return (contentType.includes('application/json') ? response.json() : response.text()) as Promise<T>
}

export async function testBackend(): Promise<string> {
  return apiRequest<string>('/test')
}
