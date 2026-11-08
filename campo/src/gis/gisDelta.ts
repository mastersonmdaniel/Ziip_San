/**
 * Coleta e edicao offline como DELTAS (ED-66 / GIS-15, §8): cada mudanca
 * nasce com client_change_id (UUIDv7) gerado UMA vez, versao de base e
 * SO os campos alterados; os deltas sao aplicados no servidor num pacote
 * de edicao de tipo FIELD (gis_change_set, V13_9). O reenvio do mesmo
 * delta NAO duplica: o client_change_id e a chave de idempotencia e a
 * fila local nunca gera id novo por tentativa (mesmo desenho do task_id
 * da ED-18).
 *
 * Topologia e validacao DEFINITIVAS ficam no servidor (gatilhos nao
 * rodam na copia local, §8): o aparelho valida a forma e a meta de
 * captura; quem aplica e o servidor.
 */
import type { FieldFeature, GisFieldPackage } from './fieldPackage.ts';
import { isPackageExpired, targetFor, targetForClass } from './fieldPackage.ts';
import { asBuiltRefusals, captureMethodFor, type GnssFix } from './gnss.ts';

export type DeltaOperation = 'INSERT' | 'UPDATE' | 'RETIRE';

/**
 * Um delta de campo. `changedAttributes` traz SOMENTE os campos cujo
 * valor difere do retrato de base (UPDATE) ou todos os campos da coleta
 * (INSERT). `baseAttributes` e o retrato de base dos campos alterados —
 * e ele que o servidor compara campo a campo para o conflito por
 * atributo quando a versao de base esta desatualizada.
 */
export interface GisDelta {
  clientChangeId: string;
  packageId: string;
  operation: DeltaOperation;
  featureId: string;
  featureTable: FieldFeature['featureTable'];
  /** Versao de base da feicao; null somente em INSERT de coleta nova. */
  baseVersion: number | null;
  changedAttributes: Record<string, unknown>;
  baseAttributes: Record<string, unknown>;
  /** Nova geometria WKT (SRID 4674) quando a geometria mudou; senao null. */
  geomWkt: string | null;
  /** O fix GNSS da coleta/edicao (obrigatorio quando ha geometria nova). */
  fix: GnssFix | null;
  /** As-built: geometria medida que substitui a projetada (§6.4). */
  asBuilt: boolean;
  capturedAt: string;
}

const UUID_V7 = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** client_change_id e UUIDv7 (§8). A GERACAO e do chamador (relógio +
 *  aleatoriedade do aparelho); aqui so a forma e conferida. */
export function isUuidV7(value: string): boolean {
  return UUID_V7.test(value);
}

function sameValue(a: unknown, b: unknown): boolean {
  return JSON.stringify(a ?? null) === JSON.stringify(b ?? null);
}

/** Campos de `edited` que diferem do retrato de base (diff por atributo). */
export function diffAttributes(
  base: Record<string, unknown>,
  edited: Record<string, unknown>,
): { changed: Record<string, unknown>; baseOfChanged: Record<string, unknown> } {
  const changed: Record<string, unknown> = {};
  const baseOfChanged: Record<string, unknown> = {};
  for (const key of Object.keys(edited)) {
    if (!sameValue(base[key], edited[key])) {
      changed[key] = edited[key];
      baseOfChanged[key] = base[key] ?? null;
    }
  }
  return { changed, baseOfChanged };
}

export type DeltaProblem =
  | 'CLIENT_CHANGE_ID_NOT_UUIDV7'
  | 'FEATURE_NOT_IN_PACKAGE'
  | 'EMPTY_DELTA'
  | 'GEOMETRY_WITHOUT_FIX'
  | 'AS_BUILT_TARGET_MISSING'
  | 'AS_BUILT_FIX_REFUSED'
  | 'PACKAGE_EXPIRED'
  | 'CAPTURE_METHOD_MISSING';

/**
 * Monta um delta de EDICAO sobre uma feicao do pacote. Recusas nomeadas:
 * delta vazio (nada mudou) nao nasce; geometria nova sem fix GNSS nao
 * nasce; as-built sem meta da classe no pacote, ou com fix recusado pelo
 * portao (RTK fixo dentro da meta), nao nasce.
 */
