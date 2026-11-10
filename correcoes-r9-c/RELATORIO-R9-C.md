# RELATÓRIO — Lote R9-C (auditoria contínua, Rodada 9, 09/11/2026)

**Produtor do lote (sessão):** `4e92c4e6-246f-4504-bef8-638594add3d5`
**Data:** 09/11/2026.
**Fonte:** `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-09-0543/00-CONSOLIDADO-RODADA9.md` (lido antes de começar).
**Método:** trabalho 100% local; **zero chamadas ao GitHub** (parada dura por HTTP 429
respeitada — nenhum comando `gh`/`git` remoto, nenhum push). Não editei
`DECISOES-DO-DONO.md` nem o portão (`esteira/portao-deploy.py`). Nenhum
original foi reescrito: todas as correcções documentais foram feitas por
adendo/errata/registo datado, em ficheiro novo, com o original intacto
(hashes re-verificados no fecho — ver §6).

**Declaração de independência:** não produzi nenhum dos pacotes corrigidos
neste lote (Camada 4, Camadas 1/2, base fictícia 12k, ciclo P25 variante T,
MM-05). Todas as afirmações abaixo citam ficheiro/linha/hash que eu próprio
verifiquei em disco nesta sessão; placares lidos no log integral, nunca em
saída filtrada.

## 1. R9-C4-1 — total de testes novos da Camada 4: 25/25 → 29/29

**O que foi verificado:**
- `proposta-tarifaria/camada-4-campo/RELATORIO-CAMADA-4.md:117-119`
  (SHA-256 `ebe0b5a579384d25b2242f06d4259905134c3c6ce02e88db3c1439efc65a38f2`):
  13/13 + 12/12, total **25/25**.
- Log original `tools/saida-harness-junit.txt` (SHA-256 `d236d277…c6d99c58`):
  secção "JUnit C4" com **25 found / 25 started / 25 successful / 0 failed**.
- Log da correcção `tools/saida-harness-junit-correcao.txt` (SHA-256 `b3fd3fc4a…39906f2d`):
  secção "JUnit C4" com **29 found / 29 started / 29 successful / 0 failed**.
- Contagem `@Test` no código entregue: `FieldChargeResolverTest.java` = 13
  (SHA-256 `e3d0d869…599cecb`); `FieldSnapshotProjectorEquivalenceTest.java` = 16
  (SHA-256 `ed74b1e3…5a84e`, coincide com `CORRECAO-CAMADA-4.md:204`). Total **29**.
- Os 4 testes acrescentados, por nome (método + `@DisplayName` integral no adendo):
  `equivalenceMunicipalAgainstLiteralExpectedSnapshot` (R-01),
  `derivedSustainedOnlyByDivergentTariffVersionIdIsNotConfronted` (R-02),
  `failsClosedOnMoreThanOneDerivedCharge` e
  `failsClosedOnDerivedChargeWithSewerDisabledData` (R-04).
  Cada excerto distintivo tem 0 ocorrências no log original e 1 no log da
  correcção. Os 4 nomes constam em `CORRECAO-CAMADA-4.md:204` e o total 29/29
  em `CORRECAO-CAMADA-4.md:37-38`.

**Artefacto produzido:** `proposta-tarifaria/camada-4-campo/ADENDO-R9-C4-1-2026-11-09.md`
— SHA-256 `45cb3ee6d7558beb5e0385a394174b6da04e618194fd9a7ae058312fc7f06dab`.
Corrige o total para **29/29 (13+16)**, lista os 4 testes por nome e declara
que o 25/25 era o estado antes do acréscimo (correcto no seu momento).

**Pendência que sobrevive:** nenhuma deste item. A reexecução ED-19 (53/53)
não é afectada.

## 2. R9-I-01 — citação do log ANTES da Camada 2

**O que foi verificado:**
- `CORRECAO-CAMADA-2-R8C.md:35` (ficheiro SHA-256
  `45b4b9157b5a30b211738d7923abe9bb4230d10c12721e10108593b5ebc9a9a6`) cita
  `trabalho-r8c/prova-reaplicacao-ANTES.log` e
  `trabalho-r8c/prova-reaplicacao-DEPOIS-3.log`.
