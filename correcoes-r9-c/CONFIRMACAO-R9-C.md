# CONFIRMAÇÃO — Lote R9-C (auditoria contínua, Rodada 9, 09/11/2026)

**Confirmador (sessão):** `9f7c05fb-1e9f-4188-8059-2c3495338c52`
**Produtor do lote (sessão):** `4e92c4e6-246f-4504-bef8-638594add3d5`
**Revisor do lote (sessão):** `6b58596e-ab3d-4847-99e8-b6596bef2521`
**Data da confirmação:** 09/11/2026.

**Declaração expressa de independência:** não produzi este lote nem o revi.
Não produzi nem revi nenhum dos pacotes corrigidos nele (Camada 4, Camadas 1/2,
base fictícia 12k, ciclo P25 variante T, MM-05). Sou uma sessão distinta do
produtor (`9f7c05fb-1e9f-4188-8059-2c3495338c52` ≠
`4e92c4e6-246f-4504-bef8-638594add3d5`) e distinta do revisor
(`9f7c05fb-1e9f-4188-8059-2c3495338c52` ≠
`6b58596e-ab3d-4847-99e8-b6596bef2521`).

**Fonte:** `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-09-0543/00-CONSOLIDADO-RODADA9.md`
(§6, lote R9-C), `~/workspace/ziipsan-producao/correcoes-r9-c/RELATORIO-R9-C.md`
e `~/workspace/ziipsan-producao/correcoes-r9-c/REVISAO-R9-C.md`.

**Método:** trabalho 100% local; **zero chamadas ao GitHub** (parada dura por
HTTP 429 respeitada — nenhum comando `gh`, nenhum `git` remoto, nenhum push).
Não editei `DECISOES-DO-DONO.md` nem o portão
(`esteira/portao-deploy.py`). Todos os hashes e todas as contagens abaixo foram
recomputados por mim em disco nesta sessão, por leitura própria dos ficheiros;
nenhum hash ou contagem do produtor ou do revisor foi aceite sem verificação.
Placares lidos no log integral, nunca em saída filtrada.

## 0. Hashes recomputados por mim

### 0.1 Artefactos do lote + relatório + revisão

| Artefacto | SHA-256 recomputado por mim | Declarado pelo produtor | Confere? |
|---|---|---|---|
| `proposta-tarifaria/camada-4-campo/ADENDO-R9-C4-1-2026-11-09.md` | `45cb3ee6d7558beb5e0385a394174b6da04e618194fd9a7ae058312fc7f06dab` | `45cb3ee6…` | Sim, exacto |
| `proposta-tarifaria/camada-2-maestro/ADENDO-R9-I-01-2026-11-09.md` | `ba241c13384dbba5cd6abc34eaa0cefc09a28e47b22acc3c3fe4a31eed0e7c71` | `ba241c13…` | Sim, exacto |
| `base-ficticia-12k/ADENDO-R9-I-02-2026-11-09.md` | `2e3c86645a932d4c91660a4f19fff5bb3e494f08efeb4375a51f39bae6f1efd8` | `2e3c8664…` | Sim, exacto |
| `correcoes-r9-c/REGISTRO-R9-P2-2026-11-09.md` | `09e433081dd3a437e6277105c2875dfbba57b5cf833f5e1fd0b4e5256b13d8ba` | `09e43308…` | Sim, exacto |
| `multimunicipio/mm-05-fiscal/ADENDO-R9-P3-2026-11-09.md` | `65875c7167a54beb8c78c90bddb9e07dda5829e45230ad3e4c07772d99b9966a` | `65875c71…` | Sim, exacto |
| `correcoes-r9-c/RELATORIO-R9-C.md` | `ab5c1aa9e61a4d5afc32c1e922ce6b7116d2f960a44deee73af924d1f1ca9fb1` | `ab5c1aa9…` | Sim, exacto |
| `correcoes-r9-c/REVISAO-R9-C.md` | `096c474e535c9fd82921a2cf88aa6b29814c35a8a1c7b53a3f308d3ab6b76411` | `096c474e…` (enunciado da tarefa) | Sim, exacto |

