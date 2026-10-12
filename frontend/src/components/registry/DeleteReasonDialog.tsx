import { useState, type ReactNode } from 'react'
import { Alert } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Dialog } from '@components/ui/Dialog'
import { Field, Textarea } from '@components/ui/Field'
import { errorMessage } from '@services/registry/errors'

interface DeleteReasonDialogProps {
  title: string
  description: ReactNode
  /** Chama a API; se lançar, a mensagem é exibida e o diálogo continua aberto. */
  onConfirm: (reason: string) => Promise<void>
  onClose: () => void
}

/** Exclusão LÓGICA: exige um motivo (vai para deleted_at + motivo). Nunca exclusão física. */
export function DeleteReasonDialog({ title, description, onConfirm, onClose }: DeleteReasonDialogProps) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const tooShort = reason.trim().length < 3

  const confirm = async () => {
    setBusy(true)
    setError(null)
    try {
      await onConfirm(reason.trim())
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open
      onClose={onClose}
      title={title}
      description={description}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={busy}>
            Cancelar
          </Button>
          <Button variant="destructive" loading={busy} disabled={tooShort} onClick={() => void confirm()}>
            Confirmar exclusão
          </Button>
        </>
      }
    >
      {error ? <Alert tone="error">{error}</Alert> : null}
      <Field label="Motivo da exclusão" required hint="Mínimo de 3 caracteres. Fica registrado na auditoria.">
        {(aria) => <Textarea {...aria} data-autofocus value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} />}
      </Field>
    </Dialog>
  )
}
