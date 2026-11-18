import { useResource } from './useResource'

export interface FeatureState { code: string; label: string; enabled: boolean }
export interface PlanFeatures { plan: string; features: FeatureState[] }

export function usePlanFeatures() {
  const { data, loading, error, reload } = useResource<PlanFeatures>('/subscriptions/current/features')
  // Until the plan loads nothing is shown as locked; the backend enforces the plan on every request anyway.
  const has = (code: string) => !data || data.features.some(feature => feature.code === code && feature.enabled)
  return { plan: data?.plan, features: data?.features ?? [], has, loading, error, reload }
}
