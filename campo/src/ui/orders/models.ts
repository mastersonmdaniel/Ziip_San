/**
 * Modelos de exibicao das telas de OS em campo (R2-M3 / ED-20) — logica PURA
 * de apresentacao sobre os contratos REAIS da ED-20, no mesmo padrao das
 * telas de leitura (ui/models.ts).
 *
 * Fontes dos contratos (espelho TS da ED-20):
 *  - CutSnapshot/CutFacts/CutReason/revalidateCut, ExpectedIdentity/
 *    ScannedCode/identityMatches, VisibleState/nextVisibleState:
 *    src/orders/fieldOrder.ts (ED-20)
 *  - Estados visiveis e rotulos: FieldExecution.java (ED-20) — EN_ROUTE
 *    "a caminho", ON_SITE "no local", IN_EXECUTION "em execucao",
 *    COMPLETED "concluida"; foto ANTES e DEPOIS obrigatorias, comprovante
 *    na conclusao; o servidor revalida TUDO na sincronizacao.
 *
 * O que os espelhos TS NAO expoem NAO aparece como controle funcional:
 * aceite por assinatura/codigo, pesquisa de satisfacao, materiais e GPS
 * vivem so no Java da ED-20 (CustomerAcceptance, SatisfactionSurvey,
 * MaterialUse, Tracking) sem espelho TS — a tela declara isso como
 * pendencia de integracao (P-ED20-02) em vez de fingir os controles.
 * Foto ANTES/DEPOIS entra como REFERENCIA (URI local), o mesmo padrao
 * honesto da foto de ocorrencia das telas de leitura (D-01): a captura
 * por camera segue pendencia (P-07); a varredura (P1.20) e o desfoque
 * (field.photo_blur_faces) sao validados pelo servidor na sync — a tela
 * nao afirma que a foto esta "liberada", so guarda as referencias.
 */
import {
  identityMatches,
  nextVisibleState,
  revalidateCut,
  type CutFacts,
  type CutReason,
  type CutSnapshot,
  type ExpectedIdentity,
  type ScannedCode,
  type VisibleState,
} from '../../orders/fieldOrder.ts';

// ---------------------------------------------------------------------------
// 1. Pacote de OS / ordem
// ---------------------------------------------------------------------------

/**
 * UMA ordem de servico no aparelho (entrada da integracao, como o
 * RoutePackage e para a leitura): o pacote de OS baixado do servidor.
 * `kindLabel` e o rotulo de exibicao do tipo de OS (ex.: "Corte",
 * "Ligacao") fornecido pelo pacote — a tela nao inventa o tipo: sem
 * rotulo no pacote, a ordem aparece so pelo numero. `cutSnapshot` so
 * existe em OS de corte (CutPackage da ED-20); `expectedIdentity` so
 * quando o pacote traz a identidade esperada (IdentityScan).
 */
export interface FieldOrder {
  orderId: string;
  unitId: string;
  taskId: string;
  kindLabel: string;
  unitAddress?: string | null;
  state: VisibleState;
  issuedAt: string;
  cutSnapshot?: CutSnapshot | null;
  expectedIdentity?: ExpectedIdentity | null;
}

/** Pacote de OS baixado (entrada da integracao; o servidor ainda nao emite este payload — RG2-C-P3). */
export interface FieldOrderPackage {
  orders: FieldOrder[];
}

/** Rotulo da ordem (R2-M4): endereco legivel quando o pacote o carrega; sem ele, so os IDs. */
export function orderLabel(order: FieldOrder): string {
  const base = `${order.kindLabel} · OS ${order.orderId} · unidade ${order.unitId}`;
  const address = order.unitAddress?.trim();
  return address ? `${base} · ${address}` : base;
}

/** Rotulo do estado visivel (FieldExecution.java). */
export function visibleStateLabel(state: VisibleState): string {
  switch (state) {
    case 'EN_ROUTE': return 'A caminho';
    case 'ON_SITE': return 'No local';
    case 'IN_EXECUTION': return 'Em execução';
    case 'COMPLETED': return 'Concluída';
  }
}

/** Rotulo do proximo estado visivel (o botao de avanco da tela). */
export function nextStateLabel(state: VisibleState): string | null {
  switch (state) {
    case 'EN_ROUTE': return 'Registrar chegada';
    case 'ON_SITE': return 'Iniciar execução';
    case 'IN_EXECUTION': return 'Concluir OS';
    case 'COMPLETED': return null;
  }
}

/** O estado visivel seguinte (regra real da ED-20); null quando nao ha. */
export function nextState(state: VisibleState): VisibleState | null {
  const target: VisibleState | null =
    state === 'EN_ROUTE' ? 'ON_SITE'
    : state === 'ON_SITE' ? 'IN_EXECUTION'
    : state === 'IN_EXECUTION' ? 'COMPLETED'
    : null;
  if (target === null) return null;
  return nextVisibleState(state, target);
}