### 0.2 Originais — nenhum reescrito

| Original | SHA-256 recomputado por mim | Confere com produtor/revisor? |
|---|---|---|
| `proposta-tarifaria/camada-4-campo/RELATORIO-CAMADA-4.md` | `ebe0b5a579384d25b2242f06d4259905134c3c6ce02e88db3c1439efc65a38f2` | Sim |
| `proposta-tarifaria/camada-2-maestro/CORRECAO-CAMADA-2-R8C.md` | `45b4b9157b5a30b211738d7923abe9bb4230d10c12721e10108593b5ebc9a9a6` | Sim |
| `proposta-tarifaria/camada-1-catalogo/trabalho-r8c/prova-reaplicacao-ANTES.log` | `c85b5539d9c5980a05e177a712704edc714a2a01b27ced08be90213445fade32` | Sim |
| `base-ficticia-12k/LANDING.md` | `e1b90121d0250c6f5147fa65bd57bcef9888c42ed50ae526dc46c77ef10e55ad` | Sim |
| `multimunicipio/mm-05-fiscal/PORTAO-REGISTRO-MM-05.md` | `cb44736a7b5365954026acd527810d4efe28e8a4ae359719e2cfe8c864693e71` | Sim |
| `correcao-p25-receivable/CONFIRMACAO-EXECUCAO-VARIANTE-T.md` | `1c9346098f8d861a10ac2f4c397546663c2a7c9037256531edebf2b272178764` | Sim |
| `multimunicipio/MAPA-DE-PACOTES.md` | `90ee83bd6494c92f67c09b794d57e812877bc707ad25a308f1398fcab9113bc8` | Sim |
| `correcoes-r8-c/PORTAO-REGISTRO-R8-C.md` | `3bfa888a83a53d85ed09c42cf70d51edb2aff700b23934bb48bbc4352948b577` | Sim |
| `correcao-p25-receivable/portao-saida-execucao-variante-t.txt` | `ef51032046ce339f1622d69c20b5946b0a336a917ed4776496cd057765ea8f4b` | Sim |

Portão mecânico: `esteira/portao-deploy.py`, SHA-256
`05b58ba7378a2b8b3978feb2f7c5da240701a32c960b248fb3dc74352967cc16`,
mtime 2026-11-09 00:14:29 UTC — igual ao declarado pelo revisor, não tocado
por mim nem por este lote.

## 1. R9-C4-1 — total de testes novos da Camada 4: 25/25 → 29/29

**Veredito do item: CONFIRMADO.**

Evidência recomputada por mim:

- `RELATORIO-CAMADA-4.md:118-119` segue intacto (SHA-256 §0.2): 13/13 +
  12/12, total **25/25**. O adendo não reescreveu o original.
  (Nota de linha: o produtor cita `:117-119`; o total 25/25 está na linha
  119 e o 12/12 na 118 — a citação cobre o quadro, sem divergência de
  conteúdo.)
- Log original `tools/saida-harness-junit.txt` (SHA-256
  `d236d27797414eec1c6682ea6d122c227da90323bfd387b6a655e774c6d99c58`,
  201 linhas): secção "JUnit C4" lida na íntegra (linhas 29–78) —
  **25 tests found / 25 started / 25 successful / 0 failed**
  (linhas 72/74/76/77). Árvore contada mecanicamente: 13 entradas sob
  `FieldChargeResolverTest`, **12** sob
  `FieldSnapshotProjectorEquivalenceTest`.
- Log da correcção `tools/saida-harness-junit-correcao.txt` (SHA-256
  `b3fd3fc4a66ac8d447a487fc9dc7139ae2e97d9255a10d80478bcf8c39906f2d`,
  189 linhas): secção "JUnit C4" lida na íntegra (linhas 13–66) —
  **29 tests found / 29 started / 29 successful / 0 failed**
  (linhas 60/62/64/65). Árvore contada mecanicamente: 13 entradas sob
  `FieldChargeResolverTest`, **16** sob
  `FieldSnapshotProjectorEquivalenceTest`, incluindo as 4 entradas novas
  pelos prefixos `R-01/INV-C4-01` (1×), `R-02 CARACTERIZACAO` (1×) e
  `R-04/INV-C4-05` (2×); 0 ocorrências desses prefixos no log original.
