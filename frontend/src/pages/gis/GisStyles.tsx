import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { Field, Select } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { useCan } from '@hooks/usePermissions'
import { errorMessage } from '@services/registry/errors'
import { gisMap, type GisStyleRow } from '@services/gis/map'
import { MAP_TOKENS } from '@services/gis/mapStyle'

/**
 * GIS-06 (§10.2): tela de estilos — a simbologia sai da fonte única
 * (gis_style); o tenant sobrescreve por tipo e estado SEM código:
 * cor como token map-* (ADR-0017), padrão de traço do padrão Ziip,
 * halo de precisão e o símbolo ESCOLHIDO DA BIBLIOTECA da P1.30
 * (nunca SVG enviado). Restaurar apaga o override e volta o padrão
 * de fábrica, que a tabela mostra mas não deixa apagar.
 */
const DASH_PRESETS: { label: string; value: string; dash: number[] | null }[] = [
  { label: 'Contínuo (em operação)', value: 'continuous', dash: [] },
  { label: 'Tracejado (projetado)', value: 'planned', dash: [3, 2] },
  { label: 'Traço e ponto (em obra)', value: 'construction', dash: [4, 2, 1, 2] },
  { label: 'Tracejado fino (abandonado)', value: 'abandoned', dash: [2, 2] },
  { label: 'Herdar da fábrica', value: 'inherit', dash: null },
]

function dashPresetValue(dash: number[] | null): string {
  if (dash === null) return 'inherit'
  const found = DASH_PRESETS.find(
    (p) => p.dash !== null && JSON.stringify(p.dash) === JSON.stringify(dash),
  )
  return found?.value ?? 'inherit'
}

interface RowDraft {
  colorToken: string
  dashPreset: string
  iconKey: string
}

