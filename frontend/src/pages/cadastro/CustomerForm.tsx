import { useCallback, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { Controller, useFieldArray, useForm, type FieldPath } from 'react-hook-form'
import { Icon } from '@components/ui/Icon'
import { AddressFields } from '@components/registry/AddressFields'
import { CatalogSelect } from '@components/registry/CatalogSelect'
import { ConfirmWarning, useConfirmable } from '@components/registry/ConfirmWarning'
import { ConflictAlert } from '@components/registry/ConflictAlert'
import { CpfCnpjInput } from '@components/registry/CpfCnpjInput'
import { LookupField } from '@components/registry/LookupField'
import { PhoneInput } from '@components/registry/PhoneInput'
import { Alert, Spinner } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Card, CardContent, CardHeader, CardTitle } from '@components/ui/Card'
import { Checkbox, Field, Input, Select, Textarea } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { ApiError, errorMessage, toFormPath } from '@services/registry/errors'
import { customers } from '@services/registry/resources'
import type { CustomerInput } from '@services/registry/types'
import { PHONE_TYPE_LABEL } from '@utils/labels'
import {
  buildCustomerSchema,
  customerToForm,
  emptyCustomerForm,
  EMPTY_PHONE,
  formToCustomerInput,
  type CustomerFormValues,
} from './customerSchema'
import { EMPTY_ADDRESS } from '@components/registry/addressForm'

interface FormProps {
  customerId: string | null
  initial: CustomerFormValues
  initialVersion: number | null
  /** Documento mascarado atual (edição), para dica e para saber se "em branco" mantém. */
  currentDocumentMasked: string | null
}

