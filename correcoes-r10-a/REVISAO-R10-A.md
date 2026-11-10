# REVISÃO INDEPENDENTE — Lote R10-A (auditoria contínua, Rodada 10, 09/11/2026)

- **Revisor (sessão):** `0f0d6c28-d83f-447a-bf2b-8e0150f68ae5` (sessão distinta do produtor `f082f02f-6479-4505-a7e5-7f6783201066` e do futuro confirmador)
- **Escopo:** lote R10-A — R10-C-01 (moderado) + R10-C-02 (menor) sobre o pacote `correcoes-r9-c/`
- **Método:** SOMENTE LEITURA. Zero chamadas ao GitHub. Todos os hashes, exits, mtimes e contagens abaixo foram recomputados por mim em disco nesta sessão, por leitura própria dos ficheiros e re-execução própria do portão e do teste de causalidade em cópias frescas para `/tmp`; nenhum número do produtor foi aceite sem verificação própria. Placares lidos no log integral, nunca em saída filtrada (lição do R-01). O portão só lê ficheiros; nenhum artefacto do produtor nem do pacote-alvo foi editado por mim.
- **Fonte do repasse:** `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-09-0743/00-CONSOLIDADO-RODADA10.md` (§3, §6) e `03-REVISOR-R9-C.md` (§7) — ambos lidos antes de começar.
- **Data:** 09/11/2026

## Veredito geral

**Lote R10-A — APROVADO. 0 divergências.**

Os dois itens estão feitos como o repasse pedia, com a evidência reproduzida por mim do zero: o adendo R10-C-01 declara correctamente o estado pré-consolidado da saída arquivada, com o teste de causalidade reproduzido nas minhas próprias cópias e o mecanismo confirmado contra o código do portão; o preenchimento R10-C-02 usa exactamente o SHA pré-preenchimento do ficheiro, provado por cópias pré-edição, e só a linha 28 mudou. Nenhum original foi reescrito; o portão não foi editado; nenhum registo duplicado foi feito no ESTADO. Não há ressalvas.

## 1. R10-C-01 — adendo ao registo do portão do R9-C — APROVADO

Evidência recomputada por mim:

- **Adendo existe e tem o hash declarado.** `correcoes-r9-c/ADENDO-R10-C-01-2026-11-09.md`, SHA-256 `736aaf734d6452604ef80afbd1c1f904cb5f9b1ab7e27b995eb31bb29810527b` — confere. Conteúdo lido na íntegra: declara que a saída arquivada corresponde ao estado pré-consolidado, que a re-execução pós-consolidado dá exit 0 por sobre-captura, remete a instância (iv) para `ESTADO.md:33` sem a re-registar, inclui a guarda de leitura ("não ler o exit 0 de uma re-execução como portão APROVADO") e declara que não reescreve a saída nem o consolidado. As citações de SHA no adendo (registo `3d6cdf7a…`, saída `e40419ee…`, portão `05b58ba7…`) conferem com os ficheiros.
- **Re-execução própria sobre a pasta actual: exit 0.** Corri `python3 esteira/portao-deploy.py correcoes-r9-c/` a partir de `~/workspace/ziipsan-producao/`: **exit 0**, `PORTÃO: APROVADO`, Regras 1–6 `[OK]`. Saída guardada em `/tmp/r10a-rev-atual.txt`, SHA-256 `ac68ad0dbaaa68b04f7d3e1c645a6f9679918212706902d01284b798fc538148` — **byte-idêntica** à saída do produtor (`/tmp/r10a-portao-atual.txt`, mesmo SHA). Esta corrida foi feita **depois** da escrita do adendo (10:53:07) e dá a mesma saída da corrida do produtor (10:52, antes do adendo): prova empírica de que o adendo não afecta o glob do portão, como o adendo §5 afirma.
- **Teste de causalidade reproduzido por mim, em cópias frescas** (`/tmp/r10a-rev-sem/`, `/tmp/r10a-rev-com/`, originais intactos):

  | Corrida | Estado da pasta | Exit | Resultado |
  |---|---|---|---|
  | sem consolidado | sem `RELATORIO-CONSOLIDADO-R9-C.md` | **1** | `[FALHA] REGRA 3`, `PORTÃO: BLOQUEADO` |
  | com consolidado | com consolidado (+ adendo) | **0** | `PORTÃO: APROVADO`, Regras 1–6 `[OK]` |

  - A minha saída "sem", após normalização só da linha `Pacote:` (caminho diferente), é **byte-idêntica à arquivada** em `portao-saida.txt` — verificado por `cmp`. A saída arquivada é, portanto, exactamente a saída do estado pré-consolidado.
  - As minhas saídas "sem" e "com", após a mesma normalização, são **byte-idênticas às do produtor** (`/tmp/r10a-portao-sem.txt`, `/tmp/r10a-portao-com.txt`).
  - As três saídas `/tmp` do produtor **ainda existem** e os seus hashes conferem: actual `ac68ad0d…`, sem `c23a8f43af7f22a4a97ab11d6fdd32b8a5c7016d4c2daa3385e8e9afcbbf8701`, com `908e59316047715db91f78e25426d6f1fd5e5617f02b23d060cf35895a0311f4`.
  - O diff da minha corrida actual contra a arquivada difere só na Regra 3 e na linha final (APROVADO vs BLOQUEADO + 1 item) — exacto.
