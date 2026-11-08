/**
 * Modelos de exibicao das telas do app de campo (LOTE 3 / M1) — logica PURA
 * de apresentacao sobre os contratos REAIS da ED-18/ED-19. Nenhum campo e
 * inventado: cada funcao consome somente o que os DTOs existentes expoem.
 *
 * Fontes dos contratos (pacote mesclado ED-18 + ED-19, app `campo`):
 *  - RoutePackage/RouteTask/PendingReading/OccurrenceSnapshot:
 *    src/readings/routePackage.ts (ED-18)
 *  - FieldBillResultDto/EmissionOutcome/OcrVerdictName:
 *    src/field/fieldEmission.ts (ED-19)
 *  - DeviceDirective: src/sync/syncEngine.ts (ED-18)
 *  - evaluateOcr/OcrMode/OcrVerdict: src/ocr/ocrCompare.ts (ED-18)
 *
 * O que os DTOs NAO expoem (endereco da unidade, tarifa, divergencia de
 * campo) NAO aparece aqui — ver pendencias no RELATORIO-APP-CAMPO-TELAS.md.
 */
import type {
  OccurrenceSnapshot,
  PendingReading,
  RoutePackage,
  RouteTask,
} from '../readings/routePackage.ts';
import type { DeviceDirective } from '../sync/syncEngine.ts';
import type { EmissionOutcome, FieldBillResultDto, OcrVerdictName } from '../field/fieldEmission.ts';
import { evaluateOcr, type OcrMode } from '../ocr/ocrCompare.ts';

// ---------------------------------------------------------------------------
// 1. Rota / tarefas do dia
// ---------------------------------------------------------------------------

/** Estado de uma tarefa derivado SOMENTE da fila local real (PendingReading). */
export type TaskState = 'PENDENTE' | 'LIDA';

export interface TaskRow {
  task: RouteTask;
  state: TaskState;
  /** A leitura local desta tarefa, quando existe. */
  pending: PendingReading | null;
}

export interface RouteProgress {
  total: number;
  done: number;
  pending: number;
  rows: TaskRow[];
}

/**
 * Progresso da rota: uma tarefa esta LIDA quando existe PendingReading com o
 * mesmo taskId na fila local (a leitura executada fica na fila ate sincronizar
 * — routePackage.ts). Tarefas ordenadas por `sequence` (campo real do DTO).
 */
export function routeProgress(pkg: RoutePackage, pending: PendingReading[]): RouteProgress {
  const byTask = new Map(pending.map((p) => [p.taskId, p]));
  const rows = [...pkg.tasks]
    .sort((a, b) => a.sequence - b.sequence)
    .map((task) => {
      const p = byTask.get(task.taskId) ?? null;
      return { task, state: (p ? 'LIDA' : 'PENDENTE') as TaskState, pending: p };
    });
  const done = rows.filter((r) => r.state === 'LIDA').length;
  return { total: rows.length, done, pending: rows.length - done, rows };
}

/** Rotulo de identificacao da tarefa: SOMENTE identificadores que o DTO expoe. */
export function taskLabel(task: RouteTask): string {
  const kind = task.kind === 'WATER' ? 'Água' : 'Poço';
  return `${kind} · unidade ${task.unitId} · instalação ${task.installationId}`;
}

// ---------------------------------------------------------------------------
// 2. Leitura de uma unidade
// ---------------------------------------------------------------------------

export interface ReadingDraft {
  /** Texto digitado pelo leiturista (antes de parse). */
  readingText: string;
  occurrenceCode: string | null;
  photoRefs: string[];
  ocrValue: number | null;
}

export interface OcrSettings {
  mode: OcrMode;
  toleranceDigits: number;
}

/**
 * Veredito OCR da leitura usando a regra REAL da ED-18 (evaluateOcr, espelho
 * do Java). Os parametros (mode/tolerance) NAO estao no RoutePackage TS —
 * quem chama injeta as definicoes do tenant (pendencia P-02 do relatorio);
 * sem settings, o veredito e DISABLED (o FieldTaskEmission aceita o valor).
 */
export function ocrVerdictFor(
  task: RouteTask,
  reading: number | null,
  ocrValue: number | null,
  settings: OcrSettings | null,
): OcrVerdictName {
  if (!settings) return 'DISABLED';
  return evaluateOcr(
    settings.mode,
    settings.toleranceDigits,
    task.meterDigits,
    reading,
    ocrValue,
    task.previousReading,
  );
}

