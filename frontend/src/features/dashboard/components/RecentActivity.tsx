import { useNavigate } from 'react-router-dom'
import { Icon } from '../../../shared/components/Icon'
import type { ProgressHistoryItem } from '../../progress/types/progress'
import { SESSION_STATUS_META, TRAINING_TYPE_META } from '../../training/types/training'

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('es-BO', { dateStyle: 'medium' }).format(new Date(iso))
}

export function RecentActivity({ history }: { history: ProgressHistoryItem[] }) {
  const navigate = useNavigate()
  const sessions = [...history].reverse().slice(0, 5)
  return (
    <section className="panel-card recent-card">
      <div className="panel-card__heading"><h2>Actividad reciente</h2><button type="button" onClick={() => navigate('/sessions')}>Ver todo</button></div>
      <div className="recent-list">
        {sessions.length === 0 && <p className="recent-empty">Aún no tienes sesiones. Tu actividad aparecerá aquí.</p>}
        {sessions.map((session) => (
          <button className="recent-list__item" type="button" key={session.sessionId} onClick={() => navigate(`/results?sessionId=${encodeURIComponent(session.sessionId)}`)}>
            <span className="recent-list__icon"><Icon name={TRAINING_TYPE_META[session.trainingType].icon} /></span>
            <span><strong>{session.exerciseTitle || TRAINING_TYPE_META[session.trainingType].label}</strong><small>{formatDate(session.createdAt)} · {SESSION_STATUS_META[session.sessionStatus].label}</small></span>
            <b>{session.overallScore == null ? '—' : session.overallScore.toFixed(1)}</b>
          </button>
        ))}
      </div>
    </section>
  )
}
