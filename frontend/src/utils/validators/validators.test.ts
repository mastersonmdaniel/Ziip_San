import { describe, expect, it } from 'vitest'
import { computeCnpjCheckDigits, formatCnpj, isAlphanumericCnpj, isValidCnpj, stripCnpj } from './cnpj'
import { formatCep, isValidCep, stripCep } from './cep'
import { computeCpfCheckDigits, formatCpf, isValidCpf, stripCpf } from './cpf'
import { detectDocumentKind, maskDocument } from './document'
import { BRAZIL_BOUNDS, checkCoordinatePair, isInsideBrazil, parseDecimalPtBr } from './geo'
import { formatInscription, isValidInscription, maskInscription, parseInscription } from './inscription'
import { computeMatriculaDv, formatMatricula, isValidMatricula } from './matricula'
import { formatPhone, fromE164, isValidPhone, stripPhone, toE164 } from './phone'

/*
 * Todos os valores esperados abaixo foram CALCULADOS À MÃO (soma ponderada e resto por 11),
 * não obtidos executando o código. O cálculo de cada um está no comentário do teste.
 */

describe('CPF (módulo 11, pesos 10..2 e 11..2)', () => {
  it('529.982.247-25: DV1 e DV2 calculados à mão', () => {
    // DV1: 5*10+2*9+9*8+9*7+8*6+2*5+2*4+4*3+7*2 = 50+18+72+63+48+10+8+12+14 = 295; 295 mod 11 = 9; DV1 = 11-9 = 2
    // DV2: 5*11+2*10+9*9+9*8+8*7+2*6+2*5+4*4+7*3+2*2 = 55+20+81+72+56+12+10+16+21+4 = 347; 347 mod 11 = 6; DV2 = 11-6 = 5
    expect(computeCpfCheckDigits('529982247')).toBe('25')
    expect(isValidCpf('529.982.247-25')).toBe(true)
    expect(isValidCpf('52998224725')).toBe(true)
  })

  it('111.444.777-35', () => {
    // DV1: 1*10+1*9+1*8+4*7+4*6+4*5+7*4+7*3+7*2 = 10+9+8+28+24+20+28+21+14 = 162; 162 mod 11 = 8; DV1 = 3
    // DV2: 11+10+9+4*8+4*7+4*6+7*5+7*4+7*3+3*2 = 11+10+9+32+28+24+35+28+21+6 = 204; 204 mod 11 = 6; DV2 = 5
    expect(computeCpfCheckDigits('111444777')).toBe('35')
    expect(isValidCpf('111.444.777-35')).toBe(true)
  })

  it('resto 0 ou 1 gera DV 0', () => {
    // 000000006: DV1: 6*2 = 12; 12 mod 11 = 1 (< 2) -> DV1 = 0
    // DV2: 6*3 + 0*2 = 18; 18 mod 11 = 7; DV2 = 11-7 = 4  -> "04"
    expect(computeCpfCheckDigits('000000006')).toBe('04')
    // 000000000: somas 0, resto 0 -> "00"
    expect(computeCpfCheckDigits('000000000')).toBe('00')
  })

  it('00000000191 (DV2 = 1 vindo do resto 10)', () => {
    // DV1: 1*2 = 2; 2 mod 11 = 2; DV1 = 9.  DV2: 1*3 + 9*2 = 21; 21 mod 11 = 10; DV2 = 11-10 = 1
    expect(computeCpfCheckDigits('000000001')).toBe('91')
  })

  it('rejeita DV errado, tamanho errado e sequências repetidas', () => {
    expect(isValidCpf('529.982.247-24')).toBe(false)
    expect(isValidCpf('529.982.247')).toBe(false)
    expect(isValidCpf('')).toBe(false)
    expect(isValidCpf('111.111.111-11')).toBe(false)
    expect(isValidCpf('000.000.000-00')).toBe(false)
  })

  it('máscara e limpeza', () => {
    expect(formatCpf('52998224725')).toBe('529.982.247-25')
    expect(formatCpf('5299')).toBe('529.9')
    expect(stripCpf('529.982.247-25 abc')).toBe('52998224725')
  })
})

