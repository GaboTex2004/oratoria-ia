import { useEffect, useMemo, useRef, useState } from 'react'
import { apiRequest } from '../../../shared/services/api'
import { useRecordingLimit } from '../../../shared/hooks/useRecordingLimit'
import { formatDuration, type VerbalMetrics } from '../types/training'
import { pitchEstimate, scaledWords, targetDuration, voiceObservations, waveformPath, type TimedWord, type VoicePoint } from '../utils/voiceCoach'
import './voiceCoach.css'
import { KaraokePrompter } from './KaraokePrompter'

interface Transcript { transcriptText: string; words: TimedWord[]; metrics: VerbalMetrics }
interface Attempt { blob: Blob; url: string; duration: number; points: VoicePoint[]; speech: Transcript | null }

export function VoiceCoachModal({ onClose }: { onClose: () => void }) {
  const limit = useRecordingLimit()
  const dialog = useRef<HTMLDialogElement>(null)
  const media = useRef<MediaStream | null>(null)
  const recorder = useRef<MediaRecorder | null>(null)
  const context = useRef<AudioContext | null>(null)
  const timer = useRef<number | null>(null)
  const request = useRef<AbortController | null>(null)
  const alive = useRef(true)
  const urls = useRef(new Set<string>())
  const audio = useRef<HTMLAudioElement>(null)
  const [step, setStep] = useState<'record' | 'review' | 'prepare' | 'practice'>('record')
  const [consent, setConsent] = useState(false)
  const [recording, setRecording] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [reference, setReference] = useState<Attempt | null>(null)
  const [current, setCurrent] = useState<Attempt | null>(null)
  const [live, setLive] = useState<VoicePoint[]>([])
  const [elapsed, setElapsed] = useState(0)
  const [guideElapsed, setGuideElapsed] = useState(0)
  const [pace, setPace] = useState(140)
  const [target, setTarget] = useState(60)
  const [fontSize, setFontSize] = useState(26)
  const [emphasis, setEmphasis] = useState('')
  const [playTime, setPlayTime] = useState(0)
  const [previewPlaying, setPreviewPlaying] = useState(false)
  const [paused, setPaused] = useState(false)
  const pausedAt = useRef<number | null>(null)
  const pauseTotal = useRef(0)
  const planned = reference ? Math.max(Math.ceil(reference.duration / 4), Math.min(limit, reference.duration * 4, target)) : target
  const guide = useMemo(() => {
    if (!reference) return []
    const words = reference.speech?.words.length ? reference.speech.words : (reference.speech?.transcriptText.split(/\s+/).filter(Boolean) ?? []).map((word, index, list) => ({ word, startSeconds: index / list.length * reference.duration, endSeconds: (index + 1) / list.length * reference.duration }))
    return scaledWords(words, reference.duration, planned)
  }, [reference, planned])
  const clock = recording ? guideElapsed : playTime
  const axis = Math.max(1, reference ? planned : 0, current?.duration ?? 0, elapsed)
  const observations = current ? voiceObservations(current.points) : []

  function release() {
    if (timer.current !== null) window.clearInterval(timer.current)
    timer.current = null
    media.current?.getTracks().forEach(track => track.stop())
    media.current = null
    void context.current?.close().catch(() => {})
    context.current = null
  }

  useEffect(() => {
    alive.current = true
    dialog.current?.showModal()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const links = urls.current
    return () => {
      alive.current = false
      request.current?.abort()
      if (recorder.current) {
        recorder.current.onstop = null; recorder.current.onerror = null; recorder.current.ondataavailable = null
        if (recorder.current.state !== 'inactive') recorder.current.stop()
      }
      release()
      for (const url of links) URL.revokeObjectURL(url)
      links.clear()
      document.body.style.overflow = previousOverflow
    }
  }, [])

  useEffect(() => {
    const retained = new Set([reference?.url, current?.url])
    for (const url of urls.current) if (!retained.has(url)) { URL.revokeObjectURL(url); urls.current.delete(url) }
  }, [reference, current])

  useEffect(() => {
    if (audio.current && reference) {
      audio.current.playbackRate = reference.duration / planned
      audio.current.preservesPitch = true
    }
  }, [reference, planned])

  function close() {
    if ((recording || current || reference || busy) && !window.confirm('Al cerrar se descartan los audios y resultados de esta práctica. ¿Cerrar?')) return
    onClose()
  }

  async function analyze(attempt: Attempt) {
    setBusy(true); setError('')
    const controller = new AbortController()
    request.current = controller
    try {
      const form = new FormData()
      form.append('audio', attempt.blob, 'voice-practice.webm')
      form.append('durationSeconds', String(Math.max(1, Math.round(attempt.duration))))
      form.append('consent', 'true')
      const speech = await apiRequest<Transcript>('/voice-coach/analyze', { method: 'POST', body: form, signal: controller.signal })
      if (!alive.current) return
      setCurrent({ ...attempt, speech })
      if (!reference) setTarget(targetDuration(attempt.duration, speech.metrics.wordCount, pace, limit))
    } catch (reason) {
      if (alive.current && !controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'No se pudo transcribir. El audio sigue disponible para reintentar.')
    } finally { if (alive.current) setBusy(false) }
  }

  async function start() {
    if (busy || recording || !consent) return
    setBusy(true); setError(''); setLive([]); setElapsed(0); setGuideElapsed(0); setPlayTime(0); setPaused(false)
    audio.current?.pause(); setPreviewPlaying(false)
    pausedAt.current = null; pauseTotal.current = 0
    try {
      if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') throw new Error('Tu navegador no permite grabar audio.')
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      if (!alive.current) { stream.getTracks().forEach(track => track.stop()); return }
      media.current = stream
      const audioContext = new AudioContext()
      context.current = audioContext
      await audioContext.resume()
      if (!alive.current) return
      const analyser = audioContext.createAnalyser()
      analyser.fftSize = 4096
      audioContext.createMediaStreamSource(stream).connect(analyser)
      const mime = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4'].find(value => MediaRecorder.isTypeSupported(value))
      const capture = mime ? new MediaRecorder(stream, { mimeType: mime }) : new MediaRecorder(stream)
      recorder.current = capture
      const chunks: Blob[] = []
      const points: VoicePoint[] = []
      let bytes = 0
      let failed = false
      const startedAt = Date.now()
      capture.ondataavailable = event => {
        if (event.data.size) { chunks.push(event.data); bytes += event.data.size }
        if (bytes > 15 * 1024 * 1024 && capture.state === 'recording') capture.stop()
      }
      capture.onerror = () => { failed = true; release(); if (alive.current) { setRecording(false); setBusy(false); setError('Se interrumpió el micrófono. Reintenta la grabación.') } }
      capture.onstop = () => {
        release()
        recorder.current = null
        if (!alive.current || failed) return
        setRecording(false)
        const blob = new Blob(chunks, { type: capture.mimeType || mime || 'audio/webm' })
        if (!blob.size || blob.size > 15 * 1024 * 1024) { setError('Audio vacío o mayor de 15 MB. Graba nuevamente.'); return }
        const url = URL.createObjectURL(blob)
        urls.current.add(url)
        const attempt = { blob, url, duration: Math.min(limit, (Date.now() - startedAt) / 1000), points, speech: null }
        setCurrent(attempt); setStep('review'); void analyze(attempt)
      }
      capture.start(250)
      setRecording(true); setBusy(false)
      const buffer = new Float32Array(analyser.fftSize)
      timer.current = window.setInterval(() => {
        const now = Date.now()
        const time = (now - startedAt) / 1000
        analyser.getFloatTimeDomainData(buffer)
        let energy = 0
        for (const value of buffer) energy += value * value
        const stride = Math.max(1, Math.floor(audioContext.sampleRate / 8000))
        const sample = Float32Array.from(buffer.filter((_, index) => index % stride === 0))
        points.push({ time, amplitude: Math.sqrt(energy / buffer.length), pitch: pitchEstimate(sample, audioContext.sampleRate / stride) })
        setLive([...points]); setElapsed(time)
        setGuideElapsed(time - pauseTotal.current / 1000 - (pausedAt.current ? (now - pausedAt.current) / 1000 : 0))
        if (time >= limit && capture.state === 'recording') capture.stop()
      }, 100)
    } catch (reason) {
      release()
      if (alive.current) { setBusy(false); setError(reason instanceof Error ? reason.message : 'Revisa el permiso del micrófono.') }
    }
  }

  function prepare() {
    if (!current?.speech?.transcriptText.trim()) return
    setReference(current); setCurrent(null); setLive([]); setElapsed(0); setPlayTime(0); setStep('prepare')
    setTarget(targetDuration(current.duration, current.speech.metrics.wordCount, pace, limit))
  }

  function pauseGuide() {
    if (pausedAt.current === null) { pausedAt.current = Date.now(); setPaused(true) }
    else { pauseTotal.current += Date.now() - pausedAt.current; pausedAt.current = null; setPaused(false) }
  }

  function setPlaybackRate() {
    if (audio.current && reference) { audio.current.playbackRate = reference.duration / planned; audio.current.preservesPitch = true }
  }

  return <dialog ref={dialog} className="voice-coach" aria-labelledby="voice-coach-title" onCancel={event => { event.preventDefault(); close() }}>
    <header className="voice-coach__header"><div><span className="eyebrow">Entrenador de voz local</span><h2 id="voice-coach-title">Ajusta tu voz y entonación</h2></div><button className="button button--ghost" onClick={close}>Cerrar</button></header>
    <p>Solo se conserva la referencia anterior y el intento actual mientras este modal esté abierto. No se añaden al historial.</p>
    <ol className="voice-coach__steps"><li aria-current={step === 'record' ? 'step' : undefined}>1. Grabar</li><li aria-current={step === 'review' ? 'step' : undefined}>2. Revisar</li><li aria-current={step === 'prepare' ? 'step' : undefined}>3. Ajustar</li><li aria-current={step === 'practice' ? 'step' : undefined}>4. Practicar</li></ol>
    {error && <p role="alert" className="training-alert training-alert--error">{error}</p>}
    {step === 'record' && <><p>Da tu discurso libremente. Después usaremos su transcripción como guion para repetirlo.</p><label className="terms-field"><input type="checkbox" checked={consent} disabled={busy || recording} onChange={event => setConsent(event.target.checked)} />Autorizo el procesamiento local de mi voz para esta práctica temporal.</label></>}
    {(recording || current || reference) && <section aria-label="Comparación de ondas">
      <p>Tiempo real: {formatDuration(elapsed)}{reference && ` · Objetivo ajustado: ${formatDuration(planned)} · Original: ${formatDuration(reference.duration)}`}</p>
      <svg viewBox="0 0 1000 100" preserveAspectRatio="none" className="voice-coach__wave" role="img" aria-label="Intensidad relativa: referencia sombreada e intento actual">
        <line x1="0" y1="50" x2="1000" y2="50" stroke="#cbd5e1" />
        {reference && <path d={waveformPath(reference.points.map(point => ({ ...point, time: point.time * planned / reference.duration })), axis, 14)} fill="#64748b" stroke="#64748b" strokeWidth="1" opacity="0.35" />}
        <path d={waveformPath(recording ? live : current?.points ?? [], axis)} fill="#216de0" fillOpacity="0.25" stroke="#216de0" strokeWidth="1" />
      </svg>
      <p className="voice-coach__legend">Gris: referencia adaptada al tiempo objetivo. Azul: intento actual. Escala compartida: 0–{formatDuration(axis)}. La altura indica intensidad relativa, no calidad.</p>
    </section>}
    {step === 'review' && current && <>
      <h3>Tu último intento</h3><audio controls src={current.url} aria-label="Escuchar último intento" />
      {busy && <p role="status">Transcribiendo con Whisper local…</p>}
      {!busy && !current.speech && <button className="button button--primary" onClick={() => void analyze(current)}>Reintentar transcripción</button>}
      {current.speech && <>
        <p>{current.speech.transcriptText || 'No se detectó discurso suficiente. Vuelve a grabar.'}</p>
        <dl className="stat-list"><div><dt>Ritmo</dt><dd>{current.speech.metrics.wordsPerMinute.toFixed(0)} palabras/min</dd></div><div><dt>Muletillas</dt><dd>{current.speech.metrics.fillerWordsTotal}</dd></div><div><dt>Pausa más larga</dt><dd>{current.speech.metrics.longestPauseSeconds?.toFixed(1) ?? '—'} s</dd></div></dl>
        <p>{current.speech.metrics.wordsPerMinute > 170 ? 'El ritmo es ágil. Puedes elegir más tiempo para practicar pausas.' : current.speech.metrics.wordsPerMinute < 110 ? 'El ritmo es pausado. Puedes elegir menos tiempo para practicar mayor fluidez.' : 'Elige el ritmo según tu objetivo y audiencia.'} Rangos orientativos, no una calificación.</p>
        {Object.entries(current.speech.metrics.fillerWordCounts).length > 0 && <p>Repeticiones de muletillas en la transcripción: {Object.entries(current.speech.metrics.fillerWordCounts).map(([word, count]) => `${word}: ${count}`).join(', ')}.</p>}
      </>}
      <h3>Observaciones de la señal</h3><ul>{observations.map((item, index) => <li key={index}><button className="voice-coach__seek" onClick={event => { const player = event.currentTarget.closest('dialog')?.querySelector('audio'); if (player) { player.currentTime = item.time; void player.play().catch(() => {}) } }}>{formatDuration(item.time)}</button> {item.text}</li>)}</ul>
      <svg viewBox="0 0 1000 120" className="voice-coach__pitch" role="img" aria-label="Tono estimado de 65 a 500 hercios, con huecos sin tono fiable">{current.points.map((point, index, points) => {
        const previous = points[index - 1]
        return point.pitch && previous?.pitch ? <line key={index} x1={previous.time / current.duration * 1000} y1={120 - (previous.pitch - 65) / 435 * 120} x2={point.time / current.duration * 1000} y2={120 - (point.pitch - 65) / 435 * 120} stroke="#7c3aed" strokeWidth="2" /> : null
      })}</svg><p>Curva tonal estimada: 65–500 Hz. Los huecos indican silencio o tono poco fiable; no representan emoción ni un diagnóstico.</p>
      {reference && <p>Comparación de ritmo: antes {reference.speech?.metrics.wordsPerMinute.toFixed(0)} palabras/min; ahora {current.speech?.metrics.wordsPerMinute.toFixed(0) ?? 'pendiente'}. Duración anterior {formatDuration(reference.duration)}; actual {formatDuration(current.duration)}. Compara también el contenido: omitir palabras no equivale a mejorar.</p>}
      <div className="workspace-actions"><button className="button button--primary" disabled={busy || !current.speech?.transcriptText.trim()} onClick={prepare}>Ajustar y practicar otra vez</button><button className="button button--ghost" disabled={busy} onClick={() => { setCurrent(null); setStep(reference ? 'practice' : 'record') }}>Descartar este intento</button></div>
    </>}
    {step === 'prepare' && reference && <>
      <h3>Elegí el objetivo para el mismo discurso</h3>
      <label>Ritmo objetivo<select value={pace} onChange={event => { const value = Number(event.target.value); setPace(value); setTarget(targetDuration(reference.duration, reference.speech!.metrics.wordCount, value, limit)) }}><option value={110}>Más pausado · 110 palabras/min</option><option value={140}>Equilibrado · 140 palabras/min</option><option value={170}>Más ágil · 170 palabras/min</option></select></label>
      <label>Duración objetivo en segundos<input type="number" min={Math.max(1, Math.ceil(reference.duration / 4))} max={Math.min(limit, Math.floor(reference.duration * 4))} value={target} onChange={event => setTarget(Number(event.target.value) || 1)} /></label>
      <p>Antes: {formatDuration(reference.duration)}. Próximo intento: {formatDuration(planned)}. El objetivo puede ser menor o mayor que el original; se limita a la duración permitida y a una velocidad de reproducción entre 0,25× y 4×.</p>
      <label>Palabras a enfatizar (separadas por comas)<input value={emphasis} onChange={event => setEmphasis(event.target.value)} placeholder="idea, solución, resultado" /></label>
      <p>Para dar fuerza a una idea, elige sus palabras clave y marca una pausa antes o después. La señal acústica por sí sola no identifica qué ideas merecen énfasis.</p>
      <audio ref={audio} controls src={reference.url} onLoadedMetadata={setPlaybackRate} onPlay={setPlaybackRate} onPlaying={() => setPreviewPlaying(true)} onWaiting={() => setPreviewPlaying(false)} onSeeking={() => setPreviewPlaying(false)} onSeeked={event => { setPlayTime(event.currentTarget.currentTime * planned / reference.duration); setPreviewPlaying(!event.currentTarget.paused) }} onPause={() => setPreviewPlaying(false)} onEnded={() => setPreviewPlaying(false)} onTimeUpdate={event => setPlayTime(event.currentTarget.currentTime * planned / reference.duration)} aria-label="Referencia con ritmo ajustado" />
      <p>Referencia de escucha: cambia la velocidad conservando el tono cuando el navegador lo permite. El énfasis se guía visualmente; no se sintetiza otra voz.</p>
      <button className="button button--primary" onClick={() => { audio.current?.pause(); setPreviewPlaying(false); setPlayTime(0); setStep('practice') }}>Ir al teleprompter</button>
    </>}
    {(step === 'practice' || step === 'prepare') && reference && <>
      <label>Tamaño del guion<input type="range" min="18" max="42" value={fontSize} onChange={event => setFontSize(Number(event.target.value))} /></label>
      <KaraokePrompter words={guide} time={clock} running={recording ? !paused : previewPlaying} fontSize={fontSize} emphasis={emphasis} />
      <p>El resaltado sigue el tiempo objetivo; no escucha en vivo si has terminado la palabra. Las pausas de la referencia se escalan al nuevo tiempo.</p>
      {recording && <button className="button button--ghost" onClick={pauseGuide}>{paused ? 'Continuar guion' : 'Pausar guion (el audio sigue grabándose)'}</button>}
      {step === 'practice' && !recording && <button className="button button--ghost" disabled={busy} onClick={() => setStep('prepare')}>Cambiar ajustes</button>}
    </>}
    {(step === 'record' || step === 'practice') && <div className="workspace-actions">{recording ? <button className="button button--danger" onClick={() => { if (recorder.current?.state === 'recording') recorder.current.stop() }}>Detener grabación</button> : <button className="button button--primary" disabled={!consent || busy} onClick={() => void start()}>{busy ? 'Preparando micrófono…' : reference ? 'Grabar nuevo intento' : 'Grabar discurso inicial'}</button>}</div>}
  </dialog>
}
