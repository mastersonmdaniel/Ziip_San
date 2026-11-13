/**
 * Testes do pacote portal-consumidor-telas (node --test, Node 24).
 * Transporte FALSO: registra metodo+URL+payload e devolve FIXTURES
 * FICTICIAS DE DEMONSTRACAO (nenhum dado real). DOM minimo proprio para
 * provar renderizacao dos estados e dos campos das fixtures.
 */
import assert from 'node:assert/strict';
import { test } from 'node:test';

// ---------- DOM minimo ----------
class FakeEl {
  tagName: string;
  children: FakeEl[] = [];
  attrs = new Map<string, string>();
  listeners = new Map<string, (() => void)[]>();
  value = '';
  private text = '';
  style = { setProperty: (_n: string, _v: string) => undefined };
  constructor(tag: string) { this.tagName = tag.toUpperCase(); }
  set textContent(v: string) { this.text = v; if (v === '') this.children = []; }
  get textContent(): string {
    return this.text + this.children.map((c) => c.textContent).join('');
  }
  setAttribute(n: string, v: string) { this.attrs.set(n, v); }
  getAttribute(n: string) { return this.attrs.get(n) ?? null; }
  removeAttribute(n: string) { this.attrs.delete(n); }
  appendChild(c: FakeEl) { this.children.push(c); return c; }
  addEventListener(n: string, fn: () => void) {
    this.listeners.set(n, [...(this.listeners.get(n) ?? []), fn]);
  }
  click() { for (const fn of this.listeners.get('click') ?? []) fn(); }
  focus() { /* no-op */ }
  querySelectorAll(tag: string): FakeEl[] {
    const out: FakeEl[] = [];
    const walk = (e: FakeEl) => { for (const c of e.children) { if (c.tagName === tag.toUpperCase()) out.push(c); walk(c); } };
    walk(this); return out;
  }
  querySelector(tag: string): FakeEl | null { return this.querySelectorAll(tag)[0] ?? null; }
  findAll(tag: string): FakeEl[] { return this.querySelectorAll(tag); }
  byId(id: string): FakeEl | null {
    let found: FakeEl | null = null;
    const walk = (e: FakeEl) => { for (const c of e.children) { if (c.getAttribute('id') === id) found = c; walk(c); } };
    walk(this); return found;
  }
  buttonByText(text: string): FakeEl {
    const b = this.querySelectorAll('button').find((x) => x.textContent === text);
    assert.ok(b, `botao nao encontrado: ${text}`);
    return b;
  }
}
const fakeDocument = {
  createElement: (tag: string) => new FakeEl(tag),
  documentElement: new FakeEl('html'),
};
(globalThis as unknown as { document: unknown }).document = fakeDocument;

const { PortalClient } = await import('./transport.ts');
const screens = await import('./screens.ts');
const models = await import('./models.ts');

// ---------- Transporte falso + fixtures de DEMONSTRACAO ----------
interface Call { method: string; url: string; body: unknown }
function fakeHttp(responder: (url: string) => unknown) {
  const calls: Call[] = [];
  return {
    calls,
    async request<T>(method: 'GET' | 'POST', url: string, body?: unknown): Promise<T> {
      calls.push({ method, url, body });
      const v = responder(url);
      if (v instanceof Error) throw v;
      return v as T;
    },
  };
}
const tick = () => new Promise((r) => setTimeout(r, 0));
function root(): FakeEl { return new FakeEl('main'); }

// FIXTURES FICTICIAS DE DEMONSTRACAO:
const GUEST = { amount: 87.5, dueDate: '2026-11-20', referenceMonth: '2026-10', digitableLine: 'DEMO-LINHA-001', pixCopyPaste: 'DEMO-PIX-001' };
const BILLS = [{ billId: 'b-demo-1', reference: '2026-10', dueDate: '2026-11-20', amount: 87.5, status: 'PENDING' }];

