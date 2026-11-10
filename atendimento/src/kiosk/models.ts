/**
 * Espelho TypeScript FIEL do nucleo Java da ED-40
 * (br.com.ziipsan.core.sac.kiosk) — pacote ed-40-telas.
 *
 * Cada tipo/funcao abaixo corresponde a uma classe entregue em
 * ed-40/entrega/shared/ziipsan-core/src/main/java/br/com/ziipsan/core/sac/kiosk/:
 *  - PriorityAttendanceProfile -> perfil, categorias, formatos, canais
 *  - KioskParameters           -> chaves de parametro e valores de fabrica
 *  - KioskTerminal             -> gate portal.kiosk_enabled + lista fechada
 *  - QueueTicket               -> senha e ciclo de vida monotonico
 *  - TicketQueue               -> ordenacao prioridade legal -> emissao
 *  - CallPanel                 -> snapshot do painel (SG-626)
 *  - HolidayCalendar           -> calendario parametrizavel do tenant
 *  - WaitingLimitPolicy        -> limite do dia (Lei SC 12.465/2002)
 *  - WaitingTimeReport         -> relatorio RAS-26 por unidade e dia
 *
 * Nenhuma regra e inventada aqui: mensagens de erro copiam as do Java e as
 * transicoes recusadas sao as mesmas. Instantes sao strings ISO locais
 * ("YYYY-MM-DDTHH:mm:ss", o formato de LocalDateTime serializado); datas
 * sao "YYYY-MM-DD" (LocalDate). Comparacoes usam aritmetica UTC sobre os
 * componentes, nunca o fuso da maquina.
 */

// ---------------------------------------------------------------------------
// Erros (espelhos dos tipos Java)
// ---------------------------------------------------------------------------

/** Espelho de IllegalStateException do nucleo. */
export class IllegalStateError extends Error {
  override readonly name = 'IllegalStateException';
}

/** Espelho de IllegalArgumentException do nucleo. */
export class IllegalArgumentError extends Error {
  override readonly name = 'IllegalArgumentException';
}

/** Espelho de br.com.ziipsan.core.parameters.MissingParameterException. */
export class MissingParameterError extends Error {
  override readonly name = 'MissingParameterException';
  readonly parameterKey: string;
  constructor(parameterKey: string, message: string) {
    super(message);
    this.parameterKey = parameterKey;
  }
}

function requireNonNull<T>(value: T | null | undefined, message: string): T {
  if (value === null || value === undefined) throw new TypeError(message);
  return value;
}

// ---------------------------------------------------------------------------
// Datas e instantes (LocalDate / LocalDateTime)
// ---------------------------------------------------------------------------

const LDT_RE = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/;

/** Milissegundos comparaveis de um LocalDateTime ISO (aritmetica UTC). */
export function ldtMillis(ldt: string): number {
  const m = LDT_RE.exec(ldt);
  if (!m) throw new IllegalArgumentError(`instante invalido: ${ldt}`);
  return Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] ?? 0));
}

export function ldtDate(ldt: string): string {
  return ldt.slice(0, 10);
}

export function diffSeconds(fromLdt: string, toLdt: string): number {
  return (ldtMillis(toLdt) - ldtMillis(fromLdt)) / 1000;
}

export function dateMillis(date: string): number {
  const [y, mo, d] = date.split('-').map(Number);
  return Date.UTC(y, (mo ?? 1) - 1, d ?? 1);
}

export function addDays(date: string, days: number): string {
  const t = new Date(dateMillis(date) + days * 86_400_000);
  const y = t.getUTCFullYear();
  const mo = String(t.getUTCMonth() + 1).padStart(2, '0');
  const d = String(t.getUTCDate()).padStart(2, '0');
  return `${y}-${mo}-${d}`;
}

/** 0 = domingo ... 6 = sabado (DayOfWeek do Java, via UTC). */
export function dayOfWeek(date: string): number {
  return new Date(dateMillis(date)).getUTCDay();
}

