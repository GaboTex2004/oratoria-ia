import assert from 'node:assert/strict'
import test from 'node:test'
import { pitchEstimate, scaledWords, targetDuration, voiceObservations, wordProgress } from '../src/features/training/utils/voiceCoach.ts'

test('el relleno karaoke avanza dentro de la palabra y se mantiene entre palabras', () => {
  const word = { word: 'Hola', startSeconds: 1, endSeconds: 3 }
  assert.equal(wordProgress(word, 0), 0)
  assert.equal(wordProgress(word, 1), 0)
  assert.equal(wordProgress(word, 1.5), 0.25)
  assert.equal(wordProgress(word, 2), 0.5)
  assert.equal(wordProgress(word, 3), 1)
  assert.equal(wordProgress(word, 4), 1)
  assert.equal(wordProgress({ ...word, endSeconds: 1 }, 1), 1)
})

test('el relleno sigue el objetivo tanto si se acorta como si se alarga el tiempo', () => {
  const words = [{ word: 'Idea', startSeconds: 2, endSeconds: 4 }]
  assert.equal(wordProgress(scaledWords(words, 10, 20)[0], 6), 0.5)
  assert.equal(wordProgress(scaledWords(words, 10, 5)[0], 1.5), 0.5)
})

test('el tiempo objetivo alarga discursos rápidos y acorta discursos lentos', () => {
  assert.equal(targetDuration(60, 210, 140, 300), 90)
  assert.equal(targetDuration(60, 70, 140, 300), 30)
  assert.equal(targetDuration(60, 140, 140, 300), 60)
  assert.equal(targetDuration(60, 1000, 110, 300), 300)
})

test('el karaoke adapta palabras y pausas a ambas escalas de tiempo sin modificar el original', () => {
  const words = [{ word: 'Hola', startSeconds: 1, endSeconds: 2 }, { word: 'mundo', startSeconds: 4, endSeconds: 5 }]
  assert.equal(scaledWords(words, 10, 20)[1].startSeconds, 8)
  assert.equal(scaledWords(words, 10, 5)[1].startSeconds, 2)
  assert.equal(words[1].startSeconds, 4)
})

test('el tono estimado reconoce una señal periódica y no inventa tono en silencio', () => {
  const samples = Float32Array.from({ length: 1024 }, (_, index) => 0.2 * Math.sin(2 * Math.PI * 200 * index / 8000))
  assert.ok(Math.abs(pitchEstimate(samples, 8000)! - 200) < 5)
  assert.equal(pitchEstimate(new Float32Array(1024), 8000), null)
})

test('las pausas producen observaciones sin afirmar emociones o repeticiones textuales', () => {
  const points = Array.from({ length: 40 }, (_, index) => ({ time: index / 10, amplitude: index < 30 ? 0 : 0.2, pitch: null }))
  assert.match(voiceObservations(points)[0].text, /Pausa o señal muy baja/)
})
