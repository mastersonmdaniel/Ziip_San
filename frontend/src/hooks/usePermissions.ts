import { useAuthOptional } from '@hooks/useAuth'
import type { AuthUser } from '@services/api'

/**
 * P1.12: ações exibidas por permissão (`recurso:ação`, catálogo da P0.13).
 *
 * A exibição é progressiva, não é a segurança: o servidor continua negando
 * (403) qualquer ação sem a permissão, e o log de acesso registra a negação.
 * Por isso a regra de exibição é fail-open quando a informação falta:
 * - fora do shell do app (testes de tela) ou sem usuário, não se esconde nada;
 * - com usuário mas sem o campo `permissions` (backend anterior à P1.12, que
 *   devolvia só `roles` em GET /api/v1/me), não se esconde nada;
 * - com `permissions` presente (backend da P1.12 em diante), a ação só aparece
 *   para quem tem a permissão exata.
 */
export function hasPermission(user: AuthUser | null | undefined, permission: string): boolean {
  if (!user) return true
  if (!user.permissions) return true
  return user.permissions.includes(permission)
}

export function useCan(permission: string): boolean {
  const auth = useAuthOptional()
  return hasPermission(auth?.user ?? null, permission)
}
