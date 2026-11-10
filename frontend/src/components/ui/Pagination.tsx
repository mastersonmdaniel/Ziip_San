import { useId, useState, type FormEvent } from 'react'
import { Icon } from '@components/ui/Icon'
import { Button } from './Button'
import { Input, Select } from './Field'

/**
 * Pagination — copia da implementacao ENTREGUE na ED-08
 * (ed-08/entrega/frontend/src/components/ui/Pagination.tsx), incluindo
 * comentarios e textos. Unica diferenca: nenhuma na logica; as classes
 * visuais internas passam pelas classes do tema (ui-*) via Button/
 * Input/Select desta biblioteca, como no ficheiro entregue passavam
 * pelos mesmos componentes.
 *
 * Tamanhos de página oferecidos no seletor (ED-08). O teto é o do servidor:
 * a P0.16 fixou `size` máximo de 100 nas listas, e o padrão do cadastro é 25
 * (`DEFAULT_PAGE_SIZE` em services/registry/client.ts).
 */
export const PAGE_SIZE_OPTIONS: readonly number[] = [10, 25, 50, 100]

interface PaginationProps {
  /** Página atual, base 0. */
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
  /**
   * ED-08: tamanho de página atual. O seletor de tamanho só aparece quando
   * `onPageSizeChange` é informado; sem ele, o componente é exatamente o de
   * antes (Anterior/Próxima), para não mudar as listas que ainda não o usam.
   */
  pageSize?: number
  onPageSizeChange?: (size: number) => void
}

export function Pagination({ page, totalPages, totalElements, onPageChange, pageSize = 25, onPageSizeChange }: PaginationProps) {
  const uid = useId().replace(/:/g, '')
  const sizeId = `pg-size-${uid}`
  const goToId = `pg-goto-${uid}`
  const [target, setTarget] = useState('')
  const [goToError, setGoToError] = useState<string | null>(null)
  const last = Math.max(totalPages - 1, 0)
  const maxPage = Math.max(totalPages, 1)

  /**
   * ED-08: "ir para página". A página digitada é validada contra o intervalo
   * real (1..totalPages): fora dele, NÃO navega e avisa no lugar — nunca chama
   * `onPageChange` com página inexistente. Nas listas keyset, quem recebe o
   * `onPageChange` traduz o número em avanço de cursores (âncoras), sem offset:
   * ver utils/keysetPagination.ts.
   */
  function goTo(event: FormEvent) {
    event.preventDefault()
    const parsed = Number(target)
    if (!Number.isInteger(parsed) || parsed < 1 || parsed > maxPage) {
      setGoToError(`Digite uma página entre 1 e ${maxPage}.`)
      return
    }
    setGoToError(null)
    onPageChange(parsed - 1)
  }

  return (
    <nav aria-label="Paginação" className="ui-pagination">
      <p className="ui-pagination-summary" aria-live="polite">
        {totalElements === 0
          ? 'Nenhum registro'
          : `Página ${page + 1} de ${Math.max(totalPages, 1)} · ${totalElements} ${totalElements === 1 ? 'registro' : 'registros'}`}
      </p>
      <div className="ui-pagination-controls">
        {onPageSizeChange ? (
          <span className="ui-pagination-size">
            <label htmlFor={sizeId} className="ui-field-label">
              Itens por página
            </label>
            <Select
              id={sizeId}
              className="w-auto"
              value={pageSize}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
            >
              {PAGE_SIZE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </Select>
          </span>
        ) : null}
        {totalPages > 1 ? (
          <form onSubmit={goTo} className="ui-pagination-goto" noValidate>
            <span className="ui-pagination-goto-row">
              <label htmlFor={goToId} className="ui-field-label">
                Ir para página
              </label>
              <Input
                id={goToId}
                type="number"
                inputMode="numeric"
                min={1}
                max={maxPage}
                className="w-24"
                value={target}
                aria-invalid={goToError ? true : undefined}
                aria-describedby={goToError ? `${goToId}-error` : undefined}
                onChange={(e) => setTarget(e.target.value)}
              />
              <Button type="submit" variant="outline" size="sm">
                Ir
              </Button>
            </span>
            {goToError ? (
              <span id={`${goToId}-error`} role="alert" className="ui-pagination-error">
                {goToError}
              </span>
            ) : null}
          </form>
        ) : null}
        <div className="ui-pagination-nav">
          <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
            <Icon name="chevron-left" className="h-4 w-4" />
            Anterior
          </Button>
          <Button variant="outline" size="sm" disabled={page >= last} onClick={() => onPageChange(page + 1)}>
            Próxima
            <Icon name="chevron-right" className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </nav>
  )
}
