import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { Field, Input, Select } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { useDensity } from '@hooks/useDensity'
import {
  savedViewsApi,
  type SavedView,
  type SavedViewInput,
  type SavedViewResource,
} from '@services/search'
import { errorMessage } from '@services/registry/errors'
import { saveListState } from '@utils/listState'

/**
 * P1.33: visões salvas de filtros e colunas por usuário (servidor, tabela
 * `saved_views` da V1_17). A lista mostra só as visões do próprio usuário — a
 * regra vale no servidor; uma visão marcada ORG_UNIT continua só do dono até a
 * P3.30 existir (o selo abaixo diz isso, para ninguém achar que já compartilhou).
 *
 * Aplicar: a visão de clientes grava o filtro no estado da lista (o mesmo
 * `listState` da P1.12 que a lista já restaura ao montar) e navega para a
 * lista; a densidade da visão passa a valer na lista. Para os demais recursos,
 * aplicar navega para a lista correspondente com o estado gravado quando a
 * lista usa `listState` — sem ele, a visão fica salva e a aplicação é o
 * próximo passo da lista (a API já guarda filtros, colunas e ordenação).
 */
const RESOURCE_LABEL: Record<SavedViewResource, string> = {
  customers: 'Clientes',
  'consumer-units': 'Imóveis',
  'service-orders': 'Ordens de serviço',
  bills: 'Contas',
  documents: 'Documentos',
  global: 'Busca global',
}

const RESOURCE_ROUTE: Partial<Record<SavedViewResource, string>> = {
  customers: '/cadastro/clientes',
  'consumer-units': '/cadastro/imoveis',
}

const LIST_STATE_KEY: Partial<Record<SavedViewResource, string>> = {
  customers: 'customers',
  'consumer-units': 'consumer-units',
}

/**
 * Filtros vazios de cada lista, para aplicar uma visão sem quebrar a lista:
 * o estado salvo substitui o filtro aplicado por inteiro, então a visão que
 * só guarda `q` precisa dos demais campos com o padrão da lista (sem isso,
 * `toParams`/`toListParams` chamariam `.trim()` em `undefined`).
 */
const EMPTY_FILTERS: Partial<Record<SavedViewResource, Record<string, unknown>>> = {
  customers: { q: '', nameMatch: 'contains', personType: '', customerTypeId: null, city: '', includeDeleted: false },
  'consumer-units': {
    q: '',
    inscription: '',
    localityId: null,
    sectorId: null,
    blockId: null,
    waterStatusId: null,
    sewerStatusId: null,
    categoryId: null,
    profileId: null,
    routeId: null,
    cep: '',
    numberFrom: '',
    numberTo: '',
    includeDeleted: false,
  },
}

function parseFilters(raw: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(raw) as unknown
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {}
  } catch {
    return {}
  }
}