/** Parse da leitura digitada: inteiro nao-negativo ou null (vazio/invalido). */
export function parseReading(text: string): number | null {
  const t = text.trim();
  if (t === '') return null;
  if (!/^\d+$/.test(t)) return null;
  const n = Number(t);
  return Number.isSafeInteger(n) ? n : null;
}

export type ReadingValidation =
  | { ok: true; reading: number | null }
  | { ok: false; error: string };

/**
 * Validacao da leitura contra o OccurrenceSnapshot REAL: a ocorrencia manda
 * se leitura e/ou foto sao obrigatorias (requiresReading/requiresPhoto).
 * Sem ocorrencia selecionada, a leitura digitada e obrigatoria (leitura
 * normal de hidrometro — a tarefa e de leitura por desenho da ED-18).
 */
export function validateReading(
  task: RouteTask,
  occurrences: OccurrenceSnapshot[],
  draft: ReadingDraft,
): ReadingValidation {
  const reading = parseReading(draft.readingText);
  if (draft.readingText.trim() !== '' && reading === null) {
    return { ok: false, error: 'Leitura inválida: use somente dígitos do mostrador.' };
  }
  const occ = draft.occurrenceCode
    ? occurrences.find((o) => o.code === draft.occurrenceCode) ?? null
    : null;
  if (draft.occurrenceCode && !occ) {
    return { ok: false, error: `Ocorrência fora do catálogo da rota: ${draft.occurrenceCode}` };
  }
  if (occ) {
    if (occ.requiresReading && reading === null) {
      return { ok: false, error: `A ocorrência ${occ.code} exige a leitura do mostrador.` };
    }
    if (occ.requiresPhoto && draft.photoRefs.length === 0) {
      return { ok: false, error: `A ocorrência ${occ.code} exige foto.` };
    }
  } else if (reading === null) {
    return { ok: false, error: 'Informe a leitura do mostrador ou selecione uma ocorrência.' };
  }
  void task;
  return { ok: true, reading };
}

/**
 * Anexa a referencia (URI local) de UMA foto de ocorrencia ao rascunho.
 *
 * Foto de ocorrencia e REFERENCIA, nao imagem embutida: no contrato real
 * (onDeviceOcr.ts, buildSyncPayload) photoRefs sao URIs locais enviadas por
 * upload separado. A captura por camera NAO tem contrato TS (pendencia
 * P-07); o que a tela oferece e o mesmo caminho manual que o OCR ja usa —
 * o leiturista informa a URI local da foto. URI vazia nao anexa; URI
 * repetida nao duplica. Devolve uma NOVA lista (o rascunho nao e mutado
 * por esta funcao alem da atribuicao de quem chama).
 */
export function addOccurrencePhotoRef(refs: string[], uri: string): string[] {
  const t = uri.trim();
  if (t === '') return [...refs];
  return refs.includes(t) ? [...refs] : [...refs, t];
}

/** A ocorrencia selecionada exige foto? (deriva SO do OccurrenceSnapshot). */
export function occurrenceRequiresPhoto(
  occurrences: OccurrenceSnapshot[],
  occurrenceCode: string | null,
): boolean {
  if (!occurrenceCode) return false;
  return occurrences.find((o) => o.code === occurrenceCode)?.requiresPhoto ?? false;
}

/** Monta o PendingReading (DTO real da ED-18) a partir do rascunho validado. */
export function buildPendingReading(
  task: RouteTask,
  draft: ReadingDraft,
  reading: number | null,
  capturedAt: string,
): PendingReading {
  return {
    taskId: task.taskId,
    reading,
    occurrenceCode: draft.occurrenceCode,
    photoRefs: [...draft.photoRefs],
    ocrValue: draft.ocrValue,
    capturedAt,
  };
}

// ---------------------------------------------------------------------------
// 3. Emissao / conta impressa
// ---------------------------------------------------------------------------

export interface BillRow {
  label: string;
  value: string;
}

/**
 * Linhas de exibicao da conta: SOMENTE os campos que FieldBillResultDto
 * realmente tem (fieldEmission.ts). Valores monetarios chegam como string
 * do artefato de regras — a tela exibe, NAO recalcula nem formata numero.
 */
