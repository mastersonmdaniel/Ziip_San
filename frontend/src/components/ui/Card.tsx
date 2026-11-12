import { createElement, type ReactNode } from 'react'
import { cn } from '@utils/cn'

/**
 * Card / CardContent — API derivada do uso entregue (todas as frentes):
 *   <Card><CardContent className="pt-6">...</CardContent></Card>
 *
 * CardHeader / CardTitle — P-TEMA-06 (frente telas-onda2, pacote
 * temas-componentes): criados porque telas REAIS da main os pedem.
 * Usos provados na main (origin/main 4b58e550):
 * - pages/Templates.tsx: 6 sítios, só com `children`
 *   (<CardHeader><CardTitle>...</CardTitle></CardHeader>);
 * - pages/cadastro/CustomerDetail.tsx e CustomerForm.tsx:
 *   <CardTitle as="h2" className="..."> — a prop `as` e o `className`
 *   fazem parte do uso real, não só do stub;
 * - pages/cadastro/PropertyDetail.tsx:
 *   <CardHeader className="pb-3"> e <CardTitle as="h2" className="...">.
 * A forma base é a do stub do harness de provas
 * (provas-de-telas/harness/web-src/components/ui/Card.tsx):
 * CardHeader({children}) -> contentor; CardTitle({children}) -> <h3>.
 * API entregue (aditiva, no padrão da biblioteca — cn() + classes ui-*):
 * - CardHeader: {children, className} -> <div class="ui-card-header">;
 * - CardTitle: {children, className, as} -> cabeçalho (omissão "h3",
 *   como no stub; `as` aceita h1..h6 para o uso as="h2" das telas de
 *   cadastro, sem partir a hierarquia de títulos da página).
 * `PageHeader subtitle` NÃO entra: nenhuma tela o usa (P-TEMA-06 parcial).
 */
export function Card({ children, className }: { children?: ReactNode; className?: string }) {
  return <div className={cn('ui-card', className)}>{children}</div>
}

export function CardContent({ children, className }: { children?: ReactNode; className?: string }) {
  return <div className={cn('ui-card-content', className)}>{children}</div>
}

export function CardHeader({ children, className }: { children?: ReactNode; className?: string }) {
  return <div className={cn('ui-card-header', className)}>{children}</div>
}

export type CardTitleLevel = 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6'

export function CardTitle({
  children,
  className,
  as = 'h3',
}: {
  children?: ReactNode
  className?: string
  as?: CardTitleLevel
}) {
  return createElement(as, { className: cn('ui-card-title', className) }, children)
}
