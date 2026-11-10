/**
 * Testes de TELA (screens.ts) — pacote ed-40-telas, correccao R2-F2/F3/F4/F5.
 *
 * screens.ts nunca correu sob `node --test` (a producao provou-o so por
 * bundle esbuild): estes testes montam um DOM MINIMO falso, suficiente
 * para a API que screens.ts usa (createElement, setAttribute, append,
 * replaceChildren, addEventListener/click, focus, textContent), e
 * exercitam o COMPORTAMENTO: botoes sao encontrados na arvore
 * renderizada e CLICADOS, e o efeito observa-se no controlador e na
 * re-renderizacao. F4/F5 sao assercoes sobre o kiosk.css (sem motor de
 * layout, a regra escrita e o objecto verificavel).
 * Fixtures FICTICIAS de demonstracao — nenhum dado real.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  HolidayCalendar,
  KioskTerminal,
  QueueTicket,
  commonProfile,
  makeWaitingLimitPolicy,
  priorityProfile,
} from './models.ts';
import {
  LocalDeskPort,
  LocalKioskIssuePort,
  LocalPanelSource,
  LocalWaitingReportSource,
} from './ports.ts';
import {
  DeskController,
  KioskController,
  PanelController,
  WaitingReportController,
} from './controller.ts';
import { AtendimentoScreensView } from './screens.ts';

const UNIT = '00000000-0000-0000-0000-0000000000a1';
const DEVICE = '00000000-0000-0000-0000-0000000000d1';

// --- DOM minimo -------------------------------------------------------------
class FakeElement {
  readonly tagName: string;
  readonly attributes = new Map<string, string>();
  children: (FakeElement | string)[] = [];
  private readonly listeners = new Map<string, (() => void)[]>();
  private ownText: string | null = null;

  constructor(tag: string) {
    this.tagName = tag.toUpperCase();
  }

  setAttribute(k: string, v: string): void {
    this.attributes.set(k, v);
  }

  append(...items: (FakeElement | string)[]): void {
    this.children.push(...items);
  }

  replaceChildren(...items: (FakeElement | string)[]): void {
    this.children = [...items];
    this.ownText = null;
  }

  addEventListener(type: string, fn: () => void): void {
    const list = this.listeners.get(type) ?? [];
    list.push(fn);
    this.listeners.set(type, list);
  }

  click(): void {
    for (const fn of this.listeners.get('click') ?? []) fn();
  }

  focus(): void {}

  set textContent(v: string) {
    this.ownText = v;
    this.children = [];
  }

  get textContent(): string {
    if (this.ownText !== null) return this.ownText;
    return this.children
      .map((c) => (typeof c === 'string' ? c : c.textContent))
      .join('');
  }
}

(globalThis as { document?: unknown }).document = {
  createElement: (tag: string) => new FakeElement(tag),
};

function collect(root: FakeElement, pred: (el: FakeElement) => boolean): FakeElement[] {
  const out: FakeElement[] = [];
  const walk = (el: FakeElement): void => {
    if (pred(el)) out.push(el);
    for (const c of el.children) if (typeof c !== 'string') walk(c);
  };
  walk(root);
  return out;
}

function buttonsByText(root: FakeElement, text: string): FakeElement[] {
  return collect(
    root,
    (el) => el.tagName === 'BUTTON' && el.textContent.trim() === text,
  );
}

async function flush(): Promise<void> {
  await new Promise((r) => setTimeout(r, 10));
  await new Promise((r) => setTimeout(r, 10));
}

// --- Fiacao falsa sobre o nucleo local --------------------------------------
function makeView(kioskEnabled: boolean) {
  let seq = 0;
  let id = 0;
  const kiosk = new KioskController({
    deviceId: DEVICE,
    serviceUnitId: UNIT,
    kioskEnabled,
    issuePort: new LocalKioskIssuePort(
      KioskTerminal.fromParameters(DEVICE, UNIT, kioskEnabled),
    ),
    serviceTypes: ['ATENDIMENTO'],
    now: () => '2026-03-04T08:00:00',
    newTicketId: () => `ticket-${++id}`,
    nextSequence: () => ++seq,
  });
  const common = new QueueTicket(
    'd1', UNIT, 1, 'ATENDIMENTO', commonProfile(), '2026-03-04T08:00:00',
  );
  const prio = new QueueTicket(
    'd2', UNIT, 2, 'ATENDIMENTO', priorityProfile('ELDERLY_60_OR_OVER'),
    '2026-03-04T08:05:00',
  );
  const desk = new DeskController(
    new LocalDeskPort([common, prio]),
    'G1',
    () => '2026-03-04T08:20:00',
  );
  const panel = new PanelController(new LocalPanelSource([], 5));
  const policy = makeWaitingLimitPolicy(HolidayCalendar.of(new Set<string>()), 15, 30);
  const report = new WaitingReportController(new LocalWaitingReportSource([], policy));
  const root = new FakeElement('div');
  const view = new AtendimentoScreensView(root as unknown as HTMLElement, {
    kiosk,
    panel,
    desk,
    report,
    panelUnitId: UNIT,
  });
  return { root, view, desk, kiosk };
}

test('tela do guiche (R2-F2): cada senha em espera tem botao Cancelar e o clique cancela sem chamar', async () => {
  const { root, view, desk } = makeView(true);
  await desk.load();
  view.render();
  // Navega pela barra, como o atendente.
  buttonsByText(root, 'Guichê')[0]?.click();
  // Um botao de cancelar por senha em espera (2 em espera).
  const cancelButtons = buttonsByText(root, 'Cancelar senha');
  assert.equal(cancelButtons.length, 2);
  // A primeira da fila e a prioritaria (ordem do nucleo).
  cancelButtons[0]?.click();
  await flush();
  const cancelled = desk.all.find((t) => t.id === 'd2');
  assert.equal(cancelled?.status, 'CANCELLED');
  assert.equal(cancelled?.calledAt, null);
  assert.equal(cancelled?.finishedAt, '2026-03-04T08:20:00');
  assert.deepEqual(desk.waiting().map((t) => t.displayCode), ['N001']);
  // Re-renderizacao mostra a fila reduzida e o aviso de cancelamento.
  assert.ok(root.textContent.includes('Fila de espera (1)'));
  assert.ok(root.textContent.includes('Senha P002 cancelada.'));
  assert.equal(buttonsByText(root, 'Cancelar senha').length, 1);
});

test('quiosque em recusa (R2-F3): botao "Ver o painel" existe e navega para o painel', () => {
  const { root, view } = makeView(false);
  view.render();
  assert.ok(root.textContent.includes('Quiosque desligado'));
  // Antes da correccao o botao era codigo morto (actionAllowed exige enabled).
  const verPainel = buttonsByText(root, 'Ver o painel');
  assert.equal(verPainel.length, 1);
  verPainel[0]?.click();
  // Navegou: a tela do painel (SG-626) esta renderizada.
  assert.ok(root.textContent.includes('Painel de chamada'));
  assert.ok(root.textContent.includes('Nenhuma senha chamada no momento.'));
});

test('kiosk.css (R2-F4): rotulos do perfil prioritario com alvo de toque de 48px', () => {
  const css = readFileSync(new URL('./kiosk.css', import.meta.url), 'utf8');
  const rule = /\.kiosk-fieldset label \{([^}]*)\}/.exec(css);
  assert.ok(rule, 'regra .kiosk-fieldset label presente');
  assert.ok(rule[1].includes('min-height: 48px'));
  assert.equal(css.includes('min-height: 32px'), false);
});

test('kiosk.css (R2-F5): nenhuma cor fixa #00548f; destaque so via var(--brand-primary, neutro)', () => {
  const css = readFileSync(new URL('./kiosk.css', import.meta.url), 'utf8');
  assert.equal(css.toLowerCase().includes('#00548f'), false);
  const uses = css.match(/var\(--brand-primary, #333333\)/g) ?? [];
  assert.equal(uses.length, 2); // .kiosk-btn-selected e .kiosk-active
});
