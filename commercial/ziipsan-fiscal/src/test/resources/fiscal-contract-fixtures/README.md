# Corpus de XML de referência — Ziip SAN × Ziip Cloud (P1.10, ADR-0003)

O mesmo corpus roda no CI do Ziip Cloud e no CI do Ziip SAN, com o mesmo resultado nos
dois. É a mitigação da divergência silenciosa entre os dois motores (C# e Java): cada
mudança de NT, schema ou regra de validação entra nos dois códigos com um vetor novo, e
divergência bloqueia o merge.

## O que este corpus é — e o que não é

- **É** a forma canônica do porte: para cada caso, entrada, resultado tributário esperado,
  XML **não assinado** esperado e resultado de validação estrutural (Id da DPS, Id do
  evento, vigência). A comparação é exata (string), porque cada caso fixa `dhEmi`/`dhEvento`
  — nenhum relógio entra na comparação (o builder C# lê o relógio direto; para byte a byte
  ele precisa de relógio injetável, mudança a pedir lá — FISCAL-PORTE-JAVA §5.3).
- **NÃO é** o XSD oficial: o XSD da DPS não está disponível neste sandbox e o próprio
  documento de porte registra que o repositório C# não tem XSD nem XML de referência
  (FISCAL-PORTE-JAVA §1.1). A validação contra o XSD oficial é pendência da homologação,
  antes de qualquer emissão fora do simulador. Nenhum caso deste corpus afirma
  conformidade com o schema oficial — afirma que os dois motores produzem o mesmo canônico.
- **Nunca contém** XML assinado, elemento `Signature` ou certificado: os vetores guardados
  de um ciclo de homologação entram aqui sem assinatura (ADR-0033, ajuste v2.9).
- **Dados sintéticos apenas.** O CNPJ alfanumérico `12ABC34501DE35` e o CPF `11144477735`
  são os vetores de teste do próprio projeto (DpsIdTest); nenhum documento real.

## Versões (tabela NT × schema, ADR-0003 §5.5)

| Documento | NT / referência | Schema (forma canônica do porte) | Estado |
|---|---|---|---|
| DPS da NFS-e Nacional | NT 007/2026 (SE/CGNFS-e) apud o porte — half-even e tolerância de R$ 0,01 só para PIS/Cofins; IBS/CBS a confirmar nas NTs 004/008/009 e no manual | `1.00` sem grupo IBSCBS, `1.01` com grupo IBSCBS (regra do porte) | Forma canônica congelada; XSD oficial pendente |
| Evento de cancelamento | Porte §1, §3.5 | `1.00`; Id `PRE` + chave (50) + código (6) | Idem |
| NFAg-e (modelo 75) | MOC/XSD em minuta (porte §4) | — | Fora deste corpus até a P2.6 |

## Formato de cada caso (JSON)

Valores monetários e alíquotas são **strings** (`"5440.00"`, `"0.009"`), nunca float, nos
dois CIs. Campos:

- `id`, `description`, `kind`: `tax-and-dps` | `tax-only` | `rounding` | `dps-id` |
  `event` | `vigencia`.
- `tax-and-dps`: `gross`, `rates` {iss, irrf, csrf, cbs, ibs}, `expected` {iss, irrf, csrf,
  cbs, ibs, net}, `dps` {ibge, inscriptionKind (`CNPJ`|`CPF`), inscription, series,
  number, dhEmi, taker, withIbsCbs}, `expectedDpsId`, `expectedXml`.
- `tax-only`: `gross`, `rates`, `expected` (idem, sem DPS).
- `rounding`: `cases` [{input, expected}] (half-even, escala 2).
- `dps-id`: `ibge`, `inscriptionKind`, `inscription`, `series`, `number`, `expectedDpsId`.
- `event`: `accessKey` (50), `eventCode` (6 — o código numérico; o nome do evento no porte
  é `e101101`), `dhEvento`, `reason`, `expectedEventId`, `expectedXml`.
- `vigencia`: `rows` [{taxCode, scope, validFrom, validTo|null, rate, note}],
  `queries` [{taxCode, competence, expectedRate|null}] — `expectedRate` nulo significa
  "alíquota ausente = erro, documento não emitido". Uma alíquota marcada como valor de
  teste (`note` com `TEST`) é dado de teste para a lógica de seleção, **nunca** valor
  normativo: a CBS plena de 2027 não tem valor publicado nestes documentos e não é
  inventada aqui.

## Como o CI do Ziip Cloud consome

O repositório do Ziip Cloud não está neste sandbox, então a ligação do lado de lá é
pendência de integração (o formato acima é o contrato): o CI do Cloud lê os mesmos
arquivos, roda os mesmos casos no `MotorFiscal`/`DpsXmlBuilder`/`EventosService` do C# e
compara: valores tributários, Id da DPS, Id do evento e XML canônico exato. Divergência
em qualquer caso bloqueia o merge nos dois repositórios.

## Casos

| Arquivo | Vetor de origem |
|---|---|
| `florianopolis-5440.json` | Porte §5: nota de Florianópolis + IBS/CBS informativos |
| `ibs-cbs-10000.json` | Porte §5: IBS/CBS sobre R$ 10.000,00 |
| `half-even.json` | Porte §5: 0,125 → 0,12; 0,135 → 0,14 |
| `dps-id-cpf.json` | Porte §5: Id da DPS com CPF |
| `cancel-event.json` | Porte §1, §3.5: cancelamento e101101 |
| `vigencia-2027.json` | P2.4: troca de alíquota na virada de 01/01/2027 (valor de 2027 = teste) |
