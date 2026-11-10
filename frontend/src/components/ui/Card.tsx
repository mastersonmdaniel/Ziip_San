import type { ReactNode } from 'react'
import { cn } from '@utils/cn'

/**
 * Card / CardContent — API derivada do uso entregue (todas as frentes):
 *   <Card><CardContent className="pt-6">...</CardContent></Card>
 * Nenhuma pagina entregue usa CardHeader/CardTitle: esses nomes so
 * existem no stub do harness de provas (provas-de-telas), que nao e
 * produto — ficam FORA desta entrega [SEM FONTE]; se forem precisos,
 * entram por pendencia P-TEMA-06.
 */
export function Card({ children, className }: { children?: ReactNode; className?: string }) {
  return <div className={cn('ui-card', className)}>{children}</div>
}

export function CardContent({ children, className }: { children?: ReactNode; className?: string }) {
  return <div className={cn('ui-card-content', className)}>{children}</div>
}
