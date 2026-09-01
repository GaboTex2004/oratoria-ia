import { Link } from 'react-router-dom'
import { Brand } from './Brand'

interface PlaceholderPageProps {
  title: string
}

export function PlaceholderPage({ title }: PlaceholderPageProps) {
  return (
    <main className="placeholder-page">
      <Brand />
      <section className="placeholder-card">
        <span className="eyebrow">Próximamente</span>
        <h1>{title}</h1>
        <p>Este módulo ya tiene una ruta reservada y será desarrollado en una siguiente etapa.</p>
        <Link className="button button--primary" to="/home">Volver al inicio</Link>
      </section>
    </main>
  )
}