export function billDisplayRows(result: FieldBillResultDto): BillRow[] {
  return [
    { label: 'Consumo (m³)', value: String(result.consumptionM3) },
    { label: 'Faturado (m³)', value: String(result.billedM3) },
    { label: 'Água', value: result.waterValue },
    { label: 'Taxa fixa', value: result.fixedValue },
    { label: 'Esgoto', value: result.sewerValue },
    { label: 'Débitos', value: result.debitsValue },
    { label: 'Créditos aplicados', value: result.creditsApplied },
    { label: 'Créditos não aplicados', value: result.creditsNotApplied },
    { label: 'Total', value: result.total },
    { label: 'Impressão digital da conta', value: result.fingerprint },
  ];
}

// ---------------------------------------------------------------------------
// 3b. Saude da impressora (ThermalPrinterBridge.health da ED-19)
// ---------------------------------------------------------------------------

/** O DTO real da ponte: fieldEmission.ts (ThermalPrinterBridge.health). */
export interface PrinterHealth {
  headOk: boolean | null;
  batteryPct: number | null;
}

/**
 * Estado de exibicao da saude da impressora na Tela 3. A distincao e
 * honesta por desenho: ponte nao injetada (UNAVAILABLE) nao e o mesmo que
 * ponte presente ainda nao consultada (NOT_CHECKED), e falha da consulta
 * (ERROR) nunca e disfarcada de leitura.
 */
export type PrinterHealthState =
  | { status: 'UNAVAILABLE' }
  | { status: 'NOT_CHECKED' }
  | { status: 'ERROR'; message: string }
  | ({ status: 'READY' } & PrinterHealth);

/** Linhas de exibicao da saude: SOMENTE os campos que health() devolve. */
export function printerHealthDisplay(state: PrinterHealthState): string[] {
  switch (state.status) {
    case 'UNAVAILABLE':
      return ['Saúde da impressora: ponte não injetada nesta integração — verificação indisponível.'];
    case 'NOT_CHECKED':
      return ['Saúde da impressora: ainda não verificada nesta sessão.'];
    case 'ERROR':
      return [`Saúde da impressora: a consulta falhou (${state.message}).`];
    case 'READY':
      return [
        `Cabeçote: ${state.headOk === true ? 'OK' : state.headOk === false ? 'com problema' : 'sem leitura'}`,
        `Bateria: ${state.batteryPct !== null ? `${state.batteryPct}%` : 'sem leitura'}`,
      ];
  }
}

export type OutcomeKind = EmissionOutcome['kind'];

export interface OutcomeDisplay {
  kind: OutcomeKind;
  /** true quando a emissao concluiu com impressao. */
  printed: boolean;
  title: string;
  detail: string;
}

/** Texto do EmissionOutcome REAL devolvido pela fachada FieldEmission. */
export function outcomeDisplay(outcome: EmissionOutcome): OutcomeDisplay {
  switch (outcome.kind) {
    case 'PRINTED':
      return {
        kind: outcome.kind,
        printed: true,
        title: 'Conta emitida e impressa',
        detail: `Total ${outcome.result.total} · tarefa ${outcome.taskId}`,
      };
    case 'OCR_HELD':
      return {
        kind: outcome.kind,
        printed: false,
        title: 'Emissão retida — revisão de OCR',
        detail: 'A crítica por foto (OCR) marcou a leitura para revisão; nada foi calculado nem impresso.',
      };
    case 'CERTIFICATE_MISSING':
      return {
        kind: outcome.kind,
        printed: false,
        title: 'Emissão retida — certificado ausente',
        detail: 'O tipo da conta exige assinatura e o aparelho não apresentou certificado no nível exigido.',
      };
    case 'ARTIFACT_HASH_MISMATCH':
      return {
        kind: outcome.kind,
        printed: false,
        title: 'Emissão bloqueada — artefato divergente',
        detail: 'O hash do artefato de regras no aparelho não confere com o pacote da rota; a rota não emite.',
      };
  }
}

// ---------------------------------------------------------------------------
// 4. Fila de retencoes (o que o contrato de campo expoe como "nao emitido")
// ---------------------------------------------------------------------------

export interface HeldEntry {
  taskId: string;
  kind: Exclude<OutcomeKind, 'PRINTED'>;
  title: string;
  detail: string;
}

/**
 * Fila de retencoes de campo: outcomes da fachada que NAO imprimiram, com o
 * motivo que o proprio contrato expoe (kind do EmissionOutcome).
 *
 * NOTA DE CONTRATO: divergencia de VALOR (campo x servidor) NAO existe no
 * contrato TS de campo — ela vive no backend da ED-19 (DivergenceEvaluator /
 * fila da P2.20). Esta fila mostra apenas retencoes locais reais; a fila de
 * divergencia do servidor e a pendencia P-05 do relatorio.
 */
