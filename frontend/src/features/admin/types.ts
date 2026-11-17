import type { TrainingType } from '../training/types/training'

export interface UserRow { id: string; name: string; email: string; role: string; enabled: boolean; plan: string }
export interface ExerciseDraft { code: string; title: string; description: string; prompt: string; objective: string; category: string; difficulty: string; duration: number; trainingType: TrainingType; active: boolean }
export interface ExerciseRow extends ExerciseDraft { id: string }
export interface CategoryDraft { code: string; name: string; description: string; active: boolean }
export interface CategoryRow extends CategoryDraft { id: string; exercises: number }
export interface EntitlementRow { code: string; kind: 'FEATURE' | 'LIMIT'; enabled: boolean; limit: number | null; unlimited: boolean }
export interface PlanAdminView { code: string; name: string; price: number | string; currency: string; active: boolean; entitlements: EntitlementRow[] }
export interface ModuleStatus { module: string; status: 'UP' | 'DOWN'; latencyMs: number; detail: string }
export interface AdminStats {
  users: number; activeUsers: number; premiumUsers: number; practicesThisMonth: number; simulationsThisMonth: number
  lastSixMonths: { month: string; practices: number; simulations: number }[]
  topExercises: { title: string; sessions: number }[]
  frequentDifficulties: { code: string; label: string; averageScore: number; evaluations: number }[]
}

export const ENTITLEMENT_LABELS: Record<string, string> = {
  SESSIONS_PER_MONTH: 'Prácticas por mes', SIMULATIONS_PER_MONTH: 'Simulaciones por mes', HISTORY_DAYS: 'Días de historial',
  BASIC_VOICE_ANALYSIS: 'Análisis de voz básico', ADVANCED_VOICE_ANALYSIS: 'Análisis de voz avanzado', CAMERA_ANALYSIS: 'Análisis con cámara',
  ADVANCED_STATISTICS: 'Estadísticas avanzadas', FULL_HISTORY: 'Historial completo', SESSION_COMPARISON: 'Comparar sesiones',
  ADAPTIVE_TRAINING: 'Entrenamiento adaptativo', IMPROVISATION: 'Improvisación', COMMUNICATION_STYLES: 'Estilos de comunicación',
  UNLIMITED_SIMULATIONS: 'Simulaciones ilimitadas',
}

export const MODULE_LABELS: Record<string, string> = { database: 'Base de datos', 'ai-service': 'Servicio de IA', 'audio-storage': 'Almacenamiento de audio' }

export function errorText(reason: unknown, fallback: string) { return reason instanceof Error ? reason.message : fallback }