- Contagem `@Test` no código entregue: `FieldChargeResolverTest.java` = **13**
  (SHA-256 `e3d0d8691d479bb0ef25a39392fc371077690f13a52209b7a177b8b45599cecb`);
  `FieldSnapshotProjectorEquivalenceTest.java` = **16** (SHA-256
  `ed74b1e3f00dbeea94551e14459da08a4a8a2f6667b0544d63184b005fd5a84e`).
  Total **29**. O hash do Projector coincide com `CORRECAO-CAMADA-4.md:204`.
- Os 4 métodos existem no ficheiro de teste, 1 ocorrência cada como método
  (`equivalenceMunicipalAgainstLiteralExpectedSnapshot`,
  `derivedSustainedOnlyByDivergentTariffVersionIdIsNotConfronted`,
  `failsClosedOnMoreThanOneDerivedCharge`,
  `failsClosedOnDerivedChargeWithSewerDisabledData`); o segundo método tem
  uma 2.ª ocorrência do nome na linha 89, numa referência `{@code …}` em
  Javadoc — não é um segundo teste, como o revisor registou.
- Os `@DisplayName` integrais citados no adendo conferem exactamente com o
  código-fonte: linhas 316 (R-01), 354 (R-04, mais de uma), 370 (R-04, sem
  esgoto) e 386 (R-02) de `FieldSnapshotProjectorEquivalenceTest.java`.
- `CORRECAO-CAMADA-4.md:37-38` declara 29/29 (13+16) e `:204` lista os 4
  métodos — conferido.

O adendo corrige o total para **29/29 (13+16)**, lista os 4 testes por nome
e declara que o 25/25 era o estado antes do acréscimo. Substância correcta.

## 2. R9-I-01 — citação do log ANTES da Camada 2

**Veredito do item: CONFIRMADO.**

Evidência recomputada por mim:

- O directório `proposta-tarifaria/camada-2-billing/` **não existe**
  (`ls`: No such file or directory). A Camada 2 é
  `proposta-tarifaria/camada-2-maestro/` — correcção de premissa do produtor
  confirmada.
- A pasta `camada-2-maestro/trabalho-r8c/` **existe** e contém `CAMADA-2.sql`
  e os três logs DEPOIS (`DEPOIS.log`, `DEPOIS-2.log`, `DEPOIS-3.log`). O
  `prova-reaplicacao-ANTES.log` **não existe** nessa pasta — só ele falta.
- Os logs DEPOIS são byte-idênticos entre as pastas da Camada 1 e da
  Camada 2: `DEPOIS-3.log` SHA-256
  `965bf388edcc3b6b393f116bf8f7115b367c28f9ae21568008cbf15736092776` nas duas;
  `DEPOIS.log` `f179d894c8d88529800ba1f4c52e2041805e11e07f1d788c6d701f6782f11971`
  nas duas; `DEPOIS-2.log`
  `80f7f7ce4631049ae898219402f1072887fb2e0f40db13e3b96f72e66a35ae39` nas duas.
- A prova ANTES existe:
  `proposta-tarifaria/camada-1-catalogo/trabalho-r8c/prova-reaplicacao-ANTES.log`,
  **460 bytes**, SHA-256 `c85b5539…fade32` (§0.2). Conteúdo integral lido por
  mim: `ANTES CAMADA-2 corrida 1: OK` / `ANTES CAMADA-2 corrida 2: OK` e
  `ANTES CAMADA-1 corrida 2: FALHOU` na linha 81
  (`relation "uq_charge_definitions_tenant_id" already exists`).
