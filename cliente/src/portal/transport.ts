/**
 * Camada de transporte tipada do portal do consumidor (pacote
 * portal-consumidor-telas). Contem EXATAMENTE os endpoints provados em
 * codigo/documento entregue — nenhum outro:
 *
 * REST reais (controllers da P3.3):
 *  - POST /api/v1/portal/guest/invoice
 *      PortalGuestController.GuestInvoiceRequest{linkingCode, documentSuffix}
 *      -> GuestPaymentService.GuestInvoiceView{amount, dueDate,
 *         referenceMonth, digitableLine, pixCopyPaste} (5 campos, so estes)
 *  - POST /api/v1/portal/units/{unitId}/second-copy        -> PDF (bytes)
 *  - POST /api/v1/portal/units/{unitId}/second-copy/pix
 *      -> PortalConsumerController.PixResponse{pixCopyPaste}
 *
 * BFF do autoatendimento (contrato em ed-38/.../PORTAL-AMPLIADO-ED38.md;
 * formatos dos records dos services de ed-38/.../portal/selfcare/):
 *  - GET  /portal/faturas?unidade=            -> Poc69 BillSummary[]
 *  - GET  /portal/faturas/{id}/segunda-via    -> ref. do documento (String,
 *      Poc69SelfCareService.secondCopy / BillsPort.secondCopyDocumentRef)
 *  - GET  /portal/faturas/{id}/explicacao     -> BillExplanation.Explanation
 *  - POST /portal/simulacao/fatura            -> Poc69 Simulation
 *  - POST /portal/simulacao/parcelamento      -> Poc69 InstallmentSimulation
 *  - GET  /portal/certidao-quitacao-anual?ano= -> AuthenticDocument.Issued
 *  - GET  /portal/validar?tipo=&numero=&codigo= -> ver pendencia P-PORTAL-04
 *  - POST /portal/relatos                     -> AnonymousReportService.Outcome
 *  - GET  /portal/abastecimento?endereco=     -> PublicOutageStatus.StatusEntry[] (ED-35)
 *  - GET  /portal/agendamento/janelas         -> SelfCareScheduling.WindowOffer[]
 *  - POST /portal/agendamento                 -> referencia do agendamento (String)
 *
 * Centralizador (ED-22, CentralizerViewService): NAO e endpoint desta
 * camada. A rota esta truncada no documento da ED-38 (literal
 * "GET /portal/centralizador/..." em docs/portal/PORTAL-AMPLIADO-ED38.md)
 * e NAO ha controller/rota HTTP entregue que a prove — nenhuma URL,
 * metodo ou envelope e afirmado para o centralizador. A tela consome a
 * PORTA TIPADA CentralizerPort (abaixo), espelho campo a campo do
 * service, exactamente como as telas da ED-40 consomem DeskPort/
 * KioskIssuePort. P-PORTAL-01 fica reformulada como: "ecra e porta
 * tipada entregues; rota HTTP [A DEFINIR] na fiacao" — residual com o
 * mesmo estatuto das P-ED40T-01/02.
 */

/** Porta HTTP minima (fetch do navegador na integracao; dublê nos testes). */
export interface HttpPort {
  request<T>(method: 'GET' | 'POST', url: string, body?: unknown): Promise<T>;
}

// ---- P3.3: visitante -------------------------------------------------------

export interface GuestInvoiceRequest {
  linkingCode: string;
  documentSuffix: string;
}

/** GuestPaymentService.GuestInvoiceView — exatamente estes 5 campos. */
export interface GuestInvoiceView {
  amount: number;
  dueDate: string;
  referenceMonth: string;
  digitableLine: string;
  pixCopyPaste: string;
}

/** PortalConsumerController.PixResponse. */
export interface PixResponse {
  pixCopyPaste: string;
}

// ---- ED-38: faturas (Poc69SelfCareService.BillSummary) ---------------------

export type BillStatus = 'PENDING' | 'SETTLED';

export interface BillSummary {
  billId: string;
  reference: string;
  dueDate: string;
  amount: number;
  status: BillStatus;
}

// ---- ED-38: explicacao (BillExplanation.Explanation) -----------------------

export interface ExplanationFactor {
  code: string;
  text: string;
}

export interface BillExplanationView {
  factors: ExplanationFactor[];
  fullText: string;
}

// ---- ED-38: simulacao (Poc69SelfCareService) -------------------------------

