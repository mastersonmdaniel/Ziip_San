/**
 * Portas e transporte das telas da ED-40 (pacote ed-40-telas).
 *
 * CONTRATOS HTTP PROVADOS no documento entregue
 * (ed-40/entrega/docs/atendimento/QUIOSQUE-E-PAINEL-ED40.md) — e SO estes
 * dois tem URL neste pacote:
 *  - Painel SG-626:  GET /painel-chamada?unidade=
 *  - Relatorio RAS-26: GET /atendimento/relatorio-espera?de=&ate=
 *
 * Emissao de senha do quiosque e acoes do guiche (SG-625) NAO tem rota
 * HTTP documentada nem controlador entregue na ED-40/ED-35/ED-38
 * (verificado por busca de Controller/Mapping nas entregas). Elas sao
 * consumidas por PORTAS TIPADAS que espelham a API do nucleo
 * (KioskTerminal.issueTicket / QueueTicket / TicketQueue) e a fiacao
 * entrega a implementacao — pendencias P-ED40T-01 (guiche) e
 * P-ED40T-02 (emissao) do RELATORIO-ED-40-TELAS.md. NENHUMA URL foi
 * inventada para elas.
 *
 * Os transportes HTTP mapeiam a resposta por LISTA BRANCA de campos:
 * somente os campos do contrato entram na tela. Campo extra que um
 * servidor devolva (ex.: um nome) e descartado no transporte e nunca
 * chega ao painel — a garantia "nenhum dado pessoal" nao depende so
 * da boa vontade do servidor.
 */
import {
  IllegalStateError,
  QueueTicket,
  generateWaitingReport,
  nextToCall,
  panelSnapshot,
  type KioskTerminal,
  type PanelView,
  type PriorityAttendanceProfile,
  type TicketStatus,
  type WaitingDayRow,
  type WaitingLimitPolicy,
} from './models.ts';

// ---------------------------------------------------------------------------
// Transporte HTTP minimo (injetado; nos testes, um dublê registra as URLs)
// ---------------------------------------------------------------------------

/** GET que devolve o JSON ja parseado. A fiacao liga isto ao fetch real. */
export type FetchJson = (url: string) => Promise<unknown>;

export const PANEL_ROUTE = '/painel-chamada';
export const WAITING_REPORT_ROUTE = '/atendimento/relatorio-espera';

// ---------------------------------------------------------------------------
// Painel de chamada (SG-626) — rota documentada
// ---------------------------------------------------------------------------

function asRecord(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error('resposta do painel fora do contrato: objeto esperado');
  }
  return value as Record<string, unknown>;
}

function mapAnnouncement(raw: unknown): PanelView['current'] {
  if (raw === null || raw === undefined) return null;
  const r = asRecord(raw);
  // LISTA BRANCA do CallAnnouncement (CallPanel.java): nada alem disto entra.
  return {
    displayCode: String(r['displayCode']),
    deskId: String(r['deskId']),
    serviceUnitId: String(r['serviceUnitId']),
    calledAt: String(r['calledAt']),
    legalPriority: r['legalPriority'] === true,
  };
}

/** Transporte do painel sobre a rota documentada GET /painel-chamada?unidade=. */
export class HttpPanelTransport {
  private readonly fetchJson: FetchJson;

  constructor(fetchJson: FetchJson) {
    this.fetchJson = fetchJson;
  }

  async fetchPanel(serviceUnitId: string): Promise<PanelView> {
    const url = `${PANEL_ROUTE}?unidade=${encodeURIComponent(serviceUnitId)}`;
    const raw = asRecord(await this.fetchJson(url));
    const historyRaw = Array.isArray(raw['history']) ? (raw['history'] as unknown[]) : [];
    return {
      current: mapAnnouncement(raw['current'] ?? null),
      history: historyRaw
        .map((h) => mapAnnouncement(h))
        .filter((h): h is NonNullable<typeof h> => h !== null),
    };
  }
}

// ---------------------------------------------------------------------------
// Relatorio de espera (RAS-26) — rota documentada
// ---------------------------------------------------------------------------