test('visitante: POST exato /api/v1/portal/guest/invoice e 5 campos renderizados', async () => {
  const http = fakeHttp(() => GUEST);
  const r = root();
  screens.renderGuest(r as unknown as HTMLElement, new PortalClient(http));
  (r.byId('guest-code') as FakeEl).value = 'COD-DEMO';
  (r.byId('guest-suffix') as FakeEl).value = '123';
  r.buttonByText('Consultar fatura').click();
  await tick();
  assert.equal(http.calls.length, 1);
  assert.equal(http.calls[0].method, 'POST');
  assert.equal(http.calls[0].url, '/api/v1/portal/guest/invoice');
  assert.deepEqual(http.calls[0].body, { linkingCode: 'COD-DEMO', documentSuffix: '123' });
  assert.match(r.textContent, /DEMO-LINHA-001/);
  assert.match(r.textContent, /DEMO-PIX-001/);
  assert.match(r.textContent, /2026-10/);
});

test('visitante: erro do servidor aparece no estado (sem mensagem inventada)', async () => {
  const http = fakeHttp(() => new Error('Nenhuma fatura em aberto para o codigo informado.'));
  const r = root();
  screens.renderGuest(r as unknown as HTMLElement, new PortalClient(http));
  (r.byId('guest-code') as FakeEl).value = 'X';
  (r.byId('guest-suffix') as FakeEl).value = '9';
  r.buttonByText('Consultar fatura').click();
  await tick();
  assert.match(r.textContent, /Erro: Nenhuma fatura em aberto/);
});

test('faturas: GET exato com ?unidade= e lista renderizada; vazio declarado', async () => {
  const http = fakeHttp(() => BILLS);
  const r = root();
  screens.renderBills(r as unknown as HTMLElement, new PortalClient(http));
  (r.byId('bills-unit') as FakeEl).value = 'u-demo';
  r.buttonByText('Listar faturas').click();
  await tick();
  assert.equal(http.calls[0].method, 'GET');
  assert.equal(http.calls[0].url, '/portal/faturas?unidade=u-demo');
  assert.match(r.textContent, /PENDING/);

  const http2 = fakeHttp(() => []);
  const r2 = root();
  screens.renderBills(r2 as unknown as HTMLElement, new PortalClient(http2));
  (r2.byId('bills-unit') as FakeEl).value = 'u-demo';
  r2.buttonByText('Listar faturas').click();
  await tick();
  assert.match(r2.textContent, /Nenhuma fatura encontrada\./);
});

test('segunda via: GET BFF, POST pix e POST pdf com URLs exatas', async () => {
  const http = fakeHttp((url) =>
    url.endsWith('/segunda-via') ? 'DEMO-DOC-REF' : url.endsWith('/pix') ? { pixCopyPaste: 'DEMO-PIX-2VIA' } : new ArrayBuffer(4));
  const r = root();
  screens.renderSecondCopy(r as unknown as HTMLElement, new PortalClient(http));
  (r.byId('sc-bill') as FakeEl).value = 'b-demo-1';
  (r.byId('sc-unit') as FakeEl).value = 'u-demo';
  r.buttonByText('Segunda via (documento)').click(); await tick();
  r.buttonByText('Pix copia e cola').click(); await tick();
  assert.equal(http.calls[0].url, '/portal/faturas/b-demo-1/segunda-via');
  assert.equal(http.calls[0].method, 'GET');
  assert.equal(http.calls[1].url, '/api/v1/portal/units/u-demo/second-copy/pix');
  assert.equal(http.calls[1].method, 'POST');
  assert.match(r.textContent, /DEMO-PIX-2VIA/);
  const direct = new PortalClient(http);
  await direct.secondCopyPdf('u-demo');
  assert.equal(http.calls[2].url, '/api/v1/portal/units/u-demo/second-copy');
  assert.equal(http.calls[2].method, 'POST');
});

