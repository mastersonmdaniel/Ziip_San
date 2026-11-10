# CONFIRMAÇÃO INDEPENDENTE — Lote R10-A (auditoria contínua, Rodada 10, 09/11/2026)

- **Confirmador (sessão):** `c8033855-eaa9-4277-b66f-91f017f694b9` (sessão distinta do produtor `f082f02f-6479-4505-a7e5-7f6783201066` e do revisor `0f0d6c28-d83f-447a-bf2b-8e0150f68ae5`)
- **Escopo:** lote R10-A — R10-C-01 (moderado) + R10-C-02 (menor) sobre o pacote `correcoes-r9-c/`
- **Método:** SOMENTE LEITURA sobre produção e revisão, mais a escrita deste ficheiro. **Zero chamadas ao GitHub** (parada dura por HTTP 429; nenhuma operação remota executada nesta sessão — só `sha256sum`, `stat`, `diff`/`cmp`, `grep` e re-execução local do portão, que só lê ficheiros). Todos os hashes, exits, mtimes e contagens abaixo foram recomputados por mim em disco nesta sessão, por leitura própria dos ficheiros e re-execução própria do portão e do teste de causalidade em cópias frescas minhas para `/tmp` (`/tmp/r10a-conf-*`, originais intactos); nenhum número do produtor nem do revisor foi aceite sem verificação própria. Placares lidos no log integral, nunca em saída filtrada (lição do R-01) — as saídas do portão aqui têm 15–16 linhas e foram lidas na íntegra (ver §1).
- **Fonte do repasse:** `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-09-0743/00-CONSOLIDADO-RODADA10.md` (§3, §6) e `03-REVISOR-R9-C.md` (§7) — ambos lidos antes de começar.
- **Data:** 09/11/2026

## Veredito formal

**Lote R10-A — CONFIRMADO COM RESSALVAS (1 ressalva, de redação, sem efeito na substância — ver §4, R-1).**

Os dois itens estão feitos como o repasse pedia, com a evidência reproduzida por mim do zero: o adendo R10-C-01 declara correctamente o estado pré-consolidado da saída arquivada, com o teste de causalidade reproduzido nas minhas próprias cópias e o mecanismo confirmado por chamada directa ao extrator do portão; o preenchimento R10-C-02 usa exactamente o SHA pré-preenchimento do ficheiro, provado pelas cópias pré-edição do produtor (ainda existentes) e pelo consolidado, e só a linha 28 mudou. Nenhum original foi reescrito; o portão não foi editado; nenhum registo duplicado foi feito no ESTADO; o adendo não afecta o glob do portão (provado empiricamente, ver §1). O veredito do revisor (APROVADO, 0 divergências) sustenta-se em substância; a única divergência que encontrei é uma imprecisão de redação na frase dele sobre identidade byte a byte (R-1), que não altera nenhum facto do lote.

## 1. R10-C-01 — adendo ao registo do portão do R9-C — CONFIRMADO

Evidência recomputada por mim:

