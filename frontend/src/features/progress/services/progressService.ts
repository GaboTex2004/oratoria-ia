import { apiRequest } from '../../../shared/services/api'
import type { ProgressSummary } from '../types/progress'

export const progressService = {
  getSummary(): Promise<ProgressSummary> {
    return apiRequest<ProgressSummary>('/progress/summary')
  },
}
