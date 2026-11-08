/**
 * Teste de renderizacao DOM das telas do app de campo (R2-m4 / L-01).
 *
 * O projeto nao tem jsdom nem ambiente DOM de teste (L-01): este arquivo
 * usa um STUB MINIMO de DOM, so de teste, que implementa exatamente o
 * subconjunto que `screens.ts` usa (createElement, setAttribute, append,
 * replaceChildren, addEventListener/click, textContent, focus, value).
 * Nao e um navegador: nao prova layout, CSS nem WebView real — prova que
 * a renderizacao monta a arvore certa (titulos, rotulos, secoes, botoes)
 * e que as acoes dos botoes disparam os caminhos do controller. A prova
 * em WebView real continua sendo da CI/aparelho do dono (L-01/L-03).
 *
 * O stub vive NESTE arquivo de teste: nenhum codigo de producao o usa.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { PendingReading, RoutePackage } from '../readings/routePackage.ts';
import { SyncEngine, type LocalStore, type SyncTransport } from '../sync/syncEngine.ts';
import type { FieldSecurityBridge } from '../plugins/fieldSecurity.ts';
import { FieldScreensController, type FieldScreensDeps } from './controller.ts';
import { FieldScreensView } from './screens.ts';
import type { DivergenceEntry } from './models.ts';

// ---------------------------------------------------------------------------
// Stub minimo de DOM (so de teste)
// ---------------------------------------------------------------------------

type Listener = () => void;

class StubElement {
  readonly tagName: string;
  readonly attributes = new Map<string, string>();
  readonly children: (StubElement | string)[] = [];
  private readonly listeners = new Map<string, Listener[]>();
  value = '';

  constructor(tag: string) {
    this.tagName = tag.toUpperCase();
  }

  setAttribute(key: string, v: string): void {
    this.attributes.set(key, v);
    if (key === 'value') this.value = v;
  }

  getAttribute(key: string): string | null {
    return this.attributes.get(key) ?? null;
  }

  append(...nodes: (StubElement | string)[]): void {
    this.children.push(...nodes);
  }

  replaceChildren(...nodes: (StubElement | string)[]): void {
    this.children.length = 0;
    this.children.push(...nodes);
  }

  addEventListener(type: string, fn: Listener): void {
    const list = this.listeners.get(type) ?? [];
    list.push(fn);
    this.listeners.set(type, list);
  }

  click(): void {
    for (const fn of this.listeners.get('click') ?? []) fn();
  }

  focus(): void {
    // Sem foco real no stub: a chamada existe para nao quebrar o render.
  }

  get textContent(): string {
    return this.children
      .map((c) => (typeof c === 'string' ? c : c.textContent))
      .join('');
  }

  set textContent(v: string) {
    this.children.length = 0;
    this.children.push(v);
  }
}

class StubDocument {
  createElement(tag: string): StubElement {
    return new StubElement(tag);
  }
}

(globalThis as unknown as { document: StubDocument }).document = new StubDocument();

function findAll(el: StubElement, tag: string, out: StubElement[] = []): StubElement[] {
  if (el.tagName === tag.toUpperCase()) out.push(el);
  for (const c of el.children) {
    if (typeof c !== 'string') findAll(c, tag, out);
  }
  return out;
}

function buttonWithText(root: StubElement, text: string): StubElement | undefined {
  return findAll(root, 'button').find((b) => b.textContent.includes(text));
}

const sleep = (ms: number): Promise<void> => new Promise((r) => setTimeout(r, ms));

// ---------------------------------------------------------------------------
// Fakes (mesmo padrao de controller.test.ts, compactados)
// ---------------------------------------------------------------------------

const PKG: RoutePackage = {
  routeId: 'rota-1',
  reference: '2026-11',
  downloadedAt: '2026-11-08T06:00:00Z',
  occurrences: [
    { code: 'VAZAMENTO', requiresPhoto: true, requiresReading: false },
  ],
  tasks: [
    { taskId: 'task-1', unitId: 'u-1', installationId: 'i-1', kind: 'WATER', reference: '2026-11', previousReading: 12000, meterDigits: 5, sequence: 1 },
    { taskId: 'task-2', unitId: 'u-2', installationId: 'i-2', kind: 'WATER', reference: '2026-11', previousReading: 500, meterDigits: 5, sequence: 2 },
  ],
};

function makeStore(initial: PendingReading[] = []) {
  let pending = [...initial];
  const store: LocalStore = {
    async pendingReadings() { return [...pending]; },
    async saveRoute() {},
    async clearRoute() { pending = []; },
  };
  return { store };
}

function makeSync(store: LocalStore, directive: 'NONE' | 'RETIRE' | 'BLOCK' = 'NONE') {
  const transport: SyncTransport = {
    async fetchDirective() { return directive; },
    async uploadPending(_routeId, pending) {
      return pending.map((p) => ({ taskId: p.taskId, duplicate: false }));
    },
    async downloadRoute() { throw new Error('nao usado'); },
  };
  const security: FieldSecurityBridge = {
    async openEncryptedDatabase() { return { opened: true }; },
    async wipeAll() { return { wiped: true }; },
  };
  return new SyncEngine(transport, store, security);
}

function mount(over: Partial<FieldScreensDeps> = {}): { ctl: FieldScreensController; view: FieldScreensView; root: StubElement } {
  const storeFake = makeStore();
  const deps: FieldScreensDeps = {
    routePackage: PKG,
    store: storeFake.store,
    sync: makeSync(storeFake.store),
    emission: null,
    buildEmissionTask: null,
    saveReading: null,
    ocrSettings: null,
    ocrEngine: null,
    printer: null,
    fetchDivergences: null,
    queryDirective: null,
    now: () => '2026-11-08T10:00:00Z',
    ...over,
  };
  const ctl = new FieldScreensController(deps);
  const root = new StubElement('div');
  const view = new FieldScreensView(root as unknown as HTMLElement, ctl);
  return { ctl, view, root };
}

// ---------------------------------------------------------------------------
// Testes
// ---------------------------------------------------------------------------

test('DOM (R2-m4): tela de rota renderiza titulo, progresso e tarefas com botoes', async () => {
  const { ctl, view, root } = mount();
  await ctl.load();
  view.render();
  assert.match(root.textContent, /Rota do dia/);
  assert.match(root.textContent, /Rota rota-1/);
  assert.match(root.textContent, /0 de 2 leituras feitas/);
  assert.match(root.textContent, /unidade u-1/);
  assert.match(root.textContent, /unidade u-2/);
  assert.equal(findAll(root, 'button').some((b) => b.textContent === 'Fazer leitura'), true);
  assert.equal(findAll(root, 'h1')[0].textContent, 'Rota do dia');
});

test('DOM (R2-m4): tela de leitura renderiza rotulos, campo de foto e confirmar', async () => {
  const { ctl, view, root } = mount();
  await ctl.load();
  ctl.nav.openTask('task-1');
  view.render();
  assert.match(root.textContent, /Leitura/);
  assert.match(root.textContent, /Leitura atual do mostrador/);
  assert.match(root.textContent, /Foto da ocorrência \(URI local\)/);
  assert.match(root.textContent, /Anexar foto da ocorrência/);
  assert.match(root.textContent, /Confirmar leitura/);
  assert.equal(findAll(root, 'input').length >= 2, true);
  assert.equal(findAll(root, 'label').some((l) => l.getAttribute('for') === 'campo-leitura-atual'), true);
});

test('DOM (R2-m4): tela de retencoes exibe a secao de divergencias com a entrada do servidor', async () => {
  const entries: DivergenceEntry[] = [
    { taskId: 'task-1', basisDivergent: false, differences: [{ field: 'total', fieldValue: '27.90', serverValue: '31.40' }] },
  ];
  const { ctl, view, root } = mount({ fetchDivergences: async () => entries });
  await ctl.load();
  await ctl.refreshDivergences();
  ctl.nav.go('retidas');
  view.render();
  assert.match(root.textContent, /Divergências campo × servidor/);
  assert.match(root.textContent, /total: campo 27\.90 · servidor 31\.40/);
  assert.match(root.textContent, /Consultar divergências do servidor/);
  // O botao de consulta re-renderiza apos a resposta da porta.
  const btn = buttonWithText(root, 'Consultar divergências do servidor');
  assert.ok(btn);
  btn.click();
  await sleep(10);
  assert.match(root.textContent, /1 conta\(s\) em divergência/);
});

test('DOM (R2-m4): sem a porta, a tela de retencoes declara a fila indisponivel (nada fingido)', async () => {
  const { ctl, view, root } = mount({ fetchDivergences: null });
  await ctl.load();
  ctl.nav.go('retidas');
  view.render();
  assert.match(root.textContent, /indisponível neste aparelho/);
  assert.equal(root.textContent.includes('Nenhuma divergência na fila do servidor.'), false);
});

test('DOM (R2-m4): tela de sincronizacao exibe diretiva consultada e ultima sync', async () => {
  const { ctl, view, root } = mount({ queryDirective: async () => 'BLOCK' as const });
  await ctl.load();
  ctl.nav.go('sincronizacao');
  view.render();
  assert.match(root.textContent, /Sincronização/);
  assert.match(root.textContent, /ainda não consultada nesta sessão/);
  assert.match(root.textContent, /NONE — Nenhuma diretiva do servidor/);
  const btn = buttonWithText(root, 'Consultar diretiva atual');
  assert.ok(btn);
  btn.click();
  await sleep(10);
  assert.match(root.textContent, /Diretiva atual do aparelho: BLOCK/);
  assert.match(root.textContent, /bloqueado/);
  await ctl.syncNow();
  view.render();
  assert.match(root.textContent, /Última sincronização desta sessão/);
});
