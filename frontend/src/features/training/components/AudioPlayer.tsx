import { useCallback, useEffect, useRef, useState } from 'react'
import { Icon } from '../../../shared/components/Icon'
import { ApiError } from '../../../shared/services/api'
import { formatDuration } from '../types/training'
import {
  calculateProgressPercent,
  createInitialAudioPlayerModel,
  resolveEffectiveDuration,
  transitionAudioPlayer,
  type AudioPlayerModel,
} from '../utils/audioPlayerState'

interface AudioPlayerProps {
  sessionKey: string
  loadAudio?: () => Promise<Blob>
  initialBlobUrl?: string | null
  knownDurationSeconds?: number | null
  compact?: boolean
  label?: string
}

export function AudioPlayer({
  sessionKey,
  loadAudio,
  initialBlobUrl = null,
  knownDurationSeconds = null,
  compact = false,
  label = 'Grabación de audio',
}: AudioPlayerProps) {
  const [model, setModel] = useState<AudioPlayerModel>(() =>
    createInitialAudioPlayerModel(
      sessionKey,
      knownDurationSeconds,
      Boolean(initialBlobUrl),
    ),
  )
  const [downloadedUrl, setDownloadedUrl] = useState<{ key: string; url: string } | null>(null)

  const audioRef = useRef<HTMLAudioElement | null>(null)
  const ownedObjectUrlRef = useRef<string | null>(null)
  const inFlightRef = useRef(false)
  const mountedRef = useRef(true)
  const activeKeyRef = useRef(sessionKey)
  const autoPlayAfterLoadRef = useRef(false)

  // Synchronize state during render if sessionKey changes without component remount
  if (model.sessionKey !== sessionKey) {
    setModel(
      transitionAudioPlayer(
        model,
        {
          type: 'RESET',
          sessionKey,
          fallbackDuration: knownDurationSeconds,
        },
        knownDurationSeconds,
      ),
    )
  }

  const audioSrc =
    (downloadedUrl && downloadedUrl.key === sessionKey ? downloadedUrl.url : null) ?? initialBlobUrl

  const revokeOwnedUrl = useCallback(() => {
    if (ownedObjectUrlRef.current) {
      URL.revokeObjectURL(ownedObjectUrlRef.current)
      ownedObjectUrlRef.current = null
    }
  }, [])

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      revokeOwnedUrl()
    }
  }, [revokeOwnedUrl])

  useEffect(() => {
    if (activeKeyRef.current !== sessionKey) {
      activeKeyRef.current = sessionKey
      inFlightRef.current = false
      autoPlayAfterLoadRef.current = false
      if (audioRef.current) {
        audioRef.current.pause()
        audioRef.current.currentTime = 0
      }
      revokeOwnedUrl()
    }
  }, [sessionKey, revokeOwnedUrl])

  const triggerDownload = useCallback(
    async (playWhenReady: boolean) => {
      if (!loadAudio || inFlightRef.current) return
      const requestKey = activeKeyRef.current
      inFlightRef.current = true
      autoPlayAfterLoadRef.current = playWhenReady
      setModel((prev) => transitionAudioPlayer(prev, { type: 'LOAD_START' }, knownDurationSeconds))

      try {
        const blob = await loadAudio()
        if (!mountedRef.current || activeKeyRef.current !== requestKey) {
          return
        }
        if (!blob || blob.size === 0) {
          throw new Error('El archivo de audio recibido está vacío.')
        }
        revokeOwnedUrl()
        const nextUrl = URL.createObjectURL(blob)
        ownedObjectUrlRef.current = nextUrl
        setDownloadedUrl({ key: requestKey, url: nextUrl })
        setModel((prev) =>
          transitionAudioPlayer(
            prev,
            { type: 'LOAD_SUCCESS', duration: knownDurationSeconds },
            knownDurationSeconds,
          ),
        )
      } catch (err) {
        if (!mountedRef.current || activeKeyRef.current !== requestKey) {
          return
        }
        autoPlayAfterLoadRef.current = false
        const message =
          err instanceof ApiError
            ? err.message
            : err instanceof Error
              ? err.message
              : 'No se pudo descargar la grabación de audio.'
        setModel((prev) =>
          transitionAudioPlayer(prev, { type: 'LOAD_ERROR', message }, knownDurationSeconds),
        )
      } finally {
        if (activeKeyRef.current === requestKey) {
          inFlightRef.current = false
        }
      }
    },
    [loadAudio, knownDurationSeconds, revokeOwnedUrl],
  )

  async function handleTogglePlay() {
    if (model.status === 'loading') return
    if (!audioSrc) {
      await triggerDownload(true)
      return
    }
    const element = audioRef.current
    if (!element) return

    if (model.status === 'playing') {
      element.pause()
      return
    }

    try {
      await element.play()
    } catch {
      setModel((prev) =>
        transitionAudioPlayer(
          prev,
          {
            type: 'LOAD_ERROR',
            message: 'El navegador no pudo reproducir este formato de audio.',
          },
          knownDurationSeconds,
        ),
      )
    }
  }

  function handleSeek(nextSeconds: number) {
    const element = audioRef.current
    if (!element || !Number.isFinite(nextSeconds)) return
    element.currentTime = nextSeconds
    setModel((prev) =>
      transitionAudioPlayer(
        prev,
        {
          type: 'TIME_UPDATE',
          currentTime: nextSeconds,
          mediaDuration: element.duration,
        },
        knownDurationSeconds,
      ),
    )
  }

  function handleToggleMute() {
    const element = audioRef.current
    if (!element) return
    const nextMuted = !element.muted
    element.muted = nextMuted
    setModel((prev) =>
      transitionAudioPlayer(prev, {
        type: 'SET_VOLUME',
        volume: element.volume,
        muted: nextMuted,
      }),
    )
  }

  function handleVolumeChange(nextVolume: number) {
    const element = audioRef.current
    if (!element) return
    const clamped = Math.min(1, Math.max(0, nextVolume))
    element.volume = clamped
    element.muted = clamped === 0
    setModel((prev) =>
      transitionAudioPlayer(prev, {
        type: 'SET_VOLUME',
        volume: clamped,
        muted: element.muted,
      }),
    )
  }

  const effectiveDuration = resolveEffectiveDuration(model.duration, knownDurationSeconds)
  const progressPercent = calculateProgressPercent(model.currentTime, effectiveDuration)

  if (model.status === 'idle' && !audioSrc) {
    return (
      <div className={`audio-player audio-player--idle ${compact ? 'audio-player--compact' : ''}`}>
        <button
          type="button"
          className="button button--ghost audio-player__trigger"
          onClick={() => void triggerDownload(true)}
          aria-label={`Escuchar ${label}`}
        >
          <Icon name="play_circle" />
          <span>Escuchar ahora</span>
        </button>
      </div>
    )
  }

  if (model.status === 'loading') {
    return (
      <div
        className={`audio-player audio-player--loading ${compact ? 'audio-player--compact' : ''}`}
        role="status"
        aria-live="polite"
      >
        <Icon name="sync" className="audio-player__spinner" />
        <span>Descargando audio…</span>
      </div>
    )
  }

  if (model.status === 'error') {
    return (
      <div
        className={`audio-player audio-player--error ${compact ? 'audio-player--compact' : ''}`}
        role="alert"
      >
        <div className="audio-player__error-text">
          <Icon name="error" />
          <span>{model.errorMessage || 'Error al cargar el audio.'}</span>
        </div>
        {loadAudio && (
          <button
            type="button"
            className="button button--ghost audio-player__retry"
            onClick={() => void triggerDownload(true)}
          >
            <Icon name="refresh" /> Reintentar
          </button>
        )}
      </div>
    )
  }

  return (
    <div
      className={`audio-player audio-player--active ${compact ? 'audio-player--compact' : ''}`}
      aria-label={label}
    >
      <audio
        ref={audioRef}
        src={audioSrc || undefined}
        preload="metadata"
        onLoadedMetadata={(event) => {
          const element = event.currentTarget
          setModel((prev) =>
            transitionAudioPlayer(
              prev,
              { type: 'LOAD_SUCCESS', duration: element.duration },
              knownDurationSeconds,
            ),
          )
          if (autoPlayAfterLoadRef.current) {
            autoPlayAfterLoadRef.current = false
            void element.play().catch(() => {
              /* Browser blocked autoplay; user stays in ready state with visible play button */
            })
          }
        }}
        onPlay={() =>
          setModel((prev) => transitionAudioPlayer(prev, { type: 'PLAY' }, knownDurationSeconds))
        }
        onPause={() => {
          if (audioRef.current && !audioRef.current.ended) {
            setModel((prev) => transitionAudioPlayer(prev, { type: 'PAUSE' }, knownDurationSeconds))
          }
        }}
        onEnded={() =>
          setModel((prev) => transitionAudioPlayer(prev, { type: 'ENDED' }, knownDurationSeconds))
        }
        onTimeUpdate={(event) => {
          const element = event.currentTarget
          setModel((prev) =>
            transitionAudioPlayer(
              prev,
              {
                type: 'TIME_UPDATE',
                currentTime: element.currentTime,
                mediaDuration: element.duration,
              },
              knownDurationSeconds,
            ),
          )
        }}
        onError={() => {
          autoPlayAfterLoadRef.current = false
          setModel((prev) =>
            transitionAudioPlayer(
              prev,
              {
                type: 'LOAD_ERROR',
                message: 'No se pudo decodificar o reproducir el archivo de audio.',
              },
              knownDurationSeconds,
            ),
          )
        }}
      />

      <button
        type="button"
        className="audio-player__play-btn"
        onClick={() => void handleTogglePlay()}
        aria-label={model.status === 'playing' ? 'Pausar audio' : 'Reproducir audio'}
      >
        <Icon name={model.status === 'playing' ? 'pause' : 'play_arrow'} filled />
      </button>

      <div className="audio-player__timeline">
        <input
          type="range"
          min={0}
          max={effectiveDuration > 0 ? effectiveDuration : 1}
          step={0.1}
          value={Math.min(model.currentTime, effectiveDuration || 1)}
          onChange={(event) => handleSeek(Number(event.target.value))}
          disabled={effectiveDuration <= 0}
          aria-label="Barra de progreso de audio"
          style={{
            background: `linear-gradient(to right, var(--secondary-bright) ${progressPercent}%, #cbd8ea ${progressPercent}%)`,
          }}
        />
        <div className="audio-player__times">
          <span>{formatDuration(model.currentTime)}</span>
          <span>{formatDuration(effectiveDuration)}</span>
        </div>
      </div>

      {!compact && (
        <div className="audio-player__volume">
          <button
            type="button"
            className="audio-player__mute-btn"
            onClick={handleToggleMute}
            aria-label={model.muted ? 'Activar sonido' : 'Silenciar'}
          >
            <Icon name={model.muted || model.volume === 0 ? 'volume_off' : 'volume_up'} />
          </button>
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={model.muted ? 0 : model.volume}
            onChange={(event) => handleVolumeChange(Number(event.target.value))}
            aria-label="Volumen"
          />
        </div>
      )}
    </div>
  )
}
