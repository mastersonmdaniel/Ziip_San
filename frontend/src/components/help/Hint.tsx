import { HELP_BUNDLE } from '../../help/helpBundle.generated'

/**
 * P1.21 — dica de campo (AJUDA-SUPORTE-SOBRE.md §3).
 *
 * <Hint id="cadastro.property.search" /> exibe a dica do campo, vinda de
 * content/help/hints via bundle gerado. Chave desconhecida não renderiza nada
 * (nunca um texto inventado nem um "?" órfão sem conteúdo).
 */
export function Hint({ id }: { id: string }) {
  const text = HELP_BUNDLE.hints[id]
  if (!text) return null
  return (
    <span
      data-hint-id={id}
      title={text}
      aria-label={text}
      className="inline-flex h-5 w-5 items-center justify-center rounded-full border text-xs text-muted-foreground"
    >
      ?
    </span>
  )
}