test('explicacao: GET exato e fatores/fullText renderizados', async () => {
  const http = fakeHttp(() => ({ factors: [{ code: 'BAND', text: 'Faixa DEMO atingida.' }], fullText: 'Explicacao DEMO completa.' }));
  const r = root();
  screens.renderExplanation(r as unknown as HTMLElement, new PortalClient(http));
  (r.byId('ex-bill') as FakeEl).value = 'b-demo-1';
  r.buttonByText('Ver explicação').click(); await tick();
  assert.equal(http.calls[0].url, '/portal/faturas/b-demo-1/explicacao');
  assert.match(r.textContent, /Faixa DEMO atingida\./);
  assert.match(r.textContent, /Explicacao DEMO completa\./);
});

test('simulacao: POSTs exatos, aviso "nao cria acordo" e valores renderizados', async () => {
  const http = fakeHttp((url) => url === '/portal/simulacao/fatura'
    ? { consumptionM3: 10, waterAmount: 50, sewageAmount: 40, totalAmount: 90, simulationOnly: true }
    : { totalDebt: 300, installments: 3, monthlyRate: 0, amounts: [100, 100, 100], simulationOnly: true });
  const r = root();
  screens.renderSimulation(r as unknown as HTMLElement, new PortalClient(http));
  assert.match(r.textContent, /não cria acordo/);
  (r.byId('sim-cons') as FakeEl).value = '10';
  (r.byId('sim-min') as FakeEl).value = '20';
  (r.byId('sim-band-upto') as FakeEl).value = '10';
  (r.byId('sim-band-price') as FakeEl).value = '3';
  (r.byId('sim-sewage') as FakeEl).value = '80';
  r.buttonByText('Simular fatura').click(); await tick();
  assert.equal(http.calls[0].method, 'POST');
  assert.equal(http.calls[0].url, '/portal/simulacao/fatura');
  assert.deepEqual(http.calls[0].body, { consumptionM3: 10, tariff: { minimumFee: 20, bands: [{ upToM3: 10, pricePerM3: 3 }], sewagePercent: 80 } });
  assert.match(r.textContent, /Total simulado: R\$ 90,00/);
  (r.byId('sim-debt') as FakeEl).value = '300';
  (r.byId('sim-inst') as FakeEl).value = '3';
  (r.byId('sim-rate') as FakeEl).value = '0';
  r.buttonByText('Simular parcelamento').click(); await tick();
  assert.equal(http.calls[1].url, '/portal/simulacao/parcelamento');
  assert.deepEqual(http.calls[1].body, { totalDebt: 300, installments: 3, monthlyRate: 0 });
  assert.match(r.textContent, /Parcela 3: R\$ 100,00/);
});

test('certidao: GET exato ?ano= e campos do Issued renderizados', async () => {
  const http = fakeHttp(() => ({ kind: 'CERTIFICATE', documentType: 'ANNUAL_SETTLEMENT_CERTIFICATE', documentNumber: 'DEMO-CERT-1', issuedOn: '2026-11-09', signatureLevel: 'SIMPLE', verificationCode: 'DEMO-COD', qrPayload: 'https://demo.invalid/validar?tipo=X' }));
  const r = root();
  screens.renderCertificate(r as unknown as HTMLElement, new PortalClient(http));
  (r.byId('cert-year') as FakeEl).value = '2025';
  r.buttonByText('Emitir certidão').click(); await tick();
  assert.equal(http.calls[0].url, '/portal/certidao-quitacao-anual?ano=2025');
  assert.match(r.textContent, /DEMO-CERT-1/);
  assert.match(r.textContent, /DEMO-COD/);
});

