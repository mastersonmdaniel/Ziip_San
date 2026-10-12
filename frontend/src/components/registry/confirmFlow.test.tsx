import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@services/registry/errors'
import type { ResourceClient } from '@services/registry/client'
import type { CrudItem } from '@services/registry/types'
import { renderWithProviders } from '../../test/render'
import { CatalogCrud } from './CatalogCrud'
import type { CrudMeta } from './crudMeta'

type Client = ResourceClient<CrudItem, CrudItem, Record<string, unknown>>

const EXISTING: CrudItem = { id: 'item-1', version: 7, name: 'TESTE Item', economiesSoftLimit: 10 }

function makeClient(overrides: Partial<Client> = {}): Client {
  return {
    path: 'tests',
    list: vi.fn().mockResolvedValue({ content: [EXISTING], page: 0, size: 25, totalElements: 1, totalPages: 1 }),
    get: vi.fn().mockResolvedValue({ ...EXISTING, version: 8 }),
    create: vi.fn().mockResolvedValue({ ...EXISTING, id: 'novo', version: 0 }),
    update: vi.fn().mockResolvedValue({ ...EXISTING, version: 8 }),
    remove: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  }
}

function metaFor(client: Client): CrudMeta {
  return {
    key: 'tests',
    title: 'Itens de teste',
    singular: 'item',
    defaultSort: 'name,asc',
    client,
    fields: [
      { kind: 'text', name: 'name', label: 'Descrição', required: true, maxLength: 40, column: true },
      { kind: 'integer', name: 'economiesSoftLimit', label: 'Limite de economias', min: 1, column: true },
    ],
  }
}

const softLimitWarning = new ApiError(
  'warning',
  422,
  { warnings: [{ code: 'SOFT_LIMIT', message: 'A quantidade de economias está acima do limite da categoria.', confirmToken: 'tok-soft-limit' }] },
  'aviso',
)

describe('fluxo de aviso confirmável', () => {
  it('mostra o aviso, não grava sozinho e reenvia com o confirmToken ao confirmar', async () => {
    const user = userEvent.setup()
    const create = vi.fn().mockRejectedValueOnce(softLimitWarning).mockResolvedValueOnce({ ...EXISTING, id: 'novo', version: 0 })
    const client = makeClient({ create })
    renderWithProviders(<CatalogCrud meta={metaFor(client)} />)

    await user.click(await screen.findByRole('button', { name: /Adicionar item/ }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Descrição/), 'TESTE Novo')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    // 1ª tentativa: sem tokens, o servidor devolveu só o aviso.
    const alert = await within(dialog).findByRole('alertdialog')
    expect(within(alert).getByText(/acima do limite da categoria/)).toBeInTheDocument()
    expect(create).toHaveBeenCalledTimes(1)
    expect(create.mock.calls[0]?.[1]).toEqual({ confirmTokens: [] })

    // O botão Salvar fica travado enquanto há aviso pendente.
    expect(within(dialog).getByRole('button', { name: 'Salvar' })).toBeDisabled()

    // Confirmar reenvia a MESMA requisição com o token aceito.
    await user.click(within(alert).getByRole('button', { name: 'Confirmar e continuar' }))
    await waitFor(() => expect(create).toHaveBeenCalledTimes(2))
    expect(create.mock.calls[1]?.[0]).toMatchObject({ name: 'TESTE Novo' })
    expect(create.mock.calls[1]?.[1]).toEqual({ confirmTokens: ['tok-soft-limit'] })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('Registro criado.')).toBeInTheDocument()
  })

  it('"Voltar e corrigir" descarta o aviso sem gravar', async () => {
    const user = userEvent.setup()
    const create = vi.fn().mockRejectedValue(softLimitWarning)
    renderWithProviders(<CatalogCrud meta={metaFor(makeClient({ create }))} />)

    await user.click(await screen.findByRole('button', { name: /Adicionar item/ }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Descrição/), 'TESTE Novo')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    const alert = await within(dialog).findByRole('alertdialog')
    await user.click(within(alert).getByRole('button', { name: 'Voltar e corrigir' }))
    expect(within(dialog).queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(create).toHaveBeenCalledTimes(1)
    expect(within(dialog).getByLabelText(/Descrição/)).toHaveValue('TESTE Novo')
  })
})

