import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Card, CardContent, CardHeader, CardTitle } from '@components/ui/Card'
import { Field, Input, Select, Textarea } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { useCan } from '@hooks/usePermissions'
import { errorMessage } from '@services/registry/errors'
import {
  approveVersion,
  copyFactory,
  createTemplate,
  createVersion,
  generateDocument,
  listFactory,
  listGenerated,
  listTemplates,
  listVersions,
  previewVersion,
  proposeVersion,
  rejectVersion,
  type TemplateBlockInput,
  type TemplateVariableInput,
} from '@services/templates'

const DOCUMENT_TYPES = [
  'PROTOCOL_RECEIPT', 'USER_RESPONSE', 'CUT_NOTICE', 'COLLECTION_LETTER', 'INSTALLMENT_TERM',
  'INFRACTION_NOTICE', 'CERTIFICATE', 'CDA', 'SERVICE_ORDER_PRINT', 'LAB_REPORT', 'AUDIT_REPORT',
  'EMAIL', 'SMS', 'WHATSAPP',
] as const

const DOCUMENT_TYPE_LABEL: Record<string, string> = {
  PROTOCOL_RECEIPT: 'Comprovante de protocolo', USER_RESPONSE: 'Resposta ao usuário',
  CUT_NOTICE: 'Aviso de corte', COLLECTION_LETTER: 'Carta de cobrança',
  INSTALLMENT_TERM: 'Termo de parcelamento', INFRACTION_NOTICE: 'Auto de infração',
  CERTIFICATE: 'Certidão', CDA: 'CDA', SERVICE_ORDER_PRINT: 'OS impressa',
  LAB_REPORT: 'Laudo', AUDIT_REPORT: 'Relatório de auditoria', EMAIL: 'E-mail',
  SMS: 'SMS', WHATSAPP: 'WhatsApp',
}

const CHANNELS = ['PDF', 'EMAIL', 'SMS', 'WHATSAPP', 'THERMAL'] as const
const VARIABLE_TYPES = ['TEXT', 'NUMBER', 'MONEY', 'DATE', 'BOOLEAN'] as const
const STATUS_LABEL: Record<string, string> = {
  DRAFT: 'Rascunho', PROPOSED: 'Proposta', APPROVED: 'Aprovada', REJECTED: 'Rejeitada',
  ACTIVE: 'Ativo', ARCHIVED: 'Arquivado',
}

function parseVariables(text: string): TemplateVariableInput[] {
  const out: TemplateVariableInput[] = []
  for (const line of text.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed) continue
    const [name, type] = trimmed.split(':').map((s) => s.trim())
    if (!name || !(VARIABLE_TYPES as readonly string[]).includes(type)) {
      throw new Error(`Variável inválida: "${trimmed}". Use o formato nome:TIPO (TEXT, NUMBER, MONEY, DATE ou BOOLEAN).`)
    }
    out.push({ name, type: type as TemplateVariableInput['type'] })
  }
  return out
}

function parseValues(text: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const line of text.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed) continue
    const eq = trimmed.indexOf('=')
    if (eq <= 0) throw new Error(`Valor inválido: "${trimmed}". Use o formato nome=valor.`)
    out[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim()
  }
  return out
}

/**
 * Tela Configurações > Modelos (P2.30): o motor único de modelos de
 * documento e mensagem. O fluxo é o do servidor, sem atalho: cria-se o
 * modelo, cria-se a versão em rascunho, gera-se a prévia com massa
 * sintética, propõe-se e outra pessoa aprova — autor e proponente nunca
 * aprovam a própria versão. O documento gerado guarda o modelo e a versão
 * usados e não muda quando o modelo muda depois.
 *
 * Blocos travados: um bloco LOCKED de uma versão sobrevive byte a byte
 * nas seguintes (o servidor recusa alterar ou remover). Blocos de origem
 * SYSTEM são o conteúdo legal obrigatório da P2.32 futura e não podem ser
 * criados por esta tela — o servidor os recusa.
 */