- **Adendo existe e tem o hash declarado.** `correcoes-r9-c/ADENDO-R10-C-01-2026-11-09.md`, SHA-256 `736aaf734d6452604ef80afbd1c1f904cb5f9b1ab7e27b995eb31bb29810527b` — confere. Conteúdo lido na íntegra: declara que a saída arquivada corresponde ao estado pré-consolidado, que a re-execução pós-consolidado dá exit 0 por sobre-captura, remete a instância (iv) para `ESTADO.md:33` sem a re-registar, inclui a guarda de leitura ("não ler o exit 0 de uma re-execução como portão APROVADO") e declara que não reescreve a saída nem o consolidado. As citações de SHA no adendo conferem com os ficheiros: registo `3d6cdf7a…`, saída `e40419ee…`, portão `05b58ba7…`, e as três saídas `/tmp` do produtor (`ac68ad0d…`, `c23a8f43…`, `908e5931…`) — os três ficheiros ainda existem e os hashes conferem.
- **Re-execução própria sobre a pasta actual: exit 0.** Corri `python3 esteira/portao-deploy.py correcoes-r9-c/` a partir de `~/workspace/ziipsan-producao/`: **exit 0**, `PORTÃO: APROVADO`, Regras 1–6 `[OK]`. Saída integral guardada em `/tmp/r10a-conf-atual.txt` (15 linhas, lidas na íntegra), SHA-256 `ac68ad0dbaaa68b04f7d3e1c645a6f9679918212706902d01284b798fc538148` — **byte-idêntica** à saída do produtor (`/tmp/r10a-portao-atual.txt`, mesmo SHA, mesmo argumento `Pacote:`). Esta corrida foi feita **depois** da escrita do adendo (10:53:07) e dá a mesma saída da corrida do produtor (10:52, antes do adendo): prova empírica de que o adendo não afecta o glob do portão (ver também o ponto seguinte).
- **Teste de causalidade reproduzido por mim, em cópias frescas** (`/tmp/r10a-conf-sem/`, `/tmp/r10a-conf-com/`, originais intactos):

  | Corrida | Estado da pasta | Exit | Resultado |
  |---|---|---|---|
  | sem consolidado | sem `RELATORIO-CONSOLIDADO-R9-C.md` | **1** | `[FALHA] REGRA 3`, `PORTÃO: BLOQUEADO` |
  | com consolidado | com consolidado (+ adendo) | **0** | `PORTÃO: APROVADO`, Regras 1–6 `[OK]` |

  - A minha saída "sem" (`/tmp/r10a-conf-sem.txt`, 16 linhas, lidas na íntegra), após normalização só da linha `Pacote:` (caminho diferente), é **byte-idêntica à arquivada** em `portao-saida.txt` — verificado por `diff` e por `cmp` (CMP-OK). A saída arquivada é, portanto, exactamente a saída do estado pré-consolidado.
  - A minha saída "com", após a mesma normalização, é **byte-idêntica à do produtor** (`/tmp/r10a-portao-com.txt`); a minha saída actual é byte-idêntica à do produtor sem qualquer normalização.
  - A minha cópia "com" **inclui o adendo** (copiada da pasta actual, após 10:53:07) e a cópia "com" do produtor (10:52) **não o incluía**; ambas dão a mesma saída normalizada — segunda prova empírica de que o adendo não afecta o glob do portão, como o adendo §5 afirma.
  - O diff da minha corrida actual contra a arquivada difere só na Regra 3 e na linha final (APROVADO vs BLOQUEADO + 1 item) — exacto.
- **Mecanismo confirmado contra o código, por chamada directa.** Chamada directa a `autor_do_cabecalho(texto, "produtor")` do próprio verificador, nesta sessão: `RELATORIO-R9-C.md` → `None`; `RELATORIO-CONSOLIDADO-R9-C.md` → `'a duplicação é linha a linha adjacente'` — exacto como o produtor declara. Linhas citadas conferidas: `PADROES_AUTOR["produtor"]` (`portao-deploy.py:228-230`, o padrão casa `Produtor\s*:` no meio de prosa); `autor_do_cabecalho` (`:447-466`, primeiras 50 linhas, corte na primeira vírgula/parêntese); iteração da Regra 3 com `break` no primeiro produtor (`:663-665`); `artefatos_raiz()` ordena (`sorted(os.listdir(...))`, `:280`) e o glob de relatórios é `startswith("RELATORIO")` (`:285`), logo o consolidado precede `RELATORIO-R9-C.md`. A linha 44 do consolidado é exactamente `- **Precisão acrescida pelo produtor:** a duplicação é linha a linha adjacente (A A B B), não repetição de bloco…` — conferida por leitura directa. (Chamadas para "revisor"/"confirmador" sobre ambos os ficheiros devolvem `None`, coerente com os rótulos com qualificador da classe (ii).)
- **Cronologia (mtimes com segundos, UTC):** `portao-saida.txt` 09:36:31.153 → `PORTAO-REGISTRO-R9-C.md` 09:37:12.881 → `RELATORIO-CONSOLIDADO-R9-C.md` 09:37:55.923 — o consolidado foi escrito depois da corrida, como declarado.
- **Portão não editado.** `esteira/portao-deploy.py`, SHA-256 `05b58ba7378a2b8b3978feb2f7c5da240701a32c960b248fb3dc74352967cc16`, mtime 2026-11-09 00:14:29.905 UTC — confere.
- **Saída arquivada, consolidado e registo NÃO reescritos.** `portao-saida.txt` SHA-256 `e40419ee28ebe6db5f15e1fec8e448006236c78b3973cb1542b57f11f6d53fc4`; `RELATORIO-CONSOLIDADO-R9-C.md` SHA-256 `6c1905be5085cda8aa2e8579f8fe8a904e13dd5b4c67c9eea7435551e305c10c`; `PORTAO-REGISTRO-R9-C.md` SHA-256 `3d6cdf7ac686524760122560067ab77c3610b9f2439a29728c05d7e2e58110e3` — todos conferem.
- **Rol acumulado: instância (iv) já registada, sem duplicado.** `ESTADO.md:33` traz a instância (iv) ("sobre-captura de prosa — `PADROES_AUTOR[\"produtor\"]` casa \"produtor:\" no meio de prosa do consolidado…"). O `ESTADO.md` tem mtime 2026-11-09 10:50:57.687 UTC — **antes** do trabalho deste lote (saídas `/tmp` do produtor 10:52:10–10:52:14, adendo 10:53:07.780, LANDING 10:53:10.544). O produtor não tocou o ESTADO; a remissão do adendo é correcta e não há registo duplicado.
- **Adendo não afecta o glob do portão (código + empiria).** O nome `ADENDO-R10-C-01-2026-11-09.md` não casa com `RELATORIO*`, `REVISAO*` nem `CONFIRMACAO*` e não é `LANDING.md` (verificado por `fnmatch`/`startswith`); o glob de relatórios do portão é `startswith("RELATORIO")` (`:285`). Empiricamente: as minhas corridas actual e "com", ambas com o adendo presente, reproduzem byte a byte (salvo `Pacote:`) as corridas do produtor feitas antes do adendo existir.

