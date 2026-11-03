import { useEffect, useRef, type ReactNode } from 'react'
import { Icon } from '@components/ui/Icon'
import { Button } from '@components/ui/Button'
import { cn } from '@utils/cn'

export interface WizardStep {
  id: string
  label: string
}

interface WizardProps {
  steps: WizardStep[]
  /** Etapa atual (base 0). */
  current: number
  /** Etapas até esta (inclusive) já foram validadas e podem ser reabertas. */
  maxReached: number
  onGoTo: (index: number) => void
  onBack: () => void
  onNext: () => void
  onSaveDraft: () => void
  busy?: boolean
  savingDraft?: boolean
  /** Texto do botão final (ex.: "Gravar imóvel"). */
  finishLabel: string
  /** Mensagem de estado do rascunho (ex.: "Rascunho salvo às 10:32"). */
  draftStatus?: string | null
  /** Banner de erro/aviso acima do conteúdo. */
  notices?: ReactNode
  children: ReactNode
}

/**
 * Esqueleto de assistente: indicador de etapas (lista ordenada com aria-current), conteúdo,
 * e botões Voltar / Salvar rascunho / Próximo. O RASCUNHO vive no servidor (a página chama
 * POST/PUT .../drafts); aqui só há a interface. Ao trocar de etapa o foco vai para o título.
 */
export function Wizard({
  steps,
  current,
  maxReached,
  onGoTo,
  onBack,
  onNext,
  onSaveDraft,
  busy = false,
  savingDraft = false,
  finishLabel,
  draftStatus,
  notices,
  children,
}: WizardProps) {
  const headingRef = useRef<HTMLHeadingElement>(null)
  const first = useRef(true)

  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    headingRef.current?.focus()
  }, [current])

  const isLast = current === steps.length - 1
  const step = steps[current]

  return (
    <div className="space-y-6">
      <nav aria-label="Etapas do assistente">
        <ol className="flex flex-wrap gap-2">
          {steps.map((s, index) => {
            const done = index < current || index <= maxReached - 1
            const reachable = index <= maxReached && index !== current
            return (
              <li key={s.id}>
                <button
                  type="button"
                  disabled={!reachable || busy}
                  onClick={() => onGoTo(index)}
                  aria-current={index === current ? 'step' : undefined}
                  className={cn(
                    'flex items-center gap-2 rounded-lg border px-3 py-2 text-sm font-medium',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                    index === current ? 'border-primary bg-primary text-primary-foreground' : 'bg-background',
                    reachable && 'hover:bg-accent',
                    !reachable && index !== current && 'opacity-70',
                  )}
                >
                  <span
                    className={cn(
                      'flex h-6 w-6 items-center justify-center rounded-full border text-xs',
                      index === current ? 'border-primary-foreground' : 'border-current',
                    )}
                    aria-hidden="true"
                  >
                    {done && index !== current ? <Icon name="check" className="h-3.5 w-3.5" /> : index + 1}
                  </span>
                  <span>
                    <span className="sr-only">Etapa {index + 1} de {steps.length}: </span>
                    {s.label}
                    {done && index !== current ? <span className="sr-only"> (concluída)</span> : null}
                  </span>
                </button>
              </li>
            )
          })}
        </ol>
      </nav>

      <section aria-labelledby="wizard-step-title" className="space-y-4">
        <h2 id="wizard-step-title" ref={headingRef} tabIndex={-1} className="text-xl font-semibold focus:outline-none">
          Etapa {current + 1} de {steps.length}: {step?.label}
        </h2>
        {notices}
        {children}
      </section>

      <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-4">
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" onClick={onBack} disabled={current === 0 || busy}>
            Voltar
          </Button>
          <Button variant="secondary" onClick={onSaveDraft} loading={savingDraft} disabled={busy}>
            Salvar rascunho
          </Button>
          <p className="text-sm text-muted-foreground" role="status" aria-live="polite">
            {draftStatus ?? ''}
          </p>
        </div>
        <Button onClick={onNext} loading={busy}>
          {isLast ? finishLabel : 'Próximo'}
        </Button>
      </div>
    </div>
  )
}
