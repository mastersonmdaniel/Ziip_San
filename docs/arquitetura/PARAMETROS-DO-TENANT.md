# Parâmetros do tenant

Data: 12/10/2026. Origem: ADR-0002, ADR-0015, plano `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` §2.3, relatórios
`docs/levantamento/08`, `10`, `11`, `13`, `14`, `17` e o código. Fases (ADR-0001): P1 tarifa social (R3), P2 ciclo
(R4), P3 arrecadação, corte e portal (R5a/R5b, R7), P4 cobrança e Pix Automático (R8, R5c) e, na trilha de editais,
emissão em campo e almoxarifado (ADR-0031, ADR-0032); GIS no P1 a P3 (ADR-0034; plano v2.11).

> **Não é parecer jurídico.** Os limites vêm dos relatórios do projeto, com as ressalvas deles ("NÃO CONFIRMADO", fonte
> secundária). Limite do sistema é trava mínima, não validação da escolha do cliente.

Abreviações de caminho: `V1` = `shared/sigsan-core/src/main/resources/db/migration/core/V1__create_core_schema.sql`;
`V4_1` = `commercial/sigsan-billing/src/main/resources/db/migration/tenant/V4_1__create_billing_schema.sql`;
`V6_1` = `commercial/sigsan-collection/src/main/resources/db/migration/tenant/V6_1__create_collection_schema.sql`;
`V2_5` = `compliance/sigsan-lgpd/src/main/resources/db/migration/tenant/V2_5__create_lgpd_schema.sql`;
`V5_1` = `operations/sigsan-metering/src/main/resources/db/migration/tenant/V5_1__create_metering_schema.sql`.

---

## 1. Princípios

1. **Vigência.** `valid_from`/`valid_to` em todo parâmetro regulatório, sem sobreposição garantida por `EXCLUDE USING
   gist`, como em `tariff_versions` (`V4_1:65`).
2. **Versão gravada no resultado.** Conta, encargo, classificação social, liberação de OS de corte e expurgo gravam as
   versões de parâmetro usadas. Hoje só a tarifa faz isso (`bills.tariff_snapshot`, `V4_1:267`;
   `bill_items.tariff_version_id`, `V4_1:298`).
3. **Aprovação dupla e aceite formal.** Quem propõe não aprova. O aceite registra usuário, nome, cargo, data, hash do
   conteúdo e a **base normativa citada pelo cliente**. Suporte da plataforma em acesso assistido (ADR-0020) não
   propõe nem aprova.
4. **Sugestão marcada como sugestão**, com a fonte. Aceitá-la exige o mesmo aceite; divergir dela exige justificativa.
5. **Limite validado conforme a classe** (§2, ADR-0015). Valor fora do limite é recusado; dentro do limite, a escolha
   do prestador ou da agência fica aberta, com validação de tipo e coerência.
6. **Auditoria** de proposta, aprovação, recusa, simulação e ativação, sem dado pessoal.
7. **Efeito só futuro.** `valid_from` ≥ data da aprovação. Conta emitida não é recalculada; correção é retificação com
   a versão da competência.
8. **Simulação antes de ativar** para parâmetros de cálculo (simulador N4 do R3).
9. **Ausência é erro explícito** (plano §2.3).
10. **Bifurcação vira parâmetro** (ADR-0038). Onde lei, norma de agência ou prática legítima admitem caminhos
    alternativos, ou normas divergem, todos os caminhos são opções do parâmetro; o padrão de fábrica é o mais protetivo
    ao usuário; lei federal que proíbe expressamente fica travada (classe a ou c). Cada opção cita a norma de origem e
    tem teste de aceite.
11. **Norma voluntária liga e desliga** (ADR-0041). O sistema atende às capacidades comuns das normas de gestão desde o
    início; a exigência específica de cada norma só vale com a norma ligada pelo tenant, e as normas obrigatórias não
    se desligam. A lei vence qualquer norma voluntária.

---

## 2. Classes de parâmetro

Definidas no ADR-0015. Toda classe segue os princípios da seção 1 (vigência, aprovação dupla, aceite do conjunto).

| Classe | Regra | Exemplos |
|---|---|---|
| **a** — travado pelo sistema | valor ou procedimento fixo do produto ou da lei; o tenant não altera | vedação de corte por inadimplência; cifra de NIS e renda; eliminação ao fim do contrato |
| **b** — padrão de fábrica com aceite | o sistema traz o padrão; sair dele exige norma citada, aprovação dupla, aceite formal registrado e, quando previsto, alerta de risco; havendo limite de fábrica, valor fora dele é recusado | multa; abrangência das vedações de corte; janela de horário; MFA |
| **c** — só ampliável | piso fixo da lei ou do Ziip SAN; o tenant só altera no sentido mais protetivo (mais prazo, mais benefício, mais restrição). Onde a lei fixa teto, o tenant só reduz | tarifa social; retenção; aviso de corte; máscara de documento |
| **d** — livre | escolha do prestador ou da ERI, sem padrão de fábrica; só validação de tipo e coerência | ERI; convênios; prazos da ERI |

Na coluna **Cl.** das tabelas abaixo. Na classe b, a coluna **Sug.** é o padrão de fábrica; na classe c, a coluna
**Limite** é o piso (ou teto).

---

## 3. Catálogo por grupo

Colunas: **Sug.** = sugestão do produto; **Limite** = trava legal ou "livre"; **Cl.** = classe (§2); **Apr.** = área do
prestador que aprova (REG regulatório, JUR jurídico, FIN financeiro, DPO encarregado, OPE operação comercial; "—" =
travado, sem aprovação do tenant); **Fase**; **Código** = onde já existe ou "novo". Todas as linhas seguem os
princípios da seção 1.

### G1 — Agência, norma e perfil regulatório

| Chave | Descrição | Tipo | Sug. | Limite | Cl. | Apr. | Fase | Código |
|---|---|---|---|---|---|---|---|---|
| `reg.regulator` | ERI competente, ou "sem ERI" (titular, NR 11 art. 52 §3º) | enum do catálogo do produto | sem | livre | d | REG | P1 | novo |
| `reg.general_conditions_act` | norma de condições gerais vigente: número, data, URL | objeto | sem | obrigatório | d | REG | P1 | novo |
| `reg.ana_nr_adoption` | adesão da ERI às NR 11, 13 e 15 da ANA | mapa NR → status, data | sem | livre (fato declarado) | d | REG | P1 | novo |
| `reg.legal_nature` | privada, autarquia, empresa pública, SEM | enum | sem | livre; muda o regime LGPD (`10` d.1) | d | JUR | P1 | novo |
| `reg.service_area` | UF, municípios IBGE, serviços (água, esgoto) | lista | sem | livre | d | REG | P1 | novo (`tenant_catalog` só tem endereço, `V1:26`) |
| `reg.contract_pre_nr13` | contrato anterior à NR 13 (admite consumo mínimo) | bool | sem | NR 13 arts. 8º–9º | d | JUR | P2 | novo |
| `meter.average_months`, `meter.max_estimated_cycles`, `meter.after_cap_rule` | meses da média sem leitura, teto de ciclos estimados e regra depois do teto | int, int, enum | sem (ERIs: 5 a 12 meses; teto 3 ou 6) | ERI (NR 11 art. 35) | d | REG | P2 | entrada de `AveragePolicy` (`operations/sigsan-metering/src/main/java/br/com/sigsan/operations/metering/rules/AveragePolicy.java:17-21`); meses usados gravados em `V5_1:250`; teto novo |
| `bill.due_date_options` | dias de vencimento ofertados | lista | sem | ≥ 6 para concessionária (Lei 8.987 art. 7º-A); autarquia: a confirmar; só amplia | c | REG | P2 | entrada de `DueDateCalculator` |
| `bill.min_days_delivery_to_due` | antecedência entrega → vencimento, por categoria | int dias | sem | ERI (ex.: ARSAE-MG ≥ 10/≥ 5) | d | REG | P2 | novo |
| `bill.invoice_template` | modelo de fatura aprovado pela ERI | versão | sem | Lei 11.445 art. 39 p.u.; bloco do fornecedor travado (CDC art. 42-A) | d | REG | P2 | novo |
| `bill.tariff_readjust` | data-base, índice, ato, data de publicação | objeto | sem | vigência ≥ publicação + 30 dias, travada (NR 10, relatório `08` R-13) | d | REG | P2 | parcial: `regulatory_act`, `published_on` (`V4_1:53-54`) |

Tarifa, faixas e categorias continuam nas tabelas próprias do billing (`V4_1:19-140`), com o mesmo envelope de
aprovação e aceite.

### G2 — Tarifa social

Travado (classe a): a concessão não depende de adimplência (Lei 14.898; o motor já não recebe situação de pagamento,
`commercial/sigsan-billing/README.md`); BPC dá direito; uma economia por família; NIS e renda sempre cifrados, com
cada acesso registrado (como na busca por documento, ADR-0008), em qualquer configuração (ADR-0015).