function mapDayRow(raw: unknown): WaitingDayRow {
  const r = asRecord(raw);
  // LISTA BRANCA do WaitingTimeReport.DayRow (WaitingTimeReport.java).
  return {
    serviceUnitId: String(r['serviceUnitId']),
    date: String(r['date']),
    limitMinutes: Number(r['limitMinutes']),
    limitKind: r['limitKind'] === 'HOLIDAY_ADJACENT' ? 'HOLIDAY_ADJACENT' : 'BUSINESS_DAY',
    startedCount: Number(r['startedCount']),
    withinLimit: Number(r['withinLimit']),
    outsideLimit: Number(r['outsideLimit']),
    withinPct: Number(r['withinPct']),
    averageWaitMinutes: Number(r['averageWaitMinutes']),
    maxWaitMinutes: Number(r['maxWaitMinutes']),
    pendingStart: Number(r['pendingStart']),
    closedWithoutStart: Number(r['closedWithoutStart']),
  };
}

/** Transporte do relatorio sobre GET /atendimento/relatorio-espera?de=&ate=. */
export class HttpWaitingReportTransport {
  private readonly fetchJson: FetchJson;

  constructor(fetchJson: FetchJson) {
    this.fetchJson = fetchJson;
  }

  async fetchReport(de: string, ate: string): Promise<WaitingDayRow[]> {
    const url =
      `${WAITING_REPORT_ROUTE}?de=${encodeURIComponent(de)}` +
      `&ate=${encodeURIComponent(ate)}`;
    const raw = await this.fetchJson(url);
    if (!Array.isArray(raw)) {
      throw new Error('resposta do relatorio fora do contrato: lista esperada');
    }
    return raw.map((r) => mapDayRow(r));
  }
}

// ---------------------------------------------------------------------------
// Retrato da senha (DTO das portas locais) — espelha os acessores do QueueTicket
// ---------------------------------------------------------------------------

export interface TicketSnapshot {
  id: string;
  serviceUnitId: string;
  sequenceNumber: number;
  displayCode: string;
  serviceType: string;
  profile: PriorityAttendanceProfile;
  issuedAt: string;
  calledAt: string | null;
  deskId: string | null;
  serviceStartedAt: string | null;
  finishedAt: string | null;
  status: TicketStatus;
}

export function snapshotOf(t: QueueTicket): TicketSnapshot {
  return {
    id: t.id,
    serviceUnitId: t.serviceUnitId,
    sequenceNumber: t.sequenceNumber,
    displayCode: t.displayCode(),
    serviceType: t.serviceType,
    profile: t.profile,
    issuedAt: t.issuedAt,
    calledAt: t.calledAt(),
    deskId: t.deskId(),
    serviceStartedAt: t.serviceStartedAt(),
    finishedAt: t.finishedAt(),
    status: t.status(),
  };
}

// ---------------------------------------------------------------------------
// Porta de emissao do quiosque — espelha KioskTerminal.issueTicket.
// Rota HTTP da emissao: [A DEFINIR] na fiacao (P-ED40T-02). O documento
// entregue nomeia a acao e a recusa com o quiosque desligado, mas nao
// publica URL de emissao; nenhuma URL e inventada aqui.
// ---------------------------------------------------------------------------

export interface IssueRequest {
  ticketId: string;
  sequenceNumber: number;
  serviceType: string;
  profile: PriorityAttendanceProfile;
  now: string;
}

/** O que a tela do totem exibe da emissao: SO codigo e hora (contrato). */
export interface IssuedTicketView {
  displayCode: string;
  issuedAt: string;
  serviceUnitId: string;
}

export interface KioskIssuePort {
  issue(request: IssueRequest): Promise<IssuedTicketView>;
}

/**
 * Implementacao local da porta sobre o nucleo real (KioskTerminal):
 * usada nos testes e na demonstracao. Desligado, lanca a mesma recusa
 * do Java (IllegalStateException). Na fiacao, a sequencia por
 * unidade/dia vem do banco (pendencia P-ED40-2 da ED-40) e o terminal
 * vem do cadastro por dispositivo/unidade.
 */
export class LocalKioskIssuePort implements KioskIssuePort {
  private readonly terminal: KioskTerminal;
  /** Senhas emitidas nesta sessao local (a tela do guiche as consome na demo). */
  readonly issued: QueueTicket[] = [];

  constructor(terminal: KioskTerminal) {
    this.terminal = terminal;
  }

  async issue(request: IssueRequest): Promise<IssuedTicketView> {
    const ticket = this.terminal.issueTicket(
      request.ticketId,
      request.sequenceNumber,
      request.serviceType,
      request.profile,
      request.now,
    );
    this.issued.push(ticket);
    return {
      displayCode: ticket.displayCode(),
      issuedAt: ticket.issuedAt,
      serviceUnitId: ticket.serviceUnitId,
    };
  }
}

