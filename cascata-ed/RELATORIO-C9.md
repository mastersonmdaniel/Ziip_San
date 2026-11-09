# Relatório da Camada C9 — Cascata ED (ED-98) + FECHO CONSOLIDADO DA CASCATA

Coordenador da camada C9 (sessão do Portão), 09/11/2026. Autoridade:
`MAPA-CASCATA.md`; método: `PROTOCOLO-ESTEIRA.md` (esteira com
coordenador-portão, ordem do dono de 08/11/2026 18:04) e as 7 regras
da cascata.

## 1. A camada C9 — ED-98 pousada e verificada

**ED-98 (B) — Agenda de obrigações regulatórias e de controle do
tenant (GES-05)** — deps ED-97, P3.15, ED-07, P2.25 (todas
executadas). Entrega: 17 arquivos novos (90.349 bytes) em
`shared/ziipsan-core`, subpacote `br.com.ziipsan.core.regulatory.agenda`
(8 classes de produção, 6 de teste, SQL pendente e doc do pacote).

O que a ED-98 entregou, contra o aceite:

- Calendário de obrigações por tenant gerado do perfil `reg.*` e da
  natureza jurídica (`reg.obligations`, classe d — parâmetro definido
  no SQL pendente, no padrão da ED-97): ciclo anual do SINISA,
  relatório anual de tarifa social, declaração anual de quitação
  (fatura de maio), balanço de perdas da NR 15, remessa bimestral ao
  e-Sfinge (IN TC-28/2021 e alterações), DMR, SISAGUA, publicação de
  reajuste com antecedência mínima **executável** de 30 dias da
  data-base, RREO e RGF do ente quando aplicável.
- Cada item com responsável, evidência, situação e lembrete pelos
  alertas da ED-07 (via `ReminderPort`, fiação nomeada P-ED98-03).
- Regras das obrigações lidas do catálogo de normas da P3.15 — a
  `RegulatoryLibrary` real é compilada e exercitada nos testes.
- Evidência de envio dos relatórios da ED-97 ligada ao item
  (RAS-08), com conferência de tenant, período, tipo e estado de
  envio (correção D1).
- Fiscalização recebida (agência, Tribunal de Contas, órgão
  ambiental) registrada como ocorrência da P2.25 via `OccurrencePort`
  (JOR-24; fiação nomeada P-ED98-02), com plano de ação e lembrete
  nesta agenda.
- MTR, CDF e DMR como item manual travado enquanto a ED-104 não for
  liberada (RAS-17).
- Alerta antecipado de norma com data de efeito futura, a partir da
  data de efeito da P1.34 (`NormEffectivenessService` real, via
  espelho); norma sem data não alerta.
- Teste GES-05 com tenants de naturezas jurídicas diferentes
  gerando calendários diferentes (diferença atribuível à natureza,
  não à flag — correção D3).

**Ciclo da esteira (5 frentes, sessões distintas):**

| Estágio | Artefato | Veredito/resultado |
|---|---|---|
| Produção | RELATORIO-ED-98.md | JUnit 17/17; PG 3 pos., 6/6 neg. |
| Revisão | REVISAO-ED-98.md | **APROVADO COM RESSALVAS** — D1, D2, D4 moderados; D3, D5, D6, D7 menores; sondas S1–S8 |
| Correção | RELATORIO-CORRECAO-ED-98.md | D1–D7 por construção; JUnit **39/39**; PG **12/12** neg. |
| Reapreciação | REVISAO-REAPRECIACAO-ED-98.md | **APROVADO** — sondas refeitas 44/44; RA-01/RA-02 → P-ED98-07 |
| Confirmação | CONFIRMACAO-ED-98.md | **CONFIRMADO** — hashes 17/17; placares reexecutados |
| Portão | PORTAO-REGISTRO-ED-98.md | 4 itens declarados; Regra 3 mecânica = falso positivo demonstrado (§3.2), portão não editado |

**Pouso:** commit `020909350a238c7444c0a2b5e578baca216ee8c4`
(lote único, HEAD no ato `b143cba6`); verificação pós-pouso
**17/17 byte-idênticos** (SHA-1 de blob git). **Diário:** commit
`f659bad8396229af39bc5cfad423f7291386c410` (blob verificado
`12d1b19e…` = mescla sobre o blob vivo, 0 remoções).

