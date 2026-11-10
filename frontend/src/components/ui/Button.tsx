import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { Icon } from '@components/ui/Icon'
import { cn } from '@utils/cn'

/**
 * Button — API copiada do espelho da ED-08
 * (ed-08/espelho/frontend/src/components/ui/Button.tsx), que e ficheiro
 * entregue: variantes primary/secondary/outline/ghost/destructive,
 * tamanhos default/sm/lg/icon, `loading` mostra indicador, desabilita o
 * botao e marca aria-busy; type por omissao "button".
 *
 * Uso confirmado nas paginas entregues: variant primary/ghost/outline/
 * destructive (P2.28, ED-08, P3.25), size sm (ED-08), loading (ED-08
 * CatalogCrud), type submit/button (varias).
 *
 * Diferenca deliberada face ao espelho: as classes visuais sao as do tema
 * compartilhado (styles/theme.css, tokens do campo.css) em vez de classes
 * Tailwind soltas, porque a configuracao Tailwind nunca foi entregue
 * (pendencia P-TEMA-04). A API (props) e identica.
 */
export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'destructive'
export type ButtonSize = 'default' | 'sm' | 'lg' | 'icon'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  /** Mostra indicador e desabilita o botão; o texto continua lido pelo leitor de tela. */
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant = 'primary', size = 'default', loading = false, disabled, type = 'button', children, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn('ui-btn', `ui-btn--${variant}`, size !== 'default' ? `ui-btn--${size}` : '', className)}
      {...props}
    >
      {loading ? <Icon name="loader-2" className="h-4 w-4 animate-spin" /> : null}
      {children}
    </button>
  )
})
