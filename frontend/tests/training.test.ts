import assert from 'node:assert/strict'
import test from 'node:test'
import {
  ANALYSIS_STATUS_META,
  CRITERION_LEVEL_META,
  EVALUATION_STATUS_META,
  MAX_AUTO_POLL_ATTEMPTS,
  SESSION_STATUS_META,
  SLOW_POLL_THRESHOLD,
  TRAINING_TYPE_META,
  extensionForMimeType,
  formatBytes,
  formatDuration,
  isSlowProcessing,
  shouldContinuePolling,
  type AnalysisStatus,
  type CriterionLevel,
  type EvaluationStatus,
  type TrainingType,
} from '../src/features/training/types/training.ts'
import {
  calculateProgressPercent,
  createInitialAudioPlayerModel,
  resolveEffectiveDuration,
  transitionAudioPlayer,
} from '../src/features/training/utils/audioPlayerState.ts'
import type { ProgressHistoryItem } from '../src/features/progress/types/progress.ts'
import {
  buildHistorySeries,
  compareSessions,
  formatMetricValue,
  formatSignedDifference,
} from '../src/features/progress/utils/progressMetrics.ts'
import { canFinish, canRespond } from '../src/features/simulations/types/simulation.ts'

test('formatDuration formatea segundos en mm:ss de forma segura', () => {
  assert.equal(formatDuration(0), '00:00')
  assert.equal(formatDuration(45), '00:45')
  assert.equal(formatDuration(90), '01:30')
  assert.equal(formatDuration(300), '05:00')
  assert.equal(formatDuration(null), '00:00')
  assert.equal(formatDuration(-5), '00:00')
})

test('formatBytes convierte tamaños de audio a unidades legibles', () => {
  assert.equal(formatBytes(null), '—')
  assert.equal(formatBytes(0), '—')
  assert.equal(formatBytes(512), '512 B')
  assert.equal(formatBytes(2048), '2.0 KB')
  assert.equal(formatBytes(1048576), '1.00 MB')
})

test('extensionForMimeType normaliza tipos MIME de MediaRecorder con codecs', () => {
  assert.equal(extensionForMimeType('audio/webm;codecs=opus'), 'webm')
  assert.equal(extensionForMimeType('audio/ogg; codecs=opus'), 'ogg')
  assert.equal(extensionForMimeType('audio/wav'), 'wav')
  assert.equal(extensionForMimeType('audio/mp4'), 'm4a')
  assert.equal(extensionForMimeType('audio/mpeg'), 'mp3')
})

test('TRAINING_TYPE_META, SESSION_STATUS_META y ANALYSIS_STATUS_META cubren categorías y estados del dominio', () => {
  const expectedTypes: TrainingType[] = [
    'PRESENTATION',
    'INTERVIEW',
    'DEBATE',
    'SALES',
    'PROJECT_DEFENSE',
    'PITCH',
    'IMPROVISATION',
  ]
  for (const type of expectedTypes) {
    assert.ok(TRAINING_TYPE_META[type], `Falta metadato para ${type}`)
    assert.ok(TRAINING_TYPE_META[type].label.length > 0)
    assert.ok(TRAINING_TYPE_META[type].icon.length > 0)
  }

  assert.equal(SESSION_STATUS_META.RECORDING.label, 'En grabación')
  assert.equal(SESSION_STATUS_META.COMPLETED.label, 'Grabación lista')

  const expectedAnalysisStatuses: AnalysisStatus[] = [
    'PENDING',
    'PROCESSING',
    'COMPLETED',
    'FAILED',
  ]
  for (const status of expectedAnalysisStatuses) {
    assert.ok(ANALYSIS_STATUS_META[status], `Falta metadato de análisis para ${status}`)
    assert.ok(ANALYSIS_STATUS_META[status].label.length > 0)
  }

  const expectedEvalStatuses: EvaluationStatus[] = [
    'PENDING',
    'PROCESSING',
    'COMPLETED',
    'FAILED',
  ]
  for (const status of expectedEvalStatuses) {
    assert.ok(EVALUATION_STATUS_META[status], `Falta metadato de evaluación para ${status}`)
    assert.ok(EVALUATION_STATUS_META[status].label.length > 0)
  }

  const expectedLevels: CriterionLevel[] = [
    'INSUFFICIENT_EVIDENCE',
    'INICIAL',
    'EN_DESARROLLO',
    'COMPETENTE',
    'SOBRESALIENTE',
  ]
  for (const level of expectedLevels) {
    assert.ok(CRITERION_LEVEL_META[level], `Falta metadato de nivel para ${level}`)
    assert.ok(CRITERION_LEVEL_META[level].label.length > 0)
  }
})