## 2. R10-C-02 — SHA do LANDING do R9-C preenchido — CONFIRMADO

Evidência recomputada por mim:

- **O valor preenchido é o SHA do ficheiro ANTES do preenchimento.** As cópias `/tmp` do produtor (`/tmp/r10a-causal-sem/LANDING.md` e `/tmp/r10a-causal-com/LANDING.md`, feitas às 10:52:14, antes da edição das 10:53:10) **ainda existem** e têm SHA-256 `c451fa9e833d2125fd78a8a398211881a8323366a8720d20842c56313c7c4066` — **exacto** face ao valor preenchido, ao declarado no `RELATORIO-CONSOLIDADO-R9-C.md` (1 ocorrência do valor, conferida por `grep`, na linha 22) e ao registado pelo revisor 3 da Rodada 10. Nenhum valor inventado.
- **Depois do preenchimento:** `correcoes-r9-c/LANDING.md` SHA-256 `6145522c480ff0740a766c2debdc797ac7818dd4c13cde5cd715a485e9e875fb` — confere. Ficheiro com **44 linhas**; **0 ocorrências** restantes de "a registar após o portão"; a linha 28 é agora `| \`LANDING.md\` (este ficheiro) | \`c451fa9e833d2125fd78a8a398211881a8323366a8720d20842c56313c7c4066\` |` — conferida por leitura directa.
- **Só a linha 28 mudou.** Comparação byte a byte (`diff`) entre cada cópia pré-edição e o ficheiro actual: **44/44 linhas, só a 28 difere** (`28c28`, placeholder → valor com backticks); restantes hashes da tabela da cadeia intactos e iguais aos registados pelo revisor 3 (§6): itens `45cb3ee6…`, `ba241c13…`, `2e3c8664…`, `09e43308…`, `65875c71…`; cadeia `ab5c1aa9…`, `096c474e…`, `1ba202ea…`.
- **Nada mais no pacote-alvo foi alterado.** Os 9 ficheiros de `correcoes-r9-c/` com hashes/mtimes conferidos contra a tabela do revisor 3 (§6) e a tabela da cadeia do produtor — todos exactos (ver §3). Os únicos ficheiros com mtime de hoje após as 09:37 são o adendo (10:53:07.780) e o LANDING (10:53:10.544), ambos deste lote.

## 3. Hashes da cadeia — todos recalculados por mim (10/10 do pacote-alvo + 3 do lote)

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
| `correcoes-r10-a/REVISAO-R10-A.md` (revisor) | `cb94c3961eb1a5fb336281f3f909c41a0e79fb616defac3571a7232c63764bf0` | registado |
| `correcoes-r10-a/LANDING.md` (lote) | `6493ddff458b8ffe2b58972a9e6bec8895ed323f5322084879a8a458ebc80e93` | registado |

Tabela da cadeia do produtor (`RELATORIO-R10-A.md`): **7/7 conferem**. Tabela do revisor (§3): **10/10 do pacote-alvo + 2 do lote conferem** (a 3.ª linha da tabela dele, o LANDING pré-preenchimento, confere via as cópias `/tmp` acima).

## 4. Verificação do RELATORIO-R10-A.md do produtor e da REVISAO-R10-A.md do revisor

Cada hash, exit e afirmação factual foi verificado por mim, não aceite por leitura:

