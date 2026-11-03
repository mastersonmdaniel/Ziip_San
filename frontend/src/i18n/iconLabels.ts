import type { IconName } from '@components/ui/Icon'

/**
 * P0.46 — nomes acessiveis dos icones, em pt-BR (unico idioma publicado;
 * o catalogo completo de mensagens da interface e P1.18). Cada icone do
 * registro tem exatamente um rotulo aqui: o teste de icone quebra se um
 * icone novo entrar sem rotulo. O componente <Icon> usa o rotulo quando o
 * icone NAO e decorativo (label); icone decorativo fica aria-hidden.
 */
export const ICON_LABELS: Record<IconName, string> = {
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
}
