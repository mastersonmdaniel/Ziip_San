import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Eye, Pencil, Plus, Search } from 'lucide-react'
import { CatalogSelect } from '@components/registry/CatalogSelect'
import { CepInput } from '@components/registry/CepInput'
import { InscriptionInput } from '@components/registry/InscriptionInput'
import { LookupField, type LookupValue } from '@components/registry/LookupField'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { Checkbox, Field, Input } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { Pagination } from '@components/ui/Pagination'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { errorMessage } from '@services/registry/errors'
import { blocks, commercialSectors, consumerUnits, localities, readingRoutes } from '@services/registry/resources'
import type { ListParams } from '@services/registry/types'
import { formatInscription, parseInscription } from '@utils/validators/inscription'
import { formatMatricula } from '@utils/validators/matricula'
import { orDash } from '@utils/labels'

interface Filters {
  q: string
  inscription: string
  localityId: LookupValue | null
  sectorId: LookupValue | null
  blockId: LookupValue | null
  waterStatusId: LookupValue | null
  sewerStatusId: LookupValue | null
  categoryId: LookupValue | null
  profileId: LookupValue | null
  routeId: LookupValue | null
  cep: string
  numberFrom: string
  numberTo: string
  includeDeleted: boolean
}

const EMPTY: Filters = {
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
}

/**
 * Converte os filtros em parâmetros da API. Observação: a inscrição completa vai em `q`,
 * porque o servidor reconhece o formato LLL.SSS.QQQ.LLLL.SSS ali (relatório 07, S1); ela tem
 * prioridade sobre a busca livre.
 */
export function toListParams(f: Filters, page: number): ListParams {
  const inscription = parseInscription(f.inscription)
  return {
    q: inscription ? formatInscription(inscription) : f.q.trim(),
    localityId: f.localityId?.id,
    sectorId: f.sectorId?.id,
    blockId: f.blockId?.id,
    waterStatusId: f.waterStatusId?.id,
    sewerStatusId: f.sewerStatusId?.id,
    categoryId: f.categoryId?.id,
    profileId: f.profileId?.id,
    routeId: f.routeId?.id,
    cep: f.cep || undefined,
    numberFrom: f.numberFrom.trim() || undefined,
    numberTo: f.numberTo.trim() || undefined,
    includeDeleted: f.includeDeleted || undefined,
    page,
    size: 25,
    sort: 'inscription,asc',
  }
}

