import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Icon } from '../../../shared/components/Icon'
import { ApiError } from '../../../shared/services/api'
import { trainingService } from '../services/trainingService'
import {
  TRAINING_TYPE_META,
  formatBytes,
  formatDuration,
  type Exercise,
  type TrainingSession,
} from '../types/training'
import { AudioPlayer } from './AudioPlayer'

const MAX_DURATION_SECONDS = 300
const MAX_SIZE_BYTES = 15 * 1024 * 1024

const CANDIDATE_MIME_TYPES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/ogg;codecs=opus',
  'audio/ogg',
  'audio/mp4',
]

function detectSupportedMimeType(): string {
  if (typeof MediaRecorder === 'undefined' || typeof MediaRecorder.isTypeSupported !== 'function') {
    return ''
  }
  for (const candidate of CANDIDATE_MIME_TYPES) {
    if (MediaRecorder.isTypeSupported(candidate)) {
      return candidate
    }
  }
  return ''
}

function mapMediaError(error: unknown): string {
  if (error instanceof DOMException) {
    if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
      return 'Permiso de micrófono denegado. Autoriza el uso del micrófono en la barra de direcciones de tu navegador e inténtalo nuevamente.'
    }
    if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
      return 'No se detectó ningún micrófono conectado en este dispositivo.'
    }
    if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
      return 'No se pudo acceder al micrófono porque otra aplicación lo está utilizando.'
    }
  }
  return 'No fue posible iniciar el micrófono. Verifica tus permisos y el dispositivo de entrada.'
}

type RecorderPhase =
  | 'idle'
  | 'requesting_permission'
  | 'recording'
  | 'recorded'
  | 'uploading'
  | 'completed'

interface AudioRecorderPanelProps {
  exercise: Exercise
  session: TrainingSession
  onSessionCompleted: (updated: TrainingSession) => void
  onCancel: () => void
}

