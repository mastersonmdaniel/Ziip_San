/**
 * Fiacao da tela de autoleitura na casca da ED-39 (mesmo padrao de
 * cliente/src/portal/boot.ts): o main.ts da integracao chama
 * mountSelfReading(...) apos applyTheme(...); o tema reutiliza
 * cssVariables() de src/branding/tenantTheme.ts, sem reescrever.
 * A porta e a configuracao sao INJECTADAS: sem fiacao HTTP
 * (P-II7-FIACAO) e sem limiares do tenant (P-II7-LIMIARES [A DEFINIR])
 * nao ha valores por omissao nesta camada.
 */
import { cssVariables, type TenantTheme } from '../branding/tenantTheme.ts';
import type { SelfReadingPort, SelfReadingScoreConfig } from './transport.ts';
import { renderSelfReading } from './screens.ts';

export function mountSelfReading(
  root: HTMLElement,
  port: SelfReadingPort,
  config: SelfReadingScoreConfig,
  theme: TenantTheme,
  newSubmissionId: () => string,
): void {
  for (const [name, value] of Object.entries(cssVariables(theme))) {
    document.documentElement.style.setProperty(name, value);
  }
  renderSelfReading(root, { port, config, newSubmissionId });
}
