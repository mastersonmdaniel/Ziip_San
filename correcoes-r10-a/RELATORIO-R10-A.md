# RELATÓRIO — Lote R10-A (auditoria contínua, Rodada 10, 09/11/2026)

- **Produtor do lote (sessão):** `f082f02f-6479-4505-a7e5-7f6783201066`
- **Fonte:** `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-09-0743/00-CONSOLIDADO-RODADA10.md` (§3, §6; relatórios 01–04 lidos antes de começar: `01-REVISOR-R9-A.md`, `02-REVISOR-R9-B.md`, `03-REVISOR-R9-C.md`, `04-REVISOR-M2-ADENDO-D.md`).
- **Pacote-alvo:** `~/workspace/ziipsan-producao/correcoes-r9-c/` (NÃO reescrito — só adendo; o único ficheiro alterado no alvo é o `LANDING.md`, na linha 28, por ordem expressa do item R10-C-02).
- **Data:** 09/11/2026
- **Trabalho:** 100% local; **zero chamadas ao GitHub** (parada dura por HTTP 429). `DECISOES-DO-DONO.md` e `portao-deploy.py` não editados.

## Veredito formal do lote (produção): CONCLUÍDO — 2/2 itens

- R10-C-01 (moderado): adendo datado ao `PORTAO-REGISTRO-R9-C.md`, com reprodução própria do teste de causalidade.
- R10-C-02 (menor): SHA do `LANDING.md` do R9-C preenchido na linha 28, após recomputação sobre o ficheiro actual.

Revisão e confirmação: em sessões distintas (a designar); portão mecânico após a confirmação.

## 1. R10-C-01 — a saída arquivada do portão do R9-C já não se reproduz

**Artefacto:** `correcoes-r9-c/ADENDO-R10-C-01-2026-11-09.md` — SHA-256 `736aaf734d6452604ef80afbd1c1f904cb5f9b1ab7e27b995eb31bb29810527b`.

**Reprodução própria do teste de causalidade (nada aceite por leitura):** o portão foi re-executado por mim sobre a pasta actual e sobre cópias em `/tmp` (originais intactos), com o verificador intacto (SHA-256 `05b58ba7378a2b8b3978feb2f7c5da240701a32c960b248fb3dc74352967cc16`, mtime 2026-11-09 00:14:29 UTC):

| Corrida | Estado da pasta | Exit | Resultado |
|---|---|---|---|
| (a) pasta actual `correcoes-r9-c/` | com consolidado | **0** | `PORTÃO: APROVADO`, Regras 1–6 [OK] |
| (b) cópia `/tmp/r10a-causal-sem/` | **sem** `RELATORIO-CONSOLIDADO-R9-C.md` | **1** | `[FALHA] REGRA 3`, `PORTÃO: BLOQUEADO` |
| (c) cópia `/tmp/r10a-causal-com/` | com consolidado | **0** | `PORTÃO: APROVADO`, Regras 1–6 [OK] |

- A saída de (b) é **byte-idêntica à arquivada** em `portao-saida.txt` (SHA-256 `e40419ee28ebe6db5f15e1fec8e448006236c78b3973cb1542b57f11f6d53fc4`), salvo a linha `Pacote:` (caminho diferente) — verificado por `diff` após normalização dessa linha.
- Saídas integrais das três corridas: `/tmp/r10a-portao-atual.txt` (SHA-256 `ac68ad0dbaaa68b04f7d3e1c645a6f9679918212706902d01284b798fc538148`), `/tmp/r10a-portao-sem.txt` (SHA-256 `c23a8f43af7f22a4a97ab11d6fdd32b8a5c7016d4c2daa3385e8e9afcbbf8701`), `/tmp/r10a-portao-com.txt` (SHA-256 `908e59316047715db91f78e25426d6f1fd5e5617f02b23d060cf35895a0311f4`).
- Cronologia (mtimes): `portao-saida.txt` 09:36:31 UTC → `PORTAO-REGISTRO-R9-C.md` 09:37:12 UTC → `RELATORIO-CONSOLIDADO-R9-C.md` 09:37:55 UTC — o consolidado foi escrito **depois** da corrida.
- Mecanismo verificado contra o código: `artefatos_raiz()` ordena alfabeticamente, logo `RELATORIO-CONSOLIDADO-R9-C.md` é lido primeiro pela Regra 3 (`portao-deploy.py:663-665`, `break` no primeiro produtor encontrado); chamada directa a `autor_do_cabecalho(texto, "produtor")` devolve `None` para `RELATORIO-R9-C.md` e `'a duplicação é linha a linha adjacente'` para o consolidado (linha 44: `- **Precisão acrescida pelo produtor:** a duplicação…` casa com `Produtor\s*:` no meio de prosa, nas primeiras 50 linhas).

