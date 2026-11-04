import ptBR from './messages/pt-BR.json'

/**
 * P1.18 (PLT-25): catálogo de mensagens da interface. Textos ao usuário
 * vivem aqui (um arquivo por idioma publicado), nunca literais espalhados
 * no JSX — o lint `ziipsan/no-hardcoded-ui-text` recusa texto fixo de
 * interface em arquivos que importam este módulo (ver eslint-local).
 *
 * Só pt-BR é publicado: `SUPPORTED_LOCALES` tem um único idioma e `t()`
 * sempre lê o catálogo pt-BR. Outro idioma só entra quando a tradução
 * existir — o backend já recusa `locale` diferente de pt-BR com 422, e
 * este módulo não inventa tradução por fallback silencioso para outro
 * idioma: chave ausente devolve a própria chave, para o buraco aparecer
 * no teste em vez de virar texto em idioma errado.
 */
export const SUPPORTED_LOCALES = ['pt-BR'] as const
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]
export const DEFAULT_LOCALE: SupportedLocale = 'pt-BR'

type Messages = typeof ptBR

function lookup(key: string): string | undefined {
  let node: unknown = ptBR as Messages
  for (const part of key.split('.')) {
    if (typeof node !== 'object' || node === null || !(part in node)) return undefined
    node = (node as Record<string, unknown>)[part]
  }
  return typeof node === 'string' ? node : undefined
}

export function t(key: string): string {
  return lookup(key) ?? key
}

/** Chaves existentes no catálogo — o teste de cobertura as lista. */
export function catalogKeys(): string[] {
  const out: string[] = []
  const walk = (node: unknown, prefix: string) => {
    if (typeof node === 'string') {
      out.push(prefix)
      return
    }
    if (typeof node === 'object' && node !== null) {
      for (const [k, v] of Object.entries(node)) walk(v, prefix ? `${prefix}.${k}` : k)
    }
  }
  walk(ptBR, '')
  return out.sort()
}