test('validar: GET exato com tipo/numero/codigo e desfecho renderizado', async () => {
  const http = fakeHttp(() => ({ valid: true }));
  const r = root();
  screens.renderValidate(r as unknown as HTMLElement, new PortalClient(http));
  (r.byId('val-tipo') as FakeEl).value = 'ANNUAL_SETTLEMENT_CERTIFICATE';
  (r.byId('val-numero') as FakeEl).value = 'DEMO-CERT-1';
  (r.byId('val-codigo') as FakeEl).value = 'DEMO-COD';
  r.buttonByText('Validar').click(); await tick();
  assert.equal(http.calls[0].url, '/portal/validar?tipo=ANNUAL_SETTLEMENT_CERTIFICATE&numero=DEMO-CERT-1&codigo=DEMO-COD');
  assert.match(r.textContent, /Documento válido\./);
});

test('relato: foto obrigatoria bloqueia sem chamar a rede; POST exato com contato nulo', async () => {
  const http = fakeHttp(() => ({ protocolId: 'proto-demo-1', groupedIntoExisting: false, networkOccurrenceCreated: false }));
  const r = root();
  screens.renderReport(r as unknown as HTMLElement, new PortalClient(http));
  // Navegador real: <select> sem escolha assume a 1a opcao (LEAK); o DOM minimo declara isso.
  (r.byId('rep-category') as FakeEl).value = 'LEAK';
  (r.byId('rep-location') as FakeEl).value = 'Rua DEMO, 1';
  (r.byId('rep-desc') as FakeEl).value = 'Vazamento DEMO';
  r.buttonByText('Enviar relato').click(); await tick();
  assert.equal(http.calls.length, 0);
  assert.match(r.textContent, /foto é obrigatória/);
  (r.byId('rep-photo') as FakeEl).value = 'foto-demo-ref';
  r.buttonByText('Enviar relato').click(); await tick();
  assert.equal(http.calls.length, 1);
  assert.equal(http.calls[0].method, 'POST');
  assert.equal(http.calls[0].url, '/portal/relatos');
  assert.deepEqual(http.calls[0].body, { category: 'LEAK', locationText: 'Rua DEMO, 1', photoRef: 'foto-demo-ref', contactOrNull: null, description: 'Vazamento DEMO' });
  assert.match(r.textContent, /proto-demo-1/);
});

test('abastecimento: GET exato ?endereco= e StatusEntry renderizada (motivo so quando presente)', async () => {
  const http = fakeHttp(() => [{ locality: 'Bairro DEMO', sector: 'S1', route: 'R1', start: '2026-11-09T08:00:00', expectedReturn: '2026-11-09T18:00:00', reasonOrNull: null, phase: 'ACTIVE' }]);
  const r = root();
  screens.renderOutage(r as unknown as HTMLElement, new PortalClient(http));
  (r.byId('out-addr') as FakeEl).value = 'Rua DEMO';
  r.buttonByText('Consultar').click(); await tick();
  assert.equal(http.calls[0].url, '/portal/abastecimento?endereco=Rua%20DEMO');
  assert.match(r.textContent, /Bairro DEMO/);
  assert.match(r.textContent, /ACTIVE/);
});

test('agendamento: GET janelas exato, POST exato e referencia renderizada', async () => {
  const http = fakeHttp((url) => url === '/portal/agendamento/janelas'
    ? [{ windowId: 'w-demo', start: '2026-11-12T09:00:00', freeSlots: 2 }]
    : 'booking-demo-ref');
  const r = root();
  screens.renderScheduling(r as unknown as HTMLElement, new PortalClient(http));
  r.buttonByText('Ver janelas').click(); await tick();
  assert.equal(http.calls[0].method, 'GET');
  assert.equal(http.calls[0].url, '/portal/agendamento/janelas');
  assert.match(r.textContent, /w-demo/);
  (r.byId('sch-os') as FakeEl).value = 'os-demo';
  (r.byId('sch-window') as FakeEl).value = 'w-demo';
  (r.byId('sch-start') as FakeEl).value = '2026-11-12T09:00:00';
  r.buttonByText('Agendar').click(); await tick();
  assert.equal(http.calls[1].method, 'POST');
  assert.equal(http.calls[1].url, '/portal/agendamento');
  assert.deepEqual(http.calls[1].body, { serviceOrderId: 'os-demo', windowId: 'w-demo', start: '2026-11-12T09:00:00' });
  assert.match(r.textContent, /booking-demo-ref/);
});

