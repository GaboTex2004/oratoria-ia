import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Icon } from '../../../shared/components/Icon'
import { ApiError } from '../../../shared/services/api'
import { useAuth } from '../../auth/context/useAuth'
import { Sidebar } from '../../dashboard/components/Sidebar'
import { Topbar } from '../../dashboard/components/Topbar'
import '../../dashboard/components/dashboard.css'
import { AudioPlayer } from '../components/AudioPlayer'
import '../components/training.css'
import { trainingService } from '../services/trainingService'
import {
  ANALYSIS_STATUS_META,
  CRITERION_LEVEL_META,
  EVALUATION_STATUS_META,
  TRAINING_TYPE_META,
  formatDuration,
  isSlowProcessing,
  shouldContinuePolling,
  type SpeechAnalysis,
  type SpeechEvaluation,
  type TrainingSession,
} from '../types/training'

function formatDateTime(iso: string | null): string {
  if (!iso) return '—'
  try {
    return new Intl.DateTimeFormat('es-BO', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(iso))
  } catch {
    return iso
  }
}

function formatDecimal(value: number | null | undefined, digits = 1): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—'
  return value.toFixed(digits)
}

function formatPercent(ratio: number | null | undefined): string {
  if (ratio === null || ratio === undefined || !Number.isFinite(ratio)) return '—'
  return `${Math.round(ratio * 100)}%`
}

