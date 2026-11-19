export interface VoicePoint { time: number; amplitude: number; pitch: number | null }
export interface TimedWord { word: string; startSeconds: number; endSeconds: number }

export function wordProgress(word: TimedWord, time: number): number {
  if (time < word.startSeconds) return 0
  if (word.endSeconds <= word.startSeconds) return 1
  return Math.max(0, Math.min(1, (time - word.startSeconds) / (word.endSeconds - word.startSeconds)))
}

export function targetDuration(duration: number, wordCount: number, pace: number, limit: number): number {
  const requested = wordCount > 0 ? wordCount * 60 / pace : duration
  return Math.max(1, Math.min(limit, Math.round(requested)))
}

export function scaledWords(words: TimedWord[], originalDuration: number, target: number): TimedWord[] {
  const factor = target / Math.max(0.001, originalDuration)
  return words.map(word => ({ ...word, startSeconds: word.startSeconds * factor, endSeconds: word.endSeconds * factor }))
}

export function pitchEstimate(samples: Float32Array, rate: number): number | null {
  let energy = 0
  let mean = 0
  for (const value of samples) mean += value
  mean /= samples.length
  for (const value of samples) energy += (value - mean) ** 2
  if (energy / samples.length < 0.0001) return null
  let best = 0
  let bestLag = 0
  for (let lag = Math.floor(rate / 500); lag <= Math.ceil(rate / 65); lag += 1) {
    let product = 0
    let left = 0
    let right = 0
    for (let index = 0; index < samples.length - lag; index += 1) {
      const first = samples[index] - mean
      const second = samples[index + lag] - mean
      product += first * second; left += first * first; right += second * second
    }
    const correlation = product / Math.max(1e-12, Math.sqrt(left * right))
    if (correlation > best + 0.01) { best = correlation; bestLag = lag }
  }
  return best > 0.8 && bestLag ? rate / bestLag : null
}

export function waveformPath(points: VoicePoint[], duration: number, height = 32): string {
  const upper = points.map((point, index) => `${index ? 'L' : 'M'}${Math.min(1000, point.time / Math.max(1, duration) * 1000).toFixed(1)},${(50 - Math.min(1, point.amplitude * 4) * height).toFixed(1)}`)
  const lower = [...points].reverse().map(point => `L${Math.min(1000, point.time / Math.max(1, duration) * 1000).toFixed(1)},${(50 + Math.min(1, point.amplitude * 4) * height).toFixed(1)}`)
  return points.length ? [...upper, ...lower, 'Z'].join(' ') : ''
}

export function voiceObservations(points: VoicePoint[]): { time: number; text: string }[] {
  if (!points.length) return []
  const result: { time: number; text: string }[] = []
  let quietStart: number | null = null
  for (const point of points) {
    if (point.amplitude < 0.01) quietStart ??= point.time
    else if (quietStart !== null) {
      if (point.time - quietStart >= 2 && result.length < 4) result.push({ time: quietStart, text: `Pausa o señal muy baja de ${(point.time - quietStart).toFixed(1)} segundos. Revisa si ayuda a separar ideas.` })
      quietStart = null
    }
  }
  if (quietStart !== null && points[points.length - 1].time - quietStart >= 2 && result.length < 4) {
    result.push({ time: quietStart, text: 'La grabación termina con una pausa o señal muy baja prolongada. Escucha si conviene cerrar antes.' })
  }
  const voiced = points.filter(point => point.pitch !== null)
  if (voiced.length >= 10) {
    const pitches = voiced.map(point => point.pitch!).sort((left, right) => left - right)
    const spread = 12 * Math.log2(pitches[Math.floor(pitches.length * 0.9)] / pitches[Math.floor(pitches.length * 0.1)])
    result.push({ time: voiced[0].time, text: spread < 2 ? 'Poca variación tonal estimada. Prueba enfatizar las palabras clave sin forzar la voz.' : 'Se observa variación tonal. Escucha si los cambios acompañan tus ideas principales.' })
  } else result.push({ time: 0, text: 'No hay suficiente tono fiable para valorar la entonación. Revisa ruido y distancia al micrófono.' })
  return result
}