/** "HH:mm" de um LocalDateTime ISO, para exibicao. */
export function timeOf(ldt: string): string {
  return ldt.slice(11, 16);
}

// ---------------------------------------------------------------------------
// PriorityAttendanceProfile (P1.3 / CAD-81, Lei 10.048/2000)
// ---------------------------------------------------------------------------

export type PriorityCategory =
  | 'NONE'
  | 'PERSON_WITH_DISABILITY'
  | 'ELDERLY_60_OR_OVER'
  | 'PREGNANT'
  | 'NURSING'
  | 'WITH_CHILD_IN_ARMS'
  | 'WITH_OBESITY';

export type AccessibleFormat = 'NONE' | 'BRAILLE' | 'LARGE_PRINT' | 'ACCESSIBLE_PDF' | 'AUDIO';

export type AssistanceChannel = 'NONE' | 'LIBRAS' | 'TDD' | 'VIDEO_SERVICE';

export interface PriorityAttendanceProfile {
  category: PriorityCategory;
  companionPresent: boolean;
  accessibleFormat: AccessibleFormat;
  assistanceChannel: AssistanceChannel;
}

export const PRIORITY_CATEGORIES: readonly PriorityCategory[] = [
  'NONE',
  'PERSON_WITH_DISABILITY',
  'ELDERLY_60_OR_OVER',
  'PREGNANT',
  'NURSING',
  'WITH_CHILD_IN_ARMS',
  'WITH_OBESITY',
];

export const ACCESSIBLE_FORMATS: readonly AccessibleFormat[] = [
  'NONE',
  'BRAILLE',
  'LARGE_PRINT',
  'ACCESSIBLE_PDF',
  'AUDIO',
];

export const ASSISTANCE_CHANNELS: readonly AssistanceChannel[] = [
  'NONE',
  'LIBRAS',
  'TDD',
  'VIDEO_SERVICE',
];

/** Rotulos das categorias como descritas no javadoc do Java (Lei 10.048/2000, art. 1o). */
export const PRIORITY_CATEGORY_LABELS: Record<PriorityCategory, string> = {
  NONE: 'Comum (sem prioridade legal)',
  PERSON_WITH_DISABILITY: 'Pessoa com deficiência',
  ELDERLY_60_OR_OVER: 'Idoso com 60 anos ou mais',
  PREGNANT: 'Gestante',
  NURSING: 'Lactante',
  WITH_CHILD_IN_ARMS: 'Pessoa com criança de colo',
  WITH_OBESITY: 'Pessoa com obesidade',
};

/** Formatos acessiveis da Lei 13.146/2015, art. 62, como nomeados no Java. */
export const ACCESSIBLE_FORMAT_LABELS: Record<AccessibleFormat, string> = {
  NONE: 'Sem formato acessível',
  BRAILLE: 'Braile',
  LARGE_PRINT: 'Fonte ampliada',
  ACCESSIBLE_PDF: 'PDF acessível',
  AUDIO: 'Áudio',
};

/** Canais de atendimento como nomeados no Java (P1.3). */
export const ASSISTANCE_CHANNEL_LABELS: Record<AssistanceChannel, string> = {
  NONE: 'Sem canal de assistência',
  LIBRAS: 'Libras',
  TDD: 'TDD',
  VIDEO_SERVICE: 'Videoatendimento',
};

/** PriorityAttendanceProfile.common() do Java. */
export function commonProfile(): PriorityAttendanceProfile {
  return {
    category: 'NONE',
    companionPresent: false,
    accessibleFormat: 'NONE',
    assistanceChannel: 'NONE',
  };
}

/** PriorityAttendanceProfile.priority(categoria) do Java. */
export function priorityProfile(category: PriorityCategory): PriorityAttendanceProfile {
  if (!category || category === 'NONE') {
    throw new IllegalArgumentError('categoria prioritaria obrigatoria');
  }
  return {
    category,
    companionPresent: false,
    accessibleFormat: 'NONE',
    assistanceChannel: 'NONE',
  };
}

