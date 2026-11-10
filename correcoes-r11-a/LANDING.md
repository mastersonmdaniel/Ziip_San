# LANDING — Lote R11-A (auditoria contínua, Rodada 11, 09/11/2026)

- **Coordenador do lote (sessão):** a990f294-beda-4991-a76b-17148cbfd9e6
- **Produtor (sessão):** d0db0ab0-2a43-46b8-96a5-3c1cef6e1a47
- **Revisor (sessão):** 443a1e1a-30ca-4be1-a2c1-578072355ecc — **APROVADO** (0 divergências)
- **Confirmador (sessão):** f3d47244-9701-46c9-bcd5-3f6e42028a79 — **CONFIRMADO** (0 divergências, 0 ressalvas)
- **Data:** 09/11/2026

## Natureza do lote

Lote 100% documental. Um item da Rodada 11 sobre o lote R10-A, por adendo datado, sem reescrita de originais:

| # | Item | Artefacto | SHA-256 |
|---|---|---|---|
| 1 | R11-EX-01 | `correcoes-r10-a/ADENDO-R11-EX-01-2026-11-09.md` | `5acad06f6728b0640394da56763199a01a9e6f6a9ba8880c7e9cc7fe941e7612` |

Cadeia do lote (em `correcoes-r11-a/`):

| Artefacto | SHA-256 |
|---|---|
| `RELATORIO-R11-A.md` | `a5cbce9cc08edc90d9691991a625f0d8abcfc1c082589c867de6df498f1e8da4` |
| `REVISAO-R11-A.md` | `06965e13bc46ce745f4e0a2e7e29cd2089b9cfc0818fde36306c680f478de55d` |
| `CONFIRMACAO-R11-A.md` | `67c089985d5c95b23bb2545fd9bb932ddf5350589bfb52f6ef35d1c9525dcfcd` |
| `LANDING.md` (este ficheiro) | `3f1c5308ab891f49307a37babb408c4146ac80d61f86e58653a9f76cb5a0a14c` |

## Posição de pouso

Lote único, na fila pós-429, na posição que a sessão principal atribuir (depois dos lotes da base fictícia; ordem exacta a fixar pela principal — este lote não tem dependência de ordem com os demais lotes de correcção). O adendo R11-EX-01 viaja com o pacote R10-A.

## Guardas no acto do pouso

1. **Rebase NÃO verificado** — GitHub em parada dura por HTTP 429; rebase contra a main corrente é OBRIGATÓRIO no acto, antes de qualquer push.
2. **Verificação pós-pouso por hash** — cada ficheiro da tabela acima conferido por SHA-256 contra os valores declarados; divergência ⇒ pouso não confirmado. Para o `LANDING.md` do R10-A, o valor a conferir no pouso é o actual (`574130a7…`, declarado no adendo R11-EX-01); o valor da sua tabela (`61786ae8…`) é o estado pré-preenchimento, não o valor a pousar. Para o `LANDING.md` deste lote (R11-A), o valor a conferir no pouso é o SHA actual registado no `RELATORIO-CONSOLIDADO-R11-A.md`; o valor da tabela acima é o estado pré-preenchimento, pelo mesmo método auto-referencial.
3. **Nada disto pousa código de produção** — é um adendo documental. Nenhum original foi reescrito (LANDING do R10-A e consolidado do R10-A intactos — hashes no `RELATORIO-R11-A.md`).
4. **P-MM05-R8C-01 mantém-se** — nada neste lote toca o MM-05.
5. **Leitura do portão do R10-A** — a evidência válida é a saída arquivada (exit 1) lida com o registo do lote; não ler o exit 0 de uma re-execução como "portão APROVADO".

## Estado do portão

Ver secção do `PORTAO-REGISTRO-R11-A.md` (saída mecânica executada sobre os artefactos finais, após confirmação).
