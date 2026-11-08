/**
 * Controlador das telas de OS em campo (R2-M3 / ED-20): orquestra as regras
 * REAIS do espelho TS (fieldOrder.ts) sem alterar nenhum contrato.
 *
 * Dependencias reais consumidas:
 *  - revalidateCut / identityMatches / nextVisibleState (fieldOrder.ts)
 *
 * PORTA DE INTEGRACAO (enquanto nula, a tela mostra a pendencia em vez de
 * fingir funcionamento):
 *  - getCurrentCutFacts: os fatos ATUAIS de corte (debito, contestacao,
 *    aviso, vedacoes) para revalidar offline. O retrato da emissao vem no
 *    pacote (CutSnapshot); os fatos atuais so existem na porta de fatos
 *    de corte da ED-20 (FieldOsPorts, P-ED20-02) — sem ela, a revalidacao
 *    declara "fatos atuais indisponiveis".
 *
 * A conclusao em TS e um passo LOCAL de estado visivel: o servidor
 * revalida TUDO na sincronizacao (FieldOsSyncService) e a falha cancela a
 * OS sem efeito na ligacao. Este controller NAO afirma que a OS esta
 * fechada no servidor — so que a execucao local chegou a CONCLUDED.
 */
import type { CutFacts, CutReason, ScannedCode, VisibleState } from '../../orders/fieldOrder.ts';
import {
  completionGate,
  nextState,
  orderProgress,
  revalidateOrderCut,
  orderIdentityMatches,
  addPhotoRef,
  type FieldOrder,
  type FieldOrderPackage,
  type OrderProgress,
} from './models.ts';
import { OrderNavigator } from './models.ts';

export interface OrderScreensDeps {
  pkg: FieldOrderPackage;
  /** Porta de integracao (P-ED20-02): fatos atuais de corte; null = indisponivel. */
  getCurrentCutFacts: ((order: FieldOrder) => Promise<CutFacts | null>) | null;
  now?: () => string;
}

export type AdvanceResult = { ok: true } | { ok: false; error: string };
export type CutRevalidationResult =
  | { ok: true; reasons: CutReason[] }
  | { ok: false; error: string };

export class OrderScreensController {
  readonly nav = new OrderNavigator();

  private readonly deps: OrderScreensDeps;
  private readonly states = new Map<string, VisibleState>();
  private readonly beforePhotos = new Map<string, string[]>();
  private readonly afterPhotos = new Map<string, string[]>();
  private readonly identityConfirmed = new Map<string, boolean>();
  private readonly cutResults = new Map<string, CutReason[]>();

  constructor(deps: OrderScreensDeps) {
    this.deps = deps;
    for (const order of deps.pkg.orders) {
      this.states.set(order.orderId, order.state);
      this.beforePhotos.set(order.orderId, []);
      this.afterPhotos.set(order.orderId, []);
    }
  }

  get pkg(): FieldOrderPackage {
    return this.deps.pkg;
  }

  progress(): OrderProgress {
    return orderProgress(this.deps.pkg, this.states);
  }

  order(orderId: string): FieldOrder {
    const o = this.deps.pkg.orders.find((x) => x.orderId === orderId);
    if (!o) throw new Error(`ordem fora do pacote: ${orderId}`);
    return o;
  }

  stateOf(orderId: string): VisibleState {
    return this.states.get(orderId) ?? this.order(orderId).state;
  }

  /**
   * Avanca UM estado visivel (regra real da ED-20). Avancar para COMPLETED
   * passa pelo portao de conclusao (IN_EXECUTION + fotos de antes/depois);
   * qualquer outro salto fora da ordem recusa com motivo nomeado.
   */
  advance(orderId: string): AdvanceResult {
    const current = this.stateOf(orderId);
    const target = nextState(current);
    if (target === null) return { ok: false, error: 'OS já concluída.' };
    if (target === 'COMPLETED') {
      const gate = completionGate(current, this.beforePhotos.get(orderId) ?? [], this.afterPhotos.get(orderId) ?? []);
      if (!gate.ok) return gate;
    }
    this.states.set(orderId, target);
    return { ok: true };
  }

