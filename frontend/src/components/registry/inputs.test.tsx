import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { parseInscription } from '@utils/validators/inscription'
import { CpfCnpjInput } from './CpfCnpjInput'
import { InscriptionInput } from './InscriptionInput'

function DocumentHarness({ kind }: { kind: 'CPF' | 'CNPJ' | 'auto' }) {
  const [raw, setRaw] = useState('')
  return (
    <>
      <CpfCnpjInput label="Documento" kind={kind} value={raw} onChange={setRaw} />
      <output data-testid="raw">{raw}</output>
    </>
  )
}

function InscriptionHarness() {
  const [masked, setMasked] = useState('')
  const parsed = parseInscription(masked)
  return (
    <>
      <InscriptionInput value={masked} onChange={setMasked} />
      <output data-testid="parsed">{parsed ? JSON.stringify(parsed) : 'nulo'}</output>
    </>
  )
}

describe('CpfCnpjInput', () => {
  it('aplica a máscara de CPF e entrega o valor sem pontuação', async () => {
    const user = userEvent.setup()
    render(<DocumentHarness kind="CPF" />)
    const input = screen.getByLabelText('Documento')
    await user.type(input, '52998224725')
    expect(input).toHaveValue('529.982.247-25')
    expect(screen.getByTestId('raw')).toHaveTextContent('52998224725')
    await user.tab()
    expect(input).not.toHaveAttribute('aria-invalid')
    expect(screen.queryByText(/inválido/)).not.toBeInTheDocument()
  })

  it('CPF com dígito verificador errado mostra o erro ligado ao campo', async () => {
    const user = userEvent.setup()
    render(<DocumentHarness kind="CPF" />)
    const input = screen.getByLabelText('Documento')
    await user.type(input, '52998224724')
    await user.tab()
    const message = screen.getByText('CPF inválido: confira os dígitos.')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input.getAttribute('aria-describedby')).toContain(message.closest('p')?.id ?? 'x')
  })

  it('CPF incompleto pede os 11 dígitos', async () => {
    const user = userEvent.setup()
    render(<DocumentHarness kind="CPF" />)
    await user.type(screen.getByLabelText('Documento'), '5299')
    await user.tab()
    expect(screen.getByText('CPF incompleto: informe os 11 dígitos.')).toBeInTheDocument()
  })

  it('aceita CNPJ alfanumérico oficial 12.ABC.345/01DE-35, converte para maiúsculas e valida o DV', async () => {
    const user = userEvent.setup()
    render(<DocumentHarness kind="CNPJ" />)
    const input = screen.getByLabelText('Documento')
    await user.type(input, '12abc34501de35')
    expect(input).toHaveValue('12.ABC.345/01DE-35')
    expect(screen.getByTestId('raw')).toHaveTextContent('12ABC34501DE35')
    await user.tab()
    expect(input).not.toHaveAttribute('aria-invalid')
  })

  it('CNPJ alfanumérico com DV trocado é rejeitado', async () => {
    const user = userEvent.setup()
    render(<DocumentHarness kind="CNPJ" />)
    await user.type(screen.getByLabelText('Documento'), '12ABC34501DE36')
    await user.tab()
    expect(screen.getByText(/CNPJ inválido/)).toBeInTheDocument()
  })

  it('CNPJ numérico continua válido', async () => {
    const user = userEvent.setup()
    render(<DocumentHarness kind="CNPJ" />)
    const input = screen.getByLabelText('Documento')
    await user.type(input, '11222333000181')
    expect(input).toHaveValue('11.222.333/0001-81')
    await user.tab()
    expect(input).not.toHaveAttribute('aria-invalid')
  })

  it('modo automático troca de CPF para CNPJ ao passar de 11 posições', async () => {
    const user = userEvent.setup()
    render(<DocumentHarness kind="auto" />)
    const input = screen.getByLabelText('Documento')
    await user.type(input, '11222333000181')
    expect(input).toHaveValue('11.222.333/0001-81')
  })

  it('sem violações de acessibilidade (axe), com e sem erro', async () => {
    const user = userEvent.setup()
    const { container } = render(<DocumentHarness kind="CPF" />)
    expect(await axe(container)).toHaveNoViolations()
    await user.type(screen.getByLabelText('Documento'), '123')
    await user.tab()
    expect(await axe(container)).toHaveNoViolations()
  })
})

describe('InscriptionInput', () => {
  it('aplica a máscara LLL.SSS.QQQ.LLLL.SSS enquanto digita e só aceita dígitos', async () => {
    const user = userEvent.setup()
    render(<InscriptionHarness />)
    const input = screen.getByLabelText('Inscrição')
    await user.type(input, '00a1002003 0004005')
    expect(input).toHaveValue('001.002.003.0004.005')
    expect(screen.getByTestId('parsed')).toHaveTextContent('{"locality":1,"sector":2,"block":3,"lot":4,"sublot":5}')
  })

  it('zeros à esquerda só na exibição: o valor interpretado são números', async () => {
    const user = userEvent.setup()
    render(<InscriptionHarness />)
    await user.type(screen.getByLabelText('Inscrição'), '0010020030004000')
    expect(screen.getByTestId('parsed')).toHaveTextContent('"sublot":0')
  })

  it('incompleta mostra erro ao sair do campo', async () => {
    const user = userEvent.setup()
    render(<InscriptionHarness />)
    const input = screen.getByLabelText('Inscrição')
    await user.type(input, '001002')
    await user.tab()
    expect(screen.getByText(/Inscrição incompleta/)).toBeInTheDocument()
    expect(input).toHaveAttribute('aria-invalid', 'true')
  })

  it('fora das faixas (lote 0000) é rejeitada', async () => {
    const user = userEvent.setup()
    render(<InscriptionHarness />)
    await user.type(screen.getByLabelText('Inscrição'), '0010020030000005')
    await user.tab()
    expect(screen.getByText(/fora das faixas/)).toBeInTheDocument()
    expect(screen.getByTestId('parsed')).toHaveTextContent('nulo')
  })

  it('sem violações de acessibilidade (axe)', async () => {
    const { container } = render(<InscriptionHarness />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