**O que o adendo faz (e o que não faz):** declara que a saída arquivada corresponde ao estado pré-consolidado e que a re-execução pós-consolidado dá exit 0 pela sobre-captura; regista a instância (iv) remetendo para o rol acumulado canónico (`auditoria-continua/ESTADO.md:33`, já registado pela mesa da Rodada 10 — **nenhum registo duplicado feito neste lote**); inclui a guarda de leitura ("não ler o exit 0 de uma re-execução como portão APROVADO"). **Não** reescreve a saída arquivada **nem** reedita o consolidado. A substância do lote R9-C não muda (cadeia independente de facto; demonstração de FP do `PORTAO-REGISTRO-R9-C.md` §2 válida para o estado da corrida).

## 2. R10-C-02 — SHA do LANDING do R9-C por preencher

**Artefacto alterado:** `correcoes-r9-c/LANDING.md`, **linha 28 apenas** — o placeholder `a registar após o portão` foi substituído pelo valor verificado.

**Verificação antes do preenchimento:** SHA-256 recomputado por mim sobre o ficheiro actual, **antes** de editar: `c451fa9e833d2125fd78a8a398211881a8323366a8720d20842c56313c7c4066` — **exacto** face ao valor do brief e ao declarado no `RELATORIO-CONSOLIDADO-R9-C.md` (e conferido pelo revisor 3 da Rodada 10). Sem divergência; nenhum valor inventado.

**Depois do preenchimento:** SHA-256 novo do `LANDING.md`: `6145522c480ff0740a766c2debdc797ac7818dd4c13cde5cd715a485e9e875fb` (a mudança de hash é a esperada: exactamente 1 linha alterada; ficheiro com 44 linhas; 0 ocorrências restantes do placeholder; restantes hashes da tabela da cadeia intactos). O valor antigo (`c451fa9e…`) permanece como o hash do ficheiro pré-preenchimento, declarado no consolidado do R9-C e no relatório do revisor 3 — não é "perdido", é o estado anterior documentado.

## Cadeia do lote R10-A

| Artefacto | SHA-256 |
|---|---|
| `correcoes-r9-c/ADENDO-R10-C-01-2026-11-09.md` | `736aaf734d6452604ef80afbd1c1f904cb5f9b1ab7e27b995eb31bb29810527b` |
| `correcoes-r9-c/LANDING.md` (após R10-C-02) | `6145522c480ff0740a766c2debdc797ac7818dd4c13cde5cd715a485e9e875fb` |
| `correcoes-r9-c/LANDING.md` (antes de R10-C-02) | `c451fa9e833d2125fd78a8a398211881a8323366a8720d20842c56313c7c4066` |
| `correcoes-r9-c/PORTAO-REGISTRO-R9-C.md` (intacto) | `3d6cdf7ac686524760122560067ab77c3610b9f2439a29728c05d7e2e58110e3` |
| `correcoes-r9-c/portao-saida.txt` (intacto) | `e40419ee28ebe6db5f15e1fec8e448006236c78b3973cb1542b57f11f6d53fc4` |
| `correcoes-r9-c/RELATORIO-CONSOLIDADO-R9-C.md` (intacto) | `6c1905be5085cda8aa2e8579f8fe8a904e13dd5b4c67c9eea7435551e305c10c` |
| `correcoes-r9-c/RELATORIO-R9-C.md` (intacto) | `ab5c1aa9e61a4d5afc32c1e922ce6b7116d2f960a44deee73af924d1f1ca9fb1` |

## Portão mecânico (deste lote)

A correr após revisão e confirmação, por sessão distinta do produtor e do revisor, sobre `correcoes-r10-a/`; resultado a registar em `PORTAO-REGISTRO-R10-A.md` (neste lote) e no `LANDING.md` deste lote.

## Pendências que sobrevivem (fora do lote)

- **Limitação conhecida do extrator do portão** — instâncias acumuladas (i)–(iv); correcção só pelo rito próprio do portão. Este lote não a corrige; regista a (iv) por remissão ao rol canónico.
- **P-MM05-R8C-01** — bloqueio de pouso do MM-05 mantém-se; nada neste lote o toca.
- **C4-01** — decisão do dono pendente (Rodada 10, §5); não é condição de pouso da C4.
- Nada mais fica pendente deste lote.
