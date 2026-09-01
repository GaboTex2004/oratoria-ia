import type { ReactNode } from 'react'
import { Brand } from '../../../shared/components/Brand'
import { Icon } from '../../../shared/components/Icon'
import './auth.css'

interface AuthLayoutProps {
  title: string
  subtitle: string
  children: ReactNode
  variant?: 'login' | 'register'
}

const benefits = [
  ['mic', 'Análisis de voz', 'Tono, ritmo y claridad en cada práctica.'],
  ['accessibility_new', 'Lenguaje corporal', 'Postura y presencia frente a cámara.'],
  ['psychology', 'Simulaciones IA', 'Escenarios para entrevistas, debates y pitch.'],
  ['trending_up', 'Progreso continuo', 'Métricas claras para seguir mejorando.'],
]

export function AuthLayout({ title, subtitle, children, variant = 'login' }: AuthLayoutProps) {
  return (
    <main className={`auth-layout auth-layout--${variant}`}>
      <section className="auth-panel">
        <div className="auth-panel__content">
          <Brand compact />
          <header className="auth-header">
            <h1>{title}</h1>
            <p>{subtitle}</p>
          </header>
          {children}
        </div>
      </section>

      <section className="auth-showcase" aria-label="Beneficios de Oratoria IA">
        <div className="auth-showcase__glow auth-showcase__glow--top" />
        <div className="auth-showcase__glow auth-showcase__glow--bottom" />
        <div className="auth-showcase__content">
          <span className="eyebrow">Entrenamiento inteligente</span>
          <h2>{variant === 'login' ? 'Practica. Analiza. Mejora.' : 'Desata tu potencial'}</h2>
          <p>Tu coach personal de comunicación, diseñado para ayudarte a hablar con claridad y confianza.</p>
          <div className="benefit-grid">
            {benefits.map(([icon, name, description]) => (
              <article className="benefit-card" key={name}>
                <span className="benefit-card__icon"><Icon name={icon} filled /></span>
                <div><h3>{name}</h3><p>{description}</p></div>
              </article>
            ))}
          </div>
        </div>
      </section>
    </main>
  )
}