export function ResultsPage() {
  const [menuOpen, setMenuOpen] = useState(false)
  const firstName = useAuth().user?.firstName || ''
  const [searchParams, setSearchParams] = useSearchParams()
  const querySessionId = searchParams.get('sessionId')

  const [sessions, setSessions] = useState<TrainingSession[]>([])
  const [loadingSessions, setLoadingSessions] = useState(true)
  const [sessionsError, setSessionsError] = useState('')

  const [analysis, setAnalysis] = useState<SpeechAnalysis | null>(null)
  const [loadingAnalysis, setLoadingAnalysis] = useState(false)
  const [retryingAnalysis, setRetryingAnalysis] = useState(false)
  const [analysisError, setAnalysisError] = useState('')
  const [analysisPollCount, setAnalysisPollCount] = useState(0)

  const [evaluation, setEvaluation] = useState<SpeechEvaluation | null>(null)
  const [loadingEvaluation, setLoadingEvaluation] = useState(false)
  const [retryingEvaluation, setRetryingEvaluation] = useState(false)
  const [evaluationError, setEvaluationError] = useState('')
  const [evaluationPollCount, setEvaluationPollCount] = useState(0)

  const completedSessions = sessions.filter((s) => s.status === 'COMPLETED' && s.hasAudio)
  const selectedSession =
    completedSessions.find((s) => s.id === querySessionId) ?? completedSessions[0] ?? null
  const selectedSessionId = selectedSession?.id ?? null

  const loadSessionsList = useCallback(() => {
    setLoadingSessions(true)
    setSessionsError('')
    trainingService
      .listSessions()
      .then((list) => {
        setSessions(list)
      })
      .catch((err: unknown) => {
        setSessionsError(
          err instanceof ApiError
            ? err.message
            : 'No se pudo cargar la lista de sesiones.',
        )
      })
      .finally(() => {
        setLoadingSessions(false)
      })
  }, [])

  useEffect(() => {
    let active = true
    trainingService
      .listSessions()
      .then((list) => {
        if (active) setSessions(list)
      })
      .catch((err: unknown) => {
        if (active) {
          setSessionsError(
            err instanceof ApiError
              ? err.message
              : 'No se pudo cargar la lista de sesiones.',
          )
        }
      })
      .finally(() => {
        if (active) setLoadingSessions(false)
      })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    if (!selectedSessionId) return
    let active = true

    trainingService
      .getSessionAnalysis(selectedSessionId)
      .then((data) => {
        if (active) {
          setAnalysis(data)
          setAnalysisError('')
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setAnalysisError(
            err instanceof ApiError
              ? err.message
              : 'No se pudo consultar el análisis de voz para esta sesión.',
          )
        }
      })
      .finally(() => {
        if (active) setLoadingAnalysis(false)
      })

    return () => {
      active = false
    }
  }, [selectedSessionId])

  const activeAnalysisStatus =
    analysis && analysis.sessionId === selectedSessionId ? analysis.status : null

  useEffect(() => {
    if (
      !selectedSessionId ||
      !shouldContinuePolling(activeAnalysisStatus, analysisPollCount)
    ) {
      return
    }
    let active = true
    const timer = window.setInterval(() => {
      trainingService
        .getSessionAnalysis(selectedSessionId)
        .then((updated) => {
          if (active) {
            setAnalysis(updated)
            setAnalysisError('')
            setAnalysisPollCount((prev) => prev + 1)
          }
        })
        .catch(() => {
          if (active) {
            setAnalysisPollCount((prev) => prev + 1)
          }
        })
    }, 2500)

    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [selectedSessionId, activeAnalysisStatus, analysisPollCount])

  // Fetch semantic evaluation once transcription is COMPLETED
  useEffect(() => {
    if (!selectedSessionId || activeAnalysisStatus !== 'COMPLETED') {
      return
    }
    let active = true
    trainingService
      .getSessionEvaluation(selectedSessionId)
      .then((data) => {
        if (active) {
          setEvaluation(data)
          setEvaluationError('')
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setEvaluationError(
            err instanceof ApiError
              ? err.message
              : 'No se pudo consultar la evaluación inteligente del discurso.',
          )
        }
      })
      .finally(() => {
        if (active) setLoadingEvaluation(false)
      })

    return () => {
      active = false
    }
  }, [selectedSessionId, activeAnalysisStatus])

  const activeEvaluationStatus =
    evaluation && evaluation.sessionId === selectedSessionId ? evaluation.status : null

  useEffect(() => {
    if (
      !selectedSessionId ||
      !shouldContinuePolling(activeEvaluationStatus, evaluationPollCount)
    ) {
      return
    }
    let active = true
    const timer = window.setInterval(() => {
      trainingService
        .getSessionEvaluation(selectedSessionId)
        .then((updated) => {
          if (active) {
            setEvaluation(updated)
            setEvaluationError('')
            setEvaluationPollCount((prev) => prev + 1)
            if (updated.status === 'COMPLETED' && updated.overallScore !== null) {
              setSessions((prev) =>
                prev.map((item) =>
                  item.id === selectedSessionId
                    ? { ...item, overallScore: updated.overallScore, evaluationStatus: 'COMPLETED' }
                    : item,
                ),
              )
            }
          }
        })
        .catch(() => {
          if (active) {
            setEvaluationPollCount((prev) => prev + 1)
          }
        })
    }, 2500)

    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [selectedSessionId, activeEvaluationStatus, evaluationPollCount])

  function handleSelectSession(sessionId: string) {
    setLoadingAnalysis(true)
    setAnalysisError('')
    setEvaluationError('')
    setAnalysisPollCount(0)
    setEvaluationPollCount(0)
    setSearchParams({ sessionId })
  }

  function handleRefreshAnalysis() {
    if (!selectedSessionId) return
    setLoadingAnalysis(true)
    setAnalysisError('')
    setAnalysisPollCount(0)
    trainingService
      .getSessionAnalysis(selectedSessionId)
      .then((data) => {
        setAnalysis(data)
      })
      .catch((err: unknown) => {
        setAnalysisError(
          err instanceof ApiError
            ? err.message
            : 'No se pudo actualizar el estado del análisis.',
        )
      })
      .finally(() => {
        setLoadingAnalysis(false)
      })
  }

  async function handleRetryAnalysis() {
    if (!selectedSessionId || retryingAnalysis) return
    setRetryingAnalysis(true)
    setAnalysisError('')
    setAnalysisPollCount(0)
    try {
      const updated = await trainingService.retrySessionAnalysis(selectedSessionId)
      setAnalysis(updated)
    } catch (err) {
      setAnalysisError(
        err instanceof ApiError
          ? err.message
          : 'No fue posible reintentar el análisis de voz.',
      )
    } finally {
      setRetryingAnalysis(false)
    }
  }

  function handleRefreshEvaluation() {
    if (!selectedSessionId) return
    setLoadingEvaluation(true)
    setEvaluationError('')
    setEvaluationPollCount(0)
    trainingService
      .getSessionEvaluation(selectedSessionId)
      .then((data) => {
        setEvaluation(data)
      })
      .catch((err: unknown) => {
        setEvaluationError(
          err instanceof ApiError
            ? err.message
            : 'No se pudo actualizar la evaluación inteligente.',
        )
      })
      .finally(() => {
        setLoadingEvaluation(false)
      })
  }

  async function handleRetryEvaluation() {
    if (!selectedSessionId || retryingEvaluation) return
    setRetryingEvaluation(true)
    setEvaluationError('')
    setEvaluationPollCount(0)
    try {
      const updated = await trainingService.retrySessionEvaluation(selectedSessionId)
      setEvaluation(updated)
    } catch (err) {
      setEvaluationError(
        err instanceof ApiError
          ? err.message
          : 'No fue posible reintentar la evaluación inteligente.',
      )
    } finally {
      setRetryingEvaluation(false)
    }
  }

  const currentAnalysis =
    analysis && analysis.sessionId === selectedSessionId ? analysis : null
  const currentEvaluation =
    evaluation && evaluation.sessionId === selectedSessionId ? evaluation : null
  const analysisSlow = isSlowProcessing(activeAnalysisStatus, analysisPollCount)
  const analysisPollingPaused =
    (activeAnalysisStatus === 'PENDING' || activeAnalysisStatus === 'PROCESSING') &&
    !shouldContinuePolling(activeAnalysisStatus, analysisPollCount)
  const evaluationSlow = isSlowProcessing(activeEvaluationStatus, evaluationPollCount)
  const evaluationPollingPaused =
    (activeEvaluationStatus === 'PENDING' || activeEvaluationStatus === 'PROCESSING') &&
    !shouldContinuePolling(activeEvaluationStatus, evaluationPollCount)
  const metrics = currentAnalysis?.metrics ?? null
  const fillerEntries = metrics?.fillerWordCounts
    ? Object.entries(metrics.fillerWordCounts)
    : []

  return (
    <div className="dashboard-layout">
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
      <main className="dashboard-main">
        <Topbar firstName={firstName} onMenuOpen={() => setMenuOpen(true)} />
        <div className="dashboard-content">
          <header className="page-section-header">
            <div>
              <span className="eyebrow">Evaluación objetiva con IA</span>
              <h2><Icon name="analytics" /> Resultados y retroalimentación inteligente</h2>
              <p>
                Reproduce tu grabación, examina tus indicadores acústicos medidos y revisa la evaluación discursiva con rúbrica estructurada.
              </p>
            </div>
            <div className="results-header-actions">
              <Link className="button button--ghost" to="/sessions">
                <Icon name="history" /> Mis sesiones
              </Link>
              <Link className="button button--primary" to="/training">
                <Icon name="mic" /> Nueva práctica
              </Link>
            </div>
          </header>

          {sessionsError && (
            <div className="training-alert training-alert--error" role="alert">
              <Icon name="error" />
              <span>{sessionsError}</span>
              <button type="button" className="button button--ghost" onClick={loadSessionsList}>
                Reintentar
              </button>
            </div>
          )}

          {loadingSessions ? (
            <div className="panel-card training-empty">
              <Icon name="sync" />
              <p>Cargando sesiones completadas…</p>
            </div>
          ) : completedSessions.length === 0 ? (
            <div className="panel-card training-empty">
              <Icon name="graphic_eq" />
              <h3>Aún no tienes sesiones completadas para analizar</h3>
              <p>Finaliza una grabación de práctica para obtener su transcripción, métricas verbales y evaluación por rúbrica.</p>
              <Link className="button button--primary" to="/training">
                <Icon name="play_arrow" /> Ir a Entrenamientos
              </Link>
            </div>
          ) : (
            <div className="results-workspace">
              <div className="panel-card results-selector-bar">
                <div className="results-selector-bar__field">
                  <label htmlFor="results-session-select">Sesión analizada</label>
                  <select
                    id="results-session-select"
                    value={selectedSession?.id || ''}
                    onChange={(event) => handleSelectSession(event.target.value)}
                  >
                    {completedSessions.map((item) => {
                      const typeLabel = TRAINING_TYPE_META[item.trainingType].label
                      return (
                        <option key={item.id} value={item.id}>
                          {(item.exerciseTitle || typeLabel)} — {formatDateTime(item.createdAt)} ({formatDuration(item.durationSeconds)})
                        </option>
                      )
                    })}
                  </select>
                </div>

                {selectedSession && (
                  <div className="results-selector-bar__audio">
                    <AudioPlayer
                      key={selectedSession.id}
                      sessionKey={selectedSession.id}
                      knownDurationSeconds={selectedSession.durationSeconds}
                      loadAudio={() => trainingService.fetchSessionAudioBlob(selectedSession.id)}
                      label={selectedSession.exerciseTitle || 'Grabación de la sesión'}
                    />
                  </div>
                )}
              </div>

              {selectedSession && currentAnalysis && (
                <div className="panel-card results-status-banner">
                  <div className="results-status-banner__info">
                    <span className={`badge badge--${TRAINING_TYPE_META[selectedSession.trainingType].badgeTone}`}>
                      <Icon name={TRAINING_TYPE_META[selectedSession.trainingType].icon} />
                      {TRAINING_TYPE_META[selectedSession.trainingType].label}
                    </span>
                    <span className={`status-pill status-pill--${ANALYSIS_STATUS_META[currentAnalysis.status].tone}`}>
                      <Icon name={ANALYSIS_STATUS_META[currentAnalysis.status].icon} />
                      {ANALYSIS_STATUS_META[currentAnalysis.status].label}
                    </span>
                    {currentEvaluation && (
                      <span className={`status-pill status-pill--${EVALUATION_STATUS_META[currentEvaluation.status].tone}`}>
                        <Icon name={EVALUATION_STATUS_META[currentEvaluation.status].icon} />
                        {EVALUATION_STATUS_META[currentEvaluation.status].label}
                      </span>
                    )}
                    {currentAnalysis.model && (
                      <span className="results-status-banner__meta">
                        STT: <strong>{currentAnalysis.model}</strong>
                      </span>
                    )}
                    {currentEvaluation?.model && (
                      <span className="results-status-banner__meta">
                        LLM: <strong>{currentEvaluation.model}</strong>
                      </span>
                    )}
                  </div>

                  <div className="results-status-banner__actions">
                    {(currentAnalysis.status === 'PENDING' || currentAnalysis.status === 'PROCESSING') && (
                      <button
                        type="button"
                        className="button button--ghost"
                        onClick={handleRefreshAnalysis}
                        disabled={loadingAnalysis}
                      >
                        <Icon name="sync" /> Actualizar estado
                      </button>
                    )}
                    {currentAnalysis.status === 'FAILED' && (
                      <button
                        type="button"
                        className="button button--primary"
                        onClick={() => void handleRetryAnalysis()}
                        disabled={retryingAnalysis}
                      >
                        <Icon name="refresh" />
                        {retryingAnalysis ? 'Reintentando…' : 'Reintentar transcripción'}
                      </button>
                    )}
                  </div>
                </div>
              )}

              {analysisError && (
                <div className="training-alert training-alert--error" role="alert">
                  <Icon name="error" />
                  <span>{analysisError}</span>
                  <button
                    type="button"
                    className="button button--ghost"
                    onClick={handleRefreshAnalysis}
                  >
                    Reintentar consulta
                  </button>
                </div>
              )}

              {loadingAnalysis && !currentAnalysis ? (
                <div className="panel-card training-empty" role="status">
                  <Icon name="sync" />
                  <h3>Consultando análisis de voz…</h3>
                  <p>Obteniendo los resultados de transcripción y métricas de la sesión seleccionada.</p>
                </div>
              ) : currentAnalysis &&
                (currentAnalysis.status === 'PENDING' || currentAnalysis.status === 'PROCESSING') ? (
                <div className="panel-card training-empty results-processing-card" role="status" aria-live="polite">
                  <Icon name={analysisPollingPaused ? 'warning' : 'hourglass_top'} />
                  <h3>
                    {analysisPollingPaused
                      ? 'La transcripción está tardando más de lo esperado'
                      : 'Etapa 1/2: Procesando transcripción y métricas verbales…'}
                  </h3>
                  <p>
                    {analysisPollingPaused
                      ? 'Pausamos las consultas automáticas para evitar esperas indefinidas. Puedes verificar el estado actual o solicitar un reintento manual.'
                      : analysisSlow
                        ? 'El servicio de transcripción sigue procesando tu audio (tiempo superior al habitual). Puedes esperar unos segundos más o verificar/reintentar manualmente.'
                        : 'Nuestro servicio de inteligencia artificial está transcribiendo tu grabación en español y calculando ritmo, pausas y muletillas. Esta vista se actualizará automáticamente.'}
                  </p>
                  <div className="results-error-card__actions">
                    <button
                      type="button"
                      className="button button--ghost"
                      onClick={handleRefreshAnalysis}
                      disabled={loadingAnalysis}
                    >
                      <Icon name="sync" /> Verificar estado ahora
                    </button>
                    {(analysisSlow || analysisPollingPaused) && (
                      <button
                        type="button"
                        className="button button--primary"
                        onClick={() => void handleRetryAnalysis()}
                        disabled={retryingAnalysis}
                      >
                        <Icon name="refresh" />
                        {retryingAnalysis ? 'Reintentando…' : 'Reintentar procesamiento'}
                      </button>
                    )}
                  </div>
                </div>
              ) : currentAnalysis && currentAnalysis.status === 'FAILED' ? (
                <div className="panel-card results-error-card" role="alert">
                  <div className="results-error-card__header">
                    <Icon name="error" />
                    <div>
                      <h3>No se pudo completar la transcripción automática</h3>
                      <p>
                        {currentAnalysis.errorMessage ||
                          'Ocurrió un problema al procesar el archivo de audio en el servicio de transcripción.'}
                      </p>
                      {currentAnalysis.errorCode && (
                        <small>Código de diagnóstico: {currentAnalysis.errorCode}</small>
                      )}
                    </div>
                  </div>
                  <div className="results-error-card__actions">
                    <button
                      type="button"
                      className="button button--primary"
                      onClick={() => void handleRetryAnalysis()}
                      disabled={retryingAnalysis}
                    >
                      <Icon name="refresh" />
                      {retryingAnalysis ? 'Solicitando análisis…' : 'Reintentar transcripción ahora'}
                    </button>
                  </div>
                </div>
              ) : currentAnalysis && currentAnalysis.status === 'COMPLETED' && metrics ? (
                <>
                  {/* BLOQUE 1: INDICADORES ACÚSTICOS Y VERBALES MEDIDOS */}
                  <div className="results-section-divider">
                    <div>
                      <span className="eyebrow">Indicadores medidos objetivamente</span>
                      <h3><Icon name="graphic_eq" /> Métricas acústicas y fluidez verbal</h3>
                    </div>
                    <span className="status-pill status-pill--success">
                      <Icon name="straighten" /> Medición determinista
                    </span>
                  </div>

                  <section className="results-metrics-grid" aria-label="Métricas de comunicación verbal">
                    <article className="panel-card results-metric-card">
                      <div className="results-metric-card__head">
                        <Icon name="speed" />
                        <span>Ritmo de habla (WPM)</span>
                      </div>
                      <strong>{formatDecimal(metrics.wordsPerMinute, 1)}</strong>
                      <small>
                        Palabras por minuto sobre {formatDuration(metrics.recordingDurationSeconds)} de grabación
                      </small>
                    </article>

                    <article className="panel-card results-metric-card">
                      <div className="results-metric-card__head">
                        <Icon name="subject" />
                        <span>Total de palabras</span>
                      </div>
                      <strong>{metrics.wordCount}</strong>
                      <small>
                        {metrics.spokenWordsPerMinute != null
                          ? `Ritmo efectivo: ${formatDecimal(metrics.spokenWordsPerMinute, 1)} WPM hablado`
                          : 'Palabras detectadas en la transcripción'}
                      </small>
                    </article>

                    <article className="panel-card results-metric-card">
                      <div className="results-metric-card__head">
                        <Icon name="record_voice_over" />
                        <span>Tiempo hablado vs. grabación</span>
                      </div>
                      <strong>
                        {metrics.estimatedSpokenDurationSeconds != null
                          ? `${formatDecimal(metrics.estimatedSpokenDurationSeconds, 1)} s`
                          : 'Sin marcas'}
                      </strong>
                      <small>
                        Grabación: {formatDuration(metrics.recordingDurationSeconds)} ({formatPercent(metrics.spokenTimeRatio)} activo)
                      </small>
                    </article>

                    <article className="panel-card results-metric-card">
                      <div className="results-metric-card__head">
                        <Icon name="chat_bubble_outline" />
                        <span>Muletillas detectadas</span>
                      </div>
                      <strong>{metrics.fillerWordsTotal}</strong>
                      <small>
                        {metrics.recordingDurationSeconds > 0
                          ? `${formatDecimal((metrics.fillerWordsTotal / metrics.recordingDurationSeconds) * 60, 2)} muletillas por minuto`
                          : '0.00 muletillas por minuto'}
                      </small>
                    </article>

                    <article className="panel-card results-metric-card">
                      <div className="results-metric-card__head">
                        <Icon name="pause_circle" />
                        <span>Pausas prolongadas (≥ 0.6 s)</span>
                      </div>
                      <strong>
                        {metrics.timestampsAvailable && metrics.estimatedPauseCount != null
                          ? metrics.estimatedPauseCount
                          : 'No disponible'}
                      </strong>
                      <small>
                        {metrics.timestampsAvailable && metrics.longestPauseSeconds != null
                          ? `Pausa máxima: ${formatDecimal(metrics.longestPauseSeconds, 2)} s · Total: ${formatDecimal(metrics.totalPauseDurationSeconds, 2)} s`
                          : 'El proveedor no devolvió marcas de tiempo detalladas'}
                      </small>
                    </article>
                  </section>

                  <div className="results-details-grid">
                    <section className="panel-card results-transcript-panel">
                      <div className="results-panel-header">
                        <div>
                          <span className="eyebrow">Texto reconocido (Español)</span>
                          <h3><Icon name="description" /> Transcripción de la sesión</h3>
                        </div>
                        <span className="badge badge--info">
                          {metrics.wordCount} palabras
                        </span>
                      </div>

                      {currentAnalysis.transcriptText ? (
                        <blockquote className="results-transcript-text">
                          {currentAnalysis.transcriptText}
                        </blockquote>
                      ) : (
                        <p className="results-empty-note">
                          No se detectaron palabras audibles en la grabación.
                        </p>
                      )}

                      {currentAnalysis.segments && currentAnalysis.segments.length > 0 && (
                        <div className="results-segments">
                          <h4>Segmentos temporales ({currentAnalysis.segments.length})</h4>
                          <ul className="results-segments__list">
                            {currentAnalysis.segments.map((seg, idx) => (
                              <li key={`${seg.startSeconds}-${idx}`} className="results-segments__item">
                                <span className="results-segments__time">
                                  {formatDecimal(seg.startSeconds, 1)}s – {formatDecimal(seg.endSeconds, 1)}s
                                </span>
                                <p>{seg.text}</p>
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </section>

                    <aside className="panel-card results-fillers-panel">
                      <div className="results-panel-header">
                        <div>
                          <span className="eyebrow">Higiene verbal</span>
                          <h3><Icon name="psychology" /> Desglose de muletillas y pausas</h3>
                        </div>
                      </div>

                      <div className="results-fillers-section">
                        <h4>Muletillas contextuales en español</h4>
                        {fillerEntries.length === 0 ? (
                          <div className="training-alert training-alert--success">
                            <Icon name="check_circle" />
                            <span>¡Excelente! No se detectaron muletillas frecuentes en tu intervención.</span>
                          </div>
                        ) : (
                          <div className="results-filler-chips">
                            {fillerEntries.map(([filler, count]) => (
                              <div key={filler} className="results-filler-chip">
                                <span className="results-filler-chip__word">“{filler}”</span>
                                <span className="results-filler-chip__count">{count}×</span>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>

                      <div className="results-pauses-summary">
                        <h4>Detalle de silencios y pausas</h4>
                        {metrics.timestampsAvailable ? (
                          <dl className="results-definition-list">
                            <div>
                              <dt>Pausas detectadas (≥ 0.6 s)</dt>
                              <dd>{metrics.estimatedPauseCount ?? 0}</dd>
                            </div>
                            <div>
                              <dt>Tiempo total en pausas</dt>
                              <dd>{formatDecimal(metrics.totalPauseDurationSeconds, 2)} s</dd>
                            </div>
                            <div>
                              <dt>Pausa más extensa</dt>
                              <dd>{formatDecimal(metrics.longestPauseSeconds, 2)} s</dd>
                            </div>
                          </dl>
                        ) : (
                          <p className="results-empty-note">
                            Las métricas de pausas requieren marcas de tiempo por palabra o segmento.
                          </p>
                        )}
                      </div>
                    </aside>
                  </div>

                  {/* BLOQUE 2: EVALUACIÓN INTELIGENTE DEL DISCURSO Y RETROALIMENTACIÓN (FASE 3) */}
                  <div className="results-section-divider">
                    <div>
                      <span className="eyebrow">Evaluación semántica por rúbrica</span>
                      <h3><Icon name="school" /> Evaluación del discurso y retroalimentación personalizada</h3>
                    </div>
                    <div className="results-section-divider__badges">
                      <span className="status-pill status-pill--warning">
                        <Icon name="auto_awesome" /> Juicio estimado por IA (basado en texto)
                      </span>
                      {currentEvaluation?.rubricVersion && (
                        <span className="status-pill status-pill--neutral">
                          Rúbrica: {currentEvaluation.rubricVersion}
                        </span>
                      )}
                    </div>
                  </div>

                  {evaluationError && (
                    <div className="training-alert training-alert--error" role="alert">
                      <Icon name="error" />
                      <span>{evaluationError}</span>
                      <button
                        type="button"
                        className="button button--ghost"
                        onClick={handleRefreshEvaluation}
                      >
                        Reintentar consulta
                      </button>
                    </div>
                  )}

                  {loadingEvaluation && !currentEvaluation ? (
                    <div className="panel-card training-empty" role="status">
                      <Icon name="sync" />
                      <h3>Consultando evaluación discursiva…</h3>
                      <p>Cargando los resultados de la evaluación por rúbrica para esta sesión.</p>
                    </div>
                  ) : !currentEvaluation ||
                    currentEvaluation.status === 'PENDING' ||
                    currentEvaluation.status === 'PROCESSING' ? (
                    <div className="panel-card training-empty results-processing-card" role="status" aria-live="polite">
                      <Icon name={evaluationPollingPaused ? 'warning' : 'psychology'} />
                      <h3>
                        {evaluationPollingPaused
                          ? 'La evaluación discursiva está tardando más de lo esperado'
                          : 'Etapa 2/2: Evaluando claridad, coherencia, organización y argumentación…'}
                      </h3>
                      <p>
                        {evaluationPollingPaused
                          ? 'Pausamos las consultas automáticas para evitar esperas indefinidas. Puedes verificar el estado de la evaluación o reintentarla manualmente.'
                          : evaluationSlow
                            ? 'El modelo de lenguaje está tardando más de lo habitual en generar la retroalimentación estructurada. Puedes verificar el estado o reintentar.'
                            : (
                              <>
                                El motor de evaluación está aplicando la rúbrica de{' '}
                                <strong>{selectedSession ? TRAINING_TYPE_META[selectedSession.trainingType].label : 'oratoria'}</strong>{' '}
                                sobre tu transcripción y preparando ejemplos personalizados de mejora.
                              </>
                            )}
                      </p>
                      <div className="results-error-card__actions">
                        <button
                          type="button"
                          className="button button--ghost"
                          onClick={handleRefreshEvaluation}
                          disabled={loadingEvaluation}
                        >
                          <Icon name="sync" /> Actualizar evaluación
                        </button>
                        {(evaluationSlow || evaluationPollingPaused) && (
                          <button
                            type="button"
                            className="button button--primary"
                            onClick={() => void handleRetryEvaluation()}
                            disabled={retryingEvaluation}
                          >
                            <Icon name="refresh" />
                            {retryingEvaluation ? 'Solicitando evaluación…' : 'Reintentar evaluación'}
                          </button>
                        )}
                      </div>
                    </div>
                  ) : currentEvaluation.status === 'FAILED' ? (
                    <div className="panel-card results-error-card" role="alert">
                      <div className="results-error-card__header">
                        <Icon name="error" />
                        <div>
                          <h3>No se pudo completar la evaluación del contenido</h3>
                          <p>
                            {currentEvaluation.errorMessage ||
                              'Ocurrió un problema al evaluar el discurso mediante el modelo de lenguaje.'}
                          </p>
                          {currentEvaluation.errorCode && (
                            <small>Código de diagnóstico: {currentEvaluation.errorCode}</small>
                          )}
                        </div>
                      </div>
                      <div className="results-error-card__actions">
                        <button
                          type="button"
                          className="button button--primary"
                          onClick={() => void handleRetryEvaluation()}
                          disabled={retryingEvaluation}
                        >
                          <Icon name="refresh" />
                          {retryingEvaluation ? 'Solicitando evaluación…' : 'Reintentar evaluación'}
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="evaluation-workspace">
                      {/* Resumen ejecutivo y puntuación global */}
                      <section className="panel-card evaluation-summary-card" aria-label="Resumen de evaluación">
                        <div className="evaluation-summary-card__main">
                          <span className="eyebrow">Síntesis diagnóstica del contenido</span>
                          <h4>Resumen ejecutivo</h4>
                          <p className="evaluation-summary-card__text">
                            {currentEvaluation.executiveSummary ||
                              'Evaluación completada para la transcripción de esta sesión.'}
                          </p>
                          <p className="evaluation-summary-card__disclaimer">
                            <Icon name="info" /> Nota metodológica: Las puntuaciones de claridad, coherencia, organización y argumentación son estimaciones generadas por IA a partir de la transcripción; no evalúan tono vocal ni lenguaje corporal.
                          </p>
                        </div>

                        <div className="evaluation-summary-card__score-box">
                          {currentEvaluation.sufficientEvidence && currentEvaluation.overallScore != null ? (
                            <>
                              <span className="evaluation-score-circle__label">Puntaje Global</span>
                              <div className="evaluation-score-circle">
                                <strong>{formatDecimal(currentEvaluation.overallScore, 1)}</strong>
                                <small>/ 100</small>
                              </div>
                              <span className="status-pill status-pill--success">
                                Evidencia suficiente
                              </span>
                            </>
                          ) : (
                            <div className="evaluation-insufficient-box" role="note">
                              <Icon name="warning_amber" />
                              <strong>Sin puntaje numérico</strong>
                              <p>
                                {currentEvaluation.insufficientEvidenceReason ||
                                  'La grabación o transcripción es demasiado breve para emitir una calificación justa.'}
                              </p>
                            </div>
                          )}
                        </div>
                      </section>

                      {/* Puntuaciones por criterio de la rúbrica */}
                      <section className="evaluation-criteria-grid" aria-label="Criterios de la rúbrica">
                        {currentEvaluation.criteriaScores.map((criterion) => {
                          const levelMeta =
                            CRITERION_LEVEL_META[criterion.level] ??
                            CRITERION_LEVEL_META.INSUFFICIENT_EVIDENCE
                          const scorePercent =
                            criterion.score != null
                              ? Math.min(100, Math.max(0, criterion.score))
                              : 0

                          return (
                            <article key={criterion.code} className="panel-card evaluation-criterion-card">
                              <div className="evaluation-criterion-card__top">
                                <div>
                                  <span className="evaluation-criterion-card__weight">
                                    Peso: {Math.round(criterion.weight * 100)}% · Juicio IA
                                  </span>
                                  <h4>{criterion.label}</h4>
                                </div>
                                <div className="evaluation-criterion-card__score">
                                  <strong>
                                    {criterion.score != null ? `${formatDecimal(criterion.score, 0)}/100` : 'N/E'}
                                  </strong>
                                  <span className={`status-pill status-pill--${levelMeta.tone}`}>
                                    {levelMeta.label}
                                  </span>
                                </div>
                              </div>

                              {criterion.score != null && (
                                <div className="evaluation-criterion-card__bar" aria-hidden="true">
                                  <div
                                    className={`evaluation-criterion-card__bar-fill is-${levelMeta.tone}`}
                                    style={{ width: `${scorePercent}%` }}
                                  />
                                </div>
                              )}

                              <p className="evaluation-criterion-card__justification">
                                {criterion.justification}
                              </p>

                              {criterion.evidenceQuotes && criterion.evidenceQuotes.length > 0 && (
                                <div className="evaluation-criterion-card__quotes">
                                  <span>Evidencia en tu discurso:</span>
                                  {criterion.evidenceQuotes.map((quote, qIdx) => (
                                    <blockquote key={`${criterion.code}-q-${qIdx}`}>
                                      “{quote}”
                                    </blockquote>
                                  ))}
                                </div>
                              )}
                            </article>
                          )
                        })}
                      </section>

                      {/* Fortalezas y aspectos por mejorar */}
                      <div className="evaluation-feedback-columns">
                        <section className="panel-card evaluation-list-card evaluation-list-card--strengths">
                          <div className="results-panel-header">
                            <div>
                              <span className="eyebrow">Lo que hiciste bien</span>
                              <h3><Icon name="thumb_up" /> Fortalezas detectadas</h3>
                            </div>
                          </div>
                          {currentEvaluation.strengths.length === 0 ? (
                            <p className="results-empty-note">
                              Se requiere una intervención más extensa para identificar fortalezas discursivas específicas.
                            </p>
                          ) : (
                            <ul className="evaluation-bullet-list">
                              {currentEvaluation.strengths.map((item, idx) => (
                                <li key={`str-${idx}`}>
                                  <Icon name="check_circle" />
                                  <span>{item}</span>
                                </li>
                              ))}
                            </ul>
                          )}
                        </section>

                        <section className="panel-card evaluation-list-card evaluation-list-card--improvements">
                          <div className="results-panel-header">
                            <div>
                              <span className="eyebrow">Oportunidades de crecimiento</span>
                              <h3><Icon name="trending_up" /> Aspectos concretos por mejorar</h3>
                            </div>
                          </div>
                          {currentEvaluation.areasForImprovement.length === 0 ? (
                            <p className="results-empty-note">
                              No se registraron observaciones críticas en esta intervención.
                            </p>
                          ) : (
                            <ul className="evaluation-bullet-list">
                              {currentEvaluation.areasForImprovement.map((item, idx) => (
                                <li key={`imp-${idx}`}>
                                  <Icon name="Build" />
                                  <span>{item}</span>
                                </li>
                              ))}
                            </ul>
                          )}
                        </section>
                      </div>

                      {/* Recomendaciones prácticas y accionables */}
                      <section className="panel-card evaluation-recommendations-panel">
                        <div className="results-panel-header">
                          <div>
                            <span className="eyebrow">Plan de acción sugerido</span>
                            <h3><Icon name="lightbulb" /> Recomendaciones prácticas para tu siguiente intento</h3>
                          </div>
                        </div>
                        <div className="evaluation-recommendations-grid">
                          {currentEvaluation.recommendations.map((rec, idx) => {
                            const prioTone =
                              rec.priority === 'ALTA'
                                ? 'danger'
                                : rec.priority === 'MEDIA'
                                  ? 'warning'
                                  : 'neutral'
                            return (
                              <article key={`rec-${idx}`} className="evaluation-rec-item">
                                <div className="evaluation-rec-item__head">
                                  <h4>{rec.title}</h4>
                                  <span className={`status-pill status-pill--${prioTone}`}>
                                    Prioridad {rec.priority.toLowerCase()}
                                  </span>
                                </div>
                                <p>{rec.description}</p>
                              </article>
                            )
                          })}
                        </div>
                      </section>

                      {/* Ejemplos de cómo mejorar una respuesta */}
                      {currentEvaluation.improvedExamples.length > 0 && (
                        <section className="panel-card evaluation-examples-panel">
                          <div className="results-panel-header">
                            <div>
                              <span className="eyebrow">Reescritura aplicada a tu discurso</span>
                              <h3><Icon name="compare_arrows" /> Ejemplos de cómo mejorar tu respuesta</h3>
                            </div>
                          </div>
                          <div className="evaluation-examples-list">
                            {currentEvaluation.improvedExamples.map((ex, idx) => (
                              <article key={`ex-${idx}`} className="evaluation-example-card">
                                <div className="evaluation-example-card__comparison">
                                  <div className="evaluation-example-card__box is-original">
                                    <span>Lo que dijiste</span>
                                    <p>“{ex.originalFragment}”</p>
                                  </div>
                                  <div className="evaluation-example-card__box is-improved">
                                    <span>Propuesta mejorada</span>
                                    <p>“{ex.improvedProposal}”</p>
                                  </div>
                                </div>
                                <footer className="evaluation-example-card__rationale">
                                  <Icon name="verified" />
                                  <span><strong>Por qué funciona mejor:</strong> {ex.rationale}</span>
                                </footer>
                              </article>
                            ))}
                          </div>
                        </section>
                      )}
                    </div>
                  )}
                </>
              ) : null}
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
