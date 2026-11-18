export const REPORT_PERIODS = [7, 30, 90, 365, 0] as const

export function periodLabel(days: number): string {
  return days === 0 ? 'Todo el historial' : `Últimos ${days} días`
}

/** Plan feature an exercise needs and the user lacks, or null when it can be practiced. */
export function lockedFeatureFor(
  exercise: { trainingType: string; communicationStyle?: string | null },
  has: (feature: string) => boolean,
): string | null {
  if (exercise.trainingType === 'IMPROVISATION' && !has('IMPROVISATION')) return 'IMPROVISATION'
  if (exercise.communicationStyle && !has('COMMUNICATION_STYLES')) return 'COMMUNICATION_STYLES'
  return null
}

export function toggleLimited(selected: string[], value: string, max: number): string[] {
  if (selected.includes(value)) return selected.filter(item => item !== value)
  return selected.length >= max ? selected : [...selected, value]
}

export function weeklyPercent(done: number, goal: number): number {
  return goal <= 0 ? 0 : Math.min(100, Math.round(done / goal * 100))
}
