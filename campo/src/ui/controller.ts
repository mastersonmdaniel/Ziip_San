/**
 * Controlador das telas do app de campo (LOTE 3 / M1): orquestra os contratos
 * REAIS da ED-18/ED-19 sem alterar nenhum deles.
 *
 * Dependencias reais consumidas:
 *  - LocalStore.pendingReadings (ED-18, src/sync/syncEngine.ts)
 *  - SyncEngine.syncRoute (ED-18)
 *  - FieldEmission.emit / reprint / retainedBills (ED-19, src/field/fieldEmission.ts)
 *  - OnDeviceOcrEngine.recognizeDigits (ED-18, src/ocr/onDeviceOcr.ts)
 *  - evaluateOcr via models.ocrVerdictFor (ED-18, src/ocr/ocrCompare.ts)
 *
 * PORTAS DE INTEGRACAO (nao sao contratos de backend — sao a fiacao que a
 * integracao precisa entregar; enquanto nulas, a tela mostra a pendencia
 * nomeada em vez de fingir funcionamento — ver RELATORIO, P-03/P-04):
 *  - buildEmissionTask: montar o FieldTaskEmission (inputJson tarifario,
 *    templateId, documentJson, nivel de assinatura e certificado) exige o
 *    retrato de tarifa/versoes que o RoutePackage TS da ED-18 NAO carrega
 *    (no Java da ED-19 ele existe: FieldTariffSnapshot — a porta TS nao).
 *    A MONTAGEM agora existe em TS (field/fieldTariffSnapshot.ts:
 *    espelho do record Java + formato do fixture da ED-19 +
 *    createBuildEmissionTask); o que segue pendente sao os DADOS por
 *    tarefa (retrato e insumos vindos do pacote de rota — RG2-C-P2).
 *    Quando o montador lanca o erro nomeado P-03, emit() o converte em
 *    UNAVAILABLE em vez de deixar a promessa rejeitar sem resposta.
 *  - saveReading: o LocalStore da ED-18 expoe leitura da fila
 *    (pendingReadings) mas NENHUM metodo de gravacao de leitura executada.
 *    O metodo agora EXISTE no contrato (LocalStore.saveReading,
 *    syncEngine.ts) e a porta e a delegacao para ele
 *    (sync/fieldReadingStore.ts: createSaveReading). Sem a porta (null),
 *    a leitura segue so na memoria da sessao — a tela avisa; com a porta,
 *    a leitura gravada volta pela fila local (prova: o teste de
 *    controller recarrega e a leitura esta la). A implementacao SQLCipher
 *    do LocalStore segue pendencia de integracao (RG2-C-P4).
 *  - fetchDivergences (P-05): a fila de divergencia vive no servidor
 *    (ED-19, DIVERGENT_QUEUED); sem a porta, a Tela 4 mostra so as
 *    retencoes locais e declara a fila do servidor indisponivel.
 *  - queryDirective (P-06): a diretiva atual so existe no servidor; sem
 *    a porta, a Tela 5 nao afirma diretiva nenhuma fora da sync.
 */
import type { PendingReading, RoutePackage, RouteTask } from '../readings/routePackage.ts';
import type { DeviceDirective, LocalStore, SyncEngine } from '../sync/syncEngine.ts';
import type { OnDeviceOcrEngine } from '../ocr/onDeviceOcr.ts';
import {
  FieldEmission,
  type EmissionOutcome,
  type FieldBillResultDto,
  type FieldTaskEmission,
  type ThermalPrinterBridge,
} from '../field/fieldEmission.ts';
import {
  DivergenceQueue,
  HeldQueue,
  Navigator,
  addOccurrencePhotoRef,
  buildPendingReading,
  ocrVerdictFor,
  occurrenceRequiresPhoto,
  routeProgress,
  validateReading,
  type DirectiveQueryState,
  type DivergenceEntry,
  type LastSyncRun,
  type OcrSettings,
  type PrinterHealth,
  type PrinterHealthState,
  type ReadingDraft,
  type RouteProgress,
} from './models.ts';

