# ZiipSan — Registro do placar em duas dimensões (correção M2 + ressalva M4)

**Origem:** achados M2 e M4 da revisão geral de 08/11/2026
(`~/workspace/revisao-geral-2026-11-08/00-CONSOLIDADO.md` e
`04-aceite-completude.md`, frente 4). Lote 3 de correções, parte de
REGISTRO. Produzido em 08/11/2026, somente a partir dos relatórios
dos próprios pacotes — nenhuma classificação sem evidência no
relatório da tarefa. Nenhum código alterado, nenhum push/commit.

## 1. Reconciliação do universo (declarada)

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo do plano v3.0 | 286 | `ANALISE-GERAL-MODULOS.md` (só leitura), método item 1 |
| Executadas — foto das Ondas 1–11 | 217 | ANALISE: 100 pré-retomada + 117 produzidas na retomada |
| Executadas em 08/11, fora das Ondas 1–11 | +2 | P3.24 e ED-27 (produzidas e aprovadas com revisão cruzada em 08/11; relatórios locais próprios) |
| **Universo deste registro** | **219** | 217 + 2 |
| Com pacote local (pasta em `ziipsan-producao/` com RELATORIO) | **119** | 117 das Ondas 1–11 + P3.24 + ED-27 — conferido por listagem integral das pastas |
| Pré-retomada (sem pacote/relatório local por tarefa) | **100** | P0 47/47 + P1 41/41 + P2 12/34, conforme ANALISE, método item 2 |

Conferências: o `MAPA-ONDAS-CORRIGIDO.md` lista 118 tarefas nas
Ondas 1–11, das quais a P3.13 **não** foi executada (só relatório de
verificação — dependência P0.30 inexistente); 118 − 1 = 117. As
pastas locais de tarefa são 121 = 119 executadas + P3.13 (não
executada) + P3.9 (minutas, onda própria posterior, não executada
nesta foto). 100 + 119 = 219 ✓.

## 2. Critério da segunda dimensão (aplicado uniformemente)

Classifica-se pelo **relatório da própria tarefa**, nunca por
presunção:

- **Aceite completo** — o relatório não declara nenhum item de
  aceite parcial, não executado ou pendente além das pendências
  universais do fluxo (fiação de integração, numeração Flyway e a
  prova da CI total no PC do dono, que valem para **todos** os
  pacotes — a ANALISE já registra "executado ≠ provado" para o
  conjunto inteiro; isso não é ensaio externo de uma tarefa).
- **Núcleo entregue + ensaio externo pendente** — o relatório
  declara item de aceite (ou parte dele) parcial / não executado /
  não demonstrável / pendente cuja resolução depende de fator fora
  do pacote: ensaio ou homologação real (SEFAZ, VISAN/POC, SAMAE
  "sem falha", PSC/ACT, PSP), piloto ou cliente real (inclusive
  insumo que só ele fornece: leiaute do fornecedor, rede real,
  leiaute oficial de órgão — TCE/SC), hardware/aparelho real,
  execução em ambiente real (servidor limpo, banco real, massa
  oficial, máquina de referência, gráfica com amostra impressa) ou
  portão externo ao pacote (ex.: solver sob o ADR-0010 na ED-36).
  Cada linha da tabela traz a evidência (citação curta ou seção).
- **Núcleo + pendência declarada que NÃO é ensaio externo**
  (categoria de honestidade, fora do eixo M2) — o relatório declara
  parcial/pendência de natureza interna, de desenho, de recorte
  entre tarefas ou de insumo documental sem fonte. Essas tarefas
  **não** foram contadas como "aceite completo" para não inflar a
  primeira categoria; ficam nomeadas na seção 5.
- **Sem classificação por relatório local** — as 100 pré-retomada
  (seção 6). Não foram presumidas "aceite completo".

Método: varredura por grep (parcial / não executado / pendência /
ensaio / homologação / piloto / hardware e correlatos) sobre os 119
relatórios locais, com leitura de contexto de todas as ocorrências
relevantes, partindo do inventário (a) da frente 4 e conferindo
cada item no relatório da tarefa — o inventário foi confirmado e
ampliado onde o relatório de outra tarefa declarava o mesmo tipo de
pendência (ex.: ED-04, ED-05, ED-06, ED-07, ED-24, ED-26, P2.20,
P3.2, P4.5, que a frente 4 não listava no inventário (a)).

## 3. Categoria B — Núcleo entregue + ensaio externo pendente (57)

### P2 (7)

| Tarefa | Evidência no próprio relatório |
|---|---|
| P2.6 | "ACEITE EXTERNO (o maior restante da P2.6): homologação na SEFAZ do piloto com `tpAmb=2` e aceitação do e-CNPJ da Ziip/TGP como emitente de teste do modelo 75 — NÃO feitos e não fingidos" (RELATORIO-P2.6, Pendências) |
| P2.7 | "Leiaute real do fornecedor do piloto … depende de documento externo que não está no repo/plano. É a única parte do aceite que não pode ser fechada sem esse insumo." (RELATORIO-P2.7, Suposições/Pendências) |
| P2.8 | Aceite item 1: "PENDÊNCIA DE HOMOLOGAÇÃO (processo, não código) … a aprovação com amostra impressa depende da gráfica/do piloto e não pode ser cumprida nesta VM." (RELATORIO-P2.8, Cobertura do aceite) |
| P2.9 | A carga de 300 mil ligações não rodou contra banco real; o relatório lista "o que só se prova na execução no PC do dono" (semeadura, faturamento de 100 mil contas, p95) (RELATORIO-P2.9; inventário (a) da frente 4) |
| P2.11 | Alertas "✅ parcial — … o disparo ponta a ponta em homologação" pendente; pendência 2: "'Em homologação' (aceite): os testes entregues são unitários das regras; o disparo ponta a ponta em homologação … depende das pendências 1 e 3 e da CI" (RELATORIO-P2.11) |
| P2.20 | Itens 13/14 da conferência: "⏳ pendente externo (inalterado)": homologação SEFAZ do fluxo de eventos e POC-49/50 (VISAN); pendência 6 repete (RELATORIO-P2.20) |
| P2.33 | "homologação real na SEFAZ = aceite externo pendente" (tabela de aceite); pendência 1: "Aceite externo: homologação SEFAZ tpAmb=2 (inutilização e reenvio reais)" (RELATORIO-P2.33) |

### P3 (10)

| Tarefa | Evidência no próprio relatório |
|---|---|
| P3.2 | Item 12: "❌ pendente real: exige PSP/homologação; nada foi presumido" (fluxos do Manual Pix conferidos com o PSP) (RELATORIO-P3.2) |
| P3.6 | Item 1: "✅ parcial honesto: … A transmissão real ao ambiente nacional NÃO foi implementada nem é testável aqui … Ninguém deve ler esta entrega como 'NFS-e autorizada'." (RELATORIO-P3.6) |
| P3.7 | "A homologação real NÃO foi feita e não é fingida" (RELATORIO-P3.7, conforme cruzamento da frente 4) |
| P3.10 | "Simulação de mesa ⚠️ parcial honesto … a simulação com pessoas/piloto, com ata, NÃO foi realizada"; pendência 5: "Simulação de mesa com pessoas (piloto/encarregado, com ata): pendente do piloto." (RELATORIO-P3.10) |
| P3.11 | Aceite itens 1/2/5: "PARCIAL/PENDENTE COM RESSALVA ESTRUTURAL", "PENDENTE DE EXECUÇÃO" e "PROGRAMA PRONTO, EXECUÇÃO PENDENTE (PC do dono)" — programa de exercícios (réplica/PSP/SEFAZ) pronto, execução pendente (RELATORIO-P3.11) |
| P3.23 | "em ambiente real é da execução no PC do dono (pendência 2)"; "O ensaio real é a pendência 2"; o modelo de cobertura "só existe depois do primeiro ensaio real" (RELATORIO-P3.23) |
| P3.25 | Arte SVG/sprite da P1.30 inexistente; E2E GIS parcial (códigos sem correspondência listados); axe não executado; medição de tiles pendente (RELATORIO-P3.25; inventário (a) da frente 4) |
| P3.26 | "50 km sintéticos em até 2 h 🔗 pendência de medição real (critério de homologação/perf na máquina de referência) — não medido nem fingido"; pendência 4: benchmark e rede real do piloto na homologação local (RELATORIO-P3.26) |
| P3.33 | Resumo do aceite: "1 dependência de ambiente declarada (execução do ensaio/RTO medido)" — ensaios reais de restauração seletiva e RTO medido pendentes (RELATORIO-P3.33) |
| P3.34 | Pendência 1: "Homologação externa PSC/ACT (não executada)"; conferência DOC-ICP-15.03 parcial por desenho (RELATORIO-P3.34) |

### P4 (1) e GL (3)

| Tarefa | Evidência no próprio relatório |
|---|---|
| P4.5 | Pendência 2: "Homologação com o PSP do piloto é externa e não foi presumida" (RELATORIO-P4.5) |
| GL.1 | "execução no piloto pendente por natureza" e registro PARCIAL declarando etapas não executadas — carga real do cadastro do piloto é o objeto da tarefa (RELATORIO-GL.1) |
| GL.2 | "Nenhum ciclo real foi executado e nenhum resultado de ensaio, tempo, contagem ou divergência é afirmado neste relatório" — primeiro registro real do paralelo pendente (RELATORIO-GL.2) |
| GL.3 | "O ensaio de verdade … é ele que gera o primeiro `registro-ensaio.md` real … Nenhum tempo, contagem ou resultado de ensaio real é afirmado neste relatório" — harness 65/65 é com dublês (RELATORIO-GL.3) |

### ED (36)

| Tarefa | Evidência no próprio relatório |
|---|---|
| ED-04 | Itens de aceite "PENDENTE — não executado": subir em servidor Linux limpo; subir em Windows Server 2022+; carregar a exportação e passar nos testes de fumaça nos dois ambientes; pendência 1: "Ensaios reais (bloqueante do aceite) … Só pode ser feito na infra do dono." (RELATORIO-ED-04) |
| ED-05 | Cabeçalho: "VEREDITO: PARCIAL — por desenho, não por falha"; "dois itens exigem ensaio real com pessoas/ambiente" (ensaio da contingência; item 3 do SAMAE no ensaio) (RELATORIO-ED-05) |
| ED-06 | Pendência 3: "Ensaios externos: POC VISAN oficial, SAMAE 2.7 com servidor LDAP/AD de teste, SSO com provedores reais e SCIM ponta a ponta — no ambiente do dono/CI"; item 2.7 do SAMAE: "⚠️ ensaio … a prova é o ensaio no ambiente, não esta entrega" (RELATORIO-ED-06) |
| ED-07 | Item 2.5 do SAMAE: "⚠️ mecanismo + roteiro de ensaio entregue; ensaio real é na integração/PC do dono"; pendência 4: "Ensaio real do item 2.5 do SAMAE e CI total … no PC do [dono]" (RELATORIO-ED-07) |
| ED-08 | **Ver seção 7 (ressalva M4, nomeada).** Matriz E2E verde no CI pendente da 1ª rodada do job |
| ED-09 | Item 3 parcial (mapeamento GSAN de produção depende da confirmação do legado do primeiro cliente e do leiaute dele — sem fonte, não inventado); pendência 5: "Ensaio real: a 'implantação simulada concluída' como fato exige rodar a jornada num tenant sintético em ambiente com banco" (RELATORIO-ED-09) |
| ED-11 | Item 3: "ATENDIDO no aparato; execução pendente" (nenhum tenant provisionado de verdade); pendência 3: "Ensaio real de treinamento (turma de verdade) depende de cliente/piloto — nenhum resultado de treinamento é afirmado neste pacote" (RELATORIO-ED-11) |
| ED-14 | Pendência 6: "POC-12..14 VISAN e item 2.9 SAMAE ponta a ponta exigem ambiente com banco e OS reais" (RELATORIO-ED-14) |
| ED-15 | Item 11 (SAMAE 2.10): coberto "na parte de regras e parcial na ponta a ponta (exige ensaio com rota e OS reais na integração)"; pendência 8 idem (RELATORIO-ED-15) |
| ED-16 | Itens SAMAE 2.11 e 2.28: "Parcial — ensaio pendente … 'sem falha' é crité[rio de ensaio]; o ensaio é externo a esta ent[rega]" (RELATORIO-ED-16, tabela de aceite) |
| ED-17 | Pendência 3: "Teste de carga não executado contra o banco … o roteiro completo (vazão contra o PostgreSQL particionado, EXPLAIN) roda na CI do PC do dono. Nenhum número de desempenho é afirmado." (RELATORIO-ED-17) |
| ED-18 | Pendência 2: testes Java e instrumentados não executados (sem JDK/SDK nesta VM) — ensaio com aparelho/OCR real fora da VM, declarado no relatório e no inventário (a) da frente 4 (RELATORIO-ED-18) |
| ED-19 | Itens 8 (forma fiscal ADR-0031 testada na homologação SEFAZ) e 17 (POC-15/16/21 VISAN e item 2.11 SAMAE): NÃO EXECUTADOS; item 16 parcial; pendências 1–4 (hardware Zebra/Bluetooth, Android, fiscal, integração) (RELATORIO-ED-19; cruzamento item a item da frente 4) |
| ED-22 | Item 1 (POC-51 VISAN): "⚠️ parcial honesto … 'passar na POC' é prova de ensaio com o roteiro VISAN, não demonstrável nesta VM"; item 15 (SAMAE 2.13): "⚠️ não demonstrável aqui: item de ensaio/homologação" (RELATORIO-ED-22) |
| ED-24 | Item 2.13 do SAMAE: "⚠️ NÃO ENSAIADO — o roteiro SAMAE não está nas fontes locais; … o ensaio é ato de integração com o roteiro real"; pendência 6 idem (RELATORIO-ED-24) |
| ED-25 | Itens 13/14: "⏳ ensaio externo: os roteiros SG não estão nesta VM; nada foi inventado — fica para o ensaio com o roteiro real" e "⏳ ensaio externo (item 2.13 do SAMAE)" (RELATORIO-ED-25) |
| ED-26 | Item 11 (SAMAE 2.14): "⚠️ não demonstrável aqui: é item de ensaio/homologação com o edital"; pendência 3: "SAMAE 2.14: prova é de ensaio, não de unidade." (RELATORIO-ED-26) |
| ED-27 | Item 7: "Item 2.14 do SAMAE sem falha — ⚠️ não demonstrável aqui: item de ensaio/homologação; … prova tipo ENSAIO marcada" (mapa POC-29/SAMAE em `DebitAcceptanceCoverage`) (RELATORIO-ED-27) |
| ED-29 | "POC real (tela + impressora, comissão) é ensaio futuro — o que passa aqui é a lógica dos itens POC-32/POC-33, não o ensaio"; pendência: "Ensaio da POC: POC-32/POC-33 reais … e o item 2.15 no ensaio do SAMAE seguem PENDENTES" (RELATORIO-ED-29) |
| ED-32 | Item 14: "POCs VISAN (POC-37..40, 66..68) e item 2.16.18 do SAMAE — NÃO verificáveis aqui: roteiros não estão no repo/pacotes locais … Ficam para o ensaio"; pendência 4: ensaios com os roteiros reais (RELATORIO-ED-32) |
| ED-33 | Item 2: "◐ PARCIAL — pendência declarada … o leiaute vigente do TCE/SC não está nas fontes locais … até lá, toda remessa carrega `layoutOfficial=false`"; operação no piloto (LC.13) pendente-condicionada (RELATORIO-ED-33) |
| ED-34 | Item 2.17 do SAMAE: "🔶 Coberto no domínio pelos testes acima; o ensaio com o roteiro SAMAE real é pendência de integração"; ensaios POC-41/POC-31 idem (RELATORIO-ED-34) |
| ED-35 | Item 2.19 do SAMAE: "🔶 coberto no domínio; ensaio com roteiro real = integração"; POC-47/POC-48: "ensaio VISAN real = integração" (RELATORIO-ED-35) |
| ED-36 | Cabeçalho: "entrega PARCIAL declarada"; item pgRouting: "⛔ CONDICIONADA — não implementada" — solver de rotas não implementado sob o portão do ADR-0010 (decisão O8-1) (RELATORIO-ED-36) |
| ED-37 | Item 2.17 do SAMAE: "🔶 coberto no domínio …; o ensaio com o roteiro real do SAMAE é da integraç[ão]"; pendência P-ED37-2 (ensaio) (RELATORIO-ED-37) |
| ED-38 | Cabeçalho declara entrega parcial nos itens que dependem "de ensaio com roteiro real (VISAN POC-69 e SAMAE 2.20 executados em ambiente de ensaio)" — o corpo corrige: ensaio real é integração, roteiro não está nesta VM (RELATORIO-ED-38; MENOR 1 da frente 4) |
| ED-39 | POC-55: "⛔ PENDENTE EXTERNO (aparelhos + contas)"; atos externos das lojas e publicação assetlinks/AASA pendentes (RELATORIO-ED-39) |
| ED-40 | Pendência P-ED40-3 (frontend/hardware): "impressão térmica no hardware do cliente e ensaio no hardware real — integração" (RELATORIO-ED-40) |
| ED-46 | "RNF-71 medido ⚠️ Não medido na massa oficial … a medição do RNF-71 exige a massa da P2.9 (300 mil ligações) em ambiente com banco — pendência P-ED46-3"; item POC-56 a POC-68: "⚠️ Parcial honesto" (2 POC não entregues, dependentes de P4.1/ED-18) (RELATORIO-ED-46) |
| ED-53 | Item SAMAE 2.8/2.27: "❌ não executado — ensaio é homologação com ambiente/piloto"; IT PostGIS compilada, não executada; build do frontend não executado (RELATORIO-ED-53) |
| ED-55 | Item migração de amostra: "⚠️ regra codificada …; o ensaio em si é externo (rede real, GL)"; pendência: "Ensaio com QGIS real abrindo o `.qgz` gerado" (RELATORIO-ED-55) |
| ED-64 | Item TR-1048: "⏳ ensaio externo: o roteiro TR não está nesta VM nem no repo"; símbolos: "a ARTE não existe porque o ICONES-E-SIMBOLOGIA não está nesta VM e o pipeline da P1.30 também não" (`presentInLibrary=false`) (RELATORIO-ED-64) |
| ED-65 | Cabeçalho: "Veredito: PARCIAL honesto — todo o núcleo construível sem […] pgRouting real, o ensaio TR-1049, a arte dos símbolos (P1.30) e o [aceite do dono seguem pendentes]" (RELATORIO-ED-65) |
| ED-67 | Cabeçalho: "sem piloto, sem GeoServer real e sem ambiente foi entregue com testes; conformidade TEAM Engine, binários GDAL, GeoServer real e o ensaio de saída são pendências nomeadas de integração/ensaio"; devolução da base: "ensaio real é pendência (P3.16)" (RELATORIO-ED-67) |
| ED-84 | "Pendências que são dele/agência (não bloqueiam o pacote): valores reais de `developer.study_fee`, `developer.reimbursement_rule`, `works.feasibility_deadline_days`, prazos por etapa e o checklist `works.network_acceptance_checklist` de cada agência" — parâmetros obrigatórios sem valor; Flyway do SQL pendente (RELATORIO-ED-84) |
| ED-88 | Item 7 (tarifa pela fonte da agência): "🔶 parcial honesto: … nenhuma fonte consultada enumera os códigos possíveis, e enumerá-los seria inventar dado da agência — a enumeração/semântica de cada fonte é pendência" (RELATORIO-ED-88) |

## 4. Categoria A — Aceite completo (47)

Sem item de aceite parcial/não executado de natureza externa
declarado no próprio relatório; restam nelas apenas as pendências
universais de integração/CI (seção 2). Relatório indicado para
conferência de cada uma:

**P2 (11):** P2.10 (`p2.10/entrega/RELATORIO-P2.10.md`), P2.16
(`p2.16/RELATORIO-P2.16.md`), P2.17 (`p2.17/RELATORIO-P2.17.md`),
P2.18 (`p2.18/RELATORIO-P2.18.md`), P2.23
(`p2.23/entrega/RELATORIO-P2.23.md`), P2.24
(`p2.24/RELATORIO-P2.24.md`), P2.28
(`p2.28/entrega/RELATORIO-P2.28.md`), P2.29
(`p2.29/entrega/RELATORIO-P2.29.md`), P2.31
(`p2.31/entrega/RELATORIO-P2.31.md`), P2.32
(`p2.32/entrega/RELATORIO-P2.32.md`), P2.34
(`p2.34/entrega/RELATORIO-P2.34.md`).

**P3 (23):** P3.4 (`p3.4/entrega/RELATORIO-P3.4.md`), P3.5
(`p3.5/entrega/RELATORIO-P3.5.md`), P3.8
(`p3.8/RELATORIO-P3.8.md`), P3.12 (`p3.12/RELATORIO-P3.12.md`),
P3.15 (`p3.15/entrega/RELATORIO-P3.15.md`), P3.16
(`p3.16/entrega/RELATORIO-P3.16.md`), P3.17
(`p3.17/entrega/RELATORIO-P3.17.md`), P3.18
(`p3.18/entrega/RELATORIO-P3.18.md`), P3.19
(`p3.19/RELATORIO-P3.19.md`), P3.20 (`p3.20/RELATORIO-P3.20.md`),
P3.21 (`p3.21/RELATORIO-P3.21.md`), P3.22
(`p3.22/entrega/RELATORIO-P3.22.md`), P3.24
(`p3.24/RELATORIO-P3.24.md` — parciais declarados são de fiação e o
marcador M1, leiaute FEBRABAN 150 [SEM FONTE], conferível contra o
manual do banco do piloto na integração; nenhum item de aceite
marcado parcial/ensaio), P3.27 (`p3.27/RELATORIO-P3.27.md`), P3.28
(`p3.28/RELATORIO-P3.28.md`), P3.29
(`p3.29/entrega/RELATORIO-P3.29.md`), P3.30
(`p3.30/RELATORIO-P3.30.md`), P3.32 (`p3.32/RELATORIO-P3.32.md`),
P3.35 (`p3.35/RELATORIO-P3.35.md`), P3.36
(`p3.36/entrega/RELATORIO-P3.36.md`), P3.37
(`p3.37/RELATORIO-P3.37.md`), P3.38 (`p3.38/RELATORIO-P3.38.md`),
P3.39 (`p3.39/RELATORIO-P3.39.md`).

**P4 (4):** P4.1 (`p4.1/RELATORIO-P4.1.md`), P4.2
(`p4.2/entrega/RELATORIO-P4.2.md` — item do arquivo SINISA "✅
parcial honesto por desenho": confirmado oficialmente que o canal é
o sistema web e não existe formato/API publicado; o pacote assistido
entregue é o desenho do aceite, não ensaio pendente), P4.7
(`p4.7/RELATORIO-P4.7.md`), P4.8 (`p4.8/RELATORIO-P4.8.md`).

**ED (9):** ED-13 (`ed-13/RELATORIO-ED-13.md`), ED-28
(`ed-28/RELATORIO-ED-28.md`), ED-47 (`ed-47/RELATORIO-ED-47.md`),
ED-48 (`ed-48/RELATORIO-ED-48.md`), ED-57
(`ed-57/RELATORIO-ED-57.md`), ED-63 (`ed-63/RELATORIO-ED-63.md` —
solvers EPANET/SWMM executados de verdade nesta VM, declarado no
relatório; restam IT esqueleto/CI, pendência universal), ED-69
(`ed-69/RELATORIO-ED-69.md`), ED-76 (`ed-76/RELATORIO-ED-76.md`),
ED-99 (`ed-99/RELATORIO-ED-99.md`).

## 5. Categoria C — Núcleo + pendência declarada que NÃO é ensaio externo (15)

Fora do eixo do M2, mas **não** contadas como aceite completo:

| Tarefa | Evidência / natureza da pendência |
|---|---|
| P2.5 | Revisão mediu o aceite em 22 itens com parciais restantes: JOR-10 ("sem faturar" como efeito de ocorrência), período parcial (consumo medido não proporcionalizado em 1ª/final) e fiação do `BenefitExpiryRule`/dependências — parciais internos de lógica/fiação (RELATORIO-P2.5, "Pendente / incerto") |
| P2.19 | Item do framework declarativo: "⚠️ PARCIAL (correção A5): declaração PRÓPRIA em arquivo, NOS MOLDES do PLT-16 + motor genérico" — parcial interno de desenho (RELATORIO-P2.19) |
| P2.21 | Três itens "⚠️ parcial": regras de conectividade (testes de comportamento faltam para contagem de grau/JUNCTION), validação/painel e comparação `pgr_connectedComponents` — incompletudes internas declaradas (RELATORIO-P2.21) |
| P2.22 | Tela de revisão "⚠️ parcial — falta (a) o antes/depois no mapa — depende do visualizador da GIS-06 (P3.25) … (b) a fiação da rota no frontend … e teste E2E da tela" — parcial interno/frontend (RELATORIO-P2.22) |
| P3.1 | Item 12 (split payment): "⚠️ parcial — … os códigos dos modos são desta implementação: o catálogo [da P2.4 não os confirma]" — parcial interno de fonte entre tarefas (RELATORIO-P3.1) |
| P3.3 | Itens 1 e 21 "⚠️ parcial" (contrato de configuração do realm; passkey) e itens 13/15 "⏳ pendente declarada" remetidos pelo próprio aceite a ED-38/LC.2 — sem ensaio externo próprio declarado (RELATORIO-P3.3) |
| P3.14 | Produzida parcial por desenho: patch Spring Boot aplicado; a tarefa segue aberta até a troca de linha Boot 4.1→4.2 (dez/2026–jan/2027), conforme MAPA-ONDAS-CORRIGIDO e RELATORIO-P3.14 — pendência temporal, não ensaio |
| P3.31 | Item 9: "⚠️ parcial declarado: … o conteúdo do catálogo de fábrica é do pacote `sanitation`/fonte jurídica, ausente das fontes desta tarefa (pendência 1)" — insumo documental interno ao projeto (RELATORIO-P3.31) |
| P3.40 | Dois itens "PARCIAL" (controllers/relatório sobre as tabelas; menu Privacidade ao perfil de encarregado) — parciais internos de fiação/frontend (RELATORIO-P3.40) |
| P3.41 | "⚠️ parcial declarado: a vigência/sobreposição usa o motor da P0.19 sobre tabela própria … é aceita como está — declarado parcial" — parcial interno de desenho (RELATORIO-P3.41) |
| ED-12 | POC-08, POC-10 e POC-11 marcados "Parcial" porque partes pertencem a outros módulos/P1.3 já existentes — parcial de recorte entre tarefas; a frente 4 concluiu: "sem parcial de aceite próprio além das pendências de integração" (RELATORIO-ED-12) |
| ED-30 | Seção "Parcial / pendente (honesto)": layout de arquivo [SEM FONTE] (manual do canal de birô/protesto contratado por tenant, atrás de porta) e códigos SG da fatia do SAMAE 2.15 a fornecer — insumos documentais, sem ensaio declarado como item parcial (RELATORIO-ED-30) |
| ED-31 | Seção "Parcial / pendente (honesto)": minutas (CDA, petição, arquivo) não existem como arquivos (mesma pendência da P3.9) e lei do ente sem fonte (parâmetros obrigatórios) — insumos documentais (RELATORIO-ED-31) |
| ED-74 | Cabeçalho: "Veredito: PARCIAL honesto — o mecanismo completo do pacote está entregue, [mas o] conteúdo do mapa pendente (pendência 1)" — conteúdo interno ao projeto (P3.40/P3.10) (RELATORIO-ED-74) |
| ED-100 | Item dos painéis no BI: "⚠️ Parcial declarado: definições dos 5 painéis + views entregues …; a publicação no Superset é fiação de integração (pendência 3)" — parcial de fiação (RELATORIO-ED-100) |

