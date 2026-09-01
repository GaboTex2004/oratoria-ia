interface IconProps {
  name: string
  filled?: boolean
  className?: string
}

export function Icon({ name, filled = false, className = '' }: IconProps) {
  return (
    <span
      className={`material-symbols-outlined ${filled ? 'material-symbols-outlined--filled' : ''} ${className}`}
      aria-hidden="true"
    >
      {name}
    </span>
  )
}
