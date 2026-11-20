import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ApiError, apiRequest } from '../../shared/services/api'
import { Icon } from '../../shared/components/Icon'
import { useAuth } from '../auth/context/useAuth'
import { stepsFor } from '../admin/monitoringSteps'
import { findGuide, guideForScreen, guidesFor, type Guide, type Role } from './guides'
import './assistant.css'

type Severity = 'NORMAL' | 'ATENCION' | 'CRITICO'
interface Alert { code: string; severity: Severity; message: string }
interface Snapshot { status: Severity; alerts: Alert[] }
interface Message { id: number; from: 'bot' | 'user'; text: string; guide?: Guide; alerts?: Alert[] }

const REFRESH_MS = 15000
const STATUS_TEXT: Record<Severity, string> = { NORMAL: 'Todo en orden', ATENCION: 'Requiere atención', CRITICO: 'Problema crítico' }

/**
 * Asistente de ayuda en todas las pantallas y para todas las cuentas: muestra la guía de la pantalla actual, responde
 * preguntas con pasos y, a los administradores, les avisa en vivo del estado de la plataforma.
 */
export function Assistant() {
  const { user } = useAuth()
  // Al cambiar de cuenta (entrar, salir u otro rol) el asistente empieza de cero.
  return <AssistantPanel key={user?.id ?? 'invitado'} role={user ? user.role : 'GUEST'} firstName={user?.firstName ?? ''} />
}

