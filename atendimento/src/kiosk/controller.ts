/**
 * Controladores das telas da ED-40 (pacote ed-40-telas): quiosque,
 * painel (SG-626), guiche (SG-625) e relatorio de espera (RAS-26).
 *
 * Toda a logica decidivel vive aqui e em models.ts (testada sem DOM);
 * screens.ts e so renderizacao, no padrao do app de campo entregue.
 *
 * Guardas de contrato aplicadas no controlador:
 *  - Quiosque desligado (portal.kiosk_enabled=false): estado de RECUSA,
 *    nenhuma chamada a porta de emissao e nenhuma acao permitida.
 *  - Parametro ausente (null): erro de configuracao explicito
 *    (MissingParameterError do nucleo), nunca habilitado em silencio.
 *  - Emissao devolve SO codigo + hora de emissao para a tela.
 *  - Painel: varredura negativa de dado pessoal sobre a vista montada
 *    (assertPanelClean) — se um campo pessoal aparecer, a tela recusa
 *    exibir em vez de vazar.
 *  - Tipos de servico: o nucleo recebe serviceType como texto livre e
 *    NENHUM catalogo foi entregue; a lista e INJETADA pela fiacao
 *    (pendencia P-ED40T-03). Lista vazia = tela mostra a pendencia.
 */
import {
  ACCESSIBLE_FORMAT_LABELS,
  ASSISTANCE_CHANNEL_LABELS,
  IllegalArgumentError,
  KIOSK_ACTIONS,
  KioskTerminal,
  orderWaiting,
  timeOf,
  type KioskAction,
  type PanelView,
  type PriorityAttendanceProfile,
  type WaitingDayRow,
} from './models.ts';
import type {
  DeskPort,
  IssuedTicketView,
  KioskIssuePort,
  TicketSnapshot,
} from './ports.ts';
import { QueueTicket } from './models.ts';

// ---------------------------------------------------------------------------
// Quiosque (totem)
// ---------------------------------------------------------------------------

export interface KioskDeps {
  deviceId: string;
  serviceUnitId: string;
  /** Valor cru do parametro portal.kiosk_enabled; null = nao configurado. */
  kioskEnabled: boolean | null;
  issuePort: KioskIssuePort;
  /** Catalogo de tipos de servico injetado pela fiacao (P-ED40T-03). */
  serviceTypes: readonly string[];
  now: () => string;
  newTicketId: () => string;
  nextSequence: () => number;
}

export type IssueOutcome =
  | { status: 'ISSUED'; view: IssuedTicketView }
  | { status: 'REFUSED'; reason: string }
  | { status: 'INVALID'; reason: string };

export interface AccessibilityHelp {
  formats: { value: string; label: string }[];
  channels: { value: string; label: string }[];
}

export class KioskController {
  private readonly deps: KioskDeps;
  private readonly terminal: KioskTerminal | null;
  readonly configurationError: string | null;

  constructor(deps: KioskDeps) {
    this.deps = deps;
    let terminal: KioskTerminal | null = null;
    let error: string | null = null;
    try {
      terminal = KioskTerminal.fromParameters(deps.deviceId, deps.serviceUnitId, deps.kioskEnabled);
    } catch (e) {
      // MissingParameterException do nucleo: configuracao ausente e erro
      // explicito na tela, nunca terminal habilitado em silencio.
      error = e instanceof Error ? e.message : String(e);
    }
    this.terminal = terminal;
    this.configurationError = error;
  }

  get serviceUnitId(): string {
    return this.deps.serviceUnitId;
  }

  get serviceTypes(): readonly string[] {
    return this.deps.serviceTypes;
  }

  /** true somente com o parametro presente e ligado. */
  isEnabled(): boolean {
    return this.terminal !== null && this.terminal.kioskModeEnabled();
  }

  /** Estado de RECUSA: parametro desligado (ou configuracao ausente). */
  isRefused(): boolean {
    return !this.isEnabled();
  }

  refusalReason(): string {
    if (this.configurationError) {
      return `Quiosque não configurado: ${this.configurationError}`;
    }
    return 'Quiosque desligado para esta unidade (portal.kiosk_enabled).';
  }

