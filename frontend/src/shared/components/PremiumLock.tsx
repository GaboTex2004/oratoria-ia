import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Icon } from './Icon'
import './personalization.css'

export function PremiumLock({ feature, children }: { feature: string; children?: ReactNode }) {
  return <section className="premium-lock" role="note">
    <Icon name="workspace_premium" />
    <div><strong>{feature} está disponible en Premium</strong><p>{children ?? 'Mejora tu plan para usar esta función.'}</p></div>
    <Link className="button button--primary" to="/plans">Ver planes</Link>
  </section>
}

export function PremiumBadge() {
  return <span className="premium-badge"><Icon name="lock" />Premium</span>
}
