import { useResource } from './useResource'

/** Default used until the server answers, matching the backend's default limit. */
export const DEFAULT_RECORDING_LIMIT_SECONDS = 300

/** Maximum recording length configured by the administrator (GET /api/system/limits). */
export function useRecordingLimit() {
  const limits = useResource<{ audioMaxDurationSeconds: number }>('/system/limits')
  return limits.data?.audioMaxDurationSeconds ?? DEFAULT_RECORDING_LIMIT_SECONDS
}