Piso federal travado (classe c, ADR-0015): a agência ou o cliente só concede benefício maior (mais m³, desconto maior,
prazo maior), nunca menor. O piso só é ativado em produção depois de conferido no DOU, com a conferência registrada.

| Chave | Descrição | Tipo | Sug. | Limite | Cl. | Apr. | Fase | Código |
|---|---|---|---|---|---|---|---|---|
| `social.income_ratio_min_wage` | renda per capita máxima, em fração do salário mínimo | decimal | 0,5 | ≥ 0,5 (Lei 14.898); só amplia | c | REG | P1 | novo |
| `social.cadunico_max_age_months` | idade máxima da atualização do CadÚnico | int meses | 24 | ≥ 24 (Lei 14.898); só amplia | c | REG | P1 | novo |
| `social.local_categories` | categorias locais (ex.: Social I/II ARSAE-MG; Vulnerável ARSESP) | lista | sem | norma da ERI; não reduz o piso de `social.variable_bands` | d | REG | P1 | novo |
| `social.variable_bands` | faixas (de, até] e percentual de desconto | tabela | piso federal | piso travado: 50% até 15 m³ (Lei 14.898/NR 13; valor do relatório `08`, a conferir no DOU antes de ativar); só amplia faixa ou desconto | c | REG | P1 | `tariff_social_bands` (`V4_1:124-140`), sem validação do piso |
| `social.fixed_part_discount_pct` | desconto na parcela fixa | % | sem | só amplia; piso federal a confirmar na Lei 14.898/NR 13 e na norma da agência (G2) | c | REG+JUR | P1 | `tariff_versions.social_min_charge_discount_pct` (`V4_1:51`) |
| `social.sewer_discount` | desconto alcança o esgoto | bool | sem | só amplia (não desliga se a norma federal o exigir); a confirmar na Lei 14.898/NR 13 e na norma da agência (G2) | c | REG+JUR | P1 | entrada de `SewerParameters.applySocialDiscount` |
| `social.loss_notice_months` | aviso na fatura antes de retirar o benefício | int | 3 | ≥ 3 (Lei 14.898 arts. 2º §2º e 3º p.u.); só amplia | c | REG | P1 | novo |
| `social.grace_months` | permanência após deixar de ser elegível | int | 3 | ≥ 3 (relatório `08` R-14); só amplia | c | REG | P1 | novo (`GRACE_3M` no plano R3) |
| `social.family_tiebreak` | ordem de desempate da economia da família | lista ordenada | ordem do relatório `08` R-14 | a confirmar na NR 13 | d | REG | P1 | novo |
| `social.base_import` | origem (via ERI ou direta), layout, periodicidade | objeto | sem | ERI (ARSESP mensal; ARSAE-MG trimestral) | d | REG+DPO | P1 | novo (`V4_2`) |
| `social.legal_basis` | base legal do tratamento de renda e NIS; RIPD | enum + anexo | sem | G2; exige RIPD (`17` §7); cifra e registro de acesso travados | d | DPO+JUR | P1 | novo |
| `social.manual_request_sla_days` | prazo do pedido manual | int | sem | ERI (ARSAE-MG: 30 dias) | d | REG | P1 | novo |
| `social.annual_report` | destinatário, data, formato | objeto | sem | ERI (ADASA: até 20/jan) | d | REG | P1 | novo |

### G4 — Arrecadação

| Chave | Descrição | Tipo | Sug. | Limite | Cl. | Apr. | Fase | Código |
|---|---|---|---|---|---|---|---|---|
| `pay.channels` | arrecadação (código 8), boleto de cobrança, Pix Cobrança, Pix Automático | conjunto | arrecadação | só canais suportados pelo produto (§7) | d | FIN | P3 | novo |
| `pay.document_kind_by_debt` | documento por natureza do débito (mensal, acordo, entrada) | mapa | mensal = arrecadação; acordo = boleto (inferência do relatório `11`, achado 4) | livre | d | FIN | P3 | parcial: `document_kind` por convênio (`V6_1:30`) |
| `pay.agreements` | banco, convênio, layout do campo livre, versões A.09 aceitas, vigência | tabela | sem | estrutura FEBRABAN v08 validada | d | FIN | P3 | `collection_agreements` (`V6_1:23-49`) |
| `pay.febraban_company_code` | código de 4 posições, segmento 2 | texto | sem | obrigatório no segmento 2 (layout v08) | d | FIN | P3 | sem coluna própria (`V6_1:23-46`) |
| `pay.classify_tolerance`, `pay.adjust_tolerance`, `pay.duplicate_winner` | tolerâncias e vencedor da duplicidade | R$, enum | tolerâncias: sem; `pay.duplicate_winner`: `OLDEST_PAYMENT` (opções `OLDEST_PAYMENT`, `NEWEST_PAYMENT`; a quitação mais antiga protege contra acréscimo; 20c §5.2 e ARR-03, v2.15) | livre | d | FIN | P3 | `V6_1:213-215` |
| `pay.pix_provider` | PSP, chave Pix, referência da credencial no cofre, webhook | objeto | sem | Regulamento Pix (Res. BCB 1/2020); tenant do webhook vem do convênio | d | FIN | P3 | novo |
| `pay.hybrid_charge` | cobrança híbrida Pix + boleto | bool | desligado | facultativo, a partir de 01/02/2027 (Res. BCB 587/2026) | d | FIN | P3 | novo |
| `pay.pix_automatic` | habilitado, antecedência da instrução, valor máximo | objeto | desligado | débito em conta com recebedor PJ ou entidade não autorizada pelo BCB só por Pix Automático desde 13/10/2025 (Res. CMN 5.251/2025; verificado em 22/10/2026: para convênios de prestação de serviços públicos, a adequação de contratos e autorizações foi prorrogada para 01/01/2027 pela Res. CMN 5.278/2025, de 24/12/2025, que alterou o art. 2º-A, §2º, da Res. CMN 4.790/2020; o enquadramento da água e esgoto como convênio de serviço público exige análise jurídica; a data de 01/07/2027 é outra regra, a vigência do novo regime geral do débito automático, Res. BCB 565/2026) | d | FIN | P4 | novo |

Travado (classe a): ajuste de conciliação com aprovação dupla (plano R5).

### G5 — Multa, juros e atualização

| Chave | Descrição | Tipo | Sug. | Limite | Cl. | Apr. | Fase | Código |
|---|---|---|---|---|---|---|---|---|
| `fee.fine_pct` | multa de mora, percentual único | % | 2,00, padrão de fábrica (convergência de 10 ERIs, `08` d.2) | até 2,00 com o aceite comum; acima de 2,00 exige norma citada, aprovação dupla, aceite formal registrado e exibe alerta de risco (CDC art. 52 §1º; aplicação a tarifa: G5, pergunta E-02); teto absoluto: a definir (P0) | b | JUR | P2 | `V6_1:202-204`, CHECK `V6_1:222-224` e `FineRule` (`commercial/sigsan-collection/src/main/java/br/com/sigsan/commercial/collection/surcharge/FineRule.java:27-31`) aceitam teto acima de 2% só com texto de justificativa (`fine_cap_justification`), sem aceite; `LateFeePolicy` (billing) sem trava; unificar (ADR-0015) |
| `fee.interest_mode`, `fee.interest_monthly_pct`, `fee.interest_month_basis_days` | juros simples pro rata die | enum, %, int | `DAILY_RATE_WITH_CAP` com o teto da ERI (SC: 0,033%/dia; padrão mais protetivo, ADR-0038, v2.14); opções `DAILY_RATE_WITH_CAP` (`fee.interest_daily_pct`), `DAILY_RATE` sem teto, `MONTHLY_PRO_RATA` (1,00% a.m., base 30 ou 30,3) e `CALENDAR_MONTH` (20c §5.2, v2.15) | sem capitalização, travado (o produto só oferece juros simples); teto a confirmar | b (20c §5.2, v2.15; era d) | JUR | P2 | `V6_1:205-208` |
| `fee.correction_*` | modo, índice, meses mínimos, aceita negativa | enum, código | `fee.correction_mode` = `NONE`; opções `NONE`, `BY_INDEX` (Selic, INPC, IPCA ou índice do reajuste, conforme ERI) e `TARIFF` (correção pela tarifa; 20c §5.2, v2.15) | ERI ou contrato | d | JUR | P2 | `V6_1:209-212`; fatores em `surcharge_indexes` (`V6_1:173`) |
| `fee.grace_business_days`, `fee.due_rolls_to_business_day` | carência e rolagem do vencimento | int, bool | sem | livre | d | JUR | P2 | `V6_1:200-201` |
| `fee.no_carry_over` | vedar acúmulo de encargos de períodos anteriores | bool | sem | ERI (ARSAE-MG veda) | d | JUR | P2 | novo |
| `fee.legal_rate_fallback` | taxa legal do CC art. 406 quando nada foi contratado | bool | desligado | Lei 14.905/2024; aplicação a tarifa a confirmar | d | JUR | P2 | novo |
| `fee.norm_source` | norma que fundamenta a política | texto | — | obrigatório | d | JUR | P2 | `V6_1:199` |

