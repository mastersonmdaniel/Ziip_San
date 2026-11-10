/**
 * Erros da API — superficie derivada do uso entregue:
 * - CatalogCrud (ED-08): `error.kind === 'conflict'`, `error.errors`
 *   (FieldError[] com .field/.message), `error.message`, e o tipo
 *   ApiError como tipo do erro das mutacoes.
 * - Todas as frentes: errorMessage(error) -> string para mostrar.
 * - CustomerList (ED-08): shouldOfferRetry(error) -> boolean para decidir
 *   se o botao "Tentar novamente" aparece.
 * - Servicos entregues (registry/client do espelho, gis/changeSets,
 *   gis/map, gis/thematic, search): toApiError(error) converte "qualquer
 *   falha em ApiError (problem+json tratado)".
 *
 * [A DEFINIR] O formato exacto do problem+json do servidor nunca foi
 * entregue em pacote de front. A conversao abaixo aceita, por ordem:
 * data.kind (string), data.errors[] ({field, message}), data.detail /
 * data.title como mensagem, e o status HTTP em error.response.status
 * (forma axios) ou error.status. O mapeamento status 409 -> kind
 * 'conflict' e a regra de shouldOfferRetry sao decisoes de implementacao
 * declaradas, a confirmar contra o servidor. Pendencia P-TEMA-02.
 */
export interface FieldError {
  field: string
  message: string
}

export class ApiError extends Error {
  kind: string
  errors: FieldError[]
  status: number | null

  constructor(message: string, kind = 'unknown', errors: FieldError[] = [], status: number | null = null) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.errors = errors
    this.status = status
  }
}

interface AxiosLikeError {
  message?: string
  response?: { status?: number; data?: unknown }
  status?: number
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null
}

function parseFieldErrors(data: Record<string, unknown>): FieldError[] {
  if (!Array.isArray(data.errors)) return []
  const out: FieldError[] = []
  for (const item of data.errors) {
    if (isRecord(item) && typeof item.message === 'string') {
      out.push({ field: typeof item.field === 'string' ? item.field : '', message: item.message })
    }
  }
  return out
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  const like = (error ?? {}) as AxiosLikeError
  const response = like.response
  const data = isRecord(response?.data) ? response.data : isRecord((error as { data?: unknown })?.data) ? ((error as { data: Record<string, unknown> }).data as Record<string, unknown>) : undefined
  const status = typeof response?.status === 'number' ? response.status : typeof like.status === 'number' ? like.status : null

  if (data) {
    const errors = parseFieldErrors(data)
    const message =
      (typeof data.detail === 'string' && data.detail) ||
      (typeof data.title === 'string' && data.title) ||
      (typeof data.message === 'string' && data.message) ||
      (error instanceof Error ? error.message : '') ||
      'Erro inesperado'
    const kind =
      (typeof data.kind === 'string' && data.kind) || (status === 409 ? 'conflict' : status !== null ? 'http' : 'unknown')
    return new ApiError(message, kind, errors, status)
  }
  if (error instanceof Error) {
    return new ApiError(error.message, status !== null ? 'http' : 'unknown', [], status)
  }
  return new ApiError('Erro inesperado', 'unknown', [], status)
}

export function errorMessage(error: unknown): string {
  return toApiError(error).message
}

/**
 * Vale a pena oferecer "Tentar novamente"? Decisao de implementacao
 * [A DEFINIR] (P-TEMA-02): sem status (falha de rede) ou erro 5xx/408/429
 * sao transitorios; os restantes 4xx nao se resolvem repetindo.
 */
export function shouldOfferRetry(error: unknown): boolean {
  const e = toApiError(error)
  if (e.status === null) return true
  return e.status >= 500 || e.status === 408 || e.status === 429
}
