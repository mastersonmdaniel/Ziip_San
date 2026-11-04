import { api } from '@services/api'

/**
 * Modelos de documento e mensagem (P2.30): o motor único por tenant.
 * O servidor impõe o fluxo — rascunho, prévia com massa sintética,
 * proposta e aprovação por outra pessoa — e guarda o documento gerado
 * como instantâneo do modelo e da versão usados. A tela só orquestra
 * essas chamadas; nenhuma regra vive aqui.
 */

export type TemplateDocumentType =
  | 'PROTOCOL_RECEIPT' | 'USER_RESPONSE' | 'CUT_NOTICE' | 'COLLECTION_LETTER'
  | 'INSTALLMENT_TERM' | 'INFRACTION_NOTICE' | 'CERTIFICATE' | 'CDA'
  | 'SERVICE_ORDER_PRINT' | 'LAB_REPORT' | 'AUDIT_REPORT' | 'EMAIL' | 'SMS' | 'WHATSAPP'

export type TemplateChannel = 'PDF' | 'EMAIL' | 'SMS' | 'WHATSAPP' | 'THERMAL'

export interface TemplateBlockInput { id: string; kind: 'FREE' | 'LOCKED'; origin: 'TENANT' | 'SYSTEM'; text: string }
export interface TemplateVariableInput { name: string; type: 'TEXT' | 'NUMBER' | 'MONEY' | 'DATE' | 'BOOLEAN' }

export interface Template {
  id: string; code: string; title: string; documentType: string; channel: string
  factoryKey: string | null; status: string
}

export interface TemplateVersion {
  id: string; templateId: string; versionNumber: number; blocks: TemplateBlockInput[]
  variables: TemplateVariableInput[]; validFrom: string; validTo: string | null
  status: string; proposedBy: string | null; approvedBy: string | null
  previewRendered: string | null; rejectionReason: string | null
}

export interface GeneratedDocument {
  id: string; templateId: string; templateVersionId: string; renderedBody: string
  variablesUsed: string[]; generatedAt: string
}

export interface FactoryTemplate {
  key: string; documentType: string; channel: string; title: string; body: string
  variables: TemplateVariableInput[]
}

const BASE = '/templates'

export async function listFactory(): Promise<FactoryTemplate[]> {
  const { data } = await api.get<FactoryTemplate[]>(`${BASE}/factory`)
  return data
}

export async function copyFactory(key: string, input: { code: string; title: string; validFrom: string; validTo?: string | null }): Promise<Template> {
  const { data } = await api.post<Template>(`${BASE}/factory/${encodeURIComponent(key)}/copy`, input)
  return data
}

export async function listTemplates(): Promise<Template[]> {
  const { data } = await api.get<Template[]>(BASE)
  return data
}

export async function createTemplate(input: { code: string; title: string; documentType: string; channel: string }): Promise<Template> {
  const { data } = await api.post<Template>(BASE, input)
  return data
}

export async function listVersions(templateId: string): Promise<TemplateVersion[]> {
  const { data } = await api.get<TemplateVersion[]>(`${BASE}/${templateId}/versions`)
  return data
}

export async function createVersion(templateId: string, input: {
  blocks: TemplateBlockInput[]; variables: TemplateVariableInput[]; validFrom: string; validTo?: string | null
}): Promise<TemplateVersion> {
  const { data } = await api.post<TemplateVersion>(`${BASE}/${templateId}/versions`, input)
  return data
}

export async function previewVersion(templateId: string, versionId: string): Promise<string> {
  const { data } = await api.post<{ rendered: string }>(`${BASE}/${templateId}/versions/${versionId}/preview`, {})
  return data.rendered
}

export async function proposeVersion(templateId: string, versionId: string): Promise<TemplateVersion> {
  const { data } = await api.post<TemplateVersion>(`${BASE}/${templateId}/versions/${versionId}/propose`, {})
  return data
}

export async function approveVersion(templateId: string, versionId: string): Promise<TemplateVersion> {
  const { data } = await api.post<TemplateVersion>(`${BASE}/${templateId}/versions/${versionId}/approve`, {})
  return data
}

export async function rejectVersion(templateId: string, versionId: string, reason: string): Promise<TemplateVersion> {
  const { data } = await api.post<TemplateVersion>(`${BASE}/${templateId}/versions/${versionId}/reject`, { reason })
  return data
}

export async function generateDocument(templateId: string, input: {
  versionId?: string | null; date?: string | null; values: Record<string, string>
}): Promise<GeneratedDocument> {
  const { data } = await api.post<GeneratedDocument>(`${BASE}/${templateId}/generate`, input)
  return data
}

export async function listGenerated(templateId: string): Promise<GeneratedDocument[]> {
  const { data } = await api.get<GeneratedDocument[]>(`${BASE}/${templateId}/generated`)
  return data
}