export function SavedViews() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { density, setDensity } = useDensity()
  const views = useQuery({ queryKey: ['saved-views'], queryFn: () => savedViewsApi.list() })
  const [title, setTitle] = useState('')
  const [resource, setResource] = useState<SavedViewResource>('customers')
  const [filters, setFilters] = useState('{}')
  const [columns, setColumns] = useState('[]')
  const [visibility, setVisibility] = useState<'PRIVATE' | 'ORG_UNIT'>('PRIVATE')
  const [sharedOrgUnitId, setSharedOrgUnitId] = useState('')
  const [isDefault, setIsDefault] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['saved-views'] })

  const onCreate = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(null)
    setNotice(null)
    const input: SavedViewInput = {
      title: title.trim(),
      resource,
      filters: filters.trim() || '{}',
      columns: columns.trim() || '[]',
      density,
      visibility,
      sharedOrgUnitId: visibility === 'ORG_UNIT' && sharedOrgUnitId.trim() ? sharedOrgUnitId.trim() : null,
      isDefault,
    }
    setBusy(true)
    try {
      await savedViewsApi.create(input)
      setNotice(`Visão “${input.title}” salva.`)
      setTitle('')
      setFilters('{}')
      setColumns('[]')
      refresh()
    } catch (e: unknown) {
      setFormError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const onDelete = async (view: SavedView) => {
    setNotice(null)
    try {
      await savedViewsApi.remove(view.id)
      setNotice(`Visão “${view.title}” excluída.`)
      refresh()
    } catch (e: unknown) {
      setNotice(errorMessage(e))
    }
  }

  const onApply = (view: SavedView) => {
    // A densidade da visão passa a valer nas listas (preferência do dispositivo).
    setDensity(view.density)
    const route = RESOURCE_ROUTE[view.resource]
    const stateKey = LIST_STATE_KEY[view.resource]
    if (route && stateKey) {
      // A lista restaura este estado ao montar (P1.12): aplicar = gravar + navegar.
      // Mescla com o filtro vazio da lista, para a visão parcial não quebrar a lista.
      saveListState(stateKey, {
        applied: { ...(EMPTY_FILTERS[view.resource] ?? {}), ...parseFilters(view.filters) },
        page: 0,
      })
      navigate(route)
      return
    }
    setNotice(
      `Visão “${view.title}” salva, mas a lista de ${RESOURCE_LABEL[view.resource]} ainda não aplica visões — a API já guarda filtros, colunas e ordenação.`,
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Visões salvas"
        description="Filtros e colunas salvos por usuário. Visões compartilhadas por unidade organizacional aguardam o escopo da P3.30."
      />
      {notice ? <Alert tone="success">{notice}</Alert> : null}
      {views.isError ? <Alert tone="error">{errorMessage(views.error)}</Alert> : null}

      <Card>
        <CardContent>
          <form onSubmit={(event) => void onCreate(event)} className="space-y-4">
            <h2 className="text-base font-medium">Nova visão</h2>
            {formError ? <Alert tone="error">{formError}</Alert> : null}
            <Field label="Título" required>
              {(aria) => <Input {...aria} value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={200} />}
            </Field>
            <Field label="Recurso">
              {(aria) => (
                <Select {...aria} value={resource} onChange={(event) => setResource(event.target.value as SavedViewResource)}>
                  {(Object.keys(RESOURCE_LABEL) as SavedViewResource[]).map((key) => (
                    <option key={key} value={key}>
                      {RESOURCE_LABEL[key]}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="Filtros (objeto JSON)">
              {(aria) => <Input {...aria} value={filters} onChange={(event) => setFilters(event.target.value)} />}
            </Field>
            <Field label="Colunas (array JSON)">
              {(aria) => <Input {...aria} value={columns} onChange={(event) => setColumns(event.target.value)} />}
            </Field>
            <Field label="Visibilidade">
              {(aria) => (
                <Select
                  {...aria}
                  value={visibility}
                  onChange={(event) => setVisibility(event.target.value as 'PRIVATE' | 'ORG_UNIT')}
                >
                  <option value="PRIVATE">Só eu</option>
                  <option value="ORG_UNIT">Unidade organizacional (até a P3.30, só eu a vejo)</option>
                </Select>
              )}
            </Field>
            {visibility === 'ORG_UNIT' ? (
              <Field label="Unidade organizacional (UUID)">
                {(aria) => (
                  <Input
                    {...aria}
                    value={sharedOrgUnitId}
                    onChange={(event) => setSharedOrgUnitId(event.target.value)}
                    placeholder="UUID da unidade (P3.30)"
                  />
                )}
              </Field>
            ) : null}
            <label className="inline-flex items-center gap-2 text-sm">
              <input type="checkbox" checked={isDefault} onChange={(event) => setIsDefault(event.target.checked)} />
              Visão padrão deste recurso
            </label>
            <div>
              <Button type="submit" disabled={busy}>
                {busy ? 'Salvando…' : 'Salvar visão'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <div className="mb-3 flex items-center gap-2">
            <h2 className="text-base font-medium">Minhas visões</h2>
            <label className="ml-auto inline-flex items-center gap-1 text-sm">
              Densidade
              <select
                aria-label="Densidade da tabela"
                value={density}
                onChange={(event) => setDensity(event.target.value as 'comfortable' | 'compact')}
                className="rounded-md border border-input bg-card px-1 py-1 text-sm"
              >
                <option value="comfortable">Confortável</option>
                <option value="compact">Compacta</option>
              </select>
            </label>
          </div>
          {views.isPending ? (
            <Spinner label="Carregando visões…" />
          ) : (
            <Table caption="Visões salvas do usuário" density={density} mobileCards>
              <TableHeader>
                <TableRow>
                  <TableHead>Título</TableHead>
                  <TableHead>Recurso</TableHead>
                  <TableHead>Densidade</TableHead>
                  <TableHead>Visibilidade</TableHead>
                  <TableHead>Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(views.data ?? []).map((view) => (
                  <TableRow key={view.id}>
                    <TableCell label="Título">{view.title}</TableCell>
                    <TableCell label="Recurso">{RESOURCE_LABEL[view.resource]}</TableCell>
                    <TableCell label="Densidade">{view.density === 'compact' ? 'Compacta' : 'Confortável'}</TableCell>
                    <TableCell label="Visibilidade">
                      {view.visibility === 'ORG_UNIT' ? 'Unidade organizacional (só eu, até a P3.30)' : 'Só eu'}
                      {view.isDefault ? ' · padrão' : ''}
                    </TableCell>
                    <TableCell label="Ações">
                      <span className="inline-flex gap-2">
                        <Button variant="outline" size="sm" onClick={() => onApply(view)}>
                          Aplicar
                        </Button>
                        <Button variant="destructive" size="sm" onClick={() => void onDelete(view)}>
                          Excluir
                        </Button>
                      </span>
                    </TableCell>
                  </TableRow>
                ))}
                {(views.data ?? []).length === 0 ? (
                  <TableRow>
                    <TableCell label="Visões">Nenhuma visão salva ainda.</TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          )}
          <p className="mt-3 text-sm text-muted-foreground">
            <Link to="/cadastro/clientes" className="underline">
              Voltar para clientes
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
