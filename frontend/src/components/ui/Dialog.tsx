import { useEffect, useId, useRef, type ReactNode } from 'react'
import { cn } from '@utils/cn'

/**
 * Dialog — API derivada do uso entregue (ED-08 CatalogCrud):
 *   <Dialog open onClose={...} title="..." description="...">...</Dialog>
 *   <Dialog open onClose={...} title="..." description="..."
 *           footer={<>...botoes...</>}>...</Dialog>
 * `open` e booleano (uso: <Dialog open ...>); sem `open`, nao renderiza.
 *
 * Comportamento (padrao de acessibilidade do projeto, ED-39/a11y.ts:
 * rotulos completos; e pratica corrente de dialogo modal):
 * - role="dialog" aria-modal="true", titulo/descricao ligados por
 *   aria-labelledby/aria-describedby;
 * - Escape chama onClose;
 * - BOTAO PROPRIO DE FECHAR (correccao R2-F8, Rodada 8 da auditoria):
 *   quando `onClose` e dado, o painel renderiza no cabecalho um botao
 *   <button type="button" class="ui-dialog-close" aria-label="Fechar">
 *   que chama onClose — e esse botao que a regra de foco
 *   `.ui-dialog-close:focus-visible` de styles/theme.css sempre previu
 *   (antes o selector estava morto: nenhum elemento usava a classe).
 *   Escolha pelo desenho existente: a biblioteca ja tinha o selector e
 *   um dialogo modal sem fecho visivel ficava dependente de Escape ou
 *   de botoes do conteudo/footer — um Dialog sem footer nao tinha
 *   nenhum fecho visivel. Sem `onClose`, nao ha botao (nada para
 *   chamar) e o fecho fica a cargo de quem controla `open`.
 *   O glifo e o caracter "×" em texto — nenhum nome de icone "x" esta
 *   evidenciado nos usos entregues, e a API do Icon nao e alargada
 *   aqui (ver P-TEMA-05). O rotulo acessivel e "Fechar".
 * - ao abrir, o foco vai para o elemento com [data-autofocus] (uso
 *   entregue: Textarea data-autofocus no DeleteDialog da ED-08), senao
 *   para o primeiro elemento focavel, senao para o painel;
 * - ao fechar, o foco volta ao elemento que estava focado antes.
 * Clique no fundo NAO fecha: nenhuma pagina entregue o usa e fechar por
 * clique fora perderia o motivo obrigatorio da exclusao (ED-08).
 *
 * ARMADILHA DE FOCO COMPLETA — P-TEMA-08 (frente telas-onda2, pacote
 * temas-componentes): enquanto aberto, Tab/Shift+Tab circulam DENTRO do
 * painel e o foco nao escapa:
 * - a lista de focaveis e consultada no momento de cada Tab (nunca
 *   cacheada), por isso conteudo dinamico (elementos que entram/saem
 *   com o dialogo aberto) entra/sai da circulacao sem reabrir;
 * - Tab no ultimo focavel (ou com o foco fora da lista, p.ex. no
 *   proprio painel) salta para o primeiro; Shift+Tab no primeiro salta
 *   para o ultimo — sempre com preventDefault, para o browser nao
 *   mover o foco para fora no mesmo gesto;
 * - com ZERO elementos focaveis, Tab/Shift+Tab ficam no painel
 *   (tabIndex=-1): o foco nao escapa nem fica perdido;
 * - um foco que entre de fora por via programatica/clique (focusin
 *   fora do painel) e puxado de volta para o primeiro focavel, ou
 *   para o painel se nao houver focaveis.
 * Focavel = button/[href]/input/select/textarea/[tabindex] nao
 * desactivado, sem [hidden], com tabIndex >= 0 e visivel por CSS
 * (R41-4-31: `display`/`visibility` no elemento ou nos ascendentes
 * ate ao painel; `offsetParent`/rects nao servem — ver `isVisible`).
 * O painel, com tabIndex=-1, e o refugio — nunca entra na lista.
 * Elementos desactivados ficam fora: nao recebem foco no browser.
 * O efeito corre uma vez por abertura ([open]): o `onClose` corrente
 * e lido por ref, para uma nova identidade de `onClose` a cada render
 * do pai nao re-disparar o foco inicial a meio da interaccao (a
 * versao anterior dependia de [open, onClose] e re-focava o
 * [data-autofocus] sempre que o pai re-renderizava com um onClose
 * novo — com a armadilha, isso partiria a circulacao).
 */