- **Mecanismo confirmado contra o código, não só por leitura.** Chamada directa a `autor_do_cabecalho(texto, "produtor")` do próprio verificador, nesta sessão: `RELATORIO-R9-C.md` → `None`; `RELATORIO-CONSOLIDADO-R9-C.md` → `'a duplicação é linha a linha adjacente'` — exacto como o produtor declara. Linhas citadas conferidas: `PADROES_AUTOR["produtor"]` (`portao-deploy.py:228-230`, `Produtor\s*:` casa no meio de prosa); `autor_do_cabecalho` (`:447-466`, primeiras 50 linhas, corte na primeira vírgula/parêntese); iteração da Regra 3 com `break` no primeiro produtor (`:663-665`); `artefatos_raiz()` ordena (`sorted(os.listdir(...))`, `:280`) e o glob de relatórios é `startswith("RELATORIO")` (`:285`), logo o consolidado precede `RELATORIO-R9-C.md`. A linha 44 do consolidado é exactamente `- **Precisão acrescida pelo produtor:** a duplicação é linha a linha adjacente (A A B B), não repetição de bloco…` — conferida por leitura directa.
- **Cronologia (mtimes com segundos, UTC):** `portao-saida.txt` 09:36:31.153 → `PORTAO-REGISTRO-R9-C.md` 09:37:12.881 → `RELATORIO-CONSOLIDADO-R9-C.md` 09:37:55.923 — o consolidado foi escrito depois da corrida, como declarado.
- **Portão não editado.** `esteira/portao-deploy.py`, SHA-256 `05b58ba7378a2b8b3978feb2f7c5da240701a32c960b248fb3dc74352967cc16`, mtime 2026-11-09 00:14:29.905 UTC — confere.
- **Saída arquivada e consolidado NÃO reescritos.** `portao-saida.txt` SHA-256 `e40419ee28ebe6db5f15e1fec8e448006236c78b3973cb1542b57f11f6d53fc4`; `RELATORIO-CONSOLIDADO-R9-C.md` SHA-256 `6c1905be5085cda8aa2e8579f8fe8a904e13dd5b4c67c9eea7435551e305c10c`; `PORTAO-REGISTRO-R9-C.md` SHA-256 `3d6cdf7ac686524760122560067ab77c3610b9f2439a29728c05d7e2e58110e3` — todos conferem.
- **Rol acumulado: instância (iv) já registada, sem duplicado.** `ESTADO.md:33` traz a instância (iv) ("sobre-captura de prosa — `PADROES_AUTOR[\"produtor\"]` casa \"produtor:\" no meio de prosa do consolidado…"). O `ESTADO.md` tem mtime 2026-11-09 10:50:57 UTC — **antes** do trabalho deste lote (saídas `/tmp` do produtor 10:52, adendo 10:53:07, LANDING 10:53:10). O produtor não tocou o ESTADO; a remissão do adendo é correcta e não há registo duplicado.

