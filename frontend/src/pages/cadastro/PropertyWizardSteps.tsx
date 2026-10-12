import { useState } from 'react'
import { Controller, useFieldArray, type UseFormReturn } from 'react-hook-form'
import { Plus, Trash2, X } from 'lucide-react'
import { AddressFields } from '@components/registry/AddressFields'
import { CatalogSelect } from '@components/registry/CatalogSelect'
import { LookupField, type LookupItem, type LookupValue } from '@components/registry/LookupField'
import { Button } from '@components/ui/Button'
import { Checkbox, Field, Input, Select, Textarea } from '@components/ui/Field'
import { catalogSource, blockFaces, blocks, commercialSectors, consumerUnits, customers, localities, readingRoutes } from '@services/registry/resources'
import type { CatalogLookupItem, ConsumerUnitSummary, ListParams, Page } from '@services/registry/types'
import { RELATION_LABEL } from '@utils/labels'
import { formatInscription, isValidInscription } from '@utils/validators/inscription'
import { formatMatricula } from '@utils/validators/matricula'
import type { WizardValues } from './propertyForm'

export interface StepProps {
  form: UseFormReturn<WizardValues>
}

export interface Step1Props extends StepProps {
  useBlockFace: boolean
  requireReadingSequence: boolean
}

export interface Step5Props extends StepProps {
  /** A situação de esgoto escolhida fatura: esgotamento obrigatório. */
  sewerRequiresDischarge: boolean
}

// ---------------------------------------------------------------------------------------------
// Etapa 1: Localização
// ---------------------------------------------------------------------------------------------

export function StepLocation({ form, useBlockFace, requireReadingSequence }: Step1Props) {
  const { control, register, setValue, watch, formState } = form
  const { errors } = formState
  const locality = watch('localityId')
  const sector = watch('sectorId')
  const block = watch('blockId')
  const lot = watch('lot')
  const sublot = watch('sublot')

  const lotN = Number(lot)
  const sublotN = Number(sublot)
  const preview =
    locality?.code != null && sector?.code != null && block?.code != null && lot !== '' && sublot !== ''
      ? { locality: Number(locality.code), sector: Number(sector.code), block: Number(block.code), lot: lotN, sublot: sublotN }
      : null

  return (
    <div className="space-y-4">
      <div className="grid gap-4 md:grid-cols-3">
        <Controller
          control={control}
          name="localityId"
          render={({ field }) => (
            <LookupField
              label="Localidade"
              required
              source={localities}
              value={field.value}
              error={errors.localityId?.message}
              onChange={(v) => {
                field.onChange(v)
                setValue('sectorId', null)
                setValue('blockId', null)
                setValue('blockFaceId', null)
              }}
            />
          )}
        />
        <Controller
          control={control}
          name="sectorId"
          render={({ field }) => (
            <LookupField
              label="Setor comercial"
              required
              source={commercialSectors}
              filters={locality ? { localityId: locality.id } : undefined}
              disabled={!locality}
              hint={!locality ? 'Escolha primeiro a localidade.' : undefined}
              value={field.value}
              error={errors.sectorId?.message}
              onChange={(v) => {
                field.onChange(v)
                setValue('blockId', null)
                setValue('blockFaceId', null)
              }}
            />
          )}
        />
        <Controller
          control={control}
          name="blockId"
          render={({ field }) => (
            <LookupField
              label="Quadra"
              required
              source={blocks}
              filters={sector ? { sectorId: sector.id } : undefined}
              disabled={!sector}
              hint={!sector ? 'Escolha primeiro o setor.' : undefined}
              value={field.value}
              error={errors.blockId?.message}
              onChange={(v) => {
                field.onChange(v)
                setValue('blockFaceId', null)
              }}
            />
          )}
        />
      </div>

      {useBlockFace ? (
        <Controller
          control={control}
          name="blockFaceId"
          render={({ field }) => (
            <LookupField
              label="Face da quadra"
              required
              source={blockFaces}
              filters={block ? { blockId: block.id } : undefined}
              disabled={!block}
              hint={!block ? 'Escolha primeiro a quadra.' : 'Este tenant usa face de quadra.'}
              value={field.value}
              error={errors.blockFaceId?.message}
              onChange={(v) => field.onChange(v)}
            />
          )}
        />
      ) : null}

      <div className="grid gap-4 md:grid-cols-4">
        <Field label="Lote" required hint="De 1 a 9999." error={errors.lot?.message}>
          {(aria) => <Input {...aria} {...register('lot')} inputMode="numeric" maxLength={4} />}
        </Field>
        <Field label="Sublote" required hint="De 0 a 999." error={errors.sublot?.message}>
          {(aria) => <Input {...aria} {...register('sublot')} inputMode="numeric" maxLength={3} />}
        </Field>
        <Field label="Sequência na rota" required={requireReadingSequence} hint="Se já estiver ocupada, o sistema pede confirmação." error={errors.readingSequence?.message}>
          {(aria) => <Input {...aria} {...register('readingSequence')} inputMode="numeric" />}
        </Field>
        <Field label="Testada do lote (m)" error={errors.lotFrontageM?.message}>
          {(aria) => <Input {...aria} {...register('lotFrontageM')} inputMode="decimal" />}
        </Field>
      </div>

      <p className="rounded-lg bg-muted p-3 text-sm" aria-live="polite">
        Inscrição resultante:{' '}
        <span className="font-mono font-semibold">
          {preview && isValidInscription(preview) ? formatInscription(preview) : 'preencha localidade, setor, quadra, lote e sublote'}
        </span>
        . A inscrição é única; se já existir, o sistema informa a matrícula do imóvel que a usa.
      </p>
    </div>
  )
}

