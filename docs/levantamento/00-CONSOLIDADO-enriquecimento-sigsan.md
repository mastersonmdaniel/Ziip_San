# Consolidado — o que o GSAN e o GEOSAN ensinam ao SIGSAN

Data: 11/10/2026. Base: 6 relatórios desta pasta (`01` a `06`), cruzados com o `DIARIO-DE-BORDA.md` (09/10/2026).
Convenção: **[verificado]** = conferi no código do SIGSAN; **[agente]** = afirmado por um relatório, não conferido por mim.

> **Atualização (12/10/2026, fim do dia):** vários defeitos de código citados aqui já foram corrigidos (RLS e dono do banco, tenant pelo token, auditoria sem dado pessoal, cifra de CPF/CNPJ no cadastro e no MTR, JSON, MTR com testes, aplicação executável). O estado atual está no diário, seção 18; a ordem de prioridade, em `docs/PLANO-EXECUCAO-PDCA-R0-R9.md`.

---

## 1. Resumo em 10 linhas

1. **GSAN** é o sistema comercial (cadastro, leitura, faturamento, arrecadação, cobrança, OS). Stack de 2008 (Struts 1.1, EJB 2, JBoss 4), com cerca de 8.600 arquivos Java em `src/gcom` e uma `Fachada` de 40 mil linhas. Vale como **fonte de regras de domínio**, não de código.
2. **GEOSAN** é o módulo GIS (VB6 + TerraLib + banco Oracle/Postgres/SQL Server). Vale como **fonte de requisitos GIS e de regras de qualidade de dados**.
3. O SIGSAN hoje tem fundação multi-tenant sólida (RLS, provisionamento), mas **nenhum domínio comercial**: `commercial/*` e `operations/*` não têm POM.
4. Os 6 levantamentos convergem numa ordem: **cadastro → medição/consumo → tarifa → conta → ciclo mensal → perdas reais → arrecadação/cobrança**. O balanço hídrico real do `LossesService` só existe depois de billing e metering.
5. Os levantamentos acharam **defeitos estruturais no SIGSAN** que o build verde não pega (seção 2). Eles vêm antes de qualquer feature.
6. **Licença:** o GSAN não tem `LICENSE` no repositório (provável GPL, não confirmada); o GEOSAN é GPL-2.0-only. Regra: **clean-room**. Conceitos, estados, fórmulas e regras entram; código, SQL, `.jrxml`, layouts e nomes de coluna não.
7. O pacote de maior valor imediato são os **~90 casos de teste de cálculo** do relatório 02 e os 30+ do relatório 03: viram critério de aceite, na linha do que o diário já cobra.
8. O GSAN **não cobre** PIX, NPC/DDA, CNAB 444 nem SNIS/ANA/ARSAE. O SIGSAN pode entrar nessas lacunas como diferencial.
9. Há **risco regulatório** concentrado em negativação SPC/Serasa (CDC, LGPD) e em tarifa social (dado socioeconômico sensível).
10. O relatório 05 propõe esquema PostGIS por tenant com nó-trecho explícito; o 06 mostra que o escritor `.inp` atual gera arquivo inválido.

---

## 2. Defeitos no SIGSAN que os levantamentos revelaram (P0 absoluto)

Nenhum quebra o build. Todos quebram em runtime ou em produção.

| # | Defeito | Onde | Status |
|---|---|---|---|
| D1 | `BaseEntity` sem `@Id` e sem `@Version`; nenhum arquivo Java do repositório tem `@Id`, e há 25 classes `@Entity`. O Hibernate deve recusar subir ("No identifier specified"). Há também um `;;` na linha 32 | `shared/sigsan-core/.../BaseEntity.java` | **[verificado]** o código; o efeito em runtime é inferência (nenhum teste sobe contexto JPA) |
| D2 | A migration de tenant não cria as colunas `version` e `tenant_schema` que `BaseEntity`/`TenantAwareEntity` mapeiam | `platform/.../tenant/V1_1__create_tenant_schema.sql` | **[verificado]** (nenhuma ocorrência) |
| D3 | `ENABLE ROW LEVEL SECURITY.` com ponto final em vez de `;` | `intelligence/sigsan-gis/.../V1__create_gis_schema.sql:228` | **[verificado]** |
| D4 | Quatro FKs para `dma_district(tenant_id, id)`, tabela criada em outro módulo (`sigsan-losses`) e sem `UNIQUE (tenant_id, id)` | mesmo arquivo, linhas 57, 96, 197, 232 | FK **[verificado]**; falta do UNIQUE **[agente]** |
| D5 | Colunas duplicadas em `hydraulic_indicators` | mesmo arquivo | **[agente]** |
| D6 | As migrations de GIS, losses, quality, MTR e LGPD ficam em `src/main/resources/db/migration` dos próprios módulos, mas o provisionamento de tenant só lê `platform/.../db/migration/tenant`. Esses esquemas nunca são criados num tenant. É a mesma família do bug de "tenant vazio" do diário (seção 5) | `TenantProvisioningService` | **[agente]**, coerente com o diário |
| D7 | `LossesService`: ILI divide a perda real do **período** (m³) pelo UARL em **m³/dia**; só vale para período de 1 dia. `Lp = 5` fixo e `Lm`/`Nc`/`P` com valores-padrão inventados quando faltam dados; perda real negativa é zerada em silêncio | `LossesService.java:77-80, 141-158` | **[verificado]** |
| D8 | Escritor `.inp` do EPANET gera arquivo inválido: nós dos pipes vazios, `[PUMPS]` fora da sintaxe, `[OPTIONS]` com palavras de `[TIMES]`, diâmetro em metros, números com vírgula no locale pt-BR, sem reservatórios, sem `[END]` | `sigsan-gis` | **[agente]** |

