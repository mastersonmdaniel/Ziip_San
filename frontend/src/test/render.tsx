import type { ReactElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, type RenderResult } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { AxiosError, AxiosHeaders, type AxiosRequestConfig } from 'axios'

export function renderWithProviders(ui: ReactElement, route = '/'): RenderResult {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}

export type FakeRoute = (config: AxiosRequestConfig) => unknown

/** Resposta HTTP de mentira no formato do axios. */
export function ok(data: unknown, status = 200) {
  return { data, status, statusText: '', headers: {}, config: {} }
}

/**
 * Cria o roteador de API falsa dos testes: casa método + caminho e devolve os dados informados.
 * Caminho sem rota cadastrada responde 404 problem+json, como o backend faria.
 */
export function fakeApi(routes: Record<string, FakeRoute | unknown>) {
  return vi.fn(async (config: AxiosRequestConfig) => {
    const key = `${(config.method ?? 'get').toUpperCase()} ${config.url ?? ''}`
    const handler = routes[key]
    if (handler === undefined) {
      throw new AxiosError('não encontrado', '404', undefined, undefined, {
        data: { title: `Sem rota de teste: ${key}` },
        status: 404,
        statusText: '',
        headers: {},
        config: { headers: new AxiosHeaders() },
      })
    }
    return ok(typeof handler === 'function' ? (handler as FakeRoute)(config) : handler)
  })
}
