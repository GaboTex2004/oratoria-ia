import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { FormField } from '../components/FormField'
import { PasswordField } from '../components/PasswordField'
import { useAuth } from '../context/useAuth'
import { ApiError } from '../../../shared/services/api'
import type { LoginRequest } from '../types/auth'

type LoginErrors = Partial<Record<keyof LoginRequest, string>>

export function LoginPage() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const [form, setForm] = useState<LoginRequest>({ email: '', password: '' })
  const [errors, setErrors] = useState<LoginErrors>({})
  const [serverError, setServerError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function validate() {
    const next: LoginErrors = {}
    if (!form.email.trim()) next.email = 'Ingresa tu correo electrónico.'
    else if (!/^\S+@\S+\.\S+$/.test(form.email)) next.email = 'Ingresa un correo válido.'
    if (!form.password) next.password = 'Ingresa tu contraseña.'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!validate()) return
    setServerError('')
    setSubmitting(true)
    try {
      const user = await login(form)
      navigate(user.role === 'ADMIN' ? '/admin' : '/home')
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : 'No se pudo iniciar sesión.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Bienvenido de nuevo" subtitle="Mejora tu forma de comunicarte con ayuda de IA.">
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <FormField id="email" label="Correo electrónico" icon="mail" type="email" autoComplete="email" placeholder="tu@email.com" value={form.email} error={errors.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
        <div>
          <div className="auth-form__label-row"><span>Contraseña</span><button type="button" className="link-button" disabled>¿Olvidaste tu contraseña?</button></div>
          <PasswordField id="password" label="" autoComplete="current-password" placeholder="••••••••" value={form.password} error={errors.password} onChange={(event) => setForm({ ...form, password: event.target.value })} />
        </div>
        {serverError && <span className="form-field__error" role="alert">{serverError}</span>}
        <button className="button button--primary button--full" type="submit" disabled={submitting}>{submitting ? 'Ingresando…' : 'Iniciar sesión'}</button>
      </form>
      <p className="auth-switch">¿Aún no tienes una cuenta? <Link to="/register">Crear cuenta</Link></p>
    </AuthLayout>
  )
}
