import { Icon } from '../../../shared/components/Icon'
import { trainingOptions } from '../data/dashboardMock'

export function TrainingGrid() {
  return (
    <section className="dashboard-section training-section">
      <h2><Icon name="model_training" /> Entrenamientos</h2>
      <div className="training-grid">
        {trainingOptions.map((option) => (
          <article className="training-card" key={option.id}>
            <div className="training-card__head"><span className="training-card__icon"><Icon name={option.icon} /></span>{option.badge && <span className={`badge badge--${option.badgeTone}`}>{option.badge}</span>}</div>
            <h3>{option.title}</h3><p>{option.description}</p>
            <button type="button">Comenzar <Icon name="arrow_forward" /></button>
          </article>
        ))}
      </div>
    </section>
  )
}
