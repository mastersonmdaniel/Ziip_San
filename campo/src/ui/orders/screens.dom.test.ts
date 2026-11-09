/**
 * Teste de renderizacao DOM das telas de OS (R2-M3, fusao FUSAO-R3D-MAIN).
 *
 * ORIGEM E ADAPTACAO (registada): adapta os 2 testes DOM de OS do lote
 * R3-D (`DOM (R3-D R2-M3): ...`, que provavam as telas 6/7 integradas do
 * controller de leitura com as portas `fetchOrders`/`fetchCutFacts`) ao
 * modulo dedicado vivo da main (`ui/orders/*`: pacote de OS nas deps +
 * porta `getCurrentCutFacts`) — divergencia de arquitetura resolvida a
 * favor da main (base canonica). O comportamento provado e o mesmo do
 * aceite R2-M3: a lista mostra as OS do pacote com rotulo e estado; o
 * detalhe mostra o retrato da emissao e a revalidacao contra os fatos
 * atuais; sem a porta de fatos, a revalidacao declara a pendencia
 * (P-ED20-02) em vez de afirmar que pode executar.
 *
 * Stub minimo de DOM, so de teste — o mesmo subconjunto que
 * `ui/screens.dom.test.ts` usa. Nao prova layout, CSS nem WebView real.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { CutFacts } from '../../orders/fieldOrder.ts';
import { OrderScreensController, type OrderScreensDeps } from './controller.ts';
import { OrderScreensView } from './screens.ts';
import type { FieldOrder, FieldOrderPackage } from './models.ts';

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

  focus(): void {}

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
// Fakes (mesmos do controller.test.ts deste modulo)
// ---------------------------------------------------------------------------

const OPEN_OK: CutFacts = { debitState: 'OPEN', contestationOpen: false, noticeGiven: true, prohibitions: [] };

const CUT: FieldOrder = {
  orderId: 'os-1', unitId: 'u-1', taskId: 'task-1', kindLabel: 'Corte',
  state: 'EN_ROUTE', issuedAt: '2026-11-08T06:00:00Z',
  cutSnapshot: {
    orderId: 'os-1', unitId: 'u-1', taskId: 'task-1', issuedAt: '2026-11-08T06:00:00Z',
    factsAtIssue: OPEN_OK,
  },
  expectedIdentity: { unitId: 'u-1', target: 'METER', code: 'HID-123' },
};

const PKG: FieldOrderPackage = { orders: [CUT] };

function mount(over: Partial<OrderScreensDeps> = {}) {
  const ctl = new OrderScreensController({ pkg: PKG, getCurrentCutFacts: null, ...over });
  const root = new StubElement('div');
  const view = new OrderScreensView(root as unknown as HTMLElement, ctl);
  view.render();
  return { ctl, view, root };
}

// ---------------------------------------------------------------------------
// Testes
// ---------------------------------------------------------------------------

test('DOM (fusao R2-M3): lista de OS mostra rotulo e estado; detalhe mostra retrato e revalidacao cancela por quitacao', async () => {
  const { ctl, view, root } = mount({
    getCurrentCutFacts: async () => ({ ...OPEN_OK, debitState: 'PAID' as const }),
  });
  assert.match(root.textContent, /Ordens de serviço/);
  assert.match(root.textContent, /Corte · OS os-1 · unidade u-1/);
  assert.match(root.textContent, /Estado: A caminho/);
  const openBtn = buttonWithText(root, 'Abrir OS');
  assert.ok(openBtn);
  openBtn.click();
  assert.match(root.textContent, /Ordem de serviço/);
  assert.match(root.textContent, /Retrato da emissão: débito aberto/);
  assert.match(root.textContent, /emitida em 2026-11-08T06:00:00Z/);
  const revalidateBtn = buttonWithText(root, 'Revalidar corte');
  assert.ok(revalidateBtn);
  revalidateBtn.click();
  await sleep(10);
  assert.match(root.textContent, /NÃO executar — Débito quitado após a emissão/);
  assert.deepEqual(ctl.cutReasons('os-1'), ['DEBIT_PAID_AFTER_ISSUE']);
});

test('DOM (fusao R2-M3): sem a porta de fatos atuais, a revalidacao declara a pendencia (nada fingido)', async () => {
  const { view, root } = mount({ getCurrentCutFacts: null });
  const openBtn = buttonWithText(root, 'Abrir OS');
  assert.ok(openBtn);
  openBtn.click();
  const revalidateBtn = buttonWithText(root, 'Revalidar corte');
  assert.ok(revalidateBtn);
  revalidateBtn.click();
  await sleep(10);
  assert.match(root.textContent, /Fatos atuais de corte indisponíveis nesta integração \(P-ED20-02\)/);
  assert.equal(root.textContent.includes('pode executar'), false);
  void view;
});