/** legalPriority(): categoria diferente de NONE (Lei 10.048/2000). */
export function legalPriority(profile: PriorityAttendanceProfile): boolean {
  return profile.category !== 'NONE';
}

/** needsAccessibilitySupport() do Java. */
export function needsAccessibilitySupport(profile: PriorityAttendanceProfile): boolean {
  return profile.accessibleFormat !== 'NONE' || profile.assistanceChannel !== 'NONE';
}

// ---------------------------------------------------------------------------
// KioskParameters
// ---------------------------------------------------------------------------

export const KIOSK_ENABLED_KEY = 'portal.kiosk_enabled';
export const WAIT_LIMIT_BUSINESS_DAY_KEY = 'sac.queue_wait_limit_business_day_minutes';
export const WAIT_LIMIT_HOLIDAY_ADJACENT_KEY = 'sac.queue_wait_limit_holiday_adjacent_minutes';

/** Valores de FABRICA da definition: Lei SC 12.465/2002 (KioskParameters). */
export const FACTORY_WAIT_LIMIT_BUSINESS_DAY_MINUTES = 15;
export const FACTORY_WAIT_LIMIT_HOLIDAY_ADJACENT_MINUTES = 30;

/** KioskParameters.kioskEnabled: ausencia = erro explicito, nunca fallback. */
export function kioskEnabledParam(raw: boolean | null | undefined): boolean {
  if (raw === null || raw === undefined) {
    throw new MissingParameterError(
      KIOSK_ENABLED_KEY,
      'modo quiosque nao configurado para o tenant',
    );
  }
  return raw;
}

function positiveParam(key: string, raw: number | null | undefined): number {
  if (raw === null || raw === undefined) {
    throw new MissingParameterError(
      key,
      'limite de espera nao configurado para o tenant (valor de fabrica da Lei SC 12.465/2002 consta da definition)',
    );
  }
  if (raw <= 0) throw new IllegalArgumentError(`${key} deve ser maior que zero: ${raw}`);
  return raw;
}

export function waitLimitBusinessDayMinutes(raw: number | null | undefined): number {
  return positiveParam(WAIT_LIMIT_BUSINESS_DAY_KEY, raw);
}

export function waitLimitHolidayAdjacentMinutes(raw: number | null | undefined): number {
  return positiveParam(WAIT_LIMIT_HOLIDAY_ADJACENT_KEY, raw);
}

// ---------------------------------------------------------------------------
// QueueTicket — senha e ciclo de vida (transicoes monotonicas, sem reescrita)
// ---------------------------------------------------------------------------

export type TicketStatus =
  | 'WAITING'
  | 'CALLED'
  | 'IN_SERVICE'
  | 'COMPLETED'
  | 'NO_SHOW'
  | 'CANCELLED';

export class QueueTicket {
  readonly id: string;
  readonly serviceUnitId: string;
  readonly sequenceNumber: number;
  readonly serviceType: string;
  readonly profile: PriorityAttendanceProfile;
  readonly issuedAt: string;
  private _status: TicketStatus = 'WAITING';
  private _calledAt: string | null = null;
  private _deskId: string | null = null;
  private _serviceStartedAt: string | null = null;
  private _finishedAt: string | null = null;

  constructor(
    id: string,
    serviceUnitId: string,
    sequenceNumber: number,
    serviceType: string,
    profile: PriorityAttendanceProfile,
    issuedAt: string,
  ) {
    this.id = requireNonNull(id, 'id da senha obrigatorio');
    this.serviceUnitId = requireNonNull(serviceUnitId, 'unidade de atendimento obrigatoria');
    if (sequenceNumber <= 0) {
      throw new IllegalArgumentError('sequencial da senha deve ser positivo');
    }
    this.sequenceNumber = sequenceNumber;
    if (!serviceType || serviceType.trim() === '') {
      throw new IllegalArgumentError('tipo de servico obrigatorio');
    }
    this.serviceType = serviceType.trim();
    this.profile = requireNonNull(profile, 'perfil de atendimento obrigatorio');
    this.issuedAt = requireNonNull(issuedAt, 'hora de emissao obrigatoria');
  }

