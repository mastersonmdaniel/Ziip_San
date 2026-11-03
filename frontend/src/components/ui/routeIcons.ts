import type { IconName } from './Icon'

/**
 * P0.46 — toda rota tem icone (teste de cobertura em icon.test.tsx, que le
 * os paths de App.tsx e confere este mapa). A rota '/' (layout) e '*' (nao
 * encontrada) nao tem pagina propria e ficam fora do mapa de proposito: a
 * rota indice redireciona para /cadastro/imoveis, que tem icone.
 */
export const ROUTE_ICONS: Record<string, IconName> = {
  '/login': 'log-in',
  '/auth/callback': 'loader-2',
  '/cadastro/imoveis': 'building-2',
  '/cadastro/imoveis/novo': 'plus',
  '/cadastro/imoveis/:id': 'building-2',
  '/cadastro/imoveis/:id/editar': 'pencil',
  '/cadastro/clientes': 'users',
  '/cadastro/clientes/novo': 'plus',
  '/cadastro/clientes/:id': 'users',
  '/cadastro/clientes/:id/editar': 'pencil',
  '/cadastro/territorio': 'map',
  '/cadastro/territorio/:nivel': 'map',
  '/cadastro/catalogos': 'list-checks',
  '/cadastro/catalogos/:tipo': 'list-checks',
}