test('AudioPlayer state machine: carga correcta, reproducción, pausa y finalización', () => {
  let state = createInitialAudioPlayerModel('session-1', 42, false)
  assert.equal(state.status, 'idle')
  assert.equal(state.duration, 42)

  state = transitionAudioPlayer(state, { type: 'LOAD_START' }, 42)
  assert.equal(state.status, 'loading')
  assert.equal(state.errorMessage, null)

  // MediaRecorder WebM files often report Infinity as media duration; fallback to knownDurationSeconds (42)
  state = transitionAudioPlayer(state, { type: 'LOAD_SUCCESS', duration: Number.POSITIVE_INFINITY }, 42)
  assert.equal(state.status, 'ready')
  assert.equal(state.duration, 42)

  state = transitionAudioPlayer(state, { type: 'PLAY' }, 42)
  assert.equal(state.status, 'playing')

  state = transitionAudioPlayer(
    state,
    { type: 'TIME_UPDATE', currentTime: 21, mediaDuration: Number.POSITIVE_INFINITY },
    42,
  )
  assert.equal(state.currentTime, 21)
  assert.equal(calculateProgressPercent(state.currentTime, state.duration), 50)

  state = transitionAudioPlayer(state, { type: 'PAUSE' }, 42)
  assert.equal(state.status, 'paused')

  state = transitionAudioPlayer(state, { type: 'ENDED' }, 42)
  assert.equal(state.status, 'ready')
  assert.equal(state.currentTime, 0)
})

test('AudioPlayer state machine: fallo de descarga y reintento exitoso', () => {
  let state = createInitialAudioPlayerModel('session-err', 30, false)
  state = transitionAudioPlayer(state, { type: 'LOAD_START' }, 30)
  assert.equal(state.status, 'loading')

  state = transitionAudioPlayer(
    state,
    { type: 'LOAD_ERROR', message: 'Error de red al descargar el audio.' },
    30,
  )
  assert.equal(state.status, 'error')
  assert.equal(state.errorMessage, 'Error de red al descargar el audio.')

  // Retry transition clears error and moves back to loading -> ready
  state = transitionAudioPlayer(state, { type: 'LOAD_START' }, 30)
  assert.equal(state.status, 'loading')
  assert.equal(state.errorMessage, null)

  state = transitionAudioPlayer(state, { type: 'LOAD_SUCCESS', duration: 29.8 }, 30)
  assert.equal(state.status, 'ready')
  assert.equal(state.duration, 29.8)
})

test('AudioPlayer state machine: cambio de sesión reinicia estado y evita reproducir sesión anterior', () => {
  let state = createInitialAudioPlayerModel('session-A', 60, true)
  state = transitionAudioPlayer(state, { type: 'PLAY' }, 60)
  state = transitionAudioPlayer(state, { type: 'TIME_UPDATE', currentTime: 35, mediaDuration: 60 }, 60)
  assert.equal(state.sessionKey, 'session-A')
  assert.equal(state.status, 'playing')
  assert.equal(state.currentTime, 35)

  state = transitionAudioPlayer(
    state,
    {
      type: 'RESET',
      sessionKey: 'session-B',
      fallbackDuration: 25,
    },
    25,
  )
  assert.equal(state.sessionKey, 'session-B')
  assert.equal(state.status, 'idle')
  assert.equal(state.currentTime, 0)
  assert.equal(state.duration, 25)
  assert.equal(state.errorMessage, null)
  assert.equal(resolveEffectiveDuration(Number.NaN, 25), 25)
})

test('shouldContinuePolling detiene el polling en estados COMPLETED/FAILED o al alcanzar límite máximo', () => {
  assert.equal(shouldContinuePolling('PENDING', 0), true)
  assert.equal(shouldContinuePolling('PROCESSING', 5), true)
  assert.equal(shouldContinuePolling('COMPLETED', 1), false)
  assert.equal(shouldContinuePolling('FAILED', 1), false)
  assert.equal(shouldContinuePolling(null, 0), false)
  assert.equal(shouldContinuePolling('PENDING', MAX_AUTO_POLL_ATTEMPTS), false)
  assert.equal(shouldContinuePolling('PROCESSING', MAX_AUTO_POLL_ATTEMPTS + 2), false)
})

test('isSlowProcessing detecta procesamiento lento sin activarse en estados terminales', () => {
  assert.equal(isSlowProcessing('PENDING', 0), false)
  assert.equal(isSlowProcessing('PROCESSING', SLOW_POLL_THRESHOLD - 1), false)
  assert.equal(isSlowProcessing('PENDING', SLOW_POLL_THRESHOLD), true)
  assert.equal(isSlowProcessing('PROCESSING', SLOW_POLL_THRESHOLD + 3), true)
  assert.equal(isSlowProcessing('COMPLETED', SLOW_POLL_THRESHOLD + 5), false)
  assert.equal(isSlowProcessing('FAILED', SLOW_POLL_THRESHOLD + 5), false)
})