  /** Acoes permitidas AGORA: a lista fechada do contrato, ou nada se desligado. */
  allowedActions(): KioskAction[] {
    if (!this.terminal) return [];
    return KIOSK_ACTIONS.filter((a) => this.terminal?.actionAllowed(a));
  }

  actionAllowed(action: KioskAction): boolean {
    return this.terminal !== null && this.terminal.actionAllowed(action);
  }

  async issueTicket(
    serviceType: string,
    profile: PriorityAttendanceProfile,
  ): Promise<IssueOutcome> {
    if (!this.isEnabled()) {
      // Recusa SEM tocar a porta: desligado, o terminal nao emite (contrato).
      return { status: 'REFUSED', reason: this.refusalReason() };
    }
    if (!serviceType || serviceType.trim() === '') {
      return { status: 'INVALID', reason: 'tipo de servico obrigatorio' };
    }
    if (this.deps.serviceTypes.length > 0 && !this.deps.serviceTypes.includes(serviceType)) {
      return {
        status: 'INVALID',
        reason: `tipo de servico fora do catalogo da unidade: ${serviceType}`,
      };
    }
    const view = await this.deps.issuePort.issue({
      ticketId: this.deps.newTicketId(),
      sequenceNumber: this.deps.nextSequence(),
      serviceType,
      profile,
      now: this.deps.now(),
    });
    return { status: 'ISSUED', view };
  }

  /** Ajuda de acessibilidade do quiosque: formatos e canais do perfil da P1.3. */
  accessibilityHelp(): AccessibilityHelp | null {
    if (!this.actionAllowed('ACCESSIBILITY_HELP')) return null;
    return {
      formats: Object.entries(ACCESSIBLE_FORMAT_LABELS)
        .filter(([v]) => v !== 'NONE')
        .map(([value, label]) => ({ value, label })),
      channels: Object.entries(ASSISTANCE_CHANNEL_LABELS)
        .filter(([v]) => v !== 'NONE')
        .map(([value, label]) => ({ value, label })),
    };
  }
}

// ---------------------------------------------------------------------------
// Painel de chamada (SG-626)
// ---------------------------------------------------------------------------

/** Chaves que denunciam dado pessoal — o painel NUNCA as exibe (contrato). */
export const PERSONAL_DATA_KEYS: readonly string[] = [
  'nome',
  'name',
  'cpf',
  'cnpj',
  'documento',
  'telefone',
  'phone',
  'email',
  'endereco',
  'address',
  'cliente',
  'customer',
  'titular',
];

/**
 * Varredura negativa: true se qualquer chave de dado pessoal aparecer,
 * em qualquer profundidade, na vista do painel.
 */
export function panelContainsPersonalData(view: unknown): boolean {
  if (typeof view !== 'object' || view === null) return false;
  for (const [key, value] of Object.entries(view as Record<string, unknown>)) {
    if (PERSONAL_DATA_KEYS.includes(key.toLowerCase())) return true;
    if (typeof value === 'object' && value !== null && panelContainsPersonalData(value)) {
      return true;
    }
    if (Array.isArray(value) && value.some((v) => panelContainsPersonalData(v))) {
      return true;
    }
  }
  return false;
}

/** Recusa exibir um painel contaminado com dado pessoal. */
export function assertPanelClean(view: PanelView): PanelView {
  if (panelContainsPersonalData(view)) {
    throw new IllegalArgumentError(
      'painel recusado: dado pessoal fora do contrato (SG-626 exibe so codigo, guiche e hora)',
    );
  }
  return view;
}

export interface PanelSource {
  fetchPanel(serviceUnitId: string): Promise<PanelView>;
}

export class PanelController {
  private readonly source: PanelSource;
  private view: PanelView = { current: null, history: [] };

  constructor(source: PanelSource) {
    this.source = source;
  }

  async load(serviceUnitId: string): Promise<PanelView> {
    this.view = assertPanelClean(await this.source.fetchPanel(serviceUnitId));
    return this.view;
  }

