import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useResource } from '../../shared/hooks/useResource'
import { useRecordingLimit } from '../../shared/hooks/useRecordingLimit'
import { apiRequest } from '../../shared/services/api'
import { trainingService } from '../training/services/trainingService'
import { formatDuration, type Exercise, type SpeechAnalysis, type SpeechEvaluation } from '../training/types/training'

interface BodyResult {
  framesAnalyzed: number
  framesWithPerson: number
  shoulderTiltDegrees: number | null
  headOffsetDegrees: number | null
  bodyMovementNormalized: number | null
  wristMovementNormalized: number | null
  limitations: string[]
}

type Phase = 'idle' | 'permission' | 'recording' | 'recorded' | 'saving' | 'saved'

export function AudiovisualPractice({ locked }: { locked: boolean }) {
  const exercises = useResource<Exercise[]>('/exercises')
  const limit = useRecordingLimit()
  const [exerciseId, setExerciseId] = useState('')
  const [consent, setConsent] = useState(false)
  const [phase, setPhase] = useState<Phase>('idle')
  const [seconds, setSeconds] = useState(0)
  const [recording, setRecording] = useState<Blob | null>(null)
  const [preview, setPreview] = useState('')
  const [error, setError] = useState('')
  const [bodyError, setBodyError] = useState('')
  const [voiceError, setVoiceError] = useState('')
  const [sessionId, setSessionId] = useState('')
  const [audioSaved, setAudioSaved] = useState(false)
  const [body, setBody] = useState<BodyResult | null>(null)
  const [analysis, setAnalysis] = useState<SpeechAnalysis | null>(null)
  const [evaluation, setEvaluation] = useState<SpeechEvaluation | null>(null)
  const [retrying, setRetrying] = useState(false)
  const video = useRef<HTMLVideoElement>(null)
  const stream = useRef<MediaStream | null>(null)
  const recorder = useRef<MediaRecorder | null>(null)
  const timer = useRef<number | null>(null)
  const frames = useRef<string[]>([])
  const alive = useRef(true)
  const selected = exercises.data?.find(exercise => exercise.id === exerciseId)
  const active = phase === 'permission' || phase === 'recording' || phase === 'saving'

  function releaseMedia() {
    if (timer.current !== null) window.clearInterval(timer.current)
    timer.current = null
    stream.current?.getTracks().forEach(track => track.stop())
    stream.current = null
  }

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
      if (recorder.current) {
        recorder.current.onstop = null
        recorder.current.ondataavailable = null
        recorder.current.onerror = null
        if (recorder.current.state !== 'inactive') recorder.current.stop()
      }
      releaseMedia()
    }
  }, [])

  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])

  useEffect(() => {
    if (!sessionId || !audioSaved) return
    let cancelled = false
    let timeout: number | undefined
    let attempts = 0
    async function poll() {
      const results = await Promise.allSettled([
        trainingService.getSessionAnalysis(sessionId),
        trainingService.getSessionEvaluation(sessionId),
      ])
      if (cancelled) return
      const [speech, feedback] = results
      if (speech.status === 'fulfilled') setAnalysis(speech.value)
      if (feedback.status === 'fulfilled') setEvaluation(feedback.value)
      if (speech.status === 'rejected') setVoiceError('No se pudo consultar el análisis de voz. Puedes abrir los resultados completos.')
      else setVoiceError('')
      const speechDone = speech.status === 'fulfilled' && ['COMPLETED', 'FAILED'].includes(speech.value.status)
      const feedbackDone = feedback.status === 'fulfilled' && ['COMPLETED', 'FAILED'].includes(feedback.value.status)
      attempts += 1
      if (!(speechDone && (feedbackDone || speech.value.status === 'FAILED')) && attempts < 180) {
        timeout = window.setTimeout(() => { void poll() }, 3000)
      }
    }
    void poll()
    return () => { cancelled = true; window.clearTimeout(timeout) }
  }, [sessionId, audioSaved, retrying])

  function sampleFrame() {
    if (!video.current?.videoWidth || !video.current.videoHeight) return
    const canvas = document.createElement('canvas')
    canvas.width = 480
    canvas.height = Math.round(480 * video.current.videoHeight / video.current.videoWidth)
    const context = canvas.getContext('2d')
    if (!context) return
    context.drawImage(video.current, 0, 0, canvas.width, canvas.height)
    frames.current.push(canvas.toDataURL('image/jpeg', 0.75).split(',')[1])
  }

  async function start() {
    if (locked || !consent || !selected || active) return
    setError('')
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setError('Tu navegador no permite grabar audio y cámara. Prueba con una versión reciente de Chrome o Edge.')
      return
    }
    setPhase('permission')
    setRecording(null); setPreview(''); setSeconds(0)
    setSessionId(''); setAudioSaved(false); setBody(null); setBodyError(''); setVoiceError('')
    setAnalysis(null); setEvaluation(null); frames.current = []
    try {
      const media = await navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480 }, audio: true })
      if (!alive.current) { media.getTracks().forEach(track => track.stop()); return }
      stream.current = media
      if (!video.current) throw new Error('No se pudo preparar la vista previa.')
      video.current.srcObject = media
      await video.current.play()
      if (!alive.current) { releaseMedia(); return }
      const mime = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4']
        .find(candidate => MediaRecorder.isTypeSupported(candidate))
      const audioStream = new MediaStream(media.getAudioTracks())
      const capture = mime ? new MediaRecorder(audioStream, { mimeType: mime }) : new MediaRecorder(audioStream)
      recorder.current = capture
      const chunks: Blob[] = []
      let bytes = 0
      let failed = false
      const startedAt = Date.now()
      let interval = 1000
      let nextSample = interval
      capture.ondataavailable = event => {
        if (event.data.size) { chunks.push(event.data); bytes += event.data.size }
        if (bytes > 15 * 1024 * 1024 && capture.state === 'recording') capture.stop()
      }
      capture.onerror = () => {
        failed = true
        releaseMedia()
        if (alive.current) { setError('La grabación se interrumpió. Vuelve a intentarlo.'); setPhase('idle') }
      }
      capture.onstop = () => {
        if (alive.current && !failed) {
          if (frames.current.length >= 12) frames.current.pop()
          sampleFrame()
        }
        releaseMedia()
        if (!alive.current || failed) return
        const blob = new Blob(chunks, { type: capture.mimeType || mime || 'audio/webm' })
        if (!blob.size || blob.size > 15 * 1024 * 1024 || !frames.current.length) {
          setError('No se pudo conservar la captura. Comprueba la cámara y graba menos de 15 MB.'); setPhase('idle'); return
        }
        setSeconds(Math.max(1, Math.min(limit, Math.round((Date.now() - startedAt) / 1000))))
        setRecording(blob); setPreview(URL.createObjectURL(blob)); setPhase('recorded')
      }
      capture.start(250)
      sampleFrame()
      setPhase('recording')
      timer.current = window.setInterval(() => {
        const elapsed = Date.now() - startedAt
        setSeconds(Math.min(limit, Math.floor(elapsed / 1000)))
        if (elapsed >= nextSample) {
          if (frames.current.length >= 12) {
            frames.current = frames.current.filter((_, index) => index % 2 === 0)
            interval *= 2
          }
          sampleFrame()
          nextSample = elapsed + interval
        }
        if (elapsed >= limit * 1000 && capture.state === 'recording') capture.stop()
      }, 250)
    } catch {
      releaseMedia()
      if (alive.current) { setPhase('idle'); setError('No se pudo iniciar la cámara y el micrófono. Revisa los permisos y que ambos dispositivos estén disponibles.') }
    }
  }

  async function save() {
    if (!recording || !selected || active) return
    setPhase('saving'); setError('')
    try {
      const target = sessionId || (await trainingService.createSession({ exerciseId })).id
      if (!alive.current) return
      setSessionId(target)
      const jobs: Promise<void>[] = []
      if (!audioSaved) jobs.push(trainingService.completeSession(target, recording, seconds).then(() => {
        if (alive.current) { setAudioSaved(true); setError('') }
      }).catch(reason => { if (alive.current) setError(reason instanceof Error ? reason.message : 'No se pudo guardar el audio. Reintenta sin volver a grabar.') }))
      if (!body) jobs.push(apiRequest<BodyResult>(`/sessions/${target}/body-analysis`, {
        method: 'POST', body: JSON.stringify({ consent, frames: frames.current }),
      }).then(value => { if (alive.current) { setBody(value); setBodyError('') } })
        .catch(reason => { if (alive.current) setBodyError(reason instanceof Error ? reason.message : 'No se pudo analizar el cuerpo.') }))
      await Promise.all(jobs)
      if (alive.current) setPhase('saved')
    } catch (reason) {
      if (alive.current) { setError(reason instanceof Error ? reason.message : 'No se pudo crear la práctica.'); setPhase('recorded') }
    }
  }

  async function retryVoice() {
    setRetrying(true); setVoiceError('')
    try {
      if (analysis?.status === 'FAILED') setAnalysis(await trainingService.retrySessionAnalysis(sessionId))
      else setEvaluation(await trainingService.retrySessionEvaluation(sessionId))
    } catch (reason) { setVoiceError(reason instanceof Error ? reason.message : 'No se pudo reintentar.') }
    finally { if (alive.current) setRetrying(false) }
  }

  return <section className="panel-card workspace-form" aria-label="Práctica con audio y cámara">
    <h2>Práctica con audio y cámara</h2>
    <p>Graba tu discurso mientras se toman hasta doce imágenes distribuidas durante la práctica. Se crea una sesión nueva; el audio se guarda de forma privada y las imágenes solo se usan para calcular indicadores.</p>
    <label>Ejercicio audiovisual<select value={exerciseId} disabled={active || phase === 'recorded' || phase === 'saved'} onChange={event => setExerciseId(event.target.value)}>
      <option value="">Selecciona un ejercicio</option>
      {exercises.data?.map(exercise => <option key={exercise.id} value={exercise.id}>{exercise.title}</option>)}
    </select></label>
    {exercises.error && <p role="alert">{exercises.error}</p>}
    {selected && <p><strong>Consigna: </strong>{selected.prompt}</p>}
    <label className="terms-field"><input type="checkbox" checked={consent} disabled={active || phase === 'recorded' || phase === 'saved'} onChange={event => setConsent(event.target.checked)} />Autorizo grabar mi audio y procesar localmente las imágenes de mi cámara.</label>
    <video className="camera-preview" ref={video} muted playsInline aria-label="Vista previa audiovisual" />
    <p role="status">{phase === 'recording' ? 'Grabando audio y cámara' : phase === 'permission' ? 'Esperando permisos' : phase === 'saving' ? 'Guardando y analizando' : 'Duración'}: {formatDuration(seconds)} / {formatDuration(limit)}</p>
    {error && <p role="alert">{error}</p>}
    <div className="workspace-actions">
      <button className="button button--primary" disabled={locked || !selected || !consent || active} onClick={start}>{recording ? 'Nueva grabación' : 'Grabar audio y cámara'}</button>
      {phase === 'recording' && <button className="button button--danger" onClick={() => recorder.current?.stop()}>Detener audio y cámara</button>}
      {recording && <button className="button button--primary" disabled={active || (audioSaved && !!body)} onClick={save}>{phase === 'saved' ? 'Reintentar pendientes' : 'Guardar y analizar audio y cuerpo'}</button>}
    </div>
    {preview && <audio controls src={preview} aria-label="Audio de la práctica audiovisual" />}
    {sessionId && <>
      <h3>Análisis de voz</h3>
      <p>{audioSaved ? 'Audio guardado. La transcripción y la evaluación se actualizan automáticamente.' : 'Audio pendiente de guardar.'}</p>
      {voiceError && <p role="alert">{voiceError}</p>}
      {analysis && <p>Transcripción: {analysis.status === 'COMPLETED' ? 'Lista' : analysis.status === 'FAILED' ? analysis.errorMessage : 'Procesando…'}</p>}
      {analysis?.transcriptText && <p>{analysis.transcriptText}</p>}
      {analysis?.metrics && <dl className="stat-list">
        <div><dt>Palabras por minuto</dt><dd>{analysis.metrics.wordsPerMinute.toFixed(1)}</dd></div>
        <div><dt>Muletillas</dt><dd>{analysis.metrics.fillerWordsTotal}</dd></div>
        <div><dt>Pausas estimadas</dt><dd>{analysis.metrics.estimatedPauseCount ?? '—'}</dd></div>
      </dl>}
      {evaluation && <p>Evaluación: {evaluation.status === 'COMPLETED' ? (evaluation.executiveSummary || evaluation.insufficientEvidenceReason || 'Lista') : evaluation.status === 'FAILED' ? evaluation.errorMessage : 'Procesando…'}</p>}
      {((analysis?.status === 'FAILED' && analysis.retryable) || (evaluation?.status === 'FAILED' && evaluation.retryable)) && <button className="button button--ghost" disabled={retrying} onClick={retryVoice}>Reintentar análisis de voz</button>}
      {audioSaved && <Link to={`/results?sessionId=${encodeURIComponent(sessionId)}`}>Ver resultados completos de esta práctica</Link>}
      <h3>Análisis corporal de la misma grabación</h3>
      {bodyError && <p role="alert">{bodyError} El audio se procesa por separado; puedes reintentar los pendientes.</p>}
      {body && <>
        <p>Persona visible: {body.framesWithPerson}/{body.framesAnalyzed} imágenes.</p>
        {!body.framesWithPerson ? <p>No se detectó una persona. Mejora la iluminación y mantén cabeza y hombros visibles.</p> : <dl className="stat-list">
          <div><dt>Inclinación de hombros</dt><dd>{body.shoulderTiltDegrees?.toFixed(1) ?? '—'}°</dd></div>
          <div><dt>Desplazamiento de cabeza</dt><dd>{body.headOffsetDegrees?.toFixed(1) ?? '—'}°</dd></div>
          <div><dt>Movimiento corporal</dt><dd>{body.bodyMovementNormalized ?? '—'}</dd></div>
          <div><dt>Movimiento de muñecas</dt><dd>{body.wristMovementNormalized ?? '—'}</dd></div>
        </dl>}
        <ul>{body.limitations.map(item => <li key={item}>{item}</li>)}</ul>
      </>}
    </>}
  </section>
}
