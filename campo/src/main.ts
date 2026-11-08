import { FieldSession } from './auth/session.ts';
import { SyncEngine } from './sync/syncEngine.ts';
import { mountFieldScreens, type MountResult } from './ui/boot.ts';
import type { FieldScreensDeps } from './ui/controller.ts';
import { mountOrderScreens, type OrderMountResult } from './ui/orders/boot.ts';
import type { OrderScreensDeps } from './ui/orders/controller.ts';

// Bootstrap minimo do app de campo (ED-18): a casca sobe, abre o banco local
// cifrado e expoe sessao/sincronizacao para a UI de rota. A UI completa de
// leitura (telas) cresce sobre esta base nas entregas seguintes (ED-19/ED-20)
// sem mudar os contratos daqui.
export interface CampoBootstrap {
  session: FieldSession;
  sync: SyncEngine;
}

export function describeBootstrap(): string {
  return 'ZiipSan Campo — base ED-18 (autenticacao PKCE, banco cifrado, sincronizacao por task_id)';
}

/**
 * Ponto de entrada das telas de leitura (R2-M1): monta as 5 telas do app de
 * campo sobre o bootstrap real, com as dependencias da integracao (pacote
 * da rota, store, fachada de emissao e portas P-03/P-04 — ver ui/boot.ts).
 * A casca nativa (Capacitor) chama esta funcao quando o bootstrap estiver
 * montado; ate la, o DOMContentLoaded abaixo mostra o estado honesto de
 * "aguardando montagem" em vez da casca vazia (pendencia RG2-C-P1).
 */
export async function startCampoApp(
  root: HTMLElement,
  bootstrap: CampoBootstrap,
  deps: Omit<FieldScreensDeps, 'sync'>,
): Promise<MountResult> {
  return mountFieldScreens(root, bootstrap, deps);
}

/**
 * Ponto de entrada das telas de OS da ED-20 (R2-M3): monta as telas de
 * ordem de servico sobre o pacote de OS baixado (ver ui/orders/boot.ts).
 * Mesmo padrao do startCampoApp; o pacote de OS vai dentro das deps
 * (FieldOrderPackage), como o pacote de rota vai nas deps de leitura.
 */
export async function startOrderScreens(
  root: HTMLElement,
  deps: OrderScreensDeps,
): Promise<OrderMountResult> {
  return mountOrderScreens(root, deps);
}

/**
 * Estado inicial honesto (R2-M1): enquanto nenhuma integracao chamar
 * startCampoApp/startOrderScreens, o app nao sobe com a casca vazia — ele
 * declara que a casca subiu e aguarda a montagem, nomeando o ponto de
 * entrada. O texto some quando as telas montam (replaceChildren). Nao e
 * tela de producao: e o estado de "sem montagem" da casca.
 */
if (typeof document !== 'undefined') {
  document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('app');
    if (root && root.childElementCount === 0) {
      const status = document.createElement('p');
      status.setAttribute('role', 'status');
      status.textContent =
        'ZiipSan Campo — casca carregada. As telas de campo aguardam a montagem ' +
        'da integração (startCampoApp).';
      root.replaceChildren(status);
    }
  });
}
