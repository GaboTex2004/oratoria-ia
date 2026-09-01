import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { FormField } from '../components/FormField'
import { PasswordField } from '../components/PasswordField'
import { useAuth } from '../context/useAuth'
import { ApiError } from '../../../shared/services/api'
import type { RegisterRequest } from '../types/auth'

type RegisterErrors = Partial<Record<keyof RegisterRequest, string>>
const initialForm: RegisterRequest = { firstName: '', lastName: '', email: '', password: '', confirmPassword: '', acceptedTerms: false }

export function RegisterPage() {
  const navigate = useNavigate()
  const { register } = useAuth()
  const [form, setForm] = useState(initialForm)
  const [errors, setErrors] = useState<RegisterErrors>({})
  const [serverError, setServerError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function validate() {
    const next: RegisterErrors = {}
    if (!form.firstName.trim()) next.firstName = 'Ingresa tu nombre.'
    if (!form.lastName.trim()) next.lastName = 'Ingresa tu apellido.'
    if (!form.email.trim()) next.email = 'Ingresa tu correo electrónico.'
    else if (!/^\S+@\S+\.\S+$/.test(form.email)) next.email = 'Ingresa un correo válido.'
    if (form.password.length < 8) next.password = 'Usa al menos 8 caracteres.'
    if (form.confirmPassword !== form.password) next.confirmPassword = 'Las contraseñas no coinciden.'
    if (!form.acceptedTerms) next.acceptedTerms = 'Debes aceptar los términos.'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!validate()) return
    setServerError('')
    setSubmitting(true)
    try {
      await register(form)
      navigate('/home')
    } catch (error) {
      if (error instanceof ApiError) setErrors((current) => ({ ...current, ...error.fieldErrors }))
      setServerError(error instanceof ApiError ? error.message : 'No se pudo crear la cuenta.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Crear cuenta" subtitle="Únete a Oratoria IA y perfecciona tu comunicación." variant="register">
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <div className="auth-form__two-columns">
          <FormField id="firstName" label="Nombre" placeholder="Ej. Gabriel" value={form.firstName} error={errors.firstName} onChange={(event) => setForm({ ...form, firstName: event.target.value })} />
          <FormField id="lastName" label="Apellido" placeholder="Ej. Gómez" value={form.lastName} error={errors.lastName} onChange={(event) => setForm({ ...form, lastName: event.target.value })} />
        </div>
        <FormField id="registerEmail" label="Correo electrónico" icon="mail" type="email" autoComplete="email" placeholder="tu@correo.com" value={form.email} error={errors.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
        <div className="auth-form__two-columns">
          <PasswordField id="registerPassword" label="Contraseña" autoComplete="new-password" placeholder="••••••••" value={form.password} error={errors.password} onChange={(event) => setForm({ ...form, password: event.target.value })} />
          <PasswordField id="confirmPassword" label="Confirmar contraseña" autoComplete="new-password" placeholder="••••••••" value={form.confirmPassword} error={errors.confirmPassword} onChange={(event) => setForm({ ...form, confirmPassword: event.target.value })} />
        </div>
        <label className="terms-field"><input type="checkbox" checked={form.acceptedTerms} onChange={(event) => setForm({ ...form, acceptedTerms: event.target.checked })} /><span>Acepto los <button type="button" className="link-button">términos y condiciones</button>.</span></label>
        {errors.acceptedTerms && <span className="form-field__error" role="alert">{errors.acceptedTerms}</span>}
        {serverError && <span className="form-field__error" role="alert">{serverError}</span>}
        <button className="button button--primary button--full" type="submit" disabled={submitting}>{submitting ? 'Creando cuenta…' : 'Crear mi cuenta'}</button>
      </form>
      <p className="auth-switch">¿Ya tienes una cuenta? <Link to="/login">Iniciar sesión</Link></p>
    </AuthLayout>
  )
}
