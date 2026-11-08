/**
 * Porta P-03 (R2-M2) — montagem do FieldTaskEmission a partir do retrato
 * de tarifa (FieldTariffSnapshot).
 *
 * FONTES REAIS (nada inventado):
 *  - o retrato Java: `ed-19/.../FieldTariffSnapshot.java` (record: tarifa,
 *    categoria, minimo, parcela fixa, faixas, esgoto, versoes de parametro);
 *  - o formato JSON do inputJson: `ed-19/.../billing-contract-fixtures.json`
 *    (6 casos; o caso `simples-uma-economia` e reproduzido pelo teste
 *    deste arquivo, campo a campo). O inputJson montado aqui tem
 *    EXATAMENTE as chaves do fixture — a tela nao calcula nada, so monta
 *    o documento que o artefato ziipsan-billing-rules consome.
 *
 * O QUE ESTE ARQUIVO NAO FAZ (pendencia RG2-C-P2, honesta): o retrato e
 * os insumos de emissao (economias, debitos/creditos, vencimento,
 * templateId, documentJson, nivel de assinatura, certificado) NAO estao
 * no RoutePackage TS da ED-18 — eles chegam pela integracao, via o
 * resolvedor de `createBuildEmissionTask`. Sem retrato, a fabrica NAO
 * devolve um FieldTaskEmission parcial: lanca o erro nomeado P-03, e o
 * controller o converte em UNAVAILABLE (a tela declara a indisponibilidade
 * em vez de fingir emissao). Nenhum valor e presumido: economias sem
 * fonte, template sem fonte ou vencimento sem fonte recusam com motivo
 * nomeado.
 */
import type { PendingReading, RouteTask } from '../readings/routePackage.ts';
import type { FieldTaskEmission, OcrVerdictName } from './fieldEmission.ts';

/** Uma faixa progressiva do retrato (espelho do TariffBracket Java). */
export interface TariffBracketJson {
  pricePerM3: number;
  /** Largura em m3; null = faixa aberta (absorve o restante). */
  widthM3: number | null;
}

/**
 * Retrato de tarifa em JSON (espelho do record Java + formato do fixture):
 * os mesmos campos, os mesmos nomes. `fixedChargePerEconomy` e `sewerPct`
 * chegam como numero no JSON do fixture (12.5 / 80), nao como string.
 */
export interface FieldTariffSnapshotJson {
  tariffVersionId: string;
  categoryId: string;
  minimumM3PerEconomy: number;
  fixedChargePerEconomy: number;
  brackets: TariffBracketJson[];
  sewerEnabled: boolean;
  sewerPct: number;
  parameterVersions: Record<string, string>;
}

/** Item de debito/credito do inputJson (formato do fixture: amount + code). */
export interface BillAdjustmentJson {
  amount: number;
  code: string;
}

/**
 * Insumos de emissao que NAO estao no retrato: a integracao os resolve por
 * tarefa (pacote de rota do servidor e/ou configuracao do tenant). Todos
 * sao obrigatorios quando presentes na assinatura — nenhum tem default.
 */
export interface FieldEmissionInputs {
  /** Economias da unidade na tarefa (>= 1). */
  economies: number;
  debits: BillAdjustmentJson[];
  credits: BillAdjustmentJson[];
  /** Vencimento da conta (AAAA-MM-DD, como no fixture). */
  dueDate: string;
  /** Modelo termico (P2.30) a renderizar. */
  templateId: string;
  /** Documento da conta a renderizar. */
  documentJson: unknown;
  ocrVerdict: OcrVerdictName;
  requiredSignatureLevel: 'SIMPLE' | 'ADVANCED' | 'QUALIFIED' | null;
  deviceCertificatePresent: boolean;
  signedLevel: 'SIMPLE' | 'ADVANCED' | 'QUALIFIED' | null;
}

/**
 * Valida o retrato com as MESMAS regras do construtor compacto Java:
 * faixas nao-vazias, aberta so a ultima, ultima aberta, minimo e fixa
 * nao-negativos, esgoto faturado exige percentual nao-negativo.
 */
