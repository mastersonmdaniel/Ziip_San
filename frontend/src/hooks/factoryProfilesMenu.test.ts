import { describe, expect, it } from 'vitest'
import { navigation } from '../components/Layout'
import { hasPermission } from './usePermissions'
import type { AuthUser } from '@services/api'

/**
 * P1.37: menu por perfil de fábrica, sobre o menu real do Layout (P0.47 + P1.12).
 * Cada linha é um perfil com suas permissões EFETIVAS (o que GET /api/v1/me devolve):
 * auditor externo e regulador entram com lista vazia — o conjunto declarado deles
 * existe no backend, mas não concede nada até a liberação da ED-75, então o menu
 * deles é vazio. Perfis com conjunto vazio ou só registry:read aguardam o catálogo
 * do seu domínio (nenhuma permissão inventada aqui); quando o domínio catalogar,
 * esta matriz ganha as linhas novas junto com o backend.
 *
 * Itens do menu real: Imóveis, Clientes, Hierarquia territorial e Catálogos exigem
 * registry:read; Catálogo GIS exige gis:catalog-read; Arquivos gerados (ponto de
 * entrega transversal da P1.12, não item de módulo do manifesto), Sobre (P1.24) e
 * Importações (P1.40) exigem registry:read; Suporte (P1.23) exige support:read;
 * Central Ziip (P1.17) exige central:read, que nenhum perfil de fábrica tem (só
 * papéis de plataforma) — por isso ela não aparece em nenhuma linha abaixo.
 * Integração P1-C: as permissões desta matriz são os conjuntos reconciliados do
 * backend (protocolo, marca, certificações e suporte nos perfis pertinentes).
 */
const REGISTRY = ['/cadastro/imoveis', '/cadastro/clientes', '/cadastro/territorio', '/cadastro/catalogos']
const ARQUIVOS = ['/arquivos-gerados']
const GIS = ['/gis/catalogo']
const ABOUT_IMPORTS = ['/about', '/importacoes']
const SUPPORT = ['/support']
const REGISTRY_MENU = [...REGISTRY, ...ARQUIVOS, ...ABOUT_IMPORTS]
const FULL_MENU = [...REGISTRY, ...GIS, ...ARQUIVOS, ...ABOUT_IMPORTS]
const REGISTRY_SUPPORT_MENU = [...REGISTRY_MENU, ...SUPPORT]
const FULL_SUPPORT_MENU = [...FULL_MENU, ...SUPPORT]

const base: AuthUser = { id: 'u', username: 't', email: 't@x.invalid', fullName: 'T', roles: [], tenantId: 'ten' }

const MATRIX: Array<{ profile: string; permissions: string[]; visible: string[] }> = [
  { profile: 'ADMIN', permissions: ['registry:read', 'registry:write', 'user:read', 'user:write', 'gis:catalog-read', 'gis:catalog-manage', 'parameters:manage', 'brand:manage', 'support:read', 'support:write', 'support:manage'], visible: FULL_SUPPORT_MENU },
  { profile: 'MANAGER', permissions: ['registry:read', 'registry:write', 'user:read'], visible: REGISTRY_MENU },
  { profile: 'OPERATOR', permissions: ['registry:read', 'registry:write'], visible: REGISTRY_MENU },
  { profile: 'VIEWER', permissions: ['registry:read'], visible: REGISTRY_MENU },
  { profile: 'ATENDENTE', permissions: ['registry:read', 'registry:write', 'protocol:read', 'protocol:write', 'support:read', 'support:write'], visible: REGISTRY_SUPPORT_MENU },
  { profile: 'SUPERVISOR_COMERCIAL', permissions: ['registry:read', 'registry:write', 'user:read', 'protocol:read'], visible: REGISTRY_MENU },
  { profile: 'FATURISTA', permissions: ['registry:read'], visible: REGISTRY_MENU },
  { profile: 'ANALISTA_ARRECADACAO', permissions: ['registry:read'], visible: REGISTRY_MENU },
  { profile: 'LEITURISTA', permissions: ['registry:read'], visible: REGISTRY_MENU },
  { profile: 'PROGRAMADOR_OS', permissions: ['registry:read'], visible: REGISTRY_MENU },
  { profile: 'EXECUTOR_CAMPO', permissions: ['registry:read'], visible: REGISTRY_MENU },
  { profile: 'ALMOXARIFE', permissions: [], visible: [] },
  { profile: 'OPERADOR_ESTACAO', permissions: [], visible: [] },
  { profile: 'ANALISTA_PERDAS', permissions: ['registry:read'], visible: REGISTRY_MENU },
  { profile: 'LABORATORISTA', permissions: ['cert:read'], visible: [] },
  { profile: 'RESP_TEC_QUALIDADE', permissions: ['cert:read', 'cert:write', 'cert:approve', 'cert:audit'], visible: [] },
  { profile: 'EDITOR_CAD_TECNICO', permissions: ['gis:catalog-read', 'gis:catalog-manage'], visible: GIS },
  { profile: 'REVISOR_CAD_TECNICO', permissions: ['gis:catalog-read'], visible: GIS },
  { profile: 'GESTOR', permissions: ['registry:read', 'user:read', 'parameters:manage', 'support:read', 'support:write'], visible: REGISTRY_SUPPORT_MENU },
  { profile: 'AUDITOR_INTERNO', permissions: ['registry:read', 'gis:catalog-read', 'user:read', 'cert:audit'], visible: FULL_MENU },
  { profile: 'OUVIDOR', permissions: ['registry:read', 'protocol:read', 'support:read'], visible: REGISTRY_SUPPORT_MENU },
  { profile: 'ENCARREGADO_DADOS', permissions: ['registry:read', 'user:read'], visible: REGISTRY_MENU },
  { profile: 'PROCURADOR', permissions: ['registry:read'], visible: REGISTRY_MENU },
  { profile: 'CONTADOR', permissions: ['registry:read'], visible: REGISTRY_MENU },
  // Desligados até a ED-75: permissões efetivas vazias, menu vazio.
  { profile: 'AUDITOR_EXTERNO', permissions: [], visible: [] },
  { profile: 'REGULADOR', permissions: [], visible: [] },
]

describe('menu por perfil de fábrica (P1.37)', () => {
  it('cobre os 26 perfis de fábrica', () => {
    expect(MATRIX).toHaveLength(26)
    expect(new Set(MATRIX.map((row) => row.profile)).size).toBe(26)
  })

  it.each(MATRIX)('$profile vê só $visible', ({ permissions, visible }) => {
    const user: AuthUser = { ...base, permissions }
    const shown = navigation.filter((item) => hasPermission(user, item.permission)).map((item) => item.href)
    expect(shown).toEqual(visible)
  })
})
