import { createContext, createElement, useContext, type ReactNode } from 'react'

/**
 * useCan / usePermissions — API derivada do uso entregue:
 *   const canWrite = useCan('registry:write')            (ED-08 CustomerList)
 *   const canReview = useCan('gis:change-set-review')    (P2.22 ChangeSetReview)
 *   const canManage = useCan('gis:style-manage')         (P3.25 GisStyles)
 * O chamador trata o resultado como booleano sincrono.
 *
 * [A DEFINIR] A FONTE das permissoes (sessao/perfil do servidor) nunca
 * foi entregue em pacote algum: nenhuma pagina entregue monta provider
 * nem mostra de onde a lista vem. Esta implementacao expoe o ponto de
 * injecao — PermissionsProvider com a lista de permissoes efectivas —
 * e, SEM provider, nega tudo (fail-closed): mostrar uma accao sem
 * permissao confirmada seria fingir a regra que as proprias paginas
 * recusam fingir ("a tela nao finge a regra", P2.28/P2.33).
 * Quem alimenta o provider na casca da aplicacao: pendencia P-TEMA-01.
 */
export interface PermissionsContextValue {
  permissions: readonly string[]
  can: (permission: string) => boolean
}

const PermissionsContext = createContext<PermissionsContextValue>({
  permissions: [],
  can: () => false,
})

export function PermissionsProvider({
  permissions,
  children,
}: {
  permissions: readonly string[]
  children: ReactNode
}) {
  const value: PermissionsContextValue = {
    permissions,
    can: (permission: string) => permissions.includes(permission),
  }
  return createElement(PermissionsContext.Provider, { value }, children)
}

export function usePermissions(): PermissionsContextValue {
  return useContext(PermissionsContext)
}

export function useCan(permission: string): boolean {
  return useContext(PermissionsContext).can(permission)
}
