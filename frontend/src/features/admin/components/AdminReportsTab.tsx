import { useState } from 'react'
import { useResource } from '../../../shared/hooks/useResource'
import { LEVEL_LABELS } from '../../../shared/labels'
import { REPORT_PERIODS, periodLabel } from '../../../shared/personalization'
import { errorText } from '../types'
import { downloadFile } from './adminDownload'

interface ExercisesReport {
  totalCompleted: number; freePractice: number; activeUsers: number
  items: { title: string; category: string; difficulty: string; completed: number; users: number; averageScore: number | null; practiceMinutes: number }[]
}
interface DifficultiesReport {
  evaluations: number
  criteria: { code: string; label: string; averageScore: number; evaluations: number; lowScores: number; lowScoreRate: number }[]
  frequentFillers: { filler: string; count: number }[]
}

export function AdminReportsTab() {
  const [days, setDays] = useState(30)
  const exercises = useResource<ExercisesReport>(`/admin/reports/exercises?days=${days}`)
  const difficulties = useResource<DifficultiesReport>(`/admin/reports/difficulties?days=${days}`)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')

  async function pdf(kind: 'exercises' | 'difficulties', name: string) {
    setBusy(kind); setError('')
    try { await downloadFile(`/admin/reports/${kind}.pdf?days=${days}`, `${name}-${new Date().toISOString().slice(0, 10)}.pdf`) }
    catch (reason) { setError(errorText(reason, 'No se pudo generar el PDF.')) }
    finally { setBusy('') }
  }

  return <>
    <section className="panel-card workspace-actions">
      <label className="workspace-form">Periodo de los reportes<select className="workspace-select" value={days} onChange={event => setDays(Number(event.target.value))}>
        {REPORT_PERIODS.map(period => <option key={period} value={period}>{periodLabel(period)}</option>)}
      </select></label>
    </section>
    {(error || exercises.error || difficulties.error) && <p role="alert" className="workspace-notice">{error || exercises.error || difficulties.error}</p>}

    <section className="panel-card">
      <div className="admin-report-head"><h2>Ejercicios completados</h2>
        <button className="button button--primary" disabled={busy !== '' || !exercises.data} onClick={() => void pdf('exercises', 'ejercicios-completados')}>{busy === 'exercises' ? 'Generando…' : 'Descargar PDF'}</button></div>
      {exercises.loading && <p role="status">Cargando…</p>}
      {exercises.data && <>
        <p className="admin-muted">{exercises.data.totalCompleted} prácticas completadas ({exercises.data.freePractice} libres, sin ejercicio) · {exercises.data.activeUsers} usuarios.</p>
        {exercises.data.items.length === 0 ? <p>No hay ejercicios completados en este periodo.</p> :
          <div className="workspace-table-wrap"><table className="workspace-table"><thead><tr><th>Ejercicio</th><th>Categoría</th><th>Nivel</th><th>Completados</th><th>Usuarios</th><th>Puntaje prom.</th><th>Minutos</th></tr></thead>
            <tbody>{exercises.data.items.map(item => <tr key={item.title}><td>{item.title}</td><td>{item.category}</td><td>{LEVEL_LABELS[item.difficulty] ?? item.difficulty}</td>
              <td>{item.completed}</td><td>{item.users}</td><td>{item.averageScore == null ? '—' : Math.round(item.averageScore)}</td><td>{item.practiceMinutes}</td></tr>)}</tbody></table></div>}
      </>}
    </section>

    <section className="panel-card">
      <div className="admin-report-head"><h2>Dificultades frecuentes</h2>
        <button className="button button--primary" disabled={busy !== '' || !difficulties.data} onClick={() => void pdf('difficulties', 'dificultades-frecuentes')}>{busy === 'difficulties' ? 'Generando…' : 'Descargar PDF'}</button></div>
      {difficulties.loading && <p role="status">Cargando…</p>}
      {difficulties.data && <>
        <p className="admin-muted">{difficulties.data.evaluations} evaluaciones con evidencia suficiente. Un puntaje es bajo cuando es menor a 60.</p>
        {difficulties.data.criteria.length === 0 ? <p>No hay evaluaciones con evidencia suficiente en este periodo.</p> :
          <div className="workspace-table-wrap"><table className="workspace-table"><thead><tr><th>Criterio</th><th>Promedio</th><th>Evaluaciones</th><th>Puntajes bajos</th><th>% bajos</th></tr></thead>
            <tbody>{difficulties.data.criteria.map(item => <tr key={item.code}><td>{item.label}</td><td>{item.averageScore}/100</td><td>{item.evaluations}</td><td>{item.lowScores}</td><td>{item.lowScoreRate} %</td></tr>)}</tbody></table></div>}
        <h3>Muletillas más usadas</h3>
        {difficulties.data.frequentFillers.length === 0 ? <p>No se registraron muletillas en este periodo.</p> :
          <ol className="admin-rank">{difficulties.data.frequentFillers.map(item => <li key={item.filler}><span>«{item.filler}»</span> <strong>{item.count}</strong></li>)}</ol>}
      </>}
    </section>
  </>
}
