import type { IconName } from '@components/ui/Icon'

/**
 * P0.46 + P1.29 — nomes acessiveis dos icones, em pt-BR (unico idioma
 * publicado; o catalogo completo de mensagens da interface e P1.18).
 * Cada icone do registro tem exatamente um rotulo aqui: o teste de icone
 * quebra se um icone novo entrar sem rotulo. O componente <Icon> usa o
 * rotulo quando o icone NAO e decorativo (label); icone decorativo fica
 * aria-hidden. Rotulos de entidade de saneamento nomeiam a entidade, nao
 * o desenho, para o leitor de tela anunciar o significado.
 */
export const ICON_LABELS: Record<IconName, string> = {
  // Nucleo (P0.46)
  'alert-circle': 'Alerta de erro',
  'alert-triangle': 'Alerta',
  'building-2': 'Imoveis',
  check: 'Concluido',
  'check-circle-2': 'Sucesso',
  'chevron-left': 'Anterior',
  'chevron-right': 'Proximo',
  eye: 'Mostrar',
  'eye-off': 'Ocultar',
  info: 'Informacao',
  'list-checks': 'Catalogos',
  'loader-2': 'Carregando',
  'log-in': 'Entrar',
  'log-out': 'Sair',
  map: 'Mapa',
  'map-pin': 'Localizacao',
  menu: 'Menu',
  moon: 'Tema escuro',
  pencil: 'Editar',
  plus: 'Adicionar',
  search: 'Pesquisar',
  sun: 'Tema claro',
  'trash-2': 'Excluir',
  user: 'Usuario',
  users: 'Clientes',
  x: 'Fechar',
  // §4.1 navegacao
  home: 'Inicio',
  'clipboard-list': 'Cadastro',
  receipt: 'Faturamento',
  gauge: 'Medicao',
  banknote: 'Arrecadacao',
  wrench: 'Ordens de servico',
  warehouse: 'Almoxarifado',
  'bar-chart-3': 'Relatorios',
  'life-buoy': 'Chamados',
  'help-circle': 'Ajuda',
  settings: 'Configuracoes',
  'layout-dashboard': 'Central Ziip',
  // R41-4-29/30: icones do menu GIS (Layout.tsx:34-37).
  palette: 'Paleta',
  layers: 'Camadas',
  // §4.1 acoes
  save: 'Salvar',
  filter: 'Filtrar',
  upload: 'Exportar',
  download: 'Importar',
  'arrow-down-to-line': 'Baixar',
  printer: 'Imprimir',
  paperclip: 'Anexar',
  'x-circle': 'Recusar',
  'rotate-ccw': 'Reabrir',
  // §4.1 estados
  lock: 'Bloqueado',
  clock: 'Pendente',
  // §4.1 sistema
  monitor: 'Tema do sistema',
  languages: 'Idioma',
  bell: 'Notificacoes',
  // §4.2 entidades de saneamento
  'sanitation-customer': 'Cliente',
  'sanitation-property': 'Imovel',
  'sanitation-water-connection': 'Ligacao de agua',
  'sanitation-sewer-connection': 'Ligacao de esgoto',
  'sanitation-water-meter': 'Hidrometro',
  'sanitation-reading': 'Leitura',
  'sanitation-reading-route': 'Rota de leitura',
  'sanitation-bill': 'Fatura',
  'sanitation-account': 'Conta',
  'sanitation-payment': 'Pagamento',
  'sanitation-direct-debit': 'Debito automatico',
  'sanitation-invoice': 'Nota fiscal',
  'sanitation-service-order': 'Ordem de servico',
  'sanitation-material': 'Material',
  'sanitation-material-request': 'Requisicao de material',
  'sanitation-reservation': 'Reserva',
  'sanitation-stock-out': 'Baixa de estoque',
  'sanitation-inventory': 'Inventario',
  'sanitation-vehicle': 'Veiculo',
  'sanitation-field-team': 'Equipe de campo',
  'sanitation-leak': 'Vazamento',
  'sanitation-maneuver': 'Manobra',
  'sanitation-water-quality': 'Qualidade da agua',
  'sanitation-sample': 'Amostra',
  'sanitation-waste': 'Residuo (MTR)',
  'sanitation-support-ticket': 'Chamado',
  'sanitation-contract': 'Contrato',
  'sanitation-consumer-portal': 'Portal do consumidor',
}
