import { useResource } from '../hooks/useResource'
import { Icon } from './Icon'
import './systemAnnouncement.css'

export function SystemAnnouncement() {
  const announcement = useResource<{ message: string }>('/system/announcement')
  const message = announcement.data?.message.trim()
  if (!message) return null
  return <p className="system-announcement no-print" role="status"><Icon name="campaign" />{message}</p>
}