  /** Codigo de exibicao: prefixo P (prioridade legal) ou N (comum) + sequencial %03d. */
  displayCode(): string {
    return (legalPriority(this.profile) ? 'P' : 'N') + String(this.sequenceNumber).padStart(3, '0');
  }

  call(at: string, deskId: string): void {
    requireNonNull(at, 'hora da chamada obrigatoria');
    if (!deskId || deskId.trim() === '') {
      throw new IllegalArgumentError('guiche/mesa da chamada obrigatorio');
    }
    if (this._status !== 'WAITING') {
      throw new IllegalStateError(`senha nao esta aguardando: ${this._status}`);
    }
    if (ldtMillis(at) < ldtMillis(this.issuedAt)) {
      throw new IllegalArgumentError('chamada anterior a emissao');
    }
    this._calledAt = at;
    this._deskId = deskId.trim();
    this._status = 'CALLED';
  }

  startService(at: string): void {
    requireNonNull(at, 'hora de inicio do atendimento obrigatoria');
    if (this._status !== 'CALLED') {
      throw new IllegalStateError(`senha nao esta chamada: ${this._status}`);
    }
    if (ldtMillis(at) < ldtMillis(this._calledAt as string)) {
      throw new IllegalArgumentError('inicio anterior a chamada');
    }
    this._serviceStartedAt = at;
    this._status = 'IN_SERVICE';
  }

  complete(at: string): void {
    requireNonNull(at, 'hora de encerramento obrigatoria');
    if (this._status !== 'IN_SERVICE') {
      throw new IllegalStateError(`senha nao esta em atendimento: ${this._status}`);
    }
    if (ldtMillis(at) < ldtMillis(this._serviceStartedAt as string)) {
      throw new IllegalArgumentError('encerramento anterior ao inicio');
    }
    this._finishedAt = at;
    this._status = 'COMPLETED';
  }

  markNoShow(at: string): void {
    requireNonNull(at, 'instante obrigatorio');
    if (this._status !== 'CALLED') {
      throw new IllegalStateError(`so senha chamada pode ser marcada ausente: ${this._status}`);
    }
    if (ldtMillis(at) < ldtMillis(this._calledAt as string)) {
      throw new IllegalArgumentError('ausencia anterior a chamada');
    }
    this._finishedAt = at;
    this._status = 'NO_SHOW';
  }

  cancel(at: string): void {
    requireNonNull(at, 'instante obrigatorio');
    if (this._status !== 'WAITING' && this._status !== 'CALLED') {
      throw new IllegalStateError(`senha nao pode ser cancelada neste estado: ${this._status}`);
    }
    if (ldtMillis(at) < ldtMillis(this.issuedAt)) {
      throw new IllegalArgumentError('cancelamento anterior a emissao');
    }
    this._finishedAt = at;
    this._status = 'CANCELLED';
  }

  /** Espera ate a chamada em segundos (emissao -> chamada); null enquanto nao chamada. */
  waitUntilCallSeconds(): number | null {
    return this._calledAt === null ? null : diffSeconds(this.issuedAt, this._calledAt);
  }

  /** Espera do relatorio RAS-26 em segundos (emissao -> INICIO); null enquanto nao iniciado. */
  waitingTimeSeconds(): number | null {
    return this._serviceStartedAt === null
      ? null
      : diffSeconds(this.issuedAt, this._serviceStartedAt);
  }

  serviceStarted(): boolean {
    return this._serviceStartedAt !== null;
  }

  status(): TicketStatus {
    return this._status;
  }
  calledAt(): string | null {
    return this._calledAt;
  }
  deskId(): string | null {
    return this._deskId;
  }
  serviceStartedAt(): string | null {
    return this._serviceStartedAt;
  }
  finishedAt(): string | null {
    return this._finishedAt;
  }
}

