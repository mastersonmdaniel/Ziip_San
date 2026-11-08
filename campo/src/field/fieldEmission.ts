/**
 * Emissao simultanea em campo (ED-19) — orquestracao no app (TypeScript).
 *
 * A ARITMETICA nao vive aqui: quem calcula e o artefato ziipsan-billing-rules
 * atras do plugin nativo BillingRules (o mesmo JAR do servidor). Esta camada
 * so orquestra a ordem do aceite:
 *
 *  1. critica por foto (OCR da ED-18) ANTES da emissao: veredito REVIEW
 *     segura a tarefa — nada e calculado nem impresso;
 *  2. hash do artefato conferido contra o pacote da rota: divergiu, a rota
 *     nao emite;
 *  3. certificado: tipo que exige assinatura (P3.34) sem certificado no
 *     aparelho nao emite — a tarefa fica retida como CERTIFICATE_MISSING
 *     para o servidor registrar, nunca "assinada" por simulacao;
 *  4. calculo pelo plugin -> impressao termica (comando gerado pelo
 *     ThermalRenderer do artefato; aqui so o transporte) -> conta retida;
 *  5. envio a cada N imoveis (field.send_every_n do pacote) e no fim da
 *     rota; contas retidas sao reenviadas e podem ser reimpressas com o
 *     MESMO resultado (a impressa nao muda).
 */

export type OcrVerdictName = 'DISABLED' | 'MATCH' | 'SUGGESTION' | 'REVIEW';

export interface FieldBillResultDto {
  taskId: string;
  consumptionM3: number;
  billedM3: number;
  waterValue: string;
  fixedValue: string;
  sewerValue: string;
  debitsValue: string;
  creditsApplied: string;
  creditsNotApplied: string;
  total: string;
  fingerprint: string;
}

export interface BillingRulesBridge {
  verifyArtifactHash(expectedSha256: string): Promise<{ match: boolean; actual: string }>;
  calculateFieldBill(inputJson: unknown): Promise<FieldBillResultDto>;
}

export interface ThermalPrinterBridge {
  /** Envia o comando termico (ZPL/CPCL) a Zebra ZQ por Bluetooth. */
  print(command: string): Promise<{ printed: boolean }>;
  /** Le o autotest do cabecote e a bateria (comandos da ZQ). */
  health(): Promise<{ headOk: boolean | null; batteryPct: number | null }>;
}

export interface ThermalRenderBridge {
  /** Renderiza modelo (P2.30) + documento no artefato compartilhado. */
  renderThermal(templateId: string, documentJson: unknown): Promise<{ command: string }>;
}

export interface FieldTaskEmission {
  taskId: string;
  inputJson: unknown;
  templateId: string;
  documentJson: unknown;
  ocrVerdict: OcrVerdictName;
  requiredSignatureLevel: 'SIMPLE' | 'ADVANCED' | 'QUALIFIED' | null;
  deviceCertificatePresent: boolean;
  signedLevel: 'SIMPLE' | 'ADVANCED' | 'QUALIFIED' | null;
}

export type EmissionOutcome =
  | { kind: 'PRINTED'; taskId: string; result: FieldBillResultDto }
  | { kind: 'OCR_HELD'; taskId: string }
  | { kind: 'CERTIFICATE_MISSING'; taskId: string }
  | { kind: 'ARTIFACT_HASH_MISMATCH'; taskId: string };

const LEVEL_RANK = { SIMPLE: 1, ADVANCED: 2, QUALIFIED: 3 } as const;

export class FieldEmission {
  private readonly retained: FieldBillResultDto[] = [];
  private readonly rules: BillingRulesBridge;
  private readonly printer: ThermalPrinterBridge;
  private readonly renderer: ThermalRenderBridge;
  private readonly expectedArtifactSha256: string;
  private readonly sendEveryN: number;

  constructor(
    rules: BillingRulesBridge,
    printer: ThermalPrinterBridge,
    renderer: ThermalRenderBridge,
    expectedArtifactSha256: string,
    sendEveryN: number,
  ) {
    if (sendEveryN < 1) {
      throw new Error(`field.send_every_n deve ser >= 1: ${sendEveryN}`);
    }
    this.rules = rules;
    this.printer = printer;
    this.renderer = renderer;
    this.expectedArtifactSha256 = expectedArtifactSha256;
    this.sendEveryN = sendEveryN;
  }

  /** Contas impressas e ainda nao enviadas (ordem de impressao). */
  retainedBills(): FieldBillResultDto[] {
    return [...this.retained];
  }

  /** Emite UMA tarefa, na ordem dos portoes do aceite. */
  async emit(task: FieldTaskEmission): Promise<EmissionOutcome> {
    if (task.ocrVerdict === 'REVIEW') {
      return { kind: 'OCR_HELD', taskId: task.taskId };
    }
    const hash = await this.rules.verifyArtifactHash(this.expectedArtifactSha256);
    if (!hash.match) {
      return { kind: 'ARTIFACT_HASH_MISMATCH', taskId: task.taskId };
    }
    if (task.requiredSignatureLevel !== null) {
      if (!task.deviceCertificatePresent || task.signedLevel === null) {
        return { kind: 'CERTIFICATE_MISSING', taskId: task.taskId };
      }
      if (LEVEL_RANK[task.signedLevel] < LEVEL_RANK[task.requiredSignatureLevel]) {
        return { kind: 'CERTIFICATE_MISSING', taskId: task.taskId };
      }
    }
    const result = await this.rules.calculateFieldBill(task.inputJson);
    const { command } = await this.renderer.renderThermal(task.templateId, task.documentJson);
    const printed = await this.printer.print(command);
    if (!printed.printed) {
      throw new Error(`Impressao nao confirmada pela impressora (task ${task.taskId}) — conta NAO retida como impressa`);
    }
    if (!this.retained.some((r) => r.taskId === result.taskId)) {
      this.retained.push(result);
    }
    return { kind: 'PRINTED', taskId: task.taskId, result };
  }

  /** Atingiu o multiplo de N? O chamador sincroniza e chama ackSent. */
  shouldFlush(): boolean {
    return this.retained.length > 0 && this.retained.length % this.sendEveryN === 0;
  }

  /** Fim da rota: qualquer saldo retido e enviado. */
  shouldFlushAtRouteEnd(): boolean {
    return this.retained.length > 0;
  }

  /** Baixa as contas confirmadas pelo servidor; desconhecida e erro. */
  ackSent(taskIds: string[]): void {
    for (const taskId of taskIds) {
      const idx = this.retained.findIndex((r) => r.taskId === taskId);
      if (idx < 0) {
        throw new Error(`ack de tarefa nao retida: ${taskId}`);
      }
      this.retained.splice(idx, 1);
    }
  }

  /**
   * Reimpressao de conta retida: usa o resultado GUARDADO (a impressa nao
   * muda) e o mesmo caminho de renderizacao/transporte.
   */
  async reprint(taskId: string, templateId: string, documentJson: unknown): Promise<void> {
    const retained = this.retained.find((r) => r.taskId === taskId);
    if (!retained) {
      throw new Error(`Reimpressao de tarefa nao retida: ${taskId}`);
    }
    const { command } = await this.renderer.renderThermal(templateId, documentJson);
    const printed = await this.printer.print(command);
    if (!printed.printed) {
      throw new Error(`Reimpressao nao confirmada pela impressora (task ${taskId})`);
    }
  }
}
