/**
 * Icon — API derivada do uso entregue: <Icon name="..." className="..." />.
 *
 * Nomes evidenciados nas paginas/componentes entregues:
 * - "search", "plus" (CatalogCrud e CustomerList da ED-08; GisMap via Button nao usa)
 * - "pencil", "trash-2" (CatalogCrud da ED-08)
 * - "chevron-left", "chevron-right" (Pagination entregue da ED-08)
 * - "loader-2" (Button do espelho ED-08, estado loading)
 * - "alert-circle" (Field do espelho ED-08, icone do erro)
 *
 * A biblioteca grafica final NAO foi entregue: o package.json do espelho
 * ED-08 declara lucide-react 0.303.0, mas nenhum ficheiro de icones veio
 * em pacote algum. Esta implementacao desenha os glifos evidenciados em
 * SVG inline simples (traço, currentColor), sem dependencia, para a API
 * ficar estavel: se a decisao for adoptar lucide-react, troca-se so o
 * interior deste ficheiro. Pendencia P-TEMA-05.
 *
 * NOME DESCONHECIDO (correccao R2-F9, Rodada 8 da auditoria): o tipo
 * aceita qualquer string de proposito (para a API nao partir quando a
 * biblioteca de icones crescer), mas um nome fora de PATHS NAO degrada
 * mais em silencio. Comportamento documentado:
 * - em DESENVOLVIMENTO (NODE_ENV !== "production"), emite
 *   console.warn("[tema-front-web] Icon: nome desconhecido ...") com o
 *   nome pedido e os nomes conhecidos;
 * - em qualquer ambiente, o <svg> sai marcado data-icon-unknown="true"
 *   (detectavel em testes/inspecao) e renderiza o glifo de recurso —
 *   um circulo pequeno — em vez de partir: producao nunca quebra por um
 *   icone, mas a degradacao fica visivel a quem desenvolve.
 */
export type IconName =
  | 'search'
  | 'plus'
  | 'pencil'
  | 'trash-2'
  | 'chevron-left'
  | 'chevron-right'
  | 'loader-2'
  | 'alert-circle'
  | (string & {})

export interface IconProps {
  name: IconName
  className?: string
}

const PATHS: Record<string, JSX.Element> = {
  'chevron-left': <polyline points="14 6 8 12 14 18" />,
  'chevron-right': <polyline points="10 6 16 12 10 18" />,
  plus: (
    <>
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <line x1="21" y1="21" x2="16.5" y2="16.5" />
    </>
  ),
  pencil: (
    <>
      <path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
    </>
  ),
  'trash-2': (
    <>
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
      <path d="M10 11v6M14 11v6" />
    </>
  ),
  'loader-2': <path d="M21 12a9 9 0 1 1-6.2-8.56" />,
  'alert-circle': (
    <>
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="8" x2="12" y2="13" />
      <line x1="12" y1="16.5" x2="12.01" y2="16.5" />
    </>
  ),
}

export function Icon({ name, className }: IconProps) {
  const spinning = name === 'loader-2'
  const known = Object.prototype.hasOwnProperty.call(PATHS, name)
  if (!known) {
    // R2-F9: degradacao nao-silenciosa em desenvolvimento (ver cabecalho).
    // A guarda `typeof process` mantem o componente seguro em ambientes
    // browser sem shim de process: ai nao ha aviso, mas fica a marca
    // data-icon-unknown no SVG.
    const isProduction = typeof process !== 'undefined' && process.env?.NODE_ENV === 'production'
    if (!isProduction && typeof console !== 'undefined') {
      console.warn(
        `[tema-front-web] Icon: nome desconhecido "${name}". Nomes conhecidos: ${Object.keys(PATHS).join(', ')}.`,
      )
    }
  }
  return (
    <svg
      aria-hidden="true"
      data-icon={name}
      data-icon-unknown={known ? undefined : 'true'}
      className={['ui-icon', spinning ? 'ui-icon--spin' : '', className ?? ''].filter(Boolean).join(' ')}
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {PATHS[name] ?? <circle cx="12" cy="12" r="4" />}
    </svg>
  )
}
