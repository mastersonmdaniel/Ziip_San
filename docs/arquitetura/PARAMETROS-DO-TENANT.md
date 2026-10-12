# Parâmetros do tenant

Data: 12/10/2026. Origem: ADR-0002, ADR-0015, plano `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` §2.3, relatórios
`docs/levantamento/08`, `10`, `11`, `13`, `14`, `17` e o código. Fases (ADR-0001): P1 tarifa social (R3), P2 ciclo
(R4), P3 arrecadação, corte e portal (R5a/R5b, R7), P4 cobrança e Pix Automático (R8, R5c) e, na trilha de editais,
emissão em campo e almoxarifado (ADR-0031, ADR-0032).

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
| `pay.classify_tolerance`, `pay.adjust_tolerance`, `pay.duplicate_winner` | tolerâncias e vencedor da duplicidade | R$, enum | sem | livre | d | FIN | P3 | `V6_1:213-215` |
| `pay.pix_provider` | PSP, chave Pix, referência da credencial no cofre, webhook | objeto | sem | Regulamento Pix (Res. BCB 1/2020); tenant do webhook vem do convênio | d | FIN | P3 | novo |
| `pay.hybrid_charge` | cobrança híbrida Pix + boleto | bool | desligado | facultativo, a partir de 01/02/2027 (Res. BCB 587/2026) | d | FIN | P3 | novo |
| `pay.pix_automatic` | habilitado, antecedência da instrução, valor máximo | objeto | desligado | débito em conta com recebedor PJ só por Pix Automático desde 13/10/2025 (Res. CMN 5.251/2025) | d | FIN | P4 | novo |

Travado (classe a): ajuste de conciliação com aprovação dupla (plano R5).

### G5 — Multa, juros e atualização

| Chave | Descrição | Tipo | Sug. | Limite | Cl. | Apr. | Fase | Código |
|---|---|---|---|---|---|---|---|---|
| `fee.fine_pct` | multa de mora, percentual único | % | 2,00, padrão de fábrica (convergência de 10 ERIs, `08` d.2) | até 2,00 com o aceite comum; acima de 2,00 exige norma citada, aprovação dupla, aceite formal registrado e exibe alerta de risco (CDC art. 52 §1º; aplicação a tarifa: G5, pergunta E-02); teto absoluto: a definir (P0) | b | JUR | P2 | `V6_1:202-204`, CHECK `V6_1:222-224` e `FineRule` (`commercial/sigsan-collection/src/main/java/br/com/sigsan/commercial/collection/surcharge/FineRule.java:27-31`) aceitam teto acima de 2% só com texto de justificativa (`fine_cap_justification`), sem aceite; `LateFeePolicy` (billing) sem trava; unificar (ADR-0015) |
| `fee.interest_mode`, `fee.interest_monthly_pct`, `fee.interest_month_basis_days` | juros simples pro rata die | enum, %, int | `SIMPLE_PER_DAY`; 1,00% a.m.; base 30 | sem capitalização, travado (o produto só oferece juros simples); teto a confirmar | d | JUR | P2 | `V6_1:205-208` |
| `fee.correction_*` | modo, índice, meses mínimos, aceita negativa | enum, código | sem (Selic, INPC, IPCA ou índice do reajuste, conforme ERI) | ERI ou contrato | d | JUR | P2 | `V6_1:209-212`; fatores em `surcharge_indexes` (`V6_1:173`) |
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
| `ret.billing_after_closure_years` | guarda após o encerramento do vínculo | anos | 10 | piso 10 anos, a confirmar: prescrição decenal (REsp 1.117.903, tema não confirmado, `13`); repetição de indébito 10 anos (Súmula 412); só amplia | c | JUR | P2 | novo |
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
| `rec.urgent_fee`, `rec.requires_settlement` | preço da urgência; exige quitação | R$, bool | sem | ERI | d | OPE+JUR | P3 | novo |
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

---

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
