/**
 * Fotos de campo do GIS (ED-66 / GIS-15, §8): gis_attachment com posicao,
 * azimute, hora e HASH, varridas pelo pipeline de anexos da P1.20
 * (ClamAV). A foto so vale para o cadastro quando CLEARED na varredura —
 * o mesmo criterio da foto da OS da ED-20 (FieldExecution.Photo).
 *
 * Privacidade (ED-20, OPE-11): a regra do desfoque de rostos
 * (field.photo_blur_faces) e a MESMA da ED-20 — com o parametro ligado,
 * foto sem o desfoque aplicado nao e utilizavel; a ausencia do parametro
 * no pacote e recusada na abertura da coleta (nunca presumida desligada),
 * espelhando o contrato D-3 da ED-20. E a flag `facesBlurred` NAO e
 * autodeclarada: {@link registerFieldPhoto} a deriva invocando a
 * FaceBlurPort da ED-20 (correcao D-2 de la; correcao m1 desta revisao). O rastreamento do executor segue a
 * governanca da ED-20 (so na jornada, aviso, chefia) — nenhum ponto de
 * trilha e criado por este modulo; a posicao da foto e a do FIX da
 * coleta, nao uma trilha.
 *
 * O HASH (SHA-256) e calculado pelo aparelho (porta nativa de arquivo);
 * aqui so a forma e conferida. A varredura e do servidor/pipeline P1.20:
 * no aparelho a foto nasce QUARANTINE ate o servidor devolver CLEARED.
 */
import type { GnssFix } from './gnss.ts';

export type PhotoScan = 'QUARANTINE' | 'CLEARED' | 'INFECTED';

export interface FieldPhoto {
  /** Referencia local do arquivo (URI no armazenamento cifrado do app). */
  localRef: string;
  featureId: string;
  /** SHA-256 do arquivo, hex minusculo (64 caracteres). */
  sha256: string;
  /** Posicao da foto: o fix da captura (lat/lon + solucao). */
  fix: GnssFix;
  /** Azimute da camera em graus [0, 360). */
  azimuthDeg: number;
  capturedAt: string;
  scan: PhotoScan;
  facesBlurred: boolean;
}

const SHA256_HEX = /^[0-9a-f]{64}$/;

export function isSha256Hex(value: string): boolean {
  return SHA256_HEX.test(value);
}

export type PhotoProblem =
  | 'HASH_INVALID' | 'AZIMUTH_OUT_OF_RANGE' | 'FIX_INVALID'
  | 'SCAN_NOT_CLEARED' | 'BLUR_REQUIRED_NOT_APPLIED' | 'BLUR_PARAM_MISSING'
  | 'BLUR_PORT_MISSING';

/**
 * Porta de desfoque de rostos da ED-20 (FaceBlurPort, correcao D-2 de
 * la): quem APLICA o desfoque e esta porta (aparelho/servico de imagem
 * do tenant); devolve true se o desfoque foi aplicado a foto.
 */
export interface FaceBlurPort {
  blurFaces(localRef: string): boolean;
}

/**
 * Registro de foto COM o elo do desfoque (correcao m1 da revisao,
 * espelho da correcao D-2 da ED-20): a flag `facesBlurred` NUNCA e
 * autodeclarada pelo chamador — ela DERIVA da invocacao da
 * {@link FaceBlurPort}:
 *  - parametro de desfoque AUSENTE (null) -> BLUR_PARAM_MISSING (D-3),
 *    a porta nem e invocada e foto nenhuma nasce;
 *  - desfoque exigido sem porta (null) -> BLUR_PORT_MISSING nomeado:
 *    registrar foto pulando o desfoque nao e caminho legitimo;
 *  - desfoque exigido com porta -> facesBlurred = retorno da porta
 *    (false da porta segue recusado depois, em photoProblems);
 *  - desfoque nao exigido -> a porta NAO e invocada e a foto nasce
 *    com facesBlurred = false, como na ED-20.
 */
export function registerFieldPhoto(
  input: Omit<FieldPhoto, 'facesBlurred'>,
  blurParam: boolean | null,
  blurPort: FaceBlurPort | null,
): { photo: FieldPhoto | null; problems: PhotoProblem[] } {
  if (blurParam === null) return { photo: null, problems: ['BLUR_PARAM_MISSING'] };
  if (!blurParam) return { photo: { ...input, facesBlurred: false }, problems: [] };
  if (blurPort === null) return { photo: null, problems: ['BLUR_PORT_MISSING'] };
  return { photo: { ...input, facesBlurred: blurPort.blurFaces(input.localRef) }, problems: [] };
}

export function photoProblems(photo: FieldPhoto, blurParam: boolean | null): PhotoProblem[] {
  const problems: PhotoProblem[] = [];
  if (!isSha256Hex(photo.sha256)) problems.push('HASH_INVALID');
  if (!(photo.azimuthDeg >= 0 && photo.azimuthDeg < 360)) problems.push('AZIMUTH_OUT_OF_RANGE');
  if (!(photo.fix.latitude >= -90 && photo.fix.latitude <= 90)
    || !(photo.fix.longitude >= -180 && photo.fix.longitude <= 180)) problems.push('FIX_INVALID');
  if (photo.scan !== 'CLEARED') problems.push('SCAN_NOT_CLEARED');
  if (blurParam === null) problems.push('BLUR_PARAM_MISSING');
  else if (blurParam && !photo.facesBlurred) problems.push('BLUR_REQUIRED_NOT_APPLIED');
  return problems;
}

/**
 * Foto utilizavel para sustentar delta/as-built: varrida (CLEARED), com
 * hash e azimute validos e o desfoque da ED-20 satisfeito. `blurParam`
 * null (parametro ausente no pacote) = NAO utilizavel, por desenho.
 */
export function isPhotoUsable(photo: FieldPhoto, blurParam: boolean | null): boolean {
  return photoProblems(photo, blurParam).length === 0;
}
