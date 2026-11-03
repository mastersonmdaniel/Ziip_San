import { api } from '@services/api'
import { toApiError } from '@services/registry/errors'
import type { ListParams } from '@services/registry/types'

/**
 * P1.12: regra ÚNICA de exportação de listas.
 *
 * - Permissão `*:export` por recurso (`registry:export` para clientes e imóveis):
 *   o botão só aparece para quem a tem (ExportButton), e o servidor recusa
 *   (403) sem ela — a exibição nunca é a segurança.
 * - Máscara pelo teto do usuário (ADR-0008): o arquivo é montado pelo servidor
 *   a partir dos mesmos resumos mascarados da lista (`documentMasked`); o
 *   frontend nunca envia nem recebe documento em claro para exportar — o corpo
 *   da requisição leva só os filtros da lista.
 * - Registro: cada exportação (síncrona ou enfileirada, e cada negação) vai
 *   para `personal_data_access_log` no servidor, com o recurso e a quantidade.
 * - Limite: até o limite síncrono do servidor, o arquivo volta na resposta;
 *   acima dele, a resposta é 202 e o arquivo fica em "Arquivos gerados".
 * - O que NÃO se exporta aqui: busca global, atalhos, visões salvas e densidade
 *   são da P1.33 e não entram nesta frente.
 */

export type ExportResource = 'customers' | 'consumer-units'

export const EXPORT_PERMISSION = 'registry:export'

export interface GeneratedFileSummary {
  id: string
  fileName: string
  resource: string
  status: 'QUEUED' | 'READY' | 'FAILED'
  rowCount: number | null
  createdAt: string
  readyAt: string | null
}

export interface ExportQueued {
  kind: 'queued'
  generatedFileId: string
}

export interface ExportFile {
  kind: 'file'
  blob: Blob
  fileName: string
}

export type ExportResult = ExportQueued | ExportFile

/** Filtros da lista, sem paginação: a exportação cobre o filtro inteiro, não a página. */
export function toExportFilters(params: ListParams): Record<string, unknown> {
  const { page: _page, size: _size, sort: _sort, ...filters } = params
  return filters as Record<string, unknown>
}

function fileNameFromDisposition(header: string | null, fallback: string): string {
  if (!header) return fallback
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/.exec(header)
  return match?.[1] ? decodeURIComponent(match[1]) : fallback
}

export function defaultExportFileName(resource: ExportResource): string {
  const date = new Date().toISOString().slice(0, 10)
  return resource === 'customers' ? `clientes-${date}.csv` : `imoveis-${date}.csv`
}

/**
 * Pede a exportação. 200 + CSV volta como arquivo; 202 volta como enfileirada.
 * O corpo leva só `{resource, filters}` — nenhum documento, nenhum dado pessoal.
 */
export async function requestExport(resource: ExportResource, filters: Record<string, unknown>): Promise<ExportResult> {
  let response
  try {
    response = await api.request({
      method: 'POST',
      url: '/registry/exports',
      data: { resource, filters },
      responseType: 'blob',
    })
  } catch (error) {
    throw toApiError(error)
  }
  if (response.status === 202) {
    const text = await (response.data as Blob).text()
    const parsed = JSON.parse(text) as { generatedFileId?: string }
    if (!parsed.generatedFileId) throw new Error('Resposta de exportação enfileirada sem identificador.')
    return { kind: 'queued', generatedFileId: parsed.generatedFileId }
  }
  const fileName = fileNameFromDisposition(
    (response.headers as Record<string, string> | undefined)?.['content-disposition'] ?? null,
    defaultExportFileName(resource),
  )
  return { kind: 'file', blob: response.data as Blob, fileName }
}

/** Baixa o blob como arquivo. Devolve falso quando o navegador não permite criar o link. */
export function downloadBlob(blob: Blob, fileName: string): boolean {
  try {
    const url = URL.createObjectURL(blob)
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

export async function listGeneratedFiles(): Promise<GeneratedFileSummary[]> {
  const response = await api.request<GeneratedFileSummary[]>({ method: 'GET', url: '/registry/generated-files' })
  return response.data
}

export async function downloadGeneratedFile(id: string, fileName: string): Promise<boolean> {
  const response = await api.request<Blob>({
    method: 'GET',
    url: `/registry/generated-files/${id}/download`,
    responseType: 'blob',
  })
  return downloadBlob(response.data, fileName)
}
