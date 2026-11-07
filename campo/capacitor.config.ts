import type { CapacitorConfig } from '@capacitor/cli';

/**
 * App de campo (ED-18): somente Android, distribuido por APK assinado (TR-621) e,
 * quando houver conta de organizacao, pela loja na regra da ED-39.
 * appId segue o padrao reverso do projeto (br.com.ziipsan).
 */
const config: CapacitorConfig = {
  appId: 'br.com.ziipsan.campo',
  appName: 'ZiipSan Campo',
  webDir: 'dist',
  android: {
    // Sem WebView remoto nem conteudo misto: o app e offline-first e o unico
    // trafego e a API do tenant e o login no navegador do sistema.
    allowMixedContent: false,
  },
};

export default config;