### G6 — Retenção e eliminação

Pisos fixos definidos pelo Ziip SAN (classe c, ADR-0015): o cliente só amplia prazos. Eliminação ao fim do contrato e
descarte em backups seguem procedimento do Ziip SAN (classe a), com certificado de descarte entregue ao cliente. A
eliminação é executada só pelo dono da plataforma (`PLATFORM_OWNER`), uma pessoa com MFA, sem segundo aprovador,
sempre com justificativa e auditoria, depois de carência de 30 dias contados do fim do contrato; eliminação concluída
e certificado emitido em até 30 dias após a carência (RNF-42; `CENTRAL-ZIIP.md` §6.3; decisões do dono do produto,
12/10/2026). Contestação registrada na carência suspende a eliminação até a resposta do Ziip, em até 15 dias; com
ela, o prazo máximo total é de 75 dias do fim do contrato. Ordem judicial sempre suspende (plano v2.3).

Chamados de suporte do Ziip ao tenant e seus anexos não são parâmetro do tenant: ficam retidos pela vigência do
contrato mais 5 anos (ADR-0020) e não entram na eliminação de fim de contrato; o certificado de descarte registra a
exceção.

| Chave | Descrição | Tipo | Sug. | Limite | Cl. | Apr. | Fase | Código |
|---|---|---|---|---|---|---|---|---|
| `ret.billing_history_months` | histórico de leitura e faturamento | meses | 120 | piso 120 (NR 11 art. 22; `08` E-05, `11` R38, `13`), também quando a ERI não adotou a NR 11; só amplia | c | DPO+JUR | P2 | `lgpd_retention_schedule` sem semente (`V2_5:138-158`); motor não elimina |
| `ret.billing_after_closure_years` | guarda após o encerramento do vínculo | anos | 10 | piso 10 anos, a confirmar: prescrição decenal (REsp 1.117.903/RS, Temas 251 a 254 do STJ, julgado em 09/12/2009; verificado em 22/10/2026: prazo da pretensão de cobrança pelo prestador, não da repetição de indébito, `13`); repetição de indébito 10 anos (Súmula 412 e Tema 932, REsp 1.532.514/SP); só amplia | c | JUR | P2 | novo |
| `ret.prescription_years` | prescrição por natureza (tarifa, multa administrativa, repetição); não é prazo de guarda | mapa | sem | G6 | d | JUR | P4 | novo; estado `PRESCRIBED_DEBT` já existe (`V4_1:236`) |
| `ret.social_base_days` | eliminação do lote CadÚnico/BPC importado | dias | sem | piso a definir (sem norma no levantamento); só amplia | c | DPO | P1 | novo (`purge_at`, `V4_2`) |
| `ret.social_documents_years` | documentos do pedido manual | anos | 10 (ARSAE-MG) | piso a definir (sem norma federal no levantamento); só amplia | c | DPO | P1 | novo |
| `ret.service_request_years` | protocolos de atendimento | anos | 5 (ARSAE-MG) | piso a definir (sem norma federal no levantamento); só amplia | c | DPO | P3 | novo |
| `ret.cutoff_proof_years` | prova de aviso de corte | anos | sem | piso a definir (G6/G9); só amplia | c | JUR | P3 | novo |
| `ret.negative_listing_max_years` | informação negativa em birô | anos | 5 | teto 5 (CDC art. 43 §1º); só reduz | c | JUR | P4 | novo |
| `ret.incident_record_years` | registro de incidente de segurança | anos | 5 | piso 5 (Res. CD/ANPD 15/2024 art. 10, `10`); ente público: tabela de temporalidade; só amplia | c | DPO | P1 | novo |
| `lgpd.dsr_deadline_days` | prazo de resposta ao titular | dias | 15 (privado) | teto 15 (LGPD art. 19 II); ente público: LAI; só reduz | c | DPO | P1 | fixo em `compliance/sigsan-lgpd/src/main/java/br/com/sigsan/compliance/lgpd/engine/DataSubjectRightsEngine.java:31` e `.../service/LgpdEngineService.java:225` |
| `ret.contract_end` | devolução e eliminação ao fim do contrato SaaS | procedimento | procedimento do Ziip SAN | devolução em formato aberto em até 15 dias, carência de 30 dias do fim do contrato, eliminação só por `PLATFORM_OWNER` com MFA e justificativa, suspensa por contestação na carência (resposta em até 15 dias) ou por ordem judicial, e certificado de descarte entregue ao cliente em até 30 dias após a carência ou a resposta, no máximo 75 dias do fim do contrato (RNF-42; DPA; `10` §1.2 item 10) | a | — | P3 | novo (não há offboarding, `10`) |
| `ret.backup_disposal` | descarte nas cópias de backup do dado já eliminado | procedimento | procedimento do Ziip SAN | prazo de rotação a definir; incluído no certificado de descarte | a | — | P3 | novo |

### G9 — Corte e religação

Travado (classe a): por inadimplência, proibido iniciar corte em sexta, sábado, domingo, feriado e véspera (Lei 13.460
art. 6º p.u. e Lei 8.987 art. 6º §4º, redação da Lei 14.015/2020); aviso comprovado antes da OS; sem notificação a
taxa de religação não é devida (Lei 13.460 art. 6º p.u.); emergência técnica só dispensa as vedações com justificativa
e foto anexadas à OS. O bloqueio impede a OS, não só avisa.

Padrão de fábrica (classe b, ADR-0015): as vedações de dia e a janela de horário valem para todos os motivos de corte.
O tenant pode excluir motivo que não seja inadimplência mediante aceite formal registrado, com norma citada.

| Chave | Descrição | Tipo | Sug. | Limite | Cl. | Apr. | Fase | Código |
|---|---|---|---|---|---|---|---|---|
| `cut.min_notice_days` | aviso prévio | dias corridos | 30 | ≥ 30 (Lei 11.445 art. 40 §2º); ERI pode ampliar | c | JUR | P3 | novo |
| `cut.notice_validity_days` | validade do aviso não executado | dias | sem | ERI (ARSAE-MG: 90) | d | JUR | P3 | novo |
| `cut.notice_channels`, `cut.accepted_proofs` | meios de aviso e provas de entrega aceitas | listas | sem | prova obrigatória, travada; tipos: ERI/G9 | d | JUR | P3 | novo |
| `cut.business_hours` | janela de "horário comercial" por dia da semana | intervalos | padrão de fábrica a definir (P0) | limites de fábrica a definir (P0); fora deles, recusa; dentro e diferente do padrão, aceite formal (Lei 13.460 art. 5º XVI; definição: G9) | b | JUR | P3 | novo |
| `cut.extra_forbidden_rules` | vedações adicionais da ERI (ex.: AGR-GO só dias úteis) | regras | sem | só acrescenta às travadas | c | JUR | P3 | novo |
| `cal.holidays` | feriados nacional, estadual, municipal | tabela | nacionais pré-carregados (§7) | nacionais não se removem; só acrescenta | c | OPE | P2 | `holidays` (`V6_1:242-274`) |
| `cut.min_overdue_days`, `cut.max_debt_age_days`, `cut.min_debt_amount` | atraso mínimo, idade máxima do débito (débito pretérito), valor mínimo | int, R$ | sem | ERI (ADASA ≥ 60 e ≤ 120; AGERGS e AGEPAR ≤ 90); débito pretérito: G9 | d | JUR | P3 | novo |
| `cut.protected_classes` | classes protegidas e regra própria (aviso maior, carência, aviso à ERI) | tabela | saúde, educação, internação coletiva, tarifa social | lista mínima travada (Lei 11.445 art. 40 §3º); só acrescenta classe ou amplia prazo; prazos: ERI | c | JUR | P3 | novo |
| `cut.social_tariff_allowed` | corte de beneficiário inadimplente | bool | sem | só se a ERI previr (`08` R-14) | d | JUR | P3 | novo |
| `cut.sewer_allowed` | interrupção de esgoto | bool | não | Lei 11.445 art. 40 V | d | JUR | P3 | novo |
| `cut.third_party_debt_blocks` | débito de outro serviço motiva corte | bool | não | ARSESP veda; demais a confirmar (E-09) | d | JUR | P3 | novo |
| `cut.reasons` | motivos (inadimplência, fraude, risco, pedido, emergência técnica) e quais seguem as vedações de dia e horário | tabela | todos os motivos com as vedações, exceto emergência técnica com justificativa e foto | inadimplência sempre com a regra integral (classe a); excluir outro motivo exige aceite formal com norma citada | b | JUR | P3 | novo |
| `rec.sla_hours` | religação normal, urgência, indevida | horas | sem | ERI (ex.: ARSESP 48/6/6) | d | OPE | P3 | novo |
| `rec.urgent_fee`, `rec.release_condition` | preço da urgência; condição de religação (substitui `rec.requires_settlement`, 20c §5.2, v2.15) | R$, enum | `rec.urgent_fee`: sem; `rec.release_condition`: `MOTIVATING_DEBT_PAID_OR_AGREED` (opções `MOTIVATING_DEBT_PAID_OR_AGREED`, `DOWN_PAYMENT_PAID`, `FULL_SETTLEMENT`) | ERI | d (taxa); b (condição) | OPE+JUR | P3 | novo |
| `sac.response_days` | prazos de resposta do atendimento | dias | sem | ERI; Decreto 11.034/2022 só como referência (`13`) | d | REG | P3 | novo |

