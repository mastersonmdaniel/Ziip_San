import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { isRetryableError, queryRetry, withRetry } from './errors'

function httpError(status: number): AxiosError {
  return new AxiosError('erro', String(status), undefined, undefined, {
    data: { title: 'x' },
    status,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
  })
}

describe('nova tentativa por tipo de erro (P1.12)', () => {
  it('rede, timeout e 5xx são retryable; 400/403/404/409 não são; 429 é', () => {
    expect(isRetryableError(new AxiosError('x', 'ERR_NETWORK'))).toBe(true)
    expect(isRetryableError(new AxiosError('x', 'ECONNABORTED'))).toBe(true)
    expect(isRetryableError(httpError(500))).toBe(true)
    expect(isRetryableError(httpError(429))).toBe(true)
    expect(isRetryableError(httpError(400))).toBe(false)
    expect(isRetryableError(httpError(403))).toBe(false)
    expect(isRetryableError(httpError(404))).toBe(false)
    expect(isRetryableError(httpError(409))).toBe(false)
  })

  it('queryRetry tenta até 3 vezes só para erro retryable', () => {
    expect(queryRetry(0, httpError(500))).toBe(true)
    expect(queryRetry(3, httpError(500))).toBe(false)
    expect(queryRetry(0, httpError(403))).toBe(false)
  })

  it('withRetry tenta de novo o retryable e desiste no primeiro não retryable', async () => {
    vi.useFakeTimers()
    const failing = vi.fn().mockRejectedValueOnce(httpError(503)).mockResolvedValueOnce('ok')
    const result = withRetry(failing, 3, 10)
    await vi.advanceTimersByTimeAsync(100)
    await expect(result).resolves.toBe('ok')
    expect(failing).toHaveBeenCalledTimes(2)

    const forbidden = vi.fn().mockRejectedValue(httpError(403))
    await expect(withRetry(forbidden, 3, 10)).rejects.toBeDefined()
    expect(forbidden).toHaveBeenCalledTimes(1)
    vi.useRealTimers()
  })
})
