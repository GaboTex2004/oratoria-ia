import type { DashboardStats, RecentSession, SubscriptionSummary, TrainingOption } from '../types/dashboard'

export const trainingOptions: TrainingOption[] = [
  { id: 'presentation', title: 'Presentación', description: 'Practica tu introducción personal o de proyecto con una estructura clara.', icon: 'co_present', badge: 'Básico', badgeTone: 'blue' },
  { id: 'interview', title: 'Entrevista', description: 'Simula entrevistas de trabajo y mejora tus respuestas bajo presión.', icon: 'work' },
  { id: 'debate', title: 'Debate', description: 'Estructura argumentos y maneja refutaciones con confianza.', icon: 'forum' },
  { id: 'pitch', title: 'Pitch', description: 'Vende tu idea en menos de tres minutos de forma persuasiva.', icon: 'campaign', badge: 'Popular', badgeTone: 'coral' },
]

export const dashboardStats: DashboardStats = { score: 78, sessions: 12, wordsPerMinute: 140, fillerWords: 4 }

export const recentSessions: RecentSession[] = [
  { id: '1', title: 'Pitch Inversores', date: 'Ayer, 14:30', score: 78, icon: 'campaign' },
  { id: '2', title: 'Presentación Q3', date: '12 Oct, 09:15', score: 82, icon: 'co_present' },
  { id: '3', title: 'Entrevista Técnica', date: '10 Oct, 11:00', score: 74, icon: 'work' },
]

export const subscriptionSummary: SubscriptionSummary = { plan: 'FREE', sessionsUsed: 3, sessionsLimit: 5 }