export class HeldQueue {
  private readonly entries = new Map<string, HeldEntry>();

  /** Registra um outcome: PRINTED resolve a retencao da tarefa; os demais retêm. */
  record(outcome: EmissionOutcome): void {
    if (outcome.kind === 'PRINTED') {
      this.entries.delete(outcome.taskId);
      return;
    }
    const d = outcomeDisplay(outcome);
    this.entries.set(outcome.taskId, {
      taskId: outcome.taskId,
      kind: outcome.kind,
      title: d.title,
      detail: d.detail,
    });
  }

  list(): HeldEntry[] {
    return [...this.entries.values()];
  }

  count(): number {
    return this.entries.size;
  }

  has(taskId: string): boolean {
    return this.entries.has(taskId);
  }
}

// ---------------------------------------------------------------------------
// 4b. Fila de divergencia campo x servidor (R2-m2 / P-05)
// ---------------------------------------------------------------------------

/** Um campo divergente, com os dois valores em texto plano (espelho do
 *  `FieldDifference` da ED-19: `DivergenceEvaluator`). */
export interface FieldDifferenceView {
  field: string;
  fieldValue: string;
  serverValue: string;
}

/**
 * Uma conta na fila de divergencia do servidor (ED-19/P2.20, situacao
 * `DIVERGENT_QUEUED` no backend): a conta impressa em campo (taskId)
 * diverge da recalculada pelo servidor. `basisDivergent` = a base (versao
 * de tarifa/parametros) nao e a esperada; `differences` = os campos de
 * valor que diferem no centavo (vazio = valores iguais, divergencia so
 * de base). A conta impressa NUNCA e alterada pela correcao — a
 * aprovacao gera uma NOVA versao no servidor
 * (`DivergenceApprovalService`); quem aprova nao e quem submeteu.
 *
 * FONTE DOS DADOS: o aparelho nao calcula divergencia — ela e apurada no
 * servidor e entregue pela porta de integracao `fetchDivergences`
 * (controller). Sem a porta, a fila nao existe no aparelho: a tela
 * declara a indisponibilidade em vez de mostrar uma fila vazia fingida.
 */
export interface DivergenceEntry {
  taskId: string;
  basisDivergent: boolean;
  differences: FieldDifferenceView[];
}

/**
 * Fila de divergencias da sessao: substituida inteira a cada consulta a
 * porta (o servidor e a fonte; o aparelho nao acumula nem resolve).
 */
export class DivergenceQueue {
  private readonly entries = new Map<string, DivergenceEntry>();

  /** Substitui a fila pela lista devolvida pelo servidor (copia). */
  replace(entries: DivergenceEntry[]): void {
    this.entries.clear();
    for (const e of entries) {
      this.entries.set(e.taskId, {
        taskId: e.taskId,
        basisDivergent: e.basisDivergent,
        differences: e.differences.map((d) => ({ ...d })),
      });
    }
  }

  list(): DivergenceEntry[] {
    return [...this.entries.values()];
  }

  count(): number {
    return this.entries.size;
  }

  forTask(taskId: string): DivergenceEntry | null {
    return this.entries.get(taskId) ?? null;
  }
}

/** Linhas de exibicao de uma divergencia: titulo + base + cada campo. */
export function divergenceDisplay(entry: DivergenceEntry): string[] {
  const lines = [`Conta da tarefa ${entry.taskId} diverge do servidor.`];
  if (entry.basisDivergent) {
    lines.push('Base divergente: a tarifa/parâmetros usados em campo não são os esperados pelo servidor.');
  }
  for (const d of entry.differences) {
    lines.push(`${d.field}: campo ${d.fieldValue} · servidor ${d.serverValue}`);
  }
  if (!entry.basisDivergent && entry.differences.length === 0) {
    lines.push('Sem detalhe de divergência informado pelo servidor.');
  }
  return lines;
}

// ---------------------------------------------------------------------------
// 5. Sincronizacao
// ---------------------------------------------------------------------------

export interface SyncView {
  pendingCount: number;
  lastRunLabel: string | null;
}

