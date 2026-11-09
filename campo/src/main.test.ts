/**
 * Testes da entrada do app (R2-M1, fusao FUSAO-R3D-MAIN): `startCampoApp` e
 * `startOrderScreens` em main.ts montam as telas sobre o bootstrap real.
 *
 * ORIGEM E ADAPTACAO (registada): este arquivo nasce dos 2 testes de
 * `main.test.ts` do lote R3-D, ADAPTADOS a API viva da main — divergencia
 * real de assinatura resolvida a favor da main (base canonica):
 *  - R3-D: `startCampoApp(bootstrap, deps)` buscava o `#app` no documento
 *    e recusava com erro nomeado quando ele faltava;
 *  - main viva: `startCampoApp(root, bootstrap, deps)` recebe o root
 *    explicito (3 argumentos) e tem TAMBEM `startOrderScreens(root, deps)`
 *    e o estado honesto da casca via DOMContentLoaded.
 * Os testes abaixo provam, na API viva: (1) a montagem das telas de leitura
 * no root dado, devolvendo controller + view; (2) a montagem substitui o
 * estado "casca carregada" que estivesse no root (replaceChildren);
 * (3) `startOrderScreens` monta as telas de OS (R2-M3). O comportamento
 * "sem #app" do R3-D nao tem equivalente na API viva — quem chama passa o
 * root, e a casca sem montagem declara a espera (texto conferido no
 * artefato vivo `main.ts`, nao executavel aqui sem disparar o listener de
 * DOMContentLoaded registado na importacao).
 *
 * O bootstrap aqui e um duplo de teste (sessao/sync falsos, sem ponte
 * nativa) — o que se prova e a FIACAO. O bootstrap real (PKCE + SQLCipher)
 * so nasce na integracao nativa e nao e fingido aqui. O stub de DOM e o
 * mesmo subconjunto minimo dos demais testes de tela (so de teste).
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { RoutePackage } from './readings/routePackage.ts';
import { SyncEngine, type LocalStore, type SyncTransport } from './sync/syncEngine.ts';
import type { FieldSecurityBridge } from './plugins/fieldSecurity.ts';
import { createSaveReading } from './sync/fieldReadingStore.ts';
import type { CampoBootstrap } from './main.ts';
import { startCampoApp, startOrderScreens } from './main.ts';
import type { FieldOrder } from './ui/orders/models.ts';

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

  addEventListener(): void {}

  getElementById(): StubElement | null {
    return null;
  }
}

(globalThis as unknown as { document: StubDocument }).document = new StubDocument();

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

const PKG: RoutePackage = {
  routeId: 'rota-1',
  reference: '2026-11',
  downloadedAt: '2026-11-08T06:00:00Z',
  occurrences: [],
  tasks: [
    { taskId: 'task-1', unitId: 'u-1', installationId: 'i-1', kind: 'WATER', reference: '2026-11', previousReading: 12000, meterDigits: 5, sequence: 1 },
  ],
};

function makeStore(): LocalStore {
  return {
    async pendingReadings() { return []; },
    async saveReading() {},
    async saveRoute() {},
    async clearRoute() {},
  };
}

function bootstrap(store: LocalStore): CampoBootstrap {
  const transport: SyncTransport = {
    async fetchDirective() { return 'NONE'; },
    async uploadPending(_routeId, pending) {
      return pending.map((p) => ({ taskId: p.taskId, duplicate: false }));
    },
    async downloadRoute() { throw new Error('nao usado'); },
  };
  const security: FieldSecurityBridge = {
    async openEncryptedDatabase() { return { opened: true }; },
    async wipeAll() { return { wiped: true }; },
  };
  return {
    // A sessao real nasce na integracao nativa (PKCE); o boot so usa o
    // sync do bootstrap — a sessao entra aqui como duplo tipado.
    session: {} as unknown as CampoBootstrap['session'],
    sync: new SyncEngine(transport, store, security),
  };
}

function campoDeps(store: LocalStore) {
  return {
    routePackage: PKG,
    store,
    emission: null,
    buildEmissionTask: null,
    saveReading: createSaveReading(store),
    ocrSettings: null,
    ocrEngine: null,
    printer: null,
    fetchDivergences: null,
    queryDirective: null,
    now: () => '2026-11-08T10:00:00Z',
  };
}

const ORDER: FieldOrder = {
  orderId: 'os-1', unitId: 'u-1', taskId: 'task-1', kindLabel: 'Corte',
  unitAddress: 'Rua das Flores, 123', state: 'EN_ROUTE', issuedAt: '2026-11-08T06:00:00Z',
  cutSnapshot: {
    orderId: 'os-1', unitId: 'u-1', taskId: 'task-1', issuedAt: '2026-11-08T06:00:00Z',
    factsAtIssue: { debitState: 'OPEN', contestationOpen: false, noticeGiven: true, prohibitions: [] },
  },
  expectedIdentity: null,
};

// ---------------------------------------------------------------------------
// Testes
// ---------------------------------------------------------------------------

test('R2-M1 (fusao): startCampoApp monta as telas de leitura no root e devolve controller + view', async () => {
  const store = makeStore();
  const root = new StubElement('div');
  const { controller, view } = await startCampoApp(root as unknown as HTMLElement, bootstrap(store), campoDeps(store));
  assert.equal(controller.progress().total, 1);
  assert.equal(typeof view.render, 'function');
  assert.match(root.textContent, /Rota do dia/);
  assert.match(root.textContent, /unidade u-1/);
});

test('R2-M1 (fusao): a montagem substitui o estado honesto da casca que estava no root', async () => {
  const store = makeStore();
  const root = new StubElement('div');
  const status = new StubElement('p');
  status.textContent = 'ZiipSan Campo — casca carregada. As telas de campo aguardam a montagem da integração (startCampoApp).';
  root.replaceChildren(status);
  assert.match(root.textContent, /casca carregada/);
  await startCampoApp(root as unknown as HTMLElement, bootstrap(store), campoDeps(store));
  assert.equal(root.textContent.includes('casca carregada'), false);
  assert.match(root.textContent, /Rota do dia/);
});

test('R2-M3 (fusao): startOrderScreens monta as telas de OS no root e devolve controller + view', async () => {
  const root = new StubElement('div');
  const { controller, view } = await startOrderScreens(root as unknown as HTMLElement, {
    pkg: { orders: [ORDER] },
    getCurrentCutFacts: null,
    now: () => '2026-11-08T10:00:00Z',
  });
  assert.equal(controller.progress().total, 1);
  assert.equal(typeof view.render, 'function');
  assert.match(root.textContent, /Ordens de serviço/);
  assert.match(root.textContent, /Corte · OS os-1 · unidade u-1 · Rua das Flores, 123/);
  assert.match(root.textContent, /Estado: A caminho/);
});
