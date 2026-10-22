import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Icon } from '../../../shared/components/Icon'
import { ApiError } from '../../../shared/services/api'
import { useAuth } from '../../auth/context/useAuth'
import { Sidebar } from '../../dashboard/components/Sidebar'
import { Topbar } from '../../dashboard/components/Topbar'
import '../../dashboard/components/dashboard.css'
import { AudioRecorderPanel } from '../components/AudioRecorderPanel'
import '../components/training.css'
import { trainingService } from '../services/trainingService'
import {
  TRAINING_TYPE_META,
  formatDuration,
  type Exercise,
  type TrainingSession,
  type TrainingType,
} from '../types/training'

const ALL_TYPES = Object.keys(TRAINING_TYPE_META) as TrainingType[]

function parseTrainingType(value: string | null): TrainingType | 'ALL' {
  if (!value) return 'ALL'
  const upper = value.toUpperCase() as TrainingType
  return ALL_TYPES.includes(upper) ? upper : 'ALL'
}

export function TrainingPage() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [searchParams, setSearchParams] = useSearchParams()
  const firstName = useAuth().user?.firstName || ''

  const selectedFilter = parseTrainingType(searchParams.get('type'))
  const [exercises, setExercises] = useState<Exercise[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadCount, setReloadCount] = useState(0)
  const [creatingExerciseId, setCreatingExerciseId] = useState<string | null>(null)
  const [activePractice, setActivePractice] = useState<{
    exercise: Exercise
    session: TrainingSession
  } | null>(null)

  useEffect(() => {
    let active = true
    trainingService
      .listExercises(selectedFilter === 'ALL' ? undefined : selectedFilter)
      .then((data) => {
        if (active) setExercises(data)
      })
      .catch((err: unknown) => {
        if (active) {
          setError(
            err instanceof ApiError
              ? err.message
              : 'No se pudo cargar el catálogo de ejercicios.',
          )
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [selectedFilter, reloadCount])

  function handleFilterChange(type: TrainingType | 'ALL') {
    if (type === selectedFilter) return
    setLoading(true)
    setError('')
    if (type === 'ALL') {
      setSearchParams({})
    } else {
      setSearchParams({ type })
    }
  }

  function handleRetry() {
    setLoading(true)
    setError('')
    setReloadCount((c) => c + 1)
  }

  async function handleStartExercise(exercise: Exercise) {
    setError('')
    setCreatingExerciseId(exercise.id)
    try {
      const session = await trainingService.createSession({
        exerciseId: exercise.id,
        trainingType: exercise.trainingType,
      })
      setActivePractice({ exercise, session })
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : 'No se pudo iniciar la sesión de práctica.',
      )
    } finally {
      setCreatingExerciseId(null)
    }
  }

  return (
    <div className="dashboard-layout">
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
      <main className="dashboard-main">
        <Topbar firstName={firstName} onMenuOpen={() => setMenuOpen(true)} />
        <div className="dashboard-content">
          {activePractice ? (
            <AudioRecorderPanel
              exercise={activePractice.exercise}
              session={activePractice.session}
              onSessionCompleted={(updated) =>
                setActivePractice({ exercise: activePractice.exercise, session: updated })
              }
              onCancel={() => setActivePractice(null)}
            />
          ) : (
            <>
              <header className="page-section-header">
                <div>
                  <span className="eyebrow">Práctica guiada</span>
                  <h2><Icon name="fitness_center" /> Catálogo de entrenamientos</h2>
                  <p>Selecciona un ejercicio de oratoria, graba tu intervención y consérvala en tu historial.</p>
                </div>
              </header>

              <div className="filter-bar" role="tablist" aria-label="Filtrar por categoría">
                <button
                  type="button"
                  className={`filter-chip ${selectedFilter === 'ALL' ? 'is-active' : ''}`}
                  onClick={() => handleFilterChange('ALL')}
                >
                  Todos
                </button>
                {ALL_TYPES.map((type) => (
                  <button
                    key={type}
                    type="button"
                    className={`filter-chip ${selectedFilter === type ? 'is-active' : ''}`}
                    onClick={() => handleFilterChange(type)}
                  >
                    <Icon name={TRAINING_TYPE_META[type].icon} />
                    <span>{TRAINING_TYPE_META[type].label}</span>
                  </button>
                ))}
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
                  <p>Cargando ejercicios disponibles…</p>
                </div>
              ) : exercises.length === 0 ? (
                <div className="panel-card training-empty">
                  <Icon name="search_off" />
                  <p>No hay ejercicios activos para esta categoría.</p>
                </div>
              ) : (
                <div className="exercise-catalog-grid">
                  {exercises.map((exercise) => {
                    const meta = TRAINING_TYPE_META[exercise.trainingType]
                    const isCreating = creatingExerciseId === exercise.id
                    return (
                      <article key={exercise.id} className="training-card exercise-card">
                        <div className="training-card__head">
                          <span className="training-card__icon">
                            <Icon name={meta.icon} />
                          </span>
                          <span className={`badge badge--${meta.badgeTone}`}>{meta.label}</span>
                        </div>
                        <h3>{exercise.title}</h3>
                        <p>{exercise.description}</p>
                        <div className="exercise-card__prompt">
                          <strong>Consigna:</strong> {exercise.prompt}
                        </div>
                        <div className="exercise-card__footer">
                          <span className="exercise-card__duration">
                            <Icon name="schedule" /> {formatDuration(exercise.recommendedDurationSeconds)}
                          </span>
                          <button
                            type="button"
                            disabled={creatingExerciseId !== null}
                            onClick={() => handleStartExercise(exercise)}
                          >
                            {isCreating ? 'Creando sesión…' : 'Practicar ahora'}
                            <Icon name="mic" />
                          </button>
                        </div>
                      </article>
                    )
                  })}
                </div>
              )}
            </>
          )}
        </div>
      </main>
    </div>
  )
}
