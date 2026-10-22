import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
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
  EVALUATION_STATUS_META,
  SESSION_STATUS_META,
  TRAINING_TYPE_META,
  formatBytes,
  formatDuration,
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

export function SessionsPage() {
  const [menuOpen, setMenuOpen] = useState(false)
  const firstName = useAuth().user?.firstName || ''

  const [sessions, setSessions] = useState<TrainingSession[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadCount, setReloadCount] = useState(0)

  useEffect(() => {
    let active = true
    trainingService
      .listSessions()
      .then((data) => {
        if (active) setSessions(data)
      })
      .catch((err: unknown) => {
        if (active) {
          setError(
            err instanceof ApiError
              ? err.message
              : 'No se pudo cargar tu historial de sesiones.',
          )
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [reloadCount])

  function handleRetry() {
    setLoading(true)
    setError('')
    setReloadCount((c) => c + 1)
  }

  const completedCount = sessions.filter((s) => s.status === 'COMPLETED').length
  const totalSeconds = sessions.reduce((acc, s) => acc + (s.durationSeconds || 0), 0)

  return (
    <div className="dashboard-layout">
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
      <main className="dashboard-main">
        <Topbar firstName={firstName} onMenuOpen={() => setMenuOpen(true)} />
        <div className="dashboard-content">
          <header className="page-section-header">
            <div>
              <span className="eyebrow">Historial personal</span>
              <h2><Icon name="history" /> Mis sesiones de práctica</h2>
              <p>Escucha tus grabaciones anteriores y consulta el análisis verbal de cada entrenamiento.</p>
            </div>
            <Link className="button button--primary" to="/training">
              <Icon name="mic" /> Nueva grabación
            </Link>
          </header>

          <div className="sessions-kpi-grid">
            <div className="panel-card session-kpi">
              <Icon name="library_music" />
              <div>
                <span>Sesiones registradas</span>
                <strong>{sessions.length}</strong>
              </div>
            </div>
            <div className="panel-card session-kpi">
              <Icon name="check_circle" />
              <div>
                <span>Completadas con audio</span>
                <strong>{completedCount}</strong>
              </div>
            </div>
            <div className="panel-card session-kpi">
              <Icon name="timer" />
              <div>
                <span>Tiempo total grabado</span>
                <strong>{formatDuration(totalSeconds)}</strong>
              </div>
            </div>
          </div>

          {error && (
            <div className="training-alert training-alert--error" role="alert">
              <Icon name="error" />
              <span>{error}</span>
              <button type="button" className="button button--ghost" onClick={handleRetry}>
                Reintentar
              </button>
            </div>
          )}

          {loading ? (
            <div className="panel-card training-empty">
              <Icon name="sync" />
              <p>Cargando tus sesiones…</p>
            </div>
          ) : sessions.length === 0 ? (
            <div className="panel-card training-empty">
              <Icon name="mic_none" />
              <h3>Aún no tienes sesiones registradas</h3>
              <p>Selecciona un ejercicio del catálogo y realiza tu primera grabación de voz.</p>
              <Link className="button button--primary" to="/training">
                <Icon name="play_arrow" /> Ir a Entrenamientos
              </Link>
            </div>
          ) : (
            <div className="sessions-list">
              {sessions.map((session) => {
                const typeMeta = TRAINING_TYPE_META[session.trainingType]
                const statusMeta = SESSION_STATUS_META[session.status]
                const analysisMeta = session.analysisStatus
                  ? ANALYSIS_STATUS_META[session.analysisStatus]
                  : null
                const evaluationMeta = session.evaluationStatus
                  ? EVALUATION_STATUS_META[session.evaluationStatus]
                  : null
                return (
                  <article key={session.id} className="panel-card session-item">
                    <div className="session-item__main">
                      <span className="training-card__icon">
                        <Icon name={typeMeta.icon} />
                      </span>
                      <div className="session-item__info">
                        <div className="session-item__badges">
                          <span className={`badge badge--${typeMeta.badgeTone}`}>
                            {typeMeta.label}
                          </span>
                          <span className={`status-pill status-pill--${statusMeta.tone}`}>
                            {statusMeta.label}
                          </span>
                          {analysisMeta && (
                            <span className={`status-pill status-pill--${analysisMeta.tone}`}>
                              <Icon name={analysisMeta.icon} /> {analysisMeta.label}
                            </span>
                          )}
                          {evaluationMeta && (
                            <span className={`status-pill status-pill--${evaluationMeta.tone}`}>
                              <Icon name={evaluationMeta.icon} /> {evaluationMeta.label}
                            </span>
                          )}
                          {session.overallScore != null && (
                            <span className="status-pill status-pill--success">
                              <Icon name="workspace_premium" /> Puntuación: {session.overallScore.toFixed(1)}/100
                            </span>
                          )}
                        </div>
                        <h3>{session.exerciseTitle || typeMeta.label}</h3>
                        <div className="session-item__meta">
                          <span><Icon name="calendar_today" /> {formatDateTime(session.createdAt)}</span>
                          <span><Icon name="schedule" /> Duración: {formatDuration(session.durationSeconds)}</span>
                          {session.audioSizeBytes != null && (
                            <span><Icon name="graphic_eq" /> Tamaño: {formatBytes(session.audioSizeBytes)}</span>
                          )}
                        </div>
                      </div>
                    </div>

                    <div className="session-item__actions">
                      {session.hasAudio ? (
                        <>
                          <AudioPlayer
                            key={session.id}
                            sessionKey={session.id}
                            knownDurationSeconds={session.durationSeconds}
                            loadAudio={() => trainingService.fetchSessionAudioBlob(session.id)}
                            label={session.exerciseTitle || typeMeta.label}
                          />
                          <Link
                            className="button button--primary session-item__results-btn"
                            to={`/results?sessionId=${encodeURIComponent(session.id)}`}
                          >
                            <Icon name="analytics" /> Ver resultados
                          </Link>
                        </>
                      ) : (
                        <span className="session-item__no-audio">Sin audio guardado</span>
                      )}
                    </div>
                  </article>
                )
              })}
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
