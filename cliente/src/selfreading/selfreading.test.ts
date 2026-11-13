/**
 * Testes da tela de autoleitura (node --test, padrao de portal.test.ts).
 * FIXTURES DAS SUITES JAVA do nucleo (fonte nomeada em cada bloco):
 *  - SelfReadingIntakeServiceTest.java: UNIT, REF 2026-11, HASH "a"*64,
 *    photoFull foto/1 1200x1000, historico [10,12,14], desfechos
 *    96.00/57.50/PENDING_OCR.
 *  - HumanReviewQueueTest.java: ordenacao (s1,s2,s3).
 *  - ReadingScoreCalculatorTest.java / GeoProofEvaluatorTest.java:
 *    validacoes e fronteiras declaradas.
 * Porta FALSA: regista a submissao e devolve o desfecho fixture.
 */
import assert from 'node:assert/strict';
import { test } from 'node:test';

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

const models = await import('./models.ts');
const screens = await import('./screens.ts');
type Outcome = import('./transport.ts').SelfReadingOutcome;
type Submission = import('./transport.ts').SelfReadingSubmission;
type Config = import('./transport.ts').SelfReadingScoreConfig;

// SelfReadingScoreConfig.proofDefaults() — construcao de prova declarada
// no Javadoc Java (pesos 0.40/0.30/0.15/0.15, limiar 70, 100 m, 50 m).
const PROOF_CONFIG: Config = {
  weightOcr: 0.4, weightCoherence: 0.3, weightPhoto: 0.15, weightGeo: 0.15,
  acceptThreshold: 70, maxDistanceMetres: 100, maxAccuracyMetres: 50,
};
const UNIT = '11111111-1111-4111-8111-111111111111';
const HASH_A = 'a'.repeat(64);
const GEO_ABSENT = { distanceMetres: null, withinMaxDistance: false, accuracySufficient: false, present: false };
const GEO_HERE = { distanceMetres: 0, withinMaxDistance: true, accuracySufficient: true, present: true };

const ACCEPTED_96: Outcome = {
  submissionId: 'sub-demo-1', unitId: UNIT, reference: '2026-11',
  status: 'ACCEPTED_FOR_CRITIQUE', reasons: ['ACCEPTED'],
  score: { total: 96, ocrComponent: 0.9, coherenceComponent: 1, photoComponent: 1, geoComponent: 1 },
  ocrReading: 1012, geo: GEO_HERE,
  handoff: {
    submissionId: 'sub-demo-1', unitId: UNIT, reference: '2026-11',
    currentReading: 1012, previousReading: 1000, measuredConsumption: 12,
    history: [10, 12, 14], scoreTotal: 96, photoArtifactRef: 'foto/1', geoDistanceMetres: 0,
  },
};
const PENDING_OCR_OUTCOME: Outcome = {
  submissionId: 'sub-demo-2', unitId: UNIT, reference: '2026-11',
  status: 'PENDING_OCR', reasons: ['NO_OCR_ADAPTER'],
  score: null, ocrReading: null, geo: GEO_HERE, handoff: null,
};

function fakePort(outcome: Outcome) {
  const calls: Array<{ submission: Submission; config: Config }> = [];
  return {
    calls,
    async evaluate(submission: Submission, config: Config): Promise<Outcome> {
      calls.push({ submission, config });
      return outcome;
    },
  };
}
const tick = () => new Promise((r) => setTimeout(r, 0));
function root(): FakeEl { return new FakeEl('main'); }

function fillValidForm(r: FakeEl): void {
  (r.byId('sr-unit') as FakeEl).value = UNIT;
  (r.byId('sr-reference') as FakeEl).value = '2026-11';
  (r.byId('sr-previous') as FakeEl).value = '1000';
  (r.byId('sr-history') as FakeEl).value = '10, 12, 14';
  (r.byId('sr-photo-ref') as FakeEl).value = 'foto/1';
  (r.byId('sr-photo-hash') as FakeEl).value = HASH_A;
  (r.byId('sr-photo-w') as FakeEl).value = '1200';
  (r.byId('sr-photo-h') as FakeEl).value = '1000';
  (r.byId('sr-geo-lat') as FakeEl).value = '0';
  (r.byId('sr-geo-lon') as FakeEl).value = '0';
  (r.byId('sr-geo-acc') as FakeEl).value = '10';
  (r.byId('sr-geo-at') as FakeEl).value = '2026-11-10T12:00:00Z';
  (r.byId('sr-conn-lat') as FakeEl).value = '0';
  (r.byId('sr-conn-lon') as FakeEl).value = '0';
}

