import { Icon, type IconName } from './Icon'
import { ICON_LABELS } from '../../i18n/iconLabels'
import { ICON_SECTIONS, ICON_SET_SIZES } from './iconSets'

/**
 * P1.29 (ICONES-E-SIMBOLOGIA §6) — folha de contato do conjunto inicial:
 * todos os icones do §4.1 e do §4.2, em 16, 20 e 24 px, com nome do
 * registro e rotulo acessivel em pt-BR.
 *
 * A folha segue o tema vigente (claro/escuro, Azul Agua/Verde Agua):
 * todo icone usa currentColor e tokens semanticos, entao trocar o tema
 * ou a paleta na interface repinta a folha sem nova imagem — a prova de
 * contraste por combinacao (2 paletas x 2 modos) esta em
 * iconContrast.test.ts, e a renderizacao em todos os tamanhos esta em
 * iconContactSheet.test.tsx. Pagina de desenvolvimento em /dev/icones
 * (so em build de desenvolvimento; em producao redireciona para /login).
 * Simbolos de mapa (§4.3) nao entram aqui: sao da P1.30.
 */
export function IconContactSheet() {
  return (
    <div className="space-y-8">
      {ICON_SECTIONS.map((section) => (
        <section key={section.title} aria-label={section.title}>
          <h2 className="mb-3 text-lg font-semibold text-foreground">{section.title}</h2>
          <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {Object.entries(section.icons).map(([item, name]) => (
              <li key={name} className="rounded-lg border bg-card p-3">
                <p className="text-sm font-medium text-foreground">{item}</p>
                <p className="mb-2 text-xs text-muted-foreground">{name}</p>
                <div className="flex items-end gap-4 text-foreground">
                  {ICON_SET_SIZES.map((size) => (
                    <span key={size} className="flex flex-col items-center gap-1">
                      <Icon name={name as IconName} size={size} label={ICON_LABELS[name as IconName]} />
                      <span className="text-xs text-muted-foreground">{size}px</span>
                    </span>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}
