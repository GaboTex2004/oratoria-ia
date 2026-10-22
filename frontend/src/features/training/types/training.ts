export type TrainingType =
  | 'PRESENTATION'
  | 'INTERVIEW'
  | 'DEBATE'
  | 'SALES'
  | 'PROJECT_DEFENSE'
  | 'PITCH'
  | 'IMPROVISATION'

export type SessionStatus =
  | 'CREATED'
  | 'RECORDING'
  | 'UPLOADED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'

export type AnalysisStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'

export type EvaluationStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'

export type CriterionLevel =
  | 'INSUFFICIENT_EVIDENCE'
  | 'INICIAL'
  | 'EN_DESARROLLO'
  | 'COMPETENTE'
  | 'SOBRESALIENTE'

export interface Exercise {
  id: string
  code: string
  title: string
  description: string
  prompt: string
  trainingType: TrainingType
  recommendedDurationSeconds: number
}

export interface TrainingSession {
  id: string
  trainingType: TrainingType
  status: SessionStatus
  analysisStatus?: AnalysisStatus | null
  evaluationStatus?: EvaluationStatus | null
  exerciseId: string | null
  exerciseTitle: string | null
  createdAt: string
  completedAt: string | null
  durationSeconds: number | null
  overallScore: number | null
  hasAudio: boolean
  audioMimeType: string | null
  audioSizeBytes: number | null
}

export interface TranscriptSegment {
  index: number
  startSeconds: number
  endSeconds: number
  text: string
}

export interface WordTimestamp {
  word: string
  startSeconds: number
  endSeconds: number
}

export interface VerbalMetrics {
  wordCount: number
  recordingDurationSeconds: number
  estimatedSpokenDurationSeconds: number | null
  spokenTimeRatio: number | null
  wordsPerMinute: number
  spokenWordsPerMinute: number | null
  fillerWordsTotal: number
  fillerWordCounts: Record<string, number>
  estimatedPauseCount: number | null
  totalPauseDurationSeconds: number | null
  longestPauseSeconds: number | null
  timestampsAvailable: boolean
}

export interface SpeechAnalysis {
  id: string
  sessionId: string
  status: AnalysisStatus
  schemaVersion: string
  provider: string | null
  model: string | null
  language: string | null
  transcriptText: string | null
  segments: TranscriptSegment[]
  words: WordTimestamp[]
  metrics: VerbalMetrics | null
  attemptCount: number
  errorCode: string | null
  errorMessage: string | null
  retryable: boolean
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  updatedAt: string
}

export interface MeasuredIndicator {
  code: string
  label: string
  value: number | null
  unit: string
  interpretation: string
  source: 'MEASURED'
}

export interface CriterionScore {
  code: 'CLARITY' | 'COHERENCE' | 'ORGANIZATION' | 'ARGUMENTATION'
  label: string
  weight: number
  score: number | null
  level: CriterionLevel
  justification: string
  evidenceQuotes: string[]
  judgmentType: 'AI_ESTIMATED'
}

export interface ActionableRecommendation {
  title: string
  description: string
  priority: 'ALTA' | 'MEDIA' | 'SUGERIDA'
}

export interface ImprovedResponseExample {
  originalFragment: string
  improvedProposal: string
  rationale: string
}

export interface SpeechEvaluation {
  id: string
  sessionId: string
  status: EvaluationStatus
  schemaVersion: string
  rubricVersion: string | null
  provider: string | null
  model: string | null
  sufficientEvidence: boolean | null
  insufficientEvidenceReason: string | null
  overallScore: number | null
  executiveSummary: string | null
  measuredIndicators: MeasuredIndicator[]
  criteriaScores: CriterionScore[]
  strengths: string[]
  areasForImprovement: string[]
  recommendations: ActionableRecommendation[]
  improvedExamples: ImprovedResponseExample[]
  attemptCount: number
  errorCode: string | null
  errorMessage: string | null
  retryable: boolean
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  updatedAt: string
}

export interface CreateSessionPayload {
  exerciseId?: string
  trainingType?: TrainingType
}