describe('CNPJ numérico (pesos 5,4,3,2,9,8,7,6,5,4,3,2 e 6,5,4,3,2,9,8,7,6,5,4,3,2)', () => {
  it('11.222.333/0001-81', () => {
    // DV1: 1*5+1*4+2*3+2*2+2*9+3*8+3*7+3*6+0+0+0+1*2 = 5+4+6+4+18+24+21+18+2 = 102; 102 mod 11 = 3; DV1 = 8
    // DV2 (inclui DV1=8): 6+5+2*4+2*3+2*2+3*9+3*8+3*7+0+0+0+1*3+8*2 = 6+5+8+6+4+27+24+21+3+16 = 120; 120 mod 11 = 10; DV2 = 1
    expect(computeCnpjCheckDigits('112223330001')).toBe('81')
    expect(isValidCnpj('11.222.333/0001-81')).toBe(true)
    expect(isValidCnpj('11222333000181')).toBe(true)
  })

  it('11.444.777/0001-61', () => {
    // DV1: 5+4+4*3+4*2+4*9+7*8+7*7+7*6+0+0+0+2 = 5+4+12+8+36+56+49+42+2 = 214; 214 mod 11 = 5; DV1 = 6
    // DV2: 6+5+4*4+4*3+4*2+7*9+7*8+7*7+0+0+0+1*3+6*2 = 6+5+16+12+8+63+56+49+3+12 = 230; 230 mod 11 = 10; DV2 = 1
    expect(computeCnpjCheckDigits('114447770001')).toBe('61')
    expect(isValidCnpj('11.444.777/0001-61')).toBe(true)
  })

  it('rejeita DV errado, tamanho errado e sequência repetida', () => {
    expect(isValidCnpj('11.222.333/0001-82')).toBe(false)
    expect(isValidCnpj('11.222.333/0001')).toBe(false)
    expect(isValidCnpj('00000000000000')).toBe(false)
  })
})

describe('CNPJ alfanumérico (valor = ASCII - 48)', () => {
  // Vetor oficial da Receita Federal (CNPJ Alfanumérico, perguntas e respostas): 12.ABC.345/01DE-35
  it('12.ABC.345/01DE-35 (vetor oficial)', () => {
    // valores: 1,2,A=17,B=18,C=19,3,4,5,0,1,D=20,E=21
    // DV1: 1*5+2*4+17*3+18*2+19*9+3*8+4*7+5*6+0*5+1*4+20*3+21*2
    //    = 5+8+51+36+171+24+28+30+0+4+60+42 = 459; 459 mod 11 = 8; DV1 = 11-8 = 3
    // DV2 (com DV1=3): 1*6+2*5+17*4+18*3+19*2+3*9+4*8+5*7+0*6+1*5+20*4+21*3+3*2
    //    = 6+10+68+54+38+27+32+35+0+5+80+63+6 = 424; 424 mod 11 = 6; DV2 = 11-6 = 5
    expect(computeCnpjCheckDigits('12ABC34501DE')).toBe('35')
    expect(isValidCnpj('12.ABC.345/01DE-35')).toBe(true)
    expect(isValidCnpj('12abc34501de35')).toBe(true)
  })

  it('rejeita DV trocado e letras no DV', () => {
    expect(isValidCnpj('12.ABC.345/01DE-36')).toBe(false)
    expect(isValidCnpj('12.ABC.345/01DE-3X')).toBe(false)
    expect(isValidCnpj('12.ABC.345/01DE-53')).toBe(false)
  })

  it('valor do caractere: dígitos 0..9 e letras A=17..Z=42', () => {
    expect('0'.charCodeAt(0) - 48).toBe(0)
    expect('9'.charCodeAt(0) - 48).toBe(9)
    expect('A'.charCodeAt(0) - 48).toBe(17)
    expect('Z'.charCodeAt(0) - 48).toBe(42)
  })

  it('máscara, maiúsculas e detecção de letras', () => {
    expect(formatCnpj('12abc34501de35')).toBe('12.ABC.345/01DE-35')
    expect(stripCnpj('12.abc.345/01de-35')).toBe('12ABC34501DE35')
    expect(isAlphanumericCnpj('12.ABC.345/01DE-35')).toBe(true)
    expect(isAlphanumericCnpj('11.222.333/0001-81')).toBe(false)
  })
})