**Recomendação:** abrir uma fase 0 com teste de contexto JPA contra o PostgreSQL do compose, teste de provisionamento que afirme a existência das tabelas de **todos** os módulos, e testes de valor conhecido do balanço IWA. É a aplicação direta da regra do diário: "build verde não prova que o código está correto".

---

## 3. Ordem de ataque proposta

> **Superado em 12/10/2026.** A ordem de prioridade canônica é a de `docs/PLANO-EXECUCAO-PDCA-R0-R9.md`. Esta seção fica como registro do raciocínio da época.


Cruza as recomendações dos 6 relatórios com as Fases A–D do diário. Uma trilha comercial e uma trilha GIS/perdas, que se encontram no balanço hídrico.

| Fase | Entrega | Origem | Depende de |
|---|---|---|---|
| **0** | Corrigir D1–D8; teste JPA e teste de provisionamento completo; `@SpringBootApplication` + primeira API REST | diário Fase B, relatórios 01/05/06 | — |
| **1** | **Cadastro**: cliente separado de usuário; vínculo cliente-imóvel com vigência; hierarquia territorial (localidade, setor, quadra, rota); inscrição `LLL.SSS.QQQ.LLLL.SSS` e matrícula com DV módulo 11; categoria/economias; situação de ligação água/esgoto como catálogo com flags; ligações; LGPD no cadastro (campos cifrados, índice cego do documento) | 01 | 0 |
| **2** | **Medição e consumo**: hidrômetro, instalação histórica, leitura, anormalidades; `ConsumptionCalculator` sem I/O (cascata do relatório 02) | 02 | 1 |
| **3** | **Tarifa e conta**: motor de tarifa com vigência e faixas; conta com estados; idempotência por (unidade, referência); crédito/débito a realizar; retenção de impostos | 02 | 2 |
| **4** | **Ciclo mensal**: grupo de faturamento, cronograma, jobs por rota idempotentes e reiniciáveis, multi-tenant (Spring Batch + Quartz) | 02, 03 | 3 |
| **5** | **Perdas reais**: balanço IWA alimentado por consumo faturado por DMA e produção; corrigir unidade do UARL; testes com valores calculados à mão | 04, 05, 06 | 3 e zonas da trilha GIS |
| **6** | **Arrecadação e cobrança**: código de barras com fator de vencimento atual, classificação de pagamentos (14 estados), acréscimos por impontualidade, retorno CNAB/FEBRABAN, PIX, parcelamento, ações de cobrança | 03 | 3 |
| **7** | **Operacional**: OS com tipos de serviço configuráveis e efeito comercial ao encerrar; RA/protocolo com SLA; permissões granulares; auditoria por campo | 04 | 1 |
| **GIS** (paralela, depois da fase 0) | Nó-trecho explícito, tabelas de domínio, ramal/ligação ligados ao comercial, zonas/DMC, qualidade de dados persistente, traçado de manobra, mapa web (MapLibre), escritor `.inp` correto, solver | 05, 06 | 0; liga ao comercial na fase 1 |

---

## 4. Os achados que mais mudam decisões

