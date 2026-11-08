/**
 * Entrada do app do cliente (ED-39): aplica o tema do tenant carimbado
 * pelo pipeline e monta a casca. As telas de consumo/conta/avisos sao
 * as do portal do consumidor (P3.3/ED-38) sobre a base do tenant; a
 * fiacao das rotas e passo de integracao (ver relatorio).
 */
import { cssVariables, type TenantTheme } from './branding/tenantTheme.ts';

// Carimbado pelo pipeline a partir da core V11_1 (no template, o nome
// e marcador e as cores sao neutras: nenhum tenant real aqui).
export const TENANT_THEME: TenantTheme = {
  displayName: '__TENANT_APP_NAME__',
  primaryColor: '#000000',
  onPrimaryColor: '#FFFFFF',
  brandTextColor: '#000000',
};

export function applyTheme(theme: TenantTheme): void {
  const vars = cssVariables(theme);
  for (const [name, value] of Object.entries(vars)) {
    document.documentElement.style.setProperty(name, value);
  }
  document.title = theme.displayName;
}
