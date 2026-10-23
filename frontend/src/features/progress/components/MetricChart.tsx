import type { ChartPoint } from '../utils/progressMetrics'

interface MetricChartProps {
  points: ChartPoint[]
  color?: string
  unit?: string
  emptyMessage: string
}

export function MetricChart({ points, color = '#2170e4', unit = '', emptyMessage }: MetricChartProps) {
  if (points.length === 0) return <div className="chart-empty">{emptyMessage}</div>

  const width = 720
  const height = 250
  const padding = 34
  const values = points.map((point) => point.value)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const xFor = (index: number) => padding + (index * (width - padding * 2)) / Math.max(points.length - 1, 1)
  const yFor = (value: number) => height - padding - ((value - min) / range) * (height - padding * 2)
  const path = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${xFor(index)} ${yFor(point.value)}`).join(' ')

  return (
    <div className="metric-chart" role="img" aria-label={`Gráfico con ${points.length} mediciones`}>
      <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" aria-hidden="true">
        {[0, 1, 2, 3].map((line) => {
          const y = padding + line * ((height - padding * 2) / 3)
          return <line key={line} x1={padding} x2={width - padding} y1={y} y2={y} className="chart-gridline" />
        })}
        {points.length > 1 && <path d={path} fill="none" stroke={color} strokeWidth="4" strokeLinejoin="round" strokeLinecap="round" />}
        {points.map((point, index) => (
          <g key={`${point.sessionId}-${index}`}>
            <circle cx={xFor(index)} cy={yFor(point.value)} r="6" fill={color} />
            <title>{`${point.label}: ${point.value}${unit}`}</title>
          </g>
        ))}
      </svg>
      <div className="chart-caption">
        <span>{points[0]?.label}</span>
        <strong>{points.length} medición{points.length === 1 ? '' : 'es'}</strong>
        <span>{points.at(-1)?.label}</span>
      </div>
    </div>
  )
}