/** S1: pesquisa de imóveis (substitui Consumers.tsx). Rota: /cadastro/imoveis */
export function PropertySearch() {
  const [draft, setDraft] = useState<Filters>(EMPTY)
  const [applied, setApplied] = useState<Filters>(EMPTY)
  const [page, setPage] = useState(0)
  const qRef = useRef<HTMLInputElement>(null)

  // Atalho "/" foca a busca (padrão de pesquisa), sem interferir quando já se está digitando.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      const typing = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT' || target.isContentEditable)
      if (event.key === '/' && !typing && !event.ctrlKey && !event.metaKey && !event.altKey) {
        event.preventDefault()
        qRef.current?.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  const params = toListParams(applied, page)
  const list = useQuery({
    queryKey: ['consumer-units', params],
    queryFn: ({ signal }) => consumerUnits.list(params, signal),
  })

  const set = <K extends keyof Filters>(key: K, value: Filters[K]) => setDraft((d) => ({ ...d, [key]: value }))

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    setApplied(draft)
    setPage(0)
  }

  const onClear = () => {
    setDraft(EMPTY)
    setApplied(EMPTY)
    setPage(0)
  }

  const hasInscription = parseInscription(draft.inscription) !== null

  return (
    <div className="space-y-6">
      <PageHeader
        title="Imóveis"
        description="Pesquise por matrícula, inscrição, nome, documento ou endereço. Use a tecla / para ir direto à busca."
        actions={
          <Link
            to="/cadastro/imoveis/novo"
            className="inline-flex h-10 items-center gap-2 rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            <Plus className="h-4 w-4" aria-hidden="true" />
            Cadastrar imóvel
          </Link>
        }
      />

      <form onSubmit={onSubmit} role="search" aria-label="Pesquisar imóveis" className="space-y-4 rounded-xl border bg-card p-4">
        <div className="grid gap-4 md:grid-cols-2">
          <Field
            label="Busca livre"
            hint="Matrícula, nome, CPF/CNPJ ou endereço. O servidor reconhece o formato digitado."
          >
            {(aria) => (
              <Input
                {...aria}
                ref={qRef}
                type="search"
                value={draft.q}
                onChange={(e) => set('q', e.target.value)}
                disabled={hasInscription}
                autoComplete="off"
              />
            )}
          </Field>
          <InscriptionInput
            value={draft.inscription}
            onChange={(v) => set('inscription', v)}
            hint={hasInscription ? 'A inscrição completa tem prioridade sobre a busca livre.' : undefined}
          />
        </div>

        <details className="rounded-lg border p-3">
          <summary className="cursor-pointer text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
            Mais filtros
          </summary>
          <div className="mt-4 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            <LookupField
              label="Localidade"
              source={localities}
              value={draft.localityId}
              onChange={(v) => setDraft((d) => ({ ...d, localityId: v, sectorId: null, blockId: null }))}
            />
            <LookupField
              label="Setor comercial"
              source={commercialSectors}
              filters={draft.localityId ? { localityId: draft.localityId.id } : undefined}
              disabled={!draft.localityId}
              hint={!draft.localityId ? 'Escolha primeiro a localidade.' : undefined}
              value={draft.sectorId}
              onChange={(v) => setDraft((d) => ({ ...d, sectorId: v, blockId: null }))}
            />
            <LookupField
              label="Quadra"
              source={blocks}
              filters={draft.sectorId ? { sectorId: draft.sectorId.id } : undefined}
              disabled={!draft.sectorId}
              hint={!draft.sectorId ? 'Escolha primeiro o setor.' : undefined}
              value={draft.blockId}
              onChange={(v) => set('blockId', v)}
            />
            <CatalogSelect label="Situação de água" resource="water-statuses" value={draft.waterStatusId} onChange={(v) => set('waterStatusId', v)} />
            <CatalogSelect label="Situação de esgoto" resource="sewer-statuses" value={draft.sewerStatusId} onChange={(v) => set('sewerStatusId', v)} />
            <CatalogSelect label="Categoria" resource="categories" value={draft.categoryId} onChange={(v) => set('categoryId', v)} />
            <CatalogSelect label="Perfil do imóvel" resource="property-profiles" value={draft.profileId} onChange={(v) => set('profileId', v)} />
            <LookupField label="Rota de leitura" source={readingRoutes} value={draft.routeId} onChange={(v) => set('routeId', v)} />
            <CepInput value={draft.cep} onChange={(v) => set('cep', v)} />
            <Field label="Número do imóvel: de">
              {(aria) => <Input {...aria} value={draft.numberFrom} onChange={(e) => set('numberFrom', e.target.value)} inputMode="numeric" />}
            </Field>
            <Field label="Número do imóvel: até">
              {(aria) => <Input {...aria} value={draft.numberTo} onChange={(e) => set('numberTo', e.target.value)} inputMode="numeric" />}
            </Field>
            <Checkbox
              label="Incluir imóveis excluídos"
              hint="Só funciona para quem tem permissão; o servidor decide."
              checked={draft.includeDeleted}
              onChange={(e) => set('includeDeleted', e.target.checked)}
            />
          </div>
        </details>

        <div className="flex flex-wrap gap-2">
          <Button type="submit">
            <Search className="h-4 w-4" aria-hidden="true" />
            Pesquisar
          </Button>
          <Button variant="outline" onClick={onClear}>
            Limpar filtros
          </Button>
        </div>
      </form>

      <Card>
        <CardContent className="pt-6">
          <div aria-live="polite">
            {list.isLoading ? (
              <Spinner label="Carregando imóveis..." />
            ) : list.isError ? (
              <Alert tone="error" title="Não foi possível carregar os imóveis">
                <p>{errorMessage(list.error)}</p>
                <Button size="sm" variant="outline" className="mt-2" onClick={() => void list.refetch()}>
                  Tentar novamente
                </Button>
              </Alert>
            ) : list.data && list.data.content.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">Nenhum imóvel encontrado com estes filtros.</p>
            ) : null}
          </div>
          {list.data && list.data.content.length > 0 ? (
            <>
              <Table caption="Imóveis encontrados">
                <TableHeader>
                  <TableRow>
                    <TableHead>Matrícula</TableHead>
                    <TableHead>Inscrição</TableHead>
                    <TableHead>Usuário</TableHead>
                    <TableHead>Endereço</TableHead>
                    <TableHead>Categoria principal</TableHead>
                    <TableHead>Água</TableHead>
                    <TableHead>Esgoto</TableHead>
                    <TableHead className="text-right">Economias</TableHead>
                    <TableHead className="text-right">Ações</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {list.data.content.map((u) => {
                    const matricula = formatMatricula(u.registration, u.registrationDv)
                    return (
                      <TableRow key={u.id}>
                        <TableCell className="whitespace-nowrap font-mono">
                          <Link to={`/cadastro/imoveis/${u.id}`} className="font-semibold text-primary underline underline-offset-4">
                            {matricula}
                          </Link>
                          {u.deleted ? (
                            <Badge variant="destructive" className="ml-2">
                              Excluído
                            </Badge>
                          ) : null}
                        </TableCell>
                        <TableCell className="whitespace-nowrap font-mono">{formatInscription(u.inscription)}</TableCell>
                        <TableCell>
                          <span className="block">{orDash(u.userName)}</span>
                          {u.userDocumentMasked ? (
                            <span className="block font-mono text-xs text-muted-foreground">
                              <span className="sr-only">Documento mascarado: </span>
                              {u.userDocumentMasked}
                            </span>
                          ) : null}
                        </TableCell>
                        <TableCell>{u.addressText}</TableCell>
                        <TableCell>{orDash(u.mainCategoryName)}</TableCell>
                        <TableCell>{orDash(u.waterStatusName)}</TableCell>
                        <TableCell>{orDash(u.sewerStatusName)}</TableCell>
                        <TableCell className="text-right">{u.economies}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-1">
                            <Link
                              to={`/cadastro/imoveis/${u.id}`}
                              aria-label={`Abrir ficha do imóvel ${matricula}`}
                              className="inline-flex h-9 items-center gap-1 rounded-md px-3 text-sm font-medium hover:bg-accent"
                            >
                              <Eye className="h-4 w-4" aria-hidden="true" />
                              <span className="hidden xl:inline">Ficha</span>
                            </Link>
                            {!u.deleted ? (
                              <Link
                                to={`/cadastro/imoveis/${u.id}/editar`}
                                aria-label={`Editar imóvel ${matricula}`}
                                className="inline-flex h-9 items-center gap-1 rounded-md px-3 text-sm font-medium hover:bg-accent"
                              >
                                <Pencil className="h-4 w-4" aria-hidden="true" />
                                <span className="hidden xl:inline">Editar</span>
                              </Link>
                            ) : null}
                          </div>
                        </TableCell>
                      </TableRow>
                    )
                  })}
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
