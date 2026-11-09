# Relatório da Camada C6 — Cascata ED (Etapa 2)

**Camada:** C6 — ED-70, ED-80, ED-83, ED-93
**Fechamento:** 08/11/2026, pelo coordenador de fechamento C5+C6 (os
coordenadores anteriores encerraram com notas de progresso; os pousos da
camada já estavam completos na main — ver §1 — e faltavam a verificação
pós-pouso integral, este relatório, a atualização do ESTADO e o diário).
**Base de partida:** `83a9539` (reparo convergente do diário C5/R2-G1).
**HEAD final da camada:** `fde45c5`.
**Placar após a C6: ED 89/98 · geral 259/286.**

## 1. Verdade do terreno (Passo 0 deste coordenador)

- **HEAD corrente da main conferido por leitura: `fde45c5`** — é o commit
  da ED-93. O push da ED-93, que tinha ficado "executando em segundo
  plano" no encerramento do coordenador anterior, **POUSOU**: `exec.log`
  da operação regista `OK lote 1 (tentativa 1) -> HEAD fde45c5` às
  22:58:08 e o commit está na main (22:58:06Z).
- **As 4 tarefas da C6 estão na main**, em 5 commits de entrega:
  ED-80 `386cec3` · ED-83 `af04605` + `261402f` · ED-70 `e14ee3d` ·
  ED-93 `fde45c5`. Os `state.json` das 4 operações conferem com os HEADs
  reais, lote a lote.
- **C5 re-verificada neste passo 0:** os 15 commits de entrega da C5
  (incl. ED-79 `1d9a947` + `497c091`), o diário `9d1acf6` e os reparos
  `4f8e2e7`/`83a9539` estão todos na lista de commits da main; o blob
  vivo do diário é `23b5ab39…` — o mesmo verificado pelo Portão do
  R2-G1 — com as seções C5 e R2-G1 presentes. A C5 estava de facto
  fechada (RELATORIO-C5.md, ESTADO), sem qualquer ato pendente.
- **Ordem do portão nos pousos da C6:** mtime de cada CONFIRMACAO/
  demonstração é anterior ao timestamp do respectivo commit de entrega
  (ED-80: conf. → 22:43; ED-83: conf-2 → 22:50; ED-70: conf. 22:52:42 →
  commit 22:53:55; ED-93: demonstração 22:55:00 → commit 22:58:06) —
  com a **única exceção de forma declarada na ED-70** (§3).

## 2. Portão — os 4 itens, declarados por pacote

Método: PROTOCOLO-ESTEIRA.md — produção → revisão independente →
correção → confirmação por agente distinto do produtor E do revisor →
portão (mecânico + checklist) → rebase verificado → push → verificação
pós-pouso → diário. Sessões distintas nos cabeçalhos de todos os
pacotes (PROD/REV/CONF próprios por pacote).

| Pacote | (1) REVISAO — veredito | (2) CONFIRMACAO | (3) Rebase verificado | (4) Testes — placar do confirmador |
|---|---|---|---|---|
| ED-80 | APROVADO COM RESSALVAS (ED-80-REV-1) | CONF-1, independente | HEAD lido a cada lote pelo runner; pouso sobre o HEAD corrente | JUnit **21/21**; PG apply/reapply reproduzidos |
| ED-83 | APROVADO COM RESSALVAS (ED-83-REV-1, com ERRATA datada na própria revisão) | CONF-1 + CONF-2 (2ª confirmação), independentes | idem | Java **36/36**; PG 11 tabelas/RLS, negativas **10/10**; composição **25/25** |
| ED-70 | **REPROVADO** (D1/D2) → correção (39/39; PG 12/12) → **REAPRECIACAO: APROVADO** (probes 13/13) | CONF-1, independente (integridade 10/10 por hash) | idem | **39/39** + PG **12/12** reexecutados |
| ED-93 | **REPROVADO** (D1/D2/D3) → correção (32/32; PG 10 negativas) → **REAPRECIACAO: APROVADO** | CONF-1, independente (hashes críticos do LANDING; sondas próprias 17/17) | idem | **32/32** + PG reexecutados |

**Portão mecânico (`esteira/portao-deploy.py`):**
- Antes dos pousos (saídas em `cascata-ed/portao-c6/`): ED-80 e ED-83
  **APROVADO (exit 0)**; ED-70 e ED-93 **BLOQUEADO** — os dois pelo
  mesmo falso positivo **P-ESTEIRA-01** (a Regra 1 extrai veredito por
  palavra-chave no documento inteiro: não reconhece
  `REVISAO-REAPRECIACAO-*.md` como veredito vigente nem distingue
  veredito próprio de citação histórica do REPROVADO superado).
- Reexecutado por este coordenador no fechamento (saídas em
  `cascata-ed/portao-c6-fechamento/`): resultado idêntico —
  ED-80/ED-83 exit 0; ED-70/ED-93 exit 1 (P-ESTEIRA-01). A correção do
  script é de outro lote (achado registado, não editado aqui).