/** Rotulo de um motivo de cancelamento da revalidacao de corte (CutReason real). */
export function cutReasonLabel(reason: CutReason): string {
  switch (reason) {
    case 'DEBIT_PAID_AFTER_ISSUE': return 'Débito quitado após a emissão do pacote';
    case 'DEBIT_PAID_AT_ISSUE': return 'Débito já quitado na emissão do pacote';
    case 'NO_OPEN_DEBIT': return 'Sem débito aberto';
    case 'CONTESTATION_OPEN': return 'Contestação aberta';
    case 'NOTICE_NOT_GIVEN': return 'Aviso prévio não comprovado';
    case 'LEGAL_PROHIBITION': return 'Vedação legal/regulatória';
  }
}

/** Rotulo do estado do debito no retrato do pacote de corte. */
export function debitStateLabel(state: CutFacts['debitState']): string {
  switch (state) {
    case 'OPEN': return 'aberto';
    case 'PAID': return 'quitado';
    case 'NONE': return 'sem débito';
  }
}

// ---------------------------------------------------------------------------
// 2. Progresso do pacote
// ---------------------------------------------------------------------------

export interface OrderRow {
  order: FieldOrder;
  state: VisibleState;
}

export interface OrderProgress {
  total: number;
  done: number;
  pending: number;
  rows: OrderRow[];
}

/** Progresso do pacote: concluida = estado COMPLETED; demais = pendentes. */
export function orderProgress(pkg: FieldOrderPackage, states: Map<string, VisibleState>): OrderProgress {
  const rows = pkg.orders.map((order) => ({
    order,
    state: states.get(order.orderId) ?? order.state,
  }));
  const done = rows.filter((r) => r.state === 'COMPLETED').length;
  return { total: rows.length, done, pending: rows.length - done, rows };
}

// ---------------------------------------------------------------------------
// 3. Fotos antes/depois (referencias, mesmo padrao da foto de ocorrencia)
// ---------------------------------------------------------------------------

/** Anexa a referencia (URI local) de UMA foto; vazia nao anexa, repetida nao duplica. */
export function addPhotoRef(refs: string[], uri: string): string[] {
  const t = uri.trim();
  if (t === '') return [...refs];
  return refs.includes(t) ? [...refs] : [...refs, t];
}

export type CompletionGate =
  | { ok: true }
  | { ok: false; error: string };

/**
 * Portao de conclusao LOCAL da tela: estado IN_EXECUTION + foto de ANTES e
 * foto de DEPOIS referenciadas. E o portao da INTERFACE — a prova de
 * varredura (P1.20) e de desfoque e do servidor na sincronizacao
 * (FieldExecution); a tela nao afirma que as fotos estao liberadas, so
 * que as referencias existem.
 */
export function completionGate(
  state: VisibleState,
  beforePhotos: string[],
  afterPhotos: string[],
): CompletionGate {
  if (state !== 'IN_EXECUTION') {
    return { ok: false, error: `OS em "${visibleStateLabel(state)}" não pode ser concluída.` };
  }
  if (beforePhotos.length === 0) {
    return { ok: false, error: 'A foto de ANTES é obrigatória para concluir a OS.' };
  }
  if (afterPhotos.length === 0) {
    return { ok: false, error: 'A foto de DEPOIS é obrigatória para concluir a OS.' };
  }
  return { ok: true };
}

// ---------------------------------------------------------------------------
// 4. Revalidacao de corte / identidade (regras puras da ED-20)
// ---------------------------------------------------------------------------

/** Revalida o corte: motivos de cancelamento (vazio = pode executar). */
export function revalidateOrderCut(atIssue: CutFacts, current: CutFacts): CutReason[] {
  return revalidateCut(atIssue, current);
}

/** Confere a identidade escaneada contra a esperada (regra real da ED-20). */
export function orderIdentityMatches(expected: ExpectedIdentity, scanned: ScannedCode): boolean {
  return identityMatches(expected, scanned);
}

// ---------------------------------------------------------------------------
// Navegacao minima entre as telas de OS
// ---------------------------------------------------------------------------

export type OrderScreenId = 'lista' | 'detalhe';

export const ORDER_SCREEN_TITLES: Record<OrderScreenId, string> = {
  lista: 'Ordens de serviço',
  detalhe: 'Ordem de serviço',
};

/** Maquina de navegacao pura (historico simples com voltar), padrao das telas de leitura. */
export class OrderNavigator {
  private currentScreen: OrderScreenId = 'lista';
  private readonly stack: OrderScreenId[] = [];
  private selectedOrderId: string | null = null;

  current(): OrderScreenId {
    return this.currentScreen;
  }

  orderId(): string | null {
    return this.selectedOrderId;
  }

  go(screen: OrderScreenId, orderId?: string): void {
    if (screen === this.currentScreen && orderId === undefined) return;
    this.stack.push(this.currentScreen);
    this.currentScreen = screen;
    if (orderId !== undefined) this.selectedOrderId = orderId;
  }

  openOrder(orderId: string): void {
    this.go('detalhe', orderId);
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