// ---------------------------------------------------------------------------
// Porta do guiche (SG-625) — espelha QueueTicket/TicketQueue do nucleo.
// Rota HTTP do guiche: [A DEFINIR] na fiacao (P-ED40T-01). O contrato
// documenta as TRANSICOES (chamar/iniciar/encerrar/ausencia/cancelar)
// sobre o ciclo de vida entregue, mas nenhuma URL/controlador foi
// entregue para elas; a tela consome esta porta tipada.
// ---------------------------------------------------------------------------

export interface DeskPort {
  /** Todas as senhas conhecidas do guiche (retratos; a ordem da fila e do nucleo). */
  listTickets(): Promise<TicketSnapshot[]>;
  /** Chama a proxima senha (TicketQueue.nextToCall) para o guiche. */
  callNext(deskId: string, at: string): Promise<TicketSnapshot>;
  startService(ticketId: string, at: string): Promise<TicketSnapshot>;
  complete(ticketId: string, at: string): Promise<TicketSnapshot>;
  markNoShow(ticketId: string, at: string): Promise<TicketSnapshot>;
  cancel(ticketId: string, at: string): Promise<TicketSnapshot>;
}

/**
 * Implementacao local da porta sobre o nucleo real: cada acao executa a
 * transicao do QueueTicket (com as recusas do Java) e devolve o retrato
 * resultante — instantes anteriores nunca sao reescritos porque o
 * proprio QueueTicket os protege. Usada nos testes e na demonstracao;
 * a fiacao substitui por HTTP quando a rota existir (P-ED40T-01).
 */
export class LocalDeskPort implements DeskPort {
  private readonly tickets: QueueTicket[];

  constructor(tickets: QueueTicket[]) {
    this.tickets = tickets;
  }

  private find(ticketId: string): QueueTicket {
    const t = this.tickets.find((x) => x.id === ticketId);
    if (!t) throw new IllegalStateError(`senha fora da fila do guiche: ${ticketId}`);
    return t;
  }

  async listTickets(): Promise<TicketSnapshot[]> {
    return this.tickets.map((t) => snapshotOf(t));
  }

  async callNext(deskId: string, at: string): Promise<TicketSnapshot> {
    const next = nextToCall(this.tickets);
    if (!next) throw new IllegalStateError('nenhuma senha aguardando chamada');
    next.call(at, deskId);
    return snapshotOf(next);
  }

  async startService(ticketId: string, at: string): Promise<TicketSnapshot> {
    const t = this.find(ticketId);
    t.startService(at);
    return snapshotOf(t);
  }

  async complete(ticketId: string, at: string): Promise<TicketSnapshot> {
    const t = this.find(ticketId);
    t.complete(at);
    return snapshotOf(t);
  }

  async markNoShow(ticketId: string, at: string): Promise<TicketSnapshot> {
    const t = this.find(ticketId);
    t.markNoShow(at);
    return snapshotOf(t);
  }

  async cancel(ticketId: string, at: string): Promise<TicketSnapshot> {
    const t = this.find(ticketId);
    t.cancel(at);
    return snapshotOf(t);
  }
}

// ---------------------------------------------------------------------------
// Fontes locais do painel e do relatorio para teste/demonstracao:
// derivam das MESMAS funcoes do nucleo (panelSnapshot/generateWaitingReport)
// em vez de reimplementar a regra na tela.
// ---------------------------------------------------------------------------

export class LocalPanelSource {
  private readonly tickets: QueueTicket[];
  private readonly historySize: number;

  constructor(tickets: QueueTicket[], historySize = 5) {
    this.tickets = tickets;
    this.historySize = historySize;
  }

  async fetchPanel(serviceUnitId: string): Promise<PanelView> {
    return panelSnapshot(this.tickets, serviceUnitId, this.historySize);
  }
}

export class LocalWaitingReportSource {
  private readonly tickets: QueueTicket[];
  private readonly policy: WaitingLimitPolicy;

  constructor(tickets: QueueTicket[], policy: WaitingLimitPolicy) {
    this.tickets = tickets;
    this.policy = policy;
  }

  /** Filtra pelo periodo [de, ate] sobre o dia de emissao, como a rota documentada. */
  async fetchReport(de: string, ate: string): Promise<WaitingDayRow[]> {
    const inRange = this.tickets.filter((t) => {
      const day = t.issuedAt.slice(0, 10);
      return day >= de && day <= ate;
    });
    return generateWaitingReport(inRange, this.policy);
  }
}