describe('documento: tipo e máscara LGPD', () => {
  it('detecta CPF x CNPJ', () => {
    expect(detectDocumentKind('52998224725')).toBe('CPF')
    expect(detectDocumentKind('529982247251')).toBe('CNPJ')
    expect(detectDocumentKind('12ABC')).toBe('CNPJ')
  })

  it('mascara mantendo só o miolo', () => {
    expect(maskDocument('52998224725')).toBe('***.982.247-**')
    expect(maskDocument('11222333000181')).toBe('**.***.333/0001-**')
    expect(maskDocument('12ABC34501DE35')).toBe('**.***.345/01DE-**')
  })
})

describe('CEP', () => {
  it('formata 00000-000', () => {
    expect(formatCep('01310100')).toBe('01310-100')
    expect(formatCep('0131')).toBe('0131')
    expect(stripCep('01310-100')).toBe('01310100')
  })

  it('valida 8 dígitos e rejeita zeros', () => {
    expect(isValidCep('01310-100')).toBe(true)
    expect(isValidCep('01310100')).toBe(true)
    expect(isValidCep('00000-000')).toBe(false)
    expect(isValidCep('1234')).toBe(false)
    expect(isValidCep('')).toBe(false)
  })
})

describe('inscrição LLL.SSS.QQQ.LLLL.SSS', () => {
  it('formata com zeros à esquerda só na exibição', () => {
    expect(formatInscription({ locality: 1, sector: 2, block: 3, lot: 4, sublot: 5 })).toBe('001.002.003.0004.005')
  })

  it('interpreta com ou sem pontos', () => {
    const expected = { locality: 1, sector: 2, block: 3, lot: 4, sublot: 5 }
    expect(parseInscription('001.002.003.0004.005')).toEqual(expected)
    expect(parseInscription('0010020030004005')).toEqual(expected)
  })

  it('máscara parcial ao digitar', () => {
    expect(maskInscription('0010020')).toBe('001.002.0')
    expect(maskInscription('0010020030004')).toBe('001.002.003.0004')
    expect(maskInscription('abc')).toBe('')
  })

  it('rejeita incompleta e fora das faixas (lote 1-9999, sublote 0-999, demais 1-999)', () => {
    expect(parseInscription('001.002.003.0004')).toBeNull()
    expect(parseInscription('000.002.003.0004.005')).toBeNull()
    expect(parseInscription('001.002.003.0000.005')).toBeNull()
    expect(parseInscription('001.002.003.0004.000')).toEqual({ locality: 1, sector: 2, block: 3, lot: 4, sublot: 0 })
    expect(isValidInscription({ locality: 1, sector: 1, block: 1, lot: 10000, sublot: 0 })).toBe(false)
  })
})

