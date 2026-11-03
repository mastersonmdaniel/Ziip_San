import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { downloadGeneratedFile, listGeneratedFiles } from '@services/export'
import { errorMessage, shouldOfferRetry } from '@services/registry/errors'
import { formatDate } from '@utils/labels'

const STATUS_LABEL: Record<string, string> = {
  QUEUED: 'Na fila',
  READY: 'Pronto',
  FAILED: 'Falhou',
}

const RESOURCE_LABEL: Record<string, string> = {
  customers: 'Clientes',
  'consumer-units': 'Imóveis',
}

/**
 * P1.12: ponto único de entrega de arquivos gerados. Aqui ficam as exportações
 * de lista acima do limite síncrono (geradas pelo worker da P1.9) e, no futuro,
 * os relatórios agendados da P3.22 — esta frente constrói só o item de entrega,
 * não a P3.22. Arquivos com falha não têm download: o usuário exporta de novo.
 */
export function GeneratedFiles() {
  const files = useQuery({ queryKey: ['generated-files'], queryFn: () => listGeneratedFiles() })
  const [downloading, setDownloading] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const onDownload = async (id: string, fileName: string): Promise<void> => {
    setDownloading(id)
    setError(null)
    try {
      if (!(await downloadGeneratedFile(id, fileName))) {
        setError('Não foi possível baixar o arquivo neste navegador.')
      }
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setDownloading(null)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Arquivos gerados"
        description="Exportações de lista acima do limite síncrono e relatórios agendados. Os arquivos trazem a mesma máscara da tela."
      />
      {error ? (
        <Alert tone="error" title="Não foi possível baixar o arquivo">
          {error}
        </Alert>
      ) : null}
      <Card>
        <CardContent className="pt-6">
          <div aria-live="polite">
            {files.isLoading ? (
              <Spinner label="Carregando arquivos gerados..." />
            ) : files.isError ? (
              <Alert tone="error" title="Não foi possível carregar os arquivos gerados">
                <p>{errorMessage(files.error)}</p>
                {shouldOfferRetry(files.error) ? (
                  <Button size="sm" variant="outline" className="mt-2" onClick={() => void files.refetch()}>
                    Tentar novamente
                  </Button>
                ) : null}
              </Alert>
            ) : files.data && files.data.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">
                Nenhum arquivo gerado ainda. Exportações grandes aparecem aqui quando ficam prontas.
              </p>
            ) : null}
          </div>
          {files.data && files.data.length > 0 ? (
            <Table caption="Arquivos gerados" mobileCards>
              <TableHeader>
                <TableRow>
                  <TableHead>Arquivo</TableHead>
                  <TableHead>Lista</TableHead>
                  <TableHead>Situação</TableHead>
                  <TableHead className="text-right">Linhas</TableHead>
                  <TableHead>Criado em</TableHead>
                  <TableHead className="text-right">Ação</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {files.data.map((file) => (
                  <TableRow key={file.id}>
                    <TableCell label="Arquivo" className="font-mono text-xs">
                      {file.fileName}
                    </TableCell>
                    <TableCell label="Lista">{RESOURCE_LABEL[file.resource] ?? file.resource}</TableCell>
                    <TableCell label="Situação">{STATUS_LABEL[file.status] ?? file.status}</TableCell>
                    <TableCell label="Linhas" className="text-right">
                      {file.rowCount === null ? '—' : file.rowCount}
                    </TableCell>
                    <TableCell label="Criado em">{formatDate(file.createdAt)}</TableCell>
                    <TableCell label="Ação" className="text-right">
                      {file.status === 'READY' ? (
                        <Button size="sm" variant="outline" disabled={downloading === file.id} onClick={() => void onDownload(file.id, file.fileName)}>
                          {downloading === file.id ? 'Baixando...' : 'Baixar'}
                        </Button>
                      ) : (
                        '—'
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : null}
        </CardContent>
      </Card>
    </div>
  )
}
