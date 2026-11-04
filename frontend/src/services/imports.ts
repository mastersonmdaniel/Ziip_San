import { api } from '@services/api'

/**
 * P1.40: histórico de importações (menu Importações). O servidor já devolve
 * quem (createdBy), quando (createdAt), arquivo, totais aceitos/recusados e
 * a OS de lote (batchOrderId); o download do relatório de recusas sai da
 * rota de CSV do job. Sem upload nesta tela: o envio vive no cadastro que
 * declara o esquema (a P1.3 pluga o CSV do cadastro aqui).
 */
export interface ImportJobSummary {
  id: string
  schemaCode: string
  schemaLabel: string
  originalFilename: string
  status: string
  totalRows: number
  acceptedCount: number
  rejectedCount: number
  appliedCount: number
  batchOrderId: string | null
  reversalOrderId: string | null
  errorMessage: string | null
  createdAt: string | null
  createdBy: string | null
  duplicate: boolean
}

export async function listImports(): Promise<ImportJobSummary[]> {
  const response = await api.request<ImportJobSummary[]>({ method: 'GET', url: '/registry/imports' })
  return response.data
}

export async function downloadRejectionsCsv(id: string, fileName: string): Promise<boolean> {
  try {
    const response = await api.request<Blob>({
      method: 'GET',
      url: `/registry/imports/${id}/rejections.csv`,
      responseType: 'blob',
    })
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    return true
  } catch {
    return false
  }
}