- `CORRECAO-CAMADA-2-R8C.md:35` cita de facto
  `trabalho-r8c/prova-reaplicacao-ANTES.log` e
  `trabalho-r8c/prova-reaplicacao-DEPOIS-3.log`. A citação do DEPOIS-3
  **está correcta** como escrita (o ficheiro está na pasta da Camada 2);
  só a do ANTES.log é imprecisa (caminho relativo que sugere a pasta local).
  A citação na Camada 1 está correcta (`CORRECAO-CAMADA-1-R8C.md:16`,
  `CONFIRMACAO-CAMADA-1-R8C.md:17` — conferido por leitura).
- Nenhuma cópia do ANTES.log foi criada por este lote (ausência confirmada
  acima); o original da Camada 1 não foi tocado (hash §0.2).

**Juízo sobre a via (a):** a decisão do produtor é a correcta. A prova é uma
corrida única das duas camadas no mesmo script; manter uma só cópia canónica
(Camada 1) evita duas cópias que possam divergir, e o adendo corrige a
citação sem duplicar evidência. A via (b) foi considerada e rejeitada por
fundamento registado, não por falta da prova. O adendo lê a citação
correctamente como
`proposta-tarifaria/camada-1-catalogo/trabalho-r8c/prova-reaplicacao-ANTES.log`.

## 3. R9-I-02 — duplicação do segmento 108–135 no `exec.log` da base fictícia

**Veredito do item: CONFIRMADO.**

**Fotografia do ficheiro vivo no momento da minha leitura:**
`base-ficticia-12k/push/espera/exec.log` — **452 linhas**, SHA-256
`d36fdd904ab032cb1791ef20fac3b79cefc2095cd6711947047a7ee22668d0e1` — idêntica
à fotografia de fecho do produtor e à do revisor. A linha 452 é a do A-07
do lote R9-A (`09:16:02 … lote 192 marcado em state.json (done 1-192, head
3156a0bf…)`), fora do segmento duplicado. Prova de que o ficheiro só cresceu
por append: o SHA-256 das linhas 1–451, recomputado por mim, é
`e9b80003b4e2d5d2205c3536a7d8f53e13606136144f24f44355ffee6dd8e849` —
exactamente a 1.ª fotografia do produtor. O `exec.log` **não foi reescrito**.

**Contagens mecânicas recomputadas por mim (script próprio sobre o log
integral, 452 linhas):**

- Linhas "OK lote": **218**; lotes distintos com OK: **190** (1–191 excepto
  o 136, sem OK — correcto, marcado pelo rito do A-01 — e excepto o 192,
  marcado pelo rito do A-07); excesso 218 − 190 = **28**.
- Lotes duplicados (2× cada): **108–135** (28 lotes) — lista exacta
  recomputada, igual à do adendo.
- Pares de linhas adjacentes byte-idênticas: **60**, linhas **217–336**
  (primeiro par 217–218, último 335–336). Composição: 29 cabeçalhos
  `LOTE x/262` (108–136) + 28 `OK lote` (108–135) + 1 `HEAD atual` + 1 linha
  de detalhe do lote 113 + 1 `PARADA lote 136` — soma 60, exacta.
- Exemplos conferidos: `OK lote 108` linhas **221–222**; `PARADA lote 136`
  linhas **335–336**; `LOTE 108/262` linhas **216, 219, 220** (1× cabeçalho
  da 1.ª tentativa morta às 04:57:28 + 2× a linha do relançamento às
  05:01:15); linhas `PARADA lote` no ficheiro: **335, 336, 451** (136
  duplicada; 192 simples, às 08:17:39Z, payload 429 com sha
  `3156a0bf1b3c5b2874c721a04b4291d40309f050`).
- Um HEAD por lote: lote 108 → só `2075de5`; lote 135 → só `c8c0b88`.
- `state.json` contíguo: done **1–192**, head
  `3156a0bf1b3c5b2874c721a04b4291d40309f050`, SHA-256 do ficheiro
  `01be65f8ae34c54f04c682061ac8b140d317a02e3d91d9675e8d93baeb76b6e3` —
  igual ao registado pelo produtor e pelo revisor. Lotes sem OK em 1–192:
  só o 136 e o 192, ambos marcados por rito, não por push.