## 6. Pré-retomada — sem classificação por relatório local (100)

As 100 tarefas entregues antes da retomada (P0 47, P1 41 e, em P2,
as 12 nomeadas na ANALISE: P2.1, P2.2, P2.3, P2.4, P2.12, P2.13,
P2.14, P2.15, P2.25, P2.26, P2.27, P2.30) **não têm relatório local
por tarefa** em `ziipsan-producao/` — a base delas é a CI da base
integrada pré-retomada (reanálise de 07/11/2026; commits pré-04/11,
conforme a ANALISE). Por isso recebem a categoria declarada **"sem
classificação por relatório local"**: não são presumidas "aceite
completo" nem "ensaio pendente" — a segunda dimensão simplesmente
não é aferível tarefa a tarefa com a evidência local disponível.

## 7. M4 — Ressalva nomeada da ED-08 (integra este registro)

A ED-08 tem veredito formal **PARCIAL** na própria revisão cruzada:

- Fonte: `~/workspace/ziipsan-producao/ed-08/REVISAO-ED-08.md`.
- Veredito geral, literal: **"PARCIAL, aprovável com pendências"**.
- Item 1 do aceite (matriz E2E com Edge 88, Chrome 100 e Firefox 116
  **verde no CI**): **"PENDENTE — matriz configurada e carregável
  …; o verde não está comprovado …: só a primeira rodada do job
  `e2e-navegadores` no GitHub do dono comprova"**. No relatório da
  tarefa, o mesmo ponto consta como matriz E2E "configurada — verde
  pendente da 1ª rodada de CI".

**Registro:** a ED-08 conta como **executada COM RESSALVA** — fica
na categoria B deste registro (núcleo entregue + ensaio externo
pendente), com esta ressalva própria e nomeada por cima da
classificação genérica: a ressalva só cai quando existir a primeira
rodada verde da matriz E2E no CI. É o único caso, entre os 119
pacotes locais, de tarefa contada como executada cujo veredito
formal da revisão é expressamente PARCIAL (frente 4, MODERADO 3).

## 8. Totais

### Por dimensão (universo 219)

| Dimensão | Nº | % de 219 |
|---|---:|---:|
| Aceite completo (com relatório local) | 47 | 21,5% |
| Núcleo entregue + ensaio externo pendente | 57 | 26,0% |
| Núcleo + pendência declarada não externa (categoria C, seção 5) | 15 | 6,8% |
| Sem classificação por relatório local (pré-retomada) | 100 | 45,7% |
| **Total executadas** | **219** | 100% |

### Por fase

| Fase | Executadas no universo | A completo | B ensaio externo | C não externo | Sem classificação |
|---|---:|---:|---:|---:|---:|
| P0 | 47 | 0 | 0 | 0 | 47 |
| P1 | 41 | 0 | 0 | 0 | 41 |
| P2 | 34 | 11 | 7 | 4 | 12 |
| P3 | 39 (de 41; P3.9 e P3.13 não executadas) | 23 | 10 | 6 | 0 |
| P4 | 5 (de 6; P4.6 não executada) | 4 | 1 | 0 | 0 |
| GL | 3 (de 4; GL.4 não executada) | 0 | 3 | 0 | 0 |
| ED | 50 (de 98) | 9 | 36 | 5 | 0 |
| **Total** | **219** | **47** | **57** | **15** | **100** |

Por módulo: **não derivável sem inventar atribuição.** A ANALISE
atribui as tarefas da retomada a módulos por regra de caminho, mas
não publica a lista tarefa→módulo; sem essa lista, totais 2D por
módulo exigiriam reatribuir 119 tarefas por critério novo — fora do
método deste registro. Fica declarado o limite, sem número chutado.

## 9. Nota de uso

Este documento é o **insumo** para a rotina de status do ZiipSan
adotar a segunda dimensão no placar (217/219 executadas passam a
ser lidas como: aceite completo × núcleo + ensaio externo pendente,
com a categoria C e a ressalva da ED-08 nomeadas). A rotina em si
**não** foi alterada por este lote — só o registro foi produzido.

---

# Adendo de 09/11/2026 — Extensão do registro às 260 executadas (M2)

**Origem:** decisão do dono de 09/11/2026 (~00:10) — aplicar em
bloco os graves+moderados confirmados da 1ª revisão geral; o item
M2 executa-se sem nova consulta. Este adendo **não altera** nenhuma
linha das seções 1–9 acima: acrescenta a classificação das
executadas que estavam fora da foto de 219 e atualiza os totais.
Artefatos do lote: `EXTENSAO-M2-CLASSIFICACAO.md` (classificação)
e `EXTENSAO-M2-REVISAO.md` (revisão independente), nesta mesma
pasta.

## A1. Reconciliação do universo da extensão

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo do registro original (seções 1–9) | 219 | este registro |
| Executadas na foto das 22:05 de 08/11/2026 | 260 | rotina de status (259 na main + ED-75 aprovada) |
| **Classificadas neste adendo** | **41** | P3.9 + 40 EDs da cascata (C1–C7) |
| **Universo consolidado** | **260** | 219 + 41 ✓ |

As 41: **P3.9** e **ED-10, ED-20, ED-23, ED-41, ED-42, ED-43,
ED-44, ED-49, ED-50, ED-54, ED-56, ED-58, ED-59, ED-62, ED-66,
ED-70, ED-71, ED-72, ED-73, ED-75, ED-77, ED-78, ED-79, ED-80,
ED-81, ED-82, ED-83, ED-85, ED-86, ED-87, ED-89, ED-90, ED-91,
ED-92, ED-93, ED-94, ED-95, ED-96, ED-101, ED-105**.

## A2. Método e revisão independente

Classificação pelo relatório da própria tarefa, com o critério da
seção 2 **sem alteração** (incluídos os precedentes P3.24, P4.2,
ED-30/ED-31, ED-33, ED-69/ED-76). Revisão independente por sessão
distinta: amostra de **27 tarefas** (100% das 18 marcadas A, 7 de
B — P3.9, ED-10, ED-44, ED-54, ED-77, ED-80, ED-105 — e as 2 de C),
acima do mínimo de 1/3 das 41. **Veredito formal da revisão:
APROVADO COM RESSALVAS** — 25 conferem; 2 divergências, ambas com
citação literal do relatório da tarefa, **aplicadas neste
adendo**:

- **D-R1 — ED-93: A → B.** O item 9 do aceite (latência RNF/
  ADR-0012 com teste ponta a ponta) traz na própria linha
  "fronteira: até o navegador é P-ED93-03", e a P-ED93-03 é
  nomeada "(integração — **prova externa ao núcleo**): repetir a
  sonda de latência sobre o caminho integrado (**banco real +
  HTTP + navegador**) — nenhum número dela é latência de
  produção". Medição de desempenho em ambiente real pendente é B
  pelo critério e pelos precedentes P3.26, P3.33 e ED-46 — não se
  confunde com a CI total universal, da qual o relatório a separa.
- **D-R2 — ED-78: C → B.** As pendências internas citadas para o C
  existem (P-ED78-02 `[SEM FONTE]`, gap do ciclo de publicação do
  Plano de Gestão, P-ED78-08/09), mas a afirmação de "sem pendência
  externa" não se sustenta: **P-ED78-07** — "Submissão real à ERI e
  publicação efetiva… **são atos externos** — não publica";
  **P-ED78-04** — portão de publicação de ato de adoção da ERI
  (`NO_ACT_LOCATED` hoje), mesma natureza do portão CONAMA aceito
  como B em ED-80/ED-105; **P-ED78-01** — insumo de ato normativo
  de cada ERI (precedente ED-33 = B). No registro original, tarefa
  com pendência documental **e** externa foi classificada B
  (ex.: ED-09, ED-33).

Fronteiras julgadas procedentes pela revisão, sem divergência:
ED-10/ED-44/ED-91 (B por pendência "(externa, categoria B)" nomeada
pelo próprio relatório — precedente ED-84); ED-80/ED-105 (B,
portão CONAMA); ED-75 (A — itens 9/10 são fronteiras da spec
ED-97/LC.15); ED-77 (B, conteúdo de resolução de agência como
conteúdo do aceite) × ED-82 (A, texto ABNT licenciado da P1.28,
padrão ED-76); família ISO ED-70/71/72/73/81 (A, padrão ED-69/
ED-76); P3.9 (B — item 1 "PENDENTE por desenho", resolução no
piloto real; distinto de ED-31/C e P3.24/A). Nota da revisão sem
divergência: ED-83 é o A mais próximo da fronteira com a ED-77,
sustentado porque nenhum item está marcado parcial e a recusa é
por ausência de declaração do tenant (desenho).

## A3. Categoria A da extensão — Aceite completo (17)

Sem item de aceite parcial/não executado declarado no próprio
relatório; restam apenas as pendências universais de
integração/CI (seção 2). Evidência integral por tarefa em
`EXTENSAO-M2-CLASSIFICACAO.md` §2 (com a ED-93 removida desta
lista pela D-R1) e conferência em `EXTENSAO-M2-REVISAO.md` §3:

**ED (17):** ED-59 (`ed-59/RELATORIO-ED-59.md`), ED-70
(`ed-70/RELATORIO-ED-70.md`), ED-71 (`ed-71/RELATORIO-ED-71.md`),
ED-72 (`ed-72/RELATORIO-ED-72.md`), ED-73
(`ed-73/RELATORIO-ED-73.md`), ED-75 (`ed-75/RELATORIO-ED-75.md`),
ED-81 (`ed-81/RELATORIO-ED-81.md`), ED-82
(`ed-82/RELATORIO-ED-82.md`), ED-83 (`ed-83/RELATORIO-ED-83.md`),
ED-85 (`ed-85/RELATORIO-ED-85.md`), ED-86
(`ed-86/RELATORIO-ED-86.md`), ED-87 (`ed-87/RELATORIO-ED-87.md`),
ED-92 (`ed-92/RELATORIO-ED-92.md`), ED-94
(`ed-94/RELATORIO-ED-94.md`), ED-95 (`ed-95/RELATORIO-ED-95.md`),
ED-96 (`ed-96/RELATORIO-ED-96.md`), ED-101
(`ed-101/RELATORIO-ED-101.md`).

## A4. Categoria B da extensão — Núcleo entregue + ensaio externo pendente (23)

| Tarefa | Evidência no próprio relatório |
|---|---|
| P3.9 | Item 1 do aceite: "Minutas aprovadas pelo piloto... **PENDENTE por desenho** — produzidas; piloto não escolhido; aprovação impossível agora"; 59 marcadores `[A DEFINIR]` fecham na contratação com o piloto; assinatura só na LC.9 (RELATORIO-P3.9) |
| ED-10 | "P-ED10-06 (externa, categoria B): uso da planilha em proposta/edital real (VISAN/SAMAE) — depende de P-ED10-01/02/03 e de processo comercial real"; P-ED10-01..03: valores comerciais do dono `[A DEFINIR]`, sem os quais "precificar recusa `[A DEFINIR]`" (RELATORIO-ED-10) |
| ED-20 | Item POC-52 a POC-54 VISAN: "Núcleo provado abaixo; ensaio real é prova externa — **PENDENTE B** (P-ED20-01)"; "P-ED20-01 (B, externa): ensaios POC-52 a POC-54 do roteiro VISAN e prova com GPS/hardware reais em campo — dependem do piloto/ambiente" (RELATORIO-ED-20) |
| ED-23 | Item "POC-26 do roteiro VISAN (alínea g) — ensaio real": "**PENDENTE B** (P-ED23-01)"; "P-ED23-01 (B, externa): ensaio POC-26 do roteiro VISAN (alínea g) e prova em campo com aparelho real — dependem do piloto/ambiente" (RELATORIO-ED-23) |
| ED-41 | Item 1 (POC-20): "POC em si = pendência externa P-ED41-02"; item 15 (SAMAE 2.19): "Registro entregue; o ENSAIO é externo (P-ED41-02)"; §4: "envio real NÃO foi feito nem [fingido]" (RELATORIO-ED-41) |
| ED-42 | Item E2E por canal: "**PENDÊNCIA B (P-ED42-01)** — conta de teste real não existe nesta VM; núcleo provado sobre portas, ensaio NÃO presumido"; itens TR 21.1/SAMAE e SAMAE 2.22: "**PENDÊNCIA B (P-ED42-02)** — roteiros VISAN/SAMAE não estão nesta VM [SEM FONTE]; ensaio real na integração" (RELATORIO-ED-42) |
| ED-43 | Item POC-71 a POC-74 e POC-76: "**PENDÊNCIA B (P-ED43-01)** — POC é ensaio com provedor/conta reais; NÃO declaradas aprovadas aqui"; "P-ED43-01 (externa, categoria B) — prova com provedor real / POC-71..74,76" (RELATORIO-ED-43) |
| ED-44 | "P-ED44-01 (externa, categoria B): integração real com PABX/operadora do tenant (protocolo/fornecedor não nomeados — nenhum dado inventado)… O núcleo está provado com dublê honesto declarado" (RELATORIO-ED-44) |
| ED-49 | "P4 (externa): ensaio POC-77 a POC-81 com o roteiro VISAN/SAMAE no ambiente do piloto — a regra e o transporte estão provados; o ensaio [segue pendente]" (RELATORIO-ED-49, §5) |
| ED-50 | Item SAMAE 2.26: "Entregue no núcleo; ensaio com roteiro real = P-ED50-01 (externa B)"; "P-ED50-01 (B, externa): ensaios reais do roteiro SAMAE 2.26 e dos relatórios 2.28.117–126 com massa/roteiro reais e resultados laboratoriais reais — dependem do piloto/ambiente" (RELATORIO-ED-50) |
| ED-54 | Item 9 (TR-1064/1065; SAMAE 2.27): "Registro entregue; o ENSAIO é externo (P-ED54-03) — nenhum item é afirmado como ensaiado" (RELATORIO-ED-54) |
| ED-56 | Item 13 (TR-155, TR-1010, SG-1180): "Registro entregue; o ENSAIO é externo (P-ED56-04)"; "P-ED56-04 (externa): ensaio TR-155, TR-1010 e SG-1180 (SAMAE) com roteiro/massa do órgão… Inclui a confirmação PACP × WRc com o cliente e o responsável técnico" (RELATORIO-ED-56) |
| ED-58 | Item 22 (SAMAE 2.17): "`InventoryCoverage` registra o mapeamento; **ensaio é externo — não afirmado**"; "P-ED58-05 (externa): ensaio SAMAE 2.17 (parte de estoque) com roteiro/massa do órgão" (RELATORIO-ED-58) |
| ED-62 | Item 3 do aceite: "**PENDÊNCIA EXTERNA B1** — nenhum leiaute está nas fontes locais; leiaute entra como mapeamento declarado e o núcleo é provado com dublê honesto declarado"; "B2 (externa): ensaio SAMAE 2.16.18/2.17… o ensaio em si não é afirmado" (precedente P2.7 = B) (RELATORIO-ED-62) |
| ED-66 | Item "Teste instrumentado em Android sem rede": "**Escrito, não executado** — P-ED66-05 (CI do dono)"; "P-ED66-05 (externa/CI): execução do teste instrumentado Android sem rede e da jornada com receptor RTK real em aparelho — CI do dono e piloto"; P-ED66-01: plugins nativos/hardware GNSS reais (precedente ED-18 = B) (RELATORIO-ED-66) |
| ED-77 | Item 5 (catálogo de fábrica das fichas SC, RAS-07): "`FactorySheetCatalog` com os 3 perfis nomeados e conteúdo `[A DEFINIR]` explícito — variável/fórmula/unidade exigem o texto conferido de cada resolução" (ARIS/AGIR/ARESC) = P-ED77-01; item 6 (RAS-33): "o cálculo RECUSA até a declaração a partir do texto da resolução" = P-ED77-04; item 9: valores da Portaria MCID 788/2024 a conferir antes da carga = P-ED77-03 (insumo oficial de agência/órgão — precedentes ED-33/ED-88 = B) (RELATORIO-ED-77) |
| ED-78 | **Reclassificada na revisão (D-R2, era C):** P-ED78-07 — "Submissão real à ERI e publicação efetiva nos sites do prestador e da ERI **são atos externos** — não publica"; P-ED78-04 — aplicabilidade `NO_ACT_LOCATED` até uma ERI **publicar o ato de adoção** (portão externo de publicação de ato de órgão, mesma natureza do portão CONAMA de ED-80/ED-105); P-ED78-01 — procedimentos/prazos "são do ato normativo de cada ERI e dos contratos" (precedente ED-33 = B). As pendências internas coexistem (P-ED78-02 `[SEM FONTE]`, gap do Plano de Gestão, P-ED78-08/09), mas não prevalecem (RELATORIO-ED-78) |
| ED-80 | "P-ED80-02 (externa, CONAMA): publicação da norma que substitui a CONAMA 430/2011 (em revisão): só com ela — e com a nova versão revisada do catálogo da ED-105 — a DCP destrava de verdade; até lá o `DcpService` recusa por desenho"; "P-ED80-03 (externa, CNRH/CONAMA): confirmação, no catálogo, da publicação da resolução conjunta de reúso; até lá o reúso segue NOT_CODIFIED por desenho" (RELATORIO-ED-80) |
| ED-89 | Item SG-811 (fatia SAMAE 2.26): "Entregue no núcleo; ensaio com roteiro real = P-ED89-01"; "P-ED89-01 (B, externa): ensaio SAMAE da fatia SG-811 com roteiro/massa reais — depende do piloto/ambiente" (RELATORIO-ED-89) |
| ED-90 | Item 2 do aceite ("leiaute de cada distribuidora conferido na implantação"): "Portão + porta, NÃO um parser genérico… **Nenhum leiaute implementado [SEM FONTE]**"; "P-ED90-01 (implantação): conferir o leiaute de fatura de CADA distribuidora contra faturas reais… Até lá, toda importação recusa [SEM FONTE]" (precedente P2.7 = B) (RELATORIO-ED-90) |
| ED-91 | Decisão de execução nº 4: "Carro-pipa = mecanismo + pendência externa B… a logística real (frota/contratado/rota) NÃO é fingida — pendência P-ED91-B"; "P-ED91-B (externa): operação real de carro-pipa (frota, contratado, rotas, escala) — fora do software" (RELATORIO-ED-91) |
| ED-93 | **Reclassificada na revisão (D-R1, era A):** item 9 do aceite (latência RNF/ADR-0012 "com teste de latência de ponta a ponta") com a fronteira na própria linha: "até o navegador é P-ED93-03"; P-ED93-03 nomeada "(integração — **prova externa ao núcleo**): repetir a sonda de latência sobre o caminho integrado (**banco real + HTTP + navegador**) — nenhum número dela é latência de produção"; P-ED93-04: fontes reais de GPS/telemetria de nível inexistentes nas deps. Precedentes P3.26/P3.33/ED-46 = B (RELATORIO-ED-93) |
| ED-105 | "P-ED105-04 (externa, CONAMA): publicação da nova resolução que revisa a CONAMA 430/2011 (aprovada em 02/09/2026, sem publicação): só então os limites novos entram, como nova versão do catálogo com data de efeito, pela carga revisada" — portão externo de publicação de norma nomeado pelo próprio relatório; itens da tabela entregues no núcleo (RELATORIO-ED-105) |

## A5. Categoria C da extensão — Núcleo + pendência declarada que NÃO é ensaio externo (1)

| Tarefa | Evidência / natureza da pendência |
|---|---|
| ED-79 | Item de aceite não modelado, declarado no adendo de correção (R3/D3 da revisão): "o LOS `servico/conjunto`… **Não foi modelado** e estava sem marca" — P-ED79-10: "LOS `servico/conjunto` **[A DEFINIR]** — confirmar se é exigência do aceite/brief e, se for, modelá-lo ou marcá-lo formalmente como fronteira" (dono: integração/coordenação); resíduo da P-ED79-09: guarda de completude de componentes por tipo de sistema pendente ("sem fonte que defina os componentes exigidos") — pendências internas/de desenho e de recorte, sem ensaio externo declarado (RELATORIO-ED-79; conferência em EXTENSAO-M2-REVISAO.md §3) |

## A6. Sem classificação na extensão

**Nenhuma.** As 41 tarefas têm relatório próprio com estado de
aceite aferível (incluídas ED-90 e ED-105, cujos relatórios são
regularizações assinadas pelo corretor, com a reconstrução
declarada e os números refeitos em disco).

## A7. Totais consolidados (universo 260)

### Por dimensão

| Dimensão | Registro original (219) | Extensão (41) | **Consolidado (260)** | % de 260 |
|---|---:|---:|---:|---:|
| Aceite completo (com relatório local) | 47 | 17 | **64** | 24,6% |
| Núcleo entregue + ensaio externo pendente | 57 | 23 | **80** | 30,8% |
| Núcleo + pendência declarada não externa (categoria C) | 15 | 1 | **16** | 6,2% |
| Sem classificação por relatório local (pré-retomada) | 100 | 0 | **100** | 38,5% |
| **Total executadas** | **219** | **41** | **260** | 100% |

### Por fase

| Fase | Executadas no universo | A completo | B ensaio externo | C não externo | Sem classificação |
|---|---:|---:|---:|---:|---:|
| P0 | 47 | 0 | 0 | 0 | 47 |
| P1 | 41 | 0 | 0 | 0 | 41 |
| P2 | 34 | 11 | 7 | 4 | 12 |
| P3 | 40 (de 41; P3.13 não executada) | 23 | 11 | 6 | 0 |
| P4 | 5 (de 6; P4.6 não executada) | 4 | 1 | 0 | 0 |
| GL | 3 (de 4; GL.4 não executada) | 0 | 3 | 0 | 0 |
| ED | 90 (de 98) | 26 | 58 | 6 | 0 |
| **Total** | **260** | **64** | **80** | **16** | **100** |

Por módulo: segue **não derivável sem inventar atribuição**, pelo
mesmo limite declarado na seção 8 — a ANALISE não publica a lista
tarefa→módulo das tarefas da cascata.

## A8. Notas do adendo

- A ressalva **M4 da ED-08 (seção 7) segue valendo**,
  inalterada: a ED-08 continua em B com a ressalva nomeada até a
  primeira rodada verde da matriz E2E no CI.
- As **100 pré-retomada seguem sem classificação** por relatório
  local — nenhuma foi presumida aceite completo (seção 6).
- Com este adendo, **todas as 260 executadas da foto das 22:05 de
  08/11/2026 têm a segunda dimensão registrada**: 160 com
  classificação por relatório local (64 A + 80 B + 16 C) e 100
  declaradas sem classificação aferível (pré-retomada).
- Executadas posteriores a essa foto não estão neste adendo; a
  manutenção do registro a cada rodada é da rotina de status.

---

# Adendo de 09/11/2026 — Manutenção da rodada 02:05 (job ziipsan-push-2h)

**Origem:** manutenção ordenada da rotina de status — classificar as
executadas que entraram depois da foto do adendo anterior (260).
Nenhuma linha das seções 1–9 nem do adendo anterior foi alterada.

## B1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 260 | este registro, §A7 |
| Executadas na foto das 02:05 de 09/11/2026 | 262 | rotina de status (162 produzidas na retomada + 100 pré-retomada; todas as 162 na main) |
| **Classificadas neste adendo** | **2** | ED-97 e ED-98 (camadas C8 e C9 da cascata ED; pousos `4e808ac1` e `02090935`, diários `6625ed76` e `f659bad8`) |
| **Universo consolidado** | **262** | 260 + 2 ✓ |

## B2. Classificação (critério da seção 2, sem alteração)

Ambas as tarefas declaram a própria categoria B no relatório, com a
pendência externa nomeada — classificação por evidência lida do
relatório da tarefa, não por presunção:

| Tarefa | Categoria | Evidência no próprio relatório |
|---|---|---|
| ED-97 | **B** — núcleo + ensaio externo pendente | "P-ED97-03 — Prova externa (categoria B do placar 2D): envio REAL às agências, recebimento da lista positiva real da ANA e o relatório anual real à agência não se fingem — o pacote entrega geração, validação, registro com hash e evidência locais; o envio é do tenant (modo assistido) e a prova externa fica para a operação real." Itens 4/5 do aceite "ATENDIDO PARCIAL" (perfis ARIS/AGIR/ARESC e leiautes SISARIS/SAC com textos oficiais não localizados — P-ED97-01/P-ED97-02). (RELATORIO-ED-97.md) |
| ED-98 | **B** — núcleo + ensaio externo pendente | "P-ED98-04 — Prova externa (categoria B do placar 2D): geração real do calendário sobre o catálogo de normas carregado em produção, recebimento real das remessas pelos órgãos e o disparo real dos alertas não se fingem — o pacote entrega geração, travas, ligação de evidência e persistência JDBC dos itens sobre o SQL pendente..." (RELATORIO-ED-98.md) |