/** Rotulo da DeviceDirective REAL (syncEngine.ts). */
export function directiveLabel(directive: DeviceDirective): string {
  switch (directive) {
    case 'NONE':
      return 'Nenhuma diretiva do servidor';
    case 'RETIRE':
      return 'Aparelho a retirar: envia as pendências e apaga os dados locais';
    case 'BLOCK':
      return 'Aparelho bloqueado pelo servidor: nada é enviado e os dados locais são apagados';
  }
}

export interface LastSyncRun {
  uploaded: number;
  wiped: boolean;
  /** Instante LOCAL da execucao nesta sessao do app (estado de UI, nao do servidor). */
  at: string;
}

/**
 * Visao de sincronizacao. NAO carrega a diretiva atual de proposito:
 * syncRoute devolve somente {uploaded, wiped} (syncEngine.ts) — a diretiva
 * em si nao e recuperavel do resultado (wiped cobre RETIRE e BLOCK), entao
 * nenhum rotulo de diretiva atual e afirmado aqui. Os rotulos de
 * directiveLabel() sao exibidos pela Tela 5 como LEGENDA das diretivas
 * possiveis, sem afirmar qual esta ativa (limite da pendencia P-06).
 */
export function syncView(pendingCount: number, lastRun: LastSyncRun | null): SyncView {
  return {
    pendingCount,
    lastRunLabel: lastRun
      ? `${lastRun.uploaded} leitura(s) enviada(s)${lastRun.wiped ? ' · dados locais apagados (diretiva do servidor)' : ''} · ${lastRun.at}`
      : null,
  };
}

// ---------------------------------------------------------------------------
// 5b. Consulta de diretiva fora da sincronizacao (R2-m3 / P-06)
// ---------------------------------------------------------------------------

/**
 * Estado da consulta de diretiva fora da sync: a diretiva so existe no
 * servidor, entao "consultar" e perguntar a ele pela porta de integracao
 * `queryDirective` (controller). `READY` com diretiva null = o servidor
 * respondeu sem informar diretiva (desconhecida, nao "nenhuma").
 */
export type DirectiveQueryState = 'UNAVAILABLE' | 'NOT_QUERIED' | 'READY' | 'ERROR';

/** Linhas de exibicao da diretiva consultada fora da sincronizacao. */
export function directiveQueryDisplay(
  state: DirectiveQueryState,
  directive: DeviceDirective | null,
  error: string | null,
): string[] {
  switch (state) {
    case 'UNAVAILABLE':
      return ['Diretiva atual: consulta fora da sincronização indisponível nesta integração — o SyncEngine da ED-18 não expõe consulta separada (pendência P-06).'];
    case 'NOT_QUERIED':
      return ['Diretiva atual: ainda não consultada nesta sessão.'];
    case 'ERROR':
      return [`Diretiva atual: a consulta falhou (${error ?? 'erro desconhecido'}).`];
    case 'READY':
      return directive === null
        ? ['Diretiva atual: o servidor não informou diretiva nesta consulta.']
        : [`Diretiva atual do aparelho: ${directive} — ${directiveLabel(directive)}`];
  }
}

// ---------------------------------------------------------------------------
// Navegacao minima entre as telas
// ---------------------------------------------------------------------------

export type ScreenId = 'rota' | 'leitura' | 'emissao' | 'retidas' | 'sincronizacao';

export const SCREEN_TITLES: Record<ScreenId, string> = {
  rota: 'Rota do dia',
  leitura: 'Leitura',
  emissao: 'Emissão da conta',
  retidas: 'Retenções',
  sincronizacao: 'Sincronização',
};

/** Maquina de navegacao pura (historico simples com voltar). */
export class Navigator {
  private currentScreen: ScreenId = 'rota';
  private readonly stack: ScreenId[] = [];
  private selectedTaskId: string | null = null;

  current(): ScreenId {
    return this.currentScreen;
  }

  taskId(): string | null {
    return this.selectedTaskId;
  }

  go(screen: ScreenId, taskId?: string): void {
    if (screen === this.currentScreen && taskId === undefined) return;
    this.stack.push(this.currentScreen);
    this.currentScreen = screen;
    if (taskId !== undefined) this.selectedTaskId = taskId;
  }

  openTask(taskId: string): void {
    this.go('leitura', taskId);
  }

  back(): boolean {
    const prev = this.stack.pop();
    if (prev === undefined) return false;
    this.currentScreen = prev;
    return true;
  }

  canGoBack(): boolean {
    return this.stack.length > 0;
  }
}
