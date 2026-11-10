import type { ReactNode } from 'react'
import { cn } from '@utils/cn'

/**
 * PageHeader — API derivada do uso entregue (todas as frentes):
 *   <PageHeader title="..." />
 *   <PageHeader title="..." description="..." />
 *   <PageHeader title="..." description="..." actions={<...>} /> (ED-08 CustomerList)
 * A prop `subtitle` so existe no stub do harness de provas — [SEM FONTE]
 * nas paginas entregues; fica fora (ver P-TEMA-06).
 */
export interface PageHeaderProps {
  title: string
  description?: string
  actions?: ReactNode
  className?: string
}

export function PageHeader({ title, description, actions, className }: PageHeaderProps) {
  return (
    <header className={cn('ui-pageheader', className)}>
      <div>
        <h1 className="ui-pageheader-title">{title}</h1>
        {description ? <p className="ui-pageheader-description">{description}</p> : null}
      </div>
      {actions ? <div className="ui-pageheader-actions">{actions}</div> : null}
    </header>
  )
}