## B3. Sem classificação neste adendo

**Nenhuma.** As 2 tarefas têm relatório próprio com estado de
aceite aferível. As 100 pré-retomada seguem sem classificação por
relatório local (seção 6) — nenhuma presumida aceite completo. A
ressalva M4 da ED-08 (seção 7) segue valendo.

## B4. Totais consolidados (universo 262)

### Por dimensão

| Dimensão | Adendo anterior (260) | Este adendo (+2) | **Consolidado (262)** | % de 262 |
|---|---:|---:|---:|---:|
| Aceite completo (com relatório local) | 64 | 0 | **64** | 24,4% |
| Núcleo entregue + ensaio externo pendente | 80 | +2 | **82** | 31,3% |
| Núcleo + pendência declarada não externa (categoria C) | 16 | 0 | **16** | 6,1% |
| Sem classificação por relatório local (pré-retomada) | 100 | 0 | **100** | 38,2% |
| **Total executadas** | **260** | **+2** | **262** | 100% |

### Por fase

| Fase | Executadas no universo | A completo | B ensaio externo | C não externo | Sem classificação |
|---|---:|---:|---:|---:|---:|
| P0 | 47 | 0 | 0 | 0 | 47 |
| P1 | 41 | 0 | 0 | 0 | 41 |
| P2 | 34 | 11 | 7 | 4 | 12 |
| P3 | 40 (de 41; P3.13 não executada) | 23 | 11 | 6 | 0 |
| P4 | 5 (de 6; P4.6 não executada) | 4 | 1 | 0 | 0 |
| GL | 3 (de 4; GL.4 não executada) | 0 | 3 | 0 | 0 |
| ED | 92 (de 98) | 26 | 60 | 6 | 0 |
| **Total** | **262** | **64** | **82** | **16** | **100** |

## B5. Notas do adendo

- Com este adendo, **todas as 262 executadas da foto das 02:05 de
  09/11/2026 têm a segunda dimensão registrada**: 162 com
  classificação por relatório local (64 A + 82 B + 16 C) e 100
  declaradas sem classificação aferível (pré-retomada).
- A manutenção da rodada mudou o placar M2 só em B: 80 → **82**
  (+ED-97, +ED-98); A, C e sem classificação inalterados.
- A cascata ED está formalmente fechada com a C9 (RELATORIO-C9.md):
  ED 92/98 — restam ED-01, ED-51, ED-68, ED-102, ED-103 e ED-104.

## C. Adendo de 09/11/2026 — manutenção da rodada 04:05 (bloco de conclusão)

## C1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 262 | este registro, §B4 |
| Executadas na foto das 04:05 de 09/11/2026 | 280 | rotina de status (180 produzidas na retomada + 100 pré-retomada; 162 das 180 na main, 18 aguardando push) |
| **Classificadas neste adendo** | **18** | P3.13, GL.4, ED-01, ED-51, LC.1–LC.14 (bloco de conclusão; 33 sessões; cada pacote com `entrega/` + RELATORIO + REVISAO aprovada — LC.2 e ED-01 após correção e reapreciação APROVADO — + CONFIRMACAO independente; pousos pendentes da janela pós-429) |
| **Universo consolidado** | **280** | 262 + 18 ✓ |

## C2. Classificação (critério da seção 2, sem alteração)

Classificação por evidência lida dos artefatos de cada tarefa
(relatório, revisão, confirmação e, onde há, o ato do dono na
própria pasta), não por presunção. O relatório consolidado do bloco
(`conclusao/RELATORIO-CONCLUSAO.md`, §1–§4) foi usado como
índice — cada item abaixo foi conferido no artefato da tarefa.

### A — Aceite completo (1)

| Tarefa | Categoria | Evidência lida |
|---|---|---|
| GL.4 | **A** | A única pendência de aceite do relatório era GL.4-01 (decisão formal do dono), resolvida **depois** do relatório pelo ato do dono na própria pasta: `gl.4/ACEITE-DO-DONO-GL4.md` — "ACEITE da homologação local (GL.4), sem ressalvas declaradas pelo dono", 09/11/2026 03:08 −03, item 37 do DECISOES-DO-DONO.md; "a pendência GL.4-01 está RESOLVIDA — a fase GL tem o seu aceite formal". GL.4-02 (assinatura do piloto) "não se aplica no modelo vigente"; GL.4-03 (ciclos reais da GL.2/GL.3) é evento de outras tarefas, não do aceite da GL.4; GL.4-04 é errata documental no relatório da P3.24. Dossiê aceite tal como entregue e confirmado (GL.1 37/37, GL.2 56/56, GL.3 65/65, FEBRABAN 150 28/28). |

### B — Núcleo entregue + ensaio externo pendente (17)

| Tarefa | Categoria | Evidência lida |
|---|---|---|
| P3.13 | **B** | RELATORIO-P3.13.md, tabela de aceite: "Critérios da homologação local (GL.2) \| ✅ produzidos / ⚠️ **limiares por fixar**" (L-01…L-04 `[A DEFINIR]`, P3.13-01/03) e "Aprovados pelo piloto \| ❌ NÃO realizado". A decisão nº 38 (ato em `p3.13/APROVACAO-DO-DONO-P3.13.md`, 03:09) resolveu P3.13-02 (plano APROVADO pelo dono) e redefiniu P3.13-01 como **dependência de dado**: os limiares "nascem dos dados da homologação" — só se fixam quando os ciclos reais rodarem (GL.2/LC.11). Veredito do produtor: "o aceite integral da P3.13 não está cumprido enquanto P3.13-02 e P3.13-01 subsistirem" — P3.13-02 resolvida, P3.13-01 subsiste, dependente de evento externo. P3.13-04/05/06 dependem de cliente futuro. |
| ED-01 | **B** | RELATORIO-ED-01.md, § pendências: P-ED01-01 "Kit físico de campo (Android, iPhone, Zebra ZQ, bobinas, 4G)" — EXTERNA; P-ED01-02 "Dois ensaios cronometrados consecutivos (VISAN ≥146/162; SAMAE sem falha crítica)" — "ensaio NÃO rodado, declarado" — EXTERNA. Verificação PD-01 local 55/4/0 em 59 itens. |
| ED-51 | **B** | RELATORIO-ED-51.md, §6: P-ED51-01 (PD-08) resolvida após o relatório pela decisão nº 40 — pacote `ed-51-pd08` construído, revisado e confirmado; P-ED51-02 é fiação/integração (pendência universal, não bloqueia A); **P-ED51-04 (laboratório/responsável técnico do tenant): "carga dos dados declarados — escopo do laboratório, requisitos ambientais, orçamentos de incerteza, dossiês de validação, médias/desvios atribuídos dos materiais de controle — sem essa carga, os ensaios não nascem"** — insumo que só o tenant fornece; P-ED51-03 (entrada em produção sequenciada à implantação do tenant que exigir a norma) também externa. Software completo (17 classes, 18 tabelas `lims_*`, JUnit 40/40). |
| LC.1 | **B** | RELATORIO-LC.1.md: "a execução do ensaio é externa (conta AWS) e **não foi executada nem simulada**". Roteiro de 24 passos, cobertura 12/12. Conta AWS adiada para a contratação (decisão nº 41). |
| LC.2 | **B** | RELATORIO-LC.2.md: IaC produzido (44 arquivos, validação estrutural 24/24); deploy/rollback exercitados exigem AWS. Terraform adotado como decisão de execução declarada `[SEM FONTE]`. |
| LC.3 | **B** | RELATORIO-LC.3.md: "Todas as 8 provas do roteiro: **não executadas — exige AWS**" (medição de RPO/RTO, failover Multi-AZ, cofre, PITR...). |
| LC.4 | **B** | RELATORIO-LC.4.md: "6 provas do roteiro, todas **não executadas — exige AWS**" (suíte de contrato sobre KMS real, rotação, crypto-shredding...). |
| LC.5 | **B** | RELATORIO-LC.5.md: "3 provas do roteiro, todas **não executadas — exige AWS**" (S3 real, recusa do Object Lock, isolamento de prefixo). |
| LC.6 | **B** | RELATORIO-LC.6.md: "LC.6 = classe A parcial + B (configuração produzível; teste exige AWS)". Tema da P0.35 e ficheiro de realm vigentes `[SEM FONTE]` (P-LC6-01/02). |
| LC.7 | **B** | RELATORIO-LC.7.md: ativação gerenciada, sondas a correr, página publicada e exercício de falha — **não executados** (exigem AWS). |
| LC.8 | **B** | RELATORIO-LC.8.md: P-LC8-01 "**Produção AWS inexistente**... Sem produção, não há alvo de pentest" — pentest é ato de terceiro sobre produção AWS (externo). Escopo 17/17. |
| LC.9 | **B** | RELATORIO-LC.9.md: "CONJUNTO PRONTO PARA ASSINATURA — **NÃO ASSINADO**, com lacunas"; assinatura com cliente futuro (externo). Achado grave D-LC9-01: cláusula do atestado (ADR-0023) ausente das minutas da P3.9 — minuta-proposta produzida por decisão nº 39; S-06 segue aberto até revisão jurídica + incorporação (pendência P-JUR-FINAL-01, decisão nº 44). |
| LC.10 | **B** | RELATORIO-LC.10.md: "B (+prep A) — o evento real exige cliente/dado real, que não existe". Carga real, conciliação assinada pelo piloto — roteiro 9/9. |
| LC.11 | **B** | RELATORIO-LC.11.md: "B (+prep A) — os ciclos reais exigem cliente/dado real, que não existe". Trava: limiares sob P3.13-01. |
| LC.12 | **B** | RELATORIO-LC.12.md: "Classe (mapa do coordenador): B (+prep A) — o evento real exige PITR do RDS em produção (LC.3 na AWS)". Evento: 0 executado. |
| LC.13 | **B** | RELATORIO-LC.13.md: corte/volta com termo de decisão em branco; "decisão é ato externo — não preenchida"; desligamento do legado (M15) é ato do cliente. |
| LC.14 | **B** | RELATORIO-LC.14.md: "Classe (mapa do coordenador): B (+prep A) — atestado é emitido pelo piloto (externo, inexistente)". Modelo de atestado em branco, rotulado "NÃO É UM ATESTADO EMITIDO". |

### C — Núcleo + pendência declarada que NÃO é ensaio externo (0)

Nenhuma das 18 cai em C nesta rodada: as pendências não-universais
de todas elas são externas (conta AWS, cliente/dado real, piloto,
kit/ensaios, dados do tenant) — exceto a GL.4, classificada A, cuja
única pendência de aceite foi resolvida pelo ato do dono.

## C3. Sem classificação neste adendo

**Nenhuma.** As 18 tarefas têm relatório próprio com estado de
aceite aferível. As 100 pré-retomada seguem sem classificação por
relatório local (seção 6) — nenhuma presumida aceite completo. A
ressalva M4 da ED-08 (seção 7) segue valendo.

## C4. Totais consolidados (universo 280)

### Por dimensão

| Dimensão | Adendo anterior (262) | Este adendo (+18) | **Consolidado (280)** | % de 280 |
|---|---:|---:|---:|---:|
| Aceite completo (com relatório local) | 64 | +1 | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | 82 | +17 | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | 16 | 0 | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | 100 | 0 | **100** | 35,7% |
| **Total executadas** | **262** | **+18** | **280** | 100% |

### Por fase

| Fase | Executadas no universo | A completo | B ensaio externo | C não externo | Sem classificação |
|---|---:|---:|---:|---:|---:|
| P0 | 47 | 0 | 0 | 0 | 47 |
| P1 | 41 | 0 | 0 | 0 | 41 |
| P2 | 34 | 11 | 7 | 4 | 12 |
| P3 | 41 (de 41; P3.13 agora executada) | 23 | 12 | 6 | 0 |
| P4 | 5 (de 6; P4.6 não executada) | 4 | 1 | 0 | 0 |
| GL | 4 (de 4; GL.4 agora executada) | 1 | 3 | 0 | 0 |
| LC | 14 (de 15; LC.15 não executada) | 0 | 14 | 0 | 0 |
| ED | 94 (de 98) | 26 | 62 | 6 | 0 |
| **Total** | **280** | **65** | **99** | **16** | **100** |

## C5. Notas do adendo

- Com este adendo, **todas as 280 executadas da foto das 04:05 de
  09/11/2026 têm a segunda dimensão registrada**: 180 com
  classificação por relatório local (65 A + 99 B + 16 C) e 100
  declaradas sem classificação aferível (pré-retomada).
- A manutenção da rodada mudou o placar M2 em A (+1: GL.4, aceite
  formal do dono em 03:08) e em B (+17: P3.13, ED-01, ED-51,
  LC.1–LC.14); C e sem classificação inalterados.
- **Distinção registada para as próximas rodadas:** a P3.13 foi
  classificada B e não A porque, embora o plano esteja aprovado pelo
  dono (decisão nº 38), os limiares L-01/L-02/L-03 seguem
  `[A DEFINIR]` como dependência de dado dos ciclos reais — o item
  de aceite "critérios aprovados" segue parcial por fator externo.
  A GL.4 foi classificada A porque o seu aceite é um ato de decisão
  que aconteceu (item 37), sem item numérico pendente de dado
  externo. A ED-51 foi classificada B (e não A) porque o relatório
  nomeia P-ED51-04 — carga de dados do laboratório do tenant, sem a
  qual os ensaios não nascem — insumo que só o tenant fornece.
- O bloco de conclusão conta "15 dos 18 com ato/evento real não
  simulado" (`RELATORIO-CONCLUSAO.md` §1) — a contagem deste adendo
  é 17 em B porque inclui P3.13 (limiares por dado externo) e ED-51
  (dados do tenant) além das 15 do bloco (ED-01 + LC.1–LC.14); a
  diferença é de critério, não de facto: o bloco conta atos/eventos,
  este registro conta itens de aceite parciais por fator externo
  (§2). A GL.4 é A nos dois critérios.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104)
  são classe D por desenho/decisão do dono (disparo nomeado) e não
  entram neste registro até serem produzidas.

## D. Adendo de 09/11/2026 — manutenção da rodada 06:05 (job ziipsan-push-2h)

## D1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §C4 |
| Executadas na foto das 06:05 de 09/11/2026 | 280 | rotina de status (180 produzidas na retomada + 100 pré-retomada; 162 das 180 na main, 18 aguardando push) |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada nesta rodada: as 180 produzidas são as mesmas da foto das 04:05, todas já classificadas no adendo C (65 A + 99 B + 16 C). Nenhuma executada segue sem classificação. |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

## D2. Verificação (antes de apresentar, como pede a rotina)

Conferidas as 180 produzidas da retomada contra este registro: as
65 em A, as 99 em B e as 16 em C do adendo C continuam as mesmas
tarefas, sem nenhuma executada fora do registro. As 100 da
pré-retomada seguem sem classificação por relatório local (seção 6)
— nenhuma presumida aceite completo. A ressalva M4 da ED-08
(seção 7) segue valendo. A decisão nº 47 do dono (P-P25-01,
variante T, 04:49 −03) não toca nenhuma tarefa do plano: o pacote
`correcao-p25-receivable` é correção fora do universo de 286 e não
muda nenhuma classificação.

## D3. Precisão sobre o adendo R8-A1 (Rodada 8) — os dois "17" não são o mesmo conjunto

O lote R8-A corrigiu o §1 do relatório do bloco de conclusão
(`conclusao/ADENDO-R8-A1-CONTAGEM-15-17.md`): "15 dos 18" passa a
"**17 dos 18**" — 15 com resto de evento real (LC.1–LC.7, LC.8,
LC.9, LC.10–LC.14, ED-01) mais 2 cujo resto é só a decisão formal
do dono (GL.4, P3.13); a ED-51 é a 18.ª, com resto de
fiação/aplicação real (P-ED51-02, interno). A nota C5 deste registro
dizia que o bloco contava 15 e este registro 17 em B "por diferença
de critério, não de facto" — com o R8-A1, o bloco também conta 17,
mas **os conjuntos diferem num elemento**:

| Conjunto "17" | Composição |
|---|---|
| Bloco (R8-A1) | 15 de evento real + GL.4 + P3.13 (decisão formal) |
| Este registro, B | 15 de evento real + P3.13 + ED-51 (dados do tenant, P-ED51-04) |

A GL.4 é **A** neste registro porque o seu aceite é um ato de
decisão que aconteceu (item 37), sem item numérico pendente de dado
externo; a ED-51 é **B** neste registro pela P-ED51-04, enquanto no
bloco ela é a 18.ª porque o seu resto nomeado é interno
(fiação/aplicação real). Coincidência numérica (17 = 17), não
identidade de conjuntos. **Nenhuma reclassificação resulta do
R8-A1** — a nota de critério da C5 mantém-se, agora com a contagem
do bloco corrigida.

## D4. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## D5. Notas do adendo

- A manutenção desta rodada **não mudou o placar M2**: 0
  classificações novas, 0 reclassificações; A, B, C e sem
  classificação inalterados.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104)
  são classe D por desenho/decisão do dono (disparo nomeado) e não
  entram neste registro até serem produzidas.

## E. Adendo de 09/11/2026 — manutenção da rodada 08:05 (job ziipsan-push-2h)

## E1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §D4 |
| Executadas na foto das 08:05 de 09/11/2026 | 280 | rotina de status (180 produzidas na retomada + 100 pré-retomada; 162 das 180 na main, 18 aguardando push; lista de produzidas byte-idêntica à da foto das 06:05, conferida por listagem integral) |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada nesta rodada: as 180 produzidas são as mesmas da foto das 06:05, todas já classificadas no adendo C (65 A + 99 B + 16 C). Nenhuma executada segue sem classificação. |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

## E2. Verificação (antes de apresentar, como pede a rotina)

Conferidas as 180 produzidas da retomada contra este registro: as
65 em A, as 99 em B e as 16 em C do adendo C continuam as mesmas
tarefas, sem nenhuma executada fora do registro. As 100 da
pré-retomada seguem sem classificação por relatório local (seção 6)
— nenhuma presumida aceite completo. A ressalva M4 da ED-08
(seção 7) segue valendo. Os lotes R10-A e R10-B, fechados
localmente nesta janela (~07:58–08:01 −03, ambos CONFIRMADO —
R10-A com 1 ressalva de redação), são correções de achados da
auditoria contínua, fora do universo de 286, e não mudam nenhuma
classificação. A Rodada 10 da auditoria (consolidado das 07:43
−03) revalidou o adendo D integralmente, 0 achados novos sobre o
M2. A observação nova da Rodada 10 — a perna do produtor do
R10-A também sofreu sobre-captura (iv) do extrator do portão —
toca a evidência do portão, não o aceite de nenhuma tarefa do
plano: nenhuma reclassificação resulta dela. O `DECISOES-DO-DONO.md`
segue com 251 linhas, última decisão a nº 47; C4-01 segue sem
decisão do dono (item 48 inexistente) — fora do âmbito deste
registro.

## E3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## E4. Notas do adendo

- A manutenção desta rodada **não mudou o placar M2**: 0
  classificações novas, 0 reclassificações; A, B, C e sem
  classificação inalterados.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104)
  são classe D por desenho/decisão do dono (disparo nomeado) e não
  entram neste registro até serem produzidas.

## F. Adendo de 09/11/2026 — manutenção da rodada 10:05 (job ziipsan-push-2h)

## F1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §E3 |
| Executadas na foto das 10:05 de 09/11/2026 | 280 | rotina de status (180 produzidas na retomada + 100 pré-retomada; 162 das 180 na main, 18 aguardando push; lista de produzidas byte-idêntica à da foto das 08:05, conferida por listagem integral e por `foto-resultado-1005.json` = `foto-resultado-0805.json`) |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada nesta rodada: as 180 produzidas são as mesmas da foto das 08:05, todas já classificadas no adendo C (65 A + 99 B + 16 C). Nenhuma executada segue sem classificação. |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

## F2. Verificação (antes de apresentar, como pede a rotina)

Conferidas as 180 produzidas da retomada contra este registro: as
65 em A, as 99 em B e as 16 em C do adendo C continuam as mesmas
tarefas, sem nenhuma executada fora do registro. As 100 da
pré-retomada seguem sem classificação por relatório local (seção 6)
— nenhuma presumida aceite completo. A ressalva M4 da ED-08
(seção 7) segue valendo. O lote R11-A, fechado localmente nesta
janela (~09:59 −03, produção 1/1, revisão APROVADO, confirmação
CONFIRMADO — 0 divergências, 0 ressalvas; portão exit 1 só Regra 3,
3 falsos positivos demonstrados), é correção de achado da
auditoria contínua, fora do universo de 286, e não muda nenhuma
classificação. A Rodada 11 da auditoria (consolidado das ~09:55
−03) revalidou o adendo E integralmente, 0 achados novos sobre o
M2. O `DECISOES-DO-DONO.md` segue com 251 linhas, última decisão a
nº 47; C4-01 segue sem decisão do dono (item 48 inexistente) — fora
do âmbito deste registro.

## F3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## F4. Notas do adendo

- A manutenção desta rodada **não mudou o placar M2**: 0
  classificações novas, 0 reclassificações; A, B, C e sem
  classificação inalterados.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104)
  são classe D por desenho/decisão do dono (disparo nomeado) e não
  entram neste registro até serem produzidas.

## G. Adendo de 09/11/2026 — actualização pós-pouso da P3.13 (auditoria contínua, Rodada 12, lote R12-B)

- **Produtor (sessão):** `892ec79f-752e-41fc-ae8b-209968286c0d`
- **Data:** 09/11/2026 (~12h −03)
- **Âmbito:** actualização da posição "na main" após o pouso da P3.13 (achados R12-01 e R12-02, menores — consolidado da Rodada 12, §3). Nenhuma linha das secções anteriores (§§1–9, adendos A–F) foi alterada. O adendo F continua a ser o registo fiel da foto das 10:05: as suas cifras "162 das 180 na main, 18 aguardando push" descreviam essa foto e ficam historicamente correctas; a posição corrente, após o pouso, é a declarada neste adendo.

## G1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §F3 |
| Executadas (universo deste registro) | 280 | inalterado — a P3.13 já estava executada e classificada (adendo C); o pouso não cria executada nova |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada; 0 reclassificações — a classificação M2 (65 A + 99 B + 16 C + 100 sem classificação) não é afectada |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

### Posição na main após o pouso da P3.13

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **163 de 180** | foto das 10:05: 162 (ED 92 + P3 40 + P2 22 + P4 5 + GL 3); com a P3.13 pousada, P3 passa a 41, logo 163 |
| Pré-retomada na main | 100 | foto das 10:05, inalterada |
| **Total na main** | **263 de 286** | 100 + 163 = 263 |
| **Aguardando push** | **17** | 180 − 163 = 17 (GL.4, ED-01, ED-51, LC.1–LC.14) |
| **Total na main (%)** | **92,0%** | 263/286 = 91,958041…% → 92,0% a uma casa decimal, por arredondamento |

## G2. Verificação (recálculo próprio do produtor)

1. Foto das 10:05 (`~/workspace/ziipsan-retomada/ANALISE-GERAL-MODULOS.md`, §4): retomada na main 162 = ED 92 + P3 40 + P2 22 + P4 5 + GL 3 (92 + 40 + 22 + 5 + 3 = 162 ✓); total na main 262 = 100 pré-retomada + 162; aguardando push 18 = 180 − 162 ✓.
2. Pouso da P3.13: commit `b17fce5` (09/11/2026 ~11:01 −03), verificado em vivo pela mesa da Rodada 12 (consolidado §2.1: HEAD `b17fce534ba800a3a6dd5683c7189d05817e2017`, exactamente 2 ficheiros, hashes vivos `cc3e8975…` / `04169a03…` byte-idênticos aos da CONFIRMACAO-P3.13.md). **Zero chamadas ao GitHub nesta produção** (parada dura por HTTP 429) — nenhuma leitura viva própria; a verificação viva citada é a da mesa. Conferência local: `sha256sum` dos dois ficheiros da entrega (`p3.13/entrega/docs/homologacao/…`) devolve `cc3e897524797f388675d03bc96c9c2a8a3c3378c2564088adc2302553bd7c39` e `04169a039fe90b2653f49c9de48e936bd5153097ba0a938668aed0137579f979`, idênticos aos da CONFIRMACAO-P3.13.md §3 e aos hashes vivos citados pela mesa.
3. Recálculo: P3 na main 40 + 1 (P3.13) = 41; retomada na main 162 + 1 = 163; total na main 262 + 1 = 263; aguardando push 18 − 1 = 17; 180 − 163 = 17 ✓; 263/286 = 0,91958041… = 91,958041…% → **92,0%** a uma casa decimal por arredondamento.
4. Correcção R12-02: o valor "91,9%" que circulava era truncagem de 91,958…%; o valor correcto a uma casa decimal é **92,0%**. Nenhum outro percentual deste registro muda: os percentuais M2 são sobre o universo de 280 executadas, inalterado.

## G3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## G4. Notas do adendo

- A manutenção deste adendo **não mudou o placar M2**: 0
  classificações novas, 0 reclassificações; A, B, C e sem
  classificação inalterados.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104)
  são classe D por desenho/decisão do dono (disparo nomeado) e não
  entram neste registro até serem produzidas.
- O adendo F (§F1) fica historicamente correcto como registo da foto
  das 10:05; a posição corrente "na main" é a deste adendo G até à
  próxima manutenção.

---

# Adendo de 09/11/2026 — Manutenção da rodada 12:05 (job ziipsan-push-2h)

