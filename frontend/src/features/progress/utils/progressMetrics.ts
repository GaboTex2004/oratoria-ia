import type {
  CriterionCode,
  ProgressHistoryItem,
} from '../types/progress'

export interface ChartPoint {
  sessionId: string
  label: string
  value: number
}

export type ComparisonNature = 'higher' | 'lower' | 'neutral'
export type ComparisonTrend = 'positive' | 'negative' | 'neutral' | 'unavailable'

export interface ComparisonMetric {
  key: string
  label: string
  left: number | null
  right: number | null
  difference: number | null
  unit: string
  nature: ComparisonNature
  trend: ComparisonTrend
}

function isNumber(value: number | null | undefined): value is number {
  return value != null && Number.isFinite(value)
}

function chronological(items: ProgressHistoryItem[]): ProgressHistoryItem[] {
  return [...items].sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt))
}

function pointLabel(item: ProgressHistoryItem): string {
  return new Intl.DateTimeFormat('es-BO', { day: '2-digit', month: 'short' })
    .format(new Date(item.completedAt || item.createdAt))
}

export function buildHistorySeries(
  history: ProgressHistoryItem[],
  selector: (item: ProgressHistoryItem) => number | null,
): ChartPoint[] {
  return chronological(history).flatMap((item) => {
    const value = selector(item)
    return isNumber(value)
      ? [{ sessionId: item.sessionId, label: pointLabel(item), value }]
      : []
  })
}

export function criterionValue(
  session: ProgressHistoryItem,
  code: CriterionCode,
): number | null {
  return session.criteriaScores.find((criterion) => criterion.code === code)?.score ?? null
}

function metric(
  key: string,
  label: string,
  left: number | null,
  right: number | null,
  unit: string,
  nature: ComparisonNature,
): ComparisonMetric {
  const comparable = isNumber(left) && isNumber(right)
  const difference = comparable ? Math.round((right - left) * 100) / 100 : null
  let trend: ComparisonTrend = 'unavailable'
  if (difference != null) {
    if (difference === 0 || nature === 'neutral') trend = 'neutral'
    else if (nature === 'higher') trend = difference > 0 ? 'positive' : 'negative'
    else trend = difference < 0 ? 'positive' : 'negative'
  }
  return { key, label, left, right, difference, unit, nature, trend }
}

export function compareSessions(
  left: ProgressHistoryItem,
  right: ProgressHistoryItem,
): ComparisonMetric[] {
  return [
    metric('score', 'Puntuación general', left.overallScore, right.overallScore, '/100', 'higher'),
    metric('clarity', 'Claridad', criterionValue(left, 'CLARITY'), criterionValue(right, 'CLARITY'), '/100', 'higher'),
    metric('coherence', 'Coherencia', criterionValue(left, 'COHERENCE'), criterionValue(right, 'COHERENCE'), '/100', 'higher'),
    metric('organization', 'Organización', criterionValue(left, 'ORGANIZATION'), criterionValue(right, 'ORGANIZATION'), '/100', 'higher'),
    metric('argumentation', 'Argumentación', criterionValue(left, 'ARGUMENTATION'), criterionValue(right, 'ARGUMENTATION'), '/100', 'higher'),
    metric('wpm', 'Palabras por minuto', left.wordsPerMinute, right.wordsPerMinute, ' PPM', 'neutral'),
    metric('fillers', 'Muletillas', left.fillerWordsTotal, right.fillerWordsTotal, '', 'lower'),
    metric('pauses', 'Pausas estimadas', left.estimatedPauseCount, right.estimatedPauseCount, '', 'neutral'),
    metric('spokenRatio', 'Tiempo hablado', left.spokenTimeRatio, right.spokenTimeRatio, '', 'neutral'),
    metric('duration', 'Duración', left.durationSeconds, right.durationSeconds, ' s', 'neutral'),
  ]
}

export function formatMetricValue(value: number | null, unit: string): string {
  if (!isNumber(value)) return 'No disponible'
  if (unit === '' && value >= 0 && value <= 1 && !Number.isInteger(value)) {
    return `${Math.round(value * 100)}%`
  }
  return `${Number.isInteger(value) ? value : value.toFixed(2)}${unit}`
}

export function formatSignedDifference(value: number | null, unit: string): string {
  if (!isNumber(value)) return 'No comparable'
  const sign = value > 0 ? '+' : ''
  if (unit === '' && Math.abs(value) < 1 && value !== 0) {
    return `${sign}${Math.round(value * 100)} pp`
  }
  return `${sign}${Number.isInteger(value) ? value : value.toFixed(2)}${unit}`
}
