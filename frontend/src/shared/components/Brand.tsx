interface BrandProps {
  compact?: boolean
}

export function Brand({ compact = false }: BrandProps) {
  return (
    <div className={`brand ${compact ? 'brand--compact' : ''}`}>
      <span className="brand__mark" aria-hidden="true">
        <span className="material-symbols-outlined">graphic_eq</span>
      </span>
      {!compact && (
        <span className="brand__copy">
          <strong>Oratoria IA</strong>
          <small>Tu coach personal</small>
        </span>
      )}
    </div>
  )
}