test('models: validacoes espelham os services (relato/simulacao/certidao)', () => {
  assert.ok(models.validateReport({ category: 'LEAK', locationText: 'x', photoRef: '', contactOrNull: null, description: 'd' }));
  assert.equal(models.validateReport({ category: 'LEAK', locationText: 'x', photoRef: 'f', contactOrNull: null, description: 'd' }), null);
  assert.ok(models.validateInstallments({ totalDebt: 0, installments: 1, monthlyRate: 0 }));
  assert.equal(models.validateInstallments({ totalDebt: 10, installments: 1, monthlyRate: 0 }), null);
  // Correcao R-PORTAL-02: a fonte so exige ano nao-nulo — o caso recusado
  // passa a ser o ano ausente/invalido (NaN), nao o ano 0 da faixa inventada.
  assert.ok(models.validateYear(Number.NaN));
  assert.equal(models.validateYear(2025), null);
});

test('models correcao R-PORTAL-02: validateYear sem faixa inventada (fonte so exige ano nao-nulo)', () => {
  // AnnualSettlementCertificate.issue(...) so recusa year == null; anos fora
  // de 2000-2100 NAO podem ser recusados localmente (a faixa nao tinha fonte).
  assert.equal(models.validateYear(1999), null);
  assert.equal(models.validateYear(2101), null);
  assert.equal(models.validateYear(2025), null);
  // Ano ausente/invalido na tela (NaN / nao-inteiro) continua recusado.
  assert.ok(models.validateYear(Number.NaN));
  assert.ok(models.validateYear(2025.5));
  assert.ok(models.validateYear(0.5));
});

test('models correcao R-PORTAL-03: validateBillSimulation rejeita pricePerM3 negativo (TariffBand)', () => {
  const base = { consumptionM3: 10, tariff: { minimumFee: 20, bands: [{ upToM3: 10, pricePerM3: 3 }], sewagePercent: 80 } };
  assert.equal(models.validateBillSimulation(base), null);
  // TariffBand da ED-38: pricePerM3.signum() < 0 -> IllegalArgumentException.
  assert.ok(models.validateBillSimulation({
    ...base, tariff: { ...base.tariff, bands: [{ upToM3: 10, pricePerM3: -0.01 }] },
  }));
  // Preco zero continua aceito (a fonte so rejeita negativo).
  assert.equal(models.validateBillSimulation({
    ...base, tariff: { ...base.tariff, bands: [{ upToM3: 10, pricePerM3: 0 }] },
  }), null);
});

