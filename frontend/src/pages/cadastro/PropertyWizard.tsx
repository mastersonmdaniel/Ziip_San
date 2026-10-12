import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useForm, type FieldPath } from 'react-hook-form'
import { useCatalogItems } from '@components/registry/CatalogSelect'
import { ConfirmWarning, useConfirmable } from '@components/registry/ConfirmWarning'
import { ConflictAlert } from '@components/registry/ConflictAlert'
import { Wizard } from '@components/registry/Wizard'
import { Alert, Spinner } from '@components/ui/Alert'
import { PageHeader } from '@components/ui/PageHeader'
import { useRegistrySettings } from '@hooks/useRegistrySettings'
import { ApiError, errorMessage, toApiError } from '@services/registry/errors'
import { consumerUnitDrafts, consumerUnits } from '@services/registry/resources'
import type { Draft } from '@services/registry/types'
import {
  apiPathToFormPath,
  LAST_STEP,
  restoreWizardValues,
  STEP_LABELS,
  stepOfField,
  stepPayload,
  stepSchema,
  stepSlice,
  toConsumerUnitInput,
  unitToWizardValues,
  type StepContext,
  type WizardValues,
} from './propertyForm'
import { StepAddress, StepCategories, StepCharacteristics, StepConclusion, StepCustomers, StepLocation, Summary } from './PropertyWizardSteps'

interface InnerProps {
  unitId: string | null
  initialVersion: number | null
  initialValues: WizardValues
  draft: Draft<unknown> | null
}

