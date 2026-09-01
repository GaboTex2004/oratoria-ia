import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest } from '../../../shared/services/api'

export function AdminPage() {
  const [message, setMessage] = useState('Verificando acceso…')
  useEffect(() => { apiRequest<{ message: string }>('/admin/status').then((data) => setMessage(data.message)).catch((error: Error) => setMessage(error.message)) }, [])
  return <main className="placeholder-page"><section className="placeholder-card"><span className="eyebrow">Administración</span><h1>Panel administrativo</h1><p>{message}</p><Link className="button button--primary" to="/home">Volver al inicio</Link></section></main>
}
