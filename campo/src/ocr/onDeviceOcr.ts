/**
 * OCR do visor NO APARELHO (ED-18; OPE-34): o reconhecimento roda on-device
 * (ML Kit Text Recognition, processamento local) e devolve so os digitos.
 * A imagem do visor NUNCA entra no payload de sincronizacao — ver
 * buildSyncPayload e o seu teste: nao ha campo de imagem, por construcao.
 *
 * DPA: o ML Kit on-device nao envia a imagem a terceiro (processa localmente);
 * se um dia houver servico externo de OCR, ele entra no DPA antes de ligar
 * (docs/campo/POLITICA-MOVEL.md) e esta interface ganha um motor novo — a regra
 * de nao trafegar imagem nao muda.
 */
export interface OnDeviceOcrEngine {
  /** Reconhece os digitos de uma foto LOCAL (uri de arquivo do aparelho). */
  recognizeDigits(localPhotoUri: string): Promise<{ digits: string | null }>;
}

export interface SyncPayload {
  taskId: string;
  reading: number | null;
  occurrenceCode: string | null;
  photoRefs: string[];
  ocrValue: number | null;
  capturedAt: string;
}

/**
 * Monta o payload de sincronizacao de uma leitura. photoRefs sao as fotos de
 * OCORRENCIA (enviadas por upload separado, com hash); a foto do visor do OCR
 * nao e referenciada aqui nem enviada: so ocrValue (digitos) a representa.
 */
export function buildSyncPayload(p: {
  taskId: string;
  reading: number | null;
  occurrenceCode: string | null;
  occurrencePhotoRefs: string[];
  ocrValue: number | null;
  capturedAt: string;
}): SyncPayload {
  return {
    taskId: p.taskId,
    reading: p.reading,
    occurrenceCode: p.occurrenceCode,
    photoRefs: [...p.occurrencePhotoRefs],
    ocrValue: p.ocrValue,
    capturedAt: p.capturedAt,
  };
}