export function AudioRecorderPanel({
  exercise,
  session,
  onSessionCompleted,
  onCancel,
}: AudioRecorderPanelProps) {
  const [phase, setPhase] = useState<RecorderPhase>('idle')
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [audioBlob, setAudioBlob] = useState<Blob | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState('')

  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const mediaStreamRef = useRef<MediaStream | null>(null)
  const chunksRef = useRef<BlobPart[]>([])
  const timerRef = useRef<number | null>(null)
  const startedAtRef = useRef<number>(0)

  const categoryMeta = TRAINING_TYPE_META[exercise.trainingType]

  function stopTracks() {
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((track) => track.stop())
      mediaStreamRef.current = null
    }
  }

  function clearTimer() {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current)
      timerRef.current = null
    }
  }

  useEffect(() => {
    return () => {
      clearTimer()
      stopTracks()
    }
  }, [])

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl)
      }
    }
  }, [previewUrl])

  async function startRecording() {
    setErrorMessage('')
    if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setErrorMessage('Tu navegador no soporta grabación de audio mediante MediaRecorder.')
      return
    }

    if (previewUrl) {
      URL.revokeObjectURL(previewUrl)
      setPreviewUrl(null)
    }
    setAudioBlob(null)
    setElapsedSeconds(0)
    setPhase('requesting_permission')

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      mediaStreamRef.current = stream
      chunksRef.current = []

      const mimeType = detectSupportedMimeType()
      const recorder = mimeType
        ? new MediaRecorder(stream, { mimeType })
        : new MediaRecorder(stream)

      mediaRecorderRef.current = recorder

      recorder.ondataavailable = (event: BlobEvent) => {
        if (event.data && event.data.size > 0) {
          chunksRef.current.push(event.data)
        }
      }

      recorder.onerror = () => {
        clearTimer()
        stopTracks()
        setPhase('idle')
        setErrorMessage('Ocurrió un error durante la grabación de audio.')
      }

      recorder.onstop = () => {
        clearTimer()
        stopTracks()
        const finalDuration = Math.max(
          1,
          Math.min(MAX_DURATION_SECONDS, Math.round((Date.now() - startedAtRef.current) / 1000)),
        )
        setElapsedSeconds(finalDuration)

        const effectiveType = recorder.mimeType || mimeType || 'audio/webm'
        const blob = new Blob(chunksRef.current, { type: effectiveType })
        if (blob.size <= 0) {
          setPhase('idle')
          setErrorMessage('No se capturaron datos de audio. Intenta grabar nuevamente.')
          return
        }
        if (blob.size > MAX_SIZE_BYTES) {
          setPhase('idle')
          setErrorMessage('La grabación supera el límite máximo permitido de 15 MB.')
          return
        }

        const objectUrl = URL.createObjectURL(blob)
        setAudioBlob(blob)
        setPreviewUrl(objectUrl)
        setPhase('recorded')
      }

      startedAtRef.current = Date.now()
      recorder.start(250)
      setPhase('recording')

      timerRef.current = window.setInterval(() => {
        const seconds = Math.floor((Date.now() - startedAtRef.current) / 1000)
        setElapsedSeconds(seconds)
        if (seconds >= MAX_DURATION_SECONDS && mediaRecorderRef.current?.state === 'recording') {
          mediaRecorderRef.current.stop()
        }
      }, 500)
    } catch (error) {
      clearTimer()
      stopTracks()
      setPhase('idle')
      setErrorMessage(mapMediaError(error))
    }
  }

  function stopRecording() {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
      mediaRecorderRef.current.stop()
    }
  }

  async function submitRecording() {
    if (!audioBlob) return
    setErrorMessage('')
    setPhase('uploading')
    try {
      const completed = await trainingService.completeSession(
        session.id,
        audioBlob,
        Math.max(1, elapsedSeconds),
      )
      setPhase('completed')
      onSessionCompleted(completed)
    } catch (error) {
      setPhase('recorded')
      setErrorMessage(
        error instanceof ApiError
          ? error.message
          : 'No se pudo finalizar y guardar la sesión. Verifica tu conexión e inténtalo de nuevo.',
      )
    }
  }

  return (
    <section className="recorder-studio" aria-label="Estudio de grabación">
      <div className="recorder-studio__header">
        <div>
          <span className={`badge badge--${categoryMeta.badgeTone}`}>
            <Icon name={categoryMeta.icon} /> {categoryMeta.label}
          </span>
          <h2>{exercise.title}</h2>
          <p className="recorder-studio__subtitle">{exercise.description}</p>
        </div>
        {phase !== 'recording' && phase !== 'uploading' && (
          <button type="button" className="button button--ghost" onClick={onCancel}>
            <Icon name="arrow_back" /> Cambiar ejercicio
          </button>
        )}
      </div>

      <div className="recorder-prompt-box">
        <span className="eyebrow">Consigna del ejercicio</span>
        <p>{exercise.prompt}</p>
        <div className="recorder-prompt-box__meta">
          <span>
            <Icon name="timer" /> Duración sugerida: <strong>{formatDuration(exercise.recommendedDurationSeconds)}</strong>
          </span>
          <span>
            <Icon name="info" /> Límite máximo: <strong>{formatDuration(MAX_DURATION_SECONDS)}</strong> (15 MB)
          </span>
        </div>
      </div>

      {errorMessage && (
        <div className="training-alert training-alert--error" role="alert">
          <Icon name="error" />
          <span>{errorMessage}</span>
        </div>
      )}

      <div className={`recorder-console recorder-console--${phase}`}>
        <div className="recorder-timer">
          <span
            className={`recorder-indicator ${phase === 'recording' ? 'is-live' : ''}`}
            aria-hidden="true"
          />
          <strong>{formatDuration(elapsedSeconds)}</strong>
          <small>
            {phase === 'idle' && 'Listo para iniciar micrófono'}
            {phase === 'requesting_permission' && 'Solicitando permiso del micrófono…'}
            {phase === 'recording' && 'Grabando en vivo desde tu micrófono…'}
            {phase === 'recorded' && `Audio listo (${formatBytes(audioBlob?.size)})`}
            {phase === 'uploading' && 'Subiendo audio y finalizando sesión…'}
            {phase === 'completed' && 'Sesión guardada correctamente'}
          </small>
        </div>

        {phase === 'idle' && (
          <div className="recorder-actions">
            <button type="button" className="button button--primary" onClick={startRecording}>
              <Icon name="mic" /> Comenzar grabación
            </button>
          </div>
        )}

        {phase === 'requesting_permission' && (
          <div className="recorder-actions">
            <button type="button" className="button button--primary" disabled>
              <Icon name="sync" /> Esperando permiso de micrófono…
            </button>
          </div>
        )}

        {phase === 'recording' && (
          <div className="recorder-actions">
            <button type="button" className="button button--danger" onClick={stopRecording}>
              <Icon name="stop_circle" /> Detener grabación
            </button>
          </div>
        )}

        {(phase === 'recorded' || phase === 'uploading') && previewUrl && (
          <div className="recorder-preview">
            <div className="recorder-audio-player">
              <AudioPlayer
                key={previewUrl}
                sessionKey={`${session.id}-preview`}
                initialBlobUrl={previewUrl}
                knownDurationSeconds={elapsedSeconds}
                label="Vista previa de grabación"
              />
            </div>
            <div className="recorder-actions">
              <button
                type="button"
                className="button button--ghost"
                onClick={startRecording}
                disabled={phase === 'uploading'}
              >
                <Icon name="replay" /> Volver a grabar
              </button>
              <button
                type="button"
                className="button button--primary"
                onClick={submitRecording}
                disabled={phase === 'uploading'}
              >
                <Icon name="check_circle" />
                {phase === 'uploading' ? 'Guardando sesión…' : 'Finalizar y guardar sesión'}
              </button>
            </div>
          </div>
        )}

        {phase === 'completed' && (
          <div className="recorder-completed">
            <div className="training-alert training-alert--success">
              <Icon name="verified" />
              <div>
                <strong>¡Práctica finalizada con éxito!</strong>
                <p>Tu grabación ha sido almacenada de forma segura y el análisis de voz está en curso.</p>
              </div>
            </div>
            <div className="recorder-actions">
              <Link
                className="button button--primary"
                to={`/results?sessionId=${encodeURIComponent(session.id)}`}
              >
                <Icon name="analytics" /> Ver transcripción y resultados
              </Link>
              <Link className="button button--ghost" to="/sessions">
                <Icon name="history" /> Ver en Mis sesiones
              </Link>
              <button type="button" className="button button--ghost" onClick={onCancel}>
                <Icon name="fitness_center" /> Practicar otro ejercicio
              </button>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}
