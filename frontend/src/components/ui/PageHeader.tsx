import { useEffect, type ReactNode } from 'react'
import { useBrand } from '../../brand/BrandProvider'

/** Cabeçalho de página: o ÚNICO h1 da tela, com descrição e ações opcionais. Atualiza o título da aba. */
export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  const brand = useBrand()
  useEffect(() => {
    document.title = `${title} · ${brand.shortName}`
  }, [title, brand.shortName])

  return (
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        {description ? <p className="mt-1 text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
    </div>
  )
}
