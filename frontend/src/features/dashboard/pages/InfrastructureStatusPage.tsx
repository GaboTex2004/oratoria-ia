import { useEffect, useState } from 'react'
import { testBackend } from '../../../shared/services/api'

export function InfrastructureStatusPage() {
  const [message, setMessage] = useState('Conectando con Spring Boot...')

  useEffect(() => {
    testBackend()
      .then(setMessage)
      .catch((error: unknown) => {
        console.error(error)
        setMessage('No se pudo conectar con Spring Boot')
      })
  }, [])

  return (
    <main id="center">
      <h1>Oratoria IA</h1>
      <p>{message}</p>
    </main>
  )
}