function progressSession(
  id: string,
  createdAt: string,
  values: Partial<ProgressHistoryItem> = {},
): ProgressHistoryItem {
  return {
    sessionId: id,
    exerciseTitle: `Sesión ${id}`,
    trainingType: 'PRESENTATION',
    createdAt,
    completedAt: createdAt,
    durationSeconds: 60,
    sessionStatus: 'COMPLETED',
    analysisStatus: 'COMPLETED',
    evaluationStatus: 'COMPLETED',
    sufficientEvidence: true,
    overallScore: 70,
    wordCount: 120,
    wordsPerMinute: 120,
    spokenWordsPerMinute: 130,
    fillerWordsTotal: 4,
    estimatedPauseCount: 3,
    spokenTimeRatio: 0.8,
    criteriaScores: [
      { code: 'CLARITY', label: 'Claridad', score: 70, level: 'COMPETENTE' },
      { code: 'COHERENCE', label: 'Coherencia', score: 70, level: 'COMPETENTE' },
      { code: 'ORGANIZATION', label: 'Organización', score: 70, level: 'COMPETENTE' },
      { code: 'ARGUMENTATION', label: 'Argumentación', score: 70, level: 'COMPETENTE' },
    ],
    ...values,
  }
}

test('series de progreso se ordenan cronológicamente y omiten null sin inventar ceros', () => {
  const history = [
    progressSession('new', '2026-10-03T10:00:00Z', { overallScore: 82 }),
    progressSession('missing', '2026-10-02T10:00:00Z', { overallScore: null }),
    progressSession('old', '2026-10-01T10:00:00Z', { overallScore: 74 }),
  ]
  const series = buildHistorySeries(history, (session) => session.overallScore)
  assert.deepEqual(series.map((point) => [point.sessionId, point.value]), [['old', 74], ['new', 82]])
})

test('comparador calcula B menos A y respeta la naturaleza de cada métrica', () => {
  const left = progressSession('a', '2026-10-01T10:00:00Z')
  const right = progressSession('b', '2026-10-02T10:00:00Z', {
    overallScore: 80,
    wordsPerMinute: 145,
    fillerWordsTotal: 2,
    criteriaScores: left.criteriaScores.map((criterion) => ({ ...criterion, score: 80 })),
  })
  const comparison = compareSessions(left, right)
  assert.equal(comparison.find((item) => item.key === 'score')?.difference, 10)
  assert.equal(comparison.find((item) => item.key === 'score')?.trend, 'positive')
  assert.equal(comparison.find((item) => item.key === 'fillers')?.difference, -2)
  assert.equal(comparison.find((item) => item.key === 'fillers')?.trend, 'positive')
  assert.equal(comparison.find((item) => item.key === 'wpm')?.trend, 'neutral')
})

test('comparador conserva valores ausentes como no comparables', () => {
  const left = progressSession('a', '2026-10-01T10:00:00Z', { overallScore: null, wordsPerMinute: null })
  const right = progressSession('b', '2026-10-02T10:00:00Z', { overallScore: 80, wordsPerMinute: 130 })
  const comparison = compareSessions(left, right)
  const score = comparison.find((item) => item.key === 'score')
  assert.equal(score?.left, null)
  assert.equal(score?.difference, null)
  assert.equal(score?.trend, 'unavailable')
  assert.equal(formatSignedDifference(score?.difference ?? null, '/100'), 'No comparable')
})

test('formateo de comparación distingue porcentajes, unidades y null', () => {
  assert.equal(formatMetricValue(null, ' PPM'), 'No disponible')
  assert.equal(formatMetricValue(0.82, ''), '82%')
  assert.equal(formatMetricValue(140, ' PPM'), '140 PPM')
  assert.equal(formatSignedDifference(-0.1, ''), '-10 pp')
  assert.equal(formatSignedDifference(5, '/100'), '+5/100')
})

test('simulation state guards prevent overlapping turns', () => {
  assert.equal(canRespond('ACTIVE'), true)
  assert.equal(canRespond('PROCESSING'), false)
  assert.equal(canRespond('COMPLETED'), false)
  assert.equal(canFinish({ status: 'ACTIVE', currentTurn: 1 }), true)
  assert.equal(canFinish({ status: 'READY_TO_FINISH', currentTurn: 3 }), true)
  assert.equal(canFinish({ status: 'ACTIVE', currentTurn: 0 }), false)
  assert.equal(canFinish({ status: 'COMPLETED', currentTurn: 3 }), false)
})
