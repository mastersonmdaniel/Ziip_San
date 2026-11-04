import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { downloadRejectionsCsv, listImports } from '@services/imports'
import { errorMessage } from '@services/registry/errors'
import { formatDate } from '@utils/labels'

const STATUS_LABEL: Record<string, string> = {
  RECEIVED: 'Recebida',
  VALIDATING: 'Validando',
  VALIDATED: 'Validada',
  APPLYING: 'Aplicando',
  APPLIED: 'Aplicada',
  REVERSED: 'Revertida',
  FAILED: 'Falhou',
}

/**
 * P1.40: histórico de importações (menu Importações, JOR-26). Lista quem
 * enviou, quando, o arquivo, os totais aceitos/recusados e a OS de lote; o
 * relatório de recusas de cada carga baixa em CSV. O envio do arquivo e a
 * aplicação vivem no fluxo do cadastro que declara o esquema — esta tela é
 * o histórico, não o assistente de carga.
 */
export function Importacoes() {
  const jobs = useQuery({ queryKey: ['imports'], queryFn: () => listImports() })
  const [downloading, setDownloading] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const onDownload = async (id: string, fileName: string): Promise<void> => {
    setDownloading(id)
    setError(null)
    try {
      if (!(await downloadRejectionsCsv(id, fileName))) {
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
        title="Importações"
        description="Histórico das cargas por planilha: arquivo, totais aceitos e recusados e a OS de lote que aplicou a carga. As linhas recusadas ficam fora do lote e estão no relatório de recusas."
      />
      {error ? (
        <Alert tone="error" title="Não foi possível baixar o arquivo">
          {error}
        </Alert>
      ) : null}
      <Card>
        <CardContent className="pt-6">
          {jobs.isPending ? (
            <Spinner label="Carregando importações" />
          ) : jobs.isError ? (
            <Alert tone="error" title="Não foi possível carregar as importações">
              {errorMessage(jobs.error)}
            </Alert>
          ) : jobs.data.length === 0 ? (
            <p>Nenhuma importação ainda. As cargas enviadas pelos cadastros aparecem aqui.</p>
          ) : (
            <Table caption="Histórico de importações">
              <TableHead>
                <TableRow>
                  <TableHeader>Arquivo</TableHeader>
                  <TableHeader>Esquema</TableHeader>
                  <TableHeader>Enviado em</TableHeader>
                  <TableHeader>Enviado por</TableHeader>
                  <TableHeader>Situação</TableHeader>
                  <TableHeader>Aceitas</TableHeader>
                  <TableHeader>Recusadas</TableHeader>
                  <TableHeader>OS de lote</TableHeader>
                  <TableHeader>Recusas</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {jobs.data.map((job) => (
                  <TableRow key={job.id}>
                    <TableCell>{job.originalFilename}</TableCell>
                    <TableCell>{job.schemaLabel}</TableCell>
                    <TableCell>{formatDate(job.createdAt)}</TableCell>
                    <TableCell>{job.createdBy ? job.createdBy.slice(0, 8) : '—'}</TableCell>
                    <TableCell>{STATUS_LABEL[job.status] ?? job.status}</TableCell>
                    <TableCell>{job.acceptedCount}</TableCell>
                    <TableCell>{job.rejectedCount}</TableCell>
                    <TableCell>{job.batchOrderId ? job.batchOrderId.slice(0, 8) : '—'}</TableCell>
                    <TableCell>
                      {job.rejectedCount > 0 ? (
                        <Button
                          variant="ghost"
                          disabled={downloading === job.id}
                          onClick={() => onDownload(job.id, `recusas-${job.id}.csv`)}
                        >
                          Baixar
                        </Button>
                      ) : (
                        '—'
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