export function assertValidSnapshot(snapshot: FieldTariffSnapshotJson): void {
  if (!snapshot.tariffVersionId) throw new Error('P-03: retrato sem tariffVersionId');
  if (!snapshot.categoryId) throw new Error('P-03: retrato sem categoryId');
  if (snapshot.minimumM3PerEconomy < 0) {
    throw new Error(`P-03: consumo minimo por economia negativo: ${snapshot.minimumM3PerEconomy}`);
  }
  if (snapshot.fixedChargePerEconomy < 0) {
    throw new Error(`P-03: parcela fixa negativa: ${snapshot.fixedChargePerEconomy}`);
  }
  if (!snapshot.brackets || snapshot.brackets.length === 0) {
    throw new Error('P-03: retrato sem nenhuma faixa tarifaria');
  }
  for (let i = 0; i < snapshot.brackets.length; i++) {
    if (snapshot.brackets[i].widthM3 === null && i !== snapshot.brackets.length - 1) {
      throw new Error(`P-03: faixa aberta so pode ser a ultima (indice ${i})`);
    }
  }
  if (snapshot.brackets[snapshot.brackets.length - 1].widthM3 !== null) {
    throw new Error('P-03: a ultima faixa do retrato deve ser aberta (absorve o restante)');
  }
  if (snapshot.sewerEnabled && snapshot.sewerPct < 0) {
    throw new Error(`P-03: percentual de esgoto negativo: ${snapshot.sewerPct}`);
  }
}

/**
 * Monta o FieldTaskEmission de UMA tarefa: o inputJson segue o formato do
 * fixture (taskId, unitId, installationId, currentReading, previousReading,
 * meterDigits, economies, debits, credits, dueDate, snapshot). A leitura
 * atual e obrigatoria para emitir: tarefa sem leitura (so ocorrencia)
 * recusa com motivo nomeado — o artefato nao calcula sem currentReading.
 */
export function buildFieldTaskEmission(
  task: RouteTask,
  reading: PendingReading,
  snapshot: FieldTariffSnapshotJson,
  inputs: FieldEmissionInputs,
): FieldTaskEmission {
  assertValidSnapshot(snapshot);
  if (reading.reading === null) {
    throw new Error(`P-03: emissao exige a leitura atual da tarefa ${task.taskId}`);
  }
  if (inputs.economies < 1) {
    throw new Error(`P-03: economias deve ser >= 1: ${inputs.economies}`);
  }
  if (inputs.dueDate.trim() === '') throw new Error('P-03: emissao sem vencimento (dueDate)');
  if (inputs.templateId.trim() === '') throw new Error('P-03: emissao sem templateId');
  return {
    taskId: task.taskId,
    inputJson: {
      taskId: task.taskId,
      unitId: task.unitId,
      installationId: task.installationId,
      currentReading: reading.reading,
      previousReading: task.previousReading,
      meterDigits: task.meterDigits,
      economies: inputs.economies,
      debits: inputs.debits,
      credits: inputs.credits,
      dueDate: inputs.dueDate,
      snapshot,
    },
    templateId: inputs.templateId,
    documentJson: inputs.documentJson,
    ocrVerdict: inputs.ocrVerdict,
    requiredSignatureLevel: inputs.requiredSignatureLevel,
    deviceCertificatePresent: inputs.deviceCertificatePresent,
    signedLevel: inputs.signedLevel,
  };
}

/** Resolve, por tarefa, o retrato + insumos; null = dado ausente na integracao. */
export type EmissionDataResolver = (
  task: RouteTask,
) => { snapshot: FieldTariffSnapshotJson; inputs: FieldEmissionInputs } | null;

/**
 * Fabrica da porta `buildEmissionTask` do controller: fecha o resolvedor da
 * integracao na assinatura `(task, reading) => FieldTaskEmission`. Sem
 * retrato/insumos para a tarefa, lanca o erro nomeado P-03 (o controller
 * converte em UNAVAILABLE — a tela declara, nao finge).
 */
export function createBuildEmissionTask(
  resolve: EmissionDataResolver,
): (task: RouteTask, reading: PendingReading) => FieldTaskEmission {
  return (task: RouteTask, reading: PendingReading) => {
    const resolved = resolve(task);
    if (!resolved) {
      throw new Error(
        `P-03: sem retrato de tarifa para a tarefa ${task.taskId} — emissao indisponivel nesta integracao`,
      );
    }
    return buildFieldTaskEmission(task, reading, resolved.snapshot, resolved.inputs);
  };
}
