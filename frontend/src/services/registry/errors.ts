import { isAxiosError } from 'axios'

/** Erro de campo devolvido pelo servidor. `field` usa notação de caminho: customers[0].customerId */
export interface FieldError {
  field: string
  code: string
  message: string
}

/** Aviso confirmável: reenviar a mesma requisição com `confirmToken` em `confirmTokens` aceita o aviso. */
export interface ConfirmableWarning {
  code: string
  message: string
  confirmToken: string
}

/** Corpo application/problem+json (RFC 9457) estendido com errors[] e warnings[]. */
export interface ProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  errors?: FieldError[]
  warnings?: ConfirmableWarning[]
}

export type ApiErrorKind =
  | 'validation' // 400/422 com errors[]
  | 'warning' // problem+json só com warnings[]: pede confirmação
  | 'conflict' // 409 sem errors/warnings: o registro mudou (@Version)
  | 'unauthorized'
  | 'forbidden'
  | 'not-found'
  | 'network' // sem resposta (servidor fora, DNS, CORS)
  | 'timeout'
  | 'server' // 5xx
  | 'unknown'

export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status: number | null
  readonly problem: ProblemDetails | null
  readonly errors: FieldError[]
  readonly warnings: ConfirmableWarning[]

  constructor(kind: ApiErrorKind, status: number | null, problem: ProblemDetails | null, message: string) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.status = status
    this.problem = problem
    this.errors = problem?.errors ?? []
    this.warnings = problem?.warnings ?? []
  }
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null
}

/** Valida o formato mínimo de um problem+json sem confiar no servidor. */
export function parseProblem(data: unknown): ProblemDetails | null {
  if (!isRecord(data)) return null
  const problem: ProblemDetails = {}
  if (typeof data.type === 'string') problem.type = data.type
  if (typeof data.title === 'string') problem.title = data.title
  if (typeof data.status === 'number') problem.status = data.status
  if (typeof data.detail === 'string') problem.detail = data.detail
  if (Array.isArray(data.errors)) {
    problem.errors = data.errors.filter(isRecord).map((e) => ({
      field: typeof e.field === 'string' ? e.field : '',
      code: typeof e.code === 'string' ? e.code : 'INVALID',
      message: typeof e.message === 'string' ? e.message : 'Valor inválido.',
    }))
  }
  if (Array.isArray(data.warnings)) {
    problem.warnings = data.warnings
      .filter(isRecord)
      .filter((w) => typeof w.confirmToken === 'string')
      .map((w) => ({
        code: typeof w.code === 'string' ? w.code : 'WARNING',
        message: typeof w.message === 'string' ? w.message : 'Confirme para continuar.',
        confirmToken: String(w.confirmToken),
      }))
  }
  return problem
}

export function classify(status: number, problem: ProblemDetails | null): ApiErrorKind {
  const hasErrors = (problem?.errors?.length ?? 0) > 0
  const hasWarnings = (problem?.warnings?.length ?? 0) > 0
  if (hasWarnings && !hasErrors) return 'warning'
  if (hasErrors) return 'validation'
  if (status === 409) return 'conflict'
  if (status === 401) return 'unauthorized'
  if (status === 403) return 'forbidden'
  if (status === 404) return 'not-found'
  if (status === 400 || status === 422) return 'validation'
  if (status >= 500) return 'server'
  return 'unknown'
}

const DEFAULT_MESSAGES: Record<ApiErrorKind, string> = {
  validation: 'Há campos com problema. Corrija e tente de novo.',
  warning: 'Há um aviso que precisa da sua confirmação.',
  conflict: 'O registro mudou desde que você o abriu. Recarregue para ver a versão atual; o que você digitou foi mantido.',
  unauthorized: 'Sua sessão expirou. Entre novamente.',
  forbidden: 'Você não tem permissão para esta ação.',
  'not-found': 'Registro não encontrado.',
  network: 'Não foi possível falar com o servidor. Verifique a conexão e tente novamente.',
  timeout: 'O servidor demorou demais para responder. Tente novamente.',
  server: 'O servidor encontrou um erro. Tente novamente em instantes.',
  unknown: 'Ocorreu um erro inesperado.',
}

/** Converte qualquer erro lançado por axios em ApiError. Nunca inclui dados do corpo enviado. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (isAxiosError(error)) {
    if (error.response) {
      const problem = parseProblem(error.response.data)
      const kind = classify(error.response.status, problem)
      const message = kind === 'conflict' || kind === 'forbidden' || kind === 'network' ? DEFAULT_MESSAGES[kind] : problem?.detail || problem?.title || DEFAULT_MESSAGES[kind]
      return new ApiError(kind, error.response.status, problem, message)
    }
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      return new ApiError('timeout', null, null, DEFAULT_MESSAGES.timeout)
    }
    if (error.code === 'ERR_CANCELED') {
      return new ApiError('unknown', null, null, 'Requisição cancelada.')
    }
    return new ApiError('network', null, null, DEFAULT_MESSAGES.network)
  }
  return new ApiError('unknown', null, null, DEFAULT_MESSAGES.unknown)
}

/** Texto para exibir ao usuário a partir de qualquer erro. */
export function errorMessage(error: unknown): string {
  return toApiError(error).message
}

/**
 * P1.12: nova tentativa por tipo de erro. Só vale a pena tentar de novo o que
 * pode passar na próxima tentativa: falha de rede, timeout, erro 5xx e 429
 * (cota/rate limit). Erro de validação, conflito, não encontrado, sem sessão
 * ou sem permissão não muda tentando de novo — a tela mostra a mensagem e,
 * quando for o caso, o caminho (corrigir, entrar de novo, pedir permissão).
 */
export function isRetryableError(error: unknown): boolean {
  const apiError = toApiError(error)
  if (apiError.kind === 'network' || apiError.kind === 'timeout' || apiError.kind === 'server') return true
  return apiError.status === 429
}

/** O botão "Tentar novamente" só aparece para erro que pode passar na nova tentativa. */
export function shouldOfferRetry(error: unknown): boolean {
  return isRetryableError(error)
}

/** Política de retry do react-query: até 3 tentativas, só para erro retryable. */
export function queryRetry(failureCount: number, error: unknown): boolean {
  return failureCount < 3 && isRetryableError(error)
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * Executa `fn` com nova tentativa só para erro retryable (backoff simples).
 * Erro não retryable é lançado na primeira tentativa, sem espera inútil.
 */
export async function withRetry<T>(fn: () => Promise<T>, maxAttempts = 3, delayMs = 500): Promise<T> {
  let lastError: unknown
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await fn()
    } catch (error) {
      lastError = error
      if (attempt === maxAttempts || !isRetryableError(error)) throw error
      await sleep(delayMs * attempt)
    }
  }
  throw lastError
}

/** "customers[0].customerId" -> "customers.0.customerId" (caminho do react-hook-form). */
export function toFormPath(field: string): string {
  return field.replace(/\[(\d+)\]/g, '.$1')
}