**Origem:** manutenção ordenada da rotina de status — verificar se há
executadas ainda sem classificação no registro. Nenhuma linha das
seções 1–9 nem dos adendos A–G foi alterada. O adendo G (lote R12-B
da auditoria contínua, Rodada 12) já cobre o pouso da P3.13; este
adendo é a verificação própria deste job, por leitura viva do
GitHub, e o registo da foto corrente.

## H1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §G1 |
| Executadas (universo deste registro) | 280 | inalterado — nenhuma tarefa nova executada nesta rodada |
| **Classificadas neste adendo** | **0** | Lista de produzidas byte-idêntica à da foto das 10:05 (180 tarefas; `contagem_1205.json` = `contagem_1005.json`, diff vazio); 0 reclassificações |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

## H2. Verificação do adendo G por leitura viva própria do GitHub

Leitura completa via conector GitHub nesta rodada, sem 429:
**904 commits, HEAD `da56e7a`** (`feat(seed): base ficticia de
testes — pouso consolidado em espera (lote 214/262)`,
12:10:59 −03 de 09/11/2026). Continuidade SHA a SHA: os 881
commits da foto das 10:05 estão todos presentes, **0 ausentes**;
23 novos — `b17fce5` (P3.13, 11:00:35 −03) + 22 lotes de seed da
base fictícia (193–214). O casamento de assuntos reproduz as 163
tarefas da retomada na main (ED 92, P3 41, P2 22, P4 5, GL 3).
**Confirma o adendo G:** retomada na main 163 de 180; total na
main **263 de 286 = 92,0%**; aguardando push **17** (GL.4, ED-01,
ED-51, LC.1–LC.14). Os lotes de seed não são entregas de tarefa —
não mudam nenhuma contagem deste registro.

## H3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## H4. Notas do adendo

- A manutenção desta rodada **não mudou o placar M2**: 0
  classificações novas, 0 reclassificações; A, B, C e sem
  classificação inalterados.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104)
  são classe D por desenho/decisão do dono e não entram neste
  registro até serem produzidas.
- Nota de frescura da foto: no instante da leitura viva o runner
  da base fictícia já avançava (lote 217 em curso às 12:11:17 −03;
  lote 224 OK às 12:12:43 −03) — a foto acima é o instante
  12:10:59 −03; os lotes de seed posteriores não alteram a posição
  "na main" de tarefas do plano.

---

## I. Adendo de 09/11/2026 — actualização pós-pouso da ED-51 (auditoria contínua, Rodada 13, lote R13-B)

- **Produtor (sessão):** `ae9f5d56-1460-4b24-a946-4ed3f87d43c5`
- **Data:** 09/11/2026 (~14h −03)
- **Âmbito:** actualização da posição "na main" após o pouso da ED-51 (achados R13-A1 e R13-A2, menores — consolidado da Rodada 13, §3). Nenhuma linha das secções anteriores (§§1–9, adendos A–H) foi alterada. O adendo G continua a ser o registo fiel da posição após o pouso da P3.13 e o adendo H o registo fiel da foto das 12:05; as suas cifras ficam historicamente correctas; a posição corrente, após o pouso da ED-51, é a declarada neste adendo.

## I1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §H1 |
| Executadas (universo deste registro) | 280 | inalterado — a ED-51 já estava executada e classificada (adendo C); o pouso não cria executada nova |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada; 0 reclassificações — a classificação M2 (65 A + 99 B + 16 C + 100 sem classificação) não é afectada |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

### Posição na main após o pouso da ED-51

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **164 de 180** | Adendo G: 163 (ED 92 + P3 41 + P2 22 + P4 5 + GL 3); com a ED-51 pousada, ED passa a 93, logo 164 |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **264 de 286** | 100 + 164 = 264 |
| **Aguardando push** | **16** | 180 − 164 = 16 (GL.4, ED-01, LC.1–LC.14) |
| **Total na main (%)** | **92,3%** | 264/286 = 92,307692…% → 92,3% a uma casa decimal, por arredondamento |

Por fase, na main:

| Fase | Na main | De | Fonte |
|---|---|---:|---:|
| ED | **93** | 98 | Adendo G: 92; +1 ED-51 pousada |
| P3 | 41 | 41 | Adendo G, inalterada |
| P2 | 22 | 22 | Adendo G, inalterada |
| P4 | 5 | 6 | Adendo G, inalterada |
| GL | 3 | 4 | Adendo G, inalterada |
| LC | 0 | 15 | Adendo G, inalterada |
| **Retomada na main** | **164** | **180** | 93 + 41 + 22 + 5 + 3 + 0 = 164 ✓ |

## I2. Verificação (recálculo próprio do produtor)

1. Base (Adendo G §G1/G2): retomada na main 163 = ED 92 + P3 41 + P2 22 + P4 5 + GL 3 (92 + 41 + 22 + 5 + 3 = 163 ✓); total na main 263 = 100 pré-retomada + 163; aguardando push 17 = 180 − 163 ✓; 263/286 = 91,958041…% → 92,0%.
2. Pouso da ED-51: commit `91534571a90a835d71d7a01d5ccec10c7f4a29a4` (09/11/2026 16:41:20Z), HEAD verificado por leitura viva da main pela mesa da Rodada 13 (consolidado §4: cadeia `91534571 ← 70395bda ← 430c0a1 ← 1bd9278 ← 3edcbf7 ← fb9d346`). **Zero chamadas ao GitHub nesta produção** (parada dura por HTTP 429) — nenhuma leitura viva própria; a verificação viva citada é a da mesa. Corroboração local: `ed-51/VERIFICACAO-POS-POUSO-ED-51.md` regista "HEAD depois: `91534571a90a835d71d7a01d5ccec10c7f4a29a4`" e "Commit único: `91534571a90a835d71d7a01d5ccec10c7f4a29a4`", com 29/29 ficheiros byte-idênticos pós-pouso.
3. Recálculo: ED na main 92 + 1 (ED-51) = 93; retomada na main 163 + 1 = 164; total na main 263 + 1 = 264; aguardando push 17 − 1 = 16; 180 − 164 = 16 ✓; 264/286 = 0,92307692… = 92,307692…% → **92,3%** a uma casa decimal por arredondamento.
4. O Adendo G era correcto quando escrito (163/180, 263/286 = 92,0%, 17 aguardando — confirmado pelo Adendo H, foto 12:10:59 −03, HEAD `da56e7a`) e fica historicamente correcto; a sua desactualização deve-se unicamente ao pouso posterior da ED-51, que este adendo regista. O mesmo vale para o Adendo H: foto do instante 12:10:59 −03, anterior ao pouso da ED-51.

## I3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## I4. Notas do adendo

- A manutenção deste adendo **não mudou o placar M2**: 0
  classificações novas, 0 reclassificações; A, B, C e sem
  classificação inalterados. Nenhuma reclassificação resulta do pouso
  da ED-51: a ED-51 já estava classificada (categoria B, adendo C) e o
  pouso só muda a sua posição "na main".
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104)
  são classe D por desenho/decisão do dono (disparo nomeado) e não
  entram neste registro até serem produzidas.
- **R13-A2 — os pousos R7-A/B/C e a base fictícia completa não alteram
  nenhuma contagem de tarefas.** Os lotes R7-A (commit `1bd9278`),
  R7-B (commit `430c0a1`) e R7-C (commit `70395bda`) são correcções de
  achados da auditoria contínua, fora do universo de 286, e não mudam
  nenhuma classificação (precedente: Adendos E §E2 e F §F2 para
  correcções). A base fictícia completa (262/262 lotes, head
  `fb9d346cdae4`, verbete do diário pousado, commit `3edcbf7`) é seed de
  testes, não entrega de tarefa — não muda nenhuma contagem deste
  registro (precedente: Adendo H §H2, "Os lotes de seed não são
  entregas de tarefa"). A cadeia de commits na main, verificada pela
  mesa da Rodada 13 (consolidado §4), é `91534571 ← 70395bda ← 430c0a1
  ← 1bd9278 ← 3edcbf7 ← fb9d346`.
- O adendo G (§G1) e o adendo H (§H1) ficam historicamente correctos
  como registos dos instantes que descrevem; a posição corrente "na
  main" é a deste adendo I até à próxima manutenção.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.

---

## J. Adendo de 09/11/2026 — Manutenção da rodada 14:05 (job ziipsan-push-2h) — verificação do Adendo I por leitura viva própria do GitHub

- **Manutenção (job):** `ziipsan-push-2h`, rodada 14:05 de 09/11/2026
- **Âmbito:** verificação do Adendo I (lote R13-B, produtor `ae9f5d56-1460-4b24-a946-4ed3f87d43c5`) por leitura viva própria do GitHub; 0 classificações novas, 0 reclassificações. Nenhuma linha das secções anteriores (§§1–9, adendos A–I) foi alterada. O Adendo I continua a ser o registo fiel da posição após o pouso da ED-51; a sua verificação viva própria é a deste adendo.

## J1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §I1 |
| Executadas (universo deste registro) | 280 | inalterado — lista de produzidas byte-idêntica à da foto 12:05 (180 tarefas; 0 de diferença em conjunto, conferido pelo `foto-resultado-1405.json` contra o `foto-resultado-1215.json`) |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada nesta rodada; 0 reclassificações — a classificação M2 (65 A + 99 B + 16 C + 100 sem classificação) não é afectada |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

## J2. Verificação do Adendo I por leitura viva própria do GitHub

1. Leitura viva desta manutenção (conector GitHub, `list_commits`, sem 429): **957 commits, HEAD `91534571a90a835d71d7a01d5ccec10c7f4a29a4`** (`feat(ed-51): ED-51 Apoio a ISO/IEC 17025 — LIMS (27 ficheiros) + adendo ED51-1 + verbete de diario`, 09/11/2026 16:41:20Z). Continuidade SHA a SHA contra a foto 12:05 (904 commits, HEAD `da56e7a`): **0 ausentes, 53 novos** — 48 lotes de seed da base fictícia (215–262) + verbete do diário `3edcbf7` + R7-A `1bd9278` + R7-B `430c0a1` + R7-C `70395bda` + ED-51 `91534571`. A cadeia `91534571 ← 70395bda ← 430c0a1 ← 1bd9278 ← 3edcbf7 ← fb9d346` está presente na minha própria leitura, na mesma ordem.
2. Recálculo próprio (script `contar-foto.py` sobre a lista viva, `foto-resultado-1405.json`): retomada na main **164** = ED 93 + P3 41 + P2 22 + P4 5 + GL 3 (93 + 41 + 22 + 5 + 3 = 164 ✓); total na main **264 de 286** = 100 pré-retomada + 164; aguardando push **16** = 180 − 164 (GL.4, ED-01, LC.1–LC.14); 264/286 = 92,307692…% → **92,3%**. **Confere exactamente o Adendo I §I1/§I2** (164/180, 264/286, 92,3%, 16 aguardando).
3. O produtor do Adendo I declarou zero chamadas ao GitHub (parada dura por HTTP 429) e apoiou-se na verificação viva da mesa da Rodada 13; esta manutenção supre a verificação viva própria do job. **R13-A1 e R13-A2 FECHADOS em conteúdo** (posição pós-ED-51 verificada por leitura viva própria; declaração R7/base corroborada pelo casamento: o assunto do R7-A casa só a ED-97, já na main; os assuntos do R7-B/R7-C e dos lotes de seed não carregam ID de tarefa do plano).
4. O Adendo I era correcto quando escrito e fica historicamente correcto; a sua posição "na main" continua corrente — nenhum pouso de tarefa do plano ocorreu depois dele até ao instante desta leitura (o HEAD vivo segue `91534571`; o próximo da fila, ED-51-PD08, ainda não pousou).

## J3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## J4. Notas do adendo

- A manutenção desta rodada **não mudou o placar M2**: 0
  classificações novas, 0 reclassificações; A, B, C e sem
  classificação inalterados.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104)
  são classe D por desenho/decisão do dono (disparo nomeado) e não
  entram neste registro até serem produzidas.
- Nota de frescura da foto: o HEAD vivo é `91534571` (ED-51,
  16:41:20Z); nenhum commit posterior existia no instante da leitura
  viva desta manutenção (~14:07 −03). A fila de pouso segue parada
  após a ED-51; o próximo da fila é o ED-51-PD08.

---

## K. Adendo de 09/11/2026 — Manutenção da rodada 16:05 (job ziipsan-push-2h) — actualização pós-pouso da GL.4

- **Manutenção (job):** `ziipsan-push-2h`, rodada 16:05 de 09/11/2026
- **Âmbito:** actualização da posição "na main" após o pouso da GL.4 (e da ED-51-PD08, fora do universo) — Rodada 14 da auditoria contínua, FECHADA ~15:57 −03 (consolidado `00-CONSOLIDADO-RODADA14.md`). Nenhuma linha das secções anteriores (§§1–9, adendos A–J) foi alterada. O adendo I continua a ser o registo fiel da posição após o pouso da ED-51 e o adendo J o registo fiel da foto das 14:05; as suas cifras ficam historicamente correctas; a posição corrente, após o pouso da GL.4, é a declarada neste adendo.

## K1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §J1 |
| Executadas (universo deste registro) | 280 | inalterado — a GL.4 já estava executada e classificada (adendo C, categoria A); o pouso não cria executada nova; lista de produzidas byte-idêntica à da foto 14:05 (180 tarefas; `foto-resultado-1605.json` contra `foto-resultado-1405.json`: diferença em "na main" só a GL.4) |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada nesta rodada; 0 reclassificações — a classificação M2 (65 A + 99 B + 16 C + 100 sem classificação) não é afectada |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

### Posição na main após o pouso da GL.4

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **165 de 180** | Adendo I/J: 164 (ED 93 + P3 41 + P2 22 + P4 5 + GL 3); com a GL.4 pousada, GL passa a 4, logo 165 |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **265 de 286** | 100 + 165 = 265 |
| **Aguardando push** | **15** | 180 − 165 = 15 (ED-01, LC.1–LC.14) |
| **Total na main (%)** | **92,7%** | 265/286 = 92,657342…% → 92,7% a uma casa decimal, por arredondamento |

Por fase, na main (só a GL muda face ao adendo J):

| Fase | Na main | De | Fonte |
|---|---:|---:|---|
| GL | 4 de 4 | 3 de 4 | GL.4 pousada (`32fff01c`, 18:45:52Z = 15:45:52 −03) — **a GL está 4/4 na main pela primeira vez** |
| ED | 93 de 98 | 93 de 98 | inalterada |
| P3 | 41 de 41 | 41 de 41 | inalterada |
| P2 | 34 de 34 | 34 de 34 | inalterada |
| P4 | 5 de 6 | 5 de 6 | inalterada |
| LC | 0 de 15 | 0 de 15 | inalterada |

## K2. Verificação por leitura viva própria do GitHub

1. Leitura viva desta manutenção (conector GitHub, `list_commits`, sem 429): **959 commits, HEAD `32fff01c0b7ea04e435d901443495366f6479b3b`** (`feat(gl.4): GL.4 Aceite da homologação local — 2 ficheiros novos + verbete de diário (aceite do dono, decisão 37)`, 09/11/2026 18:45:52Z). Continuidade SHA a SHA contra a foto 14:05 (957 commits, HEAD `91534571`): **0 ausentes, 2 novos** — `f941d016` (ED-51-PD08, 18:43:07Z) e `32fff01c` (GL.4, 18:45:52Z). A cadeia `32fff01c ← f941d016 ← 91534571` está presente na minha própria leitura, na mesma ordem.
2. Recálculo próprio (script `contar-foto.py` sobre a lista viva, `foto-resultado-1605.json`): retomada na main **165** = ED 93 + P3 41 + P2 22 + P4 5 + GL 4 (93 + 41 + 22 + 5 + 4 = 165 ✓); total na main **265 de 286** = 100 pré-retomada + 165; aguardando push **15** = 180 − 165 (ED-01, LC.1–LC.14); 265/286 = 92,657342…% → **92,7%**. **Confere exactamente o consolidado da Rodada 14** (265/286, 92,7%, 15 aguardando).
3. Pouso da GL.4: commit `32fff01c` (2 ficheiros novos + verbete de diário), verificado em vivo pela mesa da Rodada 14 (consolidado §2: HEAD `32fff01c`, pai `f941d016`, 3/3 hashes idênticos) e conferido localmente neste job pela leitura de `gl.4/VERIFICACAO-POS-POUSO-GL.4.md` (tabela hash a hash 3/3: `DOSSIE-ACEITE-HOMOLOGACAO-GL4.md` `0dda9e48…`, `TERMO-DE-ACEITE-GL4.md` `934b4cab…`, diário `c54f3f7b…`). A GL.4 já estava classificada **A** (adendo C, §C2: aceite formal do dono, decisão nº 37) — o pouso só muda a sua posição "na main", não a classificação.
4. ED-51-PD08: commit `f941d016` (6 ficheiros novos), verificado 6/6 por hash pela mesa da Rodada 14 e conferido localmente pela leitura de `ed-51-pd08/VERIFICACAO-POS-POUSO-ED-51-PD08.md`. A ED-51-PD08 **não é tarefa do plano** (fora do universo de 286; pasta `ed-51-pd08/` sem ID de tarefa) — não muda nenhuma contagem deste registro (precedente: Adendos E §E2 e F §F2 para correcções; Adendo I §I4 para a base fictícia). Nota de casamento: o assunto `feat(ed-51-pd08)` é atribuído pelo `contar-foto.py` à ED-51 (já na main desde o adendo I) — nenhuma tarefa nova entra na contagem por ele.
5. A Rodada 14 FECHOU em conteúdo R13-B1/B2/B3, R13-P2-01, R13-A1/A2 e R13-C1/C2 (consolidado §4) — nenhum deles toca classificação M2. Os 2 menores novos (R14-PD08-01, R14-R13C-01) foram repassados no lote R14-A; a pasta `correcoes-r14-a/` existia mas estava **vazia** no instante da minha verificação (~16:10 −03) — o conteúdo do lote não estava no disco local; o repasse em si é da mesa da Rodada 14, não deste job. **C4-01 segue ABERTO** — sem decisão do dono (não existe item 48; `DECISOES-DO-DONO.md` segue com 251 linhas, última decisão a nº 47, verificado por leitura do ficheiro).

## K3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## K4. Notas do adendo

- A manutenção desta rodada **não mudou o placar M2**: 0
  classificações novas, 0 reclassificações; A, B, C e sem
  classificação inalterados. Nenhuma reclassificação resulta do pouso
  da GL.4: a GL.4 já estava classificada (categoria A, adendo C) e o
  pouso só muda a sua posição "na main". A ED-51-PD08 não é tarefa do
  plano e não altera nenhuma contagem.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104)
  são classe D por desenho/decisão do dono (disparo nomeado) e não
  entram neste registro até serem produzidas.
- O adendo J (§J1) fica historicamente correcto como registo da foto
  das 14:05; a posição corrente "na main" é a deste adendo K até à
  próxima manutenção.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- Nota de frescura da foto: o HEAD vivo é `32fff01c` (GL.4,
  18:45:52Z); nenhum commit posterior existia no instante da leitura
  viva desta manutenção (~16:07 −03). A fila de pouso segue parada
  após a GL.4; o próximo da fila é a ED-01.

---

## L. Adendo de 09/11/2026 — Actualização intra-rodada 16:05 (job ziipsan-push-2h) — ED-01 e LC.1 pousaram durante a rodada

- **Manutenção (job):** `ziipsan-push-2h`, rodada 16:05 de 09/11/2026 — segunda leitura viva própria do GitHub na mesma rodada
- **Âmbito:** a fila de pouso andou DURANTE a rodada: a ED-01 pousou às 19:07:07Z e a LC.1 às 19:13:35Z, depois da primeira leitura viva desta manutenção (~16:07 −03, adendo K). Nenhuma linha das secções anteriores (§§1–9, adendos A–K) foi alterada. O adendo K continua a ser o registo fiel da foto das ~16:07 −03; a posição corrente, após os dois pousos, é a declarada neste adendo.

## L1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §K1 |
| Executadas (universo deste registro) | 280 | inalterado — a ED-01 e a LC.1 já estavam executadas e classificadas (adendo C, ambas categoria B); os pousos não criam executadas novas; lista de produzidas byte-idêntica à da foto 14:05 (180 tarefas) |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada nesta rodada; 0 reclassificações — a classificação M2 (65 A + 99 B + 16 C + 100 sem classificação) não é afectada |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

### Posição na main após os pousos da ED-01 e da LC.1

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **167 de 180** | Adendo K: 165 (ED 93 + P3 41 + P2 22 + P4 5 + GL 4); com a ED-01 pousada, ED passa a 94, e com a LC.1 pousada, LC passa a 1, logo 167 |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **267 de 286** | 100 + 167 = 267 |
| **Aguardando push** | **13** | 180 − 167 = 13 (LC.2–LC.14) |
| **Total na main (%)** | **93,4%** | 267/286 = 93,356643…% → 93,4% a uma casa decimal, por arredondamento |

Por fase, na main (só ED e LC mudam face ao adendo K):

| Fase | Na main | De | Fonte |
|---|---:|---:|---|
| ED | 94 de 98 | 93 de 98 | ED-01 pousada (`6495e78e`, 19:07:07Z = 16:07:07 −03) — **a ED está 94/98 na main** |
| LC | 1 de 15 | 0 de 15 | LC.1 pousada (`0d0b0c52`, 19:13:35Z = 16:13:35 −03) — **a LC tem a primeira tarefa na main** |
| GL | 4 de 4 | 4 de 4 | inalterada |

## L2. Verificação por leitura viva própria do GitHub (segunda leitura da rodada)

1. Segunda leitura viva desta manutenção (conector GitHub, `list_commits`, sem 429): **961 commits, HEAD `0d0b0c5205492734a64ed4f54b10c254b177cb66`** (`docs(lc.1): LC.1 Ensaio de implantação na AWS (preparação) — 5 ficheiros novos + verbete de diário`, 09/11/2026 19:13:35Z). Continuidade SHA a SHA contra a primeira leitura desta rodada (959 commits, HEAD `32fff01c`): **0 ausentes, 2 novos** — `6495e78e` (ED-01, 19:07:07Z) e `0d0b0c52` (LC.1, 19:13:35Z). A cadeia `0d0b0c52 ← 6495e78e ← 32fff01c` está presente na minha própria leitura, na mesma ordem.
2. Recálculo próprio (script `contar-foto.py` sobre a lista viva, `foto-resultado-1605.json`): retomada na main **167** = ED 94 + P3 41 + P2 22 + P4 5 + GL 4 + LC 1 (94 + 41 + 22 + 5 + 4 + 1 = 167 ✓); total na main **267 de 286** = 100 pré-retomada + 167; aguardando push **13** = 180 − 167 (LC.2–LC.14); 267/286 = 93,356643…% → **93,4%**.
3. Pouso da ED-01: commit `6495e78e` (7 ficheiros novos sob `docs/poc/`, sem verbete de diário por prescrição do LANDING), verificado 7/7 por hash pós-pouso (`ed-01/VERIFICACAO-POS-POUSO-ED-01.md`, executor `6b4fbaa6…`; a primeira tentativa de `push-files` foi interrompida por reinício do serviço e reconciliada por `list_commits` antes da nova tentativa — HEAD permaneceu `32fff01c…`, sem duplicação). A ED-01 já estava classificada **B** (adendo C, §C2: kit físico e ensaios cronometrados externos pendentes; PD-01 local 55/4/0) — o pouso só muda a sua posição "na main", não a classificação. A prontidão PD-01 **não** foi declarada fechada (4 parciais de outros pacotes).
4. Pouso da LC.1: commit `0d0b0c52` (5 ficheiros novos sob `docs/lancamento/` + diário por append), verificado 6/6 por hash pós-pouso (`lc.1/VERIFICACAO-POS-POUSO-LC.1.md`, executor `41479bcd…`; adendo D-R1 pousado como 5.º ficheiro, por prescrição do LANDING — estados mudados, incluindo a GL.4 aceite e a ED-01 pousada). A LC.1 já estava classificada **B** (adendo C, §C2: ensaio na AWS não executado nem simulado; conta AWS adiada para a contratação, decisão nº 41) — o pouso só muda a sua posição "na main", não a classificação. O ensaio na AWS **não foi executado nem simulado** neste pouso.
5. O casamento de assuntos atribui os dois commits correctamente: `docs(poc): ED-01 — …` casa a ED-01 (pat_scope) e `docs(lc.1): LC.1 …` casa a LC.1 (pat_paren + pat_scope). Nenhum outro commit novo carrega ID de tarefa do plano.

## L3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## L4. Notas do adendo

- A manutenção desta rodada **não mudou o placar M2**: 0
  classificações novas, 0 reclassificações; A, B, C e sem
  classificação inalterados. Nenhuma reclassificação resulta dos
  pousos da ED-01 e da LC.1: ambas já estavam classificadas
  (categoria B, adendo C) e os pousos só mudam as suas posições
  "na main".
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104)
  são classe D por desenho/decisão do dono (disparo nomeado) e não
  entram neste registro até serem produzidas.
- O adendo K (§K1) fica historicamente correcto como registo da foto
  das ~16:07 −03; a posição corrente "na main" é a deste adendo L
  até à próxima manutenção.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- Nota de frescura da foto: o HEAD vivo é `0d0b0c52` (LC.1,
  19:13:35Z); a fila de pouso estava a andar no instante da segunda
  leitura viva desta manutenção (~16:17 −03) — a LC.2 é a próxima da
  fila e pode pousar a seguir; a foto é o instante 16:13:35 −03.

---

## M. Adendo de 09/11/2026 — Manutenção da rodada 18:05 (job ziipsan-push-2h) — foto sem mudança

- **Manutenção (job):** `ziipsan-push-2h`, rodada 18:05 de 09/11/2026 — leitura viva própria do GitHub na mesma rodada
- **Âmbito:** nenhuma linha das secções anteriores (§§1–9, adendos A–L) foi alterada. O adendo L continua a ser o registo fiel da foto das ~16:17 −03; a posição corrente é a declarada neste adendo.

