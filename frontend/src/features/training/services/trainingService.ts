import { apiBlobRequest, apiRequest } from '../../../shared/services/api'
import {
  extensionForMimeType,
  type CreateSessionPayload,
  type Exercise,
  type SpeechAnalysis,
  type SpeechEvaluation,
  type TrainingSession,
  type TrainingType,
} from '../types/training'

export const trainingService = {
  listExercises(trainingType?: TrainingType): Promise<Exercise[]> {
    const query = trainingType ? `?trainingType=${encodeURIComponent(trainingType)}` : ''
    return apiRequest<Exercise[]>(`/exercises${query}`)
  },

  getExercise(id: string): Promise<Exercise> {
    return apiRequest<Exercise>(`/exercises/${encodeURIComponent(id)}`)
  },

  createSession(payload: CreateSessionPayload): Promise<TrainingSession> {
    return apiRequest<TrainingSession>('/sessions', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  listSessions(): Promise<TrainingSession[]> {
    return apiRequest<TrainingSession[]>('/sessions')
  },

  getSession(id: string): Promise<TrainingSession> {
    return apiRequest<TrainingSession>(`/sessions/${encodeURIComponent(id)}`)
  },

  completeSession(
    sessionId: string,
    audioBlob: Blob,
    durationSeconds: number,
  ): Promise<TrainingSession> {
    const safeDuration = Math.max(1, Math.round(durationSeconds))
    const mimeType = audioBlob.type || 'audio/webm'
    const ext = extensionForMimeType(mimeType)
    const formData = new FormData()
    formData.append('audio', audioBlob, `session-${sessionId}.${ext}`)
    formData.append('durationSeconds', String(safeDuration))

    return apiRequest<TrainingSession>(`/sessions/${encodeURIComponent(sessionId)}/complete`, {
      method: 'POST',
      body: formData,
    })
  },

  fetchSessionAudioBlob(sessionId: string): Promise<Blob> {
    return apiBlobRequest(`/sessions/${encodeURIComponent(sessionId)}/audio`)
  },

  getSessionAnalysis(sessionId: string): Promise<SpeechAnalysis> {
    return apiRequest<SpeechAnalysis>(`/sessions/${encodeURIComponent(sessionId)}/analysis`)
  },

  retrySessionAnalysis(sessionId: string): Promise<SpeechAnalysis> {
    return apiRequest<SpeechAnalysis>(`/sessions/${encodeURIComponent(sessionId)}/analysis/retry`, {
      method: 'POST',
    })
  },

  getSessionEvaluation(sessionId: string): Promise<SpeechEvaluation> {
    return apiRequest<SpeechEvaluation>(`/sessions/${encodeURIComponent(sessionId)}/evaluation`)
  },

  retrySessionEvaluation(sessionId: string): Promise<SpeechEvaluation> {
    return apiRequest<SpeechEvaluation>(
      `/sessions/${encodeURIComponent(sessionId)}/evaluation/retry`,
      {
        method: 'POST',
      },
    )
  },
}
