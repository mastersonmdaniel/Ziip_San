import { useEffect, useState } from 'react'
import { PageHeader } from '@components/ui/PageHeader'
import {
  buildAttributesSchema,
  gisCatalogApi,
  type GisClass,
  type GisItem,
  type GisMaterial,
  type GisReferenceReport,
  type GisType,
  type SchemaProperty,
} from '@services/gis/catalog'

/**
 * Catálogo GIS do tenant (P1.27): ativar e desativar, criar tipo com JSON
 * Schema montado por formulário e item do tenant — tudo aprovado por outra
 * pessoa, sem código nem migration. A aprovação por outra pessoa é garantida
 * no servidor (403 para o autor); a tela mostra o erro devolvido.
 *
 * Rota: /gis/catalogo. Item desativado some da criação (filtro "só usáveis")
 * e continua válido nas feições existentes — desativar nunca apaga.
 */
export function GisCatalog() {
  const [tab, setTab] = useState<'tipos' | 'itens' | 'relatorio'>('tipos')
  const [classes, setClasses] = useState<GisClass[]>([])
  const [types, setTypes] = useState<GisType[]>([])
  const [items, setItems] = useState<GisItem[]>([])
  const [materials, setMaterials] = useState<GisMaterial[]>([])
  const [report, setReport] = useState<GisReferenceReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [onlyUsable, setOnlyUsable] = useState(false)
  const [query, setQuery] = useState('')

  const [newType, setNewType] = useState({ classId: '', code: '', label: '', requiresCatalogItem: false })
  const [schemaRows, setSchemaRows] = useState<SchemaProperty[]>([{ name: '', type: 'string', required: false }])
  const [newItem, setNewItem] = useState({
    featureTypeId: '', materialId: '', dnMm: '', pressureClass: '', jointType: '', manufacturer: '', model: '',
  })

  async function reload() {
    try {
      setError(null)
      const [cls, tys, its, mats, rep] = await Promise.all([
        gisCatalogApi.listClasses(),
        gisCatalogApi.listTypes({ q: query || undefined, usable: onlyUsable || undefined }),
        gisCatalogApi.listItems({ usable: onlyUsable || undefined }),
        gisCatalogApi.listMaterials(),
        gisCatalogApi.referenceReport(),
      ])
      setClasses(cls)
      setTypes(tys)
      setItems(its)
      setMaterials(mats)
      setReport(rep)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Falha ao carregar o catálogo.')
    }
  }

  useEffect(() => {
    void reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, onlyUsable])

  async function createType() {
    try {
      setError(null)
      await gisCatalogApi.createType({
        classId: newType.classId,
        code: newType.code,
        label: newType.label,
        attributesSchema: buildAttributesSchema(schemaRows),
        requiresCatalogItem: newType.requiresCatalogItem,
      })
      setNewType({ classId: '', code: '', label: '', requiresCatalogItem: false })
      setSchemaRows([{ name: '', type: 'string', required: false }])
      await reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Falha ao criar o tipo.')
    }
  }

  async function createItem() {
    try {
      setError(null)
      await gisCatalogApi.createItem({
        featureTypeId: newItem.featureTypeId,
        materialId: newItem.materialId || null,
        dnMm: newItem.dnMm ? Number(newItem.dnMm) : null,
        pressureClass: newItem.pressureClass || null,
        jointType: newItem.jointType || null,
        manufacturer: newItem.manufacturer || null,
        model: newItem.model || null,
      })
      setNewItem({ featureTypeId: '', materialId: '', dnMm: '', pressureClass: '', jointType: '', manufacturer: '', model: '' })
      await reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Falha ao criar o item.')
    }
  }

  async function act(promise: Promise<unknown>) {
    try {
      setError(null)
      await promise
      await reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Operação recusada.')
    }
  }

  const usableTypes = types.filter((t) => t.active && t.approvalStatus === 'APPROVED')

  return (
    <div className="space-y-6">
      <PageHeader
        title="Catálogo GIS"
        description="Tipos e itens de água e esgoto: fábrica e do tenant. Tipo ou item novo fica pendente até aprovação por outra pessoa."
      />
      {error && (
        <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      )}
      <div className="flex flex-wrap items-center gap-2" role="tablist" aria-label="Seções do catálogo">
        {(['tipos', 'itens', 'relatorio'] as const).map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={tab === t ? 'rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground' : 'rounded-lg px-3 py-2 text-sm hover:bg-accent'}
          >
            {t === 'tipos' ? 'Tipos' : t === 'itens' ? 'Itens' : 'Relatório'}
          </button>
        ))}
        <label className="ml-auto flex items-center gap-2 text-sm">
          <input type="checkbox" checked={onlyUsable} onChange={(e) => setOnlyUsable(e.target.checked)} />
          Só usáveis (aprovados e ativos)
        </label>
      </div>

      {tab === 'tipos' && (
        <section className="space-y-6" aria-label="Tipos">
          <div>
            <label className="mb-1 block text-sm font-medium" htmlFor="gis-busca">Buscar tipo</label>
            <div className="flex gap-2">
              <input
                id="gis-busca"
                className="rounded-lg border px-3 py-2 text-sm"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="code ou rótulo"
              />
              <button className="rounded-lg bg-primary px-3 py-2 text-sm text-primary-foreground" onClick={() => void reload()}>
                Buscar
              </button>
            </div>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left"><th>Código</th><th>Rótulo</th><th>Classe</th><th>Origem</th><th>Situação</th><th>Ativo</th><th>Ações</th></tr>
            </thead>
            <tbody>
              {types.map((t) => (
                <tr key={t.id}>
                  <td>{t.code}</td>
                  <td>{t.label}</td>
                  <td>{t.classCode}</td>
                  <td>{t.origin}</td>
                  <td>{t.approvalStatus ?? '—'}</td>
                  <td>{t.active ? 'Sim' : 'Não'}</td>
                  <td className="space-x-2">
                    {t.approvalStatus === 'PENDING' && (
                      <>
                        <button onClick={() => void act(gisCatalogApi.approveType(t.id))}>Aprovar</button>
                        <button onClick={() => void act(gisCatalogApi.rejectType(t.id))}>Recusar</button>
                      </>
                    )}
                    {t.approvalStatus === 'APPROVED' && (
                      <button onClick={() => void act(gisCatalogApi.setTypeActive(t.id, !t.active))}>
                        {t.active ? 'Desativar' : 'Ativar'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <form
            className="space-y-3 rounded-lg border p-4"
            onSubmit={(e) => {
              e.preventDefault()
              void createType()
            }}
          >
            <h3 className="font-semibold">Criar tipo do tenant</h3>
            <div className="flex flex-wrap gap-2">
              <label className="text-sm">
                Classe
                <select
                  aria-label="Classe"
                  className="ml-1 rounded-lg border px-2 py-1"
                  value={newType.classId}
                  onChange={(e) => setNewType({ ...newType, classId: e.target.value })}
                  required
                >
                  <option value="">Selecione</option>
                  {classes.map((c) => (
                    <option key={c.id} value={c.id}>{c.code} ({c.domain})</option>
                  ))}
                </select>
              </label>
              <label className="text-sm">
                Código
                <input
                  aria-label="Código do tipo"
                  className="ml-1 rounded-lg border px-2 py-1"
                  value={newType.code}
                  onChange={(e) => setNewType({ ...newType, code: e.target.value.toUpperCase() })}
                  required
                />
              </label>
              <label className="text-sm">
                Rótulo
                <input
                  aria-label="Rótulo do tipo"
                  className="ml-1 rounded-lg border px-2 py-1"
                  value={newType.label}
                  onChange={(e) => setNewType({ ...newType, label: e.target.value })}
                  required
                />
              </label>
              <label className="flex items-center gap-1 text-sm">
                <input
                  type="checkbox"
                  checked={newType.requiresCatalogItem}
                  onChange={(e) => setNewType({ ...newType, requiresCatalogItem: e.target.checked })}
                />
                Exige item de catálogo
              </label>
            </div>
            <fieldset>
              <legend className="text-sm font-medium">Atributos (JSON Schema montado pelo formulário)</legend>
              {schemaRows.map((row, i) => (
                <div key={i} className="flex flex-wrap gap-2 py-1">
                  <input
                    aria-label={`Nome do atributo ${i + 1}`}
                    className="rounded-lg border px-2 py-1 text-sm"
                    value={row.name}
                    onChange={(e) => {
                      const rows = [...schemaRows]
                      rows[i] = { ...rows[i], name: e.target.value }
                      setSchemaRows(rows)
                    }}
                    placeholder="nome do atributo"
                  />
                  <select
                    aria-label={`Tipo do atributo ${i + 1}`}
                    className="rounded-lg border px-2 py-1 text-sm"
                    value={row.type}
                    onChange={(e) => {
                      const rows = [...schemaRows]
                      rows[i] = { ...rows[i], type: e.target.value as SchemaProperty['type'] }
                      setSchemaRows(rows)
                    }}
                  >
                    <option value="string">texto</option>
                    <option value="integer">inteiro</option>
                    <option value="number">número</option>
                    <option value="boolean">booleano</option>
                  </select>
                  <label className="flex items-center gap-1 text-sm">
                    <input
                      type="checkbox"
                      checked={row.required}
                      onChange={(e) => {
                        const rows = [...schemaRows]
                        rows[i] = { ...rows[i], required: e.target.checked }
                        setSchemaRows(rows)
                      }}
                    />
                    Obrigatório
                  </label>
                </div>
              ))}
              <button
                type="button"
                className="rounded-lg border px-2 py-1 text-sm"
                onClick={() => setSchemaRows([...schemaRows, { name: '', type: 'string', required: false }])}
              >
                Adicionar atributo
              </button>
              <pre className="mt-2 overflow-auto rounded-lg bg-muted p-2 text-xs">{buildAttributesSchema(schemaRows)}</pre>
            </fieldset>
            <button type="submit" className="rounded-lg bg-primary px-3 py-2 text-sm text-primary-foreground">
              Criar tipo (fica pendente)
            </button>
          </form>
        </section>
      )}

      {tab === 'itens' && (
        <section className="space-y-6" aria-label="Itens">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left"><th>Tipo</th><th>Material</th><th>DN</th><th>Classe de pressão</th><th>Referência</th><th>Origem</th><th>Situação</th><th>Ativo</th><th>Ações</th></tr>
            </thead>
            <tbody>
              {items.map((it) => (
                <tr key={it.id}>
                  <td>{it.featureTypeCode}</td>
                  <td>{it.materialCode ?? '—'}</td>
                  <td>{it.dnMm ?? '—'}</td>
                  <td>{it.pressureClass ?? '—'}</td>
                  <td>{it.referenceStatus}</td>
                  <td>{it.origin}</td>
                  <td>{it.approvalStatus ?? '—'}</td>
                  <td>{it.active ? 'Sim' : 'Não'}</td>
                  <td className="space-x-2">
                    {it.approvalStatus === 'PENDING' && (
                      <>
                        <button onClick={() => void act(gisCatalogApi.approveItem(it.id))}>Aprovar</button>
                        <button onClick={() => void act(gisCatalogApi.rejectItem(it.id))}>Recusar</button>
                      </>
                    )}
                    {it.approvalStatus === 'APPROVED' && (
                      <button onClick={() => void act(gisCatalogApi.setItemActive(it.id, !it.active))}>
                        {it.active ? 'Desativar' : 'Ativar'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <form
            className="space-y-3 rounded-lg border p-4"
            onSubmit={(e) => {
              e.preventDefault()
              void createItem()
            }}
          >
            <h3 className="font-semibold">Criar item do tenant</h3>
            <p className="text-sm text-muted-foreground">
              Só tipos aprovados e ativos aparecem aqui. O item nasce pendente e com referência TENANT.
            </p>
            <div className="flex flex-wrap gap-2">
              <label className="text-sm">
                Tipo
                <select
                  aria-label="Tipo do item"
                  className="ml-1 rounded-lg border px-2 py-1"
                  value={newItem.featureTypeId}
                  onChange={(e) => setNewItem({ ...newItem, featureTypeId: e.target.value })}
                  required
                >
                  <option value="">Selecione</option>
                  {usableTypes.map((t) => (
                    <option key={t.id} value={t.id}>{t.code}</option>
                  ))}
                </select>
              </label>
              <label className="text-sm">
                Material
                <select
                  aria-label="Material do item"
                  className="ml-1 rounded-lg border px-2 py-1"
                  value={newItem.materialId}
                  onChange={(e) => setNewItem({ ...newItem, materialId: e.target.value })}
                >
                  <option value="">Sem material</option>
                  {materials.map((m) => (
                    <option key={m.id} value={m.id}>{m.code}</option>
                  ))}
                </select>
              </label>
              <label className="text-sm">
                DN (mm)
                <input
                  aria-label="DN do item"
                  type="number"
                  className="ml-1 rounded-lg border px-2 py-1"
                  value={newItem.dnMm}
                  onChange={(e) => setNewItem({ ...newItem, dnMm: e.target.value })}
                />
              </label>
              <label className="text-sm">
                Classe de pressão
                <input
                  aria-label="Classe de pressão do item"
                  className="ml-1 rounded-lg border px-2 py-1"
                  value={newItem.pressureClass}
                  onChange={(e) => setNewItem({ ...newItem, pressureClass: e.target.value })}
                />
              </label>
              <label className="text-sm">
                Junta
                <input
                  aria-label="Junta do item"
                  className="ml-1 rounded-lg border px-2 py-1"
                  value={newItem.jointType}
                  onChange={(e) => setNewItem({ ...newItem, jointType: e.target.value })}
                />
              </label>
            </div>
            <button type="submit" className="rounded-lg bg-primary px-3 py-2 text-sm text-primary-foreground">
              Criar item (fica pendente)
            </button>
          </form>
        </section>
      )}

      {tab === 'relatorio' && report && (
        <section className="space-y-3" aria-label="Relatório de referência a validar">
          <h3 className="font-semibold">Itens a validar</h3>
          <dl className="grid max-w-xl grid-cols-2 gap-2 text-sm">
            <dt>Tipos de fábrica</dt><dd>{report.factoryTypes}</dd>
            <dt>Itens de fábrica</dt><dd>{report.factoryItems}</dd>
            <dt>Itens a validar</dt><dd>{report.factoryItemsToValidate}</dd>
            <dt>Itens STANDARD</dt><dd>{report.factoryItemsStandard}</dd>
            <dt>Tipos de fábrica retirados</dt><dd>{report.factoryTypesRetired}</dd>
            <dt>Itens de fábrica retirados</dt><dd>{report.factoryItemsRetired}</dd>
            <dt>Tipos do tenant pendentes</dt><dd>{report.tenantTypesPending}</dd>
            <dt>Itens do tenant pendentes</dt><dd>{report.tenantItemsPending}</dd>
          </dl>
          <p className="rounded-lg border border-warning bg-warning/10 px-3 py-2 text-sm text-warning">{report.note}</p>
        </section>
      )}
    </div>
  )
}
