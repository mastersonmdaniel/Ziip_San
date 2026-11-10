# Errata R13-B1 — `RELATORIO-CORRECAO-CI-SECURITY-CATALOGO.md` §3-P5

- **Data:** 2026-11-09 (escrita 17:02 UTC; 14:02 −03).
- **Produtor do lote R13-A (auditoria contínua, Rodada 13):** sessão `1c342896-6c61-42fb-b42a-c8eaeef0de4a`.
- **Pacote:** `correcao-ci-security-catalogo`.
- **Natureza:** errata de uma linha ao RELATORIO. O ficheiro original NÃO foi
  reescrito; esta errata viaja com o pacote e corrige-o sem reabrir o mérito.

## O erro

O §3-P5 do RELATORIO (linhas 40–43) enumera só **2** das 3 permissões
"unexpected" da P3.30:

> contra a base falha por elementos "unexpected" da P3.30 (`protocol:
> transfer-any-unit`, `so:close-without-activities`)

Falta o terceiro nome: **`so:close-retroactive`**.

## A correcção

O conjunto exacto e completo dos elementos "unexpected" da P3.30 é:

1. `protocol:transfer-any-unit`
2. `so:close-without-activities`
3. **`so:close-retroactive`**

## Fontes (verificadas pelo produtor do lote R13-A em disco)

- `CONFIRMACAO-CORRECAO-CI-SECURITY-CATALOGO.md` §C5: enumera as 3 exactas
  — `protocol:transfer-any-unit`, `so:close-without-activities`,
  `so:close-retroactive` — "exactamente, e só, as 3 permissões da P3.30".
- `REVISAO-CORRECAO-CI-SECURITY-CATALOGO.md` §A5: as mesmas 3 exactas.
- `harness-logs/diff-catalogo.txt:6`: a permissão `so:close-retroactive`
  consta do diff base→fundido (linha da P3.30, presente na base e no
  fundido).

## Efeito

Nenhum. A substância do §3-P5 não muda: o espelho continua datado em
relação à main, a alegação de datamento continua confirmada nos elementos
(CONFIRMACAO §C5, REVISAO §A5) e nenhum placar, hash ou veredito se altera.
