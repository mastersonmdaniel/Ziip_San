import { NavLink, Navigate, useParams } from 'react-router-dom'
import { CatalogCrud } from '@components/registry/CatalogCrud'
import { PageHeader } from '@components/ui/PageHeader'
import { cn } from '@utils/cn'
import { TERRITORY_LEVELS } from './territoryDefinitions'

/** S5: hierarquia territorial (localidades, setores, quadras, rotas). Rota: /cadastro/territorio/:nivel */
export function Territory() {
  const { nivel } = useParams()
  const level = TERRITORY_LEVELS.find((l) => l.slug === nivel)
  const first = TERRITORY_LEVELS[0]
  if (!level) {
    return first ? <Navigate to={`/cadastro/territorio/${first.slug}`} replace /> : null
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Hierarquia territorial"
        description="Localidade, setor comercial, quadra e rota de leitura. A inscrição de cada imóvel nasce desta estrutura."
      />
      <nav aria-label="Níveis da hierarquia">
        <ul className="flex flex-wrap gap-2">
          {TERRITORY_LEVELS.map((l) => (
            <li key={l.slug}>
              <NavLink
                to={`/cadastro/territorio/${l.slug}`}
                className={({ isActive }) =>
                  cn(
                    'inline-flex rounded-lg border px-4 py-2 text-sm font-medium',
                    isActive ? 'border-primary bg-primary text-primary-foreground' : 'bg-background hover:bg-accent',
                  )
                }
              >
                {l.tabLabel}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <section aria-labelledby="territorio-nivel">
        <h2 id="territorio-nivel" className="mb-4 text-lg font-semibold">
          {level.meta.title}
        </h2>
        <CatalogCrud key={level.meta.key} meta={level.meta} />
      </section>
    </div>
  )
}
