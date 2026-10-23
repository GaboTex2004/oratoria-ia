import type {
  AnalysisStatus,
  EvaluationStatus,
  SessionStatus,
  TrainingType,
} from '../../training/types/training'

export type CriterionCode = 'CLARITY' | 'COHERENCE' | 'ORGANIZATION' | 'ARGUMENTATION'

export interface ProgressOverview {
  totalSessions: number
  completedSessions: number
  evaluatedSessions: number
  totalPracticeSeconds: number
  latestOverallScore: number | null
  averageOverallScore: number | null
  scoreDelta: number | null
  latestWordsPerMinute: number | null
  averageWordsPerMinute: number | null
  latestFillerWords: number | null
  averageFillerWords: number | null
  fillerDelta: number | null
}

export interface ProgressCriterionScore {
  code: CriterionCode
  label: string | null
  score: number | null
  level: string | null
}

export interface ProgressHistoryItem {
  sessionId: string
  exerciseTitle: string | null
  trainingType: TrainingType
  createdAt: string
  completedAt: string | null
  durationSeconds: number | null
  sessionStatus: SessionStatus
  analysisStatus: AnalysisStatus | null
  evaluationStatus: EvaluationStatus | null
  sufficientEvidence: boolean | null
  overallScore: number | null
  wordCount: number | null
  wordsPerMinute: number | null
  spokenWordsPerMinute: number | null
  fillerWordsTotal: number | null
  estimatedPauseCount: number | null
  spokenTimeRatio: number | null
  criteriaScores: ProgressCriterionScore[]
}

export interface CriterionPoint {
  sessionId: string
  completedAt: string | null
  score: number
}

export interface CriterionEvolution {
  code: CriterionCode
  latestValue: number | null
  averageValue: number | null
  delta: number | null
  series: CriterionPoint[]
}

export interface FillerCount {
  filler: string
  count: number
}

export interface ProgressInsights {
  recentStrengths: string[]
  recentAreasForImprovement: string[]
  frequentFillers: FillerCount[]
}

export interface SubscriptionProgressSummary {
  plan: string | null
  sessionsLimit: number | null
  unlimitedSessions: boolean
  sessionsUsedInPeriod: number | null
  usageDefinition: string
}

export interface ProgressSummary {
  overview: ProgressOverview
  history: ProgressHistoryItem[]
  criteriaEvolution: CriterionEvolution[]
  insights: ProgressInsights
  subscription: SubscriptionProgressSummary
  historyLimit: number
  historyTruncated: boolean
}

export const CRITERION_META: Record<CriterionCode, { label: string; color: string }> = {
  CLARITY: { label: 'Claridad', color: '#2170e4' },
  COHERENCE: { label: 'Coherencia', color: '#7c3aed' },
  ORGANIZATION: { label: 'Organización', color: '#00875a' },
  ARGUMENTATION: { label: 'Argumentación', color: '#d05a00' },
}
