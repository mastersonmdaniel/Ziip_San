/**
 * Pacote offline por area do campo GIS (ED-66 / GIS-15, §8): o retrato
 * que o servidor monta para UMA area e o aparelho carrega sem rede —
 * mapa base (PMTiles), feicoes da area, catalogo, listas de valores,
 * JSON Schemas, estilos, versao do pacote e validade, no mesmo padrao
 * da carga de rota da ED-18 (RoutePackage).
 *
 * Regras deste modulo (todas do §8/§3.8/§6.6):
 *  - camadas pessoais (feicoes ligadas a imovel/ligacao) so entram no
 *    pacote de quem tem a permissao gis.view-personal — o SERVIDOR
 *    monta sem elas para os demais; aqui a mesma regra e conferida na
 *    abertura do pacote (defesa em profundidade, nao substitui o
 *    servidor);
 *  - pacote vencido (validade) nao abre para coleta nova;
 *  - a meta de exatidao por classe (gis.accuracy_targets) viaja no
 *    pacote: sem meta para a classe, as-built daquela classe e recusado
 *    no aparelho (e de novo no servidor);
 *  - cada feicao carrega a VERSAO DE BASE e o retrato dos atributos:
 *    e sobre eles que o delta (gisDelta.ts) calcula o que mudou e o
 *    servidor detecta o conflito por atributo.
 */
import type { AccuracyTarget } from './gnss.ts';

export type GisPermission = 'gis.view' | 'gis.view-personal' | 'gis.edit';

export type FeatureTable =
  | 'gis_node' | 'gis_arc' | 'gis_connection' | 'gis_structure' | 'gis_zone' | 'gis_poi';

export interface FieldFeature {
  featureId: string;
  featureTable: FeatureTable;
  /** Codigo unico por tenant da feicao (coluna code, §3.5). */
  code: string;
  featureClass: string;
  featureTypeCode: string;
  /** Versao de base (coluna version da feicao no download). */
  baseVersion: number;
  lifecycleStatus: 'PLANNED' | 'UNDER_CONSTRUCTION' | 'IN_SERVICE' | 'ABANDONED';
  /** Retrato dos atributos tipados+JSONB na versao de base. */
  attributes: Record<string, unknown>;
  /** Geometria WKT (SRID 4674) na versao de base; null em coleta nova. */
  geomWkt: string | null;
  /** Camada pessoal: ligada a imovel/ligacao (exige gis.view-personal). */
  personal: boolean;
}

export interface GisFieldPackage {
  packageId: string;
  /** Identificacao da area (poligono/abrangencia e rotulo do servidor). */
  areaRef: string;
  packageVersion: number;
  issuedAt: string;
  validUntil: string;
  /** Referencia ao mapa base PMTiles local (arquivo ja baixado). */
  basemapRef: string;
  features: FieldFeature[];
  /** Codigos de tipo do catalogo vigentes no pacote (retrato). */
  catalogTypeCodes: string[];
  /** Listas de valores por codigo de lista (retrato). */
  valueLists: Record<string, string[]>;
  /** JSON Schema por tipo de feicao (retrato, para validacao local). */
  attributeSchemas: Record<string, unknown>;
  /** Chave do perfil de estilos (fonte unica, §10.2). */
  styleProfileKey: string;
  /** Meta de exatidao por CLASSE de feicao (gis.accuracy_targets). */
  accuracyTargets: Record<string, AccuracyTarget>;
}

export type PackageProblem =
  | 'PACKAGE_EXPIRED' | 'PERSONAL_LAYER_FORBIDDEN' | 'PACKAGE_ID_MISSING';

/** Problemas de abertura do pacote para um executor com as permissoes dadas. */
export function packageProblems(
  pkg: GisFieldPackage,
  permissions: GisPermission[],
  nowIso: string,
): PackageProblem[] {
  const problems: PackageProblem[] = [];
  if (!pkg.packageId) problems.push('PACKAGE_ID_MISSING');
  if (nowIso > pkg.validUntil) problems.push('PACKAGE_EXPIRED');
  const hasPersonal = pkg.features.some((f) => f.personal);
  if (hasPersonal && !permissions.includes('gis.view-personal')) {
    problems.push('PERSONAL_LAYER_FORBIDDEN');
  }
  return problems;
}

/** Pacote vencido no instante dado (comparacao ISO, mesmo criterio de packageProblems). */
export function isPackageExpired(pkg: GisFieldPackage, nowIso: string): boolean {
  return nowIso > pkg.validUntil;
}

/** Feicoes VISIVEIS para o executor: sem gis.view-personal, as pessoais somem. */
export function visibleFeatures(
  pkg: GisFieldPackage,
  permissions: GisPermission[],
): FieldFeature[] {
  if (permissions.includes('gis.view-personal')) return [...pkg.features];
  return pkg.features.filter((f) => !f.personal);
}

export function featureById(pkg: GisFieldPackage, featureId: string): FieldFeature | null {
  return pkg.features.find((f) => f.featureId === featureId) ?? null;
}

/**
 * Meta da classe da feicao no pacote; null = sem meta vigente no retrato
 * (as-built recusado — a meta e do tenant, nunca presumida no aparelho).
 */
export function targetFor(pkg: GisFieldPackage, feature: FieldFeature): AccuracyTarget | null {
  return targetForClass(pkg, feature.featureClass);
}

/**
 * Meta por CLASSE (correcao M1 da revisao da ED-66): a coleta nova
 * (INSERT) ainda nao tem feicao no pacote, mas tem CLASSE declarada —
 * e e pela classe que a meta de gis.accuracy_targets se resolve, no
 * aparelho e no servidor, para INSERT e UPDATE igualmente.
 */
export function targetForClass(pkg: GisFieldPackage, featureClass: string): AccuracyTarget | null {
  return pkg.accuracyTargets[featureClass] ?? null;
}