### Emissão em campo e almoxarifado (ADR-0031, ADR-0032; plano v2.5)

Travado (classe a): quem requisita material não aprova a própria requisição (segregação de funções); toda requisição,
decisão, reserva, baixa, devolução e ajuste vai para a auditoria; a conta impressa em campo nunca é alterada sem nova
versão, motivo e aprovação (ADR-0031, item 6).

| Chave | Descrição | Tipo | Sug. | Limite | Cl. | Apr. | Fase | Código |
|---|---|---|---|---|---|---|---|---|
| `bill.field_undercharge_rule` | tratamento da diferença quando a conta impressa em campo cobrou a menos | enum | diferença em conta posterior, como item separado, com motivo e aviso | norma da ERI, quando houver; cobrança a maior é sempre retificada (travado) | b | REG | P4 | novo (ED-19) |
| `inv.approval_bands` | faixas de valor da requisição de material e papel que aprova cada faixa (encarregado, almoxarife, gerente) | tabela | três faixas, com valores a definir na ED-58 | toda faixa com aprovador; segregação travada | b | OPE+FIN | P4 | novo (`approval_rule`, ED-58) |
| `inv.auto_approve_below` | valor abaixo do qual a requisição é aprovada automaticamente | R$ | a definir na ED-58 | não alcança itens controlados | b | OPE+FIN | P4 | novo |
| `inv.controlled_items` | itens e classes que sempre exigem aprovação, em qualquer valor | lista | hidrômetros e materiais de alto valor | hidrômetro não sai da lista (controle do parque, ED-14) | b | OPE | P4 | novo |
| `inv.allow_negative_balance` | aprovação de requisição sem saldo disponível, com alerta de reposição | bool | permitido | proibir exige aceite; permitido exige o relatório diário de negativos ligado | b | OPE+FIN | P4 | novo |
| `inv.negative_regularization_days` | prazo para regularizar item negativo por entrada ou ajuste com justificativa | dias | 5 dias úteis | teto de fábrica a definir na ED-58; só reduz | c | OPE | P4 | novo |
| `inv.inventory_cycle_days` | intervalo máximo entre inventários do almoxarifado | dias | 90 | teto de fábrica a definir na ED-58; só reduz | c | OPE+FIN | P4 | novo |
| `inv.costing_method` | método de custeio do material apropriado na OS | enum | custo médio ponderado | métodos suportados pelo produto | b | FIN | P4 | novo |

### GIS e cadastro técnico (ADR-0034; plano v2.11)

Travado (classe a): fora do backoffice autenticado (portal, serviços WMS e OGC públicos ou entregues a terceiros,
relatórios externos, compartilhamento com prefeitura), imóvel, ligação, ramal e cavalete nunca são publicados por
ponto, porque a coordenada ligada a matrícula ou titular é dado pessoal (LGPD, art. 5º, I); rede e ativos não são dado
pessoal e podem ser publicados, com a restrição de infraestrutura crítica de `GIS-CADASTRO-TECNICO.md` §12, item 7.
Decisão do dono do produto de 12/10/2026 (plano v2.11). Os demais parâmetros `gis.*` (`GIS-CADASTRO-TECNICO.md` §3.10)
entram neste grupo na P1.26.

| Chave | Descrição | Tipo | Sug. | Limite | Cl. | Apr. | Fase | Código |
|---|---|---|---|---|---|---|---|---|
| `gis.public_aggregation_min_units` | mínimo de imóveis por célula de agregação (quadra ou setor) em toda publicação de dado de imóvel ou ligação fora do backoffice autenticado, inclusive temáticos de consumo e inadimplência; célula abaixo do mínimo não é publicada | int | 5 | piso 5, fixado pelo Ziip SAN (decisão do dono do produto, plano v2.11); só amplia | c | DPO | P3 | novo (core `V9_6`, P1.26; aplicado na P3.27 e na ED-67) |

---

### Certificações de qualidade (ADR-0041; plano v2.17)

Normas ligadas e desligadas pelo tenant. Fontes: levantamentos 21a e 21b. `<norma>` é a chave da norma no catálogo
(`iso9001`, `iso14001`, `iso45001`, `iso55001`, `iso27001`, `iso27701`, `iso17025`, `iso46001`, `iso24510`, `iso24511`,
`iso24512` e as de referência). As normas `REGULATORY` não têm `enabled`: são obrigatórias por aplicabilidade.

| Chave | Descrição | Padrão de fábrica | Classe | Tarefa |
|---|---|---|---|---|
| `cert.<norma>.enabled` | norma ligada ou desligada | desligada | d | P1.32 |
| `cert.<norma>.edition` | edição em uso (catálogo versionado; ISO 9001 e 14001: 2015 ou 2026; 27701: 2019 ou 2025) | edição vigente do catálogo | d | P1.32 |
| `cert.scope_statement` | texto do escopo e do local (tenant, laboratório, instalação), versionado | sem | d | P1.32 |
| `cert.deactivation_keeps_records` | desligar a norma não apaga registro | verdadeiro | a | P1.32 |
| `cert.record_retention_years` | retenção de registro do sistema de gestão sem lei específica | maior prazo do G6 que o registro comprova | c | P1.7 |
| `cert.evidence_pack_personal_data` | pacote de evidência contém dado pessoal | falso | a | ED-75 |
| `cert.external_auditor_access` | papel temporário de auditor externo, somente leitura, com prazo e registro de cada leitura | desligado | b | ED-75 |
| `cert.internal_target_vs_legal` | meta interna comparada ao prazo legal | nunca mais frouxa que o prazo legal | c | P3.31 |
| `cert.risk_scale` | escala e matriz de risco do tenant | matriz 5x5 do produto | d | P3.31 |
| `cert.audit_cycle` | intervalo do programa de auditoria interna | sem | d | P3.31 |
| `meter.verification_interval_years` | prazo da verificação subsequente do hidrômetro por regime | 7 (Portaria Inmetro 155/2022) | c (só reduz) | ED-14 |

Regras: a lei vence; se uma norma ativa pedir retenção ou acesso mais restritivo que o limite legal travado, o
validador avisa e o limite legal prevalece (ADR-0041, item 9).

### Decisões de 28/10/2026 (plano v3.0)

| Chave | Descrição | Opções | Padrão de fábrica | Classe | Origem |
|---|---|---|---|---|---|
| `bill.holder_name_display` | nome impresso na conta | `USER_HOLDER_ONLY` (só o usuário titular); `USER_HOLDER_AND_OWNER` (titular e proprietário) | `USER_HOLDER_ONLY` | b | cartão de 28/10/2026; NR 11, art. 33, §2º; 23b |
| `portal.idp_govbr_enabled` | login gov.br no portal | ligado; desligado (só tenant de direito público com integração aprovada) | desligado | d | ADR-0043 |
| `portal.min_assurance_by_operation` | nível mínimo do gov.br por operação | bronze, prata ou ouro por operação | prata para troca de titularidade, tarifa social, contato e negociação | b | ADR-0043 |
| `portal.guest_payment` | pagamento sem conta | `OFF`; `CODE_ONLY`; `CODE_AND_DOCUMENT_SUFFIX` | `CODE_ONLY` | b | ADR-0043 |

### Parâmetros previstos pelos levantamentos 22a a 22d (plano v2.18)

Os achados abaixo criam ou ampliam parâmetro do tenant (ADR-0038). Cada chave nasce na tarefa que incorpora o achado, com opções, padrão mais protetivo e classe, conforme o detalhamento do relatório (22a, 22b, 22c ou 22d) e o mapa em `docs/levantamento/lacunas/22-INCORPORACAO-NO-PLANO.md`. Os achados são: CAD-65, CAD-66, CAD-67, CAD-68, CAD-69, CAD-70, CAD-71, CAD-72, CAD-73, CAD-74, CAD-75, CAD-76, CAD-77, CAD-79, CAD-80, CAD-81, CAD-82, CAD-84, CAD-85, CAD-86, CAD-87, CAD-88, CAD-89, CAD-90, CAD-91, CAD-92, CAD-93, CAD-94, CAD-95, CAD-96, CAD-97, CAD-98, CAD-99, CAD-100, CAD-101, CAD-102, CAD-103, CAD-104, OPE-01, OPE-02, OPE-03, OPE-04, OPE-05, OPE-06, OPE-07, OPE-08, OPE-09, OPE-10, OPE-11, OPE-13, OPE-14, OPE-15, OPE-16, OPE-17, OPE-18, OPE-19, OPE-20, OPE-21, OPE-23, OPE-24, OPE-25, OPE-26, OPE-27, OPE-28, OPE-29, OPE-31, OPE-32, OPE-33, OPE-34, OPE-36, OPE-37, OPE-38, OPE-40, PLT-01, PLT-02, PLT-04, PLT-05, PLT-06, PLT-07, PLT-10, PLT-11, PLT-12, PLT-13, PLT-14, PLT-17, PLT-18, PLT-19, PLT-20, PLT-22, PLT-24, PLT-25, PLT-27, PLT-29, PLT-30, PLT-31, PLT-33, PLT-34, PLT-36, PLT-38, PLT-40, PLT-41, PLT-42, GES-01, GES-02, GES-03, GES-04, GES-05, GES-06, GES-07, GES-08, GES-09, GES-10, GES-11, GES-13, GES-14, GES-15, GES-16, GES-17, GES-18, GES-19, GES-20, GES-21, GES-22, GES-24, GES-25, GES-26, GES-27, GES-28, GES-29, GES-30, GES-32, GES-33.