export function Templates() {
  const queryClient = useQueryClient()
  const canWrite = useCan('template:write')
  const canApprove = useCan('template:approve')
  const [selected, setSelected] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [previewText, setPreviewText] = useState<string | null>(null)
  const [generatedText, setGeneratedText] = useState<string | null>(null)

  const [code, setCode] = useState('')
  const [title, setTitle] = useState('')
  const [documentType, setDocumentType] = useState<string>('CUT_NOTICE')
  const [channel, setChannel] = useState<string>('PDF')

  const [factoryKey, setFactoryKey] = useState('cut-notice')
  const [copyCode, setCopyCode] = useState('')
  const [copyTitle, setCopyTitle] = useState('')
  const [copyFrom, setCopyFrom] = useState('')

  const [body, setBody] = useState('')
  const [variablesText, setVariablesText] = useState('')
  const [lockedText, setLockedText] = useState('')
  const [validFrom, setValidFrom] = useState('')
  const [validTo, setValidTo] = useState('')

  const [rejectReason, setRejectReason] = useState('')
  const [genDate, setGenDate] = useState('')
  const [genValues, setGenValues] = useState('')

  const templates = useQuery({ queryKey: ['templates'], queryFn: listTemplates })
  const factory = useQuery({ queryKey: ['templates-factory'], queryFn: listFactory })
  const versions = useQuery({
    queryKey: ['template-versions', selected],
    queryFn: () => listVersions(selected as string),
    enabled: selected !== null,
  })
  const generated = useQuery({
    queryKey: ['template-generated', selected],
    queryFn: () => listGenerated(selected as string),
    enabled: selected !== null,
  })

  const invalidate = (keys: string[][]) => {
    for (const key of keys) void queryClient.invalidateQueries({ queryKey: key })
  }

  const create = useMutation({
    mutationFn: () => createTemplate({ code, title, documentType, channel }),
    onSuccess: (t) => {
      setFormError(null); setCode(''); setTitle('')
      invalidate([['templates']]); setSelected(t.id)
    },
    onError: (e) => setFormError(errorMessage(e)),
  })
  const copy = useMutation({
    mutationFn: () => copyFactory(factoryKey, { code: copyCode, title: copyTitle, validFrom: copyFrom || new Date().toISOString().slice(0, 10) }),
    onSuccess: (t) => {
      setFormError(null); setCopyCode(''); setCopyTitle('')
      invalidate([['templates']]); setSelected(t.id)
    },
    onError: (e) => setFormError(errorMessage(e)),
  })
  const createV = useMutation({
    mutationFn: () => {
      const blocks: TemplateBlockInput[] = []
      if (lockedText.trim()) blocks.push({ id: 'travado', kind: 'LOCKED', origin: 'TENANT', text: lockedText })
      blocks.push({ id: 'corpo', kind: 'FREE', origin: 'TENANT', text: body })
      return createVersion(selected as string, {
        blocks, variables: parseVariables(variablesText),
        validFrom, validTo: validTo || null,
      })
    },
    onSuccess: () => {
      setFormError(null); setBody(''); setVariablesText(''); setLockedText('')
      invalidate([['template-versions', selected as string]])
    },
    onError: (e) => setFormError(errorMessage(e)),
  })
  const act = useMutation({
    mutationFn: async ({ kind, versionId }: { kind: string; versionId: string }): Promise<{ kind: string; rendered?: string }> => {
      const tid = selected as string
      if (kind === 'preview') return { kind, rendered: await previewVersion(tid, versionId) }
      if (kind === 'propose') await proposeVersion(tid, versionId)
      else if (kind === 'approve') await approveVersion(tid, versionId)
      else await rejectVersion(tid, versionId, rejectReason)
      return { kind }
    },
    onSuccess: (result) => {
      setFormError(null)
      if (result.kind === 'preview' && result.rendered) setPreviewText(result.rendered)
      else invalidate([['template-versions', selected as string]])
    },
    onError: (e) => setFormError(errorMessage(e)),
  })
  const generate = useMutation({
    mutationFn: () => generateDocument(selected as string, { date: genDate || null, values: parseValues(genValues) }),
    onSuccess: (doc) => {
      setFormError(null); setGeneratedText(doc.renderedBody)
      invalidate([['template-generated', selected as string]])
    },
    onError: (e) => setFormError(errorMessage(e)),
  })

  const selectedTemplate = templates.data?.find((t) => t.id === selected)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Modelos"
        description="Motor único de modelos de documento e mensagem. Cada modelo tem tipo de documento, canal, versão e vigência; variável fora do catálogo do tipo ou de tipo incompatível recusa a versão."
      />
      {formError ? <Alert tone="error">{formError}</Alert> : null}

      <Card>
        <CardHeader><CardTitle>Modelos do tenant</CardTitle></CardHeader>
        <CardContent>
          {templates.isPending ? <Spinner /> : null}
          {templates.data?.length === 0 ? <p>Nenhum modelo ainda. Crie um abaixo ou copie um modelo de fábrica.</p> : null}
          <Table caption="Modelos do tenant">
            <TableHeader><TableRow>
              <TableHead>Código</TableHead><TableHead>Título</TableHead>
              <TableHead>Tipo</TableHead><TableHead>Canal</TableHead><TableHead>Situação</TableHead>
            </TableRow></TableHeader>
            <TableBody>
              {templates.data?.map((t) => (
                <TableRow key={t.id}>
                  <TableCell><Button variant="ghost" size="sm" onClick={() => setSelected(t.id)}>{t.code}</Button></TableCell>
                  <TableCell>{t.title}</TableCell>
                  <TableCell>{DOCUMENT_TYPE_LABEL[t.documentType] ?? t.documentType}</TableCell>
                  <TableCell>{t.channel}</TableCell>
                  <TableCell>{STATUS_LABEL[t.status] ?? t.status}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {canWrite ? (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader><CardTitle>Criar modelo</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <Field label="Código">{(aria) => <Input {...aria} value={code} onChange={(e) => setCode(e.target.value)} />}</Field>
              <Field label="Título">{(aria) => <Input {...aria} value={title} onChange={(e) => setTitle(e.target.value)} />}</Field>
              <Field label="Tipo de documento">{(aria) => (
                <Select {...aria} value={documentType} onChange={(e) => setDocumentType(e.target.value)}>
                  {DOCUMENT_TYPES.map((d) => <option key={d} value={d}>{DOCUMENT_TYPE_LABEL[d]}</option>)}
                </Select>
              )}</Field>
              <Field label="Canal">{(aria) => (
                <Select {...aria} value={channel} onChange={(e) => setChannel(e.target.value)}>
                  {CHANNELS.map((c) => <option key={c} value={c}>{c}</option>)}
                </Select>
              )}</Field>
              <Button onClick={() => create.mutate()} disabled={!code.trim() || !title.trim() || create.isPending}>Criar modelo</Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>Copiar modelo de fábrica</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-muted-foreground">A fábrica nunca é editada: a cópia vira um modelo do tenant, em rascunho, que pode ser ajustado em versões novas.</p>
              <Field label="Modelo de fábrica">{(aria) => (
                <Select {...aria} value={factoryKey} onChange={(e) => setFactoryKey(e.target.value)}>
                  {factory.data?.map((f) => <option key={f.key} value={f.key}>{f.title} ({f.channel})</option>)}
                </Select>
              )}</Field>
              <Field label="Código da cópia">{(aria) => <Input {...aria} value={copyCode} onChange={(e) => setCopyCode(e.target.value)} />}</Field>
              <Field label="Título da cópia">{(aria) => <Input {...aria} value={copyTitle} onChange={(e) => setCopyTitle(e.target.value)} />}</Field>
              <Field label="Início da vigência">{(aria) => <Input {...aria} type="date" value={copyFrom} onChange={(e) => setCopyFrom(e.target.value)} />}</Field>
              <Button onClick={() => copy.mutate()} disabled={!copyCode.trim() || !copyTitle.trim() || copy.isPending}>Copiar fábrica</Button>
            </CardContent>
          </Card>
        </div>
      ) : null}

      {selectedTemplate ? (
        <>
          <Card>
            <CardHeader><CardTitle>Versões de {selectedTemplate.code}</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              {versions.isPending ? <Spinner /> : null}
              <Table caption="Versões do modelo">
                <TableHeader><TableRow>
                  <TableHead>Versão</TableHead><TableHead>Vigência</TableHead><TableHead>Situação</TableHead><TableHead>Ações</TableHead>
                </TableRow></TableHeader>
                <TableBody>
                  {versions.data?.map((v) => (
                    <TableRow key={v.id}>
                      <TableCell>{v.versionNumber}</TableCell>
                      <TableCell>{v.validFrom}{v.validTo ? ` a ${v.validTo}` : ''}</TableCell>
                      <TableCell>{STATUS_LABEL[v.status] ?? v.status}</TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-2">
                          <Button variant="outline" size="sm" onClick={() => act.mutate({ kind: 'preview', versionId: v.id })}>Prévia</Button>
                          {canWrite && v.status === 'DRAFT' ? <Button variant="outline" size="sm" onClick={() => act.mutate({ kind: 'propose', versionId: v.id })}>Propor</Button> : null}
                          {canApprove && v.status === 'PROPOSED' ? <Button variant="outline" size="sm" onClick={() => act.mutate({ kind: 'approve', versionId: v.id })}>Aprovar</Button> : null}
                          {canApprove && v.status === 'PROPOSED' ? <Button variant="outline" size="sm" onClick={() => act.mutate({ kind: 'reject', versionId: v.id })}>Rejeitar</Button> : null}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              {previewText ? <Alert tone="info">{previewText}</Alert> : null}
              {canApprove ? (
                <Field label="Motivo da rejeição">{(aria) => <Input {...aria} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} />}</Field>
              ) : null}
            </CardContent>
          </Card>

          {canWrite ? (
            <Card>
              <CardHeader><CardTitle>Nova versão</CardTitle></CardHeader>
              <CardContent className="space-y-4">
                <Field label="Texto do corpo (use {{variavel}} do catálogo do tipo)">{(aria) => <Textarea {...aria} value={body} onChange={(e) => setBody(e.target.value)} rows={5} />}</Field>
                <Field label="Variáveis declaradas, uma por linha, no formato nome:TIPO">{(aria) => <Textarea {...aria} value={variablesText} onChange={(e) => setVariablesText(e.target.value)} rows={3} />}</Field>
                <Field label="Bloco travado (opcional; não muda nem some nas versões seguintes)">{(aria) => <Textarea {...aria} value={lockedText} onChange={(e) => setLockedText(e.target.value)} rows={2} />}</Field>
                <div className="grid grid-cols-2 gap-4">
                  <Field label="Início da vigência">{(aria) => <Input {...aria} type="date" value={validFrom} onChange={(e) => setValidFrom(e.target.value)} />}</Field>
                  <Field label="Fim da vigência (opcional)">{(aria) => <Input {...aria} type="date" value={validTo} onChange={(e) => setValidTo(e.target.value)} />}</Field>
                </div>
                <p className="text-sm text-muted-foreground">Vigências de versões do mesmo modelo não se sobrepõem; a sobreposição é recusada. A prévia com massa sintética é obrigatória antes da aprovação.</p>
                <Button onClick={() => createV.mutate()} disabled={!body.trim() || !validFrom || createV.isPending}>Criar versão</Button>
              </CardContent>
            </Card>
          ) : null}

          <Card>
            <CardHeader><CardTitle>Gerar documento</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <Field label="Data (usa a versão aprovada vigente; vazio usa hoje)">{(aria) => <Input {...aria} type="date" value={genDate} onChange={(e) => setGenDate(e.target.value)} />}</Field>
              <Field label="Valores das variáveis, um por linha, no formato nome=valor">{(aria) => <Textarea {...aria} value={genValues} onChange={(e) => setGenValues(e.target.value)} rows={3} />}</Field>
              <Button onClick={() => generate.mutate()} disabled={generate.isPending}>Gerar documento</Button>
              {generatedText ? <Alert tone="success">{generatedText}</Alert> : null}
              {generated.data?.length ? (
                <Table caption="Documentos gerados">
                  <TableHeader><TableRow><TableHead>Gerado em</TableHead><TableHead>Texto</TableHead></TableRow></TableHeader>
                  <TableBody>
                    {generated.data.map((g) => (
                      <TableRow key={g.id}><TableCell>{g.generatedAt}</TableCell><TableCell>{g.renderedBody}</TableCell></TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : null}
            </CardContent>
          </Card>
        </>
      ) : null}
    </div>
  )
}
