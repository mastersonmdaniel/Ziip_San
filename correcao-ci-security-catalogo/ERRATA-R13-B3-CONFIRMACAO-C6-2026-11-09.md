# Errata R13-B3 — `CONFIRMACAO-CORRECAO-CI-SECURITY-CATALOGO.md` §C6

- **Data:** 2026-11-09 (escrita 17:02 UTC; 14:02 −03).
- **Produtor do lote R13-A (auditoria contínua, Rodada 13):** sessão `1c342896-6c61-42fb-b42a-c8eaeef0de4a`.
- **Pacote:** `correcao-ci-security-catalogo`.
- **Natureza:** errata de uma frase ao §C6 da CONFIRMACAO. O ficheiro
  original NÃO foi reescrito; esta errata viaja com o pacote e restringe
  a frase sem reabrir o mérito.

## O erro

A primeira frase do §C6 é demasiado ampla:

> "nenhum ficheiro do pacote tem mtime posterior ao da `REVISAO`
> (2026-11-09 16:09:55 UTC)"

Por desenho, os artefactos da cadeia posterior à REVISAO têm mtime
posterior a ela. Medido pelo produtor do lote R13-A (stat, UTC):

| Ficheiro | mtime (UTC) |
|---|---|
| `REVISAO-CORRECAO-CI-SECURITY-CATALOGO.md` | 2026-11-09 16:09:55.99 |
| `CONFIRMACAO-CORRECAO-CI-SECURITY-CATALOGO.md` | 2026-11-09 16:11:55.76 |
| `LANDING.md` | 2026-11-09 16:12:14.21 |
| `PORTAO-REGISTRO-CORRECAO-CI-SECURITY-CATALOGO.md` | 2026-11-09 16:12:47.91 |
| `INSTRUCOES-PARCHE-SECURITY-CATALOGO.txt` | 2026-11-09 16:12:47.93 |

## A correcção

A frase lê-se, com a restrição correcta:

> "nenhum **ficheiro do produtor** tem mtime posterior ao da `REVISAO`
> (2026-11-09 16:09:55 UTC)"

Os ficheiros do produtor datam de **16:05:04–16:07:47 UTC**, como o
próprio §C6 já diz na frase seguinte — confirmado pelo produtor do lote
R13-A: `trabalho/base/PermissionCatalog.java` 16:05:04.08,
`entrega/…/PermissionCatalog.java` 16:05:04.08,
`harness-logs/diff-catalogo.txt` 16:05:04.09,
`harness-logs/sha256-base.txt` 16:05:04.09,
`harness-logs/sha256-entrega.txt` 16:05:04.09,
`harness/run-provas.sh` 16:07:13.39, `harness-logs/provas.log`
16:07:26.40, `DIAGNOSTICO-ACHADO-4.md` 16:07:47.36,
`RELATORIO-CORRECAO-CI-SECURITY-CATALOGO.md` 16:07:47.36.
(Excluídas as classes compiladas `harness/classes-*`, artefactos de
build, como o próprio §C6 já exclui.)

## Efeito

Nenhum. A substância do §C6 não se altera: o conteúdo revisto é o
conteúdo confirmado, provado pelo hash em C1 — o hash hoje medido do
ficheiro da entrega (`42a305ee0e15…`) é o mesmo registado no relatório,
na revisão e em `harness-logs/sha256-entrega.txt`. O veredito
CONFIRMADO mantém-se.
