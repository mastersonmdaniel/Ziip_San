import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * Loader de runtime (P0.5). O módulo guarda estado (config carregada + promise), então
 * cada teste reimporta o módulo limpo com vi.resetModules() — sem isso, um teste
 * contaminaria o seguinte com a config do anterior.
 */
async function freshModule() {
  vi.resetModules()
  return import('./runtimeConfig')
}

function setWindowConfig(config: Record<string, unknown> | undefined): void {
  const w = window as unknown as Record<string, unknown>
  if (config === undefined) delete w.__ZIIPSAN_CONFIG__
  else w.__ZIIPSAN_CONFIG__ = config
}

beforeEach(() => {
  vi.unstubAllGlobals()
  setWindowConfig(undefined)
  vi.resetModules()
})

describe('runtimeConfig', () => {
  it('sem config de runtime, resolve os fallbacks (VITE ausente no teste -> padroes)', async () => {
    const { resolveApiBaseUrl, resolveOidcAuthority, resolveOidcClientId } = await freshModule()
    expect(resolveApiBaseUrl()).toBe('/api/v1')
    expect(resolveOidcAuthority()).toBe('http://localhost:8080/realms/ziipsan')
    expect(resolveOidcClientId()).toBe('ziipsan-web')
  })

  it('window.__ZIIPSAN_CONFIG__ vence os fallbacks, sem buscar /config.json', async () => {
    setWindowConfig({
      apiBaseUrl: 'https://api.homologacao.invalid/api/v1',
      oidcAuthority: 'https://id.homologacao.invalid/realms/ziipsan',
      oidcClientId: 'ziipsan-web-hom',
    })
    const fetchMock = vi.fn()
    const { loadRuntimeConfig, resolveApiBaseUrl, resolveOidcAuthority, resolveOidcClientId } =
      await freshModule()
    await loadRuntimeConfig(fetchMock as unknown as typeof fetch)
    expect(fetchMock).not.toHaveBeenCalled()
    expect(resolveApiBaseUrl()).toBe('https://api.homologacao.invalid/api/v1')
    expect(resolveOidcAuthority()).toBe('https://id.homologacao.invalid/realms/ziipsan')
    expect(resolveOidcClientId()).toBe('ziipsan-web-hom')
  })

  it('loadRuntimeConfig busca /config.json e aplica os valores', async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true,
      json: async () => ({
        apiBaseUrl: 'https://api.producao.invalid/api/v1',
        oidcAuthority: 'https://id.producao.invalid/realms/ziipsan',
        oidcClientId: 'ziipsan-web-prod',
      }),
    }))
    const { loadRuntimeConfig, resolveApiBaseUrl, resolveOidcClientId } = await freshModule()
    await loadRuntimeConfig(fetchMock as unknown as typeof fetch)
    expect(fetchMock).toHaveBeenCalledWith('/config.json', { cache: 'no-store' })
    expect(resolveApiBaseUrl()).toBe('https://api.producao.invalid/api/v1')
    expect(resolveOidcClientId()).toBe('ziipsan-web-prod')
  })

  it('falha de rede, 404 ou JSON invalido nao derrubam o app: voltam os fallbacks', async () => {
    const failing = vi.fn(async () => {
      throw new Error('rede fora')
    })
    const notFound = vi.fn(async () => ({ ok: false, json: async () => ({}) }))
    const invalid = vi.fn(async () => ({
      ok: true,
      json: async () => ({ apiBaseUrl: 42, oidcAuthority: null, oidcClientId: ['x'] }),
    }))
    for (const mock of [failing, notFound, invalid]) {
      const { loadRuntimeConfig, resolveApiBaseUrl } = await freshModule()
      await loadRuntimeConfig(mock as unknown as typeof fetch)
      expect(resolveApiBaseUrl()).toBe('/api/v1')
    }
  })

  it('carrega uma unica vez: a segunda chamada reaproveita a promise', async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true,
      json: async () => ({ apiBaseUrl: 'https://api.unica.invalid/api/v1' }),
    }))
    const { loadRuntimeConfig, resolveApiBaseUrl } = await freshModule()
    const [first, second] = await Promise.all([
      loadRuntimeConfig(fetchMock as unknown as typeof fetch),
      loadRuntimeConfig(fetchMock as unknown as typeof fetch),
    ])
    expect(first).toEqual(second)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(resolveApiBaseUrl()).toBe('https://api.unica.invalid/api/v1')
  })
})
