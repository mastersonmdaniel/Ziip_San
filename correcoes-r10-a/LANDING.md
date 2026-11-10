# LANDING — Lote R10-A (auditoria contínua, Rodada 10, 09/11/2026)

- **Coordenador do lote (sessão):** f082f02f-6479-4505-a7e5-7f6783201066
- **Produtor (sessão):** f082f02f-6479-4505-a7e5-7f6783201066
- **Revisor (sessão):** 0f0d6c28-d83f-447a-bf2b-8e0150f68ae5 — **APROVADO** (0 divergências)
- **Confirmador (sessão):** c8033855-eaa9-4277-b66f-91f017f694b9 — **CONFIRMADO COM RESSALVAS** (1 ressalva de redação, R-1, sem efeito na substância; 0 divergências materiais)
- **Data:** 09/11/2026

## Natureza do lote

Lote 100% documental. Dois itens da Rodada 10 sobre o lote R9-C, ambos por adendo/preenchimento datado, sem reescrita de originais:

| # | Item | Artefacto | SHA-256 |
|---|---|---|---|
| 1 | R10-C-01 | `correcoes-r9-c/ADENDO-R10-C-01-2026-11-09.md` | `736aaf734d6452604ef80afbd1c1f904cb5f9b1ab7e27b995eb31bb29810527b` |
| 2 | R10-C-02 | `correcoes-r9-c/LANDING.md` (linha 28 preenchida; SHA após o preenchimento) | `6145522c480ff0740a766c2debdc797ac7818dd4c13cde5cd715a485e9e875fb` |

Cadeia do lote (em `correcoes-r10-a/`):

| Artefacto | SHA-256 |
|---|---|
| `RELATORIO-R10-A.md` | `11f33ca8994786c32859913adfaf12a6eadbe6081d1fbbcb3c72acb35f2bac49` |
| `REVISAO-R10-A.md` | `cb94c3961eb1a5fb336281f3f909c41a0e79fb616defac3571a7232c63764bf0` |
| `CONFIRMACAO-R10-A.md` | `a8ea8b7751aef0bf14f227d5bd1387da75b59a3a0bb3fba337303bd1f3b87375` |
| `LANDING.md` (este ficheiro) | `61786ae8c43118d3ffd4fdf40c197e6025dd6d18d9ecb0dfe2fb83aca35ffab8` |

## Posição de pouso

Lote único, na fila pós-429, na posição que a sessão principal atribuir (depois dos lotes da base fictícia; ordem exacta a fixar pela principal — este lote não tem dependência de ordem com os demais lotes de correcção). O adendo R10-C-01 viaja com o pacote R9-C; o preenchimento R10-C-02 é parte do `LANDING.md` do R9-C.

## Guardas no acto do pouso

1. **Rebase NÃO verificado** — GitHub em parada dura por HTTP 429; rebase contra a main corrente é OBRIGATÓRIO no acto, antes de qualquer push.
2. **Verificação pós-pouso por hash** — cada ficheiro da tabela acima conferido por SHA-256 contra os valores declarados; divergência ⇒ pouso não confirmado. Para o `LANDING.md` do R9-C, o valor a conferir no pouso é o pós-preenchimento (`6145522c…`, item 2 da tabela); o valor pré-preenchimento (`c451fa9e…`) é o estado anterior documentado, não o valor a pousar.
3. **Nada disto pousa código de produção** — são adendo e preenchimento documental. Nenhum original foi reescrito (saída arquivada do portão do R9-C, consolidado do R9-C e registo do portão do R9-C intactos — hashes no `RELATORIO-R10-A.md`).
4. **P-MM05-R8C-01 mantém-se** — nada neste lote toca o MM-05.
5. **Leitura do portão do R9-C** — a evidência válida é a saída arquivada (exit 1) lida com o adendo R10-C-01; não ler o exit 0 de uma re-execução como "portão APROVADO".

## Estado do portão

Ver secção do `PORTAO-REGISTRO-R10-A.md` (saída mecânica executada sobre os artefactos finais, após confirmação).