## M1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §L1 |
| Executadas (universo deste registro) | 280 | inalterado — lista de produzidas byte-idêntica à da foto 16:05 (180 tarefas, `foto-resultado-1805.json` vs `foto-resultado-1605.json`); nenhuma tarefa nova executada nesta rodada |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada; 0 reclassificações — a classificação M2 (65 A + 99 B + 16 C + 100 sem classificação) não é afectada |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

### Posição na main — inalterada

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **167 de 180** | inalterado face ao adendo L |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **267 de 286** | 100 + 167 = 267 |
| **Aguardando push** | **13** | 180 − 167 = 13 (LC.2–LC.14) |
| **Total na main (%)** | **93,4%** | 267/286 = 93,356643…% → 93,4% a uma casa decimal, por arredondamento |

## M2. Verificação por leitura viva própria do GitHub

1. Leitura viva desta manutenção (conector GitHub, `list_commits`, sem 429): **961 commits, HEAD `0d0b0c5205492734a64ed4f54b10c254b177cb66`** (`docs(lc.1): LC.1 Ensaio de implantação na AWS (preparação) — 5 ficheiros novos + verbete de diário`, 09/11/2026 19:13:35Z). Continuidade SHA a SHA contra a foto 16:05 (`commits_1605b-raw.json`, 961 commits): **0 ausentes, 0 novos** — o HEAD não se moveu nesta janela. A segunda tentativa de pouso da LC.2 (após a decisão do dono das 16:25 −03) foi recusada com o mesmo 403 e **nenhum commit foi criado** (ver `lc.2/POUSO-BLOQUEADO-LC.2.md` §5).
2. Recálculo próprio (script `contar-foto.py` sobre a lista viva, `foto-resultado-1805.json`): retomada na main **167** = ED 94 + P3 41 + P2 22 + P4 5 + GL 4 + LC 1 (94 + 41 + 22 + 5 + 4 + 1 = 167 ✓); total na main **267 de 286** = 100 pré-retomada + 167; aguardando push **13** = 180 − 167 (LC.2–LC.14); 267/286 = 93,356643…% → **93,4%**. Listas de produzidas, na main, aguardando push e não iniciadas **byte-idênticas** às da foto 16:05.
3. Nenhum commit novo carrega ID de tarefa do plano; os dois commits-sonda (`7b19662`, `044b4a5`) continuam fora do padrão, sem efeito na contagem.
4. Pacotes novos/modificados na janela fora do universo de 286 e sem efeito neste registro: `correcao-ci-security-teste` (achado nº 4 da CI, parte 2 — cadeia completa, portão exit 0), `correcoes-r14-a` (agora com REVISAO/CONFIRMACAO — cadeia completa) e `correcoes-r15-b` (repasse R15-B em execução; só o script de provas no disco até aqui). O lote R15-A (registos da LC.2) ainda não tem pasta no disco.

## M3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## M4. Notas do adendo

- A manutenção desta rodada **não mudou o placar M2**: 0 classificações novas, 0 reclassificações; A, B, C e sem classificação inalterados.
- A Rodada 15 da auditoria contínua (consolidado `00-CONSOLIDADO-RODADA15.md`, ~17:43 −03) **revalidou os adendos K e L** (aritmética recalculada linha a linha, 0 achados novos sobre o M2) e fechou em conteúdo R14-PD08-01 e R14-R13C-01. Os achados novos da Rodada 15 (R15-F1 moderado + R15-F2/F3/F4 e R15-S3-01..04 menores) são de registo/evidência e não mudam nenhuma classificação: vão ao repasse R15-A/B, fora do universo de 286.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104) são classe D por desenho/decisão do dono (disparo nomeado) e não entram neste registro até serem produzidas.
- Nota de frescura da foto: o HEAD vivo é `0d0b0c52` (LC.1, 19:13:35Z = 16:13:35 −03); a foto é o instante 16:13:35 −03 — a main não se moveu entre as 16:05 e as 18:05. A fila de pouso segue parada na LC.2 (segunda tentativa recusada com o mesmo 403).

## M5. Nota pós-escrita (mesma rodada, ~18:13 −03)

- Depois de escrito este adendo, e ainda durante a rodada 18:05, a sessão principal **produziu o lote R15-A** (`correcoes-r15-a/`, ~18:11–18:13 −03): errata R15-F2/F3, registo da instrução do despacho (R15-F4) e **a decisão do dono das 16:25 −03 registada formalmente como decisão nº 48** no `DECISOES-DO-DONO.md` (251→257 linhas). A frase da §M2 ("o lote R15-A ainda não tem pasta no disco") era exacta no instante da escrita e fica historicamente correcta; o estado corrente é: **R15-A PRODUZIDO — aguarda revisão independente, confirmação e portão**. O R15-F1 está fechado em conteúdo pela produção; a revisão/confirmação ainda não existem.
- Esta nota **não muda nenhuma classificação nem nenhum total** deste registro (o R15-A é correcção fora do universo de 286).

---

## N. Adendo de 09/11/2026 — Manutenção da rodada 20:05 (job ziipsan-push-2h) — foto sem mudança

- **Manutenção (job):** `ziipsan-push-2h`, rodada 20:05 de 09/11/2026 — leitura viva própria do GitHub na mesma rodada
- **Âmbito:** nenhuma linha das secções anteriores (§§1–9, adendos A–M) foi alterada. O adendo M continua a ser o registo fiel da foto das ~18:13 −03; a posição corrente é a declarada neste adendo.

## N1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §M1 |
| Executadas (universo deste registro) | 280 | inalterado — lista de produzidas byte-idêntica à da foto 18:05 (180 tarefas, `foto-resultado-2005.json` vs `foto-resultado-1805.json`); nenhuma tarefa nova executada nesta rodada |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada; 0 reclassificações — a classificação M2 (65 A + 99 B + 16 C + 100 sem classificação) não é afectada |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

### Posição na main — inalterada

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **167 de 180** | inalterado face ao adendo M |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **267 de 286** | 100 + 167 = 267 |
| **Aguardando push** | **13** | 180 − 167 = 13 (LC.2–LC.14) |
| **Total na main (%)** | **93,4%** | 267/286 = 93,356643…% → 93,4% a uma casa decimal, por arredondamento |

## N2. Verificação por leitura viva própria do GitHub

1. Leitura viva desta manutenção (conector GitHub, `list_commits`, sem 429): **961 commits, HEAD `0d0b0c5205492734a64ed4f54b10c254b177cb66`** (`docs(lc.1): LC.1 Ensaio de implantação na AWS (preparação) — 5 ficheiros novos + verbete de diário`, 09/11/2026 19:13:35Z). Continuidade SHA a SHA contra a foto 18:05 (`commits_1805-raw.json`, 961 commits): **0 ausentes, 0 novos** — o HEAD não se moveu nesta janela. Nenhuma tentativa nova de pouso da LC.2 nesta janela (nada registado em `lc.2/POUSO-BLOQUEADO-LC.2.md` depois do §5).
2. Recálculo próprio (script `contar-foto.py` sobre a lista viva, `foto-resultado-2005.json`, confirmado por segunda contagem independente com `contar.py`, `contagem_2005.json` — os dois scripts concordam em 280/267): retomada na main **167** = ED 94 + P3 41 + P2 22 + P4 5 + GL 4 + LC 1 (94 + 41 + 22 + 5 + 4 + 1 = 167 ✓); total na main **267 de 286** = 100 pré-retomada + 167; aguardando push **13** = 180 − 167 (LC.2–LC.14); 267/286 = 93,356643…% → **93,4%**. Listas de produzidas, na main, aguardando push e não iniciadas **byte-idênticas** às da foto 18:05.
3. Nenhum commit novo carrega ID de tarefa do plano; os dois commits-sonda (`7b19662`, `044b4a5`) continuam fora do padrão, sem efeito na contagem.
4. Pacotes novos/modificados na janela fora do universo de 286 e sem efeito neste registro: `correcao-ci-fiscal-pom` (achado nº 5 da CI — `ziipsan-fiscal` não compila por falta da dependência Spring Security no pom; cadeia completa, portão APROVADO exit 0 na 2.ª corrida) e `correcoes-r15-b` (agora com cadeia completa — repasse R15-B concluído em conteúdo; pouso pendente). A pasta `correcoes-r16-a/` foi criada nesta janela (vazia até aqui — lote R16-A ainda por produzir).

## N3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## N4. Notas do adendo

- A manutenção desta rodada **não mudou o placar M2**: 0 classificações novas, 0 reclassificações; A, B, C e sem classificação inalterados.
- A Rodada 16 da auditoria contínua (consolidado `00-CONSOLIDADO-RODADA16.md`, ~20:10 −03) **revalidou os adendos K, L e M** (0 achados novos sobre o M2) e fechou em conteúdo R15-F1, R15-F2, R15-F3, R15-F4 e R15-S3-01..04. Achados novos da Rodada 16 (4 menores: R16-1A-1/2/3, R16-2B-1) são de registo/evidência do lote R15-A e não mudam nenhuma classificação: vão ao repasse R16-A, fora do universo de 286.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104) são classe D por desenho/decisão do dono (disparo nomeado) e não entram neste registro até serem produzidas.
- Nota de frescura da foto: o HEAD vivo é `0d0b0c52` (LC.1, 19:13:35Z = 16:13:35 −03); a foto é o instante 16:13:35 −03 — a main não se moveu entre as 16:05 e as 20:05. A fila de pouso segue parada na LC.2 (nenhuma tentativa nova nesta janela; a permissão Workflows concedida às 16:25 −03, decisão nº 48, segue sem efeito verificado).

---

## O. Adendo de 09/11/2026 — Manutenção da rodada 22:05 (job ziipsan-push-2h) — a cadeia LC pousou por completo; fila das 286 tarefas vazia

- **Manutenção (job):** `ziipsan-push-2h`, rodada 22:05 de 09/11/2026 — leitura viva própria do GitHub na mesma rodada
- **Âmbito:** nenhuma linha das secções anteriores (§§1–9, adendos A–N) foi alterada. O adendo N continua a ser o registo fiel da foto das ~20:12 −03; a posição corrente é a declarada neste adendo.
- **Correção de registo:** este adendo corrige o achado **R17-4D-1** da Rodada 17 da auditoria contínua (o adendo N declarava 167/180 na main, 267/286 = 93,4%, 13 aguardando — valores desactualizados pelo pouso da LC.2–LC.14 nesta janela).

## O1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §N1 |
| Executadas (universo deste registro) | 280 | inalterado — lista de produzidas byte-idêntica à da foto 20:05 (180 tarefas, `foto-resultado-2205.json` vs `foto-resultado-2005.json`); nenhuma tarefa nova executada nesta rodada |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada; 0 reclassificações — a classificação M2 (65 A + 99 B + 16 C + 100 sem classificação) não é afectada |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

### Posição na main — MUDOU (a cadeia LC pousou por completo)

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **180 de 180** | era 167/180 no adendo N — **+13 (LC.2–LC.14)** |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **280 de 286** | 100 + 180 = 280 |
| **Aguardando push** | **0** | era 13 (LC.2–LC.14) — **fila das 286 tarefas VAZIA pela primeira vez** |
| **Total na main (%)** | **97,9%** | 280/286 = 97,902097…% → 97,9% a uma casa decimal, por arredondamento |

## O2. Verificação por leitura viva própria do GitHub

1. Leitura viva desta manutenção (conector GitHub, `list_commits`, sem 429): **984 commits, HEAD `eb4212cc57d1c71ebf6956a642ea1d5d14439209`** (`feat(lc.14): LC.14 Primeiro atestado — 3 ficheiros novos`, 2026-11-10T00:01:15Z = 21:01:15 −03). Continuidade SHA a SHA contra a foto 20:05 (`commits_2005-raw.json`, 961 commits): **0 ausentes, 23 novos**.
2. Os 23 commits novos, em ordem cronológica (UTC): `06754dcb` LC.2 (pouso parcial 44/45, 23:17:43Z) → `13814e16` workflow da LC.2 pelo PC do dono (23:20:12Z) → `153efacf` LC.3 → `a7173507` LC.4 → `d1ed79fa` LC.5 → `d5160617` LC.6 → `32f9b624` LC.7 → `53057c7d` correcao-cutoff-ci → `309af3d2` LC.8 → `967f0945` correcao-ci-agenda → `607e2ace` correcao-ci-core-testes → `9d85086d` correcao-ci-tenant-v13 → `b41e881b` correcao-ci-security-catalogo → `2786e809` correcao-ci-security-teste → `c5737c42` LC.9 → `07a05d10` correcao-ci-fiscal-pom → `67f31588` correcao-ci-platform-pom → `a7f8e79b` correcao-ci-fiscal-v47-testes → `de3d9bc8` LC.10 → `150d0385` LC.11 → `6be4a6aa` LC.12 → `4c9e79ae` LC.13 → `eb4212cc` LC.14. Cadeia linear, sem ramos nem reescrita (confirmado também pelo Revisor 1 da Rodada 17, sem achados).
3. **LC.2 COMPLETA 45/45:** o workflow `.github/workflows/deploy-infra.yml` foi lido da main viva (`get_file_contents`, `ref=main`): 9.514 bytes, SHA-256 **`c7373e0fbb16adc37e0faf915aef0092b7362847a1f70a9e8c060873ff8a583d`** — exactamente o hash esperado registado em `lc.2/POUSO-BLOQUEADO-LC.2.md` §7, byte-idêntico à cópia local em `lc.2/pouso/`. O 403 da permissão Workflows deixou de bloquear a cadeia pela rota dividida autorizada pelo dono às 20:13 −03 (decisão nº 48 na origem; rota dividida registada no §7 do mesmo ficheiro).
4. Recálculo próprio (script `contar-foto.py` sobre a lista viva, `foto-resultado-2205.json`, confirmado por segunda contagem independente com `contar.py` sobre a lista achatada, `contagem_2205b.json` — os dois scripts concordam em 280/280): retomada na main **180** = ED 94 + P3 41 + P2 22 + P4 5 + GL 4 + LC 14 (94 + 41 + 22 + 5 + 4 + 14 = 180 ✓); total na main **280 de 286** = 100 pré-retomada + 180; aguardando push **0**; 280/286 = 97,902097…% → **97,9%**. Listas de produzidas e de não iniciadas **byte-idênticas** às da foto 20:05.
5. Nenhum commit novo carrega ID de tarefa do plano fora da cadeia LC e das correcções da CI; os dois commits-sonda (`7b19662`, `044b4a5`) continuam fora do padrão, sem efeito na contagem.
6. Pacotes novos/modificados na janela fora do universo de 286 e sem efeito neste registro: `correcoes-r16-a` (lote R16-A PRODUZIDO com cadeia completa — RELATORIO, REVISAO APROVADO COM RESSALVAS, CONFIRMACAO CONFIRMADO, CONSOLIDADO, LANDING, portão exit 1 só Regra 3 — falsos positivos demonstrados, instância (x) do rol; aguarda pouso), `correcao-ci-platform-pom` (achado nº 7 da CI — cadeia completa, pousado como `67f31588`), `correcao-ci-fiscal-v47-testes` (achado nº 6 — cadeia completa, pousado como `a7f8e79b`, 1 ficheiro removido), `correcao-ci-platform-accesscontrol` (cadeia completa, mas **sem objecto** — ver O4) e `ci-vm/` (4 logs da CI corrida na VM — ver O4).

## O3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## O4. Notas do adendo

- A manutenção desta rodada **não mudou o placar M2**: 0 classificações novas, 0 reclassificações; A, B, C e sem classificação inalterados.
- A Rodada 17 da auditoria contínua está **em curso** (rodada das 21:43 −03; 4 revisores entregues, sem consolidado até o fecho desta foto). O Revisor 4 **revalidou os adendos K, L e M** e **fechou em conteúdo R16-1A-1, R16-1A-2, R16-1A-3 e R16-2B-1** (0 achados novos sobre o M2); os achados novos da Rodada 17 até aqui são de registo/evidência e de CI, e não mudam nenhuma classificação: vão ao repasse R17-A, fora do universo de 286.
- **CI na VM — sequência de 4 logs em `ci-vm/` (HEAD testado `a7f8e79b`, pós-pouso dos 9 pacotes da CI):** core **1528/1528** (258 classes) SUCCESS; security **48/48** (9 classes) SUCCESS — primeira evidência em log integral de 48/48 sobre a árvore pousada (inclui `PermissionCatalogEd06Test` 3/3, `PermissionCatalogP330Test` 2/2, `PermissionCatalogTest` 10/10) — **a P-CISEC-01 fecha-se em CI real**; fiscal **220/220** (41 classes) SUCCESS — entra pela primeira vez; platform **FAILURE a compilar os testes** (5 erros, 3 ficheiros: `GuestPaymentServiceTest`, `HomologationSecurityPortalTest`, `DocumentNamePolicyTest`) — **achado nº 8 da CI (R17-CI-01, grave)**; 15 módulos SKIPPED sem placar; 0 testes de integração (failsafe) executados. Achados nº 5, nº 6 e nº 7 **fechados em CI real** (o main do platform compila com a correcção). A P-CISEC-02 **não se materializou** (nenhum erro de `accesscontrol` no log 4) — confirma a correcção de registo da mesa.
- **R17-R2-01 (grave, Revisor 2 da Rodada 17):** o pacote `correcao-ci-platform-accesscontrol` **não tem objecto** — as 21 classes já estão na main desde 07/11 (pouso da ED-06, `ee7f1270`); correcção devida: retirar da fila e **não pousar**; a P-CISEC-02 fecha-se como **sem objecto**. A alegação de ausência foi verificada só contra a árvore local (stages/espelhos), não contra a main viva — lição de método registada pelo revisor.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104) são classe D por desenho/decisão do dono (disparo nomeado) e não entram neste registro até serem produzidas.
- Nota de frescura da foto: o HEAD vivo é `eb4212cc` (LC.14, 2026-11-10T00:01:15Z = 21:01:15 −03); a foto é o instante 21:01:15 −03 — a main moveu-se 23 commits desde as 20:05 (a cadeia LC.2–LC.14 + 9 pacotes da CI). **A fila de pouso das 286 tarefas está vazia (0 aguardando)**; a fila que segue é a de correcções (R8–R16-A, V103, tarifário, multimunicípio) e a CI total sobre o HEAD corrente segue por provar — o log 4 da VM correu sobre `a7f8e79b`, anterior à LC.10–LC.14.

## O5. Nota de correcção (mesma manutenção, ~22:30 −03) — consolidado da Rodada 17 e registo de portão do platform-pom

- **Rodada 17 FECHADA às ~22:15 −03** (`00-CONSOLIDADO-RODADA17.md`, escrito durante esta manutenção, após o fecho da foto do GitHub desta rodada): delta APROVADO em substância, **2 graves, 1 moderado, 14 menores**, repassados em 2 lotes (R17-A e R17-B, ambos em execução ao fecho desta foto). A frase da §O4 ("Rodada 17 em curso") era exacta no instante da escrita e fica historicamente correcta; o estado corrente é: **Rodada 17 FECHADA**. O consolidado confirma os números desta foto (280/286 = 97,9%, LC 15/15, HEAD `eb4212cc`) e fecha em conteúdo R13-B1/B2/B3, R15-S3-01..04 e R12-E1.
- **Correcção à §O2 item 6:** o pacote `correcao-ci-platform-pom` **não tem cadeia completa** — pousou **sem qualquer registo de portão** (0 ocorrências de "portao" no pacote; único dos 9 pacotes da CI sem registo — **R17-R2-02**, menor). Verificado por listagem directa do pacote nesta manutenção. Os pacotes `correcao-ci-fiscal-pom` e `correcao-ci-fiscal-v47-testes` têm registo de portão, mas sem saída arquivada (R17-R2-03/04). Esta correcção **não muda nenhum total deste registro**.
- **P-CITEST-01 RESOLVIDA** (G7 4/4 verde, core 1528/1528 no log 4 da VM); a P-CITEST-02 segue aberta na sua letra (rerun no PC do dono + Docker).
- **Lotes R17-A e R17-B em execução** ao fecho desta foto (pacotes `correcao-ci-platform-testes` — com `DIAGNOSTICO-ACHADO-8.md` — e `correcoes-r17-b`, criados durante esta rodada).
- Esta nota **não muda o placar M2** (65 A + 99 B + 16 C + 100 sem classificação).

## P. Adendo de 09/11/2026 — Manutenção da rodada 23:35 (job ziipsan-push-2h, fora de hora por ordem do dono) — V103 pousou; CI completa sobre a cura-lote-1: platform com 6 classes em falha

- **Manutenção (job):** `ziipsan-push-2h`, rodada 23:35 de 09/11/2026 — disparo fora de hora por ordem expressa do dono (23:35 −03): manter o Drive actualizado com os pacotes e logs da noite. Leitura viva própria do GitHub na mesma rodada (duas leituras: a primeira às ~23:36, HEAD `cd7fcb0`; a segunda após o pouso da V103, HEAD `451c151`).
- **Âmbito:** nenhuma linha das secções anteriores (§§1–9, adendos A–O) foi alterada. O adendo O continua a ser o registo fiel da foto das ~22:20 −03; a posição corrente é a declarada neste adendo.

## P1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §O1 |
| Executadas (universo deste registro) | 280 | inalterado — lista de produzidas byte-idêntica à da foto 22:05 (180 tarefas, `foto-resultado-2335.json` vs `foto-resultado-2205.json`, listas `na_main_retomada` e `nao_iniciadas` idênticas); nenhuma tarefa nova executada nesta rodada |
| **Classificadas neste adendo** | **0** | Nenhuma tarefa nova executada; 0 reclassificações — a classificação M2 (65 A + 99 B + 16 C + 100 sem classificação) não é afectada |
| **Universo consolidado** | **280** | 280 + 0 ✓ |

### Posição na main — SEM MUDANÇA no placar (4 commits novos, 0 tarefas do plano)

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **180 de 180** | inalterada |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **280 de 286** | 100 + 180 = 280 |
| **Aguardando push** | **0** | inalterado — fila das 286 tarefas continua vazia |
| **Total na main (%)** | **97,9%** | 280/286 = 97,902097…% → 97,9% a uma casa decimal, por arredondamento |

## P2. Verificação por leitura viva própria do GitHub

1. Leitura viva desta manutenção (conector GitHub, `list_commits`, sem 429): **988 commits, HEAD `451c1515f9f6e2675cf0e63d9e5e1af5442be14`** (`feat(v103): V103 chave de remessa persistida com UNIQUE (expand do expand-contract, item 46) — migration anulável + entidade com chave obrigatória no construtor + finder + 4 testes; lista de publicadas 116→117; V104 pendente NÃO pousa`, 2026-11-10T02:37:47Z = 23:37:47 −03). Continuidade SHA a SHA contra a foto 22:05 (`commits_2205-raw.json`, 984 commits): **0 ausentes, 4 novos**. A primeira leitura desta rodada (988−1 = 987 commits, HEAD `cd7fcb0`) foi refeita após o pouso da V103, que aterrou durante a rodada — a foto final é a segunda leitura.
2. Os 4 commits novos, em ordem cronológica (UTC): `a232fdc` (01:33:33Z) correcao-ci-platform-testes — achado nº 8 da CI (3 testes do platform alinhados: Jackson 3, LinkStorePort D3b, Mode.FACTORY_DEFAULT) → `7dc98ea` (02:29:37Z) correcao-ci-cura-lote-1 — cura em lote da integração (varredura de compilação até BUILD SUCCESS: 6 poms, regra GIS-05, tipos EPANET P2.21, acessores ProvisionedTenant) → `cd7fcb0` (02:36:54Z) correcao-ci-platform-testes — `scripts/ci-linux.sh` (R17-CI-04, lote próprio, +9 linhas só de leitura) → `451c151` (02:37:47Z) V103. Cadeia linear, sem ramos nem reescrita.
3. Recálculo próprio (script `contar-foto.py` sobre a lista viva, `foto-resultado-2335.json`, confirmado por segunda contagem independente com `contar.py` sobre a lista achatada, `contagem_2335b.json` — os dois scripts concordam em 280/280, HEAD `451c151`, 988 commits): retomada na main **180** = ED 94 + P3 41 + P2 22 + P4 5 + GL 4 + LC 14; total na main **280 de 286**; aguardando push **0**; 280/286 = 97,902097…% → **97,9%**.
4. Nenhum commit novo carrega ID de tarefa do plano; os dois commits-sonda (`7b19662`, `044b4a5`) continuam fora do padrão, sem efeito na contagem.
5. Pacotes novos/modificados na janela fora do universo de 286 e sem efeito neste registro: `correcao-ci-platform-testes` (achado nº 8 — cadeia completa, pousado como `a232fdc` + `cd7fcb0`), `correcao-ci-cura-lote-1` (cura da integração — cadeia completa, pousado como `7dc98ea2`), `v103-chave-remessa` (expand do expand–contract, item 46 — cadeia completa, pousado como `451c151`; a V104 pendente NÃO pousou), `correcoes-r17-b` (lote R17-B FECHADO — ver P4), `correcao-ci-cura-lote-2` (em produção durante esta rodada; pasta criada sem `entrega/` preenchida ao fecho da foto) e `ci-vm/` (log novo `ci-completa-pos-cura.log` — ver P4).

## P3. Totais consolidados (universo 280) — inalterados

| Dimensão | Nº | % de 280 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,2% |
| Núcleo entregue + ensaio externo pendente | **99** | 35,4% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,7% |
| **Total executadas** | **280** | 100% |

## P4. Notas do adendo

