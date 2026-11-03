/**
 * Versão do cliente (P0.39). A versão NÃO vem do package.json (sentinela "0.0.0") nem é
 * escrita à mão: o build do Vite a injeta via define (__ZIIPSAN_VERSION__) a partir da
 * tag git (ou de ZIIPSAN_VERSION), com commit e data DO COMMIT — nunca o relógio da
 * máquina, para dois builds do mesmo commit darem a mesma versão.
 *
 * O SPA antigo mostra aviso de recarga quando o servidor anuncia um
 * Ziip-Min-Client-Version maior que a versão deste build (ver VersionBanner e o
 * interceptor de resposta em api.ts). Builds sem define injetado ("dev", só em testes)
 * nunca avisam: sem versão real, não há com o que comparar.
 */

export const VERSION_OUTDATED_EVENT = 'ziipsan:version-outdated'

export interface VersionOutdatedDetail {
  serverVersion: string
  minClientVersion: string
}

export interface BuildInfo {
  version: string
  commit: string
  buildDate: string
}

/** Versão deste build (define do Vite) ou "dev" fora do build (testes, dev sem define). */
export function getClientVersion(): string {
  try {
    // typeof em constante inexistente em runtime não lança (retorna "undefined").
    if (typeof __ZIIPSAN_VERSION__ !== 'undefined' && __ZIIPSAN_VERSION__) {
      return __ZIIPSAN_VERSION__
    }
  } catch {
    // queda para dev
  }
  return 'dev'
}

interface ParsedVersion {
  parts: number[]
  prerelease: boolean
}

function parseSemver(version: string): ParsedVersion | null {
  const match = /^(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-(.+))?$/.exec(version.trim())
  if (!match) return null
  return {
    parts: [Number(match[1]), Number(match[2] ?? 0), Number(match[3] ?? 0)],
    prerelease: Boolean(match[4]),
  }
}

/** -1 se a < b, 0 se iguais, 1 se a > b. Pré-release é mais antiga que a release. */
export function compareSemver(a: string, b: string): number {
  const pa = parseSemver(a)
  const pb = parseSemver(b)
  if (!pa || !pb) return 0
  for (let i = 0; i < 3; i++) {
    if (pa.parts[i] !== pb.parts[i]) return pa.parts[i] < pb.parts[i] ? -1 : 1
  }
  if (pa.prerelease === pb.prerelease) return 0
  return pa.prerelease ? -1 : 1
}

/** Verdadeiro quando o cliente está abaixo do mínimo exigido pelo servidor. */
export function isClientOutdated(clientVersion: string, minClientVersion: string): boolean {
  if (!clientVersion || !minClientVersion) return false
  if (clientVersion === 'dev' || minClientVersion === 'dev') return false
  return compareSemver(clientVersion, minClientVersion) < 0
}

export interface GitInfo {
  tagVersion?: string
  commit?: string
  date?: string
}

/**
 * Resolve a informação de build a partir do ambiente e (opcionalmente) do git.
 * Precedência da versão: ZIIPSAN_VERSION > tag git > "0.0.0-SNAPSHOT" — o MESMO padrão
 * do Maven sem -Drevision, para backend e frontend dizerem a mesma versão em dev.
 * Pura (sem I/O), para ser testável e para o vite.config reutilizar exatamente a
 * mesma regra do app.
 */
export function resolveBuildInfo(
  env: Record<string, string | undefined>,
  git?: GitInfo,
): BuildInfo {
  return {
    version: env.ZIIPSAN_VERSION ?? git?.tagVersion ?? '0.0.0-SNAPSHOT',
    commit: env.ZIIPSAN_COMMIT ?? git?.commit ?? 'unknown',
    buildDate: env.ZIIPSAN_BUILD_DATE ?? git?.date ?? 'unknown',
  }
}