- **Duas premissas do repasse estavam erradas e foram corrigidas por
  verificação própria:** (i) o directório não é `camada-2-billing` (não
  existe); a Camada 2 é `proposta-tarifaria/camada-2-maestro/`. (ii) A pasta
  `camada-2-maestro/trabalho-r8c/` **existe** e contém `CAMADA-2.sql` e os
  logs DEPOIS — `DEPOIS-3.log` byte-idêntico à cópia da Camada 1
  (SHA-256 `965bf388…92776` nas duas pastas), tal como o `DEPOIS.log`
  (`f179d894…11971`). **Só o ANTES.log falta na pasta da Camada 2.**
- A prova existe: `proposta-tarifaria/camada-1-catalogo/trabalho-r8c/prova-reaplicacao-ANTES.log`,
  460 bytes, SHA-256 `c85b5539d9c5980a05e177a712704edc714a2a01b27ced08be90213445fade32`,
  conteúdo verificado (Camada 2: corrida 1 OK / corrida 2 OK; Camada 1:
  corrida 2 FALHOU na linha 81). É uma corrida única das duas camadas no
  mesmo script; a citação na Camada 1 está correcta
  (`CORRECAO-CAMADA-1-R8C.md:16`, `CONFIRMACAO-CAMADA-1-R8C.md:17`).
  A citação do DEPOIS-3 na linha 35 **está** correcta como escrita.

**Decisão do produtor — via (a), adendo; via (b) rejeitada:** decido corrigir
a citação por adendo datado. Fundamento: a prova é uma corrida única
partilhada; manter uma só cópia canónica (Camada 1) evita duas cópias que
possam divergir, e o adendo corrige a citação sem duplicar evidência.
A via (b) (copiar o log) foi considerada e rejeitada por esse motivo.
O original da Camada 1 não foi apagado, movido ou tocado; nenhuma cópia
foi criada por este lote.

**Artefacto produzido:** `proposta-tarifaria/camada-2-maestro/ADENDO-R9-I-01-2026-11-09.md`
— SHA-256 `ba241c13384dbba5cd6abc34eaa0cefc09a28e47b22acc3c3fe4a31eed0e7c71`.
Lê a citação do ANTES.log como
`proposta-tarifaria/camada-1-catalogo/trabalho-r8c/prova-reaplicacao-ANTES.log`
e regista as duas correcções de premissa acima.

**Pendência que sobrevive:** nenhuma deste item.

## 3. R9-I-02 — duplicação do segmento 108–135 no `exec.log` da base fictícia

**O que foi verificado** (`base-ficticia-12k/push/espera/exec.log`, contagem
mecânica por script próprio sobre o log integral):
- 1.ª leitura: 451 linhas, SHA-256 `e9b80003…dd8e849`; fecho: 452 linhas,
  SHA-256 `d36fdd90…68d0e1` — a única diferença é a linha 452, A-07 do lote
  R9-A, acrescentada por outra sessão durante esta tarefa (ver abaixo).
  Todas as contagens são idênticas nas duas fotografias.
- **218 linhas "OK lote" para 190 lotes distintos** (1–191 excepto o 136,
  sem OK — correcto, marcado pelo rito do A-01); **28 lotes duplicados:
  108–135** (218 − 190 = 28 linhas a mais).
- **60 pares de linhas adjacentes byte-idênticas, linhas 217–336**
  (29 cabeçalhos `LOTE x/262` 108–136 + 28 `OK lote` 108–135 + 1 `HEAD atual`
  + 1 linha de detalhe do lote 113 + 1 `PARADA lote 136`). Exemplos:
  `OK lote 108` linhas 221–222; `PARADA lote 136` linhas 335–336.
- Mecanismo: cabeçalho `LOTE 108/262` único às 04:57:28 (linha 216), sem OK
  a seguir (1.ª tentativa morta — o adendo de execução do `LANDING.md` já
  regista a morte do processo ~04:57 UTC e o relançamento do estado
  done = 107); relançamento às 05:01:15 re-pousou 108–135, cada um uma só
  vez (um HEAD por lote; ex. 108 → só `2075de5`; 135 → só `c8c0b88`);
  na janela 05:01:15–05:18:54 cada linha ficou registada duas vezes seguidas
  (A A B B, não A B C A B C — duplicação do registo, não segundo pouso).
  A causa da escrita dupla não é determinável a partir do log e não é afirmada.
- `state.json` contíguo: done 1–191, head `87be10a4…` na 1.ª leitura;
  done 1–192, head `3156a0bf…` no fecho (A-07). PARADA: 335–336 (136,
  duplicada) e 451 (192, simples).