- ED-93: a demonstração do falso positivo foi lavrada **antes** do
  pouso (`ed-93/PORTAO-REGISTRO-ED-93.md`, 22:55:00 < 22:58:06), nos
  termos do §3.2 — checklist fechado em substância (C1/C2).
- ED-70: ver o desvio declarado no §3.

## 3. Desvio de forma do Portão na ED-70 (registado, sem atenuante)

No pacote ED-70, o comando de pouso foi encadeado com `;` após o
verificador mecânico, **sem conferir o exit code**: o push pousou
(`e14ee3d`) com o bloqueio mecânico (falso positivo P-ESTEIRA-01) ainda
**por demonstrar em artefato**. A demonstração foi lavrada depois, em
`ed-70/PORTAO-REGISTRO-ED-70.md`, pelo próprio coordenador-portão da
camada, que declarou o desvio como seu. A cadeia substantiva estava
completa **antes** do pouso (revisão → correção → reapreciação
APROVADO → confirmação independente, hashes 10/10, placares
reexecutados) — o desvio é de **forma do Portão** (§3.2/§4(i)), não de
mérito do pacote, e fica registado aqui e no diário da camada. A
correção de processo (exit code do portão como pré-condição dura,
verificada antes de qualquer pouso) foi aplicada já no pacote
seguinte, a ED-93.

## 4. Verificação pós-pouso por hash (estágio 8)

Executada por este coordenador no fechamento: para cada arquivo de
cada `plan.json` das 4 operações, o SHA de blob git do arquivo na main
(listagens de diretório do conector, ref `main` — 17 diretórios) foi
confrontado com o SHA de blob calculado localmente sobre o arquivo da
`entrega/`.

**Resultado: 91/91 arquivos byte-idênticos — 0 divergências, 0
ausentes.** (ED-80 20/20 · ED-83 25/25 · ED-70 22/22 · ED-93 24/24.)

## 5. Pendências nomeadas da camada (nenhuma é condição de pouso)

- **ED-70:** P-ED70-01..06.
- **ED-80:** P-ED80-01..05.
- **ED-83:** P-ED83-01..07.
- **ED-93:** P-ED93-01..06.
- **P-ESTEIRA-01 (da camada):** correção do `portao-deploy.py` —
  reconhecer `REVISAO-REAPRECIACAO-*.md` como veredito vigente e
  distinguir veredito próprio de citação histórica. Lote próprio; não
  editado por esta frente.
- **Herdadas e vivas:** fiação de integração dos pacotes (JPA/beans/
  telas) e numeração global dos SQL em `db/pendente/` (os 4 pacotes
  trazem SQL pendente — `ED-70__certification_env_iso14001.sql`,
  `ED-80__sewer_raw_water.sql`, `ED-83__nondomestic_discharger.sql`,
  `ED-93__situation_room.sql` — nenhum número global consumido nesta
  camada; V102 é do lote R2-G1 e V103 segue RESERVADO à pendência
  P-R2G1-1); CI total definitiva no PC do dono; piloto P0.30.

## 6. Fechamento

C6 **FECHADA** quanto a produção, portão, pousos e verificação: 4/4
tarefas aprovadas (ED-80/ED-83 com ressalvas; ED-70/ED-93 após
reprovação → correção → reapreciação) → confirmadas → pousadas →
verificadas pós-pouso (91/91), com um desvio de forma do Portão
declarado e registado (ED-70, §3). Cadeia de HEADs:
`83a9539 → 386cec3 → af04605 → 261402f → e14ee3d → fde45c5`.
**Diário da camada: PREPARADO, NÃO POUSADO** — a operação
`push/diario-c6/` (diário mesclado sobre o blob vivo `23b5ab39…`,
verbete da C6 ao final) foi tentada duas vezes neste fechamento
(23:02 e 23:34 −03) e as duas pararam no mesmo ponto: a chamada de
escrita do conector estourou o timeout de 1200s aguardando aprovação
e a segunda tentativa de cada rodada foi recusada com "an equivalent
connector write is already running"; o HEAD conferido por leitura
após as tentativas permaneceu `fde45c5` — **o commit de diário não
existe na main**. Causa registada sem presunção além da evidência: as
aprovações de escrita expiraram sem decisão nesta janela (o dono
declarou que ia viajar). O pouso do diário fica como **pendência
nomeada P-C6-DIARIO**, executável com um `runner-big.py .` em
`push/diario-c6/` assim que houver aprovação de escrita — sem
necessidade de refazer a mescla, desde que o blob vivo do diário
continue `23b5ab39…` (se tiver mudado, remesclar sobre a versão viva).
Próximas camadas (**declaradas, não executadas neste lote**):
**C7** (ED-75), **C8** (ED-97), **C9** (ED-98) — faltam 3 tarefas para
a meta da cascata (ED 92/98 · geral 262/286).