### Bifurcações resolvidas por configuração (ADR-0038, ADR-0039, ADR-0040; planos v2.14 e v2.15)

Origem: perguntas e parâmetros propostos nos levantamentos `docs/levantamento/gsan/20a` (Q1, Q2, Q5 a Q8), `20b`
(§5.1, Q5 e Q6), `20c` (§5.2), `20d` (§5) e `20f` (Q3 a Q6 e §5). Padrão de fábrica: caminho mais protetivo ao
usuário. Chave proposta em mais de um levantamento foi unificada numa só, com as duas origens citadas; chave que já
existia nos grupos G1 a G9 ou nesta seção foi ajustada na própria linha, com a origem citada (plano v2.15).

| Chave | Opções | Padrão de fábrica | Cl. | Origem | Fase |
|---|---|---|---|---|---|
| `connection.min_consumption_enabled` | consumo mínimo fixado por ligação: desligado; ligado | desligado (NR 13/2025 veda em contratos novos) | b | 20a Q1; GSAN | P2 |
| `connection.voluntary_suppression_with_debt` | `ALLOW` (mantém o débito em cobrança); `ALLOW_WITH_PERMISSION`; `BLOCK` | `ALLOW` | b | 20a Q2; GSAN `indicadorSupressao` | ED-13 |
| `registry.main_category_rule` | `MOST_ECONOMIES`; `LOWEST_TARIFF`; `CONFIGURED_ORDER`; `LOWEST_CODE` | `MOST_ECONOMIES`, desempate por `LOWEST_TARIFF` | d | 20a Q5 | P1.3 |
| `sewer.out_of_use_on_water_cut` | `AUTOMATIC` (efeito da mesma OS de corte ou supressão); `SERVICE_ORDER_ONLY`; `NONE` | `AUTOMATIC` | b | 20a Q6; GSAN | ED-13 |
| `service_type.charge_mode` (por tipo de serviço) | `BILL_DEBIT` (débito na próxima conta); `STANDALONE_INVOICE` (NFS-e avulsa, P3.6); `NONE` | por tipo no catálogo de fábrica | d | 20a Q7 | P1.31 |
| `cut.level_model` | situação de corte com atributo `cut_level` (cavalete, ramal); supressão como situação própria | `cut_level` no evento | d | 20a Q8 | P3.4 |
| `service_type.internal_approval` (por tipo) | aplicação por quem registra; aprovação por outra pessoa; atalho do tenant `NONE`, `CONTACT_ONLY`, `ALL_LOW_RISK` (`so.internal_change_auto_apply` do 20d §5, unificada nesta chave, v2.15) | outra pessoa para o que muda faturamento; direta para contato (`CONTACT_ONLY`); titularidade, documento, categoria e economias sempre com análise | b | ADR-0039 item 8; 20d §5 | P1.31 |
| `rec.undue_cut_compensation` | fórmula ARESC/AGIR (maior entre 2× religação de urgência e 20% da fatura); ARIS (religação em 4 h sem ônus); crédito manual com aprovação | conforme perfil da ERI; sem perfil, a fórmula ARESC/AGIR | b | 20f Q3, LEG-22 | P3.4 |
| `leak.*` (regra e teto do desconto, prazo, âncora, faturas em 12 meses, esgoto pela média) | perfis ARIS, AGIR, ARESC ou valores próprios; `leak.discount_rule`: `AVERAGE_6M`, `PCT_OF_EXCESS`, `MOST_FAVORABLE`; `leak.max_discount_pct_water`, `leak.max_discount_pct_sewer` (20b §5.1, v2.15) | perfil da ERI; sem perfil, o mais favorável ao usuário entre os três (`MOST_FAVORABLE`, desconto máximo de 100%) | b, desconto c quando a ERI fixar piso | 20f Q4, §5; 20b §5.1 | P3.28 |
| `meter.test_*` (aviso, cobrança só se normal, meses de revisão) | perfis ARIS, AGIR, ARESC ou valores próprios | cobrança só se o hidrômetro estiver normal | b | 20f Q4, §5 | P3.28 |
| `fee.interest_mode`, `fee.interest_daily_pct` | por dia com teto da ERI (`DAILY_RATE_WITH_CAP`); por dia sem teto (`DAILY_RATE`); mensal pro rata base 30 ou 30,3 (`MONTHLY_PRO_RATA`); mês cheio (`CALENDAR_MONTH`) (20c §5.2, v2.15) | por dia com teto (SC: 0,033%); mantido na v2.15 diante do `SIMPLE_PER_DAY` sem teto proposto no 20c | b | 20f Q5, LEG-29; 20c §5.2 | P2 |
| `neg.channels`, `neg.public_entity_allowed` | `NONE`; `BUREAU` (negativação em birô); `PROTEST` (protesto por arquivo); `BOTH`; ente público só com liberação própria (`neg.protest_enabled` absorvida em `neg.channels`, 20c §5.2, v2.15) | `NONE` até o aceite do tenant com parecer jurídico | b | 20f Q6, LEG-41; 20c §5.2 | ED-30 |
| `quit.statement_channel` | espaço na fatura; documento anexo; portal | espaço na fatura | d | 20f §5, Lei 12.007 | P2.24 |
| `bill.water_quality_source` | planilha mensal do laboratório; módulo de qualidade (ED-50) | planilha mensal até a ED-50 | d | 20f Q7, Decreto 5.440 | P2.24 |
| `sac.deadline_counting_rule` | `CALENDAR` (dias corridos); `BUSINESS` (dias úteis, com os feriados de `sac.holiday_scope`) (`sac.deadline_day_basis` do 20d §5, unificada, v2.15) | conforme ERI; sem ERI, `CALENDAR`, a contagem mais favorável ao usuário (padrão confirmado pelo dono em 21/10/2026; cada tenant pode escolher `BUSINESS`) | b (20d §5, v2.15; era d) | 20f §5; 20d ATD-04 | P3.4 |
| `cut.same_user_other_property_debt` | condiciona ligação e religação a débito do mesmo usuário em outro imóvel: sim; não | não | b | 20f §5, LEG-20 | P3.4 |

**Medição e faturamento (20b §5.1; v2.15).** Inclui as chaves de 20b unificadas com as de 20c.