function nowLabel(): string {
  return new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

function WizardInner({ unitId, initialVersion, initialValues, draft }: InnerProps) {
  const navigate = useNavigate()
  const { settings } = useRegistrySettings()
  const form = useForm<WizardValues>({ defaultValues: initialValues })
  const [step, setStep] = useState(Math.min(draft?.step ?? 0, LAST_STEP))
  const [maxReached, setMaxReached] = useState(Math.min(draft?.step ?? 0, LAST_STEP))
  const [version, setVersion] = useState<number | null>(initialVersion)
  const [draftRef, setDraftRef] = useState<{ id: string; version: number } | null>(draft ? { id: draft.id, version: draft.version } : null)
  const [draftStatus, setDraftStatus] = useState<string | null>(draft ? 'Rascunho retomado.' : null)
  const [savingDraft, setSavingDraft] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const [reloading, setReloading] = useState(false)
  const noticeRef = useRef<HTMLDivElement>(null)

  const sewerStatus = form.watch('sewerStatusId')
  const sewerItems = useCatalogItems('sewer-statuses')
  const sewerRequiresDischarge = sewerItems.data?.content.find((i) => i.id === sewerStatus?.id)?.billable === true

  const ctx: StepContext = useMemo(
    () => ({
      useBlockFace: settings.useBlockFace,
      requireReadingSequence: settings.requireReadingSequence,
      sewerRequiresDischarge,
    }),
    [settings.useBlockFace, settings.requireReadingSequence, sewerRequiresDischarge],
  )

  // Leva o foco ao resumo de erros para quem usa leitor de tela ou teclado.
  useEffect(() => {
    if (formError) noticeRef.current?.focus()
  }, [formError])

  const persistDraft = useCallback(
    async (targetStep: number, values: WizardValues): Promise<void> => {
      const input = { step: targetStep, targetId: unitId, data: values }
      const save = async (): Promise<{ id: string; version: number }> => {
        if (draftRef) return consumerUnitDrafts.update(draftRef.id, draftRef.version, input)
        return consumerUnitDrafts.create(input)
      }
      try {
        let saved: { id: string; version: number }
        try {
          saved = await save()
        } catch (e) {
          // Rascunho alterado em outra aba: pega a versão atual e tenta uma vez mais.
          if (draftRef && toApiError(e).kind === 'conflict') {
            const fresh = await consumerUnitDrafts.get(draftRef.id)
            saved = await consumerUnitDrafts.update(fresh.id, fresh.version, input)
          } else {
            throw e
          }
        }
        if (!draftRef) {
          const url = new URL(window.location.href)
          url.searchParams.set('rascunho', saved.id)
          window.history.replaceState(window.history.state, '', url)
        }
        setDraftRef({ id: saved.id, version: saved.version })
        setDraftStatus(`Rascunho salvo às ${nowLabel()}.`)
      } catch (e) {
        setDraftStatus(`Não foi possível salvar o rascunho: ${errorMessage(e)}`)
        throw e
      }
    },
    [draftRef, unitId],
  )

  const submit = useCallback(
    async ({ step: s }: { step: number }, confirmTokens: string[]) => {
      const values = form.getValues()
      // 1) Validação da etapa no servidor (sem gravar).
      await consumerUnitDrafts.validateStep(s, stepPayload(s, values), unitId, { confirmTokens })
      if (s === LAST_STEP) {
        // 2) Última etapa: grava o imóvel e descarta o rascunho (o servidor o remove pelo draftId).
        const input = { ...toConsumerUnitInput(values), draftId: draftRef?.id ?? null }
        const saved =
          unitId && version !== null
            ? await consumerUnits.update(unitId, input, version, { confirmTokens })
            : await consumerUnits.create(input, { confirmTokens })
        navigate(`/cadastro/imoveis/${saved.id}`, { replace: true })
        return
      }
      // 2) Etapa intermediária: guarda o rascunho no servidor e avança. Falha ao salvar o rascunho não trava o avanço.
      try {
        await persistDraft(s + 1, values)
      } catch {
        // o status já informa a falha
      }
      setStep(s + 1)
      setMaxReached((m) => Math.max(m, s + 1))
    },
    [form, unitId, draftRef, version, navigate, persistDraft],
  )

  const onError = useCallback(
    (error: ApiError) => {
      if (error.kind === 'conflict') {
        setConflict(true)
        setFormError(null)
        return
      }
      let first: number | null = null
      let mapped = 0
      for (const e of error.errors) {
        if (!e.field) continue
        form.setError(apiPathToFormPath(e.field) as FieldPath<WizardValues>, { type: e.code, message: e.message })
        mapped += 1
        const s = stepOfField(e.field)
        if (s !== null && (first === null || s < first)) first = s
      }
      if (first !== null) {
        setStep(first)
        setMaxReached((m) => Math.max(m, first))
      }
      setFormError(
        mapped > 0
          ? `O servidor recusou ${mapped} ${mapped === 1 ? 'campo' : 'campos'}${first !== null ? ` (etapa ${first + 1}: ${STEP_LABELS[first]})` : ''}. Corrija e tente de novo.`
          : error.message,
      )
    },
    [form],
  )

  const { run, confirm, cancel, warnings, busy } = useConfirmable<{ step: number }>(submit, onError)

  const handleNext = () => {
    setFormError(null)
    form.clearErrors()
    const values = form.getValues()
    const result = stepSchema(step, ctx).safeParse(stepSlice(step, values))
    if (!result.success) {
      const seen = new Set<string>()
      for (const issue of result.error.issues) {
        const path = issue.path.join('.')
        if (seen.has(path)) continue
        seen.add(path)
        form.setError(path as FieldPath<WizardValues>, { type: 'validate', message: issue.message })
      }
      setFormError(`Há ${seen.size} ${seen.size === 1 ? 'campo' : 'campos'} com problema nesta etapa. Corrija os campos indicados.`)
      return
    }
    void run({ step })
  }

  const handleSaveDraft = async () => {
    setSavingDraft(true)
    try {
      await persistDraft(step, form.getValues())
    } catch {
      // status já atualizado
    } finally {
      setSavingDraft(false)
    }
  }

  const reloadServer = async (keepMine: boolean) => {
    if (!unitId) return
    setReloading(true)
    try {
      const [fresh, links] = await Promise.all([consumerUnits.get(unitId), consumerUnits.customers(unitId)])
      setVersion(fresh.version)
      if (!keepMine) form.reset(unitToWizardValues(fresh, links))
      setConflict(false)
      setFormError(null)
    } catch (e) {
      setFormError(errorMessage(e))
    } finally {
      setReloading(false)
    }
  }

  const notices = (
    <>
      {conflict ? <ConflictAlert onKeepMine={() => void reloadServer(true)} onDiscardMine={() => void reloadServer(false)} busy={reloading} /> : null}
      {formError ? (
        <div ref={noticeRef} tabIndex={-1} className="focus:outline-none">
          <Alert tone="error">{formError}</Alert>
        </div>
      ) : null}
      {warnings ? <ConfirmWarning warnings={warnings} onConfirm={() => void confirm()} onCancel={cancel} busy={busy} /> : null}
    </>
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title={unitId ? 'Editar imóvel' : 'Cadastrar imóvel'}
        description="Seis etapas. Cada etapa é validada pelo servidor ao avançar, e o rascunho fica salvo no servidor."
      />
      <Wizard
        steps={STEP_LABELS.map((label) => ({ id: label, label }))}
        current={step}
        maxReached={maxReached}
        onGoTo={(i) => {
          setFormError(null)
          setStep(i)
        }}
        onBack={() => {
          setFormError(null)
          setStep((s) => Math.max(0, s - 1))
        }}
        onNext={handleNext}
        onSaveDraft={() => void handleSaveDraft()}
        busy={busy}
        savingDraft={savingDraft}
        finishLabel={unitId ? 'Gravar alterações' : 'Gravar imóvel'}
        draftStatus={draftStatus}
        notices={notices}
      >
        {step === 0 ? <StepLocation form={form} useBlockFace={settings.useBlockFace} requireReadingSequence={settings.requireReadingSequence} /> : null}
        {step === 1 ? <StepAddress form={form} /> : null}
        {step === 2 ? <StepCustomers form={form} /> : null}
        {step === 3 ? <StepCategories form={form} /> : null}
        {step === 4 ? <StepCharacteristics form={form} sewerRequiresDischarge={sewerRequiresDischarge} /> : null}
        {step === 5 ? (
          <div className="space-y-6">
            <StepConclusion form={form} />
            <Summary values={form.watch()} isEdit={unitId !== null} />
          </div>
        ) : null}
      </Wizard>
    </div>
  )
}

/** S2: assistente de imóvel (6 etapas). Rotas: /cadastro/imoveis/novo e /cadastro/imoveis/:id/editar */
export function PropertyWizard() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const draftId = params.get('rascunho')

  const unit = useQuery({
    queryKey: ['consumer-unit', id, 'wizard'],
    queryFn: async ({ signal }) => {
      const [u, links] = await Promise.all([consumerUnits.get(id ?? '', signal), consumerUnits.customers(id ?? '', signal)])
      return { unit: u, links }
    },
    enabled: Boolean(id),
    staleTime: 0,
  })
  const draft = useQuery({
    queryKey: ['consumer-unit-draft', draftId],
    queryFn: () => consumerUnitDrafts.get(draftId ?? ''),
    enabled: Boolean(draftId),
    staleTime: 0,
  })

  if ((id && unit.isLoading) || (draftId && draft.isLoading)) return <Spinner label="Carregando..." />
  if (id && (unit.isError || !unit.data)) {
    return (
      <Alert tone="error" title="Não foi possível carregar o imóvel">
        {errorMessage(unit.error)}
      </Alert>
    )
  }
  if (draftId && (draft.isError || !draft.data)) {
    return (
      <Alert tone="error" title="Não foi possível retomar o rascunho">
        {errorMessage(draft.error)}
      </Alert>
    )
  }

  const draftData = draftId && draft.data ? draft.data : null
  const initialValues = draftData
    ? restoreWizardValues(draftData.data)
    : unit.data
      ? unitToWizardValues(unit.data.unit, unit.data.links)
      : restoreWizardValues(null)

  return (
    <WizardInner
      key={`${id ?? 'novo'}-${draftId ?? ''}`}
      unitId={id ?? null}
      initialVersion={unit.data ? unit.data.unit.version : null}
      initialValues={initialValues}
      draft={draftData}
    />
  )
}
