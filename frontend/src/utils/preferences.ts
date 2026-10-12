/**
 * Preferências de interface (hoje só o tema) em localStorage. Nada de token, tenant ou dado
 * pessoal passa por aqui: a sessão é do oidc-client-ts, em sessionStorage.
 */
export function readPreference(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export function writePreference(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // armazenamento indisponível (modo privativo): a preferência vale só até recarregar
  }
}