// ---------------------------------------------------------------------------
// TicketQueue — prioridade legal primeiro, depois emissao, desempate sequencial
// ---------------------------------------------------------------------------

export function orderWaiting(tickets: readonly QueueTicket[]): QueueTicket[] {
  return tickets
    .filter((t) => t.status() === 'WAITING')
    .sort((a, b) => {
      const pa = legalPriority(a.profile) ? 0 : 1;
      const pb = legalPriority(b.profile) ? 0 : 1;
      if (pa !== pb) return pa - pb;
      const byIssued = ldtMillis(a.issuedAt) - ldtMillis(b.issuedAt);
      if (byIssued !== 0) return byIssued;
      if (a.sequenceNumber !== b.sequenceNumber) return a.sequenceNumber - b.sequenceNumber;
      return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
    });
}

export function nextToCall(tickets: readonly QueueTicket[]): QueueTicket | null {
  const ordered = orderWaiting(tickets);
  return ordered.length === 0 ? null : (ordered[0] as QueueTicket);
}

// ---------------------------------------------------------------------------
// KioskTerminal — gate do parametro + lista FECHADA de acoes
// ---------------------------------------------------------------------------

export type KioskAction = 'ISSUE_TICKET' | 'ACCESSIBILITY_HELP' | 'VIEW_CALL_PANEL';

/** Lista fechada do contrato (QUIOSQUE-E-PAINEL-ED40.md / KioskTerminal.ALLOWED). */
export const KIOSK_ACTIONS: readonly KioskAction[] = [
  'ISSUE_TICKET',
  'ACCESSIBILITY_HELP',
  'VIEW_CALL_PANEL',
];

export class KioskTerminal {
  readonly deviceId: string;
  readonly serviceUnitId: string;
  private readonly enabled: boolean;

  private constructor(deviceId: string, serviceUnitId: string, enabled: boolean) {
    this.deviceId = requireNonNull(deviceId, 'dispositivo do totem obrigatorio');
    this.serviceUnitId = requireNonNull(
      serviceUnitId,
      'unidade de atendimento do totem obrigatoria',
    );
    this.enabled = enabled;
  }

  /**
   * UNICA fabrica (espelho de KioskTerminal.fromParameters): impoe
   * KioskParameters.kioskEnabled — ausencia do parametro = erro explicito.
   */
  static fromParameters(
    deviceId: string,
    serviceUnitId: string,
    kioskEnabledValue: boolean | null | undefined,
  ): KioskTerminal {
    return new KioskTerminal(deviceId, serviceUnitId, kioskEnabledParam(kioskEnabledValue));
  }

  kioskModeEnabled(): boolean {
    return this.enabled;
  }

  actionAllowed(action: KioskAction | null | undefined): boolean {
    return this.enabled && action != null && KIOSK_ACTIONS.includes(action);
  }

  issueTicket(
    ticketId: string,
    sequenceNumber: number,
    serviceType: string,
    profile: PriorityAttendanceProfile,
    now: string,
  ): QueueTicket {
    if (!this.enabled) {
      throw new IllegalStateError('modo quiosque desligado para este tenant/terminal');
    }
    return new QueueTicket(ticketId, this.serviceUnitId, sequenceNumber, serviceType, profile, now);
  }
}

// ---------------------------------------------------------------------------
// CallPanel (SG-626) — SO codigo, guiche e instante; nenhum dado pessoal
// ---------------------------------------------------------------------------

export interface CallAnnouncement {
  displayCode: string;
  deskId: string;
  serviceUnitId: string;
  calledAt: string;
  legalPriority: boolean;
}

export interface PanelView {
  current: CallAnnouncement | null;
  history: CallAnnouncement[];
}