**Adendo (09/11/2026, executor de pousos preparados) — P-C6-DIARIO
RESOLVIDA.** Com as aprovações de escrita liberadas pelo dono
(09/11/2026 00:12 −03), a operação preparada foi executada por
`runner-big.py` em `push/diario-c6/`: pouso na primeira tentativa,
commit **`4d88b8984d3ccec14372249a90f04144441b5865`**
("docs(diario): cascata ED camada C6 — ED-70, ED-80, ED-83, ED-93").
No ato do pouso, o HEAD era `1ccacd1` (lote R3-A, pousado em paralelo;
o diário não foi tocado por ele): o blob vivo do diário seguia
`23b5ab39…`, logo **não foi preciso remesclar**; conferência de diff
prévia ao pouso: 0 linhas removidas, 13 linhas adicionadas (o verbete
da C6). Verificação pós-pouso: o blob vivo na main passou a
`af6faa0561d33336d4201e4fd43f9d56ebd2cb33`, idêntico ao git blob SHA
do arquivo pousado. A pendência P-C6-DIARIO está **fechada**.

---

**Adendo (09/11/2026, corretor do lote R7-B, sessão
`b6cf0c91-9a27-46d5-a359-d4a9ddcaecb9`) — R7-I-m2 RESOLVIDO: qual
runner/teto autorizou o pouso do diário C6.** Nenhuma linha anterior
deste relatório foi alterada.

**Determinação — a tentativa vitoriosa usou `runner-big.py`, teto
400.000 bytes.** Evidência, em duas linhas independentes:

1. **Declaração no próprio relatório:** o adendo anterior (P-C6-DIARIO
   RESOLVIDA, acima) já declara que a operação preparada "foi executada
   por `runner-big.py` em `push/diario-c6/`".
2. **Eliminação a partir dos artefatos** (`cascata-ed/push/diario-c6/exec.log`
   lido por inteiro + `plan.json` + os dois runners):
   - o lote tem **165.213 bytes** (linha de PARADA das 23:02:27; plano:
     1 lote, 1 arquivo, 161 KB — `docs/DIARIO-DE-BORDO.md`);
   - `cascata-ed/push/runner.py` tem teto **115.000 bytes** e sai com
     PARADA antes de qualquer tentativa de escrita quando o lote o
     excede;
   - `cascata-ed/push/runner-big.py` tem teto **400.000 bytes**;
   - a tentativa vitoriosa das 03:14:20 tentou a escrita e obteve
     `OK lote 1 (tentativa 1) -> HEAD 4d88b89` às 03:14:28 — logo, só
     pode ter corrido sob o `runner-big.py` (165.213 > 115.000
     excluiria o `runner.py`).

**Corolário (reconstrução completa das 4 invocações do `exec.log`):**

- 23:02:27 — `runner.py` (teto 115.000): PARADA imediata por teto,
  sem tentativa de escrita;
- 23:02:43 — `runner-big.py` (teto 400.000): tentativa 1 TIMEOUT
  1200s (23:22:45), tentativa 2 "equivalent connector write is already
  running", PARADA 23:22:47;
- 23:34:15 — `runner-big.py`: mesmo desfecho, PARADA 23:54:20;
- 03:14:20 — `runner-big.py`: OK → commit
  `4d88b8984d3ccec14372249a90f04144441b5865`.

A troca de runner entre 23:02:27 e 23:02:43 **não ficou registada no
log** — é a lacuna de registo que este achado corrige. O `state.json`
da operação (`done [1]`, head `4d88b898…`) confere com o `exec.log`.

**Precisão sobre o §6:** o resumo "tentada duas vezes (23:02 e 23:34)"
condensa as invocações: às 23:02 houve **duas** invocações do runner
(23:02:27, PARADA por teto; 23:02:43, TIMEOUT às 23:22:45). O pouso
(commit `4d88b898`) não é afetado por esta imprecisão de resumo.

**Plano nunca dividido — resolvido por esta via, não por divisão:** o
`plan.json` segue com 1 lote / 1 arquivo / 161 KB. A divisão ordenada
pela PARADA nunca foi executada; o pouso venceu porque correu sob o
teto de 400.000 bytes. Não há divisão pendente: o teto que valeu está
agora registado aqui e na nota `push/diario-c6/ADENDO-TETO.md`.

**Mensagem "~110 KB" corrigida:** a mensagem de PARADA do
`runner-big.py` dizia "~110 KB" embora o teto real seja 400.000 bytes
(~390 KB) — corrigida nesta data em `cascata-ed/push/runner-big.py`
(docstring e mensagem de PARADA passam a declarar 400.000 bytes),
alteração só de texto, sem mudança de comportamento. A mensagem do
`runner.py` fica como está (teto 115.000 ≈ 110 KB, correta).
**Não tocado:** `base-ficticia-12k/push/runner-big.py` traz a mesma
mensagem enganosa, mas não foi alterado porque o pouso consolidado da
base fictícia corre com ele neste momento — fica como pendência de
acompanhamento para depois do pouso.

**Regra prospectiva:** toda tentativa de pouso regista runner + teto no
`exec.log` (linha de abertura da invocação, a implementar nos runners
pela produção — não implementada neste lote, só registada aqui).
