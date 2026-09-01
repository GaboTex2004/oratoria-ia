export interface TrainingOption {
  id: string
  title: string
  description: string
  icon: string
  badge?: string
  badgeTone?: 'blue' | 'coral'
}

export interface DashboardStats {
  score: number
  sessions: number
  wordsPerMinute: number
  fillerWords: number
}

export interface RecentSession {
  id: string
  title: string
  date: string
  score: number
  icon: string
}

export interface SubscriptionSummary {
  plan: string
  sessionsUsed: number
  sessionsLimit: number
}
