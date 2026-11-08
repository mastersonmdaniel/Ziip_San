/**
 * Marca do tenant aplicada ao app do cliente (ED-39, sobre a P3.21):
 * nome, icone e cores vem da configuracao da core V11_1 (que o pipeline
 * carimbou da marca da P1.19). Aqui o app APLICA o tema: variaveis CSS
 * com os mesmos nomes da vista do portal da P3.21
 * (--brand-primary, --brand-on-primary, --brand-text), para portal e
 * app nao divergirem. A matematica de contraste NAO e duplicada: o
 * servidor (P1.19/P3.21) entrega as cores efetivas; o app so confere o
 * formato e aplica — cor fora do formato e recusada, nunca presumida.
 */
export interface TenantTheme {
  displayName: string;
  primaryColor: string;
  onPrimaryColor: string;
  brandTextColor: string;
}

const HEX = /^#[0-9A-Fa-f]{6}$/;

export function assertTenantTheme(theme: TenantTheme): TenantTheme {
  if (!theme.displayName || theme.displayName.trim() === '') {
    throw new Error('nome do tenant e obrigatorio no tema do app');
  }
  for (const [field, value] of Object.entries({
    primaryColor: theme.primaryColor,
    onPrimaryColor: theme.onPrimaryColor,
    brandTextColor: theme.brandTextColor,
  })) {
    if (!HEX.test(value)) {
      throw new Error(`cor ${field} fora do formato #RRGGBB: ${value}`);
    }
  }
  return theme;
}

export function cssVariables(theme: TenantTheme): Record<string, string> {
  assertTenantTheme(theme);
  return {
    '--brand-primary': theme.primaryColor,
    '--brand-on-primary': theme.onPrimaryColor,
    '--brand-text': theme.brandTextColor,
  };
}