**Pendências nomeadas da camada:** P-ED98-01 (ampliada: dia de
vencimento e periodicidades `[A DEFINIR]`, sem fonte), P-ED98-02
(fiação OccurrencePort→P2.25), P-ED98-03 (fiação
ReminderPort→TenantAlertService da ED-07), P-ED98-04 (prova externa,
categoria B), P-ED98-05 (persistência JDBC e numeração V100+ do SQL
pendente, na integração), P-ED98-06 (risco residual do casamento
regra×catálogo por token), P-ED98-07 (amarração definitiva perfil
ED-97 × obrigação, na integração; cobre RA-01/RA-02).

## 2. Fecho consolidado da cascata — as 9 camadas

A cascata destravada pela P3.9 (42 tarefas em 9 camadas topológicas
+ o micro-lote P3.22 N2 levado pela C1) está **integralmente
executada, aprovada sob a esteira e pousada na main, com verificação
pós-pouso por hash em cada camada**:

| Camada | Tarefas | Fechamento |
|---|---|---|
| C1 | ED-41, ED-73 (+ micro-lote P3.22 N2) | diário `c4bfa2c` |
| C2 | ED-10, ED-20, ED-42, ED-54, ED-101 | diário `4c357e3` |
| C3 | ED-23, ED-43, ED-50, ED-56, ED-58, ED-66, ED-91, ED-92 | diário `04c626b` |
| C4 | ED-44, ED-59, ED-62, ED-71, ED-72, ED-77, ED-85, ED-87, ED-89, ED-96 | diário `e54235b` |
| C5 | ED-49, ED-78, ED-79, ED-81, ED-82, ED-86, ED-90, ED-94, ED-95, ED-105 | diário `9d1acf6` (+ reparos) |
| C6 | ED-70, ED-80, ED-83, ED-93 | diário `4d88b89` (P-C6-DIARIO resolvida em 09/11) |
| C7 | ED-75 | diário `76d438dd` (P-C7-POUSO fechada em 09/11) |
| C8 | ED-97 | diário `6625ed7` |
| C9 | ED-98 | diário `f659bad8` |

**Placar final: ED 92/98 · geral 262/286 — a meta da cascata foi
atingida.** O placar saiu de ED 50/98 · 220/286 (partida, 08/11)
para ED 92/98 · 262/286 só com tarefas da cascata verificadas na
main. Qualidade do percurso: nenhuma camada pousou sem os 4 itens
do portão; reprovações reais aconteceram e foram corrigidas antes
do pouso (ED-54 e ED-81 na primeira volta; ED-70 e ED-93 idem;
ressalvas corrigidas por construção em C4, C5, C8 e C9); dois
desvios de forma foram declarados e reparados sem reescrever
histórico (forma do Portão na ED-70; colisão de diário C5 × R2-G1);
o falso positivo P-ESTEIRA-01 do portão foi corrigido pelo rito
próprio e o da Regra 3 na C9 foi demonstrado pelo §3.2.

**Fora da cascata (inalterado, por desenho):** ED-01 (raiz externa —
aceite é marco de mundo real: ensaios do roteiro VISAN sobre o
piloto, trilha LC) e as 5 sob demanda do dono: ED-51, ED-68,
ED-102, ED-103, ED-104. No placar geral restam ainda P3.13, P4.6
(sob demanda), GL.4 e LC.1–LC.15 — todos de raiz externa à cascata.

## 3. Pendências sobreviventes de todas as camadas (consolidado)

Nenhuma é condição de pouso; todas estão nomeadas nos relatórios
das camadas/pacotes. Agrupadas por natureza:

**(a) Integração (fecha na integração/CI, não numa camada):**
fiação JDBC/JPA/beans e telas (console P1.6, Central P2.12) de
todos os pacotes da cascata; numeração global V100+ (P3.12) de
todos os SQL em `db/pendente/`; persistência JDBC das ED-97/ED-98
(P-ED97-05, P-ED98-05); P-ED98-02 e P-ED98-03 (portas da ED-98);
P-ED98-07 (perfil ED-97 × obrigação); P-ED97-04 (fiação/formatos);
P-ED44-01/02/03; P-ED58-07; P-ED91-01; P-ED78-05; P-ED81-01;
P-ED82-04; P-ED89-02 ampliada; P-ED71-R1/R2; fiação do snapshot
ED-56→ED-54; constraint de não-sobreposição da ED-50 no banco.

