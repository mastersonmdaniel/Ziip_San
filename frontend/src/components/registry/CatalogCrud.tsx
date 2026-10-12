import { useCallback, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Search, Trash2 } from 'lucide-react'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { Dialog } from '@components/ui/Dialog'
import { Checkbox, Field, Input, Select, Textarea } from '@components/ui/Field'
import { Pagination } from '@components/ui/Pagination'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { useDebounced } from '@hooks/useDebounced'
import { ApiError, errorMessage, type FieldError } from '@services/registry/errors'
import type { CrudItem } from '@services/registry/types'
import { ConfirmWarning, useConfirmable } from './ConfirmWarning'
import { ConflictAlert } from './ConflictAlert'
import { LookupField, type LookupValue } from './LookupField'
import {
  buildSchema,
  collectErrors,
  decimalToText,
  toFormValues,
  toPayload,
  type CrudMeta,
  type FieldMeta,
  type FormValues,
} from './crudMeta'

function itemTitle(item: CrudItem): string {
  const name = typeof item.name === 'string' ? item.name : ''
  const code = typeof item.code === 'string' || typeof item.code === 'number' ? String(item.code) : ''
  return [code, name].filter(Boolean).join(' · ') || item.id
}

function displayCell(field: FieldMeta, item: CrudItem): string {
  const raw = item[field.name]
  switch (field.kind) {
    case 'boolean':
      return raw === true ? 'Sim' : 'Não'
    case 'decimal':
      return typeof raw === 'number' ? decimalToText(raw) : '—'
    case 'select': {
      const opt = field.options.find((o) => o.value === raw)
      return opt ? opt.label : typeof raw === 'string' && raw ? raw : '—'
    }
    case 'lookup': {
      const ref = item[field.refName]
      if (typeof ref === 'object' && ref !== null && 'name' in ref) {
        const r = ref as { name?: string; code?: string | number | null }
        return [r.code, r.name].filter((x) => x !== undefined && x !== null && x !== '').join(' · ') || '—'
      }
      return '—'
    }
    default:
      return raw === null || raw === undefined || raw === '' ? '—' : String(raw)
  }
}

// ---------------------------------------------------------------------------------------------
// Formulário (em diálogo)
// ---------------------------------------------------------------------------------------------

interface CrudFormProps {
  meta: CrudMeta
  item: CrudItem | null
  onClose: () => void
  onSaved: () => void
}

interface SubmitArgs {
  payload: Record<string, unknown>
}