  /** Fotos de ANTES referenciadas na ordem (copia). */
  beforePhotoRefs(orderId: string): string[] {
    return [...(this.beforePhotos.get(orderId) ?? [])];
  }

  /** Fotos de DEPOIS referenciadas na ordem (copia). */
  afterPhotoRefs(orderId: string): string[] {
    return [...(this.afterPhotos.get(orderId) ?? [])];
  }

  /** Anexa a referencia (URI local) da foto de ANTES; false quando vazia. */
  addBeforePhoto(orderId: string, uri: string): boolean {
    if (uri.trim() === '') return false;
    this.beforePhotos.set(orderId, addPhotoRef(this.beforePhotos.get(orderId) ?? [], uri));
    return true;
  }

  /** Anexa a referencia (URI local) da foto de DEPOIS; false quando vazia. */
  addAfterPhoto(orderId: string, uri: string): boolean {
    if (uri.trim() === '') return false;
    this.afterPhotos.set(orderId, addPhotoRef(this.afterPhotos.get(orderId) ?? [], uri));
    return true;
  }

  removeBeforePhoto(orderId: string, uri: string): void {
    this.beforePhotos.set(
      orderId,
      (this.beforePhotos.get(orderId) ?? []).filter((r) => r !== uri),
    );
  }

  removeAfterPhoto(orderId: string, uri: string): void {
    this.afterPhotos.set(
      orderId,
      (this.afterPhotos.get(orderId) ?? []).filter((r) => r !== uri),
    );
  }

  /** A identidade desta ordem ja foi confirmada nesta sessao? */
  isIdentityConfirmed(orderId: string): boolean {
    return this.identityConfirmed.get(orderId) ?? false;
  }

  /**
   * Confere o codigo escaneado (digitado + canal) contra a identidade
   * esperada do pacote. Sem identidade esperada no pacote, recusa com
   * motivo nomeado — nunca confirma no escuro.
   */
  confirmIdentity(orderId: string, scanned: ScannedCode): { ok: true } | { ok: false; error: string } {
    const expected = this.order(orderId).expectedIdentity ?? null;
    if (!expected) {
      return { ok: false, error: 'Ordem sem identidade esperada no pacote — confirmação indisponível.' };
    }
    const ok = orderIdentityMatches(expected, scanned);
    this.identityConfirmed.set(orderId, ok);
    return ok ? { ok: true } : { ok: false, error: 'Código de outra unidade — identidade não confirmada.' };
  }

  /** Ultima revalidacao de corte desta ordem (motivos), quando executada. */
  cutReasons(orderId: string): CutReason[] | null {
    return this.cutResults.get(orderId) ?? null;
  }

  /**
   * Revalida o corte offline (regra real da ED-20): retrato da emissao
   * contra os fatos ATUAIS da porta. Sem pacote de corte, sem porta ou
   * sem fatos atuais, recusa com motivo nomeado — nunca afirma que "pode
   * executar" sem os dois lados da regra.
   */
  async revalidateCut(orderId: string): Promise<CutRevalidationResult> {
    const order = this.order(orderId);
    const snapshot = order.cutSnapshot ?? null;
    if (!snapshot) {
      return { ok: false, error: 'Ordem sem pacote de corte — revalidação não se aplica.' };
    }
    if (!this.deps.getCurrentCutFacts) {
      return {
        ok: false,
        error: 'Fatos atuais de corte indisponíveis nesta integração (P-ED20-02) — revalidação pendente.',
      };
    }
    const current = await this.deps.getCurrentCutFacts(order);
    if (!current) {
      return { ok: false, error: 'Fatos atuais de corte indisponíveis — revalidação pendente.' };
    }
    const reasons = revalidateOrderCut(snapshot.factsAtIssue, current);
    this.cutResults.set(orderId, reasons);
    return { ok: true, reasons };
  }
}
