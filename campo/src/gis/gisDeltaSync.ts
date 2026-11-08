/**
 * Envio dos deltas GIS (ED-66 / GIS-15) SOBRE o motor de sincronizacao
 * da ED-18 — NAO e um segundo motor: a ED-18 (SyncEngine + diretivas
 * BLOCK/RETIRE/NONE do aparelho registrado, ADR-0030) continua dona do
 * contato, da diretiva e do wipe. Este modulo e so o adaptador de
 * payload do GIS: prepara o lote de deltas pendentes e aplica os recibos
 * do servidor a fila local de deltas.
 *
 * Idempotencia (§8): o lote leva o client_change_id de cada delta; o
 * reenvio (queda no meio do envio, nova tentativa) reapresenta os
 * MESMOS ids — quem deduplica e o servidor (recibo DUPLICATE), como no
 * task_id da ED-18. Conflito por atributo volta no recibo com a lista
 * de campos e o delta sai da fila para a fila de conflito (resolucao
 * na web, §8/§6.3) — o aparelho NAO resolve conflito sozinho.
 */
import type { DeviceDirective } from '../sync/syncEngine.ts';
import type { GisDelta } from './gisDelta.ts';

export type DeltaReceiptOutcome = 'APPLIED' | 'DUPLICATE' | 'CONFLICT' | 'REJECTED';

export interface DeltaReceipt {
  clientChangeId: string;
  outcome: DeltaReceiptOutcome;
  /** Campos em conflito (so em CONFLICT): conflito por atributo. */
  conflictFields: string[];
  /** Motivo nomeado do servidor (so em REJECTED). */
  reason: string | null;
}

export interface DeltaQueueState {
  pending: GisDelta[];
  conflicted: { delta: GisDelta; conflictFields: string[] }[];
}

/** Validade do pacote de origem dos deltas, para a checagem do envio (M2). */
export interface PackageValidity {
  validUntil: string;
  nowIso: string;
}

/**
 * Prepara o lote para UMA tentativa de envio sob a diretiva vigente:
 *  - BLOCK: nada sai (o SyncEngine apaga tudo sem enviar);
 *  - RETIRE/NONE: os pendentes, na ordem de captura, com os ids originais.
 *
 * Correcao M2 da revisao: com a validade do pacote informada, pacote
 * VENCIDO recusa o envio com nome (PACKAGE_EXPIRED) e NENHUM delta sai
 * — antes a validade so era conferida na abertura do pacote e um lote
 * de pacote vencido podia ser montado em silencio. O servidor recusa
 * de novo na aplicacao (FieldDeltaApplier devolve REJECTED).
 */
export function prepareUpload(
  state: DeltaQueueState,
  directive: DeviceDirective,
  validity?: PackageValidity,
): GisDelta[] {
  if (directive === 'BLOCK') return [];
  if (validity && validity.nowIso > validity.validUntil) {
    throw new Error(
      'PACKAGE_EXPIRED: pacote offline vencido nao envia deltas '
      + `(validUntil=${validity.validUntil}, agora=${validity.nowIso})`,
    );
  }
  return [...state.pending].sort((a, b) => a.capturedAt.localeCompare(b.capturedAt));
}

/**
 * Aplica os recibos a fila: APPLIED e DUPLICATE saem dos pendentes
 * (DUPLICATE = o servidor ja tinha este client_change_id: o reenvio nao
 * duplicou); CONFLICT move o delta para a fila de conflito com os
 * campos; REJECTED permanece pendente? Nao — rejeitado tambem sai da
 * fila de envio e vai para a fila de conflito com o motivo como campo
 * sentinela, para o executor ver e a web resolver; nada e descartado
 * silenciosamente.
 */
export function applyReceipts(
  state: DeltaQueueState,
  receipts: DeltaReceipt[],
): DeltaQueueState {
  const byId = new Map(receipts.map((r) => [r.clientChangeId, r]));
  const pending: GisDelta[] = [];
  const conflicted = [...state.conflicted];
  for (const delta of state.pending) {
    const receipt = byId.get(delta.clientChangeId);
    if (!receipt) {
      pending.push(delta); // sem recibo: continua pendente para o proximo envio
      continue;
    }
    if (receipt.outcome === 'APPLIED' || receipt.outcome === 'DUPLICATE') continue;
    if (receipt.outcome === 'CONFLICT') {
      conflicted.push({ delta, conflictFields: [...receipt.conflictFields] });
      continue;
    }
    // REJECTED
    conflicted.push({
      delta,
      conflictFields: [`__rejected__:${receipt.reason ?? 'sem-motivo'}`],
    });
  }
  return { pending, conflicted };
}
