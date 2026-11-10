import {
  Children,
  createContext,
  isValidElement,
  useContext,
  type ReactNode,
  type TableHTMLAttributes,
  type TdHTMLAttributes,
  type ThHTMLAttributes,
} from 'react'
import { cn } from '@utils/cn'

/**
 * Table — API derivada do uso entregue e do contrato executavel de
 * ed-08/espelho/frontend/src/components/ui/Table.test.tsx (a Table.tsx
 * em si nunca foi entregue; so o seu teste):
 * - <Table caption="..." mobileCards> -> <table data-mobile-cards="">
 *   e cada <TableCell label="Nome"> expoe data-label="Nome"
 *   (Table.test.tsx, describe 'Table mobileCards (P1.12)').
 * - density="compact" -> <table data-density="compact">, th com classe
 *   contendo "h-8", td com classe contendo "p-2"; sem density, o padrao
 *   e "comfortable" (data-density="comfortable") e o td contem "p-4"
 *   (Table.test.tsx, describe 'Table density (P1.33)').
 * - caption vira <caption> da tabela (uso: ED-08, P3.25).
 *
 * DIVERGENCIA ENTREGUE (segundo achado de consistencia deste pacote):
 * a P2.33 (DocumentosFiscais.tsx linhas 99-107) usa os nomes ao contrario
 * das outras frentes — <TableHead> como contentor do cabecalho e
 * <TableHeader> como celula de cabecalho:
 *     <Table><TableHead><TableRow><TableHeader>Documento</TableHeader>...
 * enquanto ED-08/P2.22/P2.28/P3.25/P3.39 usam <TableHeader> como
 * contentor (<thead>) e <TableHead> como celula (<th>).
 * Resolucao sem quebrar nenhuma das duas: TableHeader e TableHead sao
 * DUPLAS — se o conteudo for uma linha (TableRow), renderizam <thead>;
 * se for texto/celulas, renderizam <th>. Nenhuma pagina precisa mudar.
 */
export type TableDensity = 'comfortable' | 'compact'

const DensityContext = createContext<TableDensity>('comfortable')

function containsRow(children: ReactNode): boolean {
  return Children.toArray(children).some((c) => isValidElement(c) && c.type === TableRow)
}

function headCellClass(density: TableDensity, className?: string): string {
  return cn('ui-table-th', density === 'compact' ? 'h-8 p-2' : 'h-12 p-4', className)
}

export interface TableProps extends TableHTMLAttributes<HTMLTableElement> {
  caption?: string
  mobileCards?: boolean
  density?: TableDensity
  children?: ReactNode
}

export function Table({ caption, mobileCards, density = 'comfortable', children, className, ...props }: TableProps) {
  return (
    <DensityContext.Provider value={density}>
      <table
        {...props}
        data-mobile-cards={mobileCards ? '' : undefined}
        data-density={density}
        className={cn('ui-table', `ui-table--${density}`, className)}
      >
        {caption ? <caption>{caption}</caption> : null}
        {children}
      </table>
    </DensityContext.Provider>
  )
}

export function TableHeader({
  children,
  className,
  ...props
}: ThHTMLAttributes<HTMLTableCellElement> & { children?: ReactNode }) {
  const density = useContext(DensityContext)
  if (containsRow(children)) {
    return <thead className={cn('ui-table-header', className)}>{children}</thead>
  }
  // Modo celula (convencao da P2.33): TableHeader como <th>.
  return (
    <th scope="col" className={headCellClass(density, className)} {...props}>
      {children}
    </th>
  )
}

export function TableHead({
  children,
  className,
  ...props
}: ThHTMLAttributes<HTMLTableCellElement> & { children?: ReactNode }) {
  const density = useContext(DensityContext)
  if (containsRow(children)) {
    // Modo contentor (convencao da P2.33): TableHead como <thead>.
    return <thead className={cn('ui-table-header', className)}>{children}</thead>
  }
  return (
    <th scope="col" className={headCellClass(density, className)} {...props}>
      {children}
    </th>
  )
}

export function TableBody({ children }: { children?: ReactNode }) {
  return <tbody>{children}</tbody>
}

export function TableRow({
  children,
  className,
  ...props
}: React.HTMLAttributes<HTMLTableRowElement> & { children?: ReactNode }) {
  return (
    <tr className={cn('ui-table-row', className)} {...props}>
      {children}
    </tr>
  )
}

export interface TableCellProps extends TdHTMLAttributes<HTMLTableCellElement> {
  /** Rotulo da celula para o cartao do celular (Table.test.tsx: data-label). */
  label?: string
  children?: ReactNode
}

export function TableCell({ label, children, className, ...props }: TableCellProps) {
  const density = useContext(DensityContext)
  return (
    <td data-label={label} className={cn('ui-table-cell', density === 'compact' ? 'p-2' : 'p-4', className)} {...props}>
      {children}
    </td>
  )
}