- A manutenção desta rodada **não mudou o placar M2**: 0 classificações novas, 0 reclassificações; A, B, C e sem classificação inalterados.
- **Lotes R17-A e R17-B FECHADOS nesta janela** (ambos com veredito formal, após o fecho da foto 22:05): **R17-A** — produção 5/5, revisão APROVADO COM RESSALVAS, confirmação CONFIRMADO COM RESSALVAS, correcção documentada, re-revisão APROVADO COM RESSALVAS, portão APROVADO em substância (exit 1 só pelo falso positivo da Regra 3, instância (x)); os 3 testes pousaram em `a232fdca` antes da autorização do portão — conteúdo verificado byte-idêntico à entrega (desvio de processo registado, R17A-01); o 4.º ficheiro (`scripts/ci-linux.sh`) pousou como lote próprio em `cd7fcb0`. **R17-B** — produção 12/12, revisão APROVADO COM RESSALVAS, confirmação CONFIRMADO COM RESSALVAS, portão APROVADO em substância (exit 1 só pelos 2 falsos positivos da Regra 3); **P-CISEC-02 fechada como sem objecto** (FECHO com proveniência exacta, 21/21 MATCH na main viva); pacote `correcao-ci-platform-accesscontrol` **retirado da fila — não pousar**; portão re-corrido retrospectivamente sobre os 4 pacotes em falta (exit 0, APROVADO, 6/6 OK nos 4). Os efeitos do R17-B são documentais (fecho + adendos nos pacotes-alvo) e viajam com os próximos pousos desses pacotes.
- **CI COMPLETA na VM sobre `7dc98ea2` (P-CURA-02) — log `ci-vm/ci-completa-pos-cura.log`, 3.908 linhas, terminado 23:32:36 −03, lido na íntegra por esta manutenção (placares por soma própria por classe, não por grep filtrado):** core **1528/1528** SUCCESS; security **48/48** SUCCESS; fiscal **220/220** SUCCESS — os três em execução, sobre a árvore com a cura-lote-1; platform **FAILURE** — **1.328 testes, 20 falhas, 6 erros, 6 classes**: `NotificationIngestWiringTest` (6 testes, 5 erros — `ClassCastException`: `Timestamp` não é `UUID` no `FakeDb`, linha 221), `RegulatoryLoadGuardTest` (11 testes, 2 falhas — a mensagem de recusa não cita "14.015"), `TenantMigrationGlobalSequenceCiTest` (4 testes, 2 falhas + 1 erro — `V100__global_sequence_start.sql` com versão 100 ≤ maior publicada 102; baseline esperado "27.1", actual "102"), `TenantProvisioningServiceTest` (16 testes, 13 falhas — **migration V1_11 DUPLICADA** no platform: `V1_11__work_items_and_delegations.sql` (P2.28) × `V1_11__personal_data_access_log_export.sql` (P1.12) — o Flyway recusa provisionar qualquer tenant), `FactoryProfilesManifestTest` (3 testes, 1 falha — ADMIN sem as 3 permissões `access:*` que o catálogo já tem) e `VerticalBoundaryTest` (2 testes, 2 falhas — termos de saneamento no núcleo, ~90 ficheiros). **15 módulos SKIPPED** (quality, losses, metering, accounting, integration, reporting, GIS, MTR, LGPD, registry, billing-rules, billing, asset, collection, app); **0 testes de integração (failsafe)** — o reactor parou no platform. A P-CURA-02 cumpre-se quanto a core/security/fiscal; a P-CURA-03 (ITs, incl. GIS) não foi alcançada. **Lote 2 da cura em produção** (pacote `correcao-ci-cura-lote-2`, criado durante esta rodada): renumerar a V1_11 da P2.28 para V105 (a V103 foi ocupada pela chave de remessa, `451c151`; a V104 está reservada ao contract) (precedente R2-G1), inserir a V100 na lista de publicadas, ajustar a asserção de baseline com justificação, remapear o fake do `NotificationIngestWiringTest`, citar a lei na mensagem do `RegulatoryLoadGuard`, e dar ao ADMIN as 3 `access:*` no manifesto. **Fora do lote (P-CURA2-06): o `VerticalBoundaryTest` é decisão de arquitectura do dono** — a regra "núcleo sem termos de saneamento" colide com ~90 ficheiros das frentes posteriores. O achado nº 8 está fechado (os 3 testes alinhados passam na compilação); o resultado desta CI é matéria nova para a auditoria (Rodada 18) — esta manutenção não lhe atribui número de achado.
- **V103 POUSOU nesta janela** (`451c151`, 23:37:47 −03, 8 ficheiros, 531 inserções, 1 deleção; lista de publicadas 116→117; V104 pendente NÃO pousou) — cadeia autorizada pelo dono às 23:34 −03 (coordenador-portão, sessão `3be9fc42…`), com verificação pós-pouso por hash (`VERIFICACAO-POS-POUSO-V103.md`). A fila de correcções perde a V103; seguem R8–R16-A, tarifário C0–C4 e multimunicípio MM-01..09 (MM-05 bloqueado).
- **Nova ordem do dono (23:34 −03, registada na MEMORY.md):** decisões em cartões com **3 opções cada**, sendo a preferencial sempre a saída **mais completa/detalhada**; publicar tudo o que estiver disponível para publicação; documentos-modelo trabalham-se com **DOCUMENTO FICTÍCIO** para a primeira rodada de testes. Pasta `documentos-modelo-ficticios/` criada durante esta rodada (`entrega/` em preenchimento). A **C4-01 segue ABERTA** e passa a apresentar-se no formato novo (cartão de 3 opções) — não é condição de pouso da C4.
- O `DECISOES-DO-DONO.md` **não mudou nesta rodada** (257 linhas, decisão nº 48 continua a última).
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- As 6 não executadas (P4.6, LC.15, ED-68, ED-102, ED-103, ED-104) são classe D por desenho/decisão do dono (disparo nomeado) e não entram neste registro até serem produzidas.
- Nota de frescura da foto: o HEAD vivo é `451c151` (V103, 2026-11-10T02:37:47Z = 23:37:47 −03); a foto é o instante 23:37:47 −03 — a main moveu-se 4 commits desde as 22:05 (platform-testes, cura-lote-1, ci-linux.sh, V103). **A fila de pouso das 286 tarefas continua vazia (0 aguardando)**; a CI total sobre o HEAD corrente segue por provar — a CI completa desta janela correu sobre `7dc98ea2`, anterior à V103 e ao `cd7fcb0`.

## Adendo R18-C3-01 — errata de contagem de linhas do log da CI completa (10/11/2026)

**Sessão (produtor do lote R18-B):** `41d7d8ed-b37c-4033-b659-42a8f0c26854` · **Achado:** R18-C3-01 (funde R18-C2-05, mesmo facto; Rodada 18).

Onde a secção "P4. Notas do adendo" acima diz "log `ci-vm/ci-completa-pos-cura.log`, 3.908 linhas", **leia-se "3.908 linhas não vazias (3.956 linhas totais)"**.

Verificação própria nesta sessão: `wc -l ci-vm/ci-completa-pos-cura.log` = **3.956**; `grep -c . ci-vm/ci-completa-pos-cura.log` = **3.908** (48 linhas vazias). A linha da secção P4 **não foi reescrita** — esta errata qualifica-a por adendo datado. Nenhum placar é afectado: todos foram somados por classe no ficheiro lido. Cópia deste adendo no pacote do lote R18-B (`correcoes-r18-b/ERRATA-REGISTRO-PLACAR-2D-R18-C3-01.md`).

## Q. Adendo de 10/11/2026 — Manutenção da rodada 00:05 (job ziipsan-push-2h) — 3 tarefas novas executadas (ED-68, ED-102, LC.15); cadeia de pousos 23:34 concluída; C4-01 FECHADA; CI completa pós-cura-lote-2 com o fiscal a falhar a compilação

- **Manutenção (job):** `ziipsan-push-2h`, rodada 00:05 de 10/11/2026. Leitura viva própria do GitHub na mesma rodada (conector GitHub, `list_commits`, sem 429).
- **Âmbito:** nenhuma linha das secções anteriores (§§1–9, adendos A–P) foi alterada. O adendo P continua a ser o registo fiel da foto das 23:35 de 09/11; a posição corrente é a declarada neste adendo.

## Q1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 280 | este registro, §P1 |
| Executadas (universo deste registro) | **283** | +3 nesta rodada: ED-68, ED-102 e LC.15 — todas com `entrega/` + revisão aprovada + confirmação independente (ver abaixo); lista de produzidas aprovadas `contagem_aprovadas2_0005.json` (183 tarefas) |
| **Classificadas neste adendo** | **3** | ED-68, ED-102 e LC.15 — **todas na categoria B** (evidência lida abaixo); 0 reclassificações |
| **Universo consolidado** | **283** | 280 + 3 ✓ |

### Novas classificações — evidência lida dos artefactos de cada tarefa

| Tarefa | Categoria | Evidência lida |
|---|---|---|
| ED-68 | **B** | `RELATORIO-ED-68.md`, pendências: **P-ED68-2 (transmissão real):** "envio efectivo da remessa a uma empresa real (ficheiro transmitido/chamada de API) e recepção real dos retornos — o pacote entrega geração determinística e processamento, com prova de ida-e-volta em teste" — acto externo (empresa real) pendente; P-ED68-1 (fiação), P-ED68-3 (renumerar V106 no pouso) e P-ED68-4 (SQL não aplicado em PG nesta VM; CI total) são as pendências universais do fluxo. Cadeia: REVISAO APROVADO COM RESSALVAS → CORRECAO dos 3 achados → REAPRECIACAO APROVADO COM RESSALVAS → CONFIRMACAO **CONFIRMADO** (53/53 testes no log final, 10 containers). |
| ED-102 | **B** | `RELATORIO-ED-102.md`, pendências: **P-ED102-01 — Leiaute oficial do Transferegov `[SEM FONTE]`:** "obter o leiaute oficial vigente de importação/prestação de contas"; **P-ED102-02 — Submissão real ao Transferegov:** "envio efetivo exige convénio real, credenciais/acesso do ente e ambiente oficial — ato externo futuro; nada foi simulado como submetido"; **P-ED102-03 — ERP de obras concreto por tenant:** "fornecedor real, endpoint e leiaute de cada cliente" — todos factores externos; P-ED102-04 (fiação) é pendência universal. Cadeia: REVISAO APROVADO COM RESSALVAS → CONFIRMACAO **CONFIRMADO COM RESSALVAS** (rerun próprio do confirmador 21/21). |
| LC.15 | **B** | `REVISAO-LC.15.md`, veredito APROVADO COM RESSALVAS: "A aprovação é do pacote para a fila do Portão; **não** é o aceite final da tarefa LC.15, que só fecha com os actos vivos da contratação (**P-LC15-01/02/03, decisão 41**)"; P-LC15-01 (conta AWS dedicada real e artefactos da conta), P-LC15-02 (cliente/contrato concreto p/ ativação), P-LC15-03 (teste TR-104 vivo) — actos externos. Cadeia: REVISAO APROVADO COM RESSALVAS (0 graves, 0 moderados, 2 menores) → CONFIRMACAO **CONFIRMADO COM RESSALVAS** (harness re-executado pelo confirmador 52/52, 17/17 hashes). |

### Não classificadas nesta rodada — declarado, com a razão

- **ED-103 — NÃO executada, logo não classificada.** O pacote aprovado nesta janela é o **dossiê de consulta** ordenado pela decisão 55 (`RELATORIO-PRODUCAO-ED-103.md`: "Este lote não contém parecer, opinião jurídica, resposta a qualquer quesito nem recomendação de conclusão legal — só perguntas, contexto factual e matrizes condicionais"; REVISAO APROVADO COM RESSALVAS → REAPRECIACAO APROVADO → CONFIRMACAO **CONFIRMADO**). A decisão 55 distingue o dossiê de "a produção da ED-103", que "arranca" só "com o parecer recebido e registado". O dossiê não é o produto de trabalho da tarefa (a implementação da antecipação/conta vinculada não existe); contá-lo como executado confundiria a preparação do gatilho com a tarefa. A ED-103 entra neste registro quando for produzida.
- **P4.6 — em produção, sem revisão aprovada** (só `entrega/` + logs de harness; sem RELATORIO, sem REVISAO, sem CONFIRMACAO) — não executada, não classificada.
- **ED-104 — não iniciada** (sem pasta).

### Posição na main — 3 aguardando push (não 0)

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **180 de 183** | inalterada em número; as 3 novas (ED-68, ED-102, LC.15) ainda não pousaram |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **280 de 286** | 100 + 180 = 280 |
| **Aguardando push** | **3** | ED-68, ED-102, LC.15 — a fila das 286 tarefas volta a ter 3 (estava vazia desde 23:37 de 09/11) |
| **Total na main (%)** | **97,9%** | 280/286 = 97,902097…% → 97,9% a uma casa decimal, por arredondamento |

## Q2. Verificação por leitura viva própria do GitHub

1. Leitura viva desta manutenção (conector GitHub, `list_commits`, sem 429): **1.010 commits, HEAD `1d7550536a721eecaccd1862629bba969c0c6f27`** (`feat(c4+mm-09+r9-b): Camada 4 campo e MM-09 com as correccoes do R9-B aplicadas no pouso + errata R10-B + verbetes de diario`, 2026-11-10T03:01:49Z = 00:01:49 −03). Continuidade SHA a SHA contra a foto 23:35 (`commits_2335-raw.json`, 988 commits): **0 ausentes, 22 novos**.
2. Os 22 commits novos, em ordem cronológica (UTC): `a0a6599` (02:40:26Z) fix(r8-b) — R8-B → `2cf832d` (02:43:52Z) docs(r8-a) — adendo R8-A → `1f580d9` (02:44:47Z) docs(r9-c) — R9-C → `ad809a8` (02:45:16Z) docs(r10-a) — R10-A → `aeefe1e` (02:46:01Z) docs(r11-a) — R11-A → `b251d3d` (02:49:10Z) docs(r12-b) — R12-B → `2a0cc7e` (02:50:43Z) docs(base-12k) — R13-C (LANDING integral + diário) → `aaebd9e` (02:51:24Z) docs(r13-a) — R13-A (transporte) → `ccfdb53` (02:52:48Z) docs(tarifario-c0) — C0 (ADR-0048) → `f6f8822` (02:53:51Z) feat(tarifario-c1) — C1 → `1e3592f` (02:54:29Z) feat(tarifario-c2) — C2 (billing.composition) → `edbc00c` (02:55:12Z) feat(tarifario-c3) — C3 → `1df6167` (02:55:23Z) fix(ci) — cura-lote-2 (parte 1) → `e6f6b74` (02:55:35Z) fix(ci) — cura-lote-2 (parte 2, delete da V1_11 duplicada) → `66c1b56` (02:55:52Z) feat(mm-01) → `08c4792` (02:56:46Z) feat(mm-02) → `53bee84` (02:57:07Z) feat(mm-04) → `6c912ef` (02:57:45Z) feat(mm-03) → `0bfee56` (02:58:25Z) feat(mm-06) → `452d119` (02:58:45Z) feat(mm-07) → `4002684` (03:00:04Z) feat(mm-08) → `1d75505` (03:01:49Z) feat(c4+mm-09+r9-b) — C4 + MM-09 + R9-B (HEAD). Cadeia linear, sem ramos nem reescrita. **MM-05 NÃO pousou** (bloqueado, P-MM05-R8C-01); **R14-A, R15-A/B e R16-A NÃO pousaram**.
3. Recálculo próprio (script `contar-foto.py` sobre a lista viva, `foto-resultado-0005.json`, confirmado por segunda contagem independente com `contar.py` sobre a lista achatada, `contagem_0005.json` — os dois scripts concordam em 280/280 na main, HEAD `1d75505`, 1.010 commits): retomada na main **180** = ED 94 + P3 41 + P2 22 + P4 5 + GL 4 + LC 14; total na main **280 de 286**; aguardando push **3** (ED-68, ED-102, LC.15). Nota de método: o mecânico (`contar-foto.py`) conta 185 pastas com `entrega/`; o aprovado é 183 — P4.6 e ED-103 ficam de fora pelo filtro de revisão (ver Q1); a ED-103, apesar da revisão aprovada do dossiê, fica de fora por não ser a execução da tarefa.
4. Nenhum commit novo carrega ID de tarefa do plano; os dois commits-sonda (`7b19662`, `044b4a5`) continuam fora do padrão, sem efeito na contagem.
5. Pacotes novos/modificados na janela fora do universo de 286 e sem efeito neste registro, excepto o registo: `correcao-ci-cura-lote-2` (pousado como `1df6167` + `e6f6b74`; cadeia completa), `correcoes-r8-a/b`, `correcoes-r9-b/c`, `correcoes-r10-a`, `correcoes-r11-a`, `correcoes-r12-b`, `correcoes-r13-a/c` (pousados na cadeia), `proposta-tarifaria/` (camadas C0–C4 pousadas) e `multimunicipio/` (MM-01/02/03/04/06/07/08/09 pousados; MM-05 bloqueado).

## Q3. Totais consolidados (universo 283)

| Dimensão | Nº | % de 283 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 23,0% |
| Núcleo entregue + ensaio externo pendente | **102** | 36,0% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,7% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,3% |
| **Total executadas** | **283** | 100% |

## Q4. Notas do adendo

- A manutenção desta rodada **mudou o placar M2: +3 classificações, todas na categoria B** (ED-68, ED-102, LC.15); A, C e sem classificação inalterados. Universo 280 → **283**.
- **Cadeia de pousos das 23:34 concluída ~00:03 de 10/11 (HEAD `1d75505`)**: pousaram R8-B, adendo R8-A, R9-C, R10-A, R11-A, R12-B, R13-C (com a correcção do cabeçalho do diário, P-R17-B-02; diário com 26 verbetes de 09/11), R13-A (transporte), tarifário C0–C4 (C4 junto ao R9-B e ao MM-09), multimunicípio MM-01/02/03/04/06/07/08/09 e cura-lote-2. **MM-05 segue BLOQUEADO** (P-MM05-R8C-01); **R14-A, R15-A/B e R16-A seguem na fila** (não pousaram nesta cadeia).
- **Decisões 49–57 (09/11/2026 23:52–23:57 −03; `DECISOES-DO-DONO.md` 257 → 311 linhas):** **49 — C4-01 FECHA-SE** (prova da INV-C2-11 sobre a base fictícia de 12.000 UCs E a prova proporcional mantida); **50 — P-JUR-FINAL-01 mantém o gatilho no fim** (sem antecipar); **51 — base fictícia mantida indefinidamente** (P-BF-01..05 resolvidas); **52 — LC.15 integral já**; **53 — ED-68 onda completa já**; **54 — ED-102 âmbito completo já**; **55 — ED-103 parecer encomendado, execução após o parecer**; **56 — P4.6 procura registada, executar já**; **57 — front só após a CI total verde** (gatilho cumulativo: CI verde + lista de ajustes do dono).
- **CI COMPLETA na VM sobre `e6f6b74` (cura-lote-2) — log `ci-vm/ci-completa-pos-cura-lote-2.log`, 945 linhas, terminado 23:58:06 −03, lido na íntegra por esta manutenção (placares por soma própria por classe, nunca por grep filtrado):** core **1533/1533** SUCCESS (+5 testes vs 1528/1528 da corrida sobre `7dc98ea2`); security **48/48** SUCCESS; fiscal **FAILURE a compilar o código principal** — 5 erros em `NfageThirdPartyExclusion.java` (ficheiro da C4): importa `br.com.ziipsan.commercial.billing.composition` (`BillChargeLine`, `BillComposition`) — o pacote existe no repo (criado pela C2, `1e3592f`), mas o pom do fiscal **não declara dependência do ziipsan-billing** (verificado no clone, HEAD `1d75505`: `grep ziipsan-billing commercial/ziipsan-fiscal/pom.xml` sem ocorrências) — defeito real de integração introduzido pela C4 (falta a dependência ou a rewiring do ficheiro); platform **+ 15 módulos SKIPPED**; **0 testes de integração (failsafe)** — o reactor parou no fiscal. A cura-lote-2 **não chegou a ser testada em execução** nesta corrida. O resultado é matéria nova para a auditoria (Rodada 18) — esta manutenção não lhe atribui número de achado. **A CI total sobre o HEAD corrente (`1d75505`, com C4/MM-09/R9-B) segue por provar.**
- **Rodada 18 da auditoria ainda sem pasta aberta** (última pasta: `2026-11-09-2343`, Rodada 17, fechada ~22:15 −03).
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- As 100 da pré-retomada **nunca são presumidas de aceite pleno**.
- Nota de frescura da foto: o HEAD vivo é `1d75505` (2026-11-10T03:01:49Z = 00:01:49 −03); as pastas locais moveram-se durante a rodada — REVISAO/CONFIRMACAO de LC.15, ED-68 e ED-103 aterraram 00:10–00:14 −03, depois da foto do GitHub. As contagens de executado usam o estado local às ~00:21 −03 (ED-68, ED-102 e LC.15 aprovadas; P4.6 sem revisão; ED-103 só com o dossiê); a posição na main é a da foto 00:01:49 −03. **A fila de pouso das 286 tarefas volta a ter 3 (ED-68, ED-102, LC.15)**; a CI total sobre o HEAD corrente segue por provar.

## R. Adendo de 10/11/2026 — Manutenção da rodada 02:05 (job ziipsan-push-2h) — 2 tarefas novas executadas (P4.6, ED-104); P4.6, ED-68, ED-102 e LC.15 pousadas; decisões 58, 59 e 60

- **Manutenção (job):** `ziipsan-push-2h`, rodada 02:05 de 10/11/2026. Leitura viva própria do GitHub na mesma rodada (conector GitHub, `list_commits`, sem 429 — uma falha transitória de transporte na página 2 da primeira tentativa, repetida com sucesso a seguir).
- **Âmbito:** nenhuma linha das secções anteriores (§§1–9, adendos A–Q) foi alterada. O adendo Q continua a ser o registo fiel da foto das 00:05 de 10/11; a posição corrente é a declarada neste adendo.

## R1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 283 | este registro, §Q1 |
| Executadas (universo deste registro) | **285** | +2 nesta rodada: P4.6 e ED-104 — ambas com `entrega/` + revisão aprovada + confirmação independente (ver abaixo); lista de produzidas aprovadas `contagem_aprovadas_0205.json` (185 tarefas) |
| **Classificadas neste adendo** | **2** | P4.6 e ED-104 — **ambas na categoria B** (evidência lida abaixo); 0 reclassificações |
| **Universo consolidado** | **285** | 283 + 2 ✓ |

### Novas classificações — evidência lida dos artefactos de cada tarefa

| Tarefa | Categoria | Evidência lida |
|---|---|---|
| P4.6 | **B** | `RELATORIO-P4.6.md` + `RELATORIO-CORRECCAO-P4.6.md` + `REVISAO-P4.6-REAPRECIACAO.md`: o núcleo está entregue e provado em banco — IT `RlsIsolationThreeModesIT` **executado 7/7** na correcção (P-P46-01 CUMPRIDA), unitários 29/29; ficam pendentes **P-P46-03 (ensaios E1–E4 sobre tenants reais e primeira mudança de modo real):** "acto operacional futuro: o procedimento está escrito e o plano executável existe; executar a mudança de modo de um tenant real (e registar os ensaios) só faz sentido quando existir o primeiro tenant a mudar de modo [A DEFINIR pelo dono/operação]" — ensaio em ambiente real sobre tenant real, fora do pacote; **P-P46-04 (segundo cluster físico de IT)** — ambiente real por existir; P-P46-02 (reactor fiscal vermelho por causa alheia) e P-C46-01/02 são as pendências de integração/fiação do fluxo. Cadeia: REVISAO REPROVADO (9 achados) → CORRECAO dos 9 → REAPRECIACAO **APROVADO COM RESSALVAS** → CONFIRMACAO **CONFIRMADO COM RESSALVAS** (19/19 hashes) → portão APROVADO → **pouso `a3022adb`** (20/20, verificação pós-pouso). |
| ED-104 | **B** | `RELATORIO-ED-104.md`, pendências: **P-ED104-01 — leiautes oficiais do SINIR e do IMA `[SEM FONTE]`** ("manual federal não público"; "leiaute oficial [SEM FONTE]"); **P-ED104-05 — fiação real:** "adapter real IMA/SINIR, migração das ligações dos CDFs 1:1 legados, aplicação do SQL em PG, validação externa do PDF/A (veraPDF)" — submissões reais ao SINIR/IMA são actos externos e "NÃO se simulam" (decisão 60); **P-ED104-07 — E10 PARCIAL:** "fórmulas oficiais dos indicadores das NR ANA 7/2024 e 14/2025 `[SEM FONTE]` — ler as Resoluções ANA 187/2024 e 276/2025 antes de nomear qualquer indicador ANA" — insumo oficial externo por ler; P-ED104-02/03/04 (catálogo IBAMA vazio por desenho, NBR 2024 não confirmada, enquadramento jurídico não presumido) são insumos documentais externos. Cadeia: REVISAO **APROVADO COM RESSALVAS** (0 graves, 0 moderados, 5 menores) → CONFIRMACAO **CONFIRMADO COM RESSALVAS** (reexecução própria 114/114, hashes 41/41) → portão APROVADO exit 0. **Aguarda push** (sem commit de entrega nesta foto). |

### Não classificadas nesta rodada — declarado, com a razão

- **ED-103 — NÃO executada, logo não classificada.** O pacote aprovado continua a ser só o **dossiê de consulta** ordenado pela decisão 55 (adendo Q); a execução arranca com o parecer recebido e registado. A ED-103 entra neste registro quando for produzida.
- **Não há outras executadas sem classificação:** as 100 da pré-retomada seguem sem classificação por relatório local (nunca presumidas de aceite pleno).

### Posição na main — 1 aguardando push

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **184 de 185** | +4 nesta rodada: ED-68 (`38f5497`), ED-102 (`ab131d7`), LC.15 (`b332192` 16/17 + workflow `6e01ef5` pelo PC do dono — LC.15 completa 17/17) e P4.6 (`a3022ad`) |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **284 de 286** | 100 + 184 = 284 |
| **Aguardando push** | **1** | ED-104 — aprovada e confirmada, portão APROVADO, sem commit de entrega |
| **Total na main (%)** | **99,3%** | 284/286 = 99,300699…% → 99,3% a uma casa decimal, por arredondamento |

## R2. Verificação por leitura viva própria do GitHub

