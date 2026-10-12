import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { Field, Input } from '@components/ui/Field'
import { useDebounced } from '@hooks/useDebounced'
import { toApiError } from '@services/registry/errors'
import type { FilterValue, ListParams, Page, Uuid } from '@services/registry/types'
import { cn } from '@utils/cn'

/** Valor guardado no formulário: id para enviar e rótulo para exibir (sem nova consulta). */
export interface LookupValue {
  id: Uuid
  label: string
  /** Código da entidade (ex.: código da localidade), quando houver. */
  code?: string | number | null
}

export interface LookupItem {
  id: Uuid
  code?: string | number | null
  name?: string | null
}

export interface LookupSource<T extends LookupItem> {
  /** Identifica o recurso na cache de consultas. */
  path: string
  list(params?: ListParams, signal?: AbortSignal): Promise<Page<T>>
  /** Fonte de pessoa: o texto digitado vai pelo corpo de search() (POST), nunca na URL. */
  personalSearch?: boolean
  search?(params?: ListParams, signal?: AbortSignal): Promise<Page<T>>
}

export interface LookupFieldProps<T extends LookupItem> {
  label: ReactNode
  value: LookupValue | null
  onChange: (value: LookupValue | null, item: T | null) => void
  source: LookupSource<T>
  /** Filtros fixos (ex.: { localityId } na cascata localidade > setor). */
  filters?: Record<string, FilterValue>
  getLabel?: (item: T) => string
  required?: boolean
  disabled?: boolean
  error?: string | null
  hint?: ReactNode
  placeholder?: string
  id?: string
  name?: string
}

export function defaultLookupLabel(item: LookupItem): string {
  const name = item.name ?? ''
  const code = item.code
  if (code !== undefined && code !== null && code !== '') return name ? `${code} · ${name}` : String(code)
  return name || item.id
}

export function toLookupValue<T extends LookupItem>(item: T, getLabel?: (item: T) => string): LookupValue {
  return { id: item.id, label: getLabel ? getLabel(item) : defaultLookupLabel(item), code: item.code ?? null }
}

/** Converte a referência resumida de uma resposta da API em valor de formulário. */
export function refToLookupValue(ref: { id: Uuid; code?: string | number | null; name: string } | null | undefined): LookupValue | null {
  if (!ref) return null
  return { id: ref.id, label: defaultLookupLabel(ref), code: ref.code ?? null }
}

const PAGE_SIZE = 10

/**
 * Campo de busca com lista (padrão combobox ARIA 1.2): digite código ou nome, use as setas e
 * Enter para escolher. Mostra "código · nome" no próprio campo. Substitui os popups do GSAN.
 */
