/**
 * Coleta de recadastramento no app de campo (ED-16 sobre a base da ED-18):
 * o pacote do lote baixa do servidor com os campos PRE-DEFINIDOS a coletar
 * por unidade (o mapa campo -> tipo de OS da P1.3/P1.31, resolvido no
 * servidor) e o aparelho devolve UM valor por tarefa na sincronizacao,
 * com o MESMO task_id gerado no servidor (idempotencia: o reenvio nao
 * duplica a coleta, como nas leituras da ED-18).
 *
 * Regras espelhadas do servidor (RecadastramentoService), para recusar
 * cedo no aparelho — a palavra final e sempre a do servidor, que registra
 * a recusa como erro de coleta:
 *  - campo fora da lista pre-definida da tarefa nao e coletavel;
 *  - valor vazio nao e coletavel;
 *  - o campo de documento (CUSTOMER.document) so viaja MASCARADO: o app
 *    recebe o valor digitado/conferido e o mascara antes de enfileirar
 *    (a mascara e a do cadastro: tudo, menos os 2 ultimos caracteres).
 */

export type RecadastramentoTargetType = 'CUSTOMER' | 'CONSUMER_UNIT';

export interface RecadastramentoTaskField {
  targetType: RecadastramentoTargetType;
  field: string;
  /** Tipo de OS do campo no mapa da P1.31 (informativo no aparelho). */
  osType: string;
}

export interface RecadastramentoTask {
  taskId: string;
  batchId: string;
  unitId: string;
  customerId: string | null;
  fields: RecadastramentoTaskField[];
}

/** Valor coletado em campo, na fila local ate sincronizar. */
export interface PendingRecadastramentoValue {
  taskId: string;
  batchId: string;
  unitId: string;
  customerId: string | null;
  targetType: RecadastramentoTargetType;
  field: string;
  collectedValue: string;
  valueMasked: boolean;
  photoRefs: string[];
  capturedAt: string;
}

/** Chave "ALVO.campo" de um campo da tarefa. */
export function fieldKey(targetType: RecadastramentoTargetType, field: string): string {
  return `${targetType}.${field}`;
}

/** O campo exige valor mascarado? (espelho de RecadastramentoFieldCatalog) */
export function requiresMaskedValue(targetType: RecadastramentoTargetType, field: string): boolean {
  return targetType === 'CUSTOMER' && field === 'document';
}

/** Mascara de documento do cadastro: tudo, menos os 2 ultimos caracteres. */
export function maskDocumentValue(value: string): string {
  const normalized = value.replace(/\D/g, '');
  if (normalized.length <= 2) {
    return '*'.repeat(normalized.length);
  }
  return '*'.repeat(normalized.length - 2) + normalized.slice(-2);
}

/**
 * Monta o valor pendente de uma tarefa, recusando o que o servidor
 * recusaria: campo fora do pre-definido da tarefa e valor vazio. Para o
 * campo de documento, o valor enfileirado e SEMPRE a mascara do digitado.
 */
export function buildPendingValue(
  task: RecadastramentoTask,
  targetType: RecadastramentoTargetType,
  field: string,
  rawValue: string,
  photoRefs: string[],
  capturedAt: string,
): PendingRecadastramentoValue {
  const declared = task.fields.find((f) => f.targetType === targetType && f.field === field);
  if (!declared) {
    throw new Error(`Campo fora do pre-definido da tarefa: ${fieldKey(targetType, field)}`);
  }
  const value = rawValue.trim();
  if (value.length === 0) {
    throw new Error(`Valor coletado vazio para o campo ${fieldKey(targetType, field)}`);
  }
  const masked = requiresMaskedValue(targetType, field);
  return {
    taskId: task.taskId,
    batchId: task.batchId,
    unitId: task.unitId,
    customerId: task.customerId,
    targetType,
    field,
    collectedValue: masked ? maskDocumentValue(value) : value,
    valueMasked: masked,
    photoRefs: [...photoRefs],
    capturedAt,
  };
}