1. Leitura viva desta manutenção (conector GitHub, `list_commits`, sem 429): **1.019 commits, HEAD `cc2e3766eead14589832b9ec28859d1fe732e50b`** (`docs(diario): verbete do pouso do controle GPS em tempo real (P-MOB-GPS-01, decisão 59) — ecce890a`, 2026-11-10T04:49:13Z = 01:49:13 −03). Continuidade SHA a SHA contra a foto 00:05 (`commits_0005.json`, 1.010 commits): **0 ausentes, 9 novos**.
2. Os 9 commits novos, em ordem cronológica (UTC): `ab131d7` (03:28:18Z) feat(ed-102) — ED-102 → `38f5497` (03:30:53Z) feat(ed-68) — ED-68 → `b332192` (03:34:38Z) feat(lc.15) — LC.15 16/17 → `6e01ef5` (03:48:02Z) feat(lc.15) — workflow deploy-infra pelo PC do dono (LC.15 completa 17/17) → `a3022ad` (04:15:41Z) feat(p4.6) — P4.6 → `b38ec85` (04:18:37Z) feat(mobile) — persistência SQLCipher + dados de emissão (decisão 58) → `d3a3d43` (04:21:59Z) docs(diario) — verbete do lote mobile → `ecce890` (04:48:28Z) feat(mobile) — GPS em tempo real (decisão 59, P-MOB-GPS-01) → `cc2e376` (04:49:13Z) docs(diario) — verbete do GPS (HEAD). Cadeia linear, sem ramos nem reescrita. **R14-A, R15-A/B, R16-A, R18-A/B e MM-05 NÃO pousaram** — seguem na fila.
3. Recálculo próprio (script `contar-foto.py` sobre a lista viva, `foto-resultado-0205.json`, confirmado por segunda contagem independente com `contar.py` sobre a lista achatada, `/tmp/contar0205.out` — **divergência explicada, não erro:** o `contar.py` conta 182 na retomada porque o seu padrão de parênteses exige hífen e os assuntos de pouso da P4.6 (`feat(p4.6):`) e da LC.15 (`feat(lc.15):`) usam ponto; os dois commits estão verificados na lista viva acima e o `contar-foto.py`, que normaliza o ponto, conta-os — 184/184 na retomada, 284/284 na main): retomada na main **184** = ED 96 + P3 41 + P2 22 + P4 6 + GL 4 + LC 15; total na main **284 de 286**; aguardando push entre as executadas **1** (ED-104). Nota de método: o mecânico (`contar-foto.py`) conta 186 pastas com `entrega/`; o aprovado é 185 — a ED-103 fica de fora pelo filtro de revisão (o dossiê não é a execução da tarefa).
4. Nenhum commit novo carrega ID de tarefa do plano além dos 4 acima; os dois commits-sonda (`7b19662`, `044b4a5`) continuam fora do padrão, sem efeito na contagem.
5. Pacotes novos/modificados na janela fora do universo de 286 e sem efeito neste registro, excepto o registo: `mobile-integracao-p03-p04` e `mobile-gps-tempo-real` (pousados como `b38ec85` + `d3a3d43` e `ecce890` + `cc2e376`), `correcao-ci-cura-lote-3` (produzido; o ciclo P-P46-02-CICLO segue por decidir), `correcoes-r18-a/b` (produzidos, sem pouso), `provas-temas` (galeria de 6 temas + 3 modelos corporativos, base da escolha do dono — decisão 57).

## R3. Totais consolidados (universo 285)

| Dimensão | Nº | % de 285 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 22,8% |
| Núcleo entregue + ensaio externo pendente | **104** | 36,5% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,6% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,1% |
| **Total executadas** | **285** | 100% |

## R4. Notas do adendo

- A manutenção desta rodada **mudou o placar M2: +2 classificações, ambas na categoria B** (P4.6, ED-104); A, C e sem classificação inalterados. Universo 283 → **285**.
- **Executado 285/286 = 99,7%; na main 284/286 = 99,3%.** Falta só a execução da ED-103 (após o parecer, decisão 55) — todas as 286 tarefas têm decisão do dono (decisão 60, ED-104, 01:22 −03) e nenhuma está sem pasta: a ED-104 é a única aguardando push.
- **Decisões 58, 59 e 60 (`DECISOES-DO-DONO.md` 311 → 333 linhas):** **58 — Mobile começa pela persistência e integração (P-04+P-03)** — lote FECHADO E POUSADO (`b38ec85`, 15 ficheiros, TS 179/179, Java 12/12); **59 — controle em tempo real via GPS no campo** — frente P-MOB-GPS-01 FECHADA E POUSADA (`ecce890`, 23 ficheiros, TS 200/200, Java 29/29); **60 — ED-104: procura registada, executar a trilha GRS completa já** — produzida, aprovada e confirmada nesta janela (decisão 01:22 −03; estimativa antes de produzir, precedente ED-68).
- **CI total sobre o HEAD corrente (`cc2e376`) segue por provar.** Nenhum log novo de CI completa na VM nesta janela (último: `ci-vm/ci-completa-pos-cura-lote-2.log` sobre `e6f6b74`: core 1533/1533, security 48/48, fiscal FAILURE a compilar — defeito C4/P-P46-02, matéria da Rodada 18). O lote `correcao-ci-cura-lote-3` foi produzido (2 poms curados L3/L4; L1/L2/L5 e P-CURA3-01/02/03 por decidir) — a cura prescrita para a P-P46-02 é inexequível por ciclo Maven (ver o pacote); o fiscal continua a parar o reactor antes do platform.
- **Rodada 18 da auditoria concluída ~00:05 −03** (relatórios em `auditoria-continua/rodadas/2026-11-09-2343/`); Rodada 19 ainda sem pasta aberta. Marca d'água: HEAD `1d755053`.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- As 100 da pré-retomada **nunca são presumidas de aceite pleno**.
- Nota de frescura da foto: o HEAD vivo é `cc2e376` (2026-11-10T04:49:13Z = 01:49:13 −03); as pastas locais moveram-se durante a rodada — REVISAO/CONFIRMACAO/portão da ED-104 e os lotes mobile/GPS aterraram antes da foto do GitHub. As contagens de executado usam o estado local às ~02:17 −03 (P4.6 e ED-104 aprovadas; ED-103 só com o dossiê); a posição na main é a da foto 01:49:13 −03. **A fila de pouso das 286 tarefas tem 1 (ED-104)**; a CI total sobre o HEAD corrente segue por provar.

## S. Adendo de 10/11/2026 — Manutenção da rodada 04:05 (job ziipsan-push-2h) — 1 tarefa nova executada (ED-103, execução da mecânica, decisão 62); 286/286 executado e na main

- **Manutenção (job):** `ziipsan-push-2h`, rodada 04:05 de 10/11/2026. Leitura viva própria do GitHub na mesma rodada (conector GitHub, `list_commits`, sem 429).
- **Âmbito:** nenhuma linha das secções anteriores (§§1–9, adendos A–R) foi alterada. O adendo R continua a ser o registo fiel da foto das 02:05 de 10/11; a posição corrente é a declarada neste adendo.

## S1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 285 | este registro, §R1 |
| Executadas (universo deste registro) | **286** | +1 nesta rodada: ED-103 (execução da mecânica, decisão 62) — com `entrega/` + revisão aprovada + confirmação independente (ver abaixo); lista de produzidas aprovadas `contagem_aprovadas_0405.json` (186 tarefas) |
| **Classificadas neste adendo** | **1** | ED-103 — **categoria B** (evidência lida abaixo); 0 reclassificações |
| **Universo consolidado** | **286** | 285 + 1 ✓ |

### Nova classificação — evidência lida dos artefactos da tarefa

| Tarefa | Categoria | Evidência lida |
|---|---|---|
| ED-103 | **B** | `RELATORIO-EXECUCAO-ED-103.md`, pendências — todas de factor externo: **P-ED103-INT-01** (fiação/adapter real da porta `ReceivablesPortfolioSource` sobre os recebíveis vivos de billing/collection); **P-ED103-INT-02** (leiautes oficiais de banco/fundo/registradora `[SEM FONTE]` — formatos genéricos declarados até fonte oficial do cliente); **P-ED103-INT-03** (aplicação do SQL pendente em PostgreSQL descartável + numeração Flyway global no pouso/integração); **P-ED103-INT-04** (persistência real do `CessionStore` JDBC/JPA sobre o SQL de E6 + fiação Spring/REST/agendamento); **P-ED103-TELAS-01** (telas da mecânica — decisão 57, só após CI total verde). Núcleo entregue e provado: suíte nova **20/20**; regressão do módulo `ziipsan-collection` 785 testes (765 + 20) com o conjunto de falhas **idêntico** ao da base (4F/7E pré-existentes) — zero falhas novas. Cadeia: estimativa prévia do coordenador → produção → REVISAO-EXECUCAO **APROVADO** (0 graves, 0 moderados, 3 menores não-bloqueantes) → CONFIRMACAO-EXECUCAO **CONFIRMADO** (18/18 + dossiê 4/4) → portão **APROVADO EM SUBSTÂNCIA** → **pouso `b4601a8`** (18 ficheiros) + diário `376bf6f`. O dossiê de consulta **não pousou por desenho** (decisão 62): fica como instrumento do cliente, fora do lote. |

### Não classificadas nesta rodada — declarado, com a razão

- **Nenhuma executada sem classificação.** As 100 da pré-retomada seguem sem classificação por relatório local (nunca presumidas de aceite pleno). A ressalva M4 da ED-08 (seção 7) segue valendo.
- **Fora do universo — não classificadas, por desenho:** os 8 itens da Onda II do PDCA-01 (II-1..II-8, decisões 61/64) estão **FECHADOS** na esteira (8/8, relatório `onda-2/RELATORIO-ONDA-2.md`) mas **não são tarefas do plano v3.0** (backlog pós-CI, fora das 286) — não entram neste registro nem no placar de 286. Os lotes do momento final do front (decisões 63/65) e as correções da cura/R19 também são fora do universo.

### Posição na main — 0 aguardando push; 286/286

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **186 de 186** | +2 nesta rodada: ED-104 (`1c7ac4f` + `f21fb4b` + `9224daa` + diário `1583284` + fontes DejaVu `aff827d` pela rota do PC do dono) e ED-103 (`b4601a8` + diário `376bf6f`) |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **286 de 286** | 100 + 186 = 286 |
| **Aguardando push** | **0** | era 1 (ED-104) — **fila das 286 tarefas VAZIA; executado = na main = 286/286 pela primeira vez** |
| **Total na main (%)** | **100,0%** | 286/286 = 100,0% |

## S2. Verificação por leitura viva própria do GitHub

1. Leitura viva desta manutenção (conector GitHub, `list_commits`, sem 429): **1.031 commits, HEAD `80eef16`** (`front(temas): 9 temas no código real — 5 paletas novas (violet, amber, executive-navy, graphite, sanitation-green) + gerados + script inline/CSP + companheiros KNOWN_PALETTES (UserPreferencesService) e MARCA-E-TEMAS §4.2, lote único de 13 (decisões 63/65)`, 2026-11-10T07:05:06Z = 04:05:06 −03). Continuidade SHA a SHA contra a foto 02:05 (`commits_0205-raw.json`, 1.019 commits): **0 ausentes, 12 novos**.
2. Os 12 commits novos, em ordem cronológica (UTC): `1c7ac4f` (05:12:26Z) ED-104 parte 1/4 → `f21fb4b` (05:12:32Z) ED-104 parte 2/4 → `8ce3b98` (05:12:36Z) fix(ci) cura lote 3 parcial pousável (L4: billing declara ziipsan-accounting) → `9224daa` (05:14:47Z) ED-104 parte 3/4 (testes, 114/114) → `1583284` (05:15:21Z) diário da ED-104 → `aff827d` (05:30:12Z) ED-104 fontes DejaVu (rota PC do dono) → `8adee2d` (05:43:22Z) fix(ci) cura lote 4 (P-CURA3-03) → `b4601a8` (06:24:04Z) ED-103 execução (decisão 62) → `376bf6f` (06:25:29Z) diário da ED-103 → `c0d1c6a` (07:02:56Z) front(tema) biblioteca do molde (decisão 65) → `20eb153` (07:04:26Z) diário do tema → `80eef16` (07:05:06Z) front(temas) 9 temas no código real (decisões 63/65, HEAD). Cadeia linear, sem ramos nem reescrita.
3. Recálculo próprio (script `contar-foto.py` sobre a lista viva, `foto-resultado-0405.json`, confirmado por segunda contagem independente com `contar.py` sobre a lista achatada — **divergência explicada, não erro:** o `contar.py` conta 284 na main porque o seu padrão de parênteses exige hífen e os assuntos de pouso da P4.6 (`feat(p4.6):`) e da LC.15 (`feat(lc.15):`) usam ponto; os dois commits estão verificados na lista viva acima e o `contar-foto.py`, que normaliza o ponto, conta-os — 186/186 na retomada, 286/286 na main): retomada na main **186** = ED 98 + P3 41 + P2 22 + P4 6 + GL 4 + LC 15; total na main **286 de 286**; aguardando push entre as executadas **0**; não iniciadas **0**. Nota de método: o mecânico (`contar-foto.py`) conta 186 pastas com `entrega/` filtradas ao universo; o aprovado é 186 — a ED-103 conta agora pela execução aprovada (o dossiê, só por si, continuaria de fora).
4. Nenhum commit novo carrega ID de tarefa do plano além dos 2 acima (ED-103, ED-104); os dois commits-sonda (`7b19662`, `044b4a5`) continuam fora do padrão, sem efeito na contagem.
5. Pacotes novos/modificados na janela fora do universo de 286 e sem efeito neste registro, excepto o registo: `onda-2/` (II-1..II-8 FECHADOS, 8/8, zero escritas no GitHub — pousos aguardam ordem da sessão principal), `correcao-ci-cura-lote-3-r19a` (R19-A PRODUZIDO — L1/L2/L5 curadas em desenho; PORTAO-REGISTRO em branco: sem revisão formal, sem confirmação, sem portão — aguarda a esteira), `correcao-ci-cura-lote-4` (P-CURA3-03 curada; pousado como `8adee2d`; registry 415/415), `correcoes-r19-b` (R19-B, adendos documentais), `correcao-ci-cura-lote-3` (L4 pousável pousada como `8ce3b98`; o resto do lote 3 é substituído pelo R19-A), `tema-front-web`/`ed-40-telas`/`portal-consumidor-telas`/`provas-temas` (momento final do front, decisões 63/65; biblioteca do molde + 9 temas pousados como `c0d1c6a` + `80eef16`).

## S3. Totais consolidados (universo 286)

| Dimensão | Nº | % de 286 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 22,7% |
| Núcleo entregue + ensaio externo pendente | **105** | 36,7% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,6% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,0% |
| **Total executadas** | **286** | 100% |

### Por fase

| Fase | Executadas no universo | A completo | B ensaio externo | C não externo | Sem classificação |
|---|---:|---:|---:|---:|---:|
| P0 | 47 | 0 | 0 | 0 | 47 |
| P1 | 41 | 0 | 0 | 0 | 41 |
| P2 | 34 | 11 | 7 | 4 | 12 |
| P3 | 41 | 23 | 12 | 6 | 0 |
| P4 | 6 | 4 | 2 | 0 | 0 |
| GL | 4 | 1 | 3 | 0 | 0 |
| LC | 15 | 0 | 15 | 0 | 0 |
| ED | 98 | 26 | 66 | 6 | 0 |
| **Total** | **286** | **65** | **105** | **16** | **100** |

## S4. Notas do adendo