test('a11y correcao R-PORTAL-04: aria-current aplicado, navegacao e link PDF com alvo >= 48px', async () => {
  const fs = await import('node:fs');
  const read = (name: string) => fs.readFileSync(new URL(`./${name}`, import.meta.url), 'utf8');
  const boot = read('boot.ts');
  const css = read('portal.css');
  const screensSrc = read('screens.ts');
  // boot.show() APLICA aria-current="page" ao botao ativo (nao so remove).
  assert.match(boot, /setAttribute\('aria-current', 'page'\)/);
  // Botoes de navegacao do boot usam .portal-action (regra de 48px do CSS).
  assert.match(boot, /setAttribute\('class', 'portal-action'\)/);
  assert.match(css, /\.portal-action \{\s*\n?\s*min-height: 48px; min-width: 48px;/);
  // Link "Abrir PDF" usa .portal-action e o CSS o torna caixa (inline-flex).
  assert.match(screensSrc, /h\('a', 'Abrir PDF da segunda via'\);\s*\n\s*\/\/[^\n]*\n\s*a\.setAttribute\('class', 'portal-action'\)/);
  assert.match(css, /a\.portal-action \{ display: inline-flex;/);
});

test('transporte NAO contem rota do centralizador (nao provada)', async () => {
  const src = await import('node:fs').then((fs) => fs.readFileSync(new URL('./transport.ts', import.meta.url), 'utf8'));
  assert.ok(!/portal\/centralizador/.test(src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '')),
    'transport.ts nao pode conter chamada a /portal/centralizador');
});

// ---------- Centralizador (P-PORTAL-01: ecra + porta tipada) ----------
// Fixtures: valores da suite Java CentralizerViewServiceTest (matriculas
// 1001/1002, valores 100.00/200.00, agrupadora AGR-2026-06-X-1 de
// 2026-06, total 300.00, 2 membros) e o CSV exacto que
// CentralizerViewService.exportUnitsCsv monta (cabecalho + ';').
// Identificadores sao ficticios de demonstracao, como nas demais fixtures.
import type {
  CentralizerPort,
  CentralizerSession,
  GroupedBillView,
  UnitSummary,
} from './transport.ts';

const CENT_UNITS: UnitSummary[] = [
  { unitId: 'u-demo-1', registration: '1001', openAmount: 100 },
  { unitId: 'u-demo-2', registration: '1002', openAmount: 200 },
];
const CENT_CSV = 'unidade;matricula;valor_em_aberto\n'
  + 'u-demo-1;1001;100.00\nu-demo-2;1002;200.00\n';
const CENT_BILL: GroupedBillView = {
  code: 'AGR-2026-06-X-1', reference: '2026-06', total: 300, memberCount: 2,
};
const CENT_SESSION: CentralizerSession = {
  tenantId: 'tenant-demo', consumerSubject: 'sujeito',
  centralizerCustomerId: 'centralizador-demo', channel: 'GOV_BR',
};

interface CentCall { op: string; args: unknown[] }
function fakeCentralizerPort(over: Partial<CentralizerPort> = {}) {
  const calls: CentCall[] = [];
  const port: CentralizerPort = {
    async units(...a) { calls.push({ op: 'units', args: a }); return over.units ? over.units(...a) : CENT_UNITS; },
    async exportUnitsCsv(...a) { calls.push({ op: 'exportUnitsCsv', args: a }); return over.exportUnitsCsv ? over.exportUnitsCsv(...a) : CENT_CSV; },
    async groupedBill(...a) { calls.push({ op: 'groupedBill', args: a }); return over.groupedBill ? over.groupedBill(...a) : CENT_BILL; },
  };
  return { port, calls };
}

test('centralizador: unidades — args exactos (sessao + canal) e lista renderizada', async () => {
  const { port, calls } = fakeCentralizerPort();
  const r = root();
  screens.renderCentralizer(r as unknown as HTMLElement, { port, session: CENT_SESSION });
  r.buttonByText('Listar unidades vinculadas').click(); await tick();
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0], {
    op: 'units',
    args: ['tenant-demo', 'sujeito', 'centralizador-demo', 'GOV_BR'],
  });
  assert.match(r.textContent, /Matrícula 1001 — unidade u-demo-1 — em aberto R\$ 100,00/);
  assert.match(r.textContent, /Matrícula 1002 — unidade u-demo-2 — em aberto R\$ 200,00/);
});

test('centralizador: lista vazia declarada (sem unidades inventadas)', async () => {
  const { port } = fakeCentralizerPort({ units: async () => [] });
  const r = root();
  screens.renderCentralizer(r as unknown as HTMLElement, { port, session: CENT_SESSION });
  r.buttonByText('Listar unidades vinculadas').click(); await tick();
  assert.match(r.textContent, /Nenhuma unidade vinculada\./);
});