| Chave | Opções | Padrão de fábrica | Cl. | Origem | Fase |
|---|---|---|---|---|---|
| `bill.review_effects` | `SUSPEND_ALL` (cobrança, acréscimos, negativação, corte, débito automático); `SUSPEND_DUNNING_ONLY` | `SUSPEND_ALL`; corte de conta em revisão sempre vedado (travado) | b | 20b §5.1, FAT-35; NR 11 arts. 37–38 | P2.20 |
| `bill.hold_on_anomaly` | `HOLD_AND_REVIEW`; `ISSUE_AVERAGE_THEN_ADJUST`; `ISSUE_MEASURED_WITH_NOTICE` | `HOLD_AND_REVIEW`, com o teto de `bill.review_max_days` | b | 20b §5.1, FAT-32; ERI | P2.5 |
| `bill.review_max_days` | dias | prazo da ERI | c (teto da ERI; só reduz) | 20b §5.1, FAT-34 | P2.20 |
| `bill.auto_review_value_bands` | faixas de valor por categoria | sem faixas | d | 20b §5.1, FAT-31 | P2.5 |
| `bill.undue_charge_double_refund` | ligado; desligado com norma citada | ligado | b | 20b §5.1; 20c ARR-09; CDC art. 42 p.u. (aplicação a tarifa: G5, E-02) | P3.29 |
| `bill.rectification_limits_against_user` | quantidade por ano; meses de antiguidade | sem limite próprio, só a prescrição; correção a favor do usuário nunca limitada (travado) | d | 20b §5.1, FAT-50 | P2.20 |
| `bill.due_change_max_per_year` | inteiro; sem limite | sem limite | d | 20b §5.1, FAT-52; Lei 8.987 art. 7º-A | P2.20 |
| `bill.due_change_min_days_before_due` | dias | 0 | d | 20b §5.1, FAT-52 | P2.20 |
| `bill.min_issue_amount`, `bill.below_min_rule` | valor; `CARRY_FORWARD` (máximo de meses); `ISSUE_ANYWAY` | `CARRY_FORWARD`, 3 meses, sem encargo sobre o valor arrastado | d | 20b §5.1, FAT-82; editais TR-298/299 | P2.5 |
| `bill.validity_months` | meses | sem padrão | d | 20b §5.1 | P2.5 |
| `bill.mail_extra_days` | dias | sem padrão | d | 20b §5.1 | P2.5 |
| `bill.second_copy_fee` | valor por canal | 0; canal digital sempre 0 | b | 20b §5.1, FAT-89; `08` R-11 | P3.3 |
| `bill.delivery_address_fee` | valor | 0 | b | 20b §5.1; editais TR-302 | P2.5 |
| `bill.advance_billing` | desligado; por pedido do órgão (OS) | desligado | d | 20b §5.1, FAT-85 | ED-22 |
| `bill.cut_connection_charge` | `NONE`; `AVAILABILITY_FEE` (tarifa de disponibilidade, se a ERI previr; `BASIC_FEE` no 20b); `MIN_TARIFF_PCT` | `NONE` | b | 20b §5.1 (`bill.cut_connection_rule`, unificada); 20c §5.2; NR 13 art. 10 | P2.5 |
| `bill.inactive_economy_rule` | `NO_CHARGE`; `BASIC_FEE` (se a ERI previr) | `NO_CHARGE` | b | 20b §5.1; NR 13 art. 10 | P2.5 |
| `tariff.fixed_part_model` | `BASIC_FEE`; `MINIMUM_CONSUMPTION` | `BASIC_FEE`; `MINIMUM_CONSUMPTION` só com `reg.contract_pre_nr13` | c | 20b §5.1; NR 13 arts. 8º–9º | P2.5 |
| `sewer.no_water_volume_rule` | `CONNECTION_MINIMUM`; `NON_METERED_ESTIMATE`; `FIXED_VOLUME`; `LOWER_OF` | `LOWER_OF` | b | 20b §5.1, FAT-71; ERI | P2.5 |
| `sewer.alternative_pct_enabled` | ligado; desligado | ligado onde a ERI previr | d | 20b §5.1, FAT-70 | ED-13 |
| `condominium.apportionment_mode` | `COMMON_AREA_TO_CONDOMINIUM`; `EQUAL_PER_ECONOMY`; `UNMETERED_ONLY`; `NONE` | `COMMON_AREA_TO_CONDOMINIUM` | b | 20b §5.1, FAT-78; Lei 13.312; NR 11 arts. 28–30 | ED-15 |
| `condominium.max_increment_m3`, `condominium.tolerance_pct` | inteiros | sem padrão | d | 20b §5.1, FAT-78 | ED-15 |
| `special_situation.max_months` | meses | 12, com reavaliação por OS | b | 20b §5.1, FAT-63 | ED-22 |
| `quality.noncompliance_compensation` | `CREDIT_PCT_OF_WATER` (%); `NONE` (só se a ERI não previr) | `CREDIT_PCT_OF_WATER`, percentual a informar | b | 20b §5.1, FAT-15; ERI; CDC art. 20 | ED-22 |
| `infraction.charge_timing` | `AFTER_DECISION`; `ON_ISSUE_SUSPENDED_BY_APPEAL` | `AFTER_DECISION` | b | 20b §5.1, FAT-25 | ED-24 |
| `infraction.appeal_days`, `infraction.max_retroactive_months` | inteiros | ERI | c (prazo mínimo e teto da ERI) | 20b §5.1, FAT-25 (`inf.appeal_days` no 20c §5.1) | ED-24 |
| `credit.type_limits` | valor máximo por tipo | sem limite; acima do limite, aprovação dupla | d | 20b §5.1, FAT-11 | P2.5 |
| `large_user.min_m3`, `large_user.min_economies` | inteiros | sem padrão | d | 20b §5.1, FAT-77; NR 11 | ED-17 |
| `pay.refund_mode` | `USER_CHOICE`; `CREDIT_NEXT_BILL`; `BANK_TRANSFER` | `USER_CHOICE` | b | 20b §5.1 (`bill.overpayment_refund_mode`, unificada); 20c §5.2, ARR-08; CDC art. 42 | P3.29 |
| `inst.bonus_installments` | desligado; regra (contas consecutivas em dia, tolerância em dias, prestações perdoadas, janela) | desligado; implementado só se a P1.1 encontrar a regra no piloto | d | 20b FAT-08 (`installment.bonus_*`, unificada); 20c §5.2, COB-36 (mecanismo 2) | P4.1 |

**Arrecadação, cobrança, parcelamento, negativação e contábil (20c §5.2; v2.15).** `pay.duplicate_winner`,
`fee.correction_mode` e `rec.release_condition` foram ajustadas em G4, G5 e G9; `fee.interest_mode` e `neg.channels`,
nas linhas acima.

| Chave | Opções | Padrão de fábrica | Cl. | Origem | Fase |
|---|---|---|---|---|---|
| `fee.fine_mode` | `ONE_TIME`; `DAILY_CAPPED` (percentual diário até o teto de `fee.fine_pct`) | `DAILY_CAPPED` | b | 20c §5.2, COB-16 a COB-19; destino de `FineMode.PER_DAY` (ADR-0015) | P2.5 |
| `fee.charge_moment` | `NEXT_BILL`; `AT_PAYMENT_DOCUMENT` | `NEXT_BILL` | b | 20c §5.2 | P2.5 |
| `fee.exemptions` | conjunto: `AUTO_DEBIT`, `BANK_DELAY`, `PROVIDER_FAULT`, `COLLECTION_HOLD`, `CHANNEL_OR_ACTION_FLAG` (forma de arrecadação ou ação de cobrança marcada como isenta; 20c COB-17, 15/10/2026) | todas ligadas, exceto `CHANNEL_OR_ACTION_FLAG`, que depende de marcação por forma ou ação | c (só acrescenta) | 20c §5.2, ARR-18 | P2.5 |
| `bill.carry_over_unpaid` | `OFF`; `ON` | `OFF` | b | 20c §5.2; relação com `fee.no_carry_over` | P2.5 |
| `pay.partial_settlement_order` | `OLDEST_DUE_FIRST`; `USER_CHOICE`; `REJECT_PARTIAL` | `OLDEST_DUE_FIRST` | b | 20c §5.2 | ED-26 |
| `dun.notice_fee` | `NONE`; `FIXED` (valor) | `NONE` | b | 20c §5.2, COB-07; TR-398 | P4.1 |
| `inst.interest_method` | `NONE`; `SIMPLE`; `PRICE` | `NONE` | b | 20c §5.2, COB-30 | P4.1 |
| `inst.down_payment_rule` | `NONE`; `MIN_PCT`; `MIN_PCT_OR_MIN_TARIFF`; `LAST_BILL` | `NONE` | b | 20c §5.2; TR-360 | P4.1 |
| `inst.unpaid_down_payment_action` | `KEEP_AND_CHARGE_NEXT_BILL`; `UNDO` | `KEEP_AND_CHARGE_NEXT_BILL` | b | 20c §5.2, COB-33 | P4.1 |
| `inst.down_payment_grace_days` | inteiro | piso de fábrica a definir | c | 20c §5.2; TR-362 | P4.1 |
| `inst.cancel_after_overdue` | inteiro (prestações em atraso) | 3 | c (só amplia) | 20c §5.2, COB-34 | P4.1 |
| `inst.discount_on_cancel` | `KEEP_PROPORTIONAL`; `REVOKE_ALL` | `KEEP_PROPORTIONAL` | b | 20c §5.2, COB-34 | P4.1 |
| `inst.requires_confirmation` | `SIGNED_TERM`; `DOWN_PAYMENT`; `NONE` | `SIGNED_TERM` | b | 20c §5.2 | P4.1 |
| `neg.prior_notice_by` | `PROVIDER_AND_BUREAU`; `BUREAU` | `PROVIDER_AND_BUREAU` | c | 20c §5.2; Súmula 359 do STJ [EXT] | ED-30 |
| `neg.notice_min_days` | inteiro | piso a definir com o jurídico | c | 20c §5.2 | ED-30 |
| `neg.exclusion_business_days` | inteiro | 5 | c (teto; só reduz) | 20c §5.2, COB-43; Súmula 548 do STJ [EXT] | ED-30 |
| `neg.excluded_profiles` | conjunto: `LOW_INCOME`, `PUBLIC_BODY`, `PROTECTED_CLASSES` | todos excluídos | c | 20c §5.2; coerente com `neg.public_entity_allowed` | ED-30 |
| `cert.positive_effect` | `ON`; `OFF` | `ON` | b | 20c §5.2, COB-24 | ED-38 |
| `unit.transfer_with_previous_debt` | travado: a transferência de titularidade sempre acontece; o débito anterior é pessoal e fica com o titular que o contraiu, nunca com a unidade nem com o novo titular | `ALLOW_DEBT_STAYS_WITH_HOLDER` | a (decisão do dono do produto, 15/10/2026) | 20c §5.2 | P1.31 |
| `acc.doubtful_method` | `TARGET_WRITE_OFF`; `AGING_MATRIX`; `EXPECTED_LOSS` | sem padrão: escolha do contador do tenant | d | 20c §5.2; CPC 48 [EXT] | ED-32 |