export function LookupField<T extends LookupItem>({
  label,
  value,
  onChange,
  source,
  filters,
  getLabel,
  required,
  disabled,
  error,
  hint,
  placeholder = 'Digite o código ou o nome',
  id,
  name,
}: LookupFieldProps<T>) {
  const listboxId = useId()
  const [text, setText] = useState(value?.label ?? '')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const rootRef = useRef<HTMLDivElement>(null)
  const typing = useRef(false)

  // Sincroniza o texto quando o valor muda por fora (reset do formulário, rascunho carregado).
  const valueLabel = value?.label
  useEffect(() => {
    if (valueLabel === undefined && typing.current) {
      typing.current = false
      return
    }
    setText(valueLabel ?? '')
  }, [valueLabel])

  // Só busca enquanto o usuário digita; com valor escolhido e texto igual ao rótulo, lista tudo.
  const searching = value !== null && text === value.label ? '' : text
  const debounced = useDebounced(searching, 250)
  const filterKey = JSON.stringify(filters ?? {})

  const query = useQuery({
    queryKey: ['lookup', source.path, filterKey, debounced],
    queryFn: ({ signal }) => {
      const params = { ...filters, q: debounced, page: 0, size: PAGE_SIZE }
      return source.personalSearch && source.search ? source.search(params, signal) : source.list(params, signal)
    },
    enabled: open && !disabled,
    staleTime: 30_000,
    retry: false,
  })

  const items = useMemo(() => query.data?.content ?? [], [query.data])
  const labelOf = (item: T) => (getLabel ? getLabel(item) : defaultLookupLabel(item))

  useEffect(() => {
    setActive(0)
  }, [items])

  useEffect(() => {
    if (!open) return undefined
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    return () => document.removeEventListener('mousedown', onPointerDown)
  }, [open])

  const select = (item: T) => {
    typing.current = false
    const next = toLookupValue(item, getLabel)
    setText(next.label)
    setOpen(false)
    onChange(next, item)
  }

  const clear = () => {
    typing.current = false
    setText('')
    setOpen(false)
    onChange(null, null)
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      if (!open) setOpen(true)
      else setActive((i) => Math.min(i + 1, Math.max(items.length - 1, 0)))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((i) => Math.max(i - 1, 0))
    } else if (event.key === 'Enter' && open) {
      const item = items[active]
      if (item) {
        event.preventDefault()
        select(item)
      }
    } else if (event.key === 'Escape' && open) {
      event.stopPropagation()
      setOpen(false)
    }
  }

  const status = query.isFetching
    ? 'Buscando...'
    : query.isError
      ? `Não foi possível buscar: ${toApiError(query.error).message}`
      : items.length === 0
        ? 'Nenhum resultado.'
        : `${items.length} ${items.length === 1 ? 'resultado' : 'resultados'}. Use as setas para escolher.`

  return (
    <Field id={id} label={label} required={required} error={error} hint={hint}>
      {(aria) => (
        <div ref={rootRef} className="relative">
          <div className="relative">
            <Input
              {...aria}
              name={name}
              role="combobox"
              aria-expanded={open}
              aria-controls={listboxId}
              aria-autocomplete="list"
              aria-activedescendant={open && items[active] ? `${listboxId}-${active}` : undefined}
              value={text}
              disabled={disabled}
              placeholder={placeholder}
              autoComplete="off"
              className={value ? 'pr-10' : undefined}
              onFocus={() => setOpen(true)}
              onChange={(event) => {
                const next = event.target.value
                setText(next)
                setOpen(true)
                if (value && next !== value.label) {
                  typing.current = true
                  onChange(null, null)
                }
              }}
              onKeyDown={onKeyDown}
            />
            {value && !disabled ? (
              <button
                type="button"
                onClick={clear}
                aria-label="Limpar seleção"
                className="absolute right-1 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            ) : null}
          </div>
          {open ? (
            <ul
              id={listboxId}
              role="listbox"
              aria-label="Resultados da busca"
              className="absolute z-30 mt-1 max-h-64 w-full overflow-auto rounded-lg border bg-popover p-1 text-popover-foreground shadow-lg"
            >
              {items.map((item, index) => (
                <li
                  key={item.id}
                  id={`${listboxId}-${index}`}
                  role="option"
                  aria-selected={value?.id === item.id}
                  onMouseDown={(event) => {
                    event.preventDefault()
                    select(item)
                  }}
                  onMouseEnter={() => setActive(index)}
                  className={cn(
                    'cursor-pointer rounded-md px-3 py-2 text-sm',
                    index === active && 'bg-accent text-accent-foreground',
                    value?.id === item.id && 'font-semibold',
                  )}
                >
                  {labelOf(item)}
                </li>
              ))}
              {items.length === 0 ? (
                <li role="presentation" className="px-3 py-2 text-sm text-muted-foreground">
                  {query.isFetching ? 'Buscando...' : query.isError ? 'Erro na busca.' : 'Nenhum resultado.'}
                </li>
              ) : null}
            </ul>
          ) : null}
          <span className="sr-only" role="status" aria-live="polite">
            {open ? status : ''}
          </span>
        </div>
      )}
    </Field>
  )
}
