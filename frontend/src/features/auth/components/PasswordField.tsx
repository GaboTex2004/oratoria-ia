import { useState } from 'react'
import type { InputHTMLAttributes } from 'react'
import { Icon } from '../../../shared/components/Icon'

interface PasswordFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string
  error?: string
}

export function PasswordField({ label, error, id, ...inputProps }: PasswordFieldProps) {
  const [visible, setVisible] = useState(false)

  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      <div className="form-field__control">
        <Icon name="lock" className="form-field__icon" />
        <input id={id} type={visible ? 'text' : 'password'} className="has-icon has-action" aria-invalid={Boolean(error)} {...inputProps} />
        <button className="form-field__action" type="button" onClick={() => setVisible((value) => !value)} aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}>
          <Icon name={visible ? 'visibility_off' : 'visibility'} />
        </button>
      </div>
      {error && <span className="form-field__error" role="alert">{error}</span>}
    </div>
  )
}