export interface FieldScreensDeps {
  routePackage: RoutePackage;
  store: LocalStore;
  sync: SyncEngine;
  /** Fachada real da ED-19; null enquanto a integracao nao a montar. */
  emission: FieldEmission | null;
  /** Porta de integracao (P-03): monta o FieldTaskEmission da tarefa. */
  buildEmissionTask:
    | ((task: RouteTask, reading: PendingReading) => FieldTaskEmission)
    | null;
  /** Porta de integracao (P-04): grava a leitura executada na fila local. */
  saveReading: ((reading: PendingReading) => Promise<void>) | null;
  /** Definicoes OCR do tenant (P-02); null = OCR desligado nas telas. */
  ocrSettings: OcrSettings | null;
  /** Motor OCR on-device real (ED-18); null = sem reconhecimento por foto. */
  ocrEngine: OnDeviceOcrEngine | null;
  /** Ponte da impressora real (ED-19) para saude do aparelho; opcional. */
  printer: ThermalPrinterBridge | null;
  /**
   * Porta de integracao (P-05): fila de divergencia campo x servidor.
   * O aparelho nao apura divergencia — a fonte e o servidor (ED-19,
   * situacao DIVERGENT_QUEUED); a integracao entrega a consulta e a tela
   * exibe a fila. Null enquanto a integracao nao a entregar.
   */
  fetchDivergences: (() => Promise<DivergenceEntry[]>) | null;
  /**
   * Porta de integracao (P-06): consulta da diretiva atual do aparelho
   * fora da sincronizacao. O SyncEngine da ED-18 so conhece a diretiva
   * no contato (fetchDirective e do SyncTransport); sem esta porta, a
   * tela nao afirma diretiva nenhuma. Null enquanto nao entregue.
   */
  queryDirective: (() => Promise<DeviceDirective | null>) | null;
  now?: () => string;
}

export type EmitResult =
  | { status: 'DONE'; outcome: EmissionOutcome }
  | { status: 'UNAVAILABLE'; reason: string }
  | { status: 'NO_READING'; reason: string };

export class FieldScreensController {
  readonly nav = new Navigator();
  readonly held = new HeldQueue();
  readonly divergences = new DivergenceQueue();

  private readonly deps: FieldScreensDeps;
  private readonly now: () => string;
  private pending: PendingReading[] = [];
  private readonly drafts = new Map<string, ReadingDraft>();
  private readonly outcomes = new Map<string, EmissionOutcome>();
  private readonly emissionTasks = new Map<string, FieldTaskEmission>();
  private lastSync: LastSyncRun | null = null;
  private syncing = false;
  private divergenceState: 'NOT_QUERIED' | 'READY' | 'ERROR' = 'NOT_QUERIED';
  private divergenceError: string | null = null;
  private lastKnownDirective: DeviceDirective | null = null;
  private directiveState: 'NOT_QUERIED' | 'READY' | 'ERROR' = 'NOT_QUERIED';
  private directiveError: string | null = null;
  private printerHealthCache: PrinterHealth | null = null;
  private printerHealthChecked = false;
  private printerHealthError: string | null = null;

  constructor(deps: FieldScreensDeps) {
    this.deps = deps;
    this.now = deps.now ?? (() => new Date().toISOString());
  }

  get pkg(): RoutePackage {
    return this.deps.routePackage;
  }

  /** Carrega a fila local real (LocalStore da ED-18). */
  async load(): Promise<void> {
    this.pending = await this.deps.store.pendingReadings(this.pkg.routeId);
  }

  progress(): RouteProgress {
    return routeProgress(this.pkg, this.pending);
  }

  task(taskId: string): RouteTask {
    const t = this.pkg.tasks.find((x) => x.taskId === taskId);
    if (!t) throw new Error(`tarefa fora do pacote da rota: ${taskId}`);
    return t;
  }

  pendingFor(taskId: string): PendingReading | null {
    return this.pending.find((p) => p.taskId === taskId) ?? null;
  }

  draftFor(taskId: string): ReadingDraft {
    let d = this.drafts.get(taskId);
    if (!d) {
      const existing = this.pendingFor(taskId);
      d = {
        readingText: existing?.reading != null ? String(existing.reading) : '',
        occurrenceCode: existing?.occurrenceCode ?? null,
        photoRefs: existing ? [...existing.photoRefs] : [],
        ocrValue: existing?.ocrValue ?? null,
      };
      this.drafts.set(taskId, d);
    }
    return d;
  }

