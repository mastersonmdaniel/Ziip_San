import { createContext, useContext, type HTMLAttributes, type TableHTMLAttributes, type TdHTMLAttributes, type ThHTMLAttributes } from 'react'
import { cn } from '@utils/cn'

/**
 * Densidade da tabela (P1.33): `comfortable` é o espaçamento atual (padrão, sem
 * mudança visual em nenhuma lista existente); `compact` reduz a altura das
 * linhas. A lista passa `density`; sem ela, vale confortável.
 */
export type TableDensityValue = 'comfortable' | 'compact'

const DensityContext = createContext<TableDensityValue>('comfortable')

function useDensityValue(): TableDensityValue {
  return useContext(DensityContext)
}

/**
 * `caption` é obrigatório: leitores de tela anunciam o propósito da tabela.
 * P1.12: com `mobileCards`, a tabela vira cartões no celular (abaixo de 640 px):
 * cada linha vira um bloco e cada célula mostra seu rótulo (prop `label` do
 * TableCell) antes do valor — sem rolagem horizontal para ler a lista. Sem
 * `mobileCards`, vale a rolagem horizontal de antes.
 */
export function Table({
  className,
  caption,
  mobileCards,
  density,
  children,
  ...props
}: TableHTMLAttributes<HTMLTableElement> & { caption: string; mobileCards?: boolean; density?: TableDensityValue }) {
  return (
    <div className="relative w-full overflow-x-auto">
      <DensityContext.Provider value={density ?? 'comfortable'}>
        <table
          className={cn('w-full caption-bottom text-sm', className)}
          data-mobile-cards={mobileCards ? '' : undefined}
          data-density={density ?? 'comfortable'}
          {...props}
        >
          <caption className="sr-only">{caption}</caption>
          {children}
        </table>
      </DensityContext.Provider>
    </div>
  )
}

export function TableHeader({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={cn('[&_tr]:border-b', className)} {...props} />
}

export function TableBody({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={cn('[&_tr:last-child]:border-0', className)} {...props} />
}

export function TableRow({ className, ...props }: HTMLAttributes<HTMLTableRowElement>) {
  return <tr className={cn('border-b transition-colors hover:bg-muted/50', className)} {...props} />
}

export function TableHead({ className, scope = 'col', ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  const density = useDensityValue()
  return (
    <th
      scope={scope}
      className={cn(density === 'compact' ? 'h-8 px-2' : 'h-12 px-4', 'text-left align-middle font-medium text-muted-foreground', className)}
      {...props}
    />
  )
}

export function TableCell({
  className,
  label,
  ...props
}: TdHTMLAttributes<HTMLTableCellElement> & { label?: string }) {
  const density = useDensityValue()
  return <td className={cn(density === 'compact' ? 'p-2' : 'p-4', 'align-middle', className)} data-label={label} {...props} />
}
