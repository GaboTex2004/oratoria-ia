import { useEffect, useRef, useState } from 'react'
import { WorkspaceLayout } from '../../shared/components/WorkspaceLayout'
import { useResource } from '../../shared/hooks/useResource'
import { apiRequest } from '../../shared/services/api'
import type { TrainingSession } from '../training/types/training'

interface BodyResult {
  framesAnalyzed: number; framesWithPerson: number; presenceRatio: number
  shoulderTiltDegrees: number | null; headOffsetDegrees: number | null
  bodyMovementNormalized: number | null; wristMovementNormalized: number | null; limitations: string[]
}

export function BodyPage() {
  const sessions = useResource<TrainingSession[]>('/sessions')
  const [sessionId, setSessionId] = useState('')
  const [consent, setConsent] = useState(false)
  const [cameraOn, setCameraOn] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState<BodyResult | null>(null)
  const video = useRef<HTMLVideoElement>(null)
  const stream = useRef<MediaStream | null>(null)
  const alive = useRef(true)
  useEffect(() => { alive.current = true; return () => { alive.current = false; stream.current?.getTracks().forEach(track => track.stop()) } }, [])
  function stop() { stream.current?.getTracks().forEach(track => track.stop()); stream.current = null; setCameraOn(false) }
  async function start() {
    setError(''); setBusy(true)
    try {
      const media = await navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480 }, audio: false })
      if (!alive.current) { media.getTracks().forEach(track => track.stop()); return }
      stream.current = media
      if (video.current) { video.current.srcObject = media; await video.current.play() }
      setCameraOn(true)
    } catch { stop(); setError('No se pudo activar la cámara. Revisa los permisos del navegador.') }
    finally { if (alive.current) setBusy(false) }
  }
  async function capture() {
    setBusy(true); setError(''); setResult(null)
    try {
      const frames: string[] = []
      const canvas = document.createElement('canvas'); canvas.width = 480
      canvas.height = Math.round(480 * (video.current?.videoHeight || 360) / (video.current?.videoWidth || 480))
      const context = canvas.getContext('2d')
      for (let i = 0; i < 8; i++) {
        if (!video.current || !context || !alive.current || !stream.current) return
        context.drawImage(video.current, 0, 0, 480, 360)
        frames.push(canvas.toDataURL('image/jpeg', 0.75).split(',')[1])
        await new Promise(resolve => setTimeout(resolve, 500))
      }
      stop()
      const data = await apiRequest<BodyResult>(`/sessions/${sessionId}/body-analysis`, { method: 'POST', body: JSON.stringify({ consent, frames }) })
      if (alive.current) setResult(data)
    } catch (reason) { if (alive.current) setError(reason instanceof Error ? reason.message : 'No se pudo analizar.') }
    finally { if (alive.current) { stop(); setBusy(false) } }
  }
  async function load() {
    setBusy(true); setError(''); setResult(null)
    try { setResult(await apiRequest<BodyResult>(`/sessions/${sessionId}/body-analysis`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'No se pudo cargar.') }
    finally { setBusy(false) }
  }
  return <WorkspaceLayout>
    <header><span className="eyebrow">Práctica frente a cámara</span><h1>Comunicación no verbal</h1><p>Asocia ocho fotogramas a una práctica. Solo se conservan los indicadores resultantes.</p></header>
    {(error || sessions.error) && <p role="alert" className="training-alert training-alert--error">{error || sessions.error}</p>}
    {sessions.loading ? <p>Cargando sesiones…</p> : !sessions.data?.length ? <p>Primero inicia una práctica en Entrenamientos.</p> : <section className="panel-card workspace-form">
      <label>Práctica<select aria-label="Práctica" disabled={busy || cameraOn} value={sessionId} onChange={event => { setSessionId(event.target.value); setResult(null) }}>
        <option value="">Selecciona una sesión</option>{sessions.data.map(session => <option key={session.id} value={session.id}>{session.exerciseTitle || session.trainingType} · {new Date(session.createdAt).toLocaleString('es-BO')}</option>)}
      </select></label>
      <label className="terms-field"><input type="checkbox" checked={consent} disabled={busy || cameraOn} onChange={event => setConsent(event.target.checked)} />Autorizo la captura y el procesamiento local de estos fotogramas. Puedo apagar la cámara antes de enviarlos.</label>
      <video className="camera-preview" ref={video} muted playsInline aria-label="Vista previa de cámara" />
      <div className="workspace-actions">
        {!cameraOn ? <button className="button button--primary" disabled={!consent || !sessionId || busy} onClick={start}>Activar cámara</button> : <><button className="button button--primary" disabled={busy} onClick={capture}>Capturar y analizar</button><button className="button button--ghost" onClick={stop}>Apagar cámara</button></>}
        <button className="button button--ghost" disabled={!sessionId || busy || cameraOn} onClick={load}>Ver último análisis</button>
      </div>{busy && <p role="status">Procesando localmente…</p>}
    </section>}
    {result && <section className="panel-card"><h2>Indicadores observados</h2>
      <p>Persona visible: {result.framesWithPerson}/{result.framesAnalyzed} fotogramas.</p>
      {result.framesWithPerson === 0 ? <p>No se encontró evidencia suficiente. Prueba con mejor iluminación y cabeza y hombros visibles.</p> : <dl className="stat-list">
        <div><dt>Inclinación de hombros en imagen</dt><dd>{result.shoulderTiltDegrees?.toFixed(1) ?? '—'}°</dd></div>
        <div><dt>Desplazamiento de cabeza respecto a hombros</dt><dd>{result.headOffsetDegrees?.toFixed(1) ?? '—'}°</dd></div>
        <div><dt>Movimiento corporal normalizado</dt><dd>{result.bodyMovementNormalized ?? '—'}</dd></div>
        <div><dt>Movimiento de muñecas normalizado</dt><dd>{result.wristMovementNormalized ?? '—'}</dd></div>
      </dl>}<ul>{result.limitations.map(item => <li key={item}>{item}</li>)}</ul>
    </section>}
  </WorkspaceLayout>
}
