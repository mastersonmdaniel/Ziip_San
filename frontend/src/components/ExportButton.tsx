import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@components/ui/Button'
import { Icon } from '@components/ui/Icon'
import { useCan } from '@hooks/usePermissions'
import { downloadBlob, EXPORT_PERMISSION, requestExport, type ExportResource } from '@services/export'
import { errorMessage } from '@services/registry/errors'

/**
 * P1.12: botão único de exportação de listas. Sem `registry:export`, não
 * aparece (o servidor continua recusando sem a permissão). Acima do limite
 * síncrono, o servidor enfileira e o botão aponta para "Arquivos gerados".
 * O arquivo tem a mesma máscara da tela: ele é montado pelo servidor a
 * partir dos resumos mascarados, e esta tela nunca envia documento algum.
 */
export function ExportButton({
  resource,
  filters,
  label = 'Exportar CSV',
}: {
  resource: ExportResource
  filters: Record<string, unknown>
  label?: string
}) {
  const canExport = useCan(EXPORT_PERMISSION)
  const [busy, setBusy] = useState(false)
  const [queued, setQueued] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!canExport) return null

  const onClick = async (): Promise<void> => {
    setBusy(true)
    setError(null)
    setQueued(false)
    try {
      const result = await requestExport(resource, filters)
      if (result.kind === 'queued') {
        setQueued(true)
      } else if (!downloadBlob(result.blob, result.fileName)) {
        setError('Não foi possível baixar o arquivo neste navegador.')
      }
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <span className="inline-flex flex-col items-start gap-1">
      <Button variant="outline" onClick={() => void onClick()} disabled={busy}>
        <Icon name="download" className="h-4 w-4" />
        {busy ? 'Exportando...' : label}
      </Button>
      {queued ? (
        <span role="status" className="text-xs text-muted-foreground">
          Lista grande: a exportação foi enfileirada. Acompanhe em{' '}
          <Link to="/arquivos-gerados" className="font-medium text-primary underline underline-offset-4">
            Arquivos gerados
          </Link>
          .
        </span>
      ) : null}
      {error ? (
        <span role="alert" className="text-xs text-destructive">
          {error}
        </span>
      ) : null}
    </span>
  )
}
