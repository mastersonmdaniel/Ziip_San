# ED-79 — Sistemas, PSA, relatório anual e Carta de Serviços: integração

Pacote `br.com.ziipsan.core.watersystems` (módulo `shared/ziipsan-core`),
núcleo puro Java — persistência JDBC/JPA, controllers, telas e a fiação
das portas são da integração, como nas ondas anteriores.

## 1. O que o núcleo faz

- **Sistemas (SAA/SES):** `WaterSystem` (componentes via
  `SystemComponent`, população atendida com data de referência,
  manancial, tratamento) + `SystemRegistryService` (duplicidade de
  código; componente só com tipo existente na P1.27 e feição existente
  na ED-53; tipo desativado continua válido, com flag — regra da P1.27).
- **PSA:** `WaterSafetyPlan`/`WaterSafetyPlanService`. Perigo = vínculo
  (`HazardLink`) a um `RiskRecord` REAL da P3.31 (GES-20), com medida
  de controle e ação corretiva declaradas pelo tenant e, opcional, a
  chave do parâmetro de monitoramento da ED-50. `PlanCoverage` expõe,
  sem presumir: cobre incidente operacional (risco OPERATIONAL, com os
  `occurrenceId` da P2.25 que a P3.31 guarda)? cobre resiliência
  climática (CLIMATE_RESILIENCE)? Aprovação exige ≥ 1 vínculo.
- **Relatório anual (Decreto 5.440/2005, art. 3º, III):**
  `AnnualQualityReport` por ligação (`consumerUnitId`) +
  `AnnualReportBuilder`. Agrega os fechamentos da ED-50
  (`MonthlyClosing.ParameterLine` real), transporta as linhas do bloco
  da P2.24 (formato `BillQualityBlockSource.BillBlockLine` da ED-50,
  sem remontar o bloco) e as limpezas da ED-89
  (`ChemReports.PublicCleaningRecord` real, só com laudo). Mês sem
  fechamento = `monthsMissing` nomeado e relatório DRAFT; READY só
  com os 12 meses. % de conformidade só sobre vereditos determinados
  (regra da ED-50); indeterminado contado à parte.
- **Carta de Serviços (Lei 13.460/2017):** `ServiceCharter`/
  `ServiceCharterService`. Conteúdo é dado do tenant; entrada sem
  requisitos/etapas/canais/prazo declarado é incompleta e trava a
  publicação, nomeando o serviço. Publicação só pela porta da P3.20,
  com a referência gravada; nova versão publicada substitui a anterior.

## 2. Fronteira dura — motor de mensagens legais (P2.32)

A ED-79 **não tem** motor, catálogo nem texto de mensagem legal.
`AnnualReportIssuanceService.issue` segue exatamente esta ordem:

1. relatório precisa estar READY;
2. pede a emissão à porta `LegalMessagePort` (adaptador da integração
   → `BillContentService`/`LegalMessageEngine` da P2.32) e exige a
   **prova de emissão** devolvida; sem prova, recusa com
   `LEGAL_EMISSION_PROOF_MISSING` e **não toca na entrega** (provado
   por teste com contador de entregas = 0);
3. pede a entrega à porta `NotificationPort` (adaptador → P3.5);
   ISSUED só com a tentativa da P3.5; se a P3.5 não despacha, o
   registro guarda prova + motivo nomeado e o relatório segue READY
   para nova tentativa.

As mensagens legais **da fatura** (bloco mensal de qualidade etc.)
continuam emitidas pela P2.32 no ciclo da conta (P2.24/P2.8): a chave
real do bloco é `legal.water_quality_block` (catálogo v1 da P2.32) e
a ED-79 não emite mensagem de fatura por nenhum caminho próprio.

**Pendência P-ED79-04:** o catálogo v1 da P2.32 **não contém** chave
para o relatório anual. A chave `legal.annual_water_quality_report`
está marcada `[PROPOSTA]` em `SystemsPorts` — registrá-la (versionada,
por norma) é ato da dona do catálogo (P2.32) na integração. Até lá, o
motor da P2.32 recusa a chave desconhecida (`LegalMessageCatalog.byKey`)
e a ED-79 não emite — comportamento seguro e declarado, não simulado.

## 3. Portas e fiação (pendências nomeadas)