function CustomerFormInner({ customerId, initial, initialVersion, currentDocumentMasked }: FormProps) {
  const navigate = useNavigate()
  const keepsExistingDocument = currentDocumentMasked !== null
  const schema = useMemo(() => buildCustomerSchema({ keepsExistingDocument }), [keepsExistingDocument])
  const form = useForm<CustomerFormValues>({ defaultValues: initial, resolver: zodResolver(schema), mode: 'onBlur' })
  const { control, register, handleSubmit, setError, reset, watch, setValue, formState } = form
  const { errors, isSubmitting } = formState
  const [version, setVersion] = useState<number | null>(initialVersion)
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const [reloading, setReloading] = useState(false)

  const emails = useFieldArray({ control, name: 'emails' })
  const phones = useFieldArray({ control, name: 'phones' })
  const addresses = useFieldArray({ control, name: 'addresses' })

  const personType = watch('personType')
  const documentValue = watch('document')
  const kind = personType === 'PF' ? 'CPF' : 'CNPJ'
  const pf = personType === 'PF'

  const submit = useCallback(
    async ({ input }: { input: CustomerInput }, confirmTokens: string[]) => {
      const saved =
        customerId && version !== null
          ? await customers.update(customerId, input, version, { confirmTokens })
          : await customers.create(input, { confirmTokens })
      navigate(`/cadastro/clientes/${saved.id}`)
    },
    [customerId, version, navigate],
  )

  const onError = useCallback(
    (error: ApiError) => {
      if (error.kind === 'conflict') {
        setConflict(true)
        setFormError(null)
        return
      }
      let mapped = 0
      for (const e of error.errors) {
        if (e.field) {
          setError(toFormPath(e.field) as FieldPath<CustomerFormValues>, { type: e.code, message: e.message })
          mapped += 1
        }
      }
      setFormError(mapped > 0 ? `O servidor recusou ${mapped} ${mapped === 1 ? 'campo' : 'campos'}. Corrija e tente de novo.` : error.message)
    },
    [setError],
  )

  const { run, confirm, cancel, warnings, busy } = useConfirmable<{ input: CustomerInput }>(submit, onError)

  const onValid = (values: CustomerFormValues) => {
    setFormError(null)
    void run({ input: formToCustomerInput(values) })
  }

  const onInvalid = () => setFormError('Há campos com problema. Corrija os campos indicados e envie de novo.')

  const reloadServer = async (keepMine: boolean) => {
    if (!customerId) return
    setReloading(true)
    try {
      const fresh = await customers.get(customerId)
      setVersion(fresh.version)
      if (!keepMine) reset(customerToForm(fresh))
      setConflict(false)
      setFormError(null)
    } catch (e) {
      setFormError(errorMessage(e))
    } finally {
      setReloading(false)
    }
  }

  const setPrimaryPhone = (index: number) => phones.fields.forEach((_, i) => setValue(`phones.${i}.primary`, i === index, { shouldDirty: true }))
  const setCorrespondence = (index: number) =>
    addresses.fields.forEach((_, i) => setValue(`addresses.${i}.correspondence`, i === index, { shouldDirty: true }))

  const title = customerId ? 'Editar cliente' : 'Cadastrar cliente'

  return (
    <div className="space-y-6">
      <PageHeader title={title} description="Campos marcados com * são obrigatórios. O documento é validado pelos dígitos verificadores." />

      <form onSubmit={(e) => void handleSubmit(onValid, onInvalid)(e)} noValidate className="space-y-6">
        {conflict ? <ConflictAlert onKeepMine={() => void reloadServer(true)} onDiscardMine={() => void reloadServer(false)} busy={reloading} /> : null}
        {formError ? <Alert tone="error">{formError}</Alert> : null}
        {warnings ? <ConfirmWarning warnings={warnings} onConfirm={() => void confirm()} onCancel={cancel} busy={busy} /> : null}

        <Card>
          <CardHeader>
            <CardTitle as="h2">Identificação</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <fieldset>
              <legend className="mb-2 text-sm font-medium">Tipo de pessoa *</legend>
              <div className="flex gap-6">
                <label className="flex items-center gap-2 text-sm">
                  <input type="radio" value="PF" {...register('personType')} className="h-4 w-4 accent-[hsl(var(--primary))]" />
                  Pessoa física
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input type="radio" value="PJ" {...register('personType')} className="h-4 w-4 accent-[hsl(var(--primary))]" />
                  Pessoa jurídica
                </label>
              </div>
            </fieldset>

            <div className="grid gap-4 md:grid-cols-2">
              <Field label={pf ? 'Nome completo' : 'Razão social'} required error={errors.name?.message}>
                {(aria) => <Input {...aria} {...register('name')} maxLength={200} autoComplete="off" />}
              </Field>
              {!pf ? (
                <Field label="Nome fantasia" error={errors.tradeName?.message}>
                  {(aria) => <Input {...aria} {...register('tradeName')} maxLength={200} autoComplete="off" />}
                </Field>
              ) : null}
              <Controller
                control={control}
                name="customerTypeId"
                render={({ field }) => (
                  <CatalogSelect
                    label="Tipo de cliente"
                    resource="customer-types"
                    required
                    value={field.value}
                    onChange={(v) => field.onChange(v)}
                    error={errors.customerTypeId?.message}
                  />
                )}
              />
              <Controller
                control={control}
                name="document"
                render={({ field }) => (
                  <CpfCnpjInput
                    label={pf ? 'CPF' : 'CNPJ'}
                    kind={kind}
                    required={!keepsExistingDocument}
                    value={field.value}
                    onChange={field.onChange}
                    onBlur={field.onBlur}
                    error={errors.document?.message}
                    hint={
                      currentDocumentMasked
                        ? `Documento atual: ${currentDocumentMasked}. Deixe em branco para manter.`
                        : pf
                          ? undefined
                          : 'O CNPJ pode ter letras e números (formato alfanumérico).'
                    }
                  />
                )}
              />
            </div>

            {documentValue === '' && !keepsExistingDocument ? (
              <Field
                label="Motivo para cadastrar sem documento"
                hint="Só é aceito com permissão especial. Descreva o motivo (mínimo de 5 caracteres)."
                error={errors.documentExemptionReason?.message}
              >
                {(aria) => <Textarea {...aria} {...register('documentExemptionReason')} maxLength={300} />}
              </Field>
            ) : null}

            {pf ? (
              <div className="grid gap-4 md:grid-cols-2">
                <Field label="Data de nascimento" hint="Opcional. Obrigatória só para tarifa social." error={errors.birthDate?.message}>
                  {(aria) => <Input {...aria} type="date" {...register('birthDate')} />}
                </Field>
              </div>
            ) : (
              <div className="grid gap-4 md:grid-cols-2">
                <Controller
                  control={control}
                  name="activityBranchId"
                  render={({ field }) => (
                    <CatalogSelect label="Ramo de atividade" resource="activity-branches" value={field.value} onChange={(v) => field.onChange(v)} error={errors.activityBranchId?.message} />
                  )}
                />
                <Controller
                  control={control}
                  name="parentCustomerId"
                  render={({ field }) => (
                    <LookupField
                      label="Cliente responsável superior"
                      source={customers}
                      value={field.value}
                      onChange={(v) => field.onChange(v)}
                      hint="Opcional: hierarquia entre clientes."
                      error={errors.parentCustomerId?.message}
                      getLabel={(c) => c.name}
                    />
                  )}
                />
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle as="h2">Endereços</CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            {addresses.fields.map((item, index) => {
              const e = errors.addresses?.[index]
              return (
                <fieldset key={item.id} className="space-y-4 rounded-lg border p-4">
                  <legend className="px-1 text-sm font-semibold">Endereço {index + 1}</legend>
                  <Controller
                    control={control}
                    name={`addresses.${index}.address`}
                    render={({ field }) => (
                      <AddressFields
                        value={field.value}
                        onChange={field.onChange}
                        legendSuffix={`endereço ${index + 1}`}
                        errors={{
                          cep: e?.address?.cep?.message,
                          streetName: e?.address?.streetName?.message,
                          number: e?.address?.number?.message,
                          complement: e?.address?.complement?.message,
                          neighborhood: e?.address?.neighborhood?.message,
                          city: e?.address?.city?.message,
                          state: e?.address?.state?.message,
                        }}
                      />
                    )}
                  />
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <label className="flex items-center gap-2 text-sm font-medium">
                        <input
                          type="radio"
                          name="correspondence"
                          checked={watch(`addresses.${index}.correspondence`)}
                          onChange={() => setCorrespondence(index)}
                          className="h-4 w-4 accent-[hsl(var(--primary))]"
                        />
                        Endereço de correspondência
                      </label>
                      {e?.correspondence?.message ? <p className="mt-1 text-sm text-destructive">{e.correspondence.message}</p> : null}
                    </div>
                    {addresses.fields.length > 1 ? (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          const wasCorrespondence = watch(`addresses.${index}.correspondence`)
                          addresses.remove(index)
                          if (wasCorrespondence) setTimeout(() => setValue('addresses.0.correspondence', true), 0)
                        }}
                      >
                        <Icon name="trash-2" className="h-4 w-4" />
                        Remover endereço {index + 1}
                      </Button>
                    ) : null}
                  </div>
                </fieldset>
              )
            })}
            <Button variant="outline" onClick={() => addresses.append({ address: EMPTY_ADDRESS, correspondence: false })}>
              <Icon name="plus" className="h-4 w-4" />
              Adicionar outro endereço
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle as="h2">Contatos</CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            <section aria-labelledby="emails-titulo" className="space-y-3">
              <h3 id="emails-titulo" className="text-sm font-semibold">
                E-mails
              </h3>
              {emails.fields.length === 0 ? <p className="text-sm text-muted-foreground">Nenhum e-mail informado.</p> : null}
              {emails.fields.map((item, index) => (
                <div key={item.id} className="flex flex-wrap items-end gap-3">
                  <div className="min-w-[16rem] flex-1">
                    <Field label={`E-mail ${index + 1}`} error={errors.emails?.[index]?.address?.message}>
                      {(aria) => <Input {...aria} type="email" {...register(`emails.${index}.address`)} autoComplete="off" />}
                    </Field>
                  </div>
                  <Button variant="outline" size="sm" onClick={() => emails.remove(index)}>
                    <Icon name="trash-2" className="h-4 w-4" />
                    Remover e-mail {index + 1}
                  </Button>
                </div>
              ))}
              <Button variant="outline" size="sm" onClick={() => emails.append({ address: '', consentId: null })}>
                <Icon name="plus" className="h-4 w-4" />
                Adicionar e-mail
              </Button>
              <p className="text-xs text-muted-foreground">
                O consentimento para fatura digital é registrado pelo módulo LGPD, que ainda não tem tela; a tela preserva o vínculo existente.
              </p>
            </section>

            <section aria-labelledby="fones-titulo" className="space-y-3">
              <h3 id="fones-titulo" className="text-sm font-semibold">
                Telefones
              </h3>
              {phones.fields.length === 0 ? <p className="text-sm text-muted-foreground">Nenhum telefone informado.</p> : null}
              {phones.fields.map((item, index) => {
                const e = errors.phones?.[index]
                return (
                  <fieldset key={item.id} className="grid gap-3 rounded-lg border p-3 md:grid-cols-4">
                    <legend className="px-1 text-sm font-medium">Telefone {index + 1}</legend>
                    <Field label="Tipo" error={e?.type?.message}>
                      {(aria) => (
                        <Select {...aria} {...register(`phones.${index}.type`)}>
                          {Object.entries(PHONE_TYPE_LABEL).map(([value, label]) => (
                            <option key={value} value={value}>
                              {label}
                            </option>
                          ))}
                        </Select>
                      )}
                    </Field>
                    <Controller
                      control={control}
                      name={`phones.${index}.number`}
                      render={({ field }) => <PhoneInput label="Número" value={field.value} onChange={field.onChange} onBlur={field.onBlur} error={e?.number?.message} />}
                    />
                    <Field label="Ramal" error={e?.extension?.message}>
                      {(aria) => <Input {...aria} {...register(`phones.${index}.extension`)} inputMode="numeric" maxLength={10} />}
                    </Field>
                    <Field label="Nome do contato" hint="Dado de terceiro: só se necessário." error={e?.contactName?.message}>
                      {(aria) => <Input {...aria} {...register(`phones.${index}.contactName`)} maxLength={100} autoComplete="off" />}
                    </Field>
                    <div className="md:col-span-4 flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <label className="flex items-center gap-2 text-sm font-medium">
                          <input
                            type="radio"
                            name="primary-phone"
                            checked={watch(`phones.${index}.primary`)}
                            onChange={() => setPrimaryPhone(index)}
                            className="h-4 w-4 accent-[hsl(var(--primary))]"
                          />
                          Telefone principal
                        </label>
                        {e?.primary?.message ? <p className="mt-1 text-sm text-destructive">{e.primary.message}</p> : null}
                      </div>
                      <Button variant="outline" size="sm" onClick={() => phones.remove(index)}>
                        <Icon name="trash-2" className="h-4 w-4" />
                        Remover telefone {index + 1}
                      </Button>
                    </div>
                  </fieldset>
                )
              })}
              <Button variant="outline" size="sm" onClick={() => phones.append({ ...EMPTY_PHONE, primary: phones.fields.length === 0 })}>
                <Icon name="plus" className="h-4 w-4" />
                Adicionar telefone
              </Button>
            </section>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle as="h2">Cobrança</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 md:grid-cols-2">
            <Field label="Dia de vencimento da conta" hint="De 1 a 31. O vencimento por imóvel prevalece." error={errors.billing?.dueDay?.message}>
              {(aria) => <Input {...aria} {...register('billing.dueDay')} inputMode="numeric" maxLength={2} />}
            </Field>
            <div className="space-y-3">
              <Checkbox label="Vencimento no mês seguinte" {...register('billing.nextMonthDue')} />
              <Checkbox label="Permite fatura antecipada" {...register('billing.advanceInvoice')} />
              <Checkbox label="Bloquear negativação" hint="Só é aceito com permissão especial; o servidor decide." {...register('billing.blockNegativation')} />
            </div>
          </CardContent>
        </Card>

        <div className="flex flex-wrap justify-end gap-3">
          <Link to={customerId ? `/cadastro/clientes/${customerId}` : '/cadastro/clientes'} className="inline-flex h-10 items-center rounded-lg border bg-background px-4 text-sm font-medium hover:bg-accent">
            Cancelar
          </Link>
          <Button type="submit" loading={isSubmitting || busy} disabled={warnings !== null || conflict}>
            Salvar cliente
          </Button>
        </div>
      </form>
    </div>
  )
}

/** S4: formulário de cliente. Rotas: /cadastro/clientes/novo e /cadastro/clientes/:id/editar */
export function CustomerForm() {
  const { id } = useParams()
  const query = useQuery({
    queryKey: ['customer', id],
    queryFn: ({ signal }) => customers.get(id ?? '', signal),
    enabled: Boolean(id),
    staleTime: 0,
  })

  if (id) {
    if (query.isLoading) return <Spinner label="Carregando cliente..." />
    if (query.isError || !query.data) {
      return (
        <Alert tone="error" title="Não foi possível carregar o cliente">
          {errorMessage(query.error)}
        </Alert>
      )
    }
    const c = query.data
    return (
      <CustomerFormInner
        key={c.id}
        customerId={c.id}
        initial={customerToForm(c)}
        initialVersion={c.version}
        currentDocumentMasked={c.documentMasked}
      />
    )
  }
  return <CustomerFormInner customerId={null} initial={emptyCustomerForm()} initialVersion={null} currentDocumentMasked={null} />
}
