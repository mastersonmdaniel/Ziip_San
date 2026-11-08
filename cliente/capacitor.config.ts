import type { CapacitorConfig } from '@capacitor/cli';

/**
 * MODELO do app do cliente (ED-39): os valores com __TENANT_*__ sao
 * carimbados pelo pipeline (pipeline/generate.ts) a partir da
 * configuracao da core V11_1 do tenant — este arquivo, sem carimbo,
 * NAO e um app publicavel. appId/appName sao por tenant (marca do
 * prestador, ADR-0026), nas DUAS plataformas (Android e iOS).
 */
const config: CapacitorConfig = {
  appId: '__TENANT_APP_ID__',
  appName: '__TENANT_APP_NAME__',
  webDir: 'dist',
  android: {
    allowMixedContent: false,
  },
  ios: {
    // Login SEMPRE no navegador do sistema (SFAuthenticationSession via
    // Capacitor Browser): nenhuma WebView embutida recebe credencial.
    contentInset: 'always',
  },
};

export default config;