test('centralizador: CSV exibido e EXACTAMENTE o da porta, com link de descarga', async () => {
  const { port, calls } = fakeCentralizerPort();
  const r = root();
  screens.renderCentralizer(r as unknown as HTMLElement, { port, session: CENT_SESSION });
  r.buttonByText('Exportar unidades (CSV)').click(); await tick();
  assert.deepEqual(calls[0], {
    op: 'exportUnitsCsv',
    args: ['tenant-demo', 'sujeito', 'centralizador-demo', 'GOV_BR'],
  });
  assert.ok(r.textContent.includes(CENT_CSV), 'CSV renderizado sem remontagem');
  const links = r.findAll('a');
  assert.equal(links.length, 1);
  assert.equal(links[0].textContent, 'Baixar CSV das unidades');
  assert.equal(links[0].getAttribute('download'), 'unidades-centralizador.csv');
});

test('centralizador: segunda exportacao CSV revoga a URL da anterior (R41-4-32)', async () => {
  // Controlo negativo: sem a cura, nenhuma URL seria revogada e cada
  // exportacao vazaria um object URL. A assercao usa inclusao (nao
  // igualdade estrita da lista): o modulo guarda UMA url anterior, e um
  // teste anterior deste ficheiro ja exportou uma vez.
  const created: string[] = [];
  const revoked: string[] = [];
  const origCreate = URL.createObjectURL;
  const origRevoke = URL.revokeObjectURL;
  URL.createObjectURL = (blob: Blob | MediaSource) => {
    const u = origCreate(blob);
    created.push(u);
    return u;
  };
  URL.revokeObjectURL = (u: string) => { revoked.push(u); origRevoke(u); };
  try {
    const { port } = fakeCentralizerPort();
    const r = root();
    screens.renderCentralizer(r as unknown as HTMLElement, { port, session: CENT_SESSION });
    r.buttonByText('Exportar unidades (CSV)').click(); await tick();
    r.buttonByText('Exportar unidades (CSV)').click(); await tick();
    assert.equal(created.length, 2, 'duas exportacoes criam duas URLs');
    assert.ok(revoked.includes(created[0]), 'a URL da 1a exportacao foi revogada na 2a');
    assert.ok(!revoked.includes(created[1]), 'a URL da 2a exportacao continua valida');
  } finally {
    URL.createObjectURL = origCreate;
    URL.revokeObjectURL = origRevoke;
  }
});

test('centralizador: fatura agrupada — competencia como 5o argumento e 4 campos renderizados', async () => {
  const { port, calls } = fakeCentralizerPort();
  const r = root();
  screens.renderCentralizer(r as unknown as HTMLElement, { port, session: CENT_SESSION });
  (r.byId('cent-reference') as FakeEl).value = '2026-06';
  r.buttonByText('Ver fatura agrupada').click(); await tick();
  assert.deepEqual(calls[0], {
    op: 'groupedBill',
    args: ['tenant-demo', 'sujeito', 'centralizador-demo', '2026-06', 'GOV_BR'],
  });
  assert.match(r.textContent, /Código: AGR-2026-06-X-1/);
  assert.match(r.textContent, /Competência: 2026-06/);
  assert.match(r.textContent, /Total: R\$ 300,00/);
  assert.match(r.textContent, /Unidades na fatura agrupada: 2/);
});

test('centralizador: competencia fora do YearMonth bloqueia sem chamar a porta', async () => {
  const { port, calls } = fakeCentralizerPort();
  const r = root();
  screens.renderCentralizer(r as unknown as HTMLElement, { port, session: CENT_SESSION });
  for (const bad of ['2026-13', '2026-00', '06/2026', '']) {
    (r.byId('cent-reference') as FakeEl).value = bad;
    r.buttonByText('Ver fatura agrupada').click(); await tick();
    assert.match(r.textContent, /Informe a competência \(AAAA-MM\)\./);
  }
  assert.equal(calls.length, 0);
});

