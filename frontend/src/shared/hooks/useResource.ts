import { useEffect, useState } from 'react'
import { apiRequest } from '../services/api'

interface Loaded<T> { path: string; revision: number; data: T | null; error: string }

export function useResource<T>(path: string) {
  const [revision, setRevision] = useState(0)
  const [loaded, setLoaded] = useState<Loaded<T> | null>(null)
  useEffect(() => {
    const controller = new AbortController()
    apiRequest<T>(path, { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) setLoaded({ path, revision, data: value, error: '' }) })
      .catch(reason => {
        if (!controller.signal.aborted) setLoaded({ path, revision, data: null, error: reason instanceof Error ? reason.message : 'No se pudo cargar la información.' })
      })
    return () => controller.abort()
  }, [path, revision])
  // A reload keeps showing the current data; a different path (filter, page, period) never shows the previous one.
  const samePath = loaded?.path === path
  const current = samePath && loaded?.revision === revision
  return {
    data: samePath ? loaded.data : null,
    loading: !current,
    error: current ? loaded.error : '',
    reload() { setRevision(value => value + 1) },
  }
}