**Juízo sobre "NÃO é duplo push":** correcto. O padrão é A A B B (linha
duplicada adjacente, mesmo timestamp e conteúdo byte-idêntico), não
A B C A B C com timestamps distintos; cada lote tem um só HEAD; o
`state.json` é contíguo sem saltos nem repetições. O que está duplicado é
o **registo**, não o pouso. A causa da escrita dupla não é determinável a
partir do log e o adendo correctamente não a afirma.

## 4. R9-P2 — Regra 2 do portão leu o veredito CITADO (ciclo P25)

**Veredito do item: CONFIRMADO.**

Evidência recomputada por mim:

- `correcao-p25-receivable/CONFIRMACAO-EXECUCAO-VARIANTE-T.md` (SHA-256
  §0.2): linha **50** cita o "veredito formal APROVADO" da revisão
  (revisor `860c7ce9-67e8-49ce-b76b-aceadd9a69ef`); linha **70** traz o
  veredito próprio, `**CONFIRMADO.**`. Cabeçalho (linhas 4–8): produtor
  `fe11fa85-2d2f-4552-8b80-a0c3593fb4e8` ≠ revisor `860c7ce9…` ≠ confirmador
  `f6490ceb-f559-4094-a22c-0eafd1bd8f28` — cadeia distinta declarada.
- A linha 70 não é "linha de veredito" pela definição do extrator
  documentada em `correcoes-r8-c/PORTAO-REGISTRO-R8-C.md` §5.1 (ficheiro
  SHA-256 §0.2; §5.1 existe e documenta a mesma limitação no R8-C, com o
  citado REPROVADO a gerar falso falhanço): não contém a palavra
  "veredito", não é título `#`, não tem ✅/❌. O extrator toma a linha 50
  (citada). Mecanismo confirmado.
- Portão do ciclo: `portao-saida-execucao-variante-t.txt` (SHA-256 §0.2),
  lido por mim na íntegra — Regras 1–6 `[OK]`, **PORTÃO: APROVADO**, item (3)
  (rebase) assinalado como não verificado pelo portão, a verificar no acto
  do pouso. A Regra 2 passou lendo o veredito citado — o resultado material
  é correcto porque o veredito próprio (:70, CONFIRMADO) confirma o mesmo
  resultado.
- A nuance registada pelo produtor é exacta:
  `PORTAO-REGISTRO-EXECUCAO-VARIANTE-T.md:21` declara "Sem falsos positivos
  de extracção neste ciclo" — conferido por grep; vale no resultado
  (exit 0, APROVADO), não à letra no caminho de extracção.

**Juízo sobre a classificação:** correcta. É uma nova instância da
limitação conhecida do extrator (`veredito_do_arquivo` lê veredito citado
em vez do próprio), **em sentido inverso** ao R8-C §5.1. Não há falso
APROVADO em substância. **Sem acção no pacote** — correcto; a correcção do
extrator só se faz pelo rito próprio do portão, que não foi editado.

## 5. R9-P3 — registo do portão do MM-05 sem remissão ao bloqueio P-MM05-R8C-01

**Veredito do item: CONFIRMADO.**

Evidência recomputada por mim:

- `multimunicipio/mm-05-fiscal/PORTAO-REGISTRO-MM-05.md` (39 linhas,
  SHA-256 §0.2, lido por mim na íntegra): **0 ocorrências** de `R8-C`,
  `R8C`, `BLOQUEIO`, `P-MM05-R8C-01` e `redesenho` em todo o ficheiro.
  A linha 35 cita JUnit **11/11** e PG **12/12** (desenho antigo); a linha
  38 declara o pacote "apto a pousar … cumprida a P-MM05-01". O registo não
  foi reescrito (hash §0.2).