**Artefacto produzido:** `base-ficticia-12k/ADENDO-R9-I-02-2026-11-09.md`
— SHA-256 `2e3c86645a932d4c91660a4f19fff5bb3e494f08efeb4375a51f39bae6f1efd8`.
Explica o mecanismo, dá as contagens e declara que **NÃO é duplo push**.
O `exec.log` não foi reescrito (histórico).

**Pendência que sobrevive:** nenhuma deste item. Nota de concorrência: o
`exec.log` é um ficheiro vivo — o lote R9-A escreveu nele durante esta
tarefa (linha 452, A-07, lote 192, 09:16:02 UTC); o adendo regista-o.

## 4. R9-P2 — Regra 2 do portão leu o veredito CITADO (ciclo P25) — só registo

**O que foi verificado:**
- `correcao-p25-receivable/CONFIRMACAO-EXECUCAO-VARIANTE-T.md`
  (SHA-256 `1c934609…178764`): linha **50** cita o "veredito formal APROVADO"
  da revisão (revisor `860c7ce9-67e8-49ce-b76b-aceadd9a69ef`); linha **70** traz
  o veredito próprio, `**CONFIRMADO.**` (confirmador
  `f6490ceb-f559-4094-a22c-0eafd1bd8f28`; produtor `fe11fa85…` ≠ revisor ≠
  confirmador). A linha 70 não é "linha de veredito" pela definição do
  extrator (não contém "veredito", não é título, não tem ✅/❌), pelo que o
  extrator toma a linha 50 — mecanismo documentado em
  `correcoes-r8-c/PORTAO-REGISTRO-R8-C.md` §5.1 (SHA-256 `3bfa888a…48b577`).
- Portão do ciclo: exit 0, APROVADO
  (`portao-saida-execucao-variante-t.txt`, SHA-256 `ef510320…57765ea8f4b`).

**Classificação:** nova instância da limitação conhecida do extrator
(`veredito_do_arquivo` lê veredito citado em vez do próprio), **em sentido
inverso** ao R8-C §5.1 (lá o citado REPROVADO gerou falso falhanço; aqui o
citado APROVADO gerou um passo **materialmente correcto**, porque o veredito
próprio :70 é igualmente positivo e a reexecução do confirmador deu 10/10).
Nuance registada: o registo do portão do ciclo declara "Sem falsos positivos
de extracção" — vale no resultado, não à letra no caminho de extracção.

**Artefacto produzido:** `correcoes-r9-c/REGISTRO-R9-P2-2026-11-09.md`
— SHA-256 `09e433081dd3a437e6277105c2875dfbba57b5cf833f5e1fd0b4e5256b13d8ba`.
**Sem acção no pacote;** a correcção do extrator só se faz pelo rito próprio
do portão (portão não editado).

**Pendência que sobrevive:** a limitação do extrator em si (conhecida,
registada; correcção futura só pelo rito do portão). Não é pendência deste lote.

## 5. R9-P3 — registo do portão do MM-05 sem remissão ao bloqueio P-MM05-R8C-01

**O que foi verificado:**
- `multimunicipio/mm-05-fiscal/PORTAO-REGISTRO-MM-05.md` (39 linhas, SHA-256
  `cb44736a…4693e71`): :35 cita JUnit 11/11 e PG 12/12 (desenho antigo);
  :38 declara o pacote "apto a pousar … cumprida a P-MM05-01". **0 ocorrências**
  de `R8-C`, `R8C`, `BLOQUEIO`, `P-MM05-R8C-01` ou `redesenho` no ficheiro.
- Bloqueio vigente: `mm-05-fiscal/LANDING.md:73-79` (SHA-256 `5344b196…0917`) e
  `multimunicipio/MAPA-DE-PACOTES.md:143-147` (SHA-256 `90ee83bd…9113bc8`):
  pacote redesenhado no ciclo R8-C (eixo `fiscal_issuers.ibge` da V4_5, sem
  coluna nova; JUnit 12/12, PG 17/17 — `CORRECAO-MM-05-R8C.md:56-57`);
  "**⛔ O MM-05 NÃO POUSA** até o ciclo R8-C fechar".