**(b) Provas externas — categoria B (mundo real, nunca fingidas):**
provedores reais de canal e POC-20/SAMAE 2.19 (C1); contas reais e
POC-52–54/TR/SAMAE (C2); provedor real da ED-43 e ensaios POC/SAMAE
(C3); envio real às agências e lista positiva real da ANA
(P-ED97-03); prova externa da ED-98 (P-ED98-04); provas em pilha
PG 16 definitiva (P-ED82-05, P-ED78-06).

**(c) Valores/decisões do dono ou de fonte externa (sem fonte hoje):**
os 14 valores abertos da P3.9 (fecham na contratação); títulos do
Anexo A da ISO/IEC 27001 (licença × reescrita, C1); planilha
comercial da ED-10 e SLA da ED-42; leiautes oficiais SISARIS/AGIR e
ato/leiaute ARESC `[SEM FONTE]` (P-ED97-01/02); valores da Portaria
MCID 788/2024 (P-ED77-01..06); dia de vencimento e periodicidades
da ED-98 (P-ED98-01); P-ED105-01..07 (valores `[SEM FONTE]`);
P-ED79-10 (decisão de fundo do LOS); V103 segue RESERVADO à
pendência P-R2G1-1 (desenho da chave de remessa — decisão do dono,
lote paralelo, fora da cascata).

**(d) Nomeadas por pacote, vivas:** P-ED49-02/03; P-ED78-01..09;
P-ED79-03/06/09; P-ED81-01..05; P-ED82-04/05; P-ED86-01..03;
P-ED90-01..04; P-ED94-01..04; P-ED95-01..06; P-ED105-01..07;
P-ED70-01..06; P-ED80-01..05; P-ED83-01..07; P-ED93-01..06;
P-ED75-02..05; P-ED77-01..06; P-ED85-01; P-ED97-01..05;
P-ED98-01..07; herdadas PEND-01/PEND-02 da P2.21; P-ED98-06
(risco residual do casamento por token).

**(e) Resolvidas desde o registro original (não contar como
abertas):** P-C6-DIARIO (09/11, `4d88b89`); P-C7-POUSO (09/11,
`625eb060`); P-ED75-01 (resolvida pela ED-97 na C8); P-ED75-06
(fechada com o pouso da C7); P-ED92-03 (fechada pelo consumidor
ED-85 na C4); P-ESTEIRA-01 (portão corrigido pelo rito próprio em
09/11); R2-G1 (V102 pousado; resta só o desenho da V103 em (c)).

**(f) Gargalo permanente, fora das camadas:** CI total definitiva
sobre o HEAD corrente — roda só no PC do dono (roteiro pronto;
"na main" ≠ "provado integrado" até ela rodar). O piloto P0.30 foi
redefinido pelo dono (decisão nº 34: base fictícia de testes,
12.000 UCs, modelo EMASA) — o pacote `base-ficticia-12k` concluiu
localmente e pousou após o "libera" de 09/11 01:40, em trilha
paralela à cascata.

## 4. Declaração formal de fechamento

**A cascata ED está FECHADA.** As 9 camadas (C1–C9) e as 42 tarefas
foram produzidas, revisadas, corrigidas quando reprovadas ou
ressalvadas, confirmadas por agentes independentes, aprovadas no
portão (mecânico ou, num único caso de falso positivo demonstrado,
pela rota do §3.2), pousadas na `main` e verificadas pós-pouso por
hash. Placar final **ED 92/98 · geral 262/286**. Este relatório,
o `ESTADO.md` atualizado e o verbete da C9 no diário de bordo são
os atos formais de fechamento.

## Adendo datado — 09/11/2026 ~02:15 −03 (correção R7-B5, Rodada 7 da auditoria contínua)