- Exits das três corridas: **reproduzidos por mim** (actual 0; sem 1; com 0) em cópias frescas minhas, não nas do produtor nem nas do revisor.
- Saídas `/tmp` citadas pelo produtor: os três ficheiros **ainda existem** e os hashes conferem (`ac68ad0d…`, `c23a8f43…`, `908e5931…`); as minhas corridas reproduzem-nas byte a byte (salvo a linha `Pacote:`, por caminho diferente).
- Saídas `/tmp` do revisor: `/tmp/r10a-rev-atual.txt` (mtime 10:54:32) tem SHA `ac68ad0d…` — byte-idêntica à do produtor; `/tmp/r10a-rev-sem.txt` e `/tmp/r10a-rev-com.txt` (10:54:45) diferem das do produtor só na linha `Pacote:` (caminho diferente) — após normalização dessa linha, a "sem" do revisor é byte-idêntica à arquivada e a "com" é byte-idêntica à do produtor (verificado por `diff` após normalização).
- Mtimes citados (09:36:31 → 09:37:12 → 09:37:55 UTC): **conferem com precisão de segundos** (`stat`, com milissegundos: .153 / .881 / .923).
- SHA e mtime do portão (`05b58ba7…`, 00:14:29 UTC): **conferem** (mtime 00:14:29.905 UTC).
- Chamada directa a `autor_do_cabecalho`: **reproduzida** (`None` para `RELATORIO-R9-C.md`; `'a duplicação é linha a linha adjacente'` para o consolidado).
- Afirmação "nenhum registo duplicado neste lote" (ESTADO): **confere** — `ESTADO.md:33` tem a (iv) e o mtime do ficheiro (10:50:57.687 UTC) é anterior ao lote.
- Afirmação "só a linha 28 mudou / 44 linhas / 0 ocorrências do placeholder": **confere** (comparação byte a byte contra as duas cópias pré-edição).
- **Cadeia de sessões (confirmada).** Produtor `f082f02f-6479-4505-a7e5-7f6783201066` (em `RELATORIO-R10-A.md:3`, `LANDING.md` do lote e no adendo) ≠ revisor `0f0d6c28-d83f-447a-bf2b-8e0150f68ae5` (em `REVISAO-R10-A.md:3`) ≠ confirmador `c8033855-eaa9-4277-b66f-91f017f694b9` (esta sessão, neste ficheiro) — três sessões distintas, cruzadas nos artefactos; todas distintas também das sessões da cadeia do R9-C (`4e92c4e6…`, `6b58596e…`, `9f7c05fb…`).
- **Veredito do revisor sustenta-se em substância.** APROVADO, 0 divergências materiais — concordo, com a única ressalva de redação abaixo.

## 5. Divergências encontradas

**R-1 (ressalva única — redação do revisor, sem efeito na substância).** Em `REVISAO-R10-A.md` §1, o revisor escreve que as suas saídas "sem" e "com" são "byte-idênticas às do produtor" (`/tmp/r10a-portao-sem.txt`, `/tmp/r10a-portao-com.txt`), sem a qualificação que ele próprio usa para a saída "sem" vs a arquivada ("após normalização só da linha `Pacote:`"). Em bruto, as saídas dele **não** são byte-idênticas às do produtor: os ficheiros brutos diferem na linha `Pacote:` (caminho `/tmp/r10a-rev-*` vs `/tmp/r10a-causal-*`), como é inevitável — os hashes brutos são `b2f25e2f…` (sem) e `512da5d1…` (com) contra `c23a8f43…` e `908e5931…`. A identidade **procede após normalização só da linha `Pacote:`**, o que verifiquei por `diff` nas duas corridas (e a saída "sem" normalizada do revisor é também byte-idêntica à arquivada, como ele declara). O produtor, no seu relatório, qualificou correctamente ("salvo a linha `Pacote:`"). Nenhum facto do lote muda; nenhuma re-execução, hash ou veredito é afectado. Registo só para exactidão — a frase do revisor deve ler-se com essa qualificação implícita.

**0 divergências materiais. 0 divergências de substância.**

## 6. Pendências

- **Portão mecânico deste lote (R10-A):** a correr após esta confirmação, por sessão distinta do produtor e do revisor, sobre `correcoes-r10-a/` — fora do escopo desta confirmação. Até lá, o `LANDING.md` do lote mantém "a designar" (revisor/confirmador) e "a registar após revisão/confirmação" nas suas tabelas — esperado, a preencher no portão do lote (não é divergência: o `LANDING.md` do lote tem SHA `6493ddff…` registado pelo revisor e conferido por mim).
- **Limitação conhecida do extrator do portão (i)–(iv):** persiste; correcção só pelo rito próprio do portão. Este lote não a corrige; regista a (iv) por remissão — correcto.
- **P-MM05-R8C-01** — bloqueio de pouso do MM-05 mantém-se; nada neste lote o toca.
- **C4-01** — decisão do dono pendente (Rodada 10, §5); não é condição de pouso da C4.
- Nada mais fica pendente desta confirmação.