| Porta | Dono real | Fiação na integração |
|---|---|---|
| `FactoryCatalogPort` | P1.27 (GIS-02) | Ler `R__gis_factory_catalog.sql` do tenant (tipo existe/ativo). A P1.27 não tem pacote local executável — porta modelada pela spec. |
| `GisFeaturePort` | ED-53 (GIS-09) | Conferir a feição no cadastro GIS do tenant. `gis_feature_id` sem FK no SQL (inter-módulo) — conferir na integração. |
| `RiskRegistryPort` | P3.31 (GES-20) | Devolver o `RiskRecord` do tenant (mesmo módulo core; leitura de `cert_risks` da V21_3). |
| `LegalMessagePort` | P2.32 (CAD-96, RAS-04) | Adaptador para `BillContentService.emissionProof` persistindo a prova na V4_10 dela; registrar a chave proposta do relatório anual no catálogo dela. |
| `NotificationPort` | P3.5 | Adaptador para `NotificationDispatchService` (outbox P1.9 + modelo P2.30); finalidade `ANNUAL_QUALITY_REPORT` a registrar no catálogo de finalidades da P1.3. Canais SMS/WhatsApp/push seguem fronteira da ED-41 na P3.5. |
| `PortalPublicationPort` | P3.20 | Publicar a versão da Carta no portal do consumidor e devolver a referência (versão publicada tem de casar com a da Carta). |

## 4. SQL

`db/pendente/ED-79__water_systems_psa_annual_charter.sql` — 14 tabelas
`ws_*`, sem número Flyway (atribuir o próximo slot livre do core na
integração), sem nenhum dado semeado. FK composta do PSA para
`cert_risks (tenant_id, id)` da P3.31 (V21_3, já na main) é real.
`consumer_unit_id` é referência ao cadastro (sem FK inter-módulo,
padrão das ED-50/ED-53). Prova real em PG descartável: ver relatório.

---

## 5. ADENDO — 08/11/2026 (correção de código ED-79-CORR-2, achados D1–D5 da revisão)

**Corretor: sessão ED-79-CORR-2 — agente de correção de código.**
Este adendo não reescreve as seções acima; registra o que a correção
dos achados D1–D5 da `REVISAO-ED-79.md` mudou no núcleo e no SQL:

- **D1 (selagem):** `AnnualQualityReport`, `IssuanceRecord`,
  `LegalEmissionProof`, `ServiceCharter` e `WaterSafetyPlan.Plan`
  viraram classes finais seladas (construtor privado, padrão M-1 da
  ED-50). Nascimento/transição de estados terminais só pelas vias de
  pacote dos serviços. O serviço de emissão confere o vínculo da
  prova com o pedido (`LEGAL_EMISSION_PROOF_MISMATCH`). Na fiação,
  o adaptador da P2.32 (P-ED79-03) constrói a prova exclusivamente
  pela fábrica de pacote `LegalEmissionProof.fromAdapter` — a
  classe-ponte do adaptador deve viver no pacote
  `br.com.ziipsan.core.watersystems` (ou a integração abre a fábrica
  com revisão própria); nenhum outro caminho de nascimento existe.
- **D1 (SQL):** `legal_basis` com CHECK de igualdade; constraint
  triggers diferidas `trg_ws_reports_terminal_guard` e
  `trg_ws_issuances_terminal_guard` (READY exige 12 meses presentes;
  ISSUED exige emissão ISSUED correspondente, e vice-versa). A
  autenticidade de `legal_proof_ref`/`portal_ref` não é verificável
  no SQL — declaração formal na P-ED79-03 (ver relatório de correção
  de código).
- **D2:** `CleaningLine` carrega `supplySystem` da ED-89; o builder
  recusa limpeza de outro sistema, sem sistema identificado ou
  duplicada. `AnnualReportBuilder.buildFromClosings` +
  `MonthlyQualitySummary.fromClosing` recebem os `MonthlyClosing`
  reais da ED-50 e recusam fechamento não-ENCERRADO e de outro
  sistema. A decisão nº 4 do produtor (limpeza ausente não trava)
  permanece.
- **D3:** o LOS `servico/conjunto` está MARCADO `[A DEFINIR]` como
  13º item do `Ed79Coverage` (status PENDENTE), conciliado com a
  pendência **P-ED79-10** já registrada pela correção documental
  ED-79-CORR-1 — nenhum valor de LOS foi inventado.
- **D4:** `SystemRegistryService.registerSystem` recebe a
  `FactoryCatalogPort` e confere `TreatmentInfo.catalogTypeKeys`
  contra a P1.27 (`TREATMENT_TYPE_UNKNOWN`). Concilia com a
  **P-ED79-09** da correção documental: a parte "conferência das
  chaves" está FECHADA em código; a "guarda de completude de
  componentes por tipo" segue pendência (nenhuma fonte define os
  componentes exigidos por tipo).