**Divergência e-Sfinge (item 29) — registrada, sem alteração do pacote pousado.** A divergência do item 29 de `DECISOES-DO-DONO.md` — plano: remessa ao e-Sfinge **"mensal desde 01/01/2022"** × ED-98: remessa **"bimestral, IN TC-28/2021"** — **não é citada em nenhum ponto do pacote ED-98** nem em nenhuma pendência P-ED98-01..07 (verificado pelo revisor 1, Rodada 7). O pacote adota o bimestral como se a divergência não existisse. Fica registrado:

- O **bimestral segue o aceite da ED-98** (não o plano): a ED-98 entrega "remessa bimestral ao e-Sfinge (IN TC-28/2021 e alterações)" — é o que está pousado (commit `02090935`, verificação pós-pouso 17/17) e nada neste adendo o altera.
- A **decisão 29 segue com o dono** — obter o leiaute vigente do e-Sfinge do TCE/SC e resolver a divergência de periodicidade das fontes. Não decidida aqui; não é condição de pouso nem reabre a camada C9.

**Observação adicional do revisor 1 (para a próxima revisão do pacote, sem alteração de código neste lote):** `AgendaPorts.NormRuleView.matchesToken` usa `toUpperCase()` sem `Locale` — padronizar (ex.: `Locale.ROOT`) na próxima revisão do pacote ED-98.

Fonte: Rodada 7 da auditoria contínua, revisor 1 via consolidado `00-CONSOLIDADO-RODADA7.md` §3, achado R7-B5 (menor, confirmado). Correção executada pelo corretor do lote R7-C (sessão `3e666a70-a89e-48af-a0f4-706cf81be20d`, 09/11/2026 ~02:15 −03), por adendo datado em append puro — nenhuma linha original reescrita; não é código. Relatório do lote: `correcoes-r7-c/RELATORIO-CORRECAO-R7-C.md`.

## Errata datada — 09/11/2026 (lote R8-C, achado A-03 da Rodada 8)

**Autor da errata:** produtor do lote R8-C (sessão `66187316-31e4-42cc-ba7d-ff4adb372ac8`). **Método:** errata em append puro; nenhuma linha anterior deste relatório foi alterada.

**Objecto:** a "Observação adicional do revisor 1" do adendo R7-B5 acima (`AgendaPorts.NormRuleView.matchesToken` usaria `toUpperCase()` sem `Locale`, a padronizar com `Locale.ROOT` na próxima revisão do pacote ED-98).

**Veredicto: a observação está desactualizada e fica SEM EFEITO; nenhum código a alterar.**

Verificação directa neste lote (09/11/2026, ficheiros locais da entrega ED-98, sem GitHub):

- `ed-98/entrega/shared/ziipsan-core/src/main/java/br/com/ziipsan/core/regulatory/agenda/AgendaPorts.java:60-63` — `matchesToken` **não** chama `toUpperCase()`; delega os dois lados a `TextFolding.fold()` (`TextFolding.fold(token)`, `TextFolding.fold(catalogKey)`, `TextFolding.fold(title)`).
- `ed-98/entrega/shared/ziipsan-core/src/main/java/br/com/ziipsan/core/regulatory/agenda/TextFolding.java:24` — `fold()` já usa `toUpperCase(Locale.ROOT)` (com normalização NFD e remoção de marcas diacríticas). A padronização sugerida **já existe** no código.
- A premissa "não há `*.java`" que acompanhava a observação é **falsa**: contam-se 6.548 ficheiros `*.java` na árvore `~/workspace/ziipsan-producao/` (contagem directa neste lote; a R8 cita 6.501, em âmbito de contagem ligeiramente distinto — em qualquer dos dois números, a premissa não se sustenta).

Corroboração: Rodada 8, revisor 1 §3 (citado no consolidado R8, A-03) — `TextFolding.fold` usa `Locale.ROOT`.

**Nota de precisão (atribuição):** a observação do adendo R7-B5 é atribuída ora ao "revisor 1" (texto do adendo) ora ao "confirmador do R7-C" (consolidado R8, A-03). Regista-se a oscilação sem atribuir culpa: a fonte primária é o texto do adendo R7-B5 tal como está escrito; o que fica sem efeito é a **observação técnica**, seja qual for o seu autor. Nada neste lote altera o adendo R7-B5 no resto (a divergência e-Sfinge mensal × bimestral mantém-se registada e por resolver pela decisão 29).
