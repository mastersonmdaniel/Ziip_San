import type { HTMLAttributes, TableHTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from 'react'
import { cn } from '@utils/cn'

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
  children,
  ...props
}: TableHTMLAttributes<HTMLTableElement> & { caption: string; mobileCards?: boolean }) {
  return (
    <div className="relative w-full overflow-x-auto">
      <table
        className={cn('w-full caption-bottom text-sm', className)}
        data-mobile-cards={mobileCards ? '' : undefined}
        {...props}
      >
        <caption className="sr-only">{caption}</caption>
        {children}
      </table>
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
  return (
    <th
      scope={scope}
      className={cn('h-12 px-4 text-left align-middle font-medium text-muted-foreground', className)}
      {...props}
    />
  )
}

export function TableCell({
  className,
  label,
  ...props
}: TdHTMLAttributes<HTMLTableCellElement> & { label?: string }) {
  return <td className={cn('p-4 align-middle', className)} data-label={label} {...props} />
}