### 4.1 Domínio comercial (GSAN)
- **O centro é o imóvel/ligação**, não o cliente. Cliente e imóvel se ligam por vínculo com vigência (proprietário, usuário, responsável). No SIGSAN, `consumer_units.owner_id` e `responsible_id` apontam para `users`, o que é conceitualmente errado e deve mudar antes de existir dado.
- **Leitura, consumo faturado e conta são tabelas separadas** (`medicao_historico`, `consumo_historico`, conta). Isso permite auditar cada cobrança. Gravar **tipo** e **anormalidade** de consumo em cada registro.
- **Esgoto tem dois percentuais:** um reduz o volume e outro multiplica o valor. O fator é arredondado a 2 casas.
- **Tolerâncias e arredondamento são regra, não detalhe.** O GSAN usa R$ 0,03 e R$ 2,00 fixos no código; no SIGSAN devem ser parâmetros por tenant, com `RoundingMode` explícito por regra (o GSAN mistura `HALF_UP` e `HALF_DOWN`).
- **Variantes por concessionária viram configuração, não subclasse.** 42 dos 73 `*SEJB` do GSAN são duplicados por empresa. No SIGSAN: tabela `barcode_layout` por tenant e pontos de extensão (`TenantPolicy`).
- **Fator de vencimento do boleto:** o GSAN usa a base de 1997, que quebra para vencimentos a partir de 22/02/2025. Implementar o ciclo novo e testar com vetores do manual FEBRABAN.
- **Crédito a realizar nunca excede a conta;** o resíduo passa ao mês seguinte. Em impostos retidos, o último absorve o resíduo de arredondamento.

### 4.2 Operação e segurança (GSAN)
- **O encerramento da OS tem efeito comercial** (corte, religação, supressão, troca de hidrômetro). Modelar como máquina de estados com eventos.
- **Segurança do GSAN** é a melhor referência de autorização: grupo × funcionalidade × operação, restrição negativa por usuário, abrangência em 5 níveis, cerca de 100 "permissões especiais" (alçadas e segregação de funções) e auditoria por coluna com valor antes/depois. **Não migrar** SHA-1 sem sal nem SSO por cookie.
- **A pasta `gcom/operacional` é só topologia** (sistema, zona, setor, distrito, zona de pressão) mais `producao_agua` por localidade e mês. É a entrada que falta ao `LossesService`.

### 4.3 GIS e perdas (GEOSAN)
- **Topologia por chave, não por geometria:** trechos apontam para nós por `INITIALCOMPONENT/FINALCOMPONENT`. O SIGSAN tem uma tabela genérica sem ligações; precisa de `gis_node` + `gis_pipe` com `from/to`.
- **Integração GSAN↔GEOSAN** era feita por 3 views chaveadas pelo número da ligação (`NRO_LIGACAO`) com a inscrição em `CLASSIFICACAO_FISCAL`. No SIGSAN: contrato de interface com matrícula e inscrição.
- **Demanda do EPANET:** L/s = m³/mês × 1000 / 30 / 86400, com o consumo do ramal dividido meio a meio entre os nós do trecho. Cotas vêm de MDT com interpolação bilinear; o GEOSAN grava cota 0 em silêncio quando falha, e o SIGSAN deve **dar erro**.
- **Balanço IWA com dado real:** consumo faturado por DMC = soma das ligações do DMC; `Lm` vem de `ST_Length` dos trechos do DMC.
- **Qualidade de dados:** o `ValidaBase` do GEOSAN tem 13 verificações, mas corrige de forma destrutiva e só em SQL Server. O relatório 05 propõe 26 regras (Q-01…Q-26) **sem correção automática**, com tabela persistente de pendências.
- **Solver:** porta `HydraulicSolver` com sidecar epanet-js (MIT, motor 2.2) e WNTR (BSD) para análises. O port Java da Baseform é GPL-3 e baseado no EPANET 2.0, então fica descartado.

---

## 5. O que NÃO absorver

| Item | Motivo |
|---|---|
| Struts 1.1, EJB 2/MDB, applets, JSP, JBoss 4 | Obsolescência |
| Tarefa Java serializada em `bytea`; motor batch que roda uma funcionalidade por vez com polling; "cancela tudo" no boot | Quebra a cada mudança de classe; frágil |
| IDs fixos no código (`Subcategoria.RESIDENCIAL_R1 = 1`), flags 1/2, linhas sentinela, coordenada em String, foto em `bytea` | Dívida técnica |
| PII (CPF, nome, endereço) em query string de URL para o banco (`formatarLinkBB`) | Proibido pela LGPD |
| Convênio bancário fixo no código | Quebra multi-tenant |
| `BigDecimal(double)` (média 105 dá 1073, não 1074), juros por mês de calendário | Erro de arredondamento |
| Bug de `subtrairNumeroDiasDeUmaData` (resultado descartado pelo chamador) | Carência de dias nunca recua |
| TerraLib, EAV generalizado para atributos comuns, SQL cru em "tema" e consulta, correção destrutiva automática, criptografia reversível de senha, credencial na base | GEOSAN |
| `api/` do GSAN | Não é API: é cliente de um serviço de relatórios externo, com risco de path traversal. Aproveitar só a ideia de relatório declarativo (metadados + dados → PDF/XLSX assíncrono) |