export const TRAINING_TYPE_META: Record<TrainingType, { label: string; icon: string; badgeTone: 'blue' | 'coral' }> = {
  PRESENTATION: { label: 'Presentación', icon: 'co_present', badgeTone: 'blue' },
  INTERVIEW: { label: 'Entrevista', icon: 'work', badgeTone: 'blue' },
  DEBATE: { label: 'Debate', icon: 'forum', badgeTone: 'coral' },
  PITCH: { label: 'Pitch', icon: 'campaign', badgeTone: 'coral' },
  PROJECT_DEFENSE: { label: 'Defensa de proyecto', icon: 'school', badgeTone: 'blue' },
  SALES: { label: 'Ventas consultivas', icon: 'handshake', badgeTone: 'coral' },
  IMPROVISATION: { label: 'Improvisación', icon: 'bolt', badgeTone: 'blue' },
}

export const SESSION_STATUS_META: Record<SessionStatus, { label: string; tone: 'success' | 'warning' | 'neutral' | 'danger' }> = {
  CREATED: { label: 'Creada', tone: 'neutral' },
  RECORDING: { label: 'En grabación', tone: 'warning' },
  UPLOADED: { label: 'Audio subido', tone: 'warning' },
  PROCESSING: { label: 'Procesando IA', tone: 'warning' },
  COMPLETED: { label: 'Grabación lista', tone: 'success' },
  FAILED: { label: 'Fallida', tone: 'danger' },
}

export const ANALYSIS_STATUS_META: Record<AnalysisStatus, { label: string; tone: 'success' | 'warning' | 'neutral' | 'danger'; icon: string }> = {
  PENDING: { label: 'Análisis pendiente', tone: 'neutral', icon: 'hourglass_top' },
  PROCESSING: { label: 'Analizando voz…', tone: 'warning', icon: 'sync' },
  COMPLETED: { label: 'Análisis completado', tone: 'success', icon: 'task_alt' },
  FAILED: { label: 'Análisis pendiente de reintento', tone: 'danger', icon: 'error' },
}

export const EVALUATION_STATUS_META: Record<EvaluationStatus, { label: string; tone: 'success' | 'warning' | 'neutral' | 'danger'; icon: string }> = {
  PENDING: { label: 'Evaluación en cola', tone: 'neutral', icon: 'hourglass_top' },
  PROCESSING: { label: 'Evaluando discurso…', tone: 'warning', icon: 'psychology' },
  COMPLETED: { label: 'Evaluación lista', tone: 'success', icon: 'verified' },
  FAILED: { label: 'Evaluación pendiente de reintento', tone: 'danger', icon: 'error' },
}

export const CRITERION_LEVEL_META: Record<CriterionLevel, { label: string; tone: 'success' | 'warning' | 'neutral' | 'danger' }> = {
  SOBRESALIENTE: { label: 'Sobresaliente', tone: 'success' },
  COMPETENTE: { label: 'Competente', tone: 'success' },
  EN_DESARROLLO: { label: 'En desarrollo', tone: 'warning' },
  INICIAL: { label: 'Inicial', tone: 'danger' },
  INSUFFICIENT_EVIDENCE: { label: 'Evidencia insuficiente', tone: 'neutral' },
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null || seconds < 0 || Number.isNaN(seconds) || !Number.isFinite(seconds)) return '00:00'
  const totalSeconds = Math.floor(seconds)
  const mins = Math.floor(totalSeconds / 60)
  const secs = totalSeconds % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null || bytes <= 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

export function extensionForMimeType(mimeType: string): string {
  const normalized = mimeType.split(';')[0].trim().toLowerCase()
  switch (normalized) {
    case 'audio/ogg':
      return 'ogg'
    case 'audio/wav':
    case 'audio/wave':
    case 'audio/x-wav':
      return 'wav'
    case 'audio/mp4':
    case 'audio/x-m4a':
      return 'm4a'
    case 'audio/mpeg':
    case 'audio/mp3':
      return 'mp3'
    case 'audio/aac':
      return 'aac'
    default:
      return 'webm'
  }
}

export const MAX_AUTO_POLL_ATTEMPTS = 24
export const SLOW_POLL_THRESHOLD = 6

export function shouldContinuePolling(
  status: AnalysisStatus | EvaluationStatus | null | undefined,
  pollCount: number,
  maxPolls: number = MAX_AUTO_POLL_ATTEMPTS,
): boolean {
  if (status !== 'PENDING' && status !== 'PROCESSING') {
    return false
  }
  return pollCount < maxPolls
}

export function isSlowProcessing(
  status: AnalysisStatus | EvaluationStatus | null | undefined,
  pollCount: number,
  thresholdPolls: number = SLOW_POLL_THRESHOLD,
): boolean {
  if (status !== 'PENDING' && status !== 'PROCESSING') {
    return false
  }
  return pollCount >= thresholdPolls
}