## 2. R10-C-02 — SHA do LANDING do R9-C preenchido — APROVADO

Evidência recomputada por mim:

- **O valor preenchido é o SHA do ficheiro ANTES do preenchimento.** As cópias `/tmp` do produtor (`/tmp/r10a-causal-sem/LANDING.md` e `/tmp/r10a-causal-com/LANDING.md`, feitas às 10:52, antes da edição das 10:53:10) têm SHA-256 `c451fa9e833d2125fd78a8a398211881a8323366a8720d20842c56313c7c4066` — **exacto** face ao valor preenchido, ao declarado no `RELATORIO-CONSOLIDADO-R9-C.md` (1 ocorrência do valor, conferida por `grep`) e ao registado pelo revisor 3 da Rodada 10. Nenhum valor inventado.
- **Depois do preenchimento:** `correcoes-r9-c/LANDING.md` SHA-256 `6145522c480ff0740a766c2debdc797ac7818dd4c13cde5cd715a485e9e875fb` — confere. Ficheiro com **44 linhas**; **0 ocorrências** restantes de "a registar após o portão"; a linha 28 é agora `| \`LANDING.md\` (este ficheiro) | \`c451fa9e833d2125fd78a8a398211881a8323366a8720d20842c56313c7c4066\` |` — conferida por leitura directa.
- **Só a linha 28 mudou.** Comparação byte a byte entre a cópia pré-edição e o ficheiro actual: **44/44 linhas, só a 28 difere**; restantes hashes da tabela da cadeia intactos e iguais aos registados pelo revisor 3 (§6): itens `45cb3ee6…`, `ba241c13…`, `2e3c8664…`, `09e43308…`, `65875c71…`; cadeia `ab5c1aa9…`, `096c474e…`, `1ba202ea…`.
- **Nada mais no pacote-alvo foi alterado.** Os 9 ficheiros de `correcoes-r9-c/` com hashes/mtimes conferidos contra a tabela do revisor 3 (§6) e a tabela da cadeia do produtor — todos exactos (ver §3). Os únicos ficheiros com mtime de hoje após as 09:37 são o adendo (10:53:07) e o LANDING (10:53:10), ambos deste lote.

## 3. Hashes da cadeia — todos recalculados por mim (9/9 do pacote-alvo + 2 do lote)

