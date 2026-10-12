import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Plus, Search } from 'lucide-react'
import { CatalogSelect } from '@components/registry/CatalogSelect'
import type { LookupValue } from '@components/registry/LookupField'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { Field, Input, Select } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { Pagination } from '@components/ui/Pagination'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { errorMessage } from '@services/registry/errors'
import { customers } from '@services/registry/resources'
import type { ListParams, PersonType } from '@services/registry/types'
import { orDash, PERSON_TYPE_LABEL } from '@utils/labels'

interface Filters {
  q: string
  nameMatch: 'contains' | 'startsWith'
  personType: '' | PersonType
  customerTypeId: LookupValue | null
  city: string
  includeDeleted: boolean
}

const EMPTY: Filters = { q: '', nameMatch: 'contains', personType: '', customerTypeId: null, city: '', includeDeleted: false }

function toParams(f: Filters, page: number): ListParams {
  return {
    q: f.q.trim(),
    nameMatch: f.q.trim() ? f.nameMatch : undefined,
    personType: f.personType || undefined,
    customerTypeId: f.customerTypeId?.id,
    city: f.city.trim() || undefined,
    includeDeleted: f.includeDeleted || undefined,
    page,
    size: 25,
    sort: 'name,asc',
  }
}

/** S4: lista de clientes. Documento SEMPRE mascarado; revelar só na ficha, por ação explícita. */
export function CustomerList() {
  const [draft, setDraft] = useState<Filters>(EMPTY)
  const [applied, setApplied] = useState<Filters>(EMPTY)
  const [page, setPage] = useState(0)
  const params = toParams(applied, page)
  const list = useQuery({ queryKey: ['customers', params], queryFn: ({ signal }) => customers.search(params, signal) })

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    setApplied(draft)
    setPage(0)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Clientes"
        description="Pessoas físicas e jurídicas. CPF e CNPJ aparecem mascarados nas listas."
        actions={
          <Link to="/cadastro/clientes/novo" className="inline-flex h-10 items-center gap-2 rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90">
            <Plus className="h-4 w-4" aria-hidden="true" />
            Cadastrar cliente
          </Link>
        }
      />

      <form onSubmit={onSubmit} role="search" aria-label="Pesquisar clientes" className="space-y-4 rounded-xl border bg-card p-4">
        <div className="grid gap-4 md:grid-cols-3">
          <Field label="Nome ou documento">
            {(aria) => <Input {...aria} type="search" value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} autoComplete="off" />}
          </Field>
          <Field label="Como procurar o nome">
            {(aria) => (
              <Select {...aria} value={draft.nameMatch} onChange={(e) => setDraft({ ...draft, nameMatch: e.target.value === 'startsWith' ? 'startsWith' : 'contains' })}>
                <option value="contains">Contendo o texto</option>
                <option value="startsWith">Iniciando pelo texto</option>
              </Select>
            )}
          </Field>
          <Field label="Tipo de pessoa">
            {(aria) => (
              <Select {...aria} value={draft.personType} onChange={(e) => setDraft({ ...draft, personType: e.target.value === 'PF' ? 'PF' : e.target.value === 'PJ' ? 'PJ' : '' })}>
                <option value="">Todas</option>
                <option value="PF">{PERSON_TYPE_LABEL.PF}</option>
                <option value="PJ">{PERSON_TYPE_LABEL.PJ}</option>
              </Select>
            )}
          </Field>
          <CatalogSelect label="Tipo de cliente" resource="customer-types" value={draft.customerTypeId} onChange={(v) => setDraft({ ...draft, customerTypeId: v })} />
          <Field label="Município">
            {(aria) => <Input {...aria} value={draft.city} onChange={(e) => setDraft({ ...draft, city: e.target.value })} autoComplete="off" />}
          </Field>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="submit">
            <Search className="h-4 w-4" aria-hidden="true" />
            Pesquisar
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              setDraft(EMPTY)
              setApplied(EMPTY)
              setPage(0)
            }}
          >
            Limpar filtros
          </Button>
        </div>
      </form>

      <Card>
        <CardContent className="pt-6">
          <div aria-live="polite">
            {list.isLoading ? (
              <Spinner label="Carregando clientes..." />
            ) : list.isError ? (
              <Alert tone="error" title="Não foi possível carregar os clientes">
                <p>{errorMessage(list.error)}</p>
                <Button size="sm" variant="outline" className="mt-2" onClick={() => void list.refetch()}>
                  Tentar novamente
                </Button>
              </Alert>
            ) : list.data && list.data.content.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">Nenhum cliente encontrado.</p>
            ) : null}
          </div>
          {list.data && list.data.content.length > 0 ? (
            <>
              <Table caption="Clientes encontrados">
                <TableHeader>
                  <TableRow>
                    <TableHead>Nome</TableHead>
                    <TableHead>Pessoa</TableHead>
                    <TableHead>Tipo de cliente</TableHead>
                    <TableHead>Documento</TableHead>
                    <TableHead>Município</TableHead>
                    <TableHead className="text-right">Imóveis</TableHead>
                    <TableHead className="text-right">Ações</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {list.data.content.map((c) => (
                    <TableRow key={c.id}>
                      <TableCell>
                        <Link to={`/cadastro/clientes/${c.id}`} className="font-medium text-primary underline underline-offset-4">
                          {c.name}
                        </Link>
                        {c.deleted ? (
                          <Badge variant="destructive" className="ml-2">
                            Excluído
                          </Badge>
                        ) : null}
                      </TableCell>
                      <TableCell>{PERSON_TYPE_LABEL[c.personType]}</TableCell>
                      <TableCell>{orDash(c.customerTypeName)}</TableCell>
                      <TableCell className="font-mono text-xs">
                        <span className="sr-only">Documento mascarado: </span>
                        {orDash(c.documentMasked)}
                      </TableCell>
                      <TableCell>{orDash(c.city)}</TableCell>
                      <TableCell className="text-right">{c.unitCount}</TableCell>
                      <TableCell className="text-right">
                        {!c.deleted ? (
                          <Link to={`/cadastro/clientes/${c.id}/editar`} aria-label={`Editar cliente ${c.name}`} className="inline-flex h-9 items-center rounded-md px-3 text-sm font-medium hover:bg-accent">
                            Editar
                          </Link>
                        ) : null}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPageChange={setPage} />
            </>
          ) : null}
        </CardContent>
      </Card>
    </div>
  )
}
