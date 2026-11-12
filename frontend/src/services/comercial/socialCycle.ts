/**
 * P-II3-TELA — porta tipada do ciclo da tarifa social e da busca activa.
 *
 * Espelho campo a campo dos records do núcleo Java na main
 * (`commercial/ziipsan-billing/.../billing/social/cycle/` e
 * `billing/social/SocialEligibility.java`). NENHUMA rota HTTP é afirmada
 * aqui: não existe controlador REST para este núcleo na main — a fiação
 * HTTP é a pendência P-II3-INTEGRACAO, que esta tela NÃO fecha. A tela
 * consome apenas esta porta; sem implementação ligada, mostra o estado
 * "fiação pendente" em vez de dados.
 *
 * Formato da lista: o leiaute oficial CadÚnico/BPC é [SEM FONTE]
 * (pendência P-II3-LEIAUTE). O que a tela declara é o formato DECLARADO
 * do núcleo (`DeclaredSocialListFormat.proofFormat()`), nomeado como
 * tal. Parâmetros legais são [A DEFINIR] (pendência P-II3-PARAM): os
 * únicos valores concretos são os de PROVA fixados pela P1.5
 * (`SocialTariffCycleParameters.proofParameters()` = 24/3/3, sem
 * intervalo de recertificação), rotulados como prova.
 */

/** SocialEligibility.Basis (SocialEligibility.java). */
export type SocialBasis = 'CADUNICO' | 'BPC' | 'LOCAL_PROGRAM'

/** DeclaredSocialListFormat (DeclaredSocialListFormat.java:36). */
export interface DeclaredSocialListFormat {
  formatId: string
  version: string
  /** char no Java; aqui a string de 1 carácter do delimitador. */
  delimiter: string
  columns: string[]
}

/**
 * Formato DECLARADO de prova do núcleo — NÃO é o leiaute oficial
 * CadÚnico/BPC ([SEM FONTE], P-II3-LEIAUTE).
 * Fonte: DeclaredSocialListFormat.proofFormat() (linhas 71-75).
 */
export const DECLARED_PROOF_FORMAT: DeclaredSocialListFormat = {
  formatId: 'II3-LISTA-SOCIAL-DECLARADA',
  version: '1',
  delimiter: ';',
  columns: [
    'family_code_hash',
    'person_document_hash',
    'person_role',
    'eligibility_basis',
    'cadunico_updated_on',
    'benefit_valid_from',
    'benefit_valid_to',
    'municipality_code',
  ],
}

/** A linha de cabeçalho esperada (DeclaredSocialListFormat.headerLine()). */
export function declaredHeaderLine(format: DeclaredSocialListFormat): string {
  return format.columns.join(format.delimiter)
}

/**
 * SocialTariffCycleParameters (SocialTariffCycleParameters.java:32).
 * `recertificationIntervalMonths` é OptionalInt no Java: null = ausente
 * ([A DEFINIR]).
 */
export interface SocialTariffCycleParameters {
  cadunicoMaxAgeMonths: number
  lossNoticeMonths: number
  graceMonths: number
  recertificationIntervalMonths: number | null
}

/**
 * Valores de PROVA declarados no núcleo (proofParameters(): 24/3/3,
 * recertificação ausente). Não são valores de produção nem de tenant.
 */
export const PROOF_PARAMETERS: SocialTariffCycleParameters = {
  cadunicoMaxAgeMonths: 24,
  lossNoticeMonths: 3,
  graceMonths: 3,
  recertificationIntervalMonths: null,
}

/** SocialListImportPort.ImportedBeneficiaryRow (SocialListImportPort.java:42). */
export interface ImportedBeneficiaryRow {
  familyCodeHash: string
  personDocumentHash: string
  personRole: string
  basis: SocialBasis
  cadunicoUpdatedOn: string | null
  benefitValidFrom: string | null
  benefitValidTo: string | null
  municipalityCode: string | null
}

/** SocialListImportPort.ImportRejection (SocialListImportPort.java:66). */
export interface ImportRejection {
  lineNumber: number
  field: string
  code: string
  message: string
}

/** SocialListImportPort.ImportOutcome (SocialListImportPort.java:73). */
export interface ImportOutcome {
  accepted: ImportedBeneficiaryRow[]
  duplicatesRemoved: ImportedBeneficiaryRow[]
  rejections: ImportRejection[]
}

/** SocialCrossingEngine.UnitView (SocialCrossingEngine.java:37). */
export interface CrossingUnitView {
  unitId: string
  holderCustomerId: string
  holderAndMemberDocumentHashes: string[]
  cadunicoResponsible: boolean
  sameAddress: boolean
  familyHead: boolean
  connectionDate: string | null
  municipalityCode: string | null
  routeCode: string | null
}

/** SocialCrossingEngine.CrossedFamily (SocialCrossingEngine.java:54). */
export interface CrossedFamily {
  familyCodeHash: string
  basis: SocialBasis
  cadunicoUpdatedOn: string | null
  benefitValidFrom: string | null
  benefitValidTo: string | null
  unitId: string
  holderCustomerId: string
  municipalityCode: string | null
  routeCode: string | null
  personCount: number
}

/** SocialCrossingEngine.UnmatchedFamily (SocialCrossingEngine.java:61). */
export interface UnmatchedFamily {
  familyCodeHash: string
  basis: SocialBasis
  municipalityCode: string | null
  personCount: number
}

/** SocialCrossingEngine.IneligibleFamily (SocialCrossingEngine.java:66). */
export interface IneligibleFamily {
  familyCodeHash: string
  basis: SocialBasis
  personCount: number
}

