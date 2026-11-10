/**
 * Boot das telas da ED-40 (pacote ed-40-telas) — arquivo de fiacao.
 *
 * INTEGRACAO: quando a fiacao montar o portal no hardware do cliente,
 * basta UMA chamada com as dependencias reais:
 *
 *   import { mountAtendimentoScreens } from './kiosk/boot.ts';
 *   await mountAtendimentoScreens(document.getElementById('app')!, {
 *     kiosk: { deviceId, serviceUnitId, kioskEnabled, issuePort,
 *              serviceTypes, now, newTicketId, nextSequence },
 *     panelSource,      // HttpPanelTransport sobre GET /painel-chamada
 *     deskPort,         // DeskPort — rota HTTP [A DEFINIR] (P-ED40T-01)
 *     deskId,
 *     reportSource,     // HttpWaitingReportTransport (RAS-26)
 *     panelUnitId, now,
 *   });
 *
 * Enquanto as portas de fiacao nao sao entregues, elas entram pela
 * implementacao local sobre o nucleo (ports.ts) e as telas funcionam
 * sobre ela — ver pendencias no RELATORIO-ED-40-TELAS.md.
 */
import {
  DeskController,
  KioskController,
  PanelController,
  WaitingReportController,
  type KioskDeps,
  type PanelSource,
  type WaitingReportSource,
} from './controller.ts';
import type { DeskPort } from './ports.ts';
import { AtendimentoScreensView } from './screens.ts';

export interface MountDeps {
  kiosk: KioskDeps;
  panelSource: PanelSource;
  deskPort: DeskPort;
  deskId: string;
  reportSource: WaitingReportSource;
  panelUnitId: string;
  now: () => string;
}

export interface MountResult {
  kiosk: KioskController;
  panel: PanelController;
  desk: DeskController;
  report: WaitingReportController;
  view: AtendimentoScreensView;
}

export async function mountAtendimentoScreens(
  root: HTMLElement,
  deps: MountDeps,
): Promise<MountResult> {
  const kiosk = new KioskController(deps.kiosk);
  const panel = new PanelController(deps.panelSource);
  const desk = new DeskController(deps.deskPort, deps.deskId, deps.now);
  const report = new WaitingReportController(deps.reportSource);
  await desk.load();
  await panel.load(deps.panelUnitId);
  const view = new AtendimentoScreensView(root, { kiosk, panel, desk, report, panelUnitId: deps.panelUnitId });
  view.render();
  return { kiosk, panel, desk, report, view };
}
