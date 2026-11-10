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
 * Armadilha de foco completa (focus trap): [A DEFINIR] — P-TEMA-08.
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

export function Dialog({ open = false, onClose, title, description, footer, children, className }: DialogProps) {
  const uid = useId().replace(/:/g, '')
  const titleId = `dlg-title-${uid}`
  const descId = `dlg-desc-${uid}`
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const previouslyFocused = document.activeElement as HTMLElement | null
    const panel = panelRef.current
    if (panel) {
      const target =
        panel.querySelector<HTMLElement>('[data-autofocus]') ??
        panel.querySelector<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])') ??
        panel
      target.focus()
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape' && onClose) onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      previouslyFocused?.focus?.()
    }
  }, [open, onClose])

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
