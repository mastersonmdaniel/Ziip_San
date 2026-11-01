/**
 * Configuração de runtime do frontend (P0.5).
 *
 * O problema: valores VITE_* são fixados no BUILD (import.meta.env). Trocar de ambiente
 * (dev → homologação → produção) exigia rebuild. A solução: o deploy serve um
 * `config.json` ao lado do index.html (frontend/public/config.json, copiado para a raiz
 * do dist pelo Vite) ou injeta `window.__ZIIPSAN_CONFIG__` antes do bundle, e o app lê
 * essa configuração ANTES de renderizar (main.tsx aguarda loadRuntimeConfig()).
 *
 * Precedência: config de runtime (janela ou /config.json) > VITE_* de build > padrão.
 * Sem config de runtime, o comportamento é exatamente o anterior (fallbacks VITE_*).
 * Só campos string são aceitos; qualquer outro formato é ignorado (não derruba o app).
 * Nenhum segredo passa por aqui: são só URLs e IDs públicos de client (o SPA não tem segredo).
 */
export interface RuntimeConfig {
  apiBaseUrl?: string
  oidcAuthority?: string
  oidcClientId?: string
}

declare global {
  interface Window {
    __ZIIPSAN_CONFIG__?: RuntimeConfig
  }
}

let loaded: RuntimeConfig | null = null
let loadPromise: Promise<RuntimeConfig> | null = null

function sanitize(value: unknown): RuntimeConfig {
  if (typeof value !== 'object' || value === null) return {}
  const record = value as Record<string, unknown>
  const pick = (key: string): string | undefined =>
    typeof record[key] === 'string' && (record[key] as string).length > 0
      ? (record[key] as string)
      : undefined
  return {
    apiBaseUrl: pick('apiBaseUrl'),
    oidcAuthority: pick('oidcAuthority'),
    oidcClientId: pick('oidcClientId'),
  }
}

/** Config já carregada (janela ou /config.json); vazio se loadRuntimeConfig() ainda não rodou. */
export function getRuntimeConfig(): RuntimeConfig {
  if (loaded) return loaded
  if (typeof window !== 'undefined' && window.__ZIIPSAN_CONFIG__) {
    return sanitize(window.__ZIIPSAN_CONFIG__)
  }
  return {}
}

/**
 * Carrega a config de runtime, uma única vez. Se a janela já tem __ZIIPSAN_CONFIG__
 * (deploy que injeta o script antes do bundle), usa-a sem buscar /config.json. Falha de
 * rede, 404 ou JSON inválido devolvem {} (fallbacks VITE_*), nunca uma exceção.
 */
export function loadRuntimeConfig(fetchFn: typeof fetch = fetch): Promise<RuntimeConfig> {
  if (loadPromise) return loadPromise
  loadPromise = (async () => {
    if (typeof window !== 'undefined' && window.__ZIIPSAN_CONFIG__) {
      loaded = sanitize(window.__ZIIPSAN_CONFIG__)
      return loaded
    }
    try {
      const response = await fetchFn('/config.json', { cache: 'no-store' })
      if (!response.ok) return {}
      loaded = sanitize(await response.json())
      return loaded
    } catch {
      return {}
    }
  })()
  return loadPromise
}

/** Base da API efetiva: runtime > VITE_API_BASE_URL > /api/v1. */
export function resolveApiBaseUrl(): string {
  return getRuntimeConfig().apiBaseUrl ?? import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
}

/** Emissor OIDC efetivo: runtime > VITE_OIDC_AUTHORITY > realm local. */
export function resolveOidcAuthority(): string {
  return (
    getRuntimeConfig().oidcAuthority ??
    import.meta.env.VITE_OIDC_AUTHORITY ??
    'http://localhost:8080/realms/ziipsan'
  )
}

/** Client OIDC efetivo: runtime > VITE_OIDC_CLIENT_ID > ziipsan-web. */
export function resolveOidcClientId(): string {
  return getRuntimeConfig().oidcClientId ?? import.meta.env.VITE_OIDC_CLIENT_ID ?? 'ziipsan-web'
}
