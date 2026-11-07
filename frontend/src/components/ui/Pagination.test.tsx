import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Pagination } from './Pagination'

describe('Pagination (base)', () => {
  it('mantém o resumo e Anterior/Próxima como antes da ED-08', async () => {
    const onPageChange = vi.fn()
    render(<Pagination page={0} totalPages={3} totalElements={60} onPageChange={onPageChange} />)
    expect(screen.getByText('Página 1 de 3 · 60 registros')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /anterior/i })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: /próxima/i }))
    expect(onPageChange).toHaveBeenCalledWith(1)
  })

  it('sem onPageSizeChange, o seletor de tamanho não aparece', () => {
    render(<Pagination page={0} totalPages={3} totalElements={60} onPageChange={vi.fn()} />)
    expect(screen.queryByLabelText(/itens por página/i)).not.toBeInTheDocument()
  })

  it('com uma página só, o "ir para página" não aparece', () => {
    render(<Pagination page={0} totalPages={1} totalElements={5} onPageChange={vi.fn()} />)
    expect(screen.queryByLabelText(/ir para página/i)).not.toBeInTheDocument()
  })
})

describe('Pagination — seletor de tamanho de página (ED-08)', () => {
  it('mudar o seletor chama onPageSizeChange com o tamanho como número', async () => {
    const onPageSizeChange = vi.fn()
    render(
      <Pagination page={0} totalPages={3} totalElements={60} onPageChange={vi.fn()} pageSize={25} onPageSizeChange={onPageSizeChange} />,
    )
    await userEvent.selectOptions(screen.getByLabelText(/itens por página/i), '50')
    expect(onPageSizeChange).toHaveBeenCalledWith(50)
  })

  it('o seletor oferece 10, 25, 50 e 100 (teto de 100 da P0.16)', () => {
    render(<Pagination page={0} totalPages={3} totalElements={60} onPageChange={vi.fn()} pageSize={25} onPageSizeChange={vi.fn()} />)
    const options = screen.getAllByRole('option').map((o) => (o as HTMLOptionElement).value)
    expect(options).toEqual(['10', '25', '50', '100'])
  })
})

describe('Pagination — ir para página (ED-08)', () => {
  it('página válida navega (base 0) ao clicar em Ir', async () => {
    const onPageChange = vi.fn()
    render(<Pagination page={0} totalPages={3} totalElements={60} onPageChange={onPageChange} />)
    await userEvent.type(screen.getByLabelText(/ir para página/i), '3')
    await userEvent.click(screen.getByRole('button', { name: 'Ir' }))
    expect(onPageChange).toHaveBeenCalledWith(2)
  })

  it('página válida navega com Enter no campo', async () => {
    const onPageChange = vi.fn()
    render(<Pagination page={0} totalPages={3} totalElements={60} onPageChange={onPageChange} />)
    await userEvent.type(screen.getByLabelText(/ir para página/i), '2{enter}')
    expect(onPageChange).toHaveBeenCalledWith(1)
  })

  it('página acima do total NÃO navega e avisa o intervalo', async () => {
    const onPageChange = vi.fn()
    render(<Pagination page={0} totalPages={3} totalElements={60} onPageChange={onPageChange} />)
    await userEvent.type(screen.getByLabelText(/ir para página/i), '9')
    await userEvent.click(screen.getByRole('button', { name: 'Ir' }))
    expect(onPageChange).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('Digite uma página entre 1 e 3.')
  })

  it('página zero NÃO navega e avisa o intervalo', async () => {
    const onPageChange = vi.fn()
    render(<Pagination page={0} totalPages={3} totalElements={60} onPageChange={onPageChange} />)
    await userEvent.type(screen.getByLabelText(/ir para página/i), '0')
    await userEvent.click(screen.getByRole('button', { name: 'Ir' }))
    expect(onPageChange).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('Digite uma página entre 1 e 3.')
  })

  it('campo vazio NÃO navega e avisa o intervalo', async () => {
    const onPageChange = vi.fn()
    render(<Pagination page={0} totalPages={3} totalElements={60} onPageChange={onPageChange} />)
    await userEvent.click(screen.getByRole('button', { name: 'Ir' }))
    expect(onPageChange).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('Digite uma página entre 1 e 3.')
  })

  it('depois de um erro, uma página válida limpa o aviso e navega', async () => {
    const onPageChange = vi.fn()
    render(<Pagination page={0} totalPages={3} totalElements={60} onPageChange={onPageChange} />)
    const input = screen.getByLabelText(/ir para página/i)
    await userEvent.type(input, '9')
    await userEvent.click(screen.getByRole('button', { name: 'Ir' }))
    expect(screen.getByRole('alert')).toBeInTheDocument()
    await userEvent.clear(input)
    await userEvent.type(input, '2')
    await userEvent.click(screen.getByRole('button', { name: 'Ir' }))
    expect(onPageChange).toHaveBeenCalledWith(1)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
