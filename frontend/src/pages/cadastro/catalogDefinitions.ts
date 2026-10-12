import type { CrudMeta, FieldMeta } from '@components/registry/crudMeta'
import { catalogSource, crudClient } from '@services/registry/resources'

/**
 * S6: METADADOS dos catálogos (colunas, tipos, flags). NENHUM item de catálogo é embutido no
 * código: os valores vêm da API (a semente só com dado oficial é responsabilidade do backend).
 *
 * Campos completos (relatório 07, T11): categorias, subcategorias, perfis de imóvel e situações
 * de água e esgoto. Os demais são catálogos SIMPLES (nome + ativo) por SUPOSIÇÃO, a confirmar
 * com o backend; quando um deles ganhar campos próprios, basta declará-los aqui.
 */

const active: FieldMeta = { kind: 'boolean', name: 'active', label: 'Ativo', column: true, defaultValue: true }
const name: FieldMeta = { kind: 'text', name: 'name', label: 'Descrição', required: true, maxLength: 80, column: true }

interface CatalogDefinition {
  /** Segmento da URL e do recurso REST: /api/registry/{resource}. */
  resource: string
  title: string
  singular: string
  description: string
  fields: FieldMeta[]
}

const flag = (fieldName: string, label: string, hint?: string): FieldMeta => ({ kind: 'boolean', name: fieldName, label, hint, column: true })

export const CATALOGS: CatalogDefinition[] = [
  {
    resource: 'categories',
    title: 'Categorias',
    singular: 'categoria',
    description: 'Categorias de uso do imóvel (residencial, comercial etc.).',
    fields: [
      name,
      { kind: 'text', name: 'abbreviation', label: 'Abreviação', required: true, maxLength: 5, column: true },
      { kind: 'integer', name: 'minimumConsumption', label: 'Consumo mínimo (m³)', required: true, min: 0, column: true },
      {
        kind: 'integer',
        name: 'economiesSoftLimit',
        label: 'Limite de economias com confirmação',
        min: 1,
        hint: 'Acima deste número o cadastro do imóvel pede confirmação.',
      },
      { kind: 'boolean', name: 'requiresSpecialPermission', label: 'Exige permissão especial', hint: 'Ex.: categoria de órgão público.' },
      active,
    ],
  },
  {
    resource: 'subcategories',
    title: 'Subcategorias',
    singular: 'subcategoria',
    description: 'Subcategorias de cada categoria.',
    fields: [
      { kind: 'lookup', name: 'categoryId', refName: 'category', label: 'Categoria', required: true, source: catalogSource('categories'), column: true },
      name,
      { kind: 'text', name: 'abbreviation', label: 'Abreviação', maxLength: 20 },
      { kind: 'integer', name: 'inspectionFactor', label: 'Fator de fiscalização', required: true, min: 0 },
      { kind: 'boolean', name: 'seasonalSupply', label: 'Abastecimento sazonal' },
      active,
    ],
  },
  {
    resource: 'property-profiles',
    title: 'Perfis do imóvel',
    singular: 'perfil do imóvel',
    description: 'Perfis com indicadores que alteram regras de geração e retificação.',
    fields: [
      name,
      flag('automaticGeneration', 'Geração automática'),
      flag('blockRectification', 'Bloquear retificação'),
      flag('largeConsumer', 'Grande consumidor'),
      active,
    ],
  },
  {
    resource: 'water-statuses',
    title: 'Situações de ligação de água',
    singular: 'situação de água',
    description: 'Situações da ligação de água e seus indicadores (fatura, rede, ligação, abastecimento).',
    fields: [
      name,
      { kind: 'text', name: 'abbreviation', label: 'Abreviação', required: true, maxLength: 5, column: true },
      { kind: 'integer', name: 'minimumBillingConsumption', label: 'Consumo mínimo de faturamento (m³)', required: true, min: 0 },
      flag('billable', 'Fatura'),
      flag('hasNetwork', 'Existe rede'),
      flag('hasConnection', 'Existe ligação'),
      flag('supplies', 'Abastece'),
      active,
    ],
  },
  {
    resource: 'sewer-statuses',
    title: 'Situações de ligação de esgoto',
    singular: 'situação de esgoto',
    description: 'Situações da ligação de esgoto e seus indicadores (fatura, rede, ligação).',
    fields: [
      name,
      { kind: 'text', name: 'abbreviation', label: 'Abreviação', required: true, maxLength: 5, column: true },
      { kind: 'decimal', name: 'minimumBillingVolume', label: 'Volume mínimo de faturamento (m³)', required: true, min: 0 },
      flag('billable', 'Fatura'),
      flag('hasNetwork', 'Existe rede'),
      flag('hasConnection', 'Existe ligação'),
      active,
    ],
  },
  {
    resource: 'customer-types',
    title: 'Tipos de cliente',
    singular: 'tipo de cliente',
    description: 'Tipos de cliente, com a esfera de poder.',
    fields: [
      name,
      {
        kind: 'select',
        name: 'powerSphere',
        label: 'Esfera de poder',
        column: true,
        options: [
          { value: 'MUNICIPAL', label: 'Municipal' },
          { value: 'STATE', label: 'Estadual' },
          { value: 'FEDERAL', label: 'Federal' },
          { value: 'PRIVATE', label: 'Privada' },
        ],
      },
      active,
    ],
  },
  { resource: 'activity-branches', title: 'Ramos de atividade', singular: 'ramo de atividade', description: 'Ramos de atividade de clientes e imóveis.', fields: [name, active] },
  { resource: 'bill-delivery-modes', title: 'Modos de envio da conta', singular: 'modo de envio', description: 'Como a conta é entregue (imóvel, responsável, e-mail...).', fields: [name, active] },
  { resource: 'sewer-discharges', title: 'Esgotamento', singular: 'tipo de esgotamento', description: 'Destino do esgoto do imóvel.', fields: [name, active] },
  { resource: 'water-sources', title: 'Fontes de abastecimento', singular: 'fonte de abastecimento', description: 'Origem da água do imóvel.', fields: [name, active] },
  { resource: 'well-types', title: 'Tipos de poço', singular: 'tipo de poço', description: 'Tipos de poço, quando a fonte é poço.', fields: [name, active] },
  { resource: 'pavements', title: 'Pavimentos', singular: 'pavimento', description: 'Pavimento de rua e de calçada.', fields: [name, active] },
]

export function catalogMeta(def: CatalogDefinition): CrudMeta {
  return {
    key: def.resource,
    title: def.title,
    singular: def.singular,
    description: def.description,
    defaultSort: 'name,asc',
    client: crudClient(def.resource),
    fields: def.fields,
  }
}

export function findCatalog(resource: string | undefined): CatalogDefinition | undefined {
  return CATALOGS.find((c) => c.resource === resource)
}