describe('matrícula (módulo 11, pesos 2..9 da direita para a esquerda; A CONFIRMAR com o backend)', () => {
  it('1234567 -> DV 9', () => {
    // dígitos da direita: 7*2+6*3+5*4+4*5+3*6+2*7+1*8 = 14+18+20+20+18+14+8 = 112; 112 mod 11 = 2; DV = 11-2 = 9
    expect(computeMatriculaDv('1234567')).toBe('9')
    expect(isValidMatricula('1234567-9')).toBe(true)
    expect(isValidMatricula('12345678')).toBe(false)
  })

  it('peso volta a 2 depois do 9: 123456789 -> DV 7', () => {
    // 9*2+8*3+7*4+6*5+5*6+4*7+3*8+2*9+1*2 = 18+24+28+30+30+28+24+18+2 = 202; 202 mod 11 = 4; DV = 7
    expect(computeMatriculaDv('123456789')).toBe('7')
  })

  it('oito dígitos: 12345678 -> DV 9', () => {
    // 8*2+7*3+6*4+5*5+4*6+3*7+2*8+1*9 = 16+21+24+25+24+21+16+9 = 156; 156 mod 11 = 2; DV = 9
    expect(computeMatriculaDv('12345678')).toBe('9')
  })

  it('resto 10 vira DV 1; resto 1 vira DV 0; DV >= 10 vira 0', () => {
    // "5": 5*2 = 10; 10 mod 11 = 10; DV = 11-10 = 1
    expect(computeMatriculaDv('5')).toBe('1')
    // "6": 6*2 = 12; 12 mod 11 = 1; DV = 11-1 = 10 -> 0
    expect(computeMatriculaDv('6')).toBe('0')
    // "0": soma 0; DV = 11 -> 0
    expect(computeMatriculaDv('0')).toBe('0')
    // "100": 0*2+0*3+1*4 = 4; DV = 11-4 = 7
    expect(computeMatriculaDv('100')).toBe('7')
  })

  it('formata base-DV', () => {
    expect(formatMatricula('1234567', '9')).toBe('1234567-9')
  })
})

describe('telefone', () => {
  it('celular e fixo', () => {
    expect(isValidPhone('(11) 98765-4321')).toBe(true)
    expect(isValidPhone('1133334444')).toBe(true)
    expect(isValidPhone('11833334444')).toBe(false) // 11 dígitos exigem 9 após o DDD
    expect(isValidPhone('0133334444')).toBe(false) // DDD não começa com 0
    expect(isValidPhone('113333444')).toBe(false)
  })

  it('formata e converte para E.164', () => {
    expect(formatPhone('11987654321')).toBe('(11) 98765-4321')
    expect(formatPhone('1133334444')).toBe('(11) 3333-4444')
    expect(toE164('(11) 98765-4321')).toBe('+5511987654321')
    expect(fromE164('+5511987654321')).toBe('11987654321')
    expect(stripPhone('+55 11 98765-4321')).toBe('11987654321')
  })
})

describe('coordenadas (SIRGAS 2000, dentro do Brasil, par completo)', () => {
  it('interpreta vírgula e ponto', () => {
    expect(parseDecimalPtBr('-9,9745')).toBeCloseTo(-9.9745, 6)
    expect(parseDecimalPtBr('-9.9745')).toBeCloseTo(-9.9745, 6)
    expect(parseDecimalPtBr('abc')).toBeNaN()
    expect(parseDecimalPtBr('')).toBeNaN()
  })

  it('exige o par completo', () => {
    expect(checkCoordinatePair('', '')).toBeNull()
    expect(checkCoordinatePair('-9,97', '')).toBe('incomplete')
    expect(checkCoordinatePair('', '-67,81')).toBe('incomplete')
    expect(checkCoordinatePair('abc', '-67,81')).toBe('invalid')
  })

  it('rejeita fora do Brasil e aceita São Paulo e Rio Branco', () => {
    expect(checkCoordinatePair('48,85', '2,35')).toBe('outside-brazil') // Paris
    expect(checkCoordinatePair('-23,55', '-46,63')).toBeNull() // São Paulo
    expect(checkCoordinatePair('-9,97', '-67,81')).toBeNull() // Rio Branco
    expect(isInsideBrazil({ lat: BRAZIL_BOUNDS.latMax + 0.01, lng: -50 })).toBe(false)
  })
})
