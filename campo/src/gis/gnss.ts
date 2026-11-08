/**
 * GNSS de campo (ED-66 / GIS-15, GIS-CADASTRO-TECNICO §8): o FIX que o
 * aparelho recebe do receptor externo e as regras puras sobre ele.
 *
 * Fontes REAIS do desenho (§8): receptor RTK externo por Bluetooth (NMEA)
 * via plugin nativo, ou localizacao simulada do Android alimentada pelo
 * receptor; correcao NTRIP pelo celular. NENHUM acesso a hardware vive
 * aqui: quem le o NMEA/Bluetooth e o plugin nativo (porta {@link GnssSource}),
 * pendencia de integracao — este modulo so recebe o fix ja parseado e
 * aplica as regras (tipo de solucao, PDOP, exatidoes, altura da antena).
 *
 * O metodo de captura derivado do fix usa EXATAMENTE a lista fechada da
 * coluna capture_method das feicoes (V13_2): RTK_FIXED, RTK_FLOAT,
 * GNSS_NAVIGATION (autonomo), TOTAL_STATION, TAPE_TIE, DIGITIZED,
 * CAD_IMPORT, AUTO_SERVICE_LINE, DESIGN, ESTIMATED.
 */

export type GnssTransport = 'BLUETOOTH_NMEA' | 'ANDROID_MOCK_LOCATION';

export type SolutionType = 'RTK_FIXED' | 'RTK_FLOAT' | 'AUTONOMOUS';

/** Metodo de captura da feicao (lista fechada da V13_2, §3.8). */
export type CaptureMethod =
  | 'RTK_FIXED' | 'RTK_FLOAT' | 'TOTAL_STATION' | 'GNSS_NAVIGATION'
  | 'TAPE_TIE' | 'DIGITIZED' | 'CAD_IMPORT' | 'AUTO_SERVICE_LINE'
  | 'DESIGN' | 'ESTIMATED';

export interface GnssFix {
  transport: GnssTransport;
  solution: SolutionType;
  latitude: number;
  longitude: number;
  /** Altitude ELIPSOIDAL (h) em metros — em transito: a conversao para
   *  altitude normal e NO SERVIDOR (hgeoHNOR2020, §3.9); o aparelho
   *  NUNCA grava esta altitude como altitude normal. */
  ellipsoidalHeightM: number | null;
  /** Exatidoes ESTIMADAS declaradas pelo receptor, em metros. */
  hAccuracyM: number | null;
  vAccuracyM: number | null;
  pdop: number | null;
  satellites: number | null;
  /** Altura da antena sobre o ponto, em metros (descontada no servidor
   *  junto da conversao; aqui so validada e transportada). */
  antennaHeightM: number | null;
  capturedAt: string;
}

/** Porta do receptor: implementada pelo plugin nativo (Bluetooth/NMEA). */
export interface GnssSource {
  currentFix(): Promise<GnssFix | null>;
}

/** Mapeia o tipo de solucao no metodo de captura da V13_2 (§3.8). */
export function captureMethodFor(fix: GnssFix): CaptureMethod {
  switch (fix.solution) {
    case 'RTK_FIXED': return 'RTK_FIXED';
    case 'RTK_FLOAT': return 'RTK_FLOAT';
    case 'AUTONOMOUS': return 'GNSS_NAVIGATION';
  }
}

export type GnssProblem =
  | 'LATITUDE_OUT_OF_RANGE' | 'LONGITUDE_OUT_OF_RANGE'
  | 'PDOP_MISSING' | 'PDOP_NOT_POSITIVE'
  | 'SATELLITES_MISSING' | 'SATELLITES_NOT_POSITIVE'
  | 'H_ACCURACY_MISSING' | 'V_ACCURACY_MISSING'
  | 'ACCURACY_NOT_POSITIVE' | 'ANTENNA_HEIGHT_NEGATIVE'
  | 'CAPTURED_AT_MISSING';

/**
 * Valida a FORMA do fix: o aceite da GIS-15 exige que cada ponto guarde
 * tipo de solucao e PDOP (§8) — fix sem PDOP nao e um fix de campo
 * completo. Nenhum LIMITE de PDOP/satelites e presumido aqui: o desenho
 * nao fixa teto de PDOP, logo teto nenhum e inventado (ver pendencia
 * P-ED66-03); a meta que vale e a de EXATIDAO do tenant (abaixo).
 */
export function validateFix(fix: GnssFix): GnssProblem[] {
  const problems: GnssProblem[] = [];
  if (!(fix.latitude >= -90 && fix.latitude <= 90)) problems.push('LATITUDE_OUT_OF_RANGE');
  if (!(fix.longitude >= -180 && fix.longitude <= 180)) problems.push('LONGITUDE_OUT_OF_RANGE');
  if (fix.pdop === null) problems.push('PDOP_MISSING');
  else if (!(fix.pdop > 0)) problems.push('PDOP_NOT_POSITIVE');
  if (fix.satellites === null) problems.push('SATELLITES_MISSING');
  else if (!(fix.satellites > 0)) problems.push('SATELLITES_NOT_POSITIVE');
  if (fix.hAccuracyM === null) problems.push('H_ACCURACY_MISSING');
  if (fix.vAccuracyM === null) problems.push('V_ACCURACY_MISSING');
  if ((fix.hAccuracyM !== null && !(fix.hAccuracyM > 0))
    || (fix.vAccuracyM !== null && !(fix.vAccuracyM > 0))) problems.push('ACCURACY_NOT_POSITIVE');
  if (fix.antennaHeightM !== null && fix.antennaHeightM < 0) problems.push('ANTENNA_HEIGHT_NEGATIVE');
  if (!fix.capturedAt) problems.push('CAPTURED_AT_MISSING');
  return problems;
}

/** Meta de exatidao do tenant para uma classe de feicao (gis.accuracy_targets, V9_6). */
export interface AccuracyTarget {
  hTargetM: number;
  vTargetM: number | null; // null = vertical nao se aplica a classe (§3.8)
}

export type AsBuiltRefusal =
  | 'FIX_INVALID' | 'NOT_RTK_FIXED' | 'H_OUT_OF_TARGET' | 'V_OUT_OF_TARGET';

/**
 * Portao do as-built (§8 + §6.4): as-built SO com RTK fixo dentro da meta
 * do tenant. A meta NAO e constante deste modulo: vem do pacote offline
 * (retrato de gis.accuracy_targets do tenant para a classe; o padrao de
 * fabrica do parametro e ±0,10 m H / ±0,05 m V — V9_6 — mas quem decide
 * o valor vigente e o tenant). Sem meta no pacote, o delta nem chama
 * esta funcao: gisDelta recusa com AS_BUILT_TARGET_MISSING.
 */
export function asBuiltRefusals(fix: GnssFix, target: AccuracyTarget): AsBuiltRefusal[] {
  const refusals: AsBuiltRefusal[] = [];
  if (validateFix(fix).length > 0) refusals.push('FIX_INVALID');
  if (fix.solution !== 'RTK_FIXED') refusals.push('NOT_RTK_FIXED');
  if (fix.hAccuracyM !== null && fix.hAccuracyM > target.hTargetM) refusals.push('H_OUT_OF_TARGET');
  if (target.vTargetM !== null && fix.vAccuracyM !== null && fix.vAccuracyM > target.vTargetM) {
    refusals.push('V_OUT_OF_TARGET');
  }
  return refusals;
}

/** true quando o fix pode sustentar um as-built para a classe (meta dada). */
export function isAsBuiltGrade(fix: GnssFix, target: AccuracyTarget): boolean {
  return asBuiltRefusals(fix, target).length === 0;
}