/** CallPanel.snapshot: atual = chamada mais recente da unidade; historico desc. */
export function panelSnapshot(
  tickets: readonly QueueTicket[],
  serviceUnitId: string,
  historySize: number,
): PanelView {
  requireNonNull(serviceUnitId, 'unidade obrigatoria');
  if (historySize < 0) throw new IllegalArgumentError('historico nao pode ser negativo');
  const calls: CallAnnouncement[] = tickets
    .filter((t) => t.serviceUnitId === serviceUnitId && t.calledAt() !== null)
    .sort((a, b) => {
      const byCalled = ldtMillis(b.calledAt() as string) - ldtMillis(a.calledAt() as string);
      if (byCalled !== 0) return byCalled;
      const ca = a.displayCode();
      const cb = b.displayCode();
      return ca < cb ? -1 : ca > cb ? 1 : 0;
    })
    .map((t) => ({
      displayCode: t.displayCode(),
      deskId: t.deskId() as string,
      serviceUnitId: t.serviceUnitId,
      calledAt: t.calledAt() as string,
      legalPriority: legalPriority(t.profile),
    }));
  if (calls.length === 0) return { current: null, history: [] };
  return {
    current: calls[0] as CallAnnouncement,
    history: calls.slice(1, 1 + historySize),
  };
}

// ---------------------------------------------------------------------------
// HolidayCalendar — feriados sao DADO do tenant; nenhuma data no codigo
// ---------------------------------------------------------------------------

export class HolidayCalendar {
  private readonly holidays: ReadonlySet<string>;

  constructor(holidays: ReadonlySet<string>) {
    requireNonNull(holidays, 'calendario de feriados obrigatorio');
    this.holidays = new Set(holidays);
  }

  static of(holidays: ReadonlySet<string>): HolidayCalendar {
    return new HolidayCalendar(holidays);
  }

  isHoliday(date: string): boolean {
    return this.holidays.has(date);
  }

  /** Dia util = segunda a sexta que nao seja feriado do calendario. */
  isBusinessDay(date: string): boolean {
    const dow = dayOfWeek(date);
    return dow !== 0 && dow !== 6 && !this.isHoliday(date);
  }

  /** Feriado prolongado: sequencia maximal de nao-uteis que o contem tem >= 3 dias. */
  isProlongedHoliday(date: string): boolean {
    if (!this.isHoliday(date)) return false;
    let streak = 1;
    let d = addDays(date, -1);
    while (!this.isBusinessDay(d)) {
      streak++;
      d = addDays(d, -1);
    }
    d = addDays(date, 1);
    while (!this.isBusinessDay(d)) {
      streak++;
      d = addDays(d, 1);
    }
    return streak >= 3;
  }

  /** Vespora (ultimo dia util antes) ou dia seguinte (primeiro dia util depois). */
  isHolidayAdjacent(date: string): boolean {
    let d = addDays(date, 1);
    while (!this.isBusinessDay(d)) {
      if (this.isProlongedHoliday(d)) return true;
      d = addDays(d, 1);
    }
    let b = addDays(date, -1);
    while (!this.isBusinessDay(b)) {
      if (this.isProlongedHoliday(b)) return true;
      b = addDays(b, -1);
    }
    return false;
  }
}

// ---------------------------------------------------------------------------
// WaitingLimitPolicy — limite do dia contra a Lei SC 12.465/2002
// ---------------------------------------------------------------------------

export type LimitKind = 'BUSINESS_DAY' | 'HOLIDAY_ADJACENT';

export interface AppliedLimit {
  minutes: number;
  kind: LimitKind;
}

export interface WaitingLimitPolicy {
  calendar: HolidayCalendar;
  businessDayLimitMinutes: number;
  holidayAdjacentLimitMinutes: number;
}

export function makeWaitingLimitPolicy(
  calendar: HolidayCalendar,
  businessDayLimitMinutes: number,
  holidayAdjacentLimitMinutes: number,
): WaitingLimitPolicy {
  requireNonNull(calendar, 'calendario obrigatorio');
  if (businessDayLimitMinutes <= 0 || holidayAdjacentLimitMinutes <= 0) {
    throw new IllegalArgumentError('limites de espera devem ser maiores que zero');
  }
  return { calendar, businessDayLimitMinutes, holidayAdjacentLimitMinutes };
}

