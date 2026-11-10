import type { ReactNode } from 'react'
import { cn } from '@utils/cn'

/**
 * Badge — API derivada do uso entregue:
 * - sem variant: <Badge>{status}</Badge> (P2.22, P2.28, P3.25)
 * - variant="danger" (P3.39 Inicio: vencidas; SEMAPHORE RED)
 * - variant por mapa SEMAPHORE_VARIANT: 'success' | 'warning' | 'danger' | 'default' (P3.39)
 * - variant="destructive" (ED-08 CustomerList: "Excluido")
 * - variant="info" | "secondary" (ED-08 CatalogCrud: coluna booleana)
 * - className livre (ED-08: className="ml-2")
 */
export type BadgeVariant = 'default' | 'success' | 'warning' | 'danger' | 'destructive' | 'info' | 'secondary'

export interface BadgeProps {
  children?: ReactNode
  variant?: BadgeVariant
  className?: string
}

export function Badge({ children, variant = 'default', className }: BadgeProps) {
  return <span className={cn('ui-badge', `ui-badge--${variant}`, className)}>{children}</span>
}