- Bloqueio vigente: `mm-05-fiscal/LANDING.md:73-79` (79 linhas) — adendo do
  ciclo R8-C (sessão `66187316-31e4-42cc-ba7d-ff4adb372ac8`): redesenho sobre
  o `fiscal_issuers.ibge` existente da V4_5, coluna `municipality_ibge`
  eliminada, JUnit 12/12 e PG 17/17; "**⛔ O MM-05 NÃO POUSA** até o ciclo
  R8-C fechar". `multimunicipio/MAPA-DE-PACOTES.md:143-147` (147 linhas;
  SHA-256 §0.2): "Estado: **⛔ BLOQUEADO — não pousa** até a reapreciação e
  a confirmação do ciclo existirem e o LANDING do bloco declarar o
  desbloqueio."
- `CORRECAO-MM-05-R8C.md:56-57` confirma os placares do redesenho:
  **JUnit 12/12; PG 17/17** — os 11/11 e 12/12 do registo são de facto os
  do desenho antigo.
- O ciclo avançou: reapreciação-2 **APROVADA**
  (`REAPRECIACAO-MM-05-R8C-2.md:9-11`, SHA-256
  `f1464a02fb2f76379c6faf4c2c8f2e3fc538dce9f02741191addecb8917e66c0`) e
  confirmação **CONFIRMADA** (`CONFIRMACAO-MM-05-R8C.md:9`, SHA-256
  `1877ebb116e59daa7188560cf984b7e2863658a4dd831cf12f45fcd08e7a14c7`) —
  e a própria confirmação declara que o "bloqueio de pouso P-MM05-R8C-01
  **mantém-se** até ao portão mecânico do ciclo e ao desbloqueio no LANDING
  do bloco — esta confirmação não o levanta".
- Nenhum adendo de desbloqueio existe no LANDING do MM-05 nem no MAPA
  (grep por "desbloqueio": só as menções ao bloqueio vigente, LANDING:79
  e MAPA:147).

**Juízo sobre se o adendo corrige de facto o risco:** sim, no que um adendo
pode corrigir. O `ADENDO-R9-P3-2026-11-09.md` remete expressamente ao
bloqueio P-MM05-R8C-01 e ao ciclo R8-C, declara que o **MM-05 NÃO pode
pousar enquanto o bloqueio vigorar** e corrige os placares (11/11 e 12/12
são do desenho antigo; os vigentes são 12/12 e 17/17). O desbloqueio
continua a exigir o portão mecânico do ciclo R8-C + o adendo de desbloqueio
no LANDING do bloco; nenhum dos dois existe, e não cabe a este lote
levantá-lo. Observação (não é ressalva): um leitor que abra **só** o
`PORTAO-REGISTRO-MM-05.md`, sem o adendo, continua sem ver a remissão —
limitação estrutural de todo o mecanismo de adendo do projecto (originais
nunca reescritos), e o achado pedia exactamente um adendo datado, que foi
entregue.

## 6. Juízo sobre a ressalva R9-C-01 do revisor

**A ressalva é procedente, e a minha reprodução corrige-lhe um detalhe:
a conclusão 29/29 mantém-se provada por via independente; a ressalva exige
apenas registo, nenhuma acção.**

Reprodução própria, com as 4 cadeias `@DisplayName` integrais extraídas
programaticamente do código-fonte (linhas 316/354/370/386) e grep `-F` sobre
os dois logs integrais:

| Teste | `@DisplayName` integral — ORIG | `@DisplayName` integral — CORR |
|---|---|---|
| R-01 | 0 | **0** |
| R-02 | 0 | **0** |
| R-04a (mais de uma…) | 0 | **0** |
| R-04b (sem esgoto) | 0 | 1 |

O revisor escreveu R-04a como 0/1; a minha reprodução, por dois métodos
independentes (contagem em Python sobre o ficheiro integral e `grep -F -c`
com a cadeia extraída do código), dá **0/0** para o R-04a — a linha da árvore
no log da correcção termina em `(fonte do ...`, truncada, pelo que a cadeia
integral não pode ocorrer. **Divergência de detalhe do revisor, declarada
expressamente:** a frase do adendo falha em **3 dos 4** `@DisplayName`
integrais (R-01, R-02, R-04a), não em 2 dos 4 como o revisor registou; só o
R-04b reproduz 0/1. A causa é a mesma que o revisor identificou: o JUnit
trunca os DisplayNames longos na árvore com `...` (R-01 termina em
"construido por valores..."; R-02 em "divergen..."; R-04a em
"(fonte do ...").