export interface TariffBandInput {
  upToM3: number;
  pricePerM3: number;
}

/** TariffStructure: estrutura vigente do tenant; nada presumido na tela. */
export interface BillSimulationRequest {
  consumptionM3: number;
  tariff: {
    minimumFee: number;
    bands: TariffBandInput[];
    sewagePercent: number;
  };
}

export interface BillSimulation {
  consumptionM3: number;
  waterAmount: number;
  sewageAmount: number;
  totalAmount: number;
  simulationOnly: boolean;
}

export interface InstallmentSimulationRequest {
  totalDebt: number;
  installments: number;
  monthlyRate: number;
}

export interface InstallmentSimulation {
  totalDebt: number;
  installments: number;
  monthlyRate: number;
  amounts: number[];
  simulationOnly: boolean;
}

// ---- ED-38: certidao (AuthenticDocument.Issued) ----------------------------

export interface IssuedDocument {
  kind: 'CERTIFICATE' | 'DECLARATION' | 'RECEIPT';
  documentType: string;
  documentNumber: string;
  issuedOn: string;
  signatureLevel: string;
  verificationCode: string;
  qrPayload: string;
}

// ---- ED-38: validacao ------------------------------------------------------

/**
 * Resultado da validacao publica. O desfecho booleano vem de
 * AuthenticDocument.verifies(...) da ED-38; o ENVELOPE JSON desta resposta
 * nao tem fonte entregue — [SEM FONTE], pendencia P-PORTAL-04. So o campo
 * `valid` e consumido pela tela.
 */
export interface DocumentValidation {
  valid: boolean;
}

// ---- ED-38: relato anonimo (AnonymousReportService) ------------------------

export type ReportCategory =
  | 'LEAK' | 'NO_WATER' | 'LOW_PRESSURE' | 'WATER_QUALITY' | 'SEWAGE' | 'OTHER';

/** AnonymousReportService.Report: foto (photoRef) OBRIGATORIA, contato opcional. */
export interface AnonymousReportRequest {
  category: ReportCategory;
  locationText: string;
  photoRef: string;
  contactOrNull: string | null;
  description: string;
}

/** AnonymousReportService.Outcome. */
export interface ReportOutcome {
  protocolId: string;
  groupedIntoExisting: boolean;
  networkOccurrenceCreated: boolean;
}

// ---- ED-35 via ED-38: abastecimento (PublicOutageStatus.StatusEntry) -------

export interface OutageStatusEntry {
  locality: string;
  sector: string;
  route: string;
  start: string;
  expectedReturn: string;
  reasonOrNull: string | null;
  phase: 'ACTIVE' | 'SCHEDULED';
}

// ---- ED-38/ED-36: agendamento (SelfCareScheduling) --------------------------

export interface WindowOffer {
  windowId: string;
  start: string;
  freeSlots: number;
}

/** Campos de SelfCareScheduling.book(tenantId, serviceOrderId, windowId, start). */
export interface BookingRequest {
  serviceOrderId: string;
  windowId: string;
  start: string;
}

// ---- ED-22 via ED-38: centralizador (CentralizerViewService) --------------

/**
 * ConsumerAccessGuard.Channel (P3.20): o canal entra como parametro de
 * cada operacao do service (correcao D6 da ED-22); sem canal declarado,
 * o service assume OWN_ACCOUNT. GUEST e recusado pelo guard no service —
 * a recusa chega a tela como erro da porta, sem regra nova nesta camada.
 */
export type ConsumerChannel = 'OWN_ACCOUNT' | 'GOV_BR' | 'GUEST';

/**
 * CentralizerViewService.UnitSummary — espelho campo a campo do record
 * Java (unitId UUID, registration, openAmount BigDecimal; valores
 * monetarios como number, convencao das demais views deste portal).
 */
export interface UnitSummary {
  unitId: string;
  registration: string;
  openAmount: number;
}

/**
 * CentralizerViewService.GroupedBillView — espelho campo a campo do
 * record Java (code, reference YearMonth "AAAA-MM", total BigDecimal,
 * memberCount).
 */
export interface GroupedBillView {
  code: string;
  reference: string;
  total: number;
  memberCount: number;
}