export function limitFor(policy: WaitingLimitPolicy, date: string): AppliedLimit {
  if (policy.calendar.isHolidayAdjacent(date)) {
    return { minutes: policy.holidayAdjacentLimitMinutes, kind: 'HOLIDAY_ADJACENT' };
  }
  return { minutes: policy.businessDayLimitMinutes, kind: 'BUSINESS_DAY' };
}

// ---------------------------------------------------------------------------
// WaitingTimeReport (RAS-26) — por unidade e por dia
// ---------------------------------------------------------------------------

export interface WaitingDayRow {
  serviceUnitId: string;
  date: string;
  limitMinutes: number;
  limitKind: LimitKind;
  startedCount: number;
  withinLimit: number;
  outsideLimit: number;
  /** Percentual dentro do limite, 2 casas (BigDecimal HALF_UP no Java). */
  withinPct: number;
  /** Espera media em minutos, 2 casas. */
  averageWaitMinutes: number;
  /** Maior espera arredondada ao minuto mais proximo (correcao D5 da ED-40). */
  maxWaitMinutes: number;
  /** Vivas ainda nao iniciadas (WAITING/CALLED) — NUNCA dentro de dentro/fora. */
  pendingStart: number;
  /** Encerradas sem inicio (NO_SHOW/CANCELLED) — contadas a parte (correcao D3). */
  closedWithoutStart: number;
}

function round2(value: number): number {
  // HALF_UP para valores nao-negativos, como o BigDecimal do relatorio.
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

/** WaitingTimeReport.generate: espera = emissao -> inicio do atendimento. */
export function generateWaitingReport(
  tickets: readonly QueueTicket[],
  policy: WaitingLimitPolicy,
): WaitingDayRow[] {
  if (tickets === null || tickets === undefined) {
    throw new IllegalArgumentError('senhas obrigatorias');
  }
  if (policy === null || policy === undefined) {
    throw new IllegalArgumentError('politica de limites obrigatoria');
  }
  const groups = new Map<string, QueueTicket[]>();
  for (const t of tickets) {
    const key = `${t.serviceUnitId}|${ldtDate(t.issuedAt)}`;
    const g = groups.get(key);
    if (g) g.push(t);
    else groups.set(key, [t]);
  }
  const rows: WaitingDayRow[] = [];
  for (const group of groups.values()) {
    const first = group[0] as QueueTicket;
    const date = ldtDate(first.issuedAt);
    const limit = limitFor(policy, date);
    let within = 0;
    let outside = 0;
    let pending = 0;
    let closedWithoutStart = 0;
    let sumSeconds = 0;
    let maxSeconds = 0;
    for (const t of group) {
      if (t.serviceStarted()) {
        const seconds = t.waitingTimeSeconds() as number;
        sumSeconds += seconds;
        maxSeconds = Math.max(maxSeconds, seconds);
        if (seconds <= limit.minutes * 60) within++;
        else outside++;
      } else if (t.status() === 'WAITING' || t.status() === 'CALLED') {
        pending++;
      } else {
        closedWithoutStart++;
      }
    }
    const started = within + outside;
    rows.push({
      serviceUnitId: first.serviceUnitId,
      date,
      limitMinutes: limit.minutes,
      limitKind: limit.kind,
      startedCount: started,
      withinLimit: within,
      outsideLimit: outside,
      withinPct: started === 0 ? 0 : round2((within * 100) / started),
      averageWaitMinutes: started === 0 ? 0 : round2(sumSeconds / 60 / started),
      maxWaitMinutes: Math.round(maxSeconds / 60),
      pendingStart: pending,
      closedWithoutStart,
    });
  }
  rows.sort((a, b) => {
    if (a.serviceUnitId !== b.serviceUnitId) {
      return a.serviceUnitId < b.serviceUnitId ? -1 : 1;
    }
    return a.date < b.date ? -1 : a.date > b.date ? 1 : 0;
  });
  return rows;
}