  /** true quando o motor OCR on-device foi injetado na integracao. */
  hasOcrEngine(): boolean {
    return this.deps.ocrEngine !== null;
  }

  /**
   * Anexa a foto de ocorrencia ao rascunho da tarefa — o caminho que a
   * Tela 2 oferece ao leiturista: a URI LOCAL da foto informada no campo
   * de foto de ocorrencia (mesmo padrao manual do visor/OCR da ED-18).
   * Sem este caminho, uma ocorrencia com requiresPhoto era impossivel de
   * confirmar pela tela (validateReading exige photoRefs nao-vazio e a
   * regra NAO foi afrouxada). Captura por camera continua pendencia
   * (P-07): aqui so entra a referencia que o leiturista/integracao tem.
   * Devolve false quando a URI e vazia (nada anexado).
   */
  addOccurrencePhoto(taskId: string, localPhotoUri: string): boolean {
    if (localPhotoUri.trim() === '') return false;
    const draft = this.draftFor(taskId);
    draft.photoRefs = addOccurrencePhotoRef(draft.photoRefs, localPhotoUri);
    return true;
  }

  /** Remove uma foto de ocorrencia anexada ao rascunho. */
  removeOccurrencePhoto(taskId: string, uri: string): void {
    const draft = this.draftFor(taskId);
    draft.photoRefs = draft.photoRefs.filter((r) => r !== uri);
  }

  /** Fotos de ocorrencia ja anexadas ao rascunho (copia). */
  occurrencePhotoRefs(taskId: string): string[] {
    return [...this.draftFor(taskId).photoRefs];
  }

  /** A ocorrencia selecionada no rascunho exige foto? (OccurrenceSnapshot). */
  draftRequiresPhoto(taskId: string): boolean {
    return occurrenceRequiresPhoto(this.pkg.occurrences, this.draftFor(taskId).occurrenceCode);
  }

  /**
   * Roda o OCR on-device (ED-18) sobre a foto LOCAL do visor e guarda apenas
   * os digitos no rascunho (a imagem nunca entra no payload — onDeviceOcr.ts).
   * Sem motor injetado, devolve null e a tela nao oferece o apoio.
   */
  async runOcr(taskId: string, localPhotoUri: string): Promise<number | null> {
    if (!this.deps.ocrEngine) return null;
    const { digits } = await this.deps.ocrEngine.recognizeDigits(localPhotoUri);
    const value = digits != null && /^\d+$/.test(digits) ? Number(digits) : null;
    this.draftFor(taskId).ocrValue = value;
    return value;
  }

  /** Veredito OCR atual do rascunho (regra real da ED-18 via models). */
  ocrVerdict(taskId: string): 'DISABLED' | 'MATCH' | 'SUGGESTION' | 'REVIEW' {
    const task = this.task(taskId);
    const draft = this.draftFor(taskId);
    const reading = draft.readingText.trim() === '' ? null : Number(draft.readingText);
    return ocrVerdictFor(task, Number.isNaN(reading) ? null : reading, draft.ocrValue, this.deps.ocrSettings);
  }

  /**
   * Confirma a leitura: valida contra o catalogo de ocorrencias do pacote,
   * monta o PendingReading real e grava pela porta de integracao. Sem a
   * porta (P-04), a leitura fica SOMENTE na memoria desta sessao e o retorno
   * diz isso — a tela avisa o leiturista, nada e fingido como persistido.
   * Falha do saver (R4-m4, fundido do lote R3-D na fusao FUSAO-R3D-MAIN)
   * devolve `{ ok:false, error }` com a mensagem original — a leitura JA
   * esta em `pending` (sem perda de dado) e a tela exibe o erro em vez de
   * o silenciar.
   */
  async submitReading(
    taskId: string,
    draft: ReadingDraft,
  ): Promise<{ ok: true; persisted: boolean } | { ok: false; error: string }> {
    const task = this.task(taskId);
    const v = validateReading(task, this.pkg.occurrences, draft);
    if (!v.ok) return { ok: false, error: v.error };
    const reading = buildPendingReading(task, draft, v.reading, this.now());
    this.pending = [...this.pending.filter((p) => p.taskId !== taskId), reading];
    this.drafts.set(taskId, { ...draft });
    if (this.deps.saveReading) {
      try {
        await this.deps.saveReading(reading);
      } catch (err) {
        // R4-m4 (fundido do lote R3-D): o tipo promete { ok:false, error } —
        // a rejeicao do saver nao pode escapar como promessa sem trato.
        return { ok: false, error: err instanceof Error ? err.message : String(err) };
      }
      return { ok: true, persisted: true };
    }
    return { ok: true, persisted: false };
  }