test('aceite 96.00 (IntakeServiceTest): submissao espelha o record e desfecho renderiza score/handoff', async () => {
  const port = fakePort(ACCEPTED_96);
  const r = root();
  screens.renderSelfReading(r as unknown as HTMLElement,
    { port, config: PROOF_CONFIG, newSubmissionId: () => 'sub-demo-1' });
  fillValidForm(r);
  r.buttonByText('Enviar autoleitura').click();
  await tick();
  assert.equal(port.calls.length, 1);
  assert.deepEqual(port.calls[0].submission.photo,
    { artifactRef: 'foto/1', sha256Hex: HASH_A, widthPx: 1200, heightPx: 1000 });
  assert.deepEqual(port.calls[0].submission.history, [10, 12, 14]);
  assert.equal(port.calls[0].submission.previousReading, 1000);
  assert.match(r.textContent, /Aceita para a crítica/);
  assert.match(r.textContent, /Score total: 96,00/);
  assert.match(r.textContent, /Consumo derivado: 12/);
});

test('PENDING_OCR (IntakeServiceTest sem adaptador): pendencia honesta, sem leitura nem score fingidos', async () => {
  const port = fakePort(PENDING_OCR_OUTCOME);
  const r = root();
  screens.renderSelfReading(r as unknown as HTMLElement,
    { port, config: PROOF_CONFIG, newSubmissionId: () => 'sub-demo-2' });
  fillValidForm(r);
  r.buttonByText('Enviar autoleitura').click();
  await tick();
  assert.match(r.textContent, /Aguarda leitura da foto \(OCR\)/);
  assert.match(r.textContent, /Nenhuma leitura é apresentada sem o motor/);
  assert.match(r.textContent, /Leitura da foto: nenhuma leitura extraída/);
  assert.match(r.textContent, /Score: sem score/);
});

test('R41-4-21: precisao GPS vazia com lat/lon NAO vira "perfeita" (0 m) — erro visivel, porta nao chamada; 0 continua valido', async () => {
  const port = fakePort(ACCEPTED_96);
  const r = root();
  screens.renderSelfReading(r as unknown as HTMLElement,
    { port, config: PROOF_CONFIG, newSubmissionId: () => 'sub-r21' });
  fillValidForm(r);
  (r.byId('sr-geo-acc') as FakeEl).value = '';
  r.buttonByText('Enviar autoleitura').click();
  await tick();
  assert.equal(port.calls.length, 0);
  assert.match(r.textContent, /precisão do GPS/);
  // Controlo positivo: precisao 0 e valida (GeoProof: >= 0) e segue.
  (r.byId('sr-geo-acc') as FakeEl).value = '0';
  r.buttonByText('Enviar autoleitura').click();
  await tick();
  assert.equal(port.calls.length, 1);
  assert.equal(port.calls[0].submission.geoProof?.accuracyMetres, 0);
});

test('R41-4-22: leitura declarada nao numerica NAO some em silencio — erro visivel, porta nao chamada', async () => {
  const port = fakePort(ACCEPTED_96);
  const r = root();
  screens.renderSelfReading(r as unknown as HTMLElement,
    { port, config: PROOF_CONFIG, newSubmissionId: () => 'sub-r22' });
  fillValidForm(r);
  (r.byId('sr-declared') as FakeEl).value = 'abc';
  r.buttonByText('Enviar autoleitura').click();
  await tick();
  assert.equal(port.calls.length, 0);
  assert.match(r.textContent, /leitura declarada precisa ser um número válido/i);
});

test('R41-4-22: leitura anterior nao numerica NAO some em silencio — erro visivel, porta nao chamada', async () => {
  const port = fakePort(ACCEPTED_96);
  const r = root();
  screens.renderSelfReading(r as unknown as HTMLElement,
    { port, config: PROOF_CONFIG, newSubmissionId: () => 'sub-r22b' });
  fillValidForm(r);
  (r.byId('sr-previous') as FakeEl).value = 'doze';
  r.buttonByText('Enviar autoleitura').click();
  await tick();
  assert.equal(port.calls.length, 0);
  assert.match(r.textContent, /leitura anterior precisa ser um número válido/i);
});

test('R41-4-23: foto com hash/dimensoes mas sem referencia NAO e descartada em silencio — erro visivel, porta nao chamada', async () => {
  const port = fakePort(ACCEPTED_96);
  const r = root();
  screens.renderSelfReading(r as unknown as HTMLElement,
    { port, config: PROOF_CONFIG, newSubmissionId: () => 'sub-r23' });
  fillValidForm(r);
  (r.byId('sr-photo-ref') as FakeEl).value = '';
  r.buttonByText('Enviar autoleitura').click();
  await tick();
  assert.equal(port.calls.length, 0);
  assert.match(r.textContent, /foto está incompleta/i);
});