O que tem 0/1 são os **prefixos truncados tal como impressos no log**,
verificado por mim: R-01 truncado 0/1, R-02 truncado 0/1, R-04a truncado 0/1,
R-04b (integral, não truncado) 0/1.

**A conclusão 29/29 mantém-se provada por via independente**, sem depender da
frase defeituosa: resumo 29/29 no log da correcção + árvore 13/16 +
contagem `@Test` 13+16 no código entregue + prefixos 0/1 nos 4 testes novos
+ 0 ocorrências dos prefixos no log original (ver §1). O defeito é só a
descrição do grep no adendo (§2). **Não exige reescrita do adendo nem
qualquer acção no lote** — fica registado aqui, com a contagem corrigida
(3 de 4, não 2 de 4), para o registo ser exacto. Esta correcção de detalhe
não altera o veredito do revisor (APROVADO COM RESSALVAS) nem o desta
confirmação.

## 7. Separação de papéis

- Produtor do lote: `4e92c4e6-246f-4504-bef8-638594add3d5` — declarado em
  `RELATORIO-R9-C.md:3` e como autor nos 5 artefactos do lote.
- Revisor do lote: `6b58596e-ab3d-4847-99e8-b6596bef2521` — declarado em
  `REVISAO-R9-C.md:3-4`, com declaração expressa de distinção do produtor.
- Confirmador do lote (eu): `9f7c05fb-1e9f-4188-8059-2c3495338c52` —
  distinto do produtor e do revisor.
- **Produtor ≠ revisor ≠ confirmador: verificado.** As três identidades de
  sessão são distintas e estão declaradas nos três artefactos.
- Nenhum original foi reescrito: os 9 hashes do §0.2 conferem com os
  declarados pelo produtor e pelo revisor; o `exec.log` só cresceu por
  append da linha 452 (A-07, lote R9-A), com o segmento analisado intacto
  (linhas 1–451 = `e9b80003…`, a 1.ª fotografia do produtor).

## 8. Veredito formal final

**Lote R9-C — CONFIRMADO.**

Os 5 artefactos existem, têm os hashes declarados pelo produtor (todos
recomputados por mim e exactos), corrigem/registam o que o repasse da
Rodada 9 pedia, e nenhum original foi reescrito. Os 5 itens estão
**CONFIRMADOS**: R9-C4-1 (29/29, 4 testes por nome), R9-I-01 (citação do
log ANTES corrigida pela via (a)), R9-I-02 (duplicação do `exec.log` —
registo duplicado, não duplo push), R9-P2 (nova instância, em sentido
inverso, da limitação do extrator — só registo, sem acção no pacote) e
R9-P3 (adendo remetendo ao bloqueio P-MM05-R8C-01).

**Divergências:** nenhuma divergência material. Regista-se uma só
correcção de detalhe à ressalva R9-C-01 do revisor (§6): a frase de
verificação do adendo R9-C4-1 falha em 3 dos 4 `@DisplayName` integrais
(R-01, R-02 e R-04a — 0/0), não em 2 dos 4; só o R-04b reproduz 0/1. Não
altera a conclusão 29/29, provada por via independente, nem exige qualquer
acção.

**Fica pendente, fora deste lote** (como o produtor declarou, o revisor
confirmou e eu confirmo): o bloqueio **P-MM05-R8C-01** (portão mecânico do
ciclo R8-C + desbloqueio no LANDING do bloco) e a limitação conhecida do
extrator do portão (correcção futura só pelo rito próprio do portão). Nada
mais fica pendente deste lote. Zero chamadas ao GitHub nesta confirmação;
`DECISOES-DO-DONO.md` e `esteira/portao-deploy.py` não tocados.
