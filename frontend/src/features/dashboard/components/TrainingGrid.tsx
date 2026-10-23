import { useNavigate } from 'react-router-dom'
import { Icon } from '../../../shared/components/Icon'
import { TRAINING_TYPE_META, type Exercise, type TrainingType } from '../../training/types/training'

const typeOrder = Object.keys(TRAINING_TYPE_META) as TrainingType[]

export function TrainingGrid({ exercises }: { exercises: Exercise[] }) {
  const navigate = useNavigate()
  const byType = new Map<TrainingType, Exercise>()
  exercises.forEach((exercise) => {
    if (!byType.has(exercise.trainingType)) byType.set(exercise.trainingType, exercise)
  })
  return (
    <section className="dashboard-section training-section">
      <h2><Icon name="model_training" /> Entrenamientos</h2>
      <div className="training-grid">
        {typeOrder.flatMap((type) => {
          const exercise = byType.get(type)
          if (!exercise) return []
          const meta = TRAINING_TYPE_META[type]
          return [<article className="training-card" key={type}>
            <div className="training-card__head"><span className="training-card__icon"><Icon name={meta.icon} /></span><span className={`badge badge--${meta.badgeTone}`}>{exercise.recommendedDurationSeconds}s</span></div>
            <h3>{meta.label}</h3><p>{exercise.description}</p>
            <button type="button" onClick={() => navigate(`/training?type=${type}`)}>Comenzar <Icon name="arrow_forward" /></button>
          </article>]
        })}
      </div>
    </section>
  )
}