| Artefacto | SHA-256 | Estado |
|---|---|---|
| `correcoes-r9-c/ADENDO-R10-C-01-2026-11-09.md` | `736aaf734d6452604ef80afbd1c1f904cb5f9b1ab7e27b995eb31bb29810527b` | confere |
| `correcoes-r9-c/LANDING.md` (após R10-C-02) | `6145522c480ff0740a766c2debdc797ac7818dd4c13cde5cd715a485e9e875fb` | confere |
| `correcoes-r9-c/LANDING.md` (antes de R10-C-02, cópias `/tmp` 10:52) | `c451fa9e833d2125fd78a8a398211881a8323366a8720d20842c56313c7c4066` | confere |
| `correcoes-r9-c/PORTAO-REGISTRO-R9-C.md` (intacto) | `3d6cdf7ac686524760122560067ab77c3610b9f2439a29728c05d7e2e58110e3` | confere |
| `correcoes-r9-c/portao-saida.txt` (intacto) | `e40419ee28ebe6db5f15e1fec8e448006236c78b3973cb1542b57f11f6d53fc4` | confere |
| `correcoes-r9-c/RELATORIO-CONSOLIDADO-R9-C.md` (intacto) | `6c1905be5085cda8aa2e8579f8fe8a904e13dd5b4c67c9eea7435551e305c10c` | confere |
| `correcoes-r9-c/RELATORIO-R9-C.md` (intacto) | `ab5c1aa9e61a4d5afc32c1e922ce6b7116d2f960a44deee73af924d1f1ca9fb1` | confere |
| `correcoes-r9-c/REVISAO-R9-C.md` (intacto) | `096c474e535c9fd82921a2cf88aa6b29814c35a8a1c7b53a3f308d3ab6b76411` | confere |
| `correcoes-r9-c/CONFIRMACAO-R9-C.md` (intacto) | `1ba202eafda6c4f49f7aec1421db5aabe00ef71bae66cf74b3957b9d10d6d501` | confere |
| `correcoes-r9-c/REGISTRO-R9-P2-2026-11-09.md` (intacto) | `09e433081dd3a437e6277105c2875dfbba57b5cf833f5e1fd0b4e5256b13d8ba` | confere |
| `correcoes-r10-a/RELATORIO-R10-A.md` (produtor) | `11f33ca8994786c32859913adfaf12a6eadbe6081d1fbbcb3c72acb35f2bac49` | registado |
| `correcoes-r10-a/LANDING.md` (lote) | `6493ddff458b8ffe2b58972a9e6bec8895ed323f5322084879a8a458ebc80e93` | registado |

## 4. Verificação do RELATORIO-R10-A.md do produtor

Cada hash, exit e afirmação factual foi verificado por mim, não aceite por leitura:

- Tabela da cadeia (§ "Cadeia do lote R10-A"): **7/7 conferem** (ver §3).
- Exits das três corridas: **reproduzidos por mim** (actual 0; sem 1; com 0) em cópias frescas, não nas do produtor.
- Saídas `/tmp` citadas: os três ficheiros **ainda existem** e os hashes conferem (`ac68ad0d…`, `c23a8f43…`, `908e5931…`); as minhas corridas reproduzem-nas byte a byte (salvo a linha `Pacote:`, por caminho diferente).
- Mtimes citados (09:36:31 → 09:37:12 → 09:37:55 UTC): **conferem com precisão de segundos** (`stat`).
- SHA e mtime do portão (`05b58ba7…`, 00:14:29 UTC): **conferem**.
- Chamada directa a `autor_do_cabecalho`: **reproduzida** (`None` para `RELATORIO-R9-C.md`; `'a duplicação é linha a linha adjacente'` para o consolidado).
- Afirmação "nenhum registo duplicado neste lote" (ESTADO): **confere** — `ESTADO.md:33` tem a (iv) e o mtime do ficheiro (10:50:57 UTC) é anterior ao lote.
- Afirmação "só a linha 28 mudou / 44 linhas / 0 ocorrências do placeholder": **confere** (comparação byte a byte contra a cópia pré-edição).
- Sessão do produtor `f082f02f-6479-4505-a7e5-7f6783201066` consistente no `RELATORIO-R10-A.md`, no `LANDING.md` do lote e no adendo — distinta da minha sessão de revisor e das sessões da cadeia do R9-C (`4e92c4e6…`, `6b58596e…`, `9f7c05fb…`).

## 5. Divergências encontradas

**0 divergências.**

## 6. Pendências

- **Portão mecânico deste lote (R10-A):** a correr após a confirmação, por sessão distinta do produtor e do revisor, sobre `correcoes-r10-a/` — fora do escopo desta revisão.
- **Limitação conhecida do extrator do portão (i)–(iv):** persiste; correcção só pelo rito próprio do portão. Este lote não a corrige; regista a (iv) por remissão — correcto.
- **P-MM05-R8C-01** — bloqueio de pouso do MM-05 mantém-se; nada neste lote o toca.
- **C4-01** — decisão do dono pendente (Rodada 10, §5); não é condição de pouso da C4.
- Nada mais fica pendente desta revisão.