test('centralizador: sessao incompleta bloqueia as tres operacoes sem chamar a porta', async () => {
  const { port, calls } = fakeCentralizerPort();
  const r = root();
  const incomplete: CentralizerSession = { tenantId: '', consumerSubject: 'sujeito', centralizerCustomerId: 'centralizador-demo' };
  screens.renderCentralizer(r as unknown as HTMLElement, { port, session: incomplete });
  r.buttonByText('Listar unidades vinculadas').click(); await tick();
  r.buttonByText('Exportar unidades (CSV)').click(); await tick();
  (r.byId('cent-reference') as FakeEl).value = '2026-06';
  r.buttonByText('Ver fatura agrupada').click(); await tick();
  assert.equal(calls.length, 0);
  assert.match(r.textContent, /Tenant, consumidor e centralizador são obrigatórios\./);
});

test('centralizador: recusa da porta (guard) aparece no estado, sem mensagem inventada', async () => {
  // Mensagem literal do ApiException.Forbidden de CentralizerViewService.requireAccess.
  const forbidden = new Error('Conta do portal nao e a do orgao centralizador/grande cliente.');
  const { port } = fakeCentralizerPort({ units: async () => { throw forbidden; } });
  const r = root();
  screens.renderCentralizer(r as unknown as HTMLElement, { port, session: CENT_SESSION });
  r.buttonByText('Listar unidades vinculadas').click(); await tick();
  assert.match(r.textContent, /Erro: Conta do portal nao e a do orgao centralizador\/grande cliente\./);
});

test('centralizador sem porta da fiacao: estado honesto [A DEFINIR] e nenhuma operacao', () => {
  const r = root();
  screens.renderCentralizer(r as unknown as HTMLElement);
  assert.match(r.textContent, /rota HTTP \[A DEFINIR\]/);
  assert.match(r.textContent, /P-PORTAL-01/);
  assert.equal(r.querySelectorAll('button').length, 0);
});

test('centralizador sem porta: campo de competencia NAO e renderizado (R41-4-33)', () => {
  // Controlo negativo: antes da cura o campo existia sem porta, inerte
  // (nenhum botao o consumia).
  const r = root();
  screens.renderCentralizer(r as unknown as HTMLElement);
  assert.equal(r.byId('cent-reference'), null);
});

test('centralizador com porta: campo de competencia renderizado (R41-4-33)', () => {
  const { port } = fakeCentralizerPort();
  const r = root();
  screens.renderCentralizer(r as unknown as HTMLElement, { port, session: CENT_SESSION });
  assert.ok(r.byId('cent-reference'), 'campo presente no ramo com porta');
});

test('models centralizador: validadores espelham requireAccess e groupedBill do service', () => {
  assert.equal(models.validateCentralizerSession(CENT_SESSION), null);
  assert.ok(models.validateCentralizerSession({ tenantId: ' ', consumerSubject: 's', centralizerCustomerId: 'c' }));
  assert.ok(models.validateCentralizerSession({ tenantId: 't', consumerSubject: '', centralizerCustomerId: 'c' }));
  assert.ok(models.validateCentralizerSession({ tenantId: 't', consumerSubject: 's', centralizerCustomerId: ' ' }));
  assert.equal(models.validateCentralizerReference('2026-06'), null);
  assert.equal(models.validateCentralizerReference(' 2026-12 '), null);
  assert.ok(models.validateCentralizerReference('2026-13'));
  assert.ok(models.validateCentralizerReference('2026-00'));
  assert.ok(models.validateCentralizerReference('2026-6'));
  assert.ok(models.validateCentralizerReference(''));
});

test('boot: tela centralizador ligada na navegacao (P-PORTAL-01)', async () => {
  const fs = await import('node:fs');
  const boot = fs.readFileSync(new URL('./boot.ts', import.meta.url), 'utf8');
  assert.match(boot, /renderCentralizer/);
  assert.match(boot, /'centralizador'/);
  // A ligacao e pela porta tipada (deps opcionais), nunca pelo PortalClient HTTP.
  assert.match(boot, /centralizer\?: CentralizerScreenDeps/);
});
