import type { CrudMeta, FieldMeta } from '@components/registry/crudMeta'
import { blocks, commercialSectors, crudClient, localities, readingRoutes } from '@services/registry/resources'

/**
 * S5: níveis da hierarquia territorial. Só entram campos cujo domínio já está no contrato do
 * relatório 07 e NÃO dependem de catálogos que ainda não existem (município normalizado,
 * porte, classe, grupo de faturamento, DMA, empresas): esses ficam para quando o backend os expuser.
 * Regras de unicidade ("código único por pai") e de rede (S5) são validadas pelo servidor.
 */

const networkOptions = [
  { value: 'NONE', label: 'Sem rede' },
  { value: 'PARTIAL', label: 'Rede parcial' },
  { value: 'FULL', label: 'Com rede' },
]

const localityFields: FieldMeta[] = [
  { kind: 'integer', name: 'code', label: 'Código', required: true, min: 1, max: 999, column: true, hint: 'De 1 a 999.' },
  { kind: 'text', name: 'name', label: 'Nome', required: true, maxLength: 60, column: true },
  { kind: 'text', name: 'costCenter', label: 'Centro de custo', maxLength: 30 },
  { kind: 'boolean', name: 'headquarters', label: 'Sede', hint: 'Só uma localidade pode ser a sede.', column: true },
  { kind: 'boolean', name: 'computerized', label: 'Informatizada' },
  { kind: 'boolean', name: 'blocked', label: 'Bloqueada para inclusão de imóveis', column: true },
]

const sectorFields: FieldMeta[] = [
  { kind: 'lookup', name: 'localityId', refName: 'locality', label: 'Localidade', required: true, source: localities, column: true },
  { kind: 'integer', name: 'code', label: 'Código do setor', required: true, min: 1, max: 999, column: true, hint: 'De 1 a 999; único dentro da localidade.' },
  { kind: 'text', name: 'name', label: 'Nome', required: true, maxLength: 60, column: true },
  { kind: 'boolean', name: 'alternative', label: 'Setor alternativo', hint: 'Setor alternativo ativo não aceita imóvel novo.', column: true },
  { kind: 'boolean', name: 'blocked', label: 'Bloqueado para inclusão de imóveis' },
]

const blockFields: FieldMeta[] = [
  { kind: 'lookup', name: 'sectorId', refName: 'sector', label: 'Setor comercial', required: true, source: commercialSectors, column: true },
  { kind: 'integer', name: 'code', label: 'Número da quadra', required: true, min: 1, max: 999, column: true, hint: 'De 1 a 999; único dentro do setor.' },
  { kind: 'lookup', name: 'readingRouteId', refName: 'readingRoute', label: 'Rota de leitura', source: readingRoutes, column: true },
  { kind: 'select', name: 'waterNetwork', label: 'Rede de água', required: true, options: networkOptions, column: true },
  { kind: 'select', name: 'sewerNetwork', label: 'Rede de esgoto', required: true, options: networkOptions, column: true },
  { kind: 'text', name: 'censusSector', label: 'Setor censitário (IBGE)', maxLength: 20 },
  { kind: 'boolean', name: 'autoIncrementLot', label: 'Incrementar lote automaticamente' },
  { kind: 'boolean', name: 'blocked', label: 'Bloqueada para inclusão de imóveis' },
]

const routeFields: FieldMeta[] = [
  { kind: 'lookup', name: 'localityId', refName: 'locality', label: 'Localidade', required: true, source: localities, column: true },
  {
    kind: 'lookup',
    name: 'sectorId',
    refName: 'sector',
    label: 'Setor comercial',
    required: true,
    source: commercialSectors,
    dependsOn: { field: 'localityId', param: 'localityId' },
    column: true,
  },
  { kind: 'integer', name: 'code', label: 'Código da rota', required: true, min: 1, max: 9999, column: true, hint: 'De 1 a 9999.' },
  { kind: 'text', name: 'name', label: 'Nome', maxLength: 60 },
  { kind: 'integer', name: 'maxUnits', label: 'Limite de imóveis na rota', required: true, min: 1, column: true },
  { kind: 'boolean', name: 'active', label: 'Rota ativa', column: true, defaultValue: true },
]

export interface TerritoryLevel {
  /** Segmento da URL: /cadastro/territorio/:nivel */
  slug: string
  tabLabel: string
  meta: CrudMeta
}

export const TERRITORY_LEVELS: TerritoryLevel[] = [
  {
    slug: 'localidades',
    tabLabel: 'Localidades',
    meta: {
      key: 'localities',
      title: 'Localidades',
      singular: 'localidade',
      defaultSort: 'code,asc',
      client: crudClient(localities.path),
      fields: localityFields,
    },
  },
  {
    slug: 'setores',
    tabLabel: 'Setores comerciais',
    meta: {
      key: 'commercial-sectors',
      title: 'Setores comerciais',
      singular: 'setor comercial',
      defaultSort: 'code,asc',
      client: crudClient(commercialSectors.path),
      fields: sectorFields,
    },
  },
  {
    slug: 'quadras',
    tabLabel: 'Quadras',
    meta: {
      key: 'blocks',
      title: 'Quadras',
      singular: 'quadra',
      defaultSort: 'code,asc',
      client: crudClient(blocks.path),
      fields: blockFields,
    },
  },
  {
    slug: 'rotas',
    tabLabel: 'Rotas de leitura',
    meta: {
      key: 'reading-routes',
      title: 'Rotas de leitura',
      singular: 'rota de leitura',
      defaultSort: 'code,asc',
      client: crudClient(readingRoutes.path),
      fields: routeFields,
    },
  },
]