- A manutenção desta rodada **mudou o placar M2: +1 classificação, na categoria B** (ED-103); A, C e sem classificação inalterados. Universo 285 → **286**.
- **Executado 286/286 = 100,0%; na main 286/286 = 100,0%.** Todas as 286 tarefas do plano v3.0 estão executadas e na main; nenhuma aguarda push; nenhuma está por iniciar. O que resta do plano é prova externa (CI total verde, ensaios reais, pareceres, actos com cliente/piloto) — não execução de tarefas.
- **Decisões 61–65 (`DECISOES-DO-DONO.md` 333 → 365 linhas):** **61 — Onda II APROVADA COMPLETA** como backlog pós-CI verde; **62 — ED-103 executa-se JÁ, só a mecânica** (redefine o gatilho da decisão 55; a logística é do cliente); **63 — todos os modelos como temas; padrão Azul Água** (torneira 3D só guardada como asset, sem ir para as telas); **64 — Onda II LIBERADA PARA EXECUÇÃO JÁ** (cai a condição pós-CI da decisão 61); **65 — momento final do front LIBERADO JÁ, COMPLETO** (cai a condição pós-CI da decisão 57; telas da Onda II entram neste momento).
- **Rodada 19 da auditoria FECHADA ~01:43 −03** (consolidado `00-CONSOLIDADO-RODADA19.md`, marca d'água HEAD `cc2e376`): delta APROVADO em substância, **1 grave (R19-C2 — quatro lesões irmãs da mesma fornada, cada uma capaz de travar o reactor sozinha depois do fiscal), 2 moderados (R19-C1, R19-C3), 7 menores**; repasse em 2 lotes (**R19-A** — redesenho do lote 3 da cura; **R19-B** — adendos documentais, produzido). **C4-01 FECHADA** (decisão 49) — fora do repasse. P-R18-01 (verificação pós-pouso por hash dos 17 pousos da onda da Rodada 18) segue aberta, prioritária para a Rodada 20.
- **CI total sobre o HEAD corrente (`80eef16`) segue por provar.** Nenhum log novo de CI completa na VM nesta janela (último: `ci-vm/ci-completa-pos-cura-lote-2.log` sobre `e6f6b74`: core 1533/1533, security 48/48, fiscal FAILURE a compilar — defeito C4/P-P46-02). A varredura `ci-vm/varredura-testcompile-pos-cura.log` (cópia arquivada pelo R18-B, md5 `f88786c7…` conferido) mostra BUILD SUCCESS na superfície, mas **4 módulos com "Nothing to compile"** (core, security, fiscal, platform — classes obsoletas) e **nenhum HEAD registado** — não é citada como prova; a Rodada 19 (R19-C3) já decidiu que uma varredura assim não prova a cura. O R19-A (L1/L2/L5) fecha a varredura limpa em 19/20 (só o MTR falha, pré-existente, ED-104 — o lote 4 confirma MTR BUILD SUCCESS no HEAD `1583284`), mas o lote **não tem revisão nem confirmação nem portão** e **não pousou**.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- As 100 da pré-retomada **nunca são presumidas de aceite pleno**.
- Nota de frescura da foto: o HEAD vivo é `80eef16` (2026-11-10T07:05:06Z = 04:05:06 −03); as pastas locais moveram-se durante a rodada — a execução da ED-103 e os lotes R19-A/B, o lote 4 e a Onda II aterraram antes da foto do GitHub. As contagens de executado usam o estado local às ~04:10 −03; a posição na main é a da foto 04:05:06 −03. **A fila de pouso das 286 tarefas está vazia (0 aguardando)**; a fila que segue é a de correcções (R14-A, R15-A/B, R16-A, R18-A/B, R19-A/B, V104, MM-05), a Onda II (8/8, aguarda ordem de pouso) e o momento final do front.

## T. Adendo de 10/11/2026 — Manutenção da rodada 06:05 (job ziipsan-push-2h) — nenhuma tarefa nova executada; 286/286 sustentado; R19-A pousado

- **Manutenção (job):** `ziipsan-push-2h`, rodada 06:05 de 10/11/2026. Leitura viva própria do GitHub na mesma rodada (conector GitHub, `list_commits`, sem 429).
- **Âmbito:** nenhuma linha das secções anteriores (§§1–9, adendos A–S) foi alterada. O adendo S continua a ser o registo fiel da foto das 04:05 de 10/11; a posição corrente é a declarada neste adendo.

## T1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 286 | este registro, §S1 |
| Executadas (universo deste registro) | **286** | conjunto **idêntico** ao do adendo S — verificado: `foto-resultado-0605.json` vs `foto-resultado-0405.json`, mesma lista de 186 tarefas da retomada; 0 pastas novas de tarefa com `entrega/` |
| **Classificadas neste adendo** | **0** | nenhuma executada sem classificação; 0 reclassificações |
| **Universo consolidado** | **286** | 286 + 0 ✓ |

### Não classificadas nesta rodada — declarado, com a razão

- **Nenhuma executada sem classificação.** As 100 da pré-retomada seguem sem classificação por relatório local (nunca presumidas de aceite pleno). A ressalva M4 da ED-08 (seção 7) segue valendo.
- **Fora do universo — não classificadas, por desenho:** os 8 itens da Onda II do PDCA-01 (II-1..II-8, decisões 61/64) **POUSARAM** nesta janela (16 commits, 131/131 por hash, Rodada 21) mas **não são tarefas do plano v3.0** — não entram neste registro nem no placar de 286. Os 5 pacotes do momento final do front (decisões 63/65) pousaram integralmente (52/52, Rodada 21), e o R19-A pousou (96/96 + 18 remoções) — também fora do universo.

### Posição na main — 0 aguardando push; 286/286

| Parcela | Nº | Fonte |
|---|---:|---|
| Produzidas na retomada na main | **186 de 186** | inalterada |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **286 de 286** | 100 + 186 = 286 |
| **Aguardando push** | **0** | **fila das 286 tarefas VAZIA** |
| **Total na main (%)** | **100,0%** | 286/286 = 100,0% |

## T2. Verificação por leitura viva própria do GitHub

1. Leitura viva desta manutenção (conector GitHub, `list_commits`, sem 429): **1.079 commits, HEAD `d807534`** (`docs(diario): verbete do pouso do R19-A cura do ciclo fiscal/billing (saida 3) — entrega c2e87589..b54f548a + 18 remocoes; guarda 6 actualizada quanto ao MTR (R-C01); proxima prova: CI completa P-CURA-02`, 2026-11-10T09:03:51Z = 06:03:51 −03). Continuidade SHA a SHA contra a foto 04:05 (`commits_0405-raw.json`, 1.031 commits): **0 ausentes, 48 novos**.
2. Os 48 commits novos, em ordem cronológica (UTC): `9e7deca` (07:17:58Z) diário dos 9 temas → `7932223` (07:18:14Z) front portal → `5c4a1f9` (07:18:40Z) diário do portal → `d5f5a2f` (07:18:55Z) front quiosque/painel ED-40 → `c5991be` (07:19:12Z) diário do quiosque → `ec0f5b4` (07:19:43Z) front rotas GIS → `f75b009` (07:20:03Z) diário das rotas GIS (momento final do front COMPLETO, decisão 65) → `b4eee25` (07:22:31Z) II-1 → `55e9efe` (07:23:00Z) diário II-1 → `c136458` (07:23:35Z) II-2 → `534e26e` (07:23:52Z) diário II-2 → `4d411e2` (07:26:34Z) II-3 → `a548aee` (07:26:54Z) diário II-3 → `3dd6f61` (07:32:55Z) II-4 → `a5682b9` (07:33:12Z) diário II-4 → `193d9a4` (07:34:44Z) II-5 → `5910eed` (07:36:05Z) diário II-5 → `ed66fa9` (07:36:31Z) II-6 → `629e556` (07:47:36Z) diário II-6 → `a8b98aa` (08:00:06Z) II-7 → `41c6f89` (08:11:59Z) diário II-7 → `613bcc9` (08:17:35Z) II-8 → `0b341a4` (08:43:24Z) diário II-8 (Onda II COMPLETA, decisão 64) → `c2e8758` (08:47:37Z) R19-A parte 1/6 → `9f80292` (08:52:42Z) parte 2/6 → `ef36612` (08:53:19Z) parte 3/6 → `50f52df` (08:54:23Z) parte 4/6 → `63ae45d` (08:55:11Z) parte 5/6 → `b54f548` (08:55:24Z) parte 6/6 → `692615c` … `b1e9f82` (08:56:10Z–09:02:02Z) 18 remoções dos caminhos antigos → `d807534` (09:03:51Z) diário do R19-A (HEAD). Cadeia linear, sem ramos nem reescrita.
3. Recálculo próprio (script `contar-foto.py` sobre a lista viva, `foto-resultado-0605.json`, confirmado por segunda contagem independente com `contar.py` sobre a lista achatada — **divergência explicada, não erro:** o `contar.py` conta 284 na main porque o seu padrão de parênteses exige hífen e os assuntos de pouso da P4.6 (`feat(p4.6):`) e da LC.15 (`feat(lc.15):`) usam ponto; os dois commits estão verificados na lista viva acima e o `contar-foto.py`, que normaliza o ponto, conta-os — 186/186 na retomada, 286/286 na main): retomada na main **186** = ED 98 + P3 41 + P2 22 + P4 6 + GL 4 + LC 15; total na main **286 de 286**; aguardando push entre as executadas **0**; não iniciadas **0**.
4. Nenhum commit novo carrega ID de tarefa do plano; os dois commits-sonda (`7b19662`, `044b4a5`) continuam fora do padrão, sem efeito na contagem.
5. Pacotes novos/modificados na janela fora do universo de 286 e sem efeito neste registro, excepto o registo: `correcao-ci-cura-lote-3-r19a` (**R19-A FECHADO E POUSADO** — REVISAO **APROVADO COM RESSALVAS** → CONFIRMACAO **CONFIRMADO COM RESSALVAS** → portão **APROVADO EM SUBSTÂNCIA**; verificação pós-pouso `VERIFICACAO-POS-POUSO-R19-A.md`: **96/96** blobs da entrega, **0/18** caminhos antigos, diário em append puro; a nota da Rodada 21 sobre 15 remoções pendentes está **ultrapassada** — as 15 foram executadas a seguir ao fecho da rodada, cada uma com commit confirmado), `onda-2/` (II-1..II-8 pousados; `VERIFICACAO-POS-POUSO-II-4..II-8.md`), `conclusao-front/` (momento final; `RELATORIO-MOMENTO-FINAL-FRONT.md`), `correcoes-r20-a` (entrega + harness-logs, sem revisão), `correcoes-r20-b` (RELATORIO + entrega, sem revisão), `correcoes-r20-c` (REVISAO **APROVADO** e CONFIRMACAO **CONFIRMADO** no texto, mas portão **BLOQUEADO** — o extrator não extraiu o veredito da revisão e sinalizou não-independência na confirmação; lote **LOCAL-ONLY**, fora do universo), `correcoes-r21-a` (PLANO + RELATORIO + portão temas-9, em produção), `correcoes-r21-c` (vazia).

## T3. Totais consolidados (universo 286) — inalterados

| Dimensão | Nº | % de 286 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 22,7% |
| Núcleo entregue + ensaio externo pendente | **105** | 36,7% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,6% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,0% |
| **Total executadas** | **286** | 100% |

### Por fase — inalterada

| Fase | Executadas no universo | A completo | B ensaio externo | C não externo | Sem classificação |
|---|---:|---:|---:|---:|---:|
| P0 | 47 | 0 | 0 | 0 | 47 |
| P1 | 41 | 0 | 0 | 0 | 41 |
| P2 | 34 | 11 | 7 | 4 | 12 |
| P3 | 41 | 23 | 12 | 6 | 0 |
| P4 | 6 | 4 | 2 | 0 | 0 |
| GL | 4 | 1 | 3 | 0 | 0 |
| LC | 15 | 0 | 15 | 0 | 0 |
| ED | 98 | 26 | 66 | 6 | 0 |
| **Total** | **286** | **65** | **105** | **16** | **100** |

## T4. Notas do adendo

- A manutenção desta rodada **NÃO mudou o placar M2: 0 classificações novas**; A, B, C e sem classificação inalterados. Universo 286 → **286**.
- **Executado 286/286 = 100,0%; na main 286/286 = 100,0%.** Todas as 286 tarefas do plano v3.0 estão executadas e na main; nenhuma aguarda push; nenhuma está por iniciar. O que resta do plano é prova externa (CI total verde, ensaios reais, pareceres, actos com cliente/piloto) — não execução de tarefas.
- **Nenhuma decisão nova além da 65** (`DECISOES-DO-DONO.md` 365 linhas, inalterado nesta janela).
- **Rodada 20 da auditoria FECHADA ~05:00 −03** (consolidado `00-CONSOLIDADO-RODADA20.md`, marca d'água HEAD `376bf6f4`; HEAD vivo no fecho: `0b341a40`): delta APROVADO em substância, **0 graves, 0 moderados, 11 menores** (R20-B2..B5, R20-I1..I4, R20-F1/F2, R20-R1), repassados em 3 lotes (**R20-A/B/C**). **FECHADOS em conteúdo:** P-R18-01 (17 pousos verificados por hash), P-R17-B-02, R19-B1, R19-I1, R19-I2, R2-F1..F9. 286/286 SUSTENTADO (limite: sem recontagem integral tarefa a tarefa — esta manutenção faz a recontagem integral e confirma 286/286).
- **Rodada 21 da auditoria FECHADA ~06:10 −03** (consolidado `00-CONSOLIDADO-RODADA21.md`, marca d'água HEAD `f7b8b74c`): delta desde `376bf6f4` — momento final do front pousado integralmente (**52/52** por hash) e Onda II pousada integralmente (**131/131** por hash); R19-A a pousar no fecho (6/6 partes + 3/18 remoções — **completado depois do fecho**, ver §T2 item 5). **0 graves, 2 moderados (R21-F1, R21-C1), 3 menores novos** (R21-O1, R21-C2, R21-C3), repassados em 3 lotes (**R21-A/B/C**). **FECHADOS em conteúdo:** R20-B1, R20-B5, R20-R2. Persistem: R20-B2, R20-B3 (parcial), R20-B4, R20-F1/F2 (pousaram como estavam — errata pós-pouso), R20-R1.
- **CI total sobre o HEAD corrente (`d807534`) segue por provar.** O ficheiro `ci-vm/ci-completa-pos-r19a.log` é um stub de 2 linhas (`ERRO: psql nao encontrado`) — tentativa falhada por ambiente, **não é prova de CI**. O último CI completo válido segue o de `e6f6b74` (core 1533/1533, security 48/48, fiscal FAILURE a compilar — defeito C4/P-P46-02, agora curado em desenho pelo R19-A pousado). O rerun da CI completa pós-R19-A (**P-CURA-02/03**) está agora exequível (Rodada 21) e pendente.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- As 100 da pré-retomada **nunca são presumidas de aceite pleno**.
- Nota de frescura da foto: o HEAD vivo é `d807534` (2026-11-10T09:03:51Z = 06:03:51 −03); as pastas locais moveram-se durante a rodada — os lotes R20-A/B/C e R21-A/C aterraram antes da foto do GitHub. As contagens de executado usam o estado local às ~06:10 −03; a posição na main é a da foto 06:03:51 −03. **A fila de pouso das 286 tarefas está vazia (0 aguardando)**; a fila que segue é a de correcções (R20-A/B/C, R21-A/B/C, R14-A, R15-A/B, R16-A, R18-A/B, R19-B, V104, MM-05), a prova INV-C2-11 sobre a base 12k (decisão 49) e o rerun da CI completa pós-R19-A (P-CURA-02/03).

## T5. Correcção ao §T2 item 5 — R20-C: portão APROVADO EM SUBSTÂNCIA (10/11/2026, durante a rodada 06:05)

- O §T2 item 5 deste adendo descreveu o R20-C como "portão BLOQUEADO" com base só no `portao-saida.txt` (exit 1, 09:01:42Z). O `PORTAO-REGISTRO-R20-C.md` (09:11:51Z, durante esta rodada) regista o veredito em substância: **APROVADO EM SUBSTÂNCIA** — as 4 falhas mecânicas são falsos positivos demonstrados do extractor (FP-1 instância **(xxi)** — veredito repartido em duas linhas; FP-2 instância **(i)** — "mesma sessão" casa uma negação que afirma a independência; FP-3 instância **(ix)** — forma `Papel (sessão):`). O lote R20-C está **FECHADO na esteira**, LOCAL-ONLY, zero escritas no GitHub. A descrição do §T2 item 5 fica corrigida por esta nota; nenhuma outra linha deste adendo muda.

## U. Adendo de 10/11/2026 — Foto da posição na main com front completo + Onda II + R19-A (lote R21-C da Rodada 21) — correcção do S4

- **Manutenção (lote):** `R21-C` da Rodada 21 da auditoria contínua (achados R21-O1, R21-C2, R21-C3; consolidado `00-CONSOLIDADO-RODADA21.md`). Produtor: sessão `78dc185d-f698-47cf-bf79-68abfd0baad6`. Leitura viva própria no clone `~/workspace/ziipsan-clone` (`git fetch origin`, `git log`/`ls-tree`), HEAD vivo `d8075343` (1.079 commits).
- **Âmbito:** nenhuma linha das secções anteriores (§§1–9, adendos A–S) foi alterada. O adendo S continua a ser o registo fiel da foto das 04:05 de 10/11/2026; a posição corrente é a declarada neste adendo.

## U1. Reconciliação

| Parcela | Nº | Fonte |
|---|---:|---|
| Universo consolidado no adendo anterior | 286 | este registro, §S3 |
| Executadas (universo deste registro) | **286** | nenhuma tarefa nova executada nesta janela |
| **Classificadas neste adendo** | **0** | nenhuma reclassificação |
| **Universo consolidado** | **286** | 286 + 0 ✓ |

### Fora do universo — incluídos na foto da posição na main, sem entrar no placar de 286

- **Momento final do front (decisões 63/65) — POUSADO COMPLETO, 52/52:** 5 pacotes + 5 verbetes, cadeia linear `376bf6f4→f75b009d` (10 commits, 0 merges): tema do front web 18 (`c0d1c6a` + verbete `20eb153`), 9 temas no código real 13 (`80eef16` + verbete `9e7deca6`), portal do consumidor 6 (`79322234` + verbete `5c4a1f97`), quiosque/painel ED-40 12 (`d5f5a2f0` + verbete `c5991bec`), rotas GIS 3 (`ec0f5b45` + verbete `f75b009d`). Contagens por commit conferidas por `diff-tree` no clone vivo (18+13+6+12+3 = 52).
- **Onda II do PDCA-01 (decisões 61/64) — POUSADA COMPLETA, 131/131:** 8 pacotes + 8 verbetes, cadeia linear `f75b009d→0b341a40` (16 commits, 0 merges): II-1 9 (`b4eee251`), II-2 22 (`c1364589`), II-3 19 (`4d411e27`), II-4 11 (`3dd6f612`), II-5 16 (`193d9a4f`), II-6 10 (`ed66fa9a`), II-7 27 (`a8b98aa9`), II-8 17 (`613bcc97`), cada um com o seu verbete (`55e9efe7`, `534e26e9`, `a548aeed`, `a5682b9c`, `5910eed2`, `629e556e`, `41c6f895`, `0b341a40`). Contagens por commit conferidas por `diff-tree` no clone vivo (9+22+19+11+16+10+27+17 = 131).
- **R19-A (cura do ciclo fiscal/billing, saída 3) — POUSADO COMPLETO:** 6/6 partes + 18/18 remoções + verbete de diário, cadeia linear `0b341a40→d8075343` (25 commits, 0 merges): partes `c2e87589` (21) + `9f802921` (22) + `ef366121` (25, incl. L5) + `50f52df3` (13) + `63ae45df` (13) + `b54f548a` (2) = 96 ficheiros; 18 remoções (`692615c4`…`b1e9f821`, uma por caminho antigo); verbete `d8075343` (06:03:51 −03, append puro +4/−0; diário vivo 2.244 linhas = 2.240 após a Onda II + 4).

### Posição na main — 0 aguardando push; 286/286; front + Onda II + R19-A pousados

| Parcela | Nº | Fonte |
|---|---:|---:|
| Produzidas na retomada na main | **186 de 186** | inalterada desde o adendo S |
| Pré-retomada na main | 100 | inalterada |
| **Total na main** | **286 de 286** | 100 + 186 = 286 |
| **Aguardando push** | **0** | fila das 286 tarefas continua VAZIA |
| **Total na main (%)** | **100,0%** | 286/286 = 100,0% |
| Front completo na main | **52/52** | 5 pacotes + 5 verbetes, `376bf6f4→f75b009d` |
| Onda II na main | **131/131** | 8 pacotes + 8 verbetes, `f75b009d→0b341a40` |
| R19-A na main | **6/6 + 18/18 + verbete** | `0b341a40→d8075343` (25 commits) |

## U2. Verificação por leitura viva própria (clone, após `fetch`)

1. HEAD vivo `d8075343bb6ce9c49b95a8d567fe1ead71b217b2` (`docs(diario): verbete do pouso do R19-A…`, 2026-11-10 06:03:51 −03). Total **1.079 commits**. Continuidade contra a foto do adendo S (HEAD `80eef16`): **48 commits novos, 0 merges** — 7 restantes do front (`9e7deca6`…`f75b009d`) + 16 da Onda II + 25 do R19-A = 48 ✓, cadeia linear sem ramos nem reescrita.
2. **R19-A — remoções verificadas por `ls-tree` no HEAD vivo:** os 18 caminhos antigos dos movimentos (17 tipos do billing + `PortalMunicipalContextTest` no platform) têm **0 ocorrências**; os caminhos novos existem (`billing-rules/.../composition/BillComposition.java` presente; `PortalMunicipalContextTest` presente uma única vez, em `commercial/ziipsan-registry/.../portal/selfcare/`). **0/18 caminhos antigos presentes.**
3. **R19-A — verbete de diário existe:** commit `d8075343`, só `docs/DIARIO-DE-BORDO.md`, +4/−0, com o texto do verbete (96 ficheiros +301/−205; L1/L2/L5; guarda 6 actualizada quanto ao MTR, R-C01; próxima prova: CI completa, P-CURA-02). A observação O-R21-02 da Rodada 21 ("R19-A sem verbete até `f7b8b74c`") fica **ultrapassada**: o verbete pousou depois do fecho da rodada.
4. **R19-A — cadeia completa (esteira):** produção `66e70ea5…` → revisão **APROVADO COM RESSALVAS** (`25f9086e…`, `REVISAO-R19-A.md:22`, 07:13:39Z) → confirmação **CONFIRMADO COM RESSALVAS** (`7e101af6…`, `CONFIRMACAO-R19-A.md:23`, 07:32:25Z) → portão **APROVADO EM SUBSTÂNCIA** (operador `ba736828…`, corrida 2026-11-10T07:34:01Z, exit 1 só pela Regra 3 — falso positivo demonstrado, instância (xxiii), `PORTAO-REGISTRO-R19-A.md` §1; `portao-saida.txt`). O desvio do "lote único" está registado no acto do pouso (`ADENDO-POUSO-R19-A.md` §2: 6 commits de conteúdo + 18 de remoção + verbete; o conector não transporta remoções em `push_files`).
5. **Alcance desta verificação (honestidade de método):** o que este produtor verificou vivo foi a cadeia (commits, pais, linearidade), as contagens de ficheiros por commit (`diff-tree`), a ausência dos 18 caminhos antigos (`ls-tree`) e a existência do verbete. Os placares por hash ficheiro a ficheiro — 52/52 do front, 131/131 da Onda II e 96/96 do R19-A — são as recomputações independentes dos revisores 1 e 2 da Rodada 21 e do coordenador de pouso do R19-A (`VERIFICACAO-POS-POUSO-R19-A.md`, sessão `e31482be…`, 09:04), citadas aqui, não reexecutadas por este produtor. A verificação pós-pouso por hash do R19-A pela mesa de auditoria continua a ser prioridade da Rodada 22 (consolidado da Rodada 21, §7).

## U3. Totais consolidados (universo 286) — inalterados

| Dimensão | Nº | % de 286 |
|---|---:|---:|
| Aceite completo (com relatório local) | **65** | 22,7% |
| Núcleo entregue + ensaio externo pendente | **105** | 36,7% |
| Núcleo + pendência declarada não externa (categoria C) | **16** | 5,6% |
| Sem classificação por relatório local (pré-retomada) | **100** | 35,0% |
| **Total executadas** | **286** | 100% |

### Por fase — inalterada (adendo S)

| Fase | Executadas no universo | A completo | B ensaio externo | C não externo | Sem classificação |
|---|---:|---:|---:|---:|---:|
| P0 | 47 | 0 | 0 | 0 | 47 |
| P1 | 41 | 0 | 0 | 0 | 41 |
| P2 | 34 | 11 | 7 | 4 | 12 |
| P3 | 41 | 23 | 12 | 6 | 0 |
| P4 | 6 | 4 | 2 | 0 | 0 |
| GL | 4 | 1 | 3 | 0 | 0 |
| LC | 15 | 0 | 15 | 0 | 0 |
| ED | 98 | 26 | 66 | 6 | 0 |
| **Total** | **286** | **65** | **105** | **16** | **100** |

## U4. Notas do adendo

- **Correcção expressa do S4:** a afirmação do adendo S de que o R19-A "não tem revisão nem confirmação nem portão e não pousou" **é agora falsa e fica corrigida por este adendo**: o R19-A tem revisão formal (APROVADO COM RESSALVAS, `25f9086e…`), confirmação independente (CONFIRMADO COM RESSALVAS, `7e101af6…`), portão APROVADO EM SUBSTÂNCIA (exit 1 só pela Regra 3, falso positivo (xxiii) demonstrado) e **pousou por completo** (6/6 partes + 18/18 remoções + verbete, HEAD `d8075343`).
- **A manutenção desta rodada não mudou o placar M2.** Universo 286; A/B/C/sem classificação inalterados. O front (52), a Onda II (131) e o R19-A (96 + 18 remoções) são fora do universo das 286, como o adendo S já declarava — entram nesta foto só como posição na main.
- **Executado 286/286 = 100,0%; na main 286/286 = 100,0%.** Todas as 286 tarefas do plano v3.0 estão executadas e na main; nenhuma aguarda push; nenhuma está por iniciar. O que resta do plano é prova externa (CI total verde, ensaios reais, pareceres, actos com cliente/piloto) — não execução de tarefas.
- **CI total sobre o HEAD corrente (`d8075343`) segue por provar.** O último log de CI completa continua a ser `ci-vm/ci-completa-pos-cura-lote-2.log` (02:58Z, sobre `e6f6b74`). Uma tentativa de rerun pós-R19-A foi registada em `ci-vm/ci-completa-pos-r19a.log` (09:05 UTC, 72 bytes) e **abortou antes de começar** — `ERRO: psql nao encontrado` — falha ambiental, não do código; P-CURA-02 e P-CURA-03 seguem abertas e o rerun continua por fazer.
- **Decisões:** nenhuma decisão nova além da 65 (`DECISOES-DO-DONO.md` termina na decisão 65, conferido pela Rodada 21).
- **Rodada 21 da auditoria FECHADA ~06:10 −03** (consolidado `00-CONSOLIDADO-RODADA21.md`, marca d'água HEAD `f7b8b74c`): 0 graves, 2 moderados (R21-F1, R21-C1), 3 menores novos (R21-O1, R21-C2, R21-C3), repassados em 3 lotes (R21-A/B/C). Este adendo é a correcção R21-C2. O R21-C1 (15 remoções restantes no fecho da rodada) fica **ultrapassado pelo pouso**: as 18 remoções estão na main (ver U2.2) e o desvio do lote único está registado no `ADENDO-POUSO-R19-A.md` §2.
- A ressalva **M4 da ED-08 (seção 7) segue valendo**, inalterada.
- As 100 da pré-retomada **nunca são presumidas de aceite pleno**.
- Nota de frescura da foto: o HEAD vivo é `d8075343` (2026-11-10T06:03:51 −03 = 09:03:51 UTC); a leitura viva foi feita por este produtor às ~06:10 −03. As contagens de executado usam o estado local às ~06:10 −03; a posição na main é a da foto 06:03:51 −03. **A fila de pouso das 286 tarefas está vazia (0 aguardando)**; a fila que segue é a de correcções (R21-A/B/C) e a prova da CI total pós-R19-A.

---

## Adendo R20-I1 — 10/11/2026 (lote R20-B, Rodada 20 da auditoria contínua)

**Produtor:** sessão `7ee2654d-dce4-4a01-bea7-cfabd20200b7` (lote R20-B, local-only; **NÃO POUSA** nesta execução).

**O que é este ficheiro:** cópia integral congelada, em 10/11/2026, da versão corrente de `correcoes-rg-lote3/REGISTRO-PLACAR-2D.md` (1.663 linhas no acto do congelamento). Esta cópia **substitui**, no caminho `correcoes-rg-lote3/REGISTRO-PLACAR-2D.md`, o blob pousado `deb09d3596a1a8831dd4d0770fe3cc18efec0e4cb90358b526fe810514ba0a67` (1.445 linhas, pousado no commit `b251d3d9`, lote R12-B) quando pousar num próximo lote que toque o registo. O blob pousado é o estado correcto ao pouso; o que mudou depois dele é manutenção posterior do registo vivo (adendos da rotina de 2h e a correcção in place da linha 1439, registada abaixo).

**A correcção da linha 1439 (§P4, Lote 2 da cura) — as duas frases, verbatim:**

- Frase pousada (blob `deb09d35…`, linha 1439): "renumerar a V1_11 da P2.28 para V103 (precedente R2-G1)".
- Frase local corrente (linha 1439 desta cópia): "renumerar a V1_11 da P2.28 para V105 (a V103 foi ocupada pela chave de remessa, `451c151`; a V104 está reservada ao contract) (precedente R2-G1)".

Verificação própria do produtor: a linha 1439 do blob e a linha 1439 do ficheiro local diferem **só** nesse excerto (diff por SequenceMatcher: inserção de "5 (a V103 foi ocupada pela chave de remessa, `451c151`; a V104 está reservada ao contract)" sobre "V103"); o resto da linha é byte-idêntico nos dois lados. A renumeração correcta é **V105** — a V103 foi ocupada pela chave de remessa (`451c151`) e a V104 está reservada ao contract da V103; a frase pousada ("para V103") está desactualizada/errada e é corrigida por este adendo, sem reescrever o original pousado.

**Aviso de instantâneo:** esta cópia congelada é um instantâneo. A manutenção posterior da rotina de 2h pode acrescentar-lhe linhas (o ficheiro vivo cresce por appends) e supersedê-la antes do pouso; nesse caso o pouso **re-congela** a versão corrente no acto, e este adendo viaja com a versão então corrente, não com esta cópia. O SHA-256 e a contagem de linhas desta cópia estão registados no `LANDING.md` do lote R20-B.

---

## V. Adendo de 10/11/2026 — Estado real da CI completa pós-R19-A + fila real de correcções (lote R22-B da Rodada 22)

**Produtor:** sessão `21cb412a-dd07-4a18-bf3f-47fe35c0168e` (lote R22-B, local-only; **NÃO POUSA** nesta execução — viaja num futuro lote transportador, padrão R20-B).
**Censo de letras verificado antes de escrever:** última letra de adendo em uso é **U** (linha 1742); `grep -c "^## V\."` = 0 — **V livre**. Os adendos nomeados sem letra (R18-C3-01, R20-I1) não consomem letras.
**Base:** cópia em disco sincronizada com o blob pousado `f99ba1d4…` (1.834 linhas, commit `32904d11`) pelo mesmo lote (R22-4A-1); este adendo é append puro sobre essa base.

## V1. Estado real da CI completa pós-R19-A — correcção dos adendos T e U (R22-U-01)

Os adendos T (§T4) e U (§U4) afirmam que o `ci-vm/ci-completa-pos-r19a.log` é um stub/aborto (72 bytes, `ERRO: psql nao encontrado`) e que "o rerun continua por fazer". **Isso já não é verdade — e era verdade no instante em que foram escritos.** O adendo U foi escrito às ~06:10 −03, quando o log ainda era o stub da tentativa abortada; a corrida completa sobrepôs-se depois (ficheiro com mtime 2026-11-10 09:26 UTC). Corrige-se aqui, sem reescrever T ou U:

- **O ficheiro actual é a corrida completa:** 423.707 bytes, HEAD testado `d8075343bb6ce9c49b95a8d567fe1ead71b217b2` (linha 1 do log), fim 2026-11-10T09:26:24Z (log linha 3568), duração total 05:14 min.
- **0 ocorrências** de "psql nao encontrado" no ficheiro actual (conferido por `grep -c`).
- **Placares verificados por leitura própria do log integral** (linhas de total por módulo; Reactor Summary, log linhas 3542–3568):

| Módulo | Placar | Estado |
|---|---:|---|
| core | 1.573/1.573 (log:824) | SUCCESS |
| security | 48/48 (log:894) | SUCCESS |
| billing-rules | 31/31 (log:959) | SUCCESS |
| fiscal | 228/228 (log:1097) | SUCCESS |
| platform | 1.374 corridos, 15 falhas (log:3539) | FAILURE |
| restantes 14 (Quality, Losses, Metering, Accounting, Integration, Reporting, GIS, MTR, LGPD, Registry, Billing, Asset, Collection, App) | — | SKIPPED |

- O reactor pára no platform (BUILD FAILURE, log:3565). O fiscal compila agora — a saída 3 do R19-A resolveu a aresta fiscal→billing. Ressalva: billing/registry/accounting ficaram SKIPPED — a compilação deles não foi provada por esta CI, só pela varredura test-compile do R19-A.
- **Persistem:** P-CURA-02 / P-CURA2-07 (prova do provisionamento — `TenantProvisioningServiceTest` 16 corridos, 13 em falha, log:3166, causa: migração duplicada V4_7, R22-2A-1, lote R22-A); P-CURA-03 (ITs GIS — o failsafe nunca executou; o reactor parou no platform); P-CURA2-06 (`VerticalBoundaryTest` 2/2 em falha, log:3344 — decisão de arquitectura do dono pendente).
- **Fresquura desta foto:** a posição na main deste adendo é o HEAD `cb244605` (2 commits de documentação depois de `d8075343`); o placar 286/286, front 52/52, Onda II 131/131 e R19-A mantêm-se (ver adendo U).

## V2. Fila real de correcções — correcção do §U4 (R22-4A-2)

O §U4 resume a fila para "a fila que segue é a de correcções (R21-A/B/C)". **A fila real é mais comprida, e nenhum dos itens mais antigos pousou.** Conferência própria (lote R22-B): `git log 1d755053..cb244605 --format='%h %s'` (71 commits) filtrado por `r14|r15|r16|r18|r19-b|r20-a|r20-c|v104|mm-05` devolve **vazio**; o único "r20-b" no intervalo é o próprio `32904d11` (o registo). Entre o adendo T e o adendo U não houve nenhum pouso desses itens (só os 2 commits de documentação R20-B). A fila completa, herdada do §T4, é:

| Item | Estado nesta data |
|---|---|
| R14-A | Local-only; sem pouso |
| R15-A / R15-B | Local-only; sem pouso |
| R16-A | Local-only; sem pouso |
| R18-A / R18-B | Local-only; sem pouso |
| R19-B | Local-only; sem pouso |
| R20-A | Local-only por desenho; instalação local executada (9/9); sem pouso |
| R20-B | **Pousado em parte** — só I1+I2 (`32904d11` + `cb244605`); I3/I4/R1 locais por desenho |
| R20-C | Local-only por desenho; sem pouso |
| R21-A | Local-only por desenho; corrigido documentalmente por este lote R22-B (errata R22-3A-1 + `portao-saida.txt` R22-3A-2); sem pouso |
| R21-B | **Sem pacote local** — executado directamente em 15 commits `fix(ci)` + verbete `d8075343` (O-R22-03); sem re-pouso |
| R21-C | Local-only por desenho; sem pouso |
| V104 | Reservada ao contract da V103; sem commit — não pousa |
| MM-05 | Bloqueado (P-MM05-R8C-01); sem pouso |
| Prova INV-C2-11 sobre a base 12k (decisão 49) | **EXECUTADA** 10/11/2026 ~00:37 −03 (11.300/11.300 perfis, 0 divergências; revisão APROVADO COM RESSALVAS, confirmação CONFIRMADO COM RESSALVAS) — não pousada por desenho |
| Rerun da CI completa pós-R19-A (P-CURA-02/03) | **EXECUTADO** — ver §V1 acima (log completo, 09:26:24Z) |

A omissão da prova INV-C2-11 no §U4 fica suprida por esta tabela. A fila de pouso das 286 tarefas continua vazia (0 aguardando) — a fila acima é de **correcções e provas**, não de tarefas do plano.