export function GisStyles() {
  const canManage = useCan('gis:style-manage')
  const queryClient = useQueryClient()
  const [drafts, setDrafts] = useState<Record<string, RowDraft>>({})
  const [actionError, setActionError] = useState<string | null>(null)

  const rowsQuery = useQuery({ queryKey: ['gis', 'map', 'styles'], queryFn: gisMap.styleRows })
  const symbolsQuery = useQuery({
    queryKey: ['gis', 'map', 'symbols'],
    queryFn: gisMap.symbols,
  })

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['gis', 'map'] })

  const save = useMutation({
    mutationFn: (args: { row: GisStyleRow; draft: RowDraft }) =>
      gisMap.saveOverride({
        featureTypeId: args.row.featureTypeId,
        domainKey: args.row.featureTypeId ? null : args.row.domainKey,
        lifecycleStatus: args.row.lifecycleStatus,
        accuracyStatus: args.row.accuracyStatus,
        colorToken: args.draft.colorToken || null,
        lineDash: DASH_PRESETS.find((p) => p.value === args.draft.dashPreset)?.dash ?? null,
        iconKey: args.draft.iconKey || null,
      }),
    onSuccess: invalidate,
    onError: (e) => setActionError(errorMessage(e)),
  })
  const restore = useMutation({
    mutationFn: (id: string) => gisMap.restoreFactory(id),
    onSuccess: invalidate,
    onError: (e) => setActionError(errorMessage(e)),
  })

  const symbolsByDomain = useMemo(() => symbolsQuery.data ?? [], [symbolsQuery.data])

  if (rowsQuery.isLoading || symbolsQuery.isLoading) return <Spinner />
  if (rowsQuery.isError) return <Alert>{errorMessage(rowsQuery.error)}</Alert>
  if (symbolsQuery.isError) return <Alert>{errorMessage(symbolsQuery.error)}</Alert>

  const rows = rowsQuery.data ?? []

  return (
    <div>
      <PageHeader
        title="Estilos do mapa"
        description="Padrão Ziip por tipo e estado; o override do tenant vale sem código e restaurar volta a fábrica."
      />
      {actionError && <Alert>{actionError}</Alert>}
      {!canManage && (
        <Alert>Sem a permissão gis.style.manage, a tela é só de consulta.</Alert>
      )}
      <Card>
        <CardContent>
          <Table caption="Estilos por tipo, domínio e estado">
            <TableHeader>
              <TableRow>
                <TableHead>Escopo</TableHead>
                <TableHead>Estado</TableHead>
                <TableHead>Origem</TableHead>
                <TableHead>Cor (token map-*)</TableHead>
                <TableHead>Traço</TableHead>
                <TableHead>Símbolo da biblioteca</TableHead>
                <TableHead>Ações</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => {
                const draft: RowDraft = drafts[row.id] ?? {
                  colorToken: row.colorToken ?? '',
                  dashPreset: dashPresetValue(row.lineDash),
                  iconKey: row.iconKey ?? '',
                }
                const setDraft = (patch: Partial<RowDraft>) =>
                  setDrafts((prev) => ({ ...prev, [row.id]: { ...draft, ...patch } }))
                const scope = row.featureTypeCode
                  ? `Tipo ${row.featureTypeCode}`
                  : row.domainKey
                    ? `Domínio ${row.domainKey}`
                    : 'Genérico'
                return (
                  <TableRow key={row.id}>
                    <TableCell label="Escopo">{scope}</TableCell>
                    <TableCell label="Estado">
                      {row.lifecycleStatus}
                      {row.accuracyStatus ? ` / ${row.accuracyStatus}` : ''}
                    </TableCell>
                    <TableCell label="Origem">
                      <Badge>{row.origin === 'TENANT' ? 'Tenant' : 'Ziip (fábrica)'}</Badge>
                    </TableCell>
                    <TableCell label="Cor (token map-*)">
                      <Field label={`Cor de ${scope} ${row.lifecycleStatus}`} id={`color-${row.id}`}>
                        {(aria) => (
                          <Select
                            {...aria}
                            value={draft.colorToken}
                            disabled={!canManage}
                            onChange={(e) => setDraft({ colorToken: e.target.value })}
                          >
                            <option value="">Herdar</option>
                            {MAP_TOKENS.map((token) => (
                              <option key={token} value={token}>
                                {token}
                              </option>
                            ))}
                          </Select>
                        )}
                      </Field>
                    </TableCell>
                    <TableCell label="Traço">
                      <Field label={`Traço de ${scope} ${row.lifecycleStatus}`} id={`dash-${row.id}`}>
                        {(aria) => (
                          <Select
                            {...aria}
                            value={draft.dashPreset}
                            disabled={!canManage}
                            onChange={(e) => setDraft({ dashPreset: e.target.value })}
                          >
                            {DASH_PRESETS.map((preset) => (
                              <option key={preset.value} value={preset.value}>
                                {preset.label}
                              </option>
                            ))}
                          </Select>
                        )}
                      </Field>
                    </TableCell>
                    <TableCell label="Símbolo da biblioteca">
                      <Field
                        label={`Símbolo de ${scope} ${row.lifecycleStatus}`}
                        id={`symbol-${row.id}`}
                      >
                        {(aria) => (
                          <Select
                            {...aria}
                            value={draft.iconKey}
                            disabled={!canManage}
                            onChange={(e) => setDraft({ iconKey: e.target.value })}
                          >
                            <option value="">Padrão do tipo</option>
                            {symbolsByDomain.map((symbol) => (
                              <option key={symbol.key} value={symbol.key}>
                                {symbol.key} — {symbol.typeLabel}
                              </option>
                            ))}
                          </Select>
                        )}
                      </Field>
                    </TableCell>
                    <TableCell label="Ações">
                      {canManage && (
                        <>
                          <Button onClick={() => save.mutate({ row, draft })}>
                            Salvar override
                          </Button>{' '}
                          {row.origin === 'TENANT' && (
                            <Button onClick={() => restore.mutate(row.id)}>
                              Restaurar padrão
                            </Button>
                          )}
                        </>
                      )}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}
