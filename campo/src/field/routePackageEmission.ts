/**
 * P-03 / RG2-C-P2 — resolvedor de emissao a partir do pacote de rota.
 *
 * Fecha o elo que faltava entre o pacote enviado pelo servidor ao
 * aparelho e a porta que ja pousou:
 * `createBuildEmissionTask(resolver)` (fieldTariffSnapshot.ts) espera um
 * EmissionDataResolver; ate aqui nenhum resolvedor de producao existia,
 * porque o RoutePackage TS nao carregava os dados por tarefa. Com o bloco
 * opcional `emission` da RouteTask (routePackage.ts, espelho do
 * FieldBillInput/RouteTask Java), esta fabrica devolve o resolvedor real:
 *
 *   const build = createBuildEmissionTask(
 *     createPackageEmissionResolver(routePackage, deviceInputs));
 *
 * FONTES DOS DADOS (nada inventado, nenhum default):
 *  - retrato, economias, debitos, creditos e vencimento: do BLOCO DA
 *    TAREFA no pacote recebido (dados do servidor, montados no Java pelo
 *    montador do pacote de rota do metering);
 *  - templateId, documentJson, nivel de assinatura requerido e estado do
 *    certificado do aparelho: NAO sao do pacote — sao insumos de
 *    aparelho/boot, entradas explicitas e obrigatorias do chamador
 *    (FieldDeviceEmissionInputs), sem valor de fabrica;
 *  - ocrVerdict: vem do controller, como hoje — o chamador entrega a
 *    funcao que o resolve por tarefa (o controller expoe
 *    `ocrVerdict(taskId)`); o pacote nao carrega veredito nenhum.
 *
 * FALHA FECHADA:
 *  - tarefa sem bloco de emissao (ou fora do pacote): devolve null — o
 *    createBuildEmissionTask converte em erro nomeado P-03 e o controller
 *    responde UNAVAILABLE, comportamento honesto preservado;
 *  - bloco PRESENTE mas parcial (falta campo): lanca recusa nomeada
 *    P-03 listando o que falta — nunca monta emissao parcial;
 *  - retrato de forma invalida: quem recusa e o assertValidSnapshot do
 *    buildFieldTaskEmission, com as recusas do espelho Java.
 */
import type { RoutePackage, RouteTask } from '../readings/routePackage.ts';
import type { OcrVerdictName } from './fieldEmission.ts';
import type {
  EmissionDataResolver,
  FieldEmissionInputs,
} from './fieldTariffSnapshot.ts';

/**
 * Insumos de aparelho/boot que NAO vivem no pacote de rota. Todos
 * obrigatorios na assinatura — nenhum tem default: quem monta o app
 * (boot) declara o modelo termico e o documento a renderizar, o nivel de
 * assinatura que o tipo de conta exige e o estado real do certificado do
 * aparelho, alem da funcao de veredito OCR do controller.
 */
export interface FieldDeviceEmissionInputs {
  /** Modelo termico (P2.30) a renderizar. */
  templateId: string;
  /** Documento da conta a renderizar. */
  documentJson: unknown;
  requiredSignatureLevel: 'SIMPLE' | 'ADVANCED' | 'QUALIFIED' | null;
  deviceCertificatePresent: boolean;
  signedLevel: 'SIMPLE' | 'ADVANCED' | 'QUALIFIED' | null;
  /** Veredito OCR da tarefa — vem do controller, como hoje. */
  ocrVerdictFor: (task: RouteTask) => OcrVerdictName;
}

/**
 * Devolve o EmissionDataResolver que le os dados por tarefa do pacote
 * recebido. O pacote e a fonte: a tarefa recebida so e usada pelo taskId
 * (a emissao resolve sobre a tarefa DO PACOTE, nao sobre uma copia).
 */
export function createPackageEmissionResolver(
  routePackage: RoutePackage,
  device: FieldDeviceEmissionInputs,
): EmissionDataResolver {
  const byTaskId = new Map(routePackage.tasks.map((t) => [t.taskId, t]));
  return (task: RouteTask) => {
    const pkgTask = byTaskId.get(task.taskId);
    const block = pkgTask?.emission;
    if (!pkgTask || block == null) {
      // Sem bloco (pacote antigo, tarefa sem emissao resolvida ou tarefa
      // fora do pacote): UNAVAILABLE honesto, via o erro nomeado P-03 do
      // createBuildEmissionTask.
      return null;
    }
    const missing: string[] = [];
    if (block.snapshot == null || typeof block.snapshot !== 'object') missing.push('snapshot');
    if (typeof block.economies !== 'number') missing.push('economies');
    if (!Array.isArray(block.debits)) missing.push('debits');
    if (!Array.isArray(block.credits)) missing.push('credits');
    if (typeof block.dueDate !== 'string' || block.dueDate.trim() === '') missing.push('dueDate');
    if (!('barcodeValue' in block)) missing.push('barcodeValue');
    if (missing.length > 0) {
      throw new Error(
        `P-03: bloco de emissao parcial da tarefa ${task.taskId} (faltam: ${missing.join(', ')})`
        + ' — emissao recusada, nunca parcial',
      );
    }
    const inputs: FieldEmissionInputs = {
      economies: block.economies,
      debits: block.debits,
      credits: block.credits,
      dueDate: block.dueDate,
      templateId: device.templateId,
      documentJson: device.documentJson,
      ocrVerdict: device.ocrVerdictFor(pkgTask),
      requiredSignatureLevel: device.requiredSignatureLevel,
      deviceCertificatePresent: device.deviceCertificatePresent,
      signedLevel: device.signedLevel,
    };
    return { snapshot: block.snapshot, inputs };
  };
}
