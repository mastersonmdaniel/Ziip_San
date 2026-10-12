import { Alert } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'

interface ConflictAlertProps {
  /** Busca a versão atual no servidor e a adota, MANTENDO o que o usuário digitou. */
  onKeepMine: () => void
  /** Descarta o digitado e recarrega o registro do servidor. */
  onDiscardMine: () => void
  busy?: boolean
}

/** HTTP 409: o registro mudou (controle otimista por `version`). Nada digitado é perdido sem escolha. */
export function ConflictAlert({ onKeepMine, onDiscardMine, busy = false }: ConflictAlertProps) {
  return (
    <Alert tone="warning" title="O registro mudou desde que você o abriu">
      <p>
        Outra pessoa salvou alterações neste registro. O que você digitou continua na tela. Escolha como seguir:
      </p>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button size="sm" onClick={onKeepMine} loading={busy}>
          Atualizar a versão e manter o que digitei
        </Button>
        <Button size="sm" variant="outline" onClick={onDiscardMine} disabled={busy}>
          Descartar o que digitei e recarregar
        </Button>
      </div>
    </Alert>
  )
}
