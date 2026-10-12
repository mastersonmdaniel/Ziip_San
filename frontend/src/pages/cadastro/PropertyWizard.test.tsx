import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type AxiosRequestConfig } from 'axios'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))
vi.mock('@services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@services/api')>()
  return { ...actual, api: { request: requestMock } }
})

import { fakeApi, renderWithProviders } from '../../test/render'
import { page } from '../../test/fixtures'
import { PropertyWizard } from './PropertyWizard'

const locality = { id: 'loc-1', version: 1, code: 1, name: 'TESTE Localidade' }
const sector = { id: 'sec-1', version: 1, code: 2, name: 'TESTE Setor' }
const block = { id: 'blk-1', version: 1, code: 3, name: null }

function problem(status: number, body: unknown): AxiosError {
  return new AxiosError('x', String(status), undefined, undefined, {
    data: body,
    status,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
  })
}

function setup(validateStep: (config: AxiosRequestConfig) => unknown) {
  const impl = fakeApi({
    'GET /registry/settings': { useBlockFace: false, requireReadingSequence: false },
    'GET /registry/localities': page([locality]),
    'GET /registry/commercial-sectors': page([sector]),
    'GET /registry/blocks': page([block]),
    'POST /registry/consumer-units/validate-step': validateStep,
    'POST /registry/consumer-units/drafts': { id: 'draft-1', version: 0, step: 1, targetId: null, data: {}, updatedAt: '2024-01-01T00:00:00' },
  })
  requestMock.mockImplementation((config: AxiosRequestConfig) => impl(config))
  return impl
}

async function pick(user: ReturnType<typeof userEvent.setup>, label: string, optionText: string) {
  const field = screen.getByRole('combobox', { name: new RegExp(label) })
  await user.click(field)
  await user.click(await screen.findByRole('option', { name: new RegExp(optionText) }))
}

async function fillStepOne(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('heading', { name: /Etapa 1 de 6: Localização/ })
  await pick(user, 'Localidade', 'TESTE Localidade')
  await pick(user, 'Setor comercial', 'TESTE Setor')
  await pick(user, 'Quadra', '3')
  await user.type(screen.getByLabelText(/^Lote/), '0004')
  await user.type(screen.getByLabelText(/^Sublote/), '5')
}

function renderWizard() {
  return renderWithProviders(
    <Routes>
      <Route path="/cadastro/imoveis/novo" element={<PropertyWizard />} />
    </Routes>,
    '/cadastro/imoveis/novo',
  )
}

beforeEach(() => {
  requestMock.mockReset()
})

describe('assistente de imóvel: servidor valida cada etapa e guarda o rascunho', () => {
  it('etapa 1 válida: chama validate-step, grava o rascunho no servidor e avança', async () => {
    const user = userEvent.setup()
    const api = setup(() => ({}))
    renderWizard()
    await fillStepOne(user)
    expect(screen.getByText('001.002.003.0004.005')).toBeInTheDocument() // inscrição resultante, zeros só na exibição

    await user.click(screen.getByRole('button', { name: 'Próximo' }))
    expect(await screen.findByRole('heading', { name: /Etapa 2 de 6: Endereço/ })).toBeInTheDocument()

    const validate = api.mock.calls.find((c) => c[0].url === '/registry/consumer-units/validate-step')?.[0]
    expect(validate?.data).toMatchObject({
      step: 0,
      targetId: null,
      data: { localityId: 'loc-1', sectorId: 'sec-1', blockId: 'blk-1', lot: 4, sublot: 5 },
    })
    const draft = api.mock.calls.find((c) => c[0].url === '/registry/consumer-units/drafts')?.[0]
    expect(draft?.method).toBe('POST')
    expect(draft?.data).toMatchObject({ step: 1, targetId: null })
    expect(await screen.findByText(/Rascunho salvo às/)).toBeInTheDocument()
  })

  it('aviso confirmável do servidor: pede confirmação e reenvia com o confirmToken', async () => {
    const user = userEvent.setup()
    let calls = 0
    const api = setup(() => {
      calls += 1
      if (calls === 1) {
        throw problem(422, {
          warnings: [{ code: 'READING_SEQUENCE_TAKEN', message: 'A sequência 12 já é usada por outro imóvel da rota.', confirmToken: 'tok-seq' }],
        })
      }
      return {}
    })
    renderWizard()
    await fillStepOne(user)
    await user.click(screen.getByRole('button', { name: 'Próximo' }))

    const alert = await screen.findByRole('alertdialog')
    expect(within(alert).getByText(/A sequência 12 já é usada/)).toBeInTheDocument()
    // Não avançou nem gravou rascunho antes de confirmar.
    expect(screen.getByRole('heading', { name: /Etapa 1 de 6/ })).toBeInTheDocument()
    expect(api.mock.calls.some((c) => c[0].url === '/registry/consumer-units/drafts')).toBe(false)

    await user.click(within(alert).getByRole('button', { name: 'Confirmar e continuar' }))
    expect(await screen.findByRole('heading', { name: /Etapa 2 de 6: Endereço/ })).toBeInTheDocument()
    const validateCalls = api.mock.calls.filter((c) => c[0].url === '/registry/consumer-units/validate-step')
    expect(validateCalls).toHaveLength(2)
    expect(validateCalls[1]?.[0].data).toMatchObject({ confirmTokens: ['tok-seq'] })
  })

  it('erro bloqueante do servidor (inscrição duplicada) aparece no campo e a etapa não avança', async () => {
    const user = userEvent.setup()
    setup(() => {
      throw problem(422, {
        errors: [{ field: 'sublot', code: 'INSCRIPTION_DUPLICATE', message: 'Inscrição já usada pelo imóvel 7654321-0.' }],
      })
    })
    renderWizard()
    await fillStepOne(user)
    await user.click(screen.getByRole('button', { name: 'Próximo' }))
    const message = await screen.findByText('Inscrição já usada pelo imóvel 7654321-0.')
    const sublot = screen.getByLabelText(/^Sublote/)
    expect(sublot).toHaveAttribute('aria-invalid', 'true')
    expect(sublot.getAttribute('aria-describedby')).toContain(message.closest('p')?.id ?? 'x')
    expect(screen.getByRole('heading', { name: /Etapa 1 de 6/ })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/O servidor recusou 1 campo/))
  })
})