---

## 6. Licença e higiene (importante)

- **GSAN:** sem `LICENSE`/`COPYING`; o README cita o Portal do Software Público Brasileiro. Provável GPL, **não confirmada**. **GEOSAN:** GPL-2.0-only, sem `LICENSE` na raiz.
- Copiar código, `.hbm`, `.jrxml`, SQL, layouts, textos de tela ou nomes de coluna `abcd_*` traria obrigação de copyleft sobre o SIGSAN.
- **Regra proposta:** tratar as duas bases como referência de domínio (clean-room), manter um registro de quem leu e de quem implementou, usar nomenclatura própria em inglês e implementar por TDD a partir das seções de casos de teste. Layouts FEBRABAN/CNAB devem vir do **manual oficial** dos bancos, não do código do GSAN.
- **Pedir parecer jurídico** sobre a licença exata antes de qualquer reuso além de conceitos.
- **Segredos:** o relatório 06 achou senhas em texto claro nos docs e `.ini` do GEOSAN. Considerar comprometidas e **nunca copiá-las** para o SIGSAN. Os comentários do GSAN citam nomes de analistas e desenvolvedores, que são dado pessoal de terceiros.
- **Dados de teste:** os bancos `db/*/banco_geosan.backup` e os scripts de recadastramento podem ter dados reais; só usar anonimizados e com licença verificada.

---

## 7. Limites do levantamento

- Os agentes não leram linha a linha os controladores do GSAN (50 a 60 mil linhas), só o essencial por amostragem. Cada relatório declara suas lacunas (ex.: regra das parcelas bônus; layout posicional campo a campo dos arquivos bancários).
- Sem o DDL do banco do GSAN (vive em `gsan-migracoes`, fora desta pasta): tipos exatos, unicidades e FKs não foram vistos.
- Os catálogos "semente" (`faturamento_atividade`, `leitura_anormalidade`) não estão no repositório.
- Os diagramas `.vsd` e os `.chm` do GEOSAN não puderam ser lidos.
- O `ls` do Drive virtual omitiu pacotes que existem; um agente achou `seguranca` só por Glob direto. Algum pacote pode ter ficado de fora dos outros relatórios.
- Busca por indicadores SNIS/ARSAE no GSAN ficou **inconclusiva**.
- Os casos de teste foram calculados à mão pelos agentes a partir do código; cada um deve ser reconferido antes de virar teste de aceite.

---

## 8. Perguntas que dependem do Daniel

1. `collection` = arrecadação e `dunning` = cobrança, como assumiu o relatório 03?
2. Quais bancos e convênios os primeiros clientes usam? Isso define os parsers CNAB iniciais e se PIX entra no MVP.
3. A regra de **parcelas bônus** existe nos clientes-alvo? Precisa ser levantada com quem opera o GSAN.
4. O primeiro cliente fecha a contabilidade em sistema externo? Em qual formato (TXT, API, SPED)?
5. Quem no jurídico valida prazos de prescrição, notificação prévia de negativação e limites de multa e juros antes de codificar defaults?
6. Criar um módulo novo `commercial/sigsan-registry` para cadastro (o relatório 01 propõe) ou encaixar em `billing`?
7. O importador GSAN→SIGSAN (ETL) é meta comercial? Seria um diferencial para quem migra, mas exige o DDL do GSAN e trata dados pessoais reais.

---

## 9. Mapa dos relatórios

| Arquivo | Conteúdo | Destaque |
|---|---|---|
| `01-gsan-visao-geral-e-cadastro.md` | Stack, módulos, cadastro, recadastramento | Modelo de dados proposto com diagrama ER |
| `02-gsan-leitura-consumo-faturamento.md` | Leitura, cascata de consumo, tarifa, conta | ~90 casos de teste calculados à mão |
| `03-gsan-arrecadacao-cobranca-batch.md` | Pagamentos, cobrança, parcelamento, contábil, batch, relatórios | Matriz regulatória; mapeamento para Spring Batch + Quartz com RLS |
| `04-gsan-operacional-atendimento-seguranca-api.md` | OS, RA, segurança, gerencial | Backlog de 24 épicos; mapa de perfis |
| `05-geosan-banco-e-modelo-de-rede.md` | 132 tabelas, componentes, ramal/ligação, ValidaBase | Esquema PostGIS por tenant; 26 regras de qualidade |
| `06-geosan-funcionalidades-epanet-docs.md` | Catálogo GIS, exportação EPANET, arquitetura VB6 | Algoritmo `.inp`; lacunas do escritor atual; opções de solver |