function AssistantPanel({ role, firstName }: { role: Role; firstName: string }) {
  const { pathname, search } = useLocation()
  const isAdmin = role === 'ADMIN'
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([])
  const [question, setQuestion] = useState('')
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null)
  const [offline, setOffline] = useState(false)
  const nextId = useRef(0)
  const seenAlerts = useRef<Set<string> | null>(null)
  const bottom = useRef<HTMLDivElement>(null)

  const say = (message: Omit<Message, 'id'>) => setMessages(list => [...list, { ...message, id: ++nextId.current }])
  const screenGuide = guideForScreen(pathname, search, role)

  // Al abrir sin conversación, saluda con la guía de la pantalla actual.
  function toggle() {
    if (!open && messages.length === 0) say({ from: 'bot', text: `Hola${firstName ? `, ${firstName}` : ''}. Así se usa esta pantalla:`, guide: screenGuide })
    setOpen(value => !value)
  }

  useEffect(() => { bottom.current?.scrollIntoView({ block: 'end' }) }, [messages, open])

  // Solo administradores: estado de la plataforma en segundo plano; una alerta nueva abre el asistente con sus pasos.
  useEffect(() => {
    if (!isAdmin) return
    let timer = 0
    let active = true
    const tick = async () => {
      if (document.visibilityState === 'visible') {
        try {
          const next = await apiRequest<Snapshot>('/admin/monitoring')
          if (!active) return
          setSnapshot(next); setOffline(false)
          const codes = new Set(next.alerts.map(alert => alert.code))
          const fresh = seenAlerts.current ? next.alerts.filter(alert => !seenAlerts.current?.has(alert.code)) : []
          seenAlerts.current = codes
          if (fresh.length > 0) {
            setOpen(true)
            say({ from: 'bot', text: 'Apareció una alerta en la plataforma. Esto es lo que hay que hacer:', alerts: fresh })
          }
        } catch (reason) {
          if (!active || (reason instanceof ApiError && (reason.status === 401 || reason.status === 403))) return
          setOffline(true)
        }
      }
      if (active) timer = window.setTimeout(tick, REFRESH_MS)
    }
    timer = window.setTimeout(tick, 0)
    return () => { active = false; window.clearTimeout(timer) }
  }, [isAdmin])

  function ask(text: string, chosen?: Guide) {
    const clean = text.trim()
    if (!clean) return
    say({ from: 'user', text: clean })
    const guide = chosen ?? findGuide(clean, role)
    say(guide
      ? { from: 'bot', text: 'Sigue estos pasos:', guide }
      : { from: 'bot', text: 'No encontré una guía para eso. Prueba con otras palabras o elige una de las preguntas de abajo.' })
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    ask(question)
    setQuestion('')
  }

  function showStatus() {
    if (offline) say({ from: 'bot', text: 'No hay conexión con el servidor. Revisa tu internet y que el backend esté encendido (docker compose ps).' })
    else if (!snapshot) say({ from: 'bot', text: 'Todavía estoy revisando el estado de la plataforma.' })
    else if (snapshot.alerts.length === 0) say({ from: 'bot', text: 'Todo en orden: todos los servicios funcionan.' })
    else say({ from: 'bot', text: `${STATUS_TEXT[snapshot.status]}. Esto es lo que hay que hacer:`, alerts: snapshot.alerts })
  }

  const suggestions = guidesFor(role).filter(guide => guide.id !== screenGuide.id).slice(0, isAdmin ? 0 : 4)
  const adminSuggestions = isAdmin ? guidesFor(role).filter(guide => guide.audience === 'admin' && guide.id !== screenGuide.id).slice(0, 4) : []
  const status: Severity = offline ? 'CRITICO' : snapshot?.status ?? 'NORMAL'
  const alertCount = snapshot?.alerts.length ?? 0

  return <div className="assistant no-print">
    {open && <section className="assistant__panel" role="dialog" aria-label="Asistente de OratoriaAI">
      <header className="assistant__header">
        <strong><Icon name="support_agent" /> Asistente</strong>
        <button type="button" aria-label="Cerrar asistente" onClick={() => setOpen(false)}><Icon name="close" /></button>
      </header>

      {isAdmin && <button type="button" className={`assistant__status assistant__status--${status.toLowerCase()}`} onClick={showStatus}>
        <Icon name="monitor_heart" />
        <span>{offline ? 'Sin conexión con el servidor' : snapshot ? `Plataforma: ${STATUS_TEXT[snapshot.status]}${alertCount ? ` (${alertCount})` : ''}` : 'Revisando la plataforma…'}</span>
      </button>}

      <div className="assistant__messages" aria-live="polite">
        {messages.map(message => <div key={message.id} className={`assistant__message assistant__message--${message.from}`}>
          <p>{message.text}</p>
          {message.guide && <div className="assistant__guide">
            <strong>{message.guide.question}</strong>
            <ol>{message.guide.steps.map(step => <li key={step}>{step}</li>)}</ol>
            {message.guide.link && !(pathname + search).startsWith(message.guide.link.to) &&
              <Link to={message.guide.link.to}>{message.guide.link.label} →</Link>}
          </div>}
          {message.alerts?.map(alert => <div key={alert.code} className={`assistant__alert assistant__alert--${alert.severity.toLowerCase()}`}>
            <strong>{alert.message}</strong>
            <ol>{stepsFor(alert.code).map(step => <li key={step}>{step}</li>)}</ol>
          </div>)}
          {message.alerts && <Link to="/admin?tab=monitoreo">Ver panel de Monitoreo →</Link>}
        </div>)}
        <div ref={bottom} />
      </div>

      <div className="assistant__chips">
        <button type="button" onClick={() => say({ from: 'bot', text: 'Así se usa esta pantalla:', guide: screenGuide })}>Esta pantalla</button>
        {[...adminSuggestions, ...suggestions].map(guide => <button key={guide.id} type="button" onClick={() => ask(guide.question, guide)}>{guide.question}</button>)}
      </div>

      <form className="assistant__form" onSubmit={submit}>
        <input aria-label="Escribe tu pregunta" maxLength={200} value={question} onChange={event => setQuestion(event.target.value)} placeholder="Escribe tu pregunta…" />
        <button type="submit" aria-label="Enviar pregunta" disabled={!question.trim()}><Icon name="send" /></button>
      </form>
    </section>}

    <button type="button" className={`assistant__button${isAdmin ? ` assistant__button--${status.toLowerCase()}` : ''}`}
      onClick={toggle} aria-expanded={open}>
      <Icon name={open ? 'close' : 'support_agent'} />
      <span>{isAdmin && (offline || alertCount > 0) ? (offline ? 'Sin conexión' : `${alertCount} ${alertCount === 1 ? 'alerta' : 'alertas'}`) : 'Asistente'}</span>
    </button>
  </div>
}
