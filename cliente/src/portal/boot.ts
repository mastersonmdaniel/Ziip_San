/**
 * Fiacao das telas do portal na casca da ED-39 (entrega/cliente/index.html,
 * <main id="app">). NAO reescreve o main.ts da ED-39: na integracao, o
 * main.ts chama mountPortalScreens(document.querySelector('#app'), ...)
 * apos applyTheme(...). O tema e aplicado pelas MESMAS variaveis da
 * ED-39 — cssVariables() de src/branding/tenantTheme.ts, reutilizado,
 * nao reescrito. Autenticacao: reutiliza src/auth/pkce.ts e
 * src/auth/session.ts da ED-39 (PortalSession) — o access token sai da
 * sessao entregue; esta fiacao nao guarda token. A tela do
 * centralizador (P-PORTAL-01) recebe as dependencias (porta tipada +
 * sessao) por parametro opcional de mountPortalScreens — a sessao vem
 * do vinculo da conta, nunca de campos digitados nesta fiacao.
 */
import { cssVariables, type TenantTheme } from '../branding/tenantTheme.ts';
import { PortalClient, type CentralizerScreenDeps, type HttpPort } from './transport.ts';
import {
  renderBills,
  renderCentralizer,
  renderCertificate,
  renderExplanation,
  renderGuest,
  renderOutage,
  renderReport,
  renderScheduling,
  renderSecondCopy,
  renderSimulation,
  renderValidate,
} from './screens.ts';

export type PortalScreenId =
  | 'visitante' | 'faturas' | 'segunda-via' | 'explicacao' | 'simulacao'
  | 'certidao' | 'validar' | 'relato' | 'abastecimento' | 'agendamento'
  | 'centralizador';
// Tela 'centralizador' (P-PORTAL-01): consome a PORTA TIPADA
// CentralizerPort (espelho do CentralizerViewService da ED-22), nao o
// PortalClient HTTP — nenhuma rota HTTP do centralizador esta provada
// (rota [A DEFINIR] na fiacao). Sem dependencias entregues a
// mountPortalScreens, a tela mostra o estado honesto da pendencia.

export function mountPortalScreens(
  root: HTMLElement,
  http: HttpPort,
  theme: TenantTheme,
  initial: PortalScreenId = 'visitante',
  centralizer?: CentralizerScreenDeps,
): { show(id: PortalScreenId): void } {
  // Mapa montado dentro da funcao: a entrada 'centralizador' fecha sobre
  // as dependencias (porta tipada + sessao) desta montagem e ignora o
  // PortalClient HTTP — o centralizador nao tem rota HTTP provada.
  const SCREENS: Record<PortalScreenId, (root: HTMLElement, c: PortalClient) => void> = {
    visitante: renderGuest,
    faturas: renderBills,
    'segunda-via': renderSecondCopy,
    explicacao: renderExplanation,
    simulacao: renderSimulation,
    certidao: renderCertificate,
    validar: renderValidate,
    relato: renderReport,
    abastecimento: renderOutage,
    agendamento: renderScheduling,
    centralizador: (r) => renderCentralizer(r, centralizer),
  };
  for (const [name, value] of Object.entries(cssVariables(theme))) {
    document.documentElement.style.setProperty(name, value);
  }
  const client = new PortalClient(http);
  const nav = document.createElement('nav');
  nav.setAttribute('aria-label', 'Portal do consumidor');
  const content = document.createElement('div');
  const show = (id: PortalScreenId): void => {
    content.textContent = '';
    for (const b of Array.from(nav.querySelectorAll('button'))) {
      b.removeAttribute('aria-current');
      // Botao ativo recebe aria-current="page" (padrao a11y da ED-39;
      // correcao R-PORTAL-04 — antes show() so removia, nunca aplicava).
      if (b.textContent === id) b.setAttribute('aria-current', 'page');
    }
    SCREENS[id](content as HTMLElement, client);
    const title = content.querySelector('h2');
    if (title) (title as HTMLElement).focus();
  };
  for (const id of Object.keys(SCREENS) as PortalScreenId[]) {
    const b = document.createElement('button');
    b.setAttribute('type', 'button');
    // Alvo >= 48px via .portal-action de portal.css (correcao R-PORTAL-04).
    b.setAttribute('class', 'portal-action');
    b.textContent = id;
    b.addEventListener('click', () => show(id));
    nav.appendChild(b);
  }
  root.appendChild(nav);
  root.appendChild(content);
  show(initial);
  return { show };
}
