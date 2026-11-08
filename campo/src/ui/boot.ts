/**
 * Boot das telas do app de campo (LOTE 3 / M1) — arquivo NOVO de fiacao.
 *
 * INTEGRACAO (executada em main.ts — R2-M1): o ponto de entrada do app
 * (`startCampoApp`, em `../main.ts`) chama esta funcao com o bootstrap
 * real e as dependencias da integracao; o CSS (`campo.css`) esta linkado
 * no `index.html`. A chamada real (3 argumentos — D-03):
 *
 *   await startCampoApp(document.getElementById('app')!, bootstrap, {
 *     routePackage, store, emission, buildEmissionTask, saveReading,
 *     ocrSettings, ocrEngine, printer, fetchDivergences, queryDirective,
 *   });
 *
 * O `bootstrap` e o CampoBootstrap da ED-18 ja montado — e dele que vem o
 * `sync` (por isso as deps sao Omit<FieldScreensDeps, 'sync'>: sync NAO
 * entra nas deps).
 *
 * Enquanto as portas de integracao (P-02/P-03/P-04 do relatorio) nao forem
 * entregues, elas entram como null e as telas mostram a pendencia em vez de
 * fingir funcionamento. P-03 e P-04 ja tem montagem real em TS
 * (field/fieldTariffSnapshot.ts e sync/fieldReadingStore.ts); o que segue
 * pendente sao os DADOS por tarefa e a implementacao SQLCipher do
 * LocalStore (RG2-C-P2/P4). Os contratos de CampoBootstrap (ED-18) e da
 * fachada (ED-19) nao mudam.
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