**Atendimento, OS e segurança (20d §5; v2.15).** `sac.deadline_day_basis` e `so.internal_change_auto_apply` foram
unificadas em `sac.deadline_counting_rule` e `service_type.internal_approval`; `cut.revalidate_on_execution` é
trava (abaixo). Política de senha por tenant pelo ADR-0040.

| Chave | Opções | Padrão de fábrica | Cl. | Origem | Fase |
|---|---|---|---|---|---|
| `sac.holiday_scope` | `NATIONAL`; `NATIONAL_STATE`; `NATIONAL_STATE_MUNICIPAL` | `NATIONAL` (só no prazo de atendimento; a vedação de corte considera todas as esferas, travada) | b | 20d §5, ATD-04 | P3.4 |
| `sac.extension_policy` | `NONE`; `ONCE_JUSTIFIED`; `PER_ERI_RULE` | `NONE`; ouvidoria no máximo uma vez, por igual período (Lei 13.460 art. 16) | b | 20d §5, ATD-05 | P3.4 |
| `sac.duplicate_handling` | `LINK_AS_REITERATION`; `LINK_AS_DUPLICATE` | `LINK_AS_REITERATION`; recusar não é opção | a (não recusar); d (forma) | 20d §5, ATD-06, ATD-07; Lei 13.460 art. 11 | ED-35 |
| `sac.ombudsman_mode` | `OWN_OMBUDSMAN`; `TITULAR`; `REGULATOR_CHANNEL` | sem | d | 20d §5, ATD-21 | ED-35 |
| `sac.ombudsman_days` | dias, com uma prorrogação | 30 + 30 | c (só reduz) | 20d §5, ATD-21; Lei 13.460 | ED-35 |
| `sac.satisfaction_survey` | `POST_CLOSURE`; `PERIODIC`; ambos | ambos, periódica anual | c (mínimo anual) | 20d §5, ATD-23; Lei 13.460 art. 23 | ED-35 |
| `sac.protocol_receipt_channels` | canais com opt-in | todos os canais com contato informado | b | 20d §5, ATD-01 | P3.4 |
| `svc.debt_blocks_request` | `NONE`; lista da ERI | `NONE`; o registro do protocolo nunca é bloqueado | b | 20d §5, ATD-08 | ED-34 |
| `so.retroactive_close_days` | inteiro | 3 | b | 20d §5, OS-12 | ED-36 |
| `so.customer_order_auto_expire` | `NEVER`; dias | `NEVER` (alerta e decisão registrada) | b | 20d §5, OS-13 | ED-36 |
| `rec.auto_order_on_payment` | sim; não | sim | b | 20d §5, OS-10; 20c COB-12 | P3.4 |
| `so.material_charge_policy` | `NEVER`; `PRICE_TABLE_ERI` | `NEVER` | b | 20d §5, OS-20 | ED-36 |
| `sched.priority_weights` | pesos por critério | do produto; urgência legal sempre no topo | d | 20d §5, OS-14 | ED-36 |
| `sec.password_min_length` | inteiro | 12, com lista de senhas vazadas | c (só amplia) | 20d §5, SEG-07; ADR-0040 | ED-06 |
| `sec.password_max_age_days` | `OFF`; dias | `OFF`, ligável por exigência de edital | b | 20d §5, SEG-08; ADR-0040 | ED-06 |
| `sec.password_history` | inteiro | 5 | c (só amplia) | 20d §5, SEG-08; ADR-0040 | ED-06 |
| `sec.lockout_attempts` | inteiro | 5 | c (só reduz; POC-05 usa 3) | 20d §5, SEG-09; ADR-0040 | ED-06 |
| `sec.user_inactivity_days` | dias | 90 | c (só reduz) | 20d §5, SEG-11; ADR-0040 | ED-06 |
| `sec.single_session` | sim; não | não | d | 20d §5, SEG-15 | ED-06 |

**Perguntas de 20b resolvidas como parâmetro (v2.15).**

| Chave | Opções | Padrão de fábrica | Cl. | Origem | Fase |
|---|---|---|---|---|---|
| `fiscal.central_billing_invoice_mode` | `PER_UNIT` (NFAg-e por unidade; fatura agrupadora só como documento de cobrança); `PER_CENTRAL_INVOICE` (NFAg-e única por fatura agrupadora) | `PER_UNIT` (confirmado pelo dono em 21/10/2026); a segunda opção só depois do manual e do XSD da NFAg-e (plano §2.4) | b | 20b Q5, FAT-83 | ED-22 |
| `bill.field_unreturned_rule` | `DIVERGENCE_QUEUE` (fila de divergências do ADR-0031, com decisão humana); `CANCEL_AND_REBILL` (cancela e refatura no servidor com a leitura disponível); `KEEP_AND_REISSUE` (mantém a pré-faturada e emite 2ª via no servidor) | `DIVERGENCE_QUEUE` (confirmado pelo dono em 21/10/2026) | b | 20b Q6; ADR-0031 | ED-19 |

Travados, fora de bifurcação (classe a ou c): vedações de corte por inadimplência da Lei 14.015; piso da tarifa social
da Lei 14.898 e vedação de perda do benefício por número de cortes; histórico de 120 meses; declaração anual de
quitação (Lei 12.007); bloco de qualidade da água (Decreto 5.440); aviso de negativação com prova.

Travados acrescentados na v2.15 (20b §5.2, 20c §5.2, 20d §5): concessão e manutenção do benefício social sem
condição de adimplência e perda só depois de aviso de pelo menos 3 meses na fatura (Lei 14.898); conta com revisão
aberta ou contestada fora de acréscimo, aviso de corte, corte e negativação (NR 11 arts. 37–38; Lei 14.015);
mensagens legais sempre impressas, antes das demais; créditos realizados devolvidos ao saldo quando a conta é
cancelada (CDC art. 42); diferença apurada em retificação sem multa nem juros; nenhuma conta apagada (NR 11 art. 22);
contestação do usuário aceita até a prescrição; religação sem taxa quando o aviso não foi provado (Lei 13.460 art. 6º
p.u.); revalidação do corte na execução, inclusive offline (quitação ou parcelamento, contestação, vedações de dia e
horário, aviso válido, classe protegida), com a OS cancelada se falhar (`cut.revalidate_on_execution`, 20d OS-07);
juros sem capitalização; abatimento proporcional dos juros na antecipação de prestações (20c COB-37); débito ajuizado
ou inscrito fora das ações automáticas; exclusão da negativação em até 5 dias úteis e expiração em 5 anos; o
prestador não recusa pagamento; protocolo nunca recusado por duplicidade nem por débito (Lei 13.460 art. 11); pedido
sem firma reconhecida nem cópia autenticada (Lei 13.726 art. 3º); usuário nunca removido, só inativado, com trilha
imutável.

## 4. Segurança e LGPD por tenant (decididos em 12/10/2026)

| Chave | Descrição | Sug. | Limite validado | Cl. | Código |
|---|---|---|---|---|---|
| `sec.mfa_roles` | papéis do tenant com MFA obrigatório (ADR-0007) | ADMIN, MANAGER, `registry.reveal-document` (`17` S4), padrão de fábrica | afrouxar exige aceite registrado e fica visível no console (ADR-0007 item 2); administração da plataforma sempre com MFA, fora do controle do tenant | b | novo; o realm não tem OTP nem WebAuthn (`infra/keycloak/realms/sigsan-realm.json`); só `users.mfa_enabled` por usuário (`platform/sigsan-platform/src/main/resources/db/migration/tenant/V1_1__create_tenant_schema.sql:41`) |
| `sec.cpf_mask_visible_digits` | dígitos visíveis do CPF (ADR-0008) | 3 | teto: no máximo 3 dígitos-base visíveis e nenhum dígito verificador (ADR-0008 item 2); só reduz; subir o teto exige novo ADR | c | hoje 6 dígitos fixos (`shared/sigsan-core/src/main/java/br/com/sigsan/core/document/DocumentNumber.java:95`) |
| `sec.cnpj_mask_pattern` | padrão da máscara de CNPJ | padrão atual: só a ordem do estabelecimento (ADR-0008 item 1) | teto de dígitos: a definir (P0), considerando o empresário individual (ADR-0008); até lá o padrão atual fica travado | a | hoje fixo (`DocumentNumber.java:98`) |
| `sec.document_search_quota` | buscas por documento por usuário por hora (ADR-0008 item 3) | a definir (P0) | teto da plataforma: a definir (P0); só reduz; recusa ao exceder e alerta de volume anômalo por usuário e por tenant; papel próprio, documento completo e registro travados, sem opção de desligar | c | novo (busca aberta a leitura, `17` S1) |
| `log.access_retention_days` | logs de acesso (IP, porta, usuário) | 6 meses | piso 6 meses, fixo (Marco Civil art. 15, `10`); a aplicabilidade ao prestador e ao Ziip continua a confirmar (`10` LOG-01 e §(e) pergunta 7, `13` pergunta 8), sem afastar o piso; só amplia | c | novo |
| `log.audit_retention_months` | trilha de negócio (`audit_log`) | sem | piso: maior prazo de G6 que ela comprova; só amplia | c | novo; `audit_log` sem partição nem expurgo (`V1:43`) |
| `log.document_search_retention_months` | registro de busca e revelação | sem | piso: `log.access_retention_days`; só amplia | c | novo |

