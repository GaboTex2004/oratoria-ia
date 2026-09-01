import type { InputHTMLAttributes } from 'react'
import { Icon } from '../../../shared/components/Icon'

interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  icon?: string
  error?: string
}

export function FormField({ label, icon, error, id, ...inputProps }: FormFieldProps) {
  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      <div className="form-field__control">
        {icon && <Icon name={icon} className="form-field__icon" />}
        <input id={id} className={icon ? 'has-icon' : ''} aria-invalid={Boolean(error)} {...inputProps} />
      </div>
      {error && <span className="form-field__error" role="alert">{error}</span>}
    </div>
  )
}
