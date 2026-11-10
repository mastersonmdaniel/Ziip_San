import type { ReactNode } from 'react'
import { Icon } from '@components/ui/Icon'
import { cn } from '@utils/cn'

/**
 * Alert / Spinner — API derivada do uso entregue. As paginas usam DOIS
 * sistemas de nomes para o mesmo componente, e os dois sao aceites:
 * - variant: "danger" | "warning" (P3.39 Inicio/Agenda, P2.28)
 * - tone: "error" | "success" (ED-08 CatalogCrud/CustomerList)
 * - sem props: <Alert>{mensagem}</Alert> (P2.22, P2.33, P3.25) — neutro
 * - title: titulo em destaque antes do conteudo (ED-08:
 *   <Alert tone="error" title="Nao foi possivel carregar a lista">)
 * `danger` e `error` normalizam para o mesmo tipo interno ("error").
 * O tipo por omissao (sem tone nem variant) e "info" — decisao de
 * implementacao declarada: as mensagens sem tipo nas paginas sao tanto
 * erros de acao como avisos neutros, e nenhum ficheiro entregue fixa o
 * omissao. Ver pendencia P-TEMA-07.
 *
 * PRECEDENCIA tone > variant (documentada na correcção R2-F7, Rodada 8
 * da auditoria — antes a regra vivia so no codigo, em silencio):
 * quando `tone` e `variant` sao passados AO MESMO TEMPO, `tone` vence e
 * `variant` e ignorado, mesmo que os dois divirjam (ex.: tone="success"
 * + variant="danger" renderiza kind "success"). A precedencia MANTEM-SE
 * — nao e corrigida — porque nenhum ficheiro entregue passa os dois em
 * divergencia nem fixa outra regra, e `tone` e o vocabolario da frente
 * mais completa (ED-08, que tambem fixa `title`); inverter a regra
 * mudaria o comportamento de quem ja migra paginas ED-08 acrescentando
 * `variant` por engano, sem fonte que o justifique. Recomendacao a quem
 * chama: passar SO um dos dois. Valor desconhecido em qualquer dos
 * campos normaliza para "info" (ver normalizeKind).
 *
 * Spinner: <Spinner /> e <Spinner label="Carregando clientes..." />
 * (todas as frentes), com role="status".
 */
export type AlertTone = 'error' | 'success' | 'warning' | 'info'
export type AlertVariant = 'danger' | 'warning' | 'success' | 'info' | 'default'

type AlertKind = 'error' | 'success' | 'warning' | 'info'

function normalizeKind(tone?: string, variant?: string): AlertKind {
  // R2-F7: precedencia deliberada e documentada no cabecalho — `tone`
  // vence `variant` quando os dois estao presentes (`tone ?? variant`).
  const raw = tone ?? variant
  if (raw === 'error' || raw === 'danger') return 'error'
  if (raw === 'success') return 'success'
  if (raw === 'warning') return 'warning'
  return 'info'
}

export interface AlertProps {
  children?: ReactNode
  title?: string
  /** Vocabulario ED-08. Se `tone` e `variant` vierem os dois, `tone` vence (R2-F7). */
  tone?: AlertTone
  /** Vocabulario P3.39/P2.28. Ignorado quando `tone` esta presente (R2-F7). */
  variant?: AlertVariant
  className?: string
}

export function Alert({ children, title, tone, variant, className }: AlertProps) {
  const kind = normalizeKind(tone, variant)
  return (
    <div
      role={kind === 'error' || kind === 'warning' ? 'alert' : 'status'}
      data-kind={kind}
      className={cn('ui-alert', `ui-alert--${kind}`, className)}
    >
      {title ? <strong className="ui-alert-title">{title}</strong> : null}
      {children}
    </div>
  )
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div role="status" aria-label={label ?? 'Carregando...'} className="ui-spinner">
      <Icon name="loader-2" className="h-4 w-4 animate-spin" />
      <span>{label ?? 'Carregando...'}</span>
    </div>
  )
}