Duração de sessão e de token ficam no realm único (`sigsan`; `ziipsan` depois da P0.33) e não são parâmetro do tenant.
Política de senha, bloqueio por tentativas e inativação por não uso são parâmetros do tenant (`sec.password_*`,
`sec.lockout_attempts`, `sec.user_inactivity_days`, na seção de bifurcações), aplicados por autenticador próprio no
realm único e testados a cada atualização do Keycloak (ADR-0040).

---

## 5. Fluxo de implantação

Ordem no assistente do console da plataforma (P1). **B** = bloqueia o go-live do tenant se faltar versão aceita.

1. Natureza jurídica e encarregado nomeado. **B**
2. Perfil regulatório (G1: ERI, norma, adesão às NR, área). **B**
3. Segurança (§4). **B**
4. Tarifa e tarifa social com simulação aprovada (G2). **B** no P1
5. Feriados do ano corrente e do seguinte por município; medição e fatura (G1). **B** no P2
6. Encargos (G5) simulados sobre competência anterior; retenção (G6) das categorias obrigatórias. **B** no P2
7. Arrecadação (G4): um convênio com arquivo de retorno de teste aceito. **B** no P3
8. Corte e religação (G9): não bloqueia o faturamento; **bloqueia toda OS de corte** enquanto faltar.
9. Aceite final do conjunto (hash) por representante do prestador.

Parâmetro opcional ausente (Pix, Pix Automático, cobrança híbrida, negativação) desliga a função. Parâmetro de classe b
que saiu do padrão aparece destacado no aceite final, com o alerta de risco quando houver.

---

## 6. Modelo de dados proposto

Três tabelas na faixa `V1_5` (antecipada para o P0, ADR-0002), mais o envelope nas tabelas de domínio.

- **`public.parameter_definitions`** (produto, só leitura para a aplicação): chave, grupo, classe (§2), JSON Schema do
  valor, obrigatoriedade por fase, data de referência da leitura, sugestão ou padrão de fábrica com fonte.
- **`public.parameter_legal_limits`** (produto): chave, expressão do limite (piso, teto, limite de fábrica, sentido
  permitido), norma, vigência do limite, teste associado.
- **`parameter_versions`** (tenant, RLS FORCE): `key`, `value` JSONB validado pelo schema, `valid_from`, `valid_to`,
  `version`, `status` (DRAFT, SIMULATED, PENDING_APPROVAL, ACTIVE, SUPERSEDED, REJECTED), `requested_by`,
  `approved_by` (CHECK de diferença), `justification`, `simulation_ref`, `content_hash`; EXCLUDE por
  `(tenant_id, key, daterange)` nas ativas.
- **`parameter_acceptances`** (tenant): conjunto aceito, usuário, cargo, data, `content_hash`, citação normativa, anexo;
  na classe b fora do padrão, também o alerta exibido.

**Leitura.** `ParameterResolver.at(key, referenceDate)` devolve valor tipado e `version_id`, ou lança
`MissingParameterException`. Os motores continuam puros; o serviço grava os `version_id` no resultado (na conta, ao
lado de `tariff_snapshot`; no pagamento; na OS de corte, junto do feriado consultado). Data de referência: competência
para tarifa e média; data planejada para corte; para encargos, vencimento ou pagamento é escolha a confirmar com o
jurídico do prestador.

**O que já existe, espalhado:**

| Onde | O que tem | Falta |
|---|---|---|
| `tenant_catalog.plan`, `.settings` (`V1:28-29`) e `tenant_metadata.settings` (`V1_1:17`) | JSONB livre, gravado no provisionamento e não lido por nenhum código | vigência, schema, aceite; não usar para regra |
| `tenant_settings` (`V1:107-122`) | chave/valor por tenant | vigência e aprovação; fica para configuração técnica e de apresentação, sem vigência nem aceite: `registry.*`, marca do tenant `branding.*` (ADR-0016, `MARCA-E-TEMAS.md` §3.1) e tema `theme.*` (ADR-0017, §4.4) |
| `RegistrySettingsService.java:28-31, 58-63` (`commercial/sigsan-registry`, pacote `application`) | `useBlockFace`, `requireReadingSequence`, `requireUserDocument`, `economiesSoftLimit` | operacionais; ficam em `tenant_settings` |
| `tariff_versions`, `tariff_social_bands`, `tax_withholding_rates` (`V4_1:45-65, 124-140, 147-162`) | vigência com EXCLUDE | aprovação, aceite, validação do piso social |
| `collection_policies` (`V6_1:193-236`) | vigência, norma-fonte, `fine_cap_justification` | EXCLUDE (só índice único em `valid_from`, `V6_1:236`), aprovação, aceite reforçado da multa acima de 2% |
| `collection_agreements`, `holidays`, `surcharge_indexes` (`V6_1:23-49, 242-274, 173`) | convênio com vigência, feriados, índices | EXCLUDE no convênio; feriados vão para `V1_5`; aceite |
| `lgpd_retention_schedule` (`V2_5:138-158`) | prazo, âncora, `legal_hold_reason` | executor, aceite, vínculo às categorias de G6, recusa abaixo do piso |
| `LateFeePolicy` (`commercial/sigsan-billing/src/main/java/br/com/sigsan/commercial/billing/bill/LateFeePolicy.java:22-24`) e `SurchargePolicy` (collection) | duas políticas de encargos; a do billing não tem tabela nem trava de 2% e admite multa por dia (`FineMode.PER_DAY`) | unificar numa só, lida de `collection_policies`, com padrão de 2% e aceite reforçado acima (ADR-0015) |
| `ExpectedReadingRange` (billing, `consumption/ExpectedReadingRange.java:37-51`) e `ExpectedRangePolicy` (metering) | faixa de leitura esperada fixa no billing e parametrizada na medição | usar só a da medição |

---

## 7. Pendências que continuam sendo do produto

1. **Canais suportados.** Hoje: arrecadação FEBRABAN 150 v08 e boleto de cobrança. A decidir: lista de bancos com
   manual de campo livre conferido (diário: ~20 de varejo), CNAB 240 V11.0 final, CNAB 400 só sob demanda, PSPs Pix
   homologados, data do Pix Automático. Débito automático FEBRABAN 150 só para autorizações legadas migradas
   (ADR-0028, item f, ajustado na v2.5): no P3, como requisito do go-live, se a escolha do piloto (plano P0.30)
   encontrar optantes; senão, na trilha do P4 (ED-27). Autorizações novas, só por Pix Automático.
2. **Perfis pré-carregados.** Há levantamento de dez ERIs (ARSAE-MG, ADASA, ARSESP, AGERGS, AGEPAR, ARES-PCJ,
   AGENERSA, AGR-GO, ARIS, ARCE); ARPE, AGEMS, AGER-MT, ARSAL e ARSEP não foram levantadas. Perfil é conjunto de
   sugestões com fonte; prioridade é a ERI do piloto.
3. **Catálogo de limites legais**, com teste por limite; o produto responde por limite permissivo demais (ADR-0002).
4. **Referências nacionais**: salário mínimo, feriados nacionais, IPCA, INPC e Selic, com fonte oficial.
5. **Monitor regulatório** (skill `monitor-regulatorio`) sobre DOU, ANA, BCB, FEBRABAN e ERIs dos clientes: gera nova
   vigência de limite ou sugestão e avisa os tenants afetados; nunca altera versão aceita.
6. **Assistente de implantação e simulador**, com o bloqueio do §5.
7. **Valores a definir** (ADR-0008 e ADR-0015):
   - P0: cota de busca por documento e teto da plataforma (`sec.document_search_quota`); teto de dígitos do CNPJ
     (`sec.cnpj_mask_pattern`); padrão e limites de fábrica da janela de corte (`cut.business_hours`); teto absoluto
     da multa acima de 2%, se houver (`fee.fine_pct`).
   - Antes da fase do parâmetro: conferência no DOU do piso da tarifa social (`social.variable_bands`); piso federal
     da parcela fixa e do esgoto (`social.fixed_part_discount_pct`, `social.sewer_discount`); pisos de
     `ret.social_base_days`, `ret.social_documents_years`, `ret.service_request_years`, `ret.cutoff_proof_years`;
     confirmação do piso de `ret.billing_after_closure_years`; prazo de rotação de backup (`ret.backup_disposal`).
   - Antes da fase do parâmetro (v2.15): `inst.down_payment_grace_days`; `neg.notice_min_days`, com o jurídico;
     percentual de `quality.noncompliance_compensation`.