describe('erro de validação do servidor e concorrência', () => {
  it('erro de campo do servidor aparece ligado ao campo (aria-invalid + aria-describedby)', async () => {
    const user = userEvent.setup()
    const create = vi
      .fn()
      .mockRejectedValue(new ApiError('validation', 422, { errors: [{ field: 'name', code: 'DUPLICATE', message: 'Já existe um item com esta descrição.' }] }, 'x'))
    renderWithProviders(<CatalogCrud meta={metaFor(makeClient({ create }))} />)
    await user.click(await screen.findByRole('button', { name: /Adicionar item/ }))
    const dialog = await screen.findByRole('dialog')
    const field = within(dialog).getByLabelText(/Descrição/)
    await user.type(field, 'TESTE Repetido')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    const message = await within(dialog).findByText('Já existe um item com esta descrição.')
    expect(field).toHaveAttribute('aria-invalid', 'true')
    expect(field.getAttribute('aria-describedby')).toContain(message.closest('p')?.id ?? 'x')
  })

  it('409 mostra "o registro mudou" e NÃO apaga o que foi digitado', async () => {
    const user = userEvent.setup()
    const update = vi.fn().mockRejectedValueOnce(new ApiError('conflict', 409, null, 'mudou')).mockResolvedValueOnce({ ...EXISTING, version: 9 })
    const get = vi.fn().mockResolvedValue({ ...EXISTING, name: 'Valor do servidor', version: 8 })
    renderWithProviders(<CatalogCrud meta={metaFor(makeClient({ update, get }))} />)

    await user.click(await screen.findByRole('button', { name: /Editar TESTE Item/ }))
    const dialog = await screen.findByRole('dialog')
    const field = within(dialog).getByLabelText(/Descrição/)
    await user.clear(field)
    await user.type(field, 'Minha edição')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    expect(await within(dialog).findByText(/O registro mudou desde que você o abriu/)).toBeInTheDocument()
    expect(update.mock.calls[0]?.[2]).toBe(7) // enviou a version carregada
    expect(field).toHaveValue('Minha edição')

    // Manter o digitado: adota a versão nova (8) e permite salvar de novo, sem perder o texto.
    await user.click(within(dialog).getByRole('button', { name: /manter o que digitei/ }))
    await waitFor(() => expect(within(dialog).queryByText(/O registro mudou/)).not.toBeInTheDocument())
    expect(field).toHaveValue('Minha edição')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(update).toHaveBeenCalledTimes(2))
    expect(update.mock.calls[1]?.[2]).toBe(8)
    expect(update.mock.calls[1]?.[1]).toMatchObject({ name: 'Minha edição' })
  })
})

describe('lista sem dados inventados', () => {
  it('mostra o estado vazio real, sem linhas', async () => {
    const client = makeClient({ list: vi.fn().mockResolvedValue({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }) })
    renderWithProviders(<CatalogCrud meta={metaFor(client)} />)
    expect(await screen.findByText(/Nenhum registro cadastrado em itens de teste/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('mostra o erro de rede real, sem lista', async () => {
    const client = makeClient({ list: vi.fn().mockRejectedValue(new ApiError('network', null, null, 'Não foi possível falar com o servidor. Verifique a conexão e tente novamente.')) })
    renderWithProviders(<CatalogCrud meta={metaFor(client)} />)
    expect(await screen.findByText('Não foi possível carregar a lista')).toBeInTheDocument()
    expect(screen.getByText(/Não foi possível falar com o servidor/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('exclusão é lógica: pede motivo e chama remove(id, motivo)', async () => {
    const user = userEvent.setup()
    const remove = vi.fn().mockResolvedValue(undefined)
    renderWithProviders(<CatalogCrud meta={metaFor(makeClient({ remove }))} />)
    await user.click(await screen.findByRole('button', { name: /Excluir TESTE Item/ }))
    const dialog = await screen.findByRole('dialog')
    const confirmButton = within(dialog).getByRole('button', { name: 'Excluir' })
    expect(confirmButton).toBeDisabled()
    await user.type(within(dialog).getByLabelText(/Motivo da exclusão/), 'cadastro duplicado')
    await user.click(confirmButton)
    await waitFor(() => expect(remove).toHaveBeenCalledWith('item-1', 'cadastro duplicado'))
  })
})
