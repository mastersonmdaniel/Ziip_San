import { useQuery } from '@tanstack/react-query'
import { Field, Select } from '@components/ui/Field'
import { errorMessage } from '@services/registry/errors'
import { catalogSource } from '@services/registry/resources'
import type { CatalogLookupItem } from '@services/registry/types'
import { defaultLookupLabel, toLookupValue, type LookupValue } from './LookupField'
import type { ReactNode } from 'react'

interface CatalogSelectProps {
  label: ReactNode
  /** Recurso do catálogo (ex.: 'water-statuses'). */
  resource: string
  value: LookupValue | null
  /** Recebe o item completo para regras que dependem de flags do catálogo (ex.: billable). */
  onChange: (value: LookupValue | null, item: CatalogLookupItem | null) => void
  required?: boolean
  disabled?: boolean
  error?: string | null
  hint?: ReactNode
  id?: string
  name?: string
}

/** Itens ativos de um catálogo pequeno (cache compartilhada com CatalogSelect). */
export function useCatalogItems(resource: string) {
  return useQuery({
    queryKey: ['catalog-select', resource],
    queryFn: ({ signal }) => catalogSource(resource).list({ size: 200, active: true, sort: 'name,asc' }, signal),
    staleTime: 60_000,
    retry: false,
  })
}

/**
 * Seleção em lista para catálogos pequenos. Lista só itens ativos, direto da API.
 * Estados honestos: carregando, erro (com a mensagem) e catálogo vazio. Nunca inventa opções.
 */
export function CatalogSelect({ label, resource, value, onChange, required, disabled, error, hint, id, name }: CatalogSelectProps) {
  const query = useCatalogItems(resource)
  const items = query.data?.content ?? []
  const selectedMissing = value !== null && !items.some((i) => i.id === value.id)

  let status: string | null = null
  if (query.isLoading) status = 'Carregando opções...'
  else if (query.isError) status = `Não foi possível carregar as opções: ${errorMessage(query.error)}`
  else if (items.length === 0) status = 'Nenhum item cadastrado neste catálogo. Cadastre em Catálogos.'

  return (
    <Field id={id} label={label} required={required} error={error} hint={status ?? hint}>
      {(aria) => (
        <Select
          {...aria}
          name={name}
          value={value?.id ?? ''}
          disabled={disabled || query.isLoading}
          onChange={(event) => {
            const picked = items.find((i) => i.id === event.target.value) ?? null
            onChange(picked ? toLookupValue(picked) : null, picked)
          }}
        >
          <option value="">Selecione...</option>
          {selectedMissing && value ? <option value={value.id}>{value.label}</option> : null}
          {items.map((item) => (
            <option key={item.id} value={item.id}>
              {defaultLookupLabel(item)}
            </option>
          ))}
        </Select>
      )}
    </Field>
  )
}