  get panel(): PanelView {
    return this.view;
  }

  /** Linhas de exibicao do historico: SO codigo, guiche e hora. */
  historyRows(): { displayCode: string; deskId: string; time: string }[] {
    return this.view.history.map((h) => ({
      displayCode: h.displayCode,
      deskId: h.deskId,
      time: timeOf(h.calledAt),
    }));
  }

  currentRow(): { displayCode: string; deskId: string; time: string } | null {
    const c = this.view.current;
    return c ? { displayCode: c.displayCode, deskId: c.deskId, time: timeOf(c.calledAt) } : null;
  }
}

// ---------------------------------------------------------------------------
// Guiche / atendente (SG-625) — consome a DeskPort (P-ED40T-01)
// ---------------------------------------------------------------------------

export class DeskController {
  private readonly port: DeskPort;
  readonly deskId: string;
  private readonly now: () => string;
  private tickets: TicketSnapshot[] = [];

  constructor(port: DeskPort, deskId: string, now: () => string) {
    this.port = port;
    this.deskId = deskId;
    this.now = now;
  }

  async load(): Promise<void> {
    this.tickets = await this.port.listTickets();
  }

  get all(): readonly TicketSnapshot[] {
    return this.tickets;
  }

  /** Fila de espera na ordem do nucleo (prioridade legal -> emissao -> sequencial). */
  waiting(): TicketSnapshot[] {
    const byId = new Map(this.tickets.map((t) => [t.id, t]));
    const live = this.tickets
      .filter((t) => t.status === 'WAITING')
      .map((t) => {
        const q = new QueueTicket(
          t.id,
          t.serviceUnitId,
          t.sequenceNumber,
          t.serviceType,
          t.profile,
          t.issuedAt,
        );
        return q;
      });
    return orderWaiting(live).map((q) => byId.get(q.id) as TicketSnapshot);
  }

  /** Senha em atendimento neste guiche (chamada ou em atendimento). */
  activeForDesk(): TicketSnapshot | null {
    return (
      this.tickets.find(
        (t) => t.deskId === this.deskId && (t.status === 'CALLED' || t.status === 'IN_SERVICE'),
      ) ?? null
    );
  }

  private replace(updated: TicketSnapshot): TicketSnapshot {
    this.tickets = this.tickets.map((t) => (t.id === updated.id ? updated : t));
    return updated;
  }

  async callNext(): Promise<TicketSnapshot> {
    return this.replace(await this.port.callNext(this.deskId, this.now()));
  }

  async startService(ticketId: string): Promise<TicketSnapshot> {
    return this.replace(await this.port.startService(ticketId, this.now()));
  }

  async complete(ticketId: string): Promise<TicketSnapshot> {
    return this.replace(await this.port.complete(ticketId, this.now()));
  }

  async markNoShow(ticketId: string): Promise<TicketSnapshot> {
    return this.replace(await this.port.markNoShow(ticketId, this.now()));
  }

  async cancel(ticketId: string): Promise<TicketSnapshot> {
    return this.replace(await this.port.cancel(ticketId, this.now()));
  }
}

// ---------------------------------------------------------------------------
// Relatorio de espera (RAS-26)
// ---------------------------------------------------------------------------

export interface WaitingReportSource {
  fetchReport(de: string, ate: string): Promise<WaitingDayRow[]>;
}

export class WaitingReportController {
  private readonly source: WaitingReportSource;
  private rows: WaitingDayRow[] = [];

  constructor(source: WaitingReportSource) {
    this.source = source;
  }

  async load(de: string, ate: string): Promise<WaitingDayRow[]> {
    if (!de || !ate) {
      throw new IllegalArgumentError('periodo do relatorio obrigatorio (de/ate)');
    }
    if (de > ate) {
      throw new IllegalArgumentError('periodo do relatorio invalido: de posterior a ate');
    }
    this.rows = await this.source.fetchReport(de, ate);
    return this.rows;
  }

  get reportRows(): readonly WaitingDayRow[] {
    return this.rows;
  }
}
