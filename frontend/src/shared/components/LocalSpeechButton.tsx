import { useEffect, useState } from 'react'

export function LocalSpeechButton({ text }: { text: string }) {
  const [voices, setVoices] = useState<SpeechSynthesisVoice[]>([])
  useEffect(() => {
    if (!('speechSynthesis' in window)) return
    const update = () => setVoices(window.speechSynthesis.getVoices().filter(voice => voice.localService && voice.lang.startsWith('es')))
    update()
    window.speechSynthesis.addEventListener('voiceschanged', update)
    return () => { window.speechSynthesis.removeEventListener('voiceschanged', update); window.speechSynthesis.cancel() }
  }, [])
  function speak() {
    const voice = voices[0]
    if (!voice?.localService) return
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.voice = voice; utterance.lang = voice.lang
    window.speechSynthesis.cancel(); window.speechSynthesis.speak(utterance)
  }
  return <button className="button button--ghost" type="button" disabled={!voices.length} onClick={speak} title={voices.length ? 'Utiliza la voz local instalada en el sistema' : 'No hay una voz local en español instalada'}>{voices.length ? 'Escuchar con voz local' : 'Voz local no disponible'}</button>
}
