/**
 * Boot das telas de OS em campo (R2-M3 / ED-20) — arquivo NOVO de fiacao.
 *
 * INTEGRACAO (executada em main.ts — R2-M3): o ponto de entrada do app
 * (`startOrderScreens`, em `../../main.ts`) chama esta funcao com as
 * dependencias da integracao (o pacote de OS vai dentro das deps, como o
 * pacote de rota vai nas deps das telas de leitura). A assinatura real tem
 * DOIS argumentos: (root, deps). O pacote de OS (FieldOrderPackage) ainda
 * nao e emitido pelo servidor (RG2-C-P3): a integracao o monta a partir
 * das OS da P1.31 + pacotes de corte da ED-20; sem pacote, as telas nao
 * montam — nada e fingido como OS.
 */
import { OrderScreensController, type OrderScreensDeps } from './controller.ts';
import { OrderScreensView } from './screens.ts';

export interface OrderMountResult {
  controller: OrderScreensController;
  view: OrderScreensView;
}

/** Monta as telas de OS sobre o pacote baixado (dentro das deps). */
export function mountOrderScreens(
  root: HTMLElement,
  deps: OrderScreensDeps,
): OrderMountResult {
  const controller = new OrderScreensController(deps);
  const view = new OrderScreensView(root, controller);
  view.render();
  return { controller, view };
}
