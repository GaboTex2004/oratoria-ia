import { Icon } from '../../../shared/components/Icon'

interface TopbarProps { firstName: string; onMenuOpen: () => void }

export function Topbar({ firstName, onMenuOpen }: TopbarProps) {
  return (
    <header className="topbar">
      <button className="topbar__menu" type="button" onClick={onMenuOpen} aria-label="Abrir menú"><Icon name="menu" /></button>
      <div className="topbar__welcome"><h1>Buenos días, {firstName}</h1><p>¿Qué quieres practicar hoy?</p></div>
      <div className="topbar__actions">
        <label className="search-field"><Icon name="search" /><input type="search" placeholder="Buscar..." aria-label="Buscar" /></label>
        <button className="button button--primary button--pill" type="button"><Icon name="add" /> Nueva práctica</button>
        <span className="avatar" aria-label={`Avatar de ${firstName}`}>{firstName.charAt(0).toUpperCase()}</span>
      </div>
    </header>
  )
}