  outcomeFor(taskId: string): EmissionOutcome | null {
    return this.outcomes.get(taskId) ?? null;
  }

  billFor(taskId: string): FieldBillResultDto | null {
    const o = this.outcomes.get(taskId);
    return o?.kind === 'PRINTED' ? o.result : null;
  }

  /** Contas retidas pela fachada real (ED-19), na ordem de impressao. */
  retainedBills(): FieldBillResultDto[] {
    return this.deps.emission ? this.deps.emission.retainedBills() : [];
  }

  /**
   * Emite a conta da tarefa pela fachada REAL da ED-19, exatamente como ela
   * e: a tela nao calcula nada — quem calcula e o artefato atras da fachada.
   * O calculador da ED-19 NAO e tocado por este pacote.
   */
  async emit(taskId: string): Promise<EmitResult> {
    if (!this.deps.emission || !this.deps.buildEmissionTask) {
      return {
        status: 'UNAVAILABLE',
        reason:
          'Emissão indisponível nesta integração: falta montar a fachada com o retrato de tarifa da rota (pendência P-03).',
      };
    }
    const reading = this.pendingFor(taskId);
    if (!reading) {
      return { status: 'NO_READING', reason: 'Registre a leitura da unidade antes de emitir.' };
    }
    let taskEmission: FieldTaskEmission;
    try {
      taskEmission = this.deps.buildEmissionTask(this.task(taskId), reading);
    } catch (err) {
      // O montador (P-03) recusa com motivo nomeado quando falta o retrato
      // ou um insumo: a tela declara a indisponibilidade em vez de deixar
      // a promessa rejeitar sem resposta na tela.
      return {
        status: 'UNAVAILABLE',
        reason: err instanceof Error ? err.message : String(err),
      };
    }
    this.emissionTasks.set(taskId, taskEmission);
    const outcome = await this.deps.emission.emit(taskEmission);
    this.outcomes.set(taskId, outcome);
    this.held.record(outcome);
    return { status: 'DONE', outcome };
  }

  /** Reimpressao pela fachada: usa o resultado GUARDADO (a impressa nao muda). */
  async reprint(taskId: string): Promise<boolean> {
    if (!this.deps.emission) return false;
    const t = this.emissionTasks.get(taskId);
    if (!t) return false;
    await this.deps.emission.reprint(taskId, t.templateId, t.documentJson);
    return true;
  }

  /** Saude da impressora pela ponte real da ED-19 (cabecote/bateria). */
  async printerHealth(): Promise<{ headOk: boolean | null; batteryPct: number | null } | null> {
    if (!this.deps.printer) return null;
    return this.deps.printer.health();
  }

  /** true quando a ponte da impressora foi injetada na integracao. */
  hasPrinter(): boolean {
    return this.deps.printer !== null;
  }

  /**
   * Consulta a ponte e guarda o resultado para a Tela 3 exibir (a
   * renderizacao e sincrona; a consulta, nao). Falha da ponte vira estado
   * ERROR com a mensagem — nunca um valor fingido.
   */
  async refreshPrinterHealth(): Promise<PrinterHealthState> {
    if (!this.deps.printer) return this.printerHealthState();
    try {
      this.printerHealthCache = await this.deps.printer.health();
      this.printerHealthError = null;
    } catch (err) {
      this.printerHealthCache = null;
      this.printerHealthError = err instanceof Error ? err.message : String(err);
    }
    this.printerHealthChecked = true;
    return this.printerHealthState();
  }

  /** Estado de exibicao da saude da impressora para a Tela 3. */
  printerHealthState(): PrinterHealthState {
    if (!this.deps.printer) return { status: 'UNAVAILABLE' };
    if (this.printerHealthError !== null) {
      return { status: 'ERROR', message: this.printerHealthError };
    }
    if (!this.printerHealthChecked || this.printerHealthCache === null) {
      return { status: 'NOT_CHECKED' };
    }
    return { status: 'READY', ...this.printerHealthCache };
  }

