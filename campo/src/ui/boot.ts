/**
 * Boot das telas do app de campo (LOTE 3 / M1) — arquivo NOVO de fiacao.
 *
 * INTEGRACAO (instrucao nomeada — o main.ts da ED-18 NAO e reescrito):
 * quando a integracao montar o CampoBootstrap real (sessao + SyncEngine +
 * LocalStore + fachada FieldEmission), basta UMA chamada no ponto onde hoje
 * a casca sobe:
 *
 *   import { mountFieldScreens } from './ui/boot.ts';
 *   await mountFieldScreens(document.getElementById('app')!, bootstrap, {
 *     routePackage, store, emission, buildEmissionTask, saveReading,
 *     ocrSettings, ocrEngine, printer,
 *   });
 *
 * A assinatura real tem TRES argumentos: (root, bootstrap, deps). O
 * `bootstrap` e o CampoBootstrap da ED-18 ja montado — e dele que vem o
 * `sync` (por isso as deps sao Omit<FieldScreensDeps, 'sync'>: sync NAO
 * entra nas deps). Correcao D-03: o exemplo anterior mostrava 2
 * argumentos com sync dentro das deps e nao compilava.
 *
 * Enquanto as portas de integracao (P-02/P-03/P-04 do relatorio) nao forem
 * entregues, elas entram como null e as telas mostram a pendencia em vez de
 * fingir funcionamento. Os contratos de CampoBootstrap (ED-18) e da fachada
 * (ED-19) nao mudam.
 */
import type { CampoBootstrap } from '../main.ts';
import { FieldScreensController, type FieldScreensDeps } from './controller.ts';
import { FieldScreensView } from './screens.ts';

export interface MountResult {
  controller: FieldScreensController;
  view: FieldScreensView;
}

/**
 * Monta as telas sobre o bootstrap existente: usa o SyncEngine que o
 * CampoBootstrap ja expoe e carrega a fila local antes da primeira pintura.
 */
export async function mountFieldScreens(
  root: HTMLElement,
  bootstrap: CampoBootstrap,
  deps: Omit<FieldScreensDeps, 'sync'>,
): Promise<MountResult> {
  const controller = new FieldScreensController({ ...deps, sync: bootstrap.sync });
  await controller.load();
  const view = new FieldScreensView(root, controller);
  view.render();
  return { controller, view };
}
