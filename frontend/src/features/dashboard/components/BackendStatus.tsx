import { useEffect, useState } from 'react'
import { testBackend } from '../../../shared/services/api'
import { Icon } from '../../../shared/components/Icon'

export function BackendStatus() {
  const [status, setStatus] = useState<'checking' | 'online' | 'offline'>('checking')
  useEffect(() => { testBackend().then(() => setStatus('online')).catch(() => setStatus('offline')) }, [])
  const labels = { checking: 'Comprobando infraestructura', online: 'Spring Boot conectado', offline: 'Spring Boot no disponible' }
  return <div className={`backend-status backend-status--${status}`}><Icon name={status === 'online' ? 'cloud_done' : status === 'offline' ? 'cloud_off' : 'sync'} /><span>{labels[status]}</span></div>
}
