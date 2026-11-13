import type { IconName } from './Icon'
import { CUSTOM_ICONS } from './customIcons'

/**
 * P1.29 (ICONES-E-SIMBOLOGIA §4.1 e §4.2) — fonte unica da cobertura do
 * conjunto inicial, em portugues (o item do inventario) para o nome do
 * registro (em ingles, §2.1). O teste iconSets.test.tsx confere que:
 * todo item do inventario tem icone no registro, todo icone de
 * saneamento tem prefixo `sanitation-`, todo icone proprio esta em
 * customIcons.ts e nenhum item ficou sem rotulo acessivel. Item novo do
 * inventario entra aqui primeiro; o teste quebra se o registro nao o
 * tiver — e o inverso (icone sem item) e permitido so para icones
 * genericos do nucleo (menu, chevron, eye, map-pin etc.), listados em
 * GENERIC_CORE_ICONS.
 *
 * Reuso declarado (§2.1, uma metafora por icone): o mesmo nome pode
 * aparecer em mais de uma categoria quando o significado e o mesmo
 * (ex.: `info` e estado e Sobre; `gauge` e medicao e leitura).
 * Tamanhos conferidos na folha de contato: 16, 20 e 24 px (§2.1).
 */
export const ICON_SET_SIZES = [16, 20, 24] as const

export const NAV_ICONS: Record<string, IconName> = {
  Inicio: 'home',
  Cadastro: 'clipboard-list',
  Faturamento: 'receipt',
  Medicao: 'gauge',
  Arrecadacao: 'banknote',
  'Ordens de servico': 'wrench',
  Almoxarifado: 'warehouse',
  GIS: 'map',
  Relatorios: 'bar-chart-3',
  Chamados: 'life-buoy',
  Ajuda: 'help-circle',
  Configuracoes: 'settings',
  'Central Ziip': 'layout-dashboard',
  Sobre: 'info',
}

export const ACTION_ICONS: Record<string, IconName> = {
  Criar: 'plus',
  Editar: 'pencil',
  Excluir: 'trash-2',
  Salvar: 'save',
  Cancelar: 'x',
  Pesquisar: 'search',
  Filtrar: 'filter',
  Exportar: 'upload',
  Importar: 'download',
  Baixar: 'arrow-down-to-line',
  Imprimir: 'printer',
  Anexar: 'paperclip',
  Aprovar: 'check',
  Recusar: 'x-circle',
  Reabrir: 'rotate-ccw',
}

export const STATE_ICONS: Record<string, IconName> = {
  Sucesso: 'check-circle-2',
  Aviso: 'alert-triangle',
  Erro: 'alert-circle',
  Informacao: 'info',
  Carregando: 'loader-2',
  Bloqueado: 'lock',
  Pendente: 'clock',
}

export const SYSTEM_ICONS: Record<string, IconName> = {
  'Tema claro': 'sun',
  'Tema escuro': 'moon',
  'Tema do sistema': 'monitor',
  Idioma: 'languages',
  Usuario: 'user',
  Sair: 'log-out',
  Notificacoes: 'bell',
  'Ajuda contextual': 'help-circle',
}

export const SANITATION_ICONS: Record<string, IconName> = {
  Cliente: 'sanitation-customer',
  Imovel: 'sanitation-property',
  'Ligacao de agua': 'sanitation-water-connection',
  'Ligacao de esgoto': 'sanitation-sewer-connection',
  Hidrometro: 'sanitation-water-meter',
  Leitura: 'sanitation-reading',
  'Rota de leitura': 'sanitation-reading-route',
  Fatura: 'sanitation-bill',
  Conta: 'sanitation-account',
  Pagamento: 'sanitation-payment',
  'Debito automatico': 'sanitation-direct-debit',
  'Nota fiscal (PDF e XML)': 'sanitation-invoice',
  'Ordem de servico': 'sanitation-service-order',
  Material: 'sanitation-material',
  'Requisicao de material': 'sanitation-material-request',
  Reserva: 'sanitation-reservation',
  'Baixa de estoque': 'sanitation-stock-out',
  Inventario: 'sanitation-inventory',
  Veiculo: 'sanitation-vehicle',
  'Equipe de campo': 'sanitation-field-team',
  Vazamento: 'sanitation-leak',
  Manobra: 'sanitation-maneuver',
  'Qualidade da agua': 'sanitation-water-quality',
  Amostra: 'sanitation-sample',
  'Residuo (MTR)': 'sanitation-waste',
  Chamado: 'sanitation-support-ticket',
  Contrato: 'sanitation-contract',
  'Portal do consumidor': 'sanitation-consumer-portal',
}

/** Icones do registro fora das categorias §4.1/§4.2 (uso generico herdado da P0.46). */
export const GENERIC_CORE_ICONS: IconName[] = [
  'building-2',
  'chevron-left',
  'chevron-right',
  'eye',
  'eye-off',
  'layers',
  'list-checks',
  'log-in',
  'map-pin',
  'menu',
  'palette',
  'users',
]

export const CUSTOM_ICON_NAMES = Object.keys(CUSTOM_ICONS) as Array<keyof typeof CUSTOM_ICONS>

/** Todas as secoes da folha de contato, na ordem de exibicao. */
export const ICON_SECTIONS: Array<{ title: string; icons: Record<string, IconName> }> = [
  { title: 'Navegacao', icons: NAV_ICONS },
  { title: 'Acoes', icons: ACTION_ICONS },
  { title: 'Estados', icons: STATE_ICONS },
  { title: 'Sistema', icons: SYSTEM_ICONS },
  { title: 'Entidades de saneamento', icons: SANITATION_ICONS },
]
