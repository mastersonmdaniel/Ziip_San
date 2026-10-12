import { useQuery } from '@tanstack/react-query'
import { getSettings } from '@services/registry/resources'
import type { RegistrySettings } from '@services/registry/types'

/** Sem a API, assume tudo desligado: a validação do servidor continua valendo. */
export const DEFAULT_SETTINGS: RegistrySettings = {
  useBlockFace: false,
  requireReadingSequence: false,
}

export function useRegistrySettings(): { settings: RegistrySettings; loaded: boolean } {
  const query = useQuery({
    queryKey: ['registry', 'settings'],
    queryFn: ({ signal }) => getSettings(signal),
    staleTime: 5 * 60 * 1000,
    retry: false,
  })
  return { settings: query.data ?? DEFAULT_SETTINGS, loaded: query.isSuccess }
}