export function buildUpdateDelta(args: {
  pkg: GisFieldPackage;
  feature: FieldFeature;
  editedAttributes: Record<string, unknown>;
  geomWkt: string | null;
  fix: GnssFix | null;
  asBuilt: boolean;
  clientChangeId: string;
  capturedAt: string;
  /** Instante da construcao para a validade do pacote (correcao M2);
   *  omitido = usa capturedAt (a coleta e o proprio instante). */
  nowIso?: string;
}): { delta: GisDelta | null; problems: DeltaProblem[] } {
  const problems: DeltaProblem[] = [];
  // M2: a validade do pacote e imposta TAMBEM na construcao do delta —
  // pacote vencido nao gera delta nenhum (nao so "nao abre").
  if (isPackageExpired(args.pkg, args.nowIso ?? args.capturedAt)) {
    problems.push('PACKAGE_EXPIRED');
  }
  if (!isUuidV7(args.clientChangeId)) problems.push('CLIENT_CHANGE_ID_NOT_UUIDV7');
  if (args.pkg.features.every((f) => f.featureId !== args.feature.featureId)) {
    problems.push('FEATURE_NOT_IN_PACKAGE');
  }
  const { changed, baseOfChanged } = diffAttributes(args.feature.attributes, args.editedAttributes);
  const geometryChanged = args.geomWkt !== null && args.geomWkt !== args.feature.geomWkt;
  if (Object.keys(changed).length === 0 && !geometryChanged && !args.asBuilt) {
    problems.push('EMPTY_DELTA');
  }
  if (geometryChanged && args.fix === null) problems.push('GEOMETRY_WITHOUT_FIX');
  if (args.asBuilt) {
    const target = targetFor(args.pkg, args.feature);
    if (target === null) problems.push('AS_BUILT_TARGET_MISSING');
    else if (args.fix === null) problems.push('GEOMETRY_WITHOUT_FIX');
    else if (asBuiltRefusals(args.fix, target).length > 0) problems.push('AS_BUILT_FIX_REFUSED');
  }
  if (problems.length > 0) return { delta: null, problems };
  return {
    problems,
    delta: {
      clientChangeId: args.clientChangeId,
      packageId: args.pkg.packageId,
      operation: 'UPDATE',
      featureId: args.feature.featureId,
      featureTable: args.feature.featureTable,
      baseVersion: args.feature.baseVersion,
      changedAttributes: changed,
      baseAttributes: baseOfChanged,
      geomWkt: geometryChanged ? args.geomWkt : null,
      fix: args.fix,
      asBuilt: args.asBuilt,
      capturedAt: args.capturedAt,
    },
  };
}

/**
 * Monta um delta de COLETA (feicao nova): sem versao de base; geometria
 * e fix obrigatorios.
 *
 * Correcao M1 da revisao: o as-built de INSERT passa pelo MESMO portao
 * de meta do UPDATE (decisao nº 5 vale nos dois verbos) — a classe da
 * feicao nova (`featureClass`) resolve a meta em gis.accuracy_targets
 * no pacote e o fix e conferido por asBuiltRefusals (RTK fixo DENTRO
 * da meta), nao so pelo tipo de solucao. Sem classe/meta, recusa
 * nomeada AS_BUILT_TARGET_MISSING; fora da meta, AS_BUILT_FIX_REFUSED.
 * O servidor confere de novo (FieldDeltaApplier, caminho INSERT).
 */
export function buildInsertDelta(args: {
  pkg: GisFieldPackage;
  featureId: string;
  featureTable: FieldFeature['featureTable'];
  /** Classe da feicao nova: resolve a meta do as-built (obrigatoria se asBuilt). */
  featureClass?: string;
  attributes: Record<string, unknown>;
  geomWkt: string;
  fix: GnssFix;
  asBuilt: boolean;
  clientChangeId: string;
  capturedAt: string;
  /** Instante da construcao para a validade do pacote (correcao M2). */
  nowIso?: string;
}): { delta: GisDelta | null; problems: DeltaProblem[] } {
  const problems: DeltaProblem[] = [];
  // M2: validade imposta tambem na construcao do INSERT.
  if (isPackageExpired(args.pkg, args.nowIso ?? args.capturedAt)) {
    problems.push('PACKAGE_EXPIRED');
  }
  if (!isUuidV7(args.clientChangeId)) problems.push('CLIENT_CHANGE_ID_NOT_UUIDV7');
  if (Object.keys(args.attributes).length === 0 && !args.geomWkt) problems.push('EMPTY_DELTA');
  if (args.asBuilt) {
    // M1: mesmo portao do UPDATE — meta da classe + asBuiltRefusals.
    const target = args.featureClass ? targetForClass(args.pkg, args.featureClass) : null;
    if (target === null) problems.push('AS_BUILT_TARGET_MISSING');
    else if (asBuiltRefusals(args.fix, target).length > 0) problems.push('AS_BUILT_FIX_REFUSED');
  }
  if (problems.length > 0) return { delta: null, problems };
  return {
    problems,
    delta: {
      clientChangeId: args.clientChangeId,
      packageId: args.pkg.packageId,
      operation: 'INSERT',
      featureId: args.featureId,
      featureTable: args.featureTable,
      baseVersion: null,
      changedAttributes: { ...args.attributes },
      baseAttributes: {},
      geomWkt: args.geomWkt,
      fix: args.fix,
      asBuilt: args.asBuilt,
      capturedAt: args.capturedAt,
    },
  };
}

/** Metodo de captura (V13_2) que o delta declara ao servidor, derivado do fix. */
export function deltaCaptureMethod(delta: GisDelta): string | null {
  return delta.fix ? captureMethodFor(delta.fix) : null;
}
