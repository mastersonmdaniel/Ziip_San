import { Icon } from '@components/ui/Icon'
import { Button } from './Button'

interface PaginationProps {
  /** Página atual, base 0. */
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalElements, onPageChange }: PaginationProps) {
  const last = Math.max(totalPages - 1, 0)
  return (
    <nav aria-label="Paginação" className="flex flex-wrap items-center justify-between gap-3 pt-4">
      <p className="text-sm text-muted-foreground" aria-live="polite">
        {totalElements === 0
          ? 'Nenhum registro'
          : `Página ${page + 1} de ${Math.max(totalPages, 1)} · ${totalElements} ${totalElements === 1 ? 'registro' : 'registros'}`}
      </p>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
          <Icon name="chevron-left" className="h-4 w-4" />
          Anterior
        </Button>
        <Button variant="outline" size="sm" disabled={page >= last} onClick={() => onPageChange(page + 1)}>
          Próxima
          <Icon name="chevron-right" className="h-4 w-4" />
        </Button>
      </div>
    </nav>
  )
}
