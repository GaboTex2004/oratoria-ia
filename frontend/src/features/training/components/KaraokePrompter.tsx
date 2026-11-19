import { useEffect, useLayoutEffect, useRef } from 'react'
import { wordProgress, type TimedWord } from '../utils/voiceCoach'

interface Props {
  words: TimedWord[]
  time: number
  running: boolean
  fontSize: number
  emphasis: string
}

export function KaraokePrompter({ words, time, running, fontSize, emphasis }: Props) {
  const container = useRef<HTMLDivElement>(null)
  const position = useRef({ time: 0, running: false, updatedAt: 0 })
  const emphasisWords = new Set(emphasis.toLocaleLowerCase('es').split(/[\s,]+/).filter(Boolean))

  useLayoutEffect(() => {
    position.current = { time, running, updatedAt: performance.now() }
  }, [time, running])

  useEffect(() => {
    const element = container.current
    if (!element) return
    const spans = Array.from(element.querySelectorAll<HTMLSpanElement>('[data-karaoke-word]'))
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
    let frame = 0
    let previousFrame = performance.now()
    function animate(now: number) {
      const anchor = position.current
      const clock = anchor.time + (anchor.running ? (now - anchor.updatedAt) / 1000 : 0)
      let active: HTMLSpanElement | undefined
      spans.forEach((span, index) => {
        const word = words[index]
        const progress = wordProgress(word, clock)
        span.style.setProperty('--word-progress', `${progress * 100}%`)
        const current = clock >= word.startSeconds && clock < word.endSeconds
        span.classList.toggle('is-current', current)
        span.classList.toggle('is-read', progress === 1)
        if (current) active = span
      })
      if (active && anchor.running && element) {
        const target = Math.max(0, Math.min(element.scrollHeight - element.clientHeight,
          active.offsetTop - element.clientHeight * 0.4))
        const difference = target - element.scrollTop
        const fraction = reducedMotion.matches ? 1 : 1 - Math.exp(-Math.min(64, now - previousFrame) / 180)
        if (Math.abs(difference) > 0.5) element.scrollTop += difference * fraction
      }
      previousFrame = now
      frame = requestAnimationFrame(animate)
    }
    frame = requestAnimationFrame(animate)
    return () => cancelAnimationFrame(frame)
  }, [words])

  return <div ref={container} className="voice-coach__prompter" style={{ fontSize }} aria-label="Teleprompter con tiempo objetivo">
    {words.map((word, index) => <span key={index} data-karaoke-word className={`voice-coach__word ${emphasisWords.has(word.word.toLocaleLowerCase('es').replace(/[^\p{L}\p{N}]/gu, '')) ? 'is-emphasis' : ''}`}>{word.word} </span>)}
  </div>
}
