import { useCallback, useEffect, useRef, useState } from 'react'
import { Icon } from '@components/ui/Icon'
import { Button } from '@components/ui/Button'
import { ApiError, toApiError, type ConfirmableWarning } from '@services/registry/errors'

interface ConfirmWarningProps {
  warnings: ConfirmableWarning[]
  onConfirm: () => void
  onCancel: () => void
  busy?: boolean
  confirmLabel?: string
  cancelLabel?: string
}

/**
 * Aviso CONFIRMÁVEL (diferente de erro bloqueante): mostra o que o servidor avisou e só
 * segue se o usuário escolher "Confirmar e continuar". O foco vai para o aviso ao aparecer.
 */
export function ConfirmWarning({
  warnings,
  onConfirm,
  onCancel,
  busy = false,
  confirmLabel = 'Confirmar e continuar',
  cancelLabel = 'Voltar e corrigir',
}: ConfirmWarningProps) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    ref.current?.focus()
  }, [])

  return (
    <div
      ref={ref}
      tabIndex={-1}
      role="alertdialog"
      aria-labelledby="confirm-warning-title"
      aria-describedby="confirm-warning-list"
      className="space-y-3 rounded-lg border-l-4 border-warning bg-warning/10 p-4 text-sm text-foreground focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <p id="confirm-warning-title" className="flex items-center gap-2 font-semibold text-warning">
        <Icon name="alert-triangle" className="h-5 w-5" />
        {warnings.length === 1 ? 'Aviso: confirme para continuar' : 'Avisos: confirme para continuar'}
      </p>
      <ul id="confirm-warning-list" className="list-disc space-y-1 pl-5">
        {warnings.map((w) => (
          <li key={w.confirmToken}>{w.message}</li>
        ))}
      </ul>
      <div className="flex flex-wrap gap-2">
        <Button onClick={onConfirm} loading={busy}>
          {confirmLabel}
        </Button>
        <Button variant="outline" onClick={onCancel} disabled={busy}>
          {cancelLabel}
        </Button>
      </div>
    </div>
  )
}

interface PendingWarning<TArgs> {
  args: TArgs
  warnings: ConfirmableWarning[]
  tokens: string[]
}

/**
 * Fluxo de aviso confirmável: tenta enviar; se o servidor responder só com warnings[], guarda
 * os argumentos e espera a confirmação. Ao confirmar, REENVIA com os confirmTokens aceitos.
 * Qualquer outro erro vai para `onError`.
 */
export function useConfirmable<TArgs>(
  submit: (args: TArgs, confirmTokens: string[]) => Promise<void>,
  onError: (error: ApiError) => void,
): {
  run: (args: TArgs) => Promise<boolean>
  confirm: () => Promise<boolean>
  cancel: () => void
  warnings: ConfirmableWarning[] | null
  busy: boolean
} {
  const [pending, setPending] = useState<PendingWarning<TArgs> | null>(null)
  const [busy, setBusy] = useState(false)

  const attempt = useCallback(
    async (args: TArgs, tokens: string[]): Promise<boolean> => {
      setBusy(true)
      try {
        await submit(args, tokens)
        setPending(null)
        return true
      } catch (e) {
        const error = toApiError(e)
        if (error.kind === 'warning') {
          setPending({ args, warnings: error.warnings, tokens })
        } else {
          setPending(null)
          onError(error)
        }
        return false
      } finally {
        setBusy(false)
      }
    },
    [submit, onError],
  )

  const run = useCallback((args: TArgs) => attempt(args, []), [attempt])

  const confirm = useCallback(async (): Promise<boolean> => {
    if (!pending) return false
    const tokens = [...pending.tokens, ...pending.warnings.map((w) => w.confirmToken)]
    return attempt(pending.args, tokens)
  }, [pending, attempt])

  const cancel = useCallback(() => setPending(null), [])

  return { run, confirm, cancel, warnings: pending?.warnings ?? null, busy }
}