function CrudForm({ meta, item, onClose, onSaved }: CrudFormProps) {
  const schema = useMemo(() => buildSchema(meta.fields), [meta.fields])
  const [values, setValues] = useState<FormValues>(() => toFormValues(meta.fields, item))
  const [version, setVersion] = useState<number | null>(item ? item.version : null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const [reloading, setReloading] = useState(false)

  const submit = useCallback(
    async ({ payload }: SubmitArgs, confirmTokens: string[]) => {
      if (item && version !== null) await meta.client.update(item.id, payload, version, { confirmTokens })
      else await meta.client.create(payload, { confirmTokens })
      onSaved()
    },
    [item, version, meta.client, onSaved],
  )

  const onError = useCallback((error: ApiError) => {
    if (error.kind === 'conflict') {
      setConflict(true)
      setFormError(null)
      return
    }
    const mapped: Record<string, string> = {}
    const loose: FieldError[] = []
    for (const e of error.errors) {
      const key = e.field.replace(/\[.*$/, '').split('.')[0] ?? ''
      if (key) mapped[key] = e.message
      else loose.push(e)
    }
    setErrors((prev) => ({ ...prev, ...mapped }))
    setFormError(loose.length > 0 ? loose.map((e) => e.message).join(' ') : Object.keys(mapped).length > 0 ? 'Corrija os campos indicados.' : error.message)
  }, [])

  const { run, confirm, cancel, warnings, busy } = useConfirmable<SubmitArgs>(submit, onError)

  const setField = (name: string, value: FormValues[string]) => {
    setValues((prev) => {
      const next = { ...prev, [name]: value }
      // Cascata: ao mudar o pai, os filhos que dependem dele são limpos.
      for (const f of meta.fields) {
        if (f.kind === 'lookup' && f.dependsOn?.field === name) next[f.name] = null
      }
      return next
    })
    setErrors((prev) => {
      if (!(name in prev)) return prev
      const rest = { ...prev }
      delete rest[name]
      return rest
    })
  }

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    setFormError(null)
    const result = schema.safeParse(values)
    const found = collectErrors(result)
    setErrors(found)
    if (Object.keys(found).length > 0) {
      setFormError('Corrija os campos indicados.')
      return
    }
    void run({ payload: toPayload(meta.fields, values) })
  }

  const reloadServer = async (keepMine: boolean) => {
    if (!item) return
    setReloading(true)
    try {
      const fresh = await meta.client.get(item.id)
      setVersion(fresh.version)
      if (!keepMine) setValues(toFormValues(meta.fields, fresh))
      setConflict(false)
      setFormError(null)
    } catch (e) {
      setFormError(errorMessage(e))
    } finally {
      setReloading(false)
    }
  }

  const renderField = (f: FieldMeta, index: number) => {
    const error = errors[f.name] ?? null
    const value = values[f.name]
    const autoFocus = index === 0 ? { 'data-autofocus': true } : {}
    switch (f.kind) {
      case 'boolean':
        return (
          <Checkbox
            key={f.name}
            label={f.label}
            hint={f.hint}
            checked={value === true}
            onChange={(e) => setField(f.name, e.target.checked)}
            {...autoFocus}
          />
        )
      case 'select':
        return (
          <Field key={f.name} label={f.label} required={f.required} hint={f.hint} error={error}>
            {(aria) => (
              <Select {...aria} value={typeof value === 'string' ? value : ''} onChange={(e) => setField(f.name, e.target.value)} {...autoFocus}>
                <option value="">Selecione...</option>
                {f.options.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </Select>
            )}
          </Field>
        )
      case 'lookup': {
        const parent = f.dependsOn ? values[f.dependsOn.field] : undefined
        const parentId = parent && typeof parent === 'object' ? parent.id : undefined
        const blocked = f.dependsOn !== undefined && parentId === undefined
        return (
          <LookupField
            key={f.name}
            label={f.label}
            required={f.required}
            hint={blocked ? 'Escolha primeiro o campo anterior.' : f.hint}
            error={error}
            source={f.source}
            filters={f.dependsOn && parentId ? { [f.dependsOn.param]: parentId } : undefined}
            disabled={blocked}
            value={typeof value === 'object' && value !== null ? (value as LookupValue) : null}
            onChange={(v) => setField(f.name, v)}
          />
        )
      }
      case 'text':
        return (
          <Field key={f.name} label={f.label} required={f.required} hint={f.hint} error={error}>
            {(aria) =>
              f.multiline ? (
                <Textarea {...aria} maxLength={f.maxLength} value={typeof value === 'string' ? value : ''} onChange={(e) => setField(f.name, e.target.value)} />
              ) : (
                <Input {...aria} maxLength={f.maxLength} value={typeof value === 'string' ? value : ''} onChange={(e) => setField(f.name, e.target.value)} {...autoFocus} />
              )
            }
          </Field>
        )
      case 'integer':
      case 'decimal':
        return (
          <Field key={f.name} label={f.label} required={f.required} hint={f.hint} error={error}>
            {(aria) => (
              <Input
                {...aria}
                inputMode={f.kind === 'integer' ? 'numeric' : 'decimal'}
                value={typeof value === 'string' ? value : ''}
                onChange={(e) => setField(f.name, e.target.value)}
                {...autoFocus}
              />
            )}
          </Field>
        )
    }
  }

  const title = item ? `Editar ${meta.singular}` : `Adicionar ${meta.singular}`

  return (
    <Dialog open onClose={onClose} title={title} description="Campos marcados com * são obrigatórios.">
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {conflict ? (
          <ConflictAlert onKeepMine={() => void reloadServer(true)} onDiscardMine={() => void reloadServer(false)} busy={reloading} />
        ) : null}
        {formError ? <Alert tone="error">{formError}</Alert> : null}
        {warnings ? <ConfirmWarning warnings={warnings} onConfirm={() => void confirm()} onCancel={cancel} busy={busy} /> : null}
        {meta.fields.map(renderField)}
        <div className="flex flex-wrap justify-end gap-2 pt-2">
          <Button variant="outline" onClick={onClose} disabled={busy}>
            Cancelar
          </Button>
          <Button type="submit" loading={busy} disabled={warnings !== null || conflict}>
            Salvar
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

// ---------------------------------------------------------------------------------------------
// Exclusão lógica (com motivo)
// ---------------------------------------------------------------------------------------------

function DeleteDialog({ meta, item, onClose, onDeleted }: { meta: CrudMeta; item: CrudItem; onClose: () => void; onDeleted: () => void }) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const mutation = useMutation({
    mutationFn: () => meta.client.remove(item.id, reason.trim()),
    onSuccess: onDeleted,
    onError: (e) => setError(errorMessage(e)),
  })
  const tooShort = reason.trim().length < 3

  return (
    <Dialog
      open
      onClose={onClose}
      title={`Excluir ${meta.singular}`}
      description={`A exclusão é lógica: ${itemTitle(item)} deixa de aparecer, mas o histórico é preservado. Informe o motivo.`}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={mutation.isPending}>
            Cancelar
          </Button>
          <Button variant="destructive" loading={mutation.isPending} disabled={tooShort} onClick={() => mutation.mutate()}>
            Excluir
          </Button>
        </>
      }
    >
      {error ? <Alert tone="error">{error}</Alert> : null}
      <Field label="Motivo da exclusão" required hint="Mínimo de 3 caracteres.">
        {(aria) => <Textarea {...aria} data-autofocus value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} />}
      </Field>
    </Dialog>
  )
}

// ---------------------------------------------------------------------------------------------
// Lista
// ---------------------------------------------------------------------------------------------

/**
 * CRUD genérico dirigido por metadados (CrudMeta): lista paginada com busca, formulário em
 * diálogo acessível e exclusão lógica com motivo. Serve catálogos (S6) e hierarquia (S5).
 * Sem dados embutidos: a lista vem da API e mostra carregando / erro / vazio.
 */
export function CatalogCrud({ meta }: { meta: CrudMeta }) {
  const queryClient = useQueryClient()
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<CrudItem | 'new' | null>(null)
  const [deleting, setDeleting] = useState<CrudItem | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const debounced = useDebounced(q, 300)

  const columns = meta.fields.filter((f) => f.column)
  const listKey = ['crud', meta.key, debounced, page]
  const list = useQuery({
    queryKey: listKey,
    queryFn: ({ signal }) => meta.client.list({ q: debounced, page, size: 25, sort: meta.defaultSort }, signal),
  })

  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['crud', meta.key] })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="w-full max-w-sm">
          <Field label={`Buscar em ${meta.title.toLowerCase()}`}>
            {(aria) => (
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
                <Input
                  {...aria}
                  type="search"
                  className="pl-9"
                  value={q}
                  onChange={(e) => {
                    setQ(e.target.value)
                    setPage(0)
                  }}
                  placeholder="Código ou nome"
                />
              </div>
            )}
          </Field>
        </div>
        <Button onClick={() => setEditing('new')}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          Adicionar {meta.singular}
        </Button>
      </div>

      {notice ? <Alert tone="success">{notice}</Alert> : null}

      <Card>
        <CardContent className="pt-6">
          {list.isLoading ? (
            <Spinner label={`Carregando ${meta.title.toLowerCase()}...`} />
          ) : list.isError ? (
            <Alert tone="error" title="Não foi possível carregar a lista">
              <p>{errorMessage(list.error)}</p>
              <Button size="sm" variant="outline" className="mt-2" onClick={() => void list.refetch()}>
                Tentar novamente
              </Button>
            </Alert>
          ) : list.data && list.data.content.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              {debounced ? 'Nenhum registro encontrado para esta busca.' : `Nenhum registro cadastrado em ${meta.title.toLowerCase()}.`}
            </p>
          ) : (
            <>
              <Table caption={`Lista de ${meta.title.toLowerCase()}`}>
                <TableHeader>
                  <TableRow>
                    {columns.map((c) => (
                      <TableHead key={c.name}>{c.label}</TableHead>
                    ))}
                    <TableHead className="text-right">Ações</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {list.data?.content.map((item) => (
                    <TableRow key={item.id}>
                      {columns.map((c, i) => (
                        <TableCell key={c.name} className={i === 0 ? 'font-medium' : undefined}>
                          {c.kind === 'boolean' ? (
                            <Badge variant={item[c.name] === true ? 'info' : 'secondary'}>{displayCell(c, item)}</Badge>
                          ) : (
                            displayCell(c, item)
                          )}
                        </TableCell>
                      ))}
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button size="sm" variant="ghost" onClick={() => setEditing(item)} aria-label={`Editar ${itemTitle(item)}`}>
                            <Pencil className="h-4 w-4" aria-hidden="true" />
                            <span className="hidden sm:inline">Editar</span>
                          </Button>
                          <Button size="sm" variant="ghost" onClick={() => setDeleting(item)} aria-label={`Excluir ${itemTitle(item)}`}>
                            <Trash2 className="h-4 w-4" aria-hidden="true" />
                            <span className="hidden sm:inline">Excluir</span>
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              {list.data ? (
                <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPageChange={setPage} />
              ) : null}
            </>
          )}
        </CardContent>
      </Card>

      {editing ? (
        <CrudForm
          meta={meta}
          item={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setNotice(editing === 'new' ? 'Registro criado.' : 'Registro atualizado.')
            setEditing(null)
            refresh()
          }}
        />
      ) : null}
      {deleting ? (
        <DeleteDialog
          meta={meta}
          item={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={() => {
            setNotice('Registro excluído.')
            setDeleting(null)
            refresh()
          }}
        />
      ) : null}
    </div>
  )
}
