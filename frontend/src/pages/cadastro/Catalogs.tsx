import { useMemo } from 'react'
import { NavLink, useParams } from 'react-router-dom'
import { CatalogCrud } from '@components/registry/CatalogCrud'
import { PageHeader } from '@components/ui/PageHeader'
import { cn } from '@utils/cn'
import { CATALOGS, catalogMeta, findCatalog } from './catalogDefinitions'

/** S6: catálogos do cadastro. Rota: /cadastro/catalogos/:tipo (tipo = recurso REST). */
export function Catalogs() {
  const { tipo } = useParams()
  const def = findCatalog(tipo)
  const meta = useMemo(() => (def ? catalogMeta(def) : null), [def])

  return (
    <div className="space-y-6">
      <PageHeader
        title="Catálogos"
        description="Tabelas de apoio do cadastro. Os valores vêm do servidor; esta tela não traz nenhum item pré-definido."
      />
      <div className="grid gap-6 lg:grid-cols-[16rem_1fr]">
        <nav aria-label="Catálogos">
          <ul className="space-y-1">
            {CATALOGS.map((c) => (
              <li key={c.resource}>
                <NavLink
                  to={`/cadastro/catalogos/${c.resource}`}
                  className={({ isActive }) =>
                    cn(
                      'block rounded-lg px-3 py-2 text-sm font-medium',
                      isActive ? 'bg-primary text-primary-foreground' : 'hover:bg-accent',
                    )
                  }
                >
                  {c.title}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <section aria-labelledby="catalogo-titulo" className="min-w-0">
          {def && meta ? (
            <>
              <h2 id="catalogo-titulo" className="text-lg font-semibold">
                {def.title}
              </h2>
              <p className="mb-4 text-sm text-muted-foreground">{def.description}</p>
              <CatalogCrud key={meta.key} meta={meta} />
            </>
          ) : (
            <p id="catalogo-titulo" className="text-sm text-muted-foreground">
              Escolha um catálogo na lista ao lado.
            </p>
          )}
        </section>
      </div>
    </div>
  )
}
