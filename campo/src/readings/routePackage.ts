/**
 * Pacote de rota que o servidor envia/entrega ao aparelho (ED-18): uma rota de leitura para trabalhar
 * offline. Cada tarefa carrega o task_id gerado no servidor — e ele que o aparelho
 * devolve na sincronizacao (idempotencia) —, o historico minimo para a critica local
 * e o retrato do catalogo de ocorrencias da P2.5 vigente no tenant.
 */
import type {
  BillAdjustmentJson,
  FieldTariffSnapshotJson,
} from '../field/fieldTariffSnapshot.ts';

export interface OccurrenceSnapshot {
  code: string;
  requiresPhoto: boolean;
  requiresReading: boolean;
}

/**
 * Bloco de emissao por tarefa (P-03 / RG2-C-P2): espelho TS dos campos que o
 * FieldBillInput Java (ziipsan-billing-rules) ja carrega por tarefa no
 * RouteTask Java e que faltavam ao pacote TS — retrato de tarifa, economias,
 * debitos, creditos e vencimento — mais o barcodeValue do RouteTask Java.
 * Os demais campos da entrada de calculo (taskId, unitId, installationId,
 * previousReading, meterDigits) ja vivem na propria RouteTask TS.
 *
 * OPCIONAL de proposito (campo `emission` da RouteTask): pacotes de rota
 * emitidos antes deste bloco continuam validos — sem ele, a emissao da
 * tarefa responde UNAVAILABLE (ver routePackageEmission.ts). Dentro do
 * bloco, TODOS os campos sao obrigatorios: bloco parcial e recusa nomeada,
 * nunca emissao parcial. Nenhum campo alem dos que existem no Java e no
 * fixture de contrato (billing-contract-fixtures.json).
 *
 * `dueDate` chega como AAAA-MM-DD (o LocalDate do Java em ISO).
 * `barcodeValue` pode ser null: e o RouteTask Java — tarefa sem cobranca
 * resolvida imprime sem barras, nunca com digitos inventados. O
 * FieldTaskEmission vigente nao tem campo para o barcodeValue: ele viaja
 * no pacote para o chamador (documento/impressao), nao entra no inputJson.
 */
export interface RouteTaskEmissionData {
  snapshot: FieldTariffSnapshotJson;
  economies: number;
  debits: BillAdjustmentJson[];
  credits: BillAdjustmentJson[];
  dueDate: string;
  barcodeValue: string | null;
}

export interface RouteTask {
  taskId: string;
  unitId: string;
  installationId: string;
  kind: 'WATER' | 'WELL';
  reference: string; // AAAA-MM
  previousReading: number | null;
  meterDigits: number;
  sequence: number;
  /**
   * Endereco legivel da unidade (R2-M4 / P-01). OPCIONAL: pacotes de rota
   * emitidos antes desta correcao nao o carregam, e a tela segue exibindo
   * os IDs. O endereco ainda NAO tem fonte no servidor — nenhum cadastro
   * de unidade com endereco foi localizado (TS, Java, cadastro) —, entao
   * o preenchimento deste campo pelo montador do pacote de rota segue
   * pendencia nomeada (RG2-C-P2); aqui ele so e exibido quando presente,
   * nunca inventado.
   */
  unitAddress?: string | null;
  /**
   * Dados de emissao da tarefa (P-03 / RG2-C-P2). OPCIONAL: ausente em
   * pacotes antigos e em tarefas sem emissao resolvida no servidor —
   * nesses casos a emissao responde UNAVAILABLE, com honestidade.
   */
  emission?: RouteTaskEmissionData | null;
}

export interface RoutePackage {
  routeId: string;
  reference: string;
  downloadedAt: string;
  occurrences: OccurrenceSnapshot[];
  tasks: RouteTask[];
}

/** Leitura executada em campo, na fila local ate sincronizar. */
export interface PendingReading {
  taskId: string;
  reading: number | null;
  occurrenceCode: string | null;
  photoRefs: string[];
  ocrValue: number | null;
  capturedAt: string;
}
