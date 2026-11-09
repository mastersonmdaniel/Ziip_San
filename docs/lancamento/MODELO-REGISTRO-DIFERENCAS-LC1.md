# LC.1 — Modelo de registo de diferenças local × AWS (item A10)

- **Pacote:** LC.1 — Ensaio de implantação na AWS com dado sintético
- **Produtor do modelo:** sessão `a1981a33-6f88-49c6-8f6f-f9d70266f27b` (papel: Produtor)
- **Regra do aceite:** **toda** a diferença registada tem correção aplicada
  **ou** tarefa aberta — nenhuma linha fica "só anotada". Linha sem um dos
  dois = A10 não cumprido (passo 5.1 do roteiro).

## Registo (uma linha por diferença)

| # | Fase/Passo | Diferença (local × AWS, factual) | Evidência | Impacto | Correção aplicada (qual/quando) **ou** Tarefa (id/título) |
|---|---|---|---|---|---|
| D-01 | | | | | |

## Classes de impacto (marcar uma por linha)

- **Bloqueante:** impede o lançamento ou viola requisito (ex.: extensão
  ausente no RDS; Object Lock não recusa exclusão; segredo exposto).
- **Funcional:** comportamento difere e exige correção de código/IaC.
- **Desempenho:** desvio de fumaça/carga fora da tolerância **[A DEFINIR]**
  (P-LC1-03).
- **Conhecida sem tarefa:** desvio registado e aceite com justificativa
  (ex.: imagem de compose local `postgis 15-3.4` × RDS PG17 — diferença já
  documentada no ambiente local da P0.27). **Continua a exigir a coluna de
  destino preenchida** ("aceite fundamentado" + referência).

## Fecho do registo

- Total de diferenças: ___ · Com correção: ___ · Com tarefa: ___ ·
  Aceites fundamentados: ___ · **Sem destino: ___ (tem de ser 0)**
- Conferido por (≠ executor): ______________ Data: __________
