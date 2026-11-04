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
  '/dev/icones': 'eye',
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
  '/gis/catalogo': 'map',
  // P1.12: item de entrega das exportações assíncronas (e, futuramente, dos relatórios da P3.22).
  '/arquivos-gerados': 'download',
  // P1.33: visões salvas da busca global.
  '/busca/visoes-salvas': 'search',
  // P1-C (integracao): as 4 rotas novas da camada C usam o icone do
  // proprio item de menu (Layout.tsx) — sem elas, icon.test.tsx quebra
  // em 'rota sem icone' na primeira que encontra (/about).
  '/about': 'info',
  '/importacoes': 'upload',
  '/platform/central': 'gauge',
  '/support': 'life-buoy',
}