  lastSyncRun(): LastSyncRun | null {
    return this.lastSync;
  }

  isSyncing(): boolean {
    return this.syncing;
  }

  /** Sincroniza pela SyncEngine REAL da ED-18 (diretivas BLOCK/RETIRE inclusas). */
  async syncNow(): Promise<LastSyncRun> {
    if (this.syncing) throw new Error('sincronizacao ja em andamento');
    this.syncing = true;
    try {
      const result = await this.deps.sync.syncRoute(this.pkg.routeId);
      this.lastSync = { uploaded: result.uploaded, wiped: result.wiped, at: this.now() };
      if (result.wiped) {
        this.pending = [];
      } else {
        await this.load();
      }
      return this.lastSync;
    } finally {
      this.syncing = false;
    }
  }

  /** true quando a porta da fila de divergencia (P-05) foi entregue. */
  hasDivergenceFeed(): boolean {
    return this.deps.fetchDivergences !== null;
  }

  /** Estado da fila de divergencias para a Tela 4. */
  divergenceFeedState(): 'UNAVAILABLE' | 'NOT_QUERIED' | 'READY' | 'ERROR' {
    if (!this.deps.fetchDivergences) return 'UNAVAILABLE';
    return this.divergenceState;
  }

  /** Erro da ultima consulta a fila de divergencias (null = sem erro). */
  divergenceFeedError(): string | null {
    return this.divergenceError;
  }

  /**
   * Consulta a fila de divergencias no servidor (porta P-05) e a guarda
   * nesta sessao — o servidor e a fonte, o aparelho nao acumula nem
   * resolve divergencia. Sem a porta, devolve UNAVAILABLE sem fingir
   * fila vazia. Falha da porta vira ERROR e mantem a ultima fila
   * consultada (nunca a apaga por causa do erro).
   */
  async refreshDivergences(): Promise<'UNAVAILABLE' | 'NOT_QUERIED' | 'READY' | 'ERROR'> {
    if (!this.deps.fetchDivergences) return 'UNAVAILABLE';
    try {
      const entries = await this.deps.fetchDivergences();
      this.divergences.replace(entries);
      this.divergenceError = null;
      this.divergenceState = 'READY';
    } catch (err) {
      this.divergenceError = err instanceof Error ? err.message : String(err);
      this.divergenceState = 'ERROR';
    }
    return this.divergenceState;
  }

  /** true quando a porta de consulta de diretiva (P-06) foi entregue. */
  hasDirectiveQuery(): boolean {
    return this.deps.queryDirective !== null;
  }

  /** Estado da consulta de diretiva fora da sync, para a Tela 5. */
  directiveQueryState(): DirectiveQueryState {
    if (!this.deps.queryDirective) return 'UNAVAILABLE';
    return this.directiveState;
  }

  /** Ultima diretiva conhecida por consulta expressa (null = desconhecida). */
  directive(): DeviceDirective | null {
    return this.lastKnownDirective;
  }

  /** Erro da ultima consulta de diretiva (null = sem erro). */
  directiveQueryError(): string | null {
    return this.directiveError;
  }

  /**
   * Consulta a diretiva atual do aparelho FORA da sincronizacao (porta
   * P-06). Sem a porta, devolve UNAVAILABLE: nenhuma diretiva e inferida
   do resultado da ultima sync (wiped cobre RETIRE e BLOCK sem dizer
   qual). A diretiva guardada so muda por esta consulta expressa — nunca
   por inferencia. Porta que devolve null = servidor sem diretiva a
   informar (desconhecida, nao "nenhuma").
   */
  async refreshDirective(): Promise<DirectiveQueryState> {
    if (!this.deps.queryDirective) return 'UNAVAILABLE';
    try {
      this.lastKnownDirective = await this.deps.queryDirective();
      this.directiveError = null;
      this.directiveState = 'READY';
    } catch (err) {
      this.directiveError = err instanceof Error ? err.message : String(err);
      this.directiveState = 'ERROR';
    }
    return this.directiveState;
  }
}