/** SocialCrossingEngine.DivergenceKind (SocialCrossingEngine.java:70). */
export type DivergenceKind = 'NO_UNIT_MATCH' | 'INELIGIBLE'

/** SocialCrossingEngine.Divergence (SocialCrossingEngine.java:78). */
export interface CrossingDivergence {
  familyCodeHash: string
  kind: DivergenceKind
  detail: string
}

/** SocialCrossingEngine.CrossingResult (SocialCrossingEngine.java:82). */
export interface CrossingResult {
  matched: CrossedFamily[]
  unmatched: UnmatchedFamily[]
  ineligible: IneligibleFamily[]
  divergences: CrossingDivergence[]
}

/** SocialGrantLifecycle.GrantStatus (SocialGrantLifecycle.java:50). */
export type GrantStatus = 'ELIGIBLE' | 'GRANTED' | 'UNDER_REVIEW' | 'GRACE' | 'ENDED'

/** SocialGrantLifecycle.RefusalReason (SocialGrantLifecycle.java:55). */
export type GrantRefusalReason =
  | 'INELIGIBLE'
  | 'OUTSIDE_VALIDITY'
  | 'LEGAL_RULE_NOT_YET_APPLICABLE'
  | 'LIST_SOURCE_NOT_ALLOWED'

/** SocialGrantLifecycle.TrailEntry (SocialGrantLifecycle.java:84). */
export interface GrantTrailEntry {
  sequence: number
  occurredAt: string
  fromStatus: GrantStatus | null
  toStatus: GrantStatus
  reasonCode: string | null
  actorId: string | null
}

/** SocialGrantLifecycle.Grant (SocialGrantLifecycle.java:89). */
export interface SocialGrant {
  grantId: string
  unitId: string
  holderCustomerId: string
  familyCodeHash: string
  basis: SocialBasis
  cadunicoUpdatedOn: string | null
  validFrom: string | null
  validTo: string | null
  status: GrantStatus
  lossNoticeStartedOn: string | null
  graceStartedOn: string | null
  endReasonCode: string | null
  legalBasis: string | null
  trail: GrantTrailEntry[]
}

/** SocialTariffCycleService.GrantRefusal (SocialTariffCycleService.java:58). */
export interface CycleGrantRefusal {
  familyCodeHash: string
  reason: GrantRefusalReason
}

/** SocialActiveSearch.Reason (SocialActiveSearch.java:44). */
export type ActiveSearchReason = 'MATCHED_NOT_ENROLLED' | 'NO_UNIT_MATCH'

/** SocialActiveSearch.ActiveSearchEntry (SocialActiveSearch.java:49). */
export interface ActiveSearchEntry {
  familyCodeHash: string
  basis: SocialBasis
  municipalityCode: string | null
  routeCode: string | null
  reason: ActiveSearchReason
}

/** SocialActiveSearch.GroupKey (SocialActiveSearch.java:54). */
export interface ActiveSearchGroupKey {
  municipalityCode: string | null
  routeCode: string | null
}

/** SocialCycleStore.FamilyCrossingSummary.Outcome (SocialCycleStore.java:34). */
export type CrossingOutcome = 'MATCHED' | 'UNMATCHED' | 'INELIGIBLE'

/** SocialCycleStore.FamilyCrossingSummary (SocialCycleStore.java:32). */
export interface FamilyCrossingSummary {
  familyCodeHash: string
  basis: string
  outcome: CrossingOutcome
  matchedUnitId: string | null
  municipalityCode: string | null
  routeCode: string | null
  personCount: number
}

/** SocialTariffCycleService.CycleReport (SocialTariffCycleService.java:62). */
export interface CycleReport {
  importedRows: number
  duplicatesRemoved: number
  importRejections: ImportRejection[]
  crossing: CrossingResult
  grantsCreated: SocialGrant[]
  alreadyEnrolled: number
  grantRefusals: CycleGrantRefusal[]
  activeSearch: ActiveSearchEntry[]
}

/**
 * Entrada de uma corrida do ciclo — os argumentos de
 * `SocialTariffCycleService.runCycle(...)`: conteúdo da lista, data de
 * referência, natureza jurídica e lista de origem (valores declarados
 * pelo operador; a tela não presume nenhum).
 */
export interface RunCycleInput {
  listContent: string
  referenceDate: string
  legalNature: string
  listSource: string
  actorId: string
}

/**
 * Porta consumida pela tela do ciclo. Espelha o serviço do núcleo e a
 * loja de resumos; a implementação HTTP é a P-II3-INTEGRACAO.
 */
export interface SocialCyclePort {
  declaredFormat(): Promise<DeclaredSocialListFormat>
  parameters(): Promise<SocialTariffCycleParameters>
  runCycle(input: RunCycleInput): Promise<CycleReport>
  familySummaries(): Promise<FamilyCrossingSummary[]>
}

/** Agrupa a busca activa por (município, rota) — GroupKey do núcleo. */
export function groupActiveSearch(
  entries: ActiveSearchEntry[],
): { key: ActiveSearchGroupKey; entries: ActiveSearchEntry[] }[] {
  const groups = new Map<string, { key: ActiveSearchGroupKey; entries: ActiveSearchEntry[] }>()
  for (const entry of entries) {
    const id = `${entry.municipalityCode ?? ''}|${entry.routeCode ?? ''}`
    const group = groups.get(id) ?? {
      key: { municipalityCode: entry.municipalityCode, routeCode: entry.routeCode },
      entries: [],
    }
    group.entries.push(entry)
    groups.set(id, group)
  }
  return [...groups.values()]
}