// ---------------------------------------------------------------------------------------------
// Etapa 2: Endereço
// ---------------------------------------------------------------------------------------------

export function StepAddress({ form }: StepProps) {
  const { control, formState } = form
  const e = formState.errors.address
  return (
    <Controller
      control={control}
      name="address"
      render={({ field }) => (
        <AddressFields
          withCoordinates
          value={field.value}
          onChange={field.onChange}
          errors={{
            cep: e?.cep?.message,
            streetName: e?.streetName?.message,
            number: e?.number?.message,
            complement: e?.complement?.message,
            neighborhood: e?.neighborhood?.message,
            city: e?.city?.message,
            state: e?.state?.message,
            coordinates: e?.coordinates?.lat?.message ?? e?.coordinates?.message,
          }}
        />
      )}
    />
  )
}

// ---------------------------------------------------------------------------------------------
// Etapa 3: Clientes
// ---------------------------------------------------------------------------------------------

export function StepCustomers({ form }: StepProps) {
  const { control, register, setValue, watch, formState } = form
  const { errors } = formState
  const rows = useFieldArray({ control, name: 'customers' })
  const setShowOnBill = (index: number) => rows.fields.forEach((_, i) => setValue(`customers.${i}.showOnBill`, i === index, { shouldDirty: true }))
  const hasResponsible = watch('customers').some((c) => c.relation === 'RESPONSIBLE')

  return (
    <div className="space-y-4">
      {rows.fields.map((item, index) => {
        const e = errors.customers?.[index]
        return (
          <fieldset key={item.id} className="space-y-4 rounded-lg border p-4">
            <legend className="px-1 text-sm font-semibold">Cliente {index + 1}</legend>
            <div className="grid gap-4 md:grid-cols-3">
              <div className="md:col-span-3">
                <Controller
                  control={control}
                  name={`customers.${index}.customerId`}
                  render={({ field }) => (
                    <LookupField
                      label={`Cliente ${index + 1}`}
                      required
                      source={customers}
                      getLabel={(c) => c.name}
                      value={field.value}
                      error={e?.customerId?.message}
                      onChange={(v) => field.onChange(v)}
                      hint="Busque pelo nome. O documento nunca aparece completo aqui."
                    />
                  )}
                />
              </div>
              <Field label="Relação com o imóvel" required error={e?.relation?.message}>
                {(aria) => (
                  <Select {...aria} {...register(`customers.${index}.relation`)}>
                    {Object.entries(RELATION_LABEL).map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </Select>
                )}
              </Field>
              <Field label="Início da relação" required error={e?.startDate?.message}>
                {(aria) => <Input {...aria} type="date" {...register(`customers.${index}.startDate`)} />}
              </Field>
              <div className="flex items-end">
                <div>
                  <label className="flex items-center gap-2 text-sm font-medium">
                    <input
                      type="radio"
                      name="show-on-bill"
                      checked={watch(`customers.${index}.showOnBill`)}
                      onChange={() => setShowOnBill(index)}
                      className="h-4 w-4 accent-[hsl(var(--primary))]"
                    />
                    Nome na conta
                  </label>
                  {e?.showOnBill?.message ? <p className="mt-1 text-sm text-destructive">{e.showOnBill.message}</p> : null}
                </div>
              </div>
            </div>
            {rows.fields.length > 1 ? (
              <Button variant="outline" size="sm" onClick={() => rows.remove(index)}>
                <Trash2 className="h-4 w-4" aria-hidden="true" />
                Remover cliente {index + 1}
              </Button>
            ) : null}
          </fieldset>
        )
      })}
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="outline" onClick={() => rows.append({ customerId: null, relation: 'OWNER', startDate: '', showOnBill: false })}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          Adicionar cliente
        </Button>
        <a href="/cadastro/clientes/novo" target="_blank" rel="noopener noreferrer" className="text-sm font-medium text-primary underline underline-offset-4">
          Cadastrar novo cliente (abre em outra aba)
        </a>
      </div>
      <p className="text-xs text-muted-foreground">É preciso ao menos um cliente com a relação Usuário e exatamente um marcado como nome na conta.</p>

      <Controller
        control={control}
        name="deliveryModeId"
        render={({ field }) => (
          <CatalogSelect
            label="Modo de envio da conta"
            resource="bill-delivery-modes"
            required={hasResponsible}
            hint={hasResponsible ? 'Obrigatório porque há um cliente Responsável. Se for por e-mail, o nome na conta precisa ter e-mail.' : undefined}
            value={field.value}
            error={errors.deliveryModeId?.message}
            onChange={(v) => field.onChange(v)}
          />
        )}
      />
    </div>
  )
}

// ---------------------------------------------------------------------------------------------
// Etapa 4: Categorias
// ---------------------------------------------------------------------------------------------

const subcategorySource = catalogSource('subcategories')

function MultiLookup({
  label,
  values,
  source,
  onChange,
}: {
  label: string
  values: LookupValue[]
  source: { path: string; list(params?: ListParams, signal?: AbortSignal): Promise<Page<CatalogLookupItem>> }
  onChange: (next: LookupValue[]) => void
}) {
  const [resetKey, setResetKey] = useState(0)
  return (
    <div className="space-y-2">
      <LookupField<CatalogLookupItem>
        key={resetKey}
        label={label}
        source={source}
        filters={{ active: true }}
        value={null}
        onChange={(v) => {
          if (v && !values.some((x) => x.id === v.id)) onChange([...values, v])
          setResetKey((k) => k + 1)
        }}
        hint="Escolha para adicionar; repetidos são ignorados."
      />
      {values.length > 0 ? (
        <ul className="flex flex-wrap gap-2" aria-label={`${label} escolhidos`}>
          {values.map((v) => (
            <li key={v.id} className="flex items-center gap-1 rounded-full border bg-secondary px-3 py-1 text-sm">
              {v.label}
              <button
                type="button"
                onClick={() => onChange(values.filter((x) => x.id !== v.id))}
                aria-label={`Remover ${v.label}`}
                className="rounded-full p-0.5 hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <X className="h-3.5 w-3.5" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

export function StepCategories({ form }: StepProps) {
  const { control, register, formState } = form
  const { errors } = formState
  const rows = useFieldArray({ control, name: 'subcategories' })

  return (
    <div className="space-y-4">
      {rows.fields.map((item, index) => {
        const e = errors.subcategories?.[index]
        return (
          <fieldset key={item.id} className="space-y-4 rounded-lg border p-4">
            <legend className="px-1 text-sm font-semibold">Subcategoria {index + 1}</legend>
            <Controller
              control={control}
              name={`subcategories.${index}.subcategoryId`}
              render={({ field }) => (
                <LookupField<CatalogLookupItem>
                  label={`Subcategoria ${index + 1}`}
                  required
                  source={subcategorySource}
                  filters={{ active: true }}
                  getLabel={(s) => (s.category ? `${s.category.name} › ${s.name}` : s.name)}
                  value={field.value}
                  error={e?.subcategoryId?.message}
                  onChange={(v) => field.onChange(v)}
                  hint="A categoria é derivada da subcategoria. Categorias públicas exigem permissão especial."
                />
              )}
            />
            <div className="grid gap-4 md:grid-cols-3">
              <Field label="Quantidade de economias" required hint="Acima do limite da categoria o sistema pede confirmação." error={e?.economies?.message}>
                {(aria) => <Input {...aria} {...register(`subcategories.${index}.economies`)} inputMode="numeric" />}
              </Field>
              <Field label="Unidades privativas" error={e?.privateUnits?.message}>
                {(aria) => <Input {...aria} {...register(`subcategories.${index}.privateUnits`)} inputMode="numeric" />}
              </Field>
              <Field label="Unidades coletivas" error={e?.collectiveUnits?.message}>
                {(aria) => <Input {...aria} {...register(`subcategories.${index}.collectiveUnits`)} inputMode="numeric" />}
              </Field>
            </div>
            <Controller
              control={control}
              name={`subcategories.${index}.activityBranchIds`}
              render={({ field }) => <MultiLookup label="Ramos de atividade" values={field.value} source={catalogSource('activity-branches')} onChange={field.onChange} />}
            />
            {rows.fields.length > 1 ? (
              <Button variant="outline" size="sm" onClick={() => rows.remove(index)}>
                <Trash2 className="h-4 w-4" aria-hidden="true" />
                Remover subcategoria {index + 1}
              </Button>
            ) : null}
          </fieldset>
        )
      })}
      <Button variant="outline" onClick={() => rows.append({ subcategoryId: null, economies: '1', privateUnits: '', collectiveUnits: '', activityBranchIds: [] })}>
        <Plus className="h-4 w-4" aria-hidden="true" />
        Adicionar subcategoria
      </Button>
    </div>
  )
}

// ---------------------------------------------------------------------------------------------
// Etapa 5: Características
// ---------------------------------------------------------------------------------------------

export function StepCharacteristics({ form, sewerRequiresDischarge }: Step5Props) {
  const { control, register, formState } = form
  const { errors } = formState
  const e = errors.physical

  const select = (name: 'waterStatusId' | 'sewerStatusId' | 'sewerDischargeId' | 'profileId' | 'waterSourceId' | 'wellTypeId' | 'pavementStreetId' | 'pavementSidewalkId', label: string, resource: string, extra?: { required?: boolean; hint?: string }) => (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <CatalogSelect
          label={label}
          resource={resource}
          required={extra?.required}
          hint={extra?.hint}
          value={field.value}
          error={errors[name]?.message}
          onChange={(v) => field.onChange(v)}
        />
      )}
    />
  )

  return (
    <div className="space-y-6">
      <div className="grid gap-4 md:grid-cols-2">
        <Field label="Área construída (m²)" required hint="A faixa de área é calculada pelo servidor." error={errors.builtAreaM2?.message}>
          {(aria) => <Input {...aria} {...register('builtAreaM2')} inputMode="decimal" />}
        </Field>
        {select('profileId', 'Perfil do imóvel', 'property-profiles', { required: true })}
        {select('waterStatusId', 'Situação da ligação de água', 'water-statuses', { required: true, hint: 'Precisa ser compatível com a rede de água da quadra (o servidor confere).' })}
        {select('sewerStatusId', 'Situação da ligação de esgoto', 'sewer-statuses', {
          required: true,
          hint: 'Precisa ser compatível com a rede de esgoto da quadra (o servidor confere).',
        })}
        {select('sewerDischargeId', 'Esgotamento', 'sewer-discharges', {
          required: sewerRequiresDischarge,
          hint: sewerRequiresDischarge ? 'Obrigatório porque esta situação de esgoto fatura.' : undefined,
        })}
        {select('waterSourceId', 'Fonte de abastecimento', 'water-sources')}
        {select('wellTypeId', 'Tipo de poço', 'well-types')}
        {select('pavementStreetId', 'Pavimento da rua', 'pavements')}
        {select('pavementSidewalkId', 'Pavimento da calçada', 'pavements')}
      </div>

      <details className="rounded-lg border p-3">
        <summary className="cursor-pointer text-sm font-medium">Características físicas (opcional)</summary>
        <div className="mt-4 grid gap-4 md:grid-cols-3">
          <div className="md:col-span-3">
            <Checkbox label="Possui jardim" {...register('physical.garden')} />
          </div>
          <Field label="Reservatório inferior (m³)" error={e?.lowerReservoirM3?.message}>
            {(aria) => <Input {...aria} {...register('physical.lowerReservoirM3')} inputMode="decimal" />}
          </Field>
          <Field label="Reservatório superior (m³)" error={e?.upperReservoirM3?.message}>
            {(aria) => <Input {...aria} {...register('physical.upperReservoirM3')} inputMode="decimal" />}
          </Field>
          <Field label="Piscina (m³)" error={e?.poolM3?.message}>
            {(aria) => <Input {...aria} {...register('physical.poolM3')} inputMode="decimal" />}
          </Field>
        </div>
      </details>
    </div>
  )
}

// ---------------------------------------------------------------------------------------------
// Etapa 6: Conclusão
// ---------------------------------------------------------------------------------------------

const unitSource = {
  path: consumerUnits.path,
  // A busca de imóvel aceita nome ou documento do titular: vai pelo corpo (POST), não pela URL.
  async list(params?: ListParams, signal?: AbortSignal): Promise<Page<LookupItem>> {
    const page = await consumerUnits.search(params, signal)
    return {
      ...page,
      content: page.content.map((u: ConsumerUnitSummary) => ({ id: u.id, code: formatMatricula(u.registration, u.registrationDv), name: u.addressText })),
    }
  },
}

export function StepConclusion({ form }: StepProps) {
  const { control, register, watch, formState } = form
  const { errors } = formState
  const notes = watch('notes')

  return (
    <div className="space-y-4">
      <div className="grid gap-4 md:grid-cols-3">
        <Field label="Pontos de utilização" error={errors.usagePoints?.message}>
          {(aria) => <Input {...aria} {...register('usagePoints')} inputMode="numeric" />}
        </Field>
        <Field label="Número de moradores" hint="Dado pessoal indireto: informe só se necessário para estimar consumo." error={errors.residents?.message}>
          {(aria) => <Input {...aria} {...register('residents')} inputMode="numeric" />}
        </Field>
        <Field label="Número do IPTU" hint="Único por município; o servidor confere." error={errors.iptu?.message}>
          {(aria) => <Input {...aria} {...register('iptu')} autoComplete="off" />}
        </Field>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        <Controller
          control={control}
          name="deliveryRouteId"
          render={({ field }) => (
            <LookupField label="Rota de entrega da conta" source={readingRoutes} value={field.value} error={errors.deliveryRouteId?.message} onChange={(v) => field.onChange(v)} hint="A rota precisa estar ativa e não ser alternativa." />
          )}
        />
        <Field label="Sequência de entrega" error={errors.deliverySequence?.message}>
          {(aria) => <Input {...aria} {...register('deliverySequence')} inputMode="numeric" />}
        </Field>
        <Controller
          control={control}
          name="altRouteId"
          render={({ field }) => <LookupField label="Rota alternativa" source={readingRoutes} value={field.value} error={errors.altRouteId?.message} onChange={(v) => field.onChange(v)} />}
        />
      </div>
      <Controller
        control={control}
        name="parentUnitId"
        render={({ field }) => (
          <LookupField label="Imóvel principal" source={unitSource} value={field.value} error={errors.parentUnitId?.message} onChange={(v) => field.onChange(v)} hint="Opcional. Precisa estar na mesma quadra." />
        )}
      />
      <Field label="Observações" hint={`${notes.length} de 750 caracteres`} error={errors.notes?.message}>
        {(aria) => <Textarea {...aria} {...register('notes')} maxLength={750} />}
      </Field>
    </div>
  )
}

// ---------------------------------------------------------------------------------------------
// Resumo (final)
// ---------------------------------------------------------------------------------------------

export function Summary({ values, isEdit }: { values: WizardValues; isEdit: boolean }) {
  const loc = values.localityId
  const sec = values.sectorId
  const blk = values.blockId
  const insc =
    loc?.code != null && sec?.code != null && blk?.code != null && values.lot !== '' && values.sublot !== ''
      ? { locality: Number(loc.code), sector: Number(sec.code), block: Number(blk.code), lot: Number(values.lot), sublot: Number(values.sublot) }
      : null
  const a = values.address
  const rows: Array<[string, string]> = [
    ['Inscrição', insc && isValidInscription(insc) ? formatInscription(insc) : '—'],
    ['Matrícula', isEdit ? 'mantida' : 'será atribuída ao gravar'],
    ['Endereço', [a.streetName, a.number].filter(Boolean).join(', ') || '—'],
    ['Clientes', String(values.customers.filter((c) => c.customerId).length)],
    ['Subcategorias', String(values.subcategories.filter((s) => s.subcategoryId).length)],
    ['Situação de água', values.waterStatusId?.label ?? '—'],
    ['Situação de esgoto', values.sewerStatusId?.label ?? '—'],
  ]
  return (
    <section aria-labelledby="resumo-titulo" className="rounded-lg border bg-muted/40 p-4">
      <h3 id="resumo-titulo" className="mb-3 text-sm font-semibold">
        Resumo antes de gravar
      </h3>
      <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
        {rows.map(([k, v]) => (
          <div key={k} className="flex gap-2">
            <dt className="text-muted-foreground">{k}:</dt>
            <dd className="font-medium">{v}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