export interface DialogProps {
  open?: boolean
  onClose?: () => void
  title?: ReactNode
  description?: ReactNode
  footer?: ReactNode
  children?: ReactNode
  className?: string
}

const FOCUSABLE_SELECTOR =
  'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'

// Visivel por CSS: `display: none` / `visibility: hidden|collapse` no
// elemento ou em qualquer ascendente ate ao painel (inclusive) esconde o
// elemento — a visibilidade e herdada e `display: none` no ascendente
// esconde os descendentes (R41-4-31).
// Criterio pelo menor impacto: estilo computado, SEM rects nem
// `offsetParent` — o jsdom (suite vitest) nao faz layout (rects vazios,
// `offsetParent` sempre null), por isso um criterio por rects excluiria
// TODOS os focaveis nos testes; e um botao visivel de rect vazio (p.ex.
// so com icone) nao pode ser excluido no browser real.
function isVisible(el: HTMLElement, panel: HTMLElement): boolean {
  let node: HTMLElement | null = el
  while (node) {
    const style = getComputedStyle(node)
    if (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse') return false
    if (node === panel) return true
    node = node.parentElement
  }
  return true
}

function focusableIn(panel: HTMLElement): HTMLElement[] {
  const nodes = panel.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)
  return Array.from(nodes).filter((el) => {
    if (el.hasAttribute('disabled')) return false
    if (el.hasAttribute('hidden')) return false
    if (el.getAttribute('aria-hidden') === 'true') return false
    if (el.tabIndex < 0) return false
    if (!isVisible(el, panel)) return false
    return true
  })
}

export function Dialog({ open = false, onClose, title, description, footer, children, className }: DialogProps) {
  const uid = useId().replace(/:/g, '')
  const titleId = `dlg-title-${uid}`
  const descId = `dlg-desc-${uid}`
  const panelRef = useRef<HTMLDivElement>(null)
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  useEffect(() => {
    if (!open) return
    const previouslyFocused = document.activeElement as HTMLElement | null
    const panel = panelRef.current
    if (panel) {
      const target =
        panel.querySelector<HTMLElement>('[data-autofocus]') ??
        focusableIn(panel)[0] ??
        panel
      target.focus()
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        if (onCloseRef.current) onCloseRef.current()
        return
      }
      if (event.key !== 'Tab') return
      const current = panelRef.current
      if (!current) return
      const items = focusableIn(current)
      if (items.length === 0) {
        // Zero focaveis: o foco fica no contentor e nao escapa.
        event.preventDefault()
        current.focus()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      const active = document.activeElement as HTMLElement | null
      const insideList = active ? items.includes(active) : false
      if (event.shiftKey) {
        if (!insideList || active === first) {
          event.preventDefault()
          last.focus()
        }
      } else {
        if (!insideList || active === last) {
          event.preventDefault()
          first.focus()
        }
      }
    }
    function onFocusIn(event: FocusEvent) {
      const current = panelRef.current
      if (!current) return
      const target = event.target as Node | null
      if (target && current.contains(target)) return
      // Foco entrou de fora (programatico ou clique fora): puxa de volta.
      const items = focusableIn(current)
      ;(items[0] ?? current).focus()
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('focusin', onFocusIn)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('focusin', onFocusIn)
      previouslyFocused?.focus?.()
    }
  }, [open])

  if (!open) return null

  return (
    <div className="ui-dialog-overlay">
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-describedby={description ? descId : undefined}
        tabIndex={-1}
        className={cn('ui-dialog-panel', className)}
      >
        {title || onClose ? (
          <div className="ui-dialog-header">
            {title ? (
              <h2 id={titleId} className="ui-dialog-title">
                {title}
              </h2>
            ) : (
              <span />
            )}
            {onClose ? (
              <button type="button" className="ui-dialog-close" aria-label="Fechar" onClick={onClose}>
                <span aria-hidden="true">×</span>
              </button>
            ) : null}
          </div>
        ) : null}
        {description ? (
          <p id={descId} className="ui-dialog-description">
            {description}
          </p>
        ) : null}
        {children}
        {footer ? <div className="ui-dialog-footer">{footer}</div> : null}
      </div>
    </div>
  )
}