- O ciclo avançou (reapreciação-2 APROVADA, `REAPRECIACAO-MM-05-R8C-2.md:9-11`,
  SHA-256 `f1464a02…17e66c0`; confirmação CONFIRMADA,
  `CONFIRMACAO-MM-05-R8C.md:9`, SHA-256 `1877ebb1…cd08e7a14c7`), mas a própria
  confirmação declara que "o bloqueio de pouso P-MM05-R8C-01 **mantém-se** até
  ao portão mecânico do ciclo e ao desbloqueio no LANDING do bloco — esta
  confirmação não o levanta". Nenhum adendo de desbloqueio existe no LANDING
  do MM-05 (79 linhas) nem no MAPA (147 linhas) — verificado.

**Artefacto produzido:** `multimunicipio/mm-05-fiscal/ADENDO-R9-P3-2026-11-09.md`
— SHA-256 `65875c7167a54beb8c78c90bddb9e07dda5829e45230ad3e4c07772d99b9966a`.
Remete expressamente ao bloqueio P-MM05-R8C-01 e ao ciclo R8-C e declara que
o **MM-05 NÃO pode pousar enquanto o bloqueio vigorar**, apesar da secção
final do registo do portão. O registo do portão não foi reescrito.

**Pendência que sobrevive:** **P-MM05-R8C-01** — o desbloqueio exige o portão
mecânico do ciclo R8-C e o adendo de desbloqueio no LANDING do bloco; nenhum
dos dois foi verificado por mim como existente. Não cabe a este lote levantá-lo.

## 6. Integridade do lote (fecho)

Originais re-verificados por hash no fecho desta sessão (idênticos aos citados
nos adendos — nenhum foi alterado):

| Original | SHA-256 |
|---|---|
| `proposta-tarifaria/camada-4-campo/RELATORIO-CAMADA-4.md` | `ebe0b5a579384d25b2242f06d4259905134c3c6ce02e88db3c1439efc65a38f2` |
| `proposta-tarifaria/camada-2-maestro/CORRECAO-CAMADA-2-R8C.md` | `45b4b9157b5a30b211738d7923abe9bb4230d10c12721e10108593b5ebc9a9a6` |
| `proposta-tarifaria/camada-1-catalogo/trabalho-r8c/prova-reaplicacao-ANTES.log` | `c85b5539d9c5980a05e177a712704edc714a2a01b27ced08be90213445fade32` |
| `base-ficticia-12k/LANDING.md` | `e1b90121d0250c6f5147fa65bd57bcef9888c42ed50ae526dc46c77ef10e55ad` |
| `multimunicipio/mm-05-fiscal/PORTAO-REGISTRO-MM-05.md` | `cb44736a7b5365954026acd527810d4efe28e8a4ae359719e2cfe8c864693e71` |
| `correcao-p25-receivable/CONFIRMACAO-EXECUCAO-VARIANTE-T.md` | `1c9346098f8d861a10ac2f4c397546663c2a7c9037256531edebf2b272178764` |

O `exec.log` da base é ficheiro vivo: o seu hash mudou durante esta sessão
(`e9b80003…` → `d36fdd90…`) apenas pelo acrescento da linha 452 (A-07, lote
R9-A) no fim; o segmento analisado no item 3 é idêntico nas duas fotografias.
`DECISOES-DO-DONO.md` e `esteira/portao-deploy.py` não foram tocados.
Nenhum push, nenhuma chamada ao GitHub.

## Veredito formal do produtor

**Lote R9-C CONCLUÍDO nos seus 5 itens.** Produzi 4 adendos datados e 1 registo
datado (5 artefactos, hashes na tabela do §6 e nos itens acima), todos por
verificação própria em disco, sem reescrever nenhum original e sem nenhuma
chamada ao GitHub. **Feito:** correcção do total da Camada 4 para 29/29 com os
4 testes por nome (R9-C4-1); correcção da citação do log ANTES, com decisão
registada pela via (a) e correcção de duas premissas erradas do repasse
(R9-I-01); adendo sobre a duplicação do `exec.log` com a declaração de que
não é duplo push (R9-I-02); registo da nova instância — em sentido inverso —
da limitação do extrator, sem acção no pacote P25 (R9-P2); adendo remetendo
ao bloqueio P-MM05-R8C-01 e declarando que o MM-05 não pode pousar enquanto
ele vigorar (R9-P3). **Fica pendente, fora deste lote:** o bloqueio
**P-MM05-R8C-01** (portão mecânico do ciclo R8-C + desbloqueio no LANDING do
bloco) e a limitação conhecida do extrator do portão (correcção futura só
pelo rito próprio do portão). Nada mais fica pendente deste lote.