/**
 * Porta tipada do centralizador: espelha a API publica do
 * CentralizerViewService (units / exportUnitsCsv / groupedBill, com as
 * sobrecargas de canal). NAO e um cliente HTTP: nenhuma rota esta
 * provada (ver cabecalho) — a fiacao entrega a implementacao quando a
 * rota HTTP for definida. Pendencia P-PORTAL-01 (residual): "ecra e
 * porta tipada entregues; rota HTTP [A DEFINIR] na fiacao".
 */
export interface CentralizerPort {
  units(
    tenantId: string,
    consumerSubject: string,
    centralizerCustomerId: string,
    channel?: ConsumerChannel,
  ): Promise<UnitSummary[]>;
  exportUnitsCsv(
    tenantId: string,
    consumerSubject: string,
    centralizerCustomerId: string,
    channel?: ConsumerChannel,
  ): Promise<string>;
  groupedBill(
    tenantId: string,
    consumerSubject: string,
    centralizerCustomerId: string,
    reference: string,
    channel?: ConsumerChannel,
  ): Promise<GroupedBillView>;
}

/**
 * Sessao que a fiacao entrega a tela do centralizador: os identificadores
 * que o service exige (tenant, sujeito do consumidor e centralizador) e
 * o canal declarado pelo chamador. Nao sao campos digitados pelo
 * utilizador — vem da sessao/vinculo, como o deskId das telas da ED-40.
 */
export interface CentralizerSession {
  tenantId: string;
  consumerSubject: string;
  centralizerCustomerId: string;
  channel?: ConsumerChannel;
}

/** Dependencias da tela do centralizador (porta + sessao da fiacao). */
export interface CentralizerScreenDeps {
  port: CentralizerPort;
  session: CentralizerSession;
}

const enc = encodeURIComponent;

export class PortalClient {
  private readonly http: HttpPort;

  constructor(http: HttpPort) {
    this.http = http;
  }

  guestInvoice(req: GuestInvoiceRequest): Promise<GuestInvoiceView> {
    return this.http.request('POST', '/api/v1/portal/guest/invoice', req);
  }

  listBills(unidade: string): Promise<BillSummary[]> {
    return this.http.request('GET', `/portal/faturas?unidade=${enc(unidade)}`);
  }

  /** Segunda via pelo BFF: devolve a referencia do documento gerado. */
  secondCopyBff(billId: string): Promise<string> {
    return this.http.request('GET', `/portal/faturas/${enc(billId)}/segunda-via`);
  }

  /** Segunda via REST da P3.3 (titular autenticado): PDF em bytes. */
  secondCopyPdf(unitId: string): Promise<ArrayBuffer> {
    return this.http.request('POST', `/api/v1/portal/units/${enc(unitId)}/second-copy`);
  }

  secondCopyPix(unitId: string): Promise<PixResponse> {
    return this.http.request('POST', `/api/v1/portal/units/${enc(unitId)}/second-copy/pix`);
  }

  billExplanation(billId: string): Promise<BillExplanationView> {
    return this.http.request('GET', `/portal/faturas/${enc(billId)}/explicacao`);
  }

  simulateBill(req: BillSimulationRequest): Promise<BillSimulation> {
    return this.http.request('POST', '/portal/simulacao/fatura', req);
  }

  simulateInstallments(req: InstallmentSimulationRequest): Promise<InstallmentSimulation> {
    return this.http.request('POST', '/portal/simulacao/parcelamento', req);
  }

  annualCertificate(ano: number): Promise<IssuedDocument> {
    return this.http.request('GET', `/portal/certidao-quitacao-anual?ano=${ano}`);
  }

  validateDocument(tipo: string, numero: string, codigo: string): Promise<DocumentValidation> {
    return this.http.request(
      'GET',
      `/portal/validar?tipo=${enc(tipo)}&numero=${enc(numero)}&codigo=${enc(codigo)}`,
    );
  }

  submitReport(req: AnonymousReportRequest): Promise<ReportOutcome> {
    return this.http.request('POST', '/portal/relatos', req);
  }

  outageStatus(endereco: string): Promise<OutageStatusEntry[]> {
    return this.http.request('GET', `/portal/abastecimento?endereco=${enc(endereco)}`);
  }

  schedulingWindows(): Promise<WindowOffer[]> {
    return this.http.request('GET', '/portal/agendamento/janelas');
  }

  book(req: BookingRequest): Promise<string> {
    return this.http.request('POST', '/portal/agendamento', req);
  }
}