test('R41-4-24: regiao de resultado do desfecho tem role=status e aria-live=polite', async () => {
  const port = fakePort(ACCEPTED_96);
  const r = root();
  screens.renderSelfReading(r as unknown as HTMLElement,
    { port, config: PROOF_CONFIG, newSubmissionId: () => 'sub-r24' });
  fillValidForm(r);
  r.buttonByText('Enviar autoleitura').click();
  await tick();
  const liveDivs = r.querySelectorAll('div')
    .filter((d) => d.getAttribute('role') === 'status');
  assert.equal(liveDivs.length, 1);
  assert.equal(liveDivs[0].getAttribute('aria-live'), 'polite');
  assert.match(liveDivs[0].textContent, /Score total: 96,00/);
});

test('R41-4-27: selector do titulo com escopo .selfreading, sem `section h2` global', async () => {
  const fs = await import('node:fs');
  const read = (name: string) => fs.readFileSync(new URL(`./${name}`, import.meta.url), 'utf8');
  const css = read('selfreading.css');
  const screensSrc = read('screens.ts');
  assert.ok(!/(^|\n)section h2 \{/.test(css), 'selfreading.css nao pode ter selector global `section h2`');
  assert.match(css, /\.selfreading h2 \{/);
  // A section renderizada carrega a classe que o selector exige.
  assert.match(screensSrc, /s\.setAttribute\('class', 'selfreading'\)/);
});

test('validacao local: hash invalido bloqueia sem chamar a porta (PhotoArtifact Java)', async () => {
  const port = fakePort(ACCEPTED_96);
  const r = root();
  screens.renderSelfReading(r as unknown as HTMLElement,
    { port, config: PROOF_CONFIG, newSubmissionId: () => 'sub-demo-3' });
  fillValidForm(r);
  (r.byId('sr-photo-hash') as FakeEl).value = 'nao-e-um-hash';
  r.buttonByText('Enviar autoleitura').click();
  await tick();
  assert.equal(port.calls.length, 0);
  assert.match(r.textContent, /64 caracteres hexadecimais/);
});

test('models: validacoes espelham os compactos Java (foto/geo/config/OCR)', () => {
  assert.equal(models.validatePhoto({ artifactRef: 'foto/1', sha256Hex: HASH_A, widthPx: 1200, heightPx: 1000 }), null);
  assert.ok(models.validatePhoto({ artifactRef: 'foto/1', sha256Hex: 'x', widthPx: 1200, heightPx: 1000 }));
  assert.ok(models.validateGeoPoint({ latitude: 91, longitude: 0 }));
  assert.ok(models.validateGeoPoint({ latitude: 0, longitude: 181 }));
  assert.equal(models.validateScoreConfig(PROOF_CONFIG), null);
  // ReadingScoreCalculatorTest.configuracaoInvalidaRecusada: pesos 0.40/0.30/0.15/0.14 nao somam 1.
  assert.ok(models.validateScoreConfig({ ...PROOF_CONFIG, weightGeo: 0.14 }));
  assert.ok(models.validateScoreConfig({ ...PROOF_CONFIG, acceptThreshold: 101 }));
  // OcrExtraction: NO_READING com leitura e contradicao tipificada.
  assert.ok(models.validateOcrExtraction({ status: 'NO_READING', reading: 10, confidence: null, engineId: 'e' }));
  assert.equal(models.validateOcrExtraction({ status: 'NO_READING', reading: null, confidence: null, engineId: 'e' }), null);
});

test('models: fila de revisao ordena como HumanReviewQueueTest (s1,s2,s3)', () => {
  const u1 = '11111111-1111-4111-8111-111111111111';
  const u2 = '22222222-2222-4222-8222-222222222222';
  const s1 = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  const s2 = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  const s3 = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
  const mk = (submissionId: string, unitId: string, reference: string): Outcome => ({
    submissionId, unitId, reference, status: 'PENDING_HUMAN_REVIEW',
    reasons: ['SCORE_BELOW_THRESHOLD'], score: null, ocrReading: null, geo: GEO_ABSENT, handoff: null,
  });
  const sorted = models.sortReviewQueue([mk(s2, u2, '2026-11'), mk(s3, u1, '2026-12'), mk(s1, u1, '2026-11')]);
  assert.deepEqual(sorted.map((o) => o.submissionId), [s1, s2, s3]);
  // R41-4-28: isReviewable removida — so existia no teste, sem guarda
  // consumidora no cliente (ver RELATORIO-grupo-a.md).
});

test('transporte NAO afirma rota HTTP nem motor OCR (P-II7-FIACAO / P-II7-OCR)', async () => {
  const fs = await import('node:fs');
  const src = fs.readFileSync(new URL('./transport.ts', import.meta.url), 'utf8');
  const code = src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  assert.ok(!/api\/v1|\/portal\//.test(code), 'transport.ts nao pode conter rota HTTP');
});
