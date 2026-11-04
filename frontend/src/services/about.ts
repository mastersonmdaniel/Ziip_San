import { api } from './api'

/**
 * P1.24: cliente do Sobre completo (ADR-0021; AJUDA §6).
 *
 * Rotas (todas autenticadas, sem permissão de catálogo — só dado global do
 * produto ou dado DO PRÓPRIO usuário, como /me; ver a allowlist do
 * RoutePermissionTest no backend):
 * - GET /api/v1/about — marca, versão, documentos com aceite do usuário;
 * - GET /api/v1/about/licenses — licenças de terceiros do servidor;
 * - GET /api/v1/release-notes — novidades com o visto do usuário;
 * - POST /api/v1/release-notes/{version}/view — marca vista (idempotente);
 * - POST /api/v1/about/documents/{code}/accept — aceita a versão vigente.
 *
 * Nenhum dado pessoal vai para localStorage/URL: o aceite e o visto são
 * estado do servidor, identificados pelo token da sessão.
 */

export interface AboutBrand {
  productName: string
  shortName: string
  description: string
  aboutText: string
}

export interface AboutDocument {
  code: string
  title: string
  currentVersion: string
  acceptedVersion: string | null
  acceptedAt: string | null
  upToDate: boolean
}

export interface AboutInfo {
  version: string
  commit: string
  environment: string
  deployedAt: string | null
  brand: AboutBrand
  documents: AboutDocument[]
}

export interface DocumentAcceptance {
  documentCode: string
  documentVersion: string
  contentHash: string
  acceptedAt: string
}

export interface ThirdPartyLicense {
  name: string
  version: string
  licenses: string[]
}

export interface ThirdPartyLicenses {
  available: boolean
  licenses: ThirdPartyLicense[]
}

export interface ReleaseNoteEntry {
  type: string
  title: string
  commits: string[]
  issue: string | null
}

export interface ReleaseNote {
  version: string
  date: string
  entries: ReleaseNoteEntry[]
  viewed: boolean
}

export interface ReleaseNoteViewed {
  version: string
  viewed: boolean
  viewedAt: string
}

export async function getAbout(): Promise<AboutInfo> {
  const { data } = await api.get<AboutInfo>('/about')
  return data
}

export async function getLicenses(): Promise<ThirdPartyLicenses> {
  const { data } = await api.get<ThirdPartyLicenses>('/about/licenses')
  return data
}

export async function getReleaseNotes(): Promise<ReleaseNote[]> {
  const { data } = await api.get<ReleaseNote[]>('/release-notes')
  return data
}

export async function markReleaseNoteViewed(version: string): Promise<ReleaseNoteViewed> {
  const { data } = await api.post<ReleaseNoteViewed>(`/release-notes/${version}/view`)
  return data
}

export async function acceptDocument(code: string): Promise<DocumentAcceptance> {
  const { data } = await api.post<DocumentAcceptance>(`/about/documents/${code}/accept`)
  return data
}
