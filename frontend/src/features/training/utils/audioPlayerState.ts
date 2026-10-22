export type AudioPlayerStatus =
  | 'idle'
  | 'loading'
  | 'ready'
  | 'playing'
  | 'paused'
  | 'error'

export interface AudioPlayerModel {
  sessionKey: string
  status: AudioPlayerStatus
  currentTime: number
  duration: number
  volume: number
  muted: boolean
  errorMessage: string | null
}

export type AudioPlayerAction =
  | { type: 'LOAD_START' }
  | { type: 'LOAD_SUCCESS'; duration?: number | null }
  | { type: 'LOAD_ERROR'; message: string }
  | { type: 'PLAY' }
  | { type: 'PAUSE' }
  | { type: 'ENDED' }
  | { type: 'TIME_UPDATE'; currentTime: number; mediaDuration?: number }
  | { type: 'SET_VOLUME'; volume: number; muted: boolean }
  | { type: 'RESET'; sessionKey: string; fallbackDuration?: number | null }

export function resolveEffectiveDuration(
  mediaDuration: number | null | undefined,
  fallbackDuration: number | null | undefined,
): number {
  if (typeof mediaDuration === 'number' && Number.isFinite(mediaDuration) && mediaDuration > 0) {
    return mediaDuration
  }
  if (typeof fallbackDuration === 'number' && Number.isFinite(fallbackDuration) && fallbackDuration > 0) {
    return fallbackDuration
  }
  return 0
}

export function createInitialAudioPlayerModel(
  sessionKey: string,
  fallbackDuration?: number | null,
  initialReady = false,
): AudioPlayerModel {
  return {
    sessionKey,
    status: initialReady ? 'ready' : 'idle',
    currentTime: 0,
    duration: resolveEffectiveDuration(null, fallbackDuration),
    volume: 1,
    muted: false,
    errorMessage: null,
  }
}

export function transitionAudioPlayer(
  state: AudioPlayerModel,
  action: AudioPlayerAction,
  fallbackDuration?: number | null,
): AudioPlayerModel {
  switch (action.type) {
    case 'RESET':
      return createInitialAudioPlayerModel(action.sessionKey, action.fallbackDuration, false)

    case 'LOAD_START':
      if (state.status === 'loading') return state
      return {
        ...state,
        status: 'loading',
        errorMessage: null,
      }

    case 'LOAD_SUCCESS':
      return {
        ...state,
        status: 'ready',
        currentTime: 0,
        duration: resolveEffectiveDuration(action.duration, fallbackDuration ?? state.duration),
        errorMessage: null,
      }

    case 'LOAD_ERROR':
      return {
        ...state,
        status: 'error',
        errorMessage: action.message || 'No se pudo cargar el archivo de audio.',
      }

    case 'PLAY':
      return {
        ...state,
        status: 'playing',
        errorMessage: null,
      }

    case 'PAUSE':
      return {
        ...state,
        status: 'paused',
      }

    case 'ENDED':
      return {
        ...state,
        status: 'ready',
        currentTime: 0,
      }

    case 'TIME_UPDATE': {
      const nextDuration = resolveEffectiveDuration(action.mediaDuration, fallbackDuration ?? state.duration)
      const clampedCurrent = nextDuration > 0
        ? Math.min(Math.max(0, action.currentTime), nextDuration)
        : Math.max(0, action.currentTime)
      return {
        ...state,
        currentTime: clampedCurrent,
        duration: nextDuration,
      }
    }

    case 'SET_VOLUME':
      return {
        ...state,
        volume: Math.min(1, Math.max(0, action.volume)),
        muted: action.muted,
      }

    default:
      return state
  }
}

export function calculateProgressPercent(currentTime: number, duration: number): number {
  if (!Number.isFinite(duration) || duration <= 0 || !Number.isFinite(currentTime) || currentTime <= 0) {
    return 0
  }
  return Math.min(100, Math.max(0, (currentTime / duration) * 100))
}
