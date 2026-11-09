import { useNavigate } from 'react-router-dom'
import { Icon } from '../../../shared/components/Icon'

interface TopbarProps { firstName: string; onMenuOpen: () => void }

export function Topbar({ firstName, onMenuOpen }: TopbarProps) {
  const navigate = useNavigate()
  return (
    <header className="topbar">
      <button className="topbar__menu" type="button" onClick={onMenuOpen} aria-label="Abrir menú"><Icon name="menu" /></button>
      <div className="topbar__welcome"><h1>Buenos días, {firstName}</h1><p>¿Qué quieres practicar hoy?</p></div>
      <div className="topbar__actions">
        <form className="search-field" role="search" onSubmit={event => {
          event.preventDefault()
          const query = new FormData(event.currentTarget).get('search')?.toString().trim() || ''
          navigate(`/training?q=${encodeURIComponent(query)}`)
        }}><Icon name="search" /><input name="search" type="search" placeholder="Buscar..." aria-label="Buscar" /></form>
        <button className="button button--primary button--pill" type="button" onClick={() => navigate('/training')}><Icon name="add" /> Nueva práctica</button>
        <span className="avatar" aria-label={`Avatar de ${firstName}`}>{firstName.charAt(0).toUpperCase()}</span>
      </div>
    </header>
  )
}
