# 23b — Revisão do modelo de dados e dos cadastros

Revisão campo a campo das entidades centrais do Ziip SAN, em 28/10/2026. Compara o que o código e as migrations têm,
o que o plano e os ADRs exigem e o que a regulação pede. Nenhum código, plano ou ADR foi alterado; as propostas são
recomendações e não mudam decisões do dono do produto.

## 0 Leitura rápida

1. O cadastro ainda grava direto: `PUT /consumer-units/{id}` muda situação de água e esgoto, economias, vínculos e vencimento, e o histórico de situação nasce sem OS. Falta o mapa campo → tipo de OS que a P1.31 vai precisar (DAD-01).
2. A tela de cadastro coleta cerca de 30 campos que a API descarta sem erro (`fail-on-unknown-properties: false`), entre eles o endereço do cliente, o tipo de cliente e o vínculo macro-micro de condomínio (DAD-02).
3. O endereço não tem código IBGE do município. Sem ele não se preenche a NFAg-e, nem o IBS por destino, nem o feriado municipal (DAD-04).
4. A conta não grava em nome de quem foi emitida, e o consumo retificado sobrescreve a linha anterior: o histórico de 120 meses da NR 11 depende hoje do `audit_log` (DAD-14, DAD-15).
5. Economias por subcategoria, situação da ligação e vínculo não têm vigência garantida no banco; o faturamento retroativo não tem como saber o valor vigente na competência (DAD-06, DAD-07, DAD-09).
6. O hidrômetro segue o modelo anterior à Portaria Inmetro 155/2022; marca, modelo, proteção e local de instalação são texto livre (DAD-11, DAD-12).
7. Débitos e créditos têm tipo e origem em texto livre, o que inviabiliza a migração de parcelamentos prevista na P2.10 sem coluna de origem (DAD-20).
8. Não há classificação LGPD por campo. A coordenada do imóvel está isenta da redação na auditoria com justificativa que contraria o GIS (DAD-29).
9. Tarifa social, parcelamento, situação especial, protocolo, documento fiscal, ativo e ocorrência de rede ainda não têm tabela; a seção 3 traz o dicionário-alvo de cada um.
10. A seção 2 tem 40 achados (DAD-01 a DAD-40), com gravidade e tarefa do plano onde encaixam.

## 1 Método

### 1.1 Fontes

- **Código.** Todas as migrations de tenant e de core (`**/db/migration/**/V*__*.sql`, 23 arquivos), lidas por inteiro as
  de `registry` (`V3_1` a `V3_3`), `billing` (`V4_1`), `metering` (`V5_1`, `V5_2`), `collection` (`V6_1`), `platform`
  (`V1_1` a `V1_4`), core (`V1`, `V2`) e `lgpd` (`V2_5`); as de `gis`, `losses`, `quality` e `mtr` por busca dirigida.
  Entidades JPA (`@Table`), DTOs e serviços do `sigsan-registry`; tipos e formulários do cadastro em `frontend/src`
  (`services/registry/types.ts`, `pages/cadastro/customerSchema.ts`, `pages/cadastro/propertyForm.ts`);
  `AuditRedactionCoverageTest` e `application.yml`.
- **Plano e decisões.** `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` por busca (mapa Flyway, P1.3, P1.5, P1.31, P2.5, P2.6, P2.10,
  P2.20, P2.23, P3.4, P3.30, ED-13, ED-14, ED-22, ED-56, ED-92); ADR-0002, 0022, 0031, 0034, 0038, 0039, 0040, 0041;
  `PARAMETROS-DO-TENANT.md`, `GIS-CADASTRO-TECNICO.md`, `ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS.md`,
  `CERTIFICACOES-DE-QUALIDADE.md`, `FISCAL-PORTE-JAVA.md` (§3).
- **Levantamentos.** `gsan/20a` a `20e` (regras e propostas por entidade), `lacunas/22a` e `22b` (achados CAD e OPE),
  `07` (dicionário de telas S1 a S8), `08`, `11` e `13` (requisitos de cadastro da NR 11).
- **Regulação.** `docs/normas/catalogo-normas.json` para conferir número, situação e id; textos das normas não foram
  relidos nesta rodada.

### 1.2 Como se leu

Para cada entidade, montou-se a lista de colunas do código, cruzou-se com as propostas dos levantamentos 20a a 20e e
dos ADRs e com os requisitos regulatórios, e registrou-se o campo-alvo. Os achados dos relatórios 22a e 22b não são
repetidos: quando um campo-alvo vem deles, a origem cita o ID (por exemplo, 22a CAD-67) e esta revisão acrescenta só o
detalhe de campo que eles não trouxeram (tipo, regra, chave, vigência).

### 1.3 Convenções das tabelas

- **Colunas comuns omitidas.** `id` (UUID), `tenant_id`, `created_at`, `updated_at`, `created_by`, `updated_by`,
  `version` e `tenant_schema` (as duas últimas criadas por `register_tenant_table`, `V1_2`) existem em toda tabela de
  tenant e não se repetem.
- **Campo.** Nome técnico-alvo em inglês. Quando o nome atual difere, aparece "alvo ← atual".
- **Tipo.** PostgreSQL. `enum(...)` indica `VARCHAR` com `CHECK`; `FK x` indica chave estrangeira composta
  `(tenant_id, id)`, padrão do código.
- **Obr.** S = obrigatório; N = opcional; C = condicional (a regra diz quando).
- **Origem.** N11 = `ana-nr-11-res-230-2024`; N13 = `ana-nr-13-res-271-2025`; N9 = `ana-nr-9-res-211-2024`;
  N16 = `ana-nr-16-res-302-2026` (MCPSB); P155 = `inmetro-portaria-155-2022`; LGPD = `lei-13709-2018`;
  L14898 = `lei-14898-2024`; L13460 = `lei-13460-2017`; CDC = `lei-8078-1990`; NFAG = `svrs-nfag-e-modelo-75`,
  `moc-nfag-modelo-75` e `nt-2026-00x-rtc-nfag`. Artigos da N11 sobre cadastro vêm dos levantamentos 08 (R-01), 11 e 13
  (CD-01, RT-01), porque o catálogo declara leitura parcial do texto (seção 4).
- **Situação.** "existe" (código atual); "existe (divergente)" (existe com nome, tipo ou regra diferente do alvo);
  "planejado" (com a migration ou tarefa); "faltando" (nem código nem plano no nível do campo).
- **LGPD** (na observação). ID = identificação; CT = contato; LOC = localização; FIN = financeiro; SOC =
  socioeconômico; SENS = dado sensível (art. 11). REDACT e OMIT indicam a regra atual em `audit_redaction_rule`.

## 2 Quadro-resumo de achados

Gravidade: **C** crítica (viola decisão ou norma e bloqueia tarefa), **A** alta, **M** média, **B** baixa.

| ID | Entidade | Tipo de achado | Grav. | Proposta | Tarefa |
|---|---|---|---|---|---|
| DAD-01 | Cliente, unidade, vínculo | Viola decisão (ADR-0039) | C | Mapa campo → tipo de OS (seção 3.2); `service_order_id NOT NULL` em `unit_status_history` e nos históricos novos; escrita direta dos campos com efeito de faturamento recusada até a P1.31 | P1.31, P1.3 |
| DAD-02 | Cliente, unidade (tela × API) | Campo coletado e descartado | A | Recusar propriedade desconhecida nas rotas de escrita do cadastro (ou retirar o campo da tela até a `V3_6`); teste de contrato tela × DTO | P1.3, P1.11 |
| DAD-03 | Unidade | Sobra | M | Remover `tariff_type_id` e `meter_id` (órfãos do `V1_1`) e `properties` JSONB; hidrômetro só por `meter_installations` | P1.3 (`V3_6`) |
| DAD-04 | Endereço | Falta de chave e de domínio | A | `municipality_ibge CHAR(7)` com CHECK e FK a catálogo IBGE; CHECK em `type`, `state` e `cep` | P1.3, P2.6 |
| DAD-05 | Cliente, endereço | Falta (norma) | A | Endereço de correspondência do cliente com tipo e vigência; a tela já envia `addresses[]` | P1.3 |
| DAD-06 | Vínculo | Texto livre, falta de histórico, decisão | A | FK de motivo de fim; `opened_by_service_order_id` e `closed_by_service_order_id`; EXCLUDE de vigência; papéis aceitos no nome da conta por parâmetro | P1.3, P1.31 |
| DAD-07 | Economias | Falta de vigência | A | `valid_from`/`valid_to` com EXCLUDE e `service_order_id` em `unit_subcategories` | P1.3 (`V3_6`) |
| DAD-08 | Categoria | Falta de domínio regulatório | M | `categories.regulatory_class` (classes mínimas da N13 e do SINISA) | P1.3, P2.5 |
| DAD-09 | Ligação | Duas fontes de verdade | A | `V3_7` move a situação para `service_connections`; catálogo único com `kind` e `system_role`; histórico da ligação substitui `unit_status_history` | P2.23, ED-13 |
| DAD-10 | Ligação | Falta (norma) | M | Data de início do serviço (`connected_on`) com destino na carga do legado | P1.2, P2.23 |
| DAD-11 | Hidrômetro | Desacordo com norma; texto livre | A | Campos da P155 (classe 1 ou 2, Q1 a Q4, modelo aprovado, verificações); catálogos de marca e modelo; local de armazenagem e motivo de baixa | ED-14 |
| DAD-12 | Instalação do hidrômetro | Falta de FK; texto livre | M | Instalação ligada à ligação; OS de instalação e de retirada; proteção e local como catálogo; lacre num lugar só | P2.23, ED-14 |
| DAD-13 | Usuário (FK transversal) | Falta de chave | M | `UNIQUE (tenant_id, id)` em `users` para permitir FK composta (`reader_id`, executor, aprovador) | P0.13 |
| DAD-14 | Consumo | Falta de histórico | A | Versão por linha ligada à versão da conta; versões de parâmetro gravadas | P2.5, P2.20 |
| DAD-15 | Fatura | Falta (norma) | A | `billed_customer_id` e retrato cifrado do titular na emissão | P2.5 |
| DAD-16 | Fatura | Texto livre; falta de histórico | M | Motivos por catálogo; `protocol_id`, `service_order_id`; `bill_status_events` | P2.20 (`V4_8`) |
| DAD-17 | Fatura, item | Falta (fiscal) | A | `fiscal_document_id`, classificação fiscal do item, IBS e CBS por item; CHECK do total com tributo | P2.6, `V4_5`, `V4_6` |
| DAD-18 | Documento fiscal | Falta | A | Dicionário da seção 3.13; pendências do MOC listadas na seção 4 | P2.6 (`V4_7`) |
| DAD-19 | Pagamento | Falta de FK; acoplamento a leiaute | M | Colunas do FEBRABAN 150 em tabela de detalhe; referência de arrecadação; vínculo com documento fiscal | P3.1, P3.2 |
| DAD-20 | Débito e crédito | Texto livre; falta de origem | A | Catálogos `debit_types` e `credit_origins`; `source_type`/`source_id`, `service_order_id`, mês contábil, prazo | P2.5, P2.10 |
| DAD-21 | Parcelamento | Falta | M | Dicionário da seção 3.16; a P2.10 usa as colunas de origem do DAD-20 | P4.1, P2.10 |
| DAD-22 | Situação especial | Falta; FK ausente | M | Dicionário da seção 3.17; `special_situation_id` no consumo | ED-22 |
| DAD-23 | Tarifa social | Falta (norma) | A | Dicionário da seção 3.18 (inscrição com fonte, validade do CadÚnico, aviso de perda) | P1.5 (`V4_2`) |
| DAD-24 | Protocolo | Falta; ordem de dependência | M | Tabela mínima de protocolo junto do núcleo de OS, ou FK adiada com coluna reservada | P1.31, P3.4 |
| DAD-25 | OS | Chave errada; texto livre | A | `service_orders` do `V1_1` refeita com solicitante pelo protocolo, situação e prioridade por domínio e alvos em tabela própria | P1.31 (`V9_1`) |
| DAD-26 | Ativo | Duas fontes de verdade | M | Regra única: códigos MCPSB no `asset`; feição e hidrômetro referenciam o ativo | ED-56, ED-14 |
| DAD-27 | Ocorrência de rede | Sobreposição com tabela existente | M | `leak_detection` passa a achado de pesquisa ligado à ocorrência e à OS; reparo só por OS | ED-92 |
| DAD-28 | Norma | Texto livre; falta de vigência | M | Referência ao id do catálogo nas tabelas com vigência; datas de vigência estruturadas no catálogo | P1.34, P0.19 |
| DAD-29 | Transversal (LGPD) | Dado pessoal sem classificação | A | Catálogo `data_classification` por tabela e coluna; rever a isenção de `consumer_units.coordinates` | P0.11, ED-74, P3.10 |
| DAD-30 | Consentimento | Falta de FK; texto livre | M | `UNIQUE (tenant_id, id)` em `lgpd_consent`; FK de `customer_contacts.consent_id`; finalidade por catálogo | P1.3, ED-41 |
| DAD-31 | Usuário e permissão | Sobra; texto livre | M | Retirar `password_hash` e `mfa_secret`; `status` por domínio; permissões pelo catálogo `recurso:ação` | P0.13, P3.30 |
| DAD-32 | Tenant | Duplicidade; falta | M | Fonte única do tenant; natureza jurídica, emitente fiscal e encarregado | P1.6, P0.19, P2.6 |
| DAD-33 | Parâmetro | Classe errada de configuração | M | `registry.require_user_document` como parâmetro com vigência e aceite | P0.19 |
| DAD-34 | Transversal | Nomes divergentes | B | Glossário de nomes e renomeação na `V3_6` (seção 3.28) | P1.3 |
| DAD-35 | Rede, perdas | Unicidade fora do tenant | M | `UNIQUE (tenant_id, code)` em `dma_district` e `leak_detection` | P1.26 (`V13_2`) |
| DAD-36 | Território | Falta de FK | M | `reading_routes.sector_id` e `billing_group_id`; rede da quadra como domínio | P1.3, P2.5 |
| DAD-37 | Unidade (condomínio) | Falta de FK | M | `parent_unit_id` com tipo de relação; o consumo já tem o registro macro | P1.3, ED-15 |
| DAD-38 | Cliente, unidade | Duplicidade | B | Uma fonte do dia de vencimento, com vigência e OS `ALTERNATIVE_DUE_DAY` | P1.3, P2.5 |
| DAD-39 | Cliente | Falta de domínio | M | `customer_type_id` e `government_sphere` (órgão público, condomínio) | P1.3 (`V3_6`) |
| DAD-40 | Documento anexo | Texto livre; falta de retenção | M | Tipo por catálogo, classe de retenção, classificação e imutabilidade | P1.20, P1.24 |

## 3 Dicionário de dados-alvo por entidade

### 3.1 Cliente (`customers`) e satélites

Tabela atual: `customers` (`V3_1:179-202`), entidade `Customer`, DTO `CustomerInput`. A tela (`customerSchema.ts`) envia
campos que o DTO não tem; o Jackson está configurado para ignorá-los (`application.yml:21`).

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `person_type` | enum(`PF`,`PJ`) | S | CHECK | 20a; `V3_1` | existe | Órgão público e condomínio não são tipo de pessoa (DAD-39) |
| `customer_type_id` | uuid FK `customer_types` | S | tipo compatível com `person_type` | 07 S4; `V3_6` | planejado (P1.3) | A tela envia `customerTypeId`; a API descarta |
| `government_sphere` | enum(`FEDERAL`,`STATE`,`MUNICIPAL`,`NONE`) | C | obrigatório se o tipo for de ente público | 20c COB-20, FIN-05; ED-30 | faltando | Prescrição, emissão separada e exclusão de negativação por esfera |
| `name` | text(200) | S | mínimo de letras e nomes genéricos por parâmetro | N11 art. 22; 20a CAD-25 | existe | LGPD ID, REDACT. Alteração por OS `CUSTOMER_DATA_UPDATE` |
| `social_name` | text(200) | N | só PF | 22a CAD-67 | planejado (22a) | LGPD ID, REDACT |
| `trade_name` | text(200) | C | nulo em PF | 20a | existe (divergente) | Falta CHECK `person_type = 'PJ' OR trade_name IS NULL` |
| `document_ciphertext`, `document_hash`, `document_masked` | text, text(64), text(30) | C | DV; CNPJ alfanumérico; hash único entre ativos | N11 art. 22 I "b"; L13460 art. 10-A; ADR-0008 | existe | LGPD ID; OMIT e REDACT. Obrigatoriedade para o usuário titular depende de parâmetro (DAD-33) |
| `document_exemption_reason_id` | uuid FK catálogo | C | obrigatório sem documento; exige permissão | L13460 art. 10-A §3º | faltando | A tela envia `documentExemptionReason`; descartado |
| `birth_date` | date | N | data passada | 07 S4 | existe | LGPD ID, REDACT. Declarar finalidade (prioridade por idade) |
| `rg_ciphertext`, `rg_issuer_id`, `rg_state` | text, uuid, char(2) | N | UF válida | ED-12 | faltando | A tela envia `rg`, `rgState`, `rgIssueDate`; descartados. Não exigível (L13460 art. 10-A) |
| `mother_name_ciphertext` | text | N | | ED-12 | faltando | A tela envia `motherName`; descartado. LGPD ID |
| `activity_branch_id` | uuid FK `activity_branches` | C | PJ | N11 art. 22 (08 R-01); 07 S4 | faltando | A tela envia; descartado. Ver ponto 4.3 |
| `parent_customer_id` | uuid FK `customers` | N | sem ciclo | 20a CAD-22; ED-22 | faltando | A tela envia; descartado. Órgão centralizador |
| `tax_withholding_profile_id` | uuid FK | N | vigência; alteração por OS `CUSTOMER_TAX_PROFILE_CHANGE` | 20b FAT-86 | faltando | `bill_taxes` não sabe hoje a quem se aplica a retenção |
| `due_day` | smallint | N | 1 a 31 | 20b | existe (divergente) | Duplica `consumer_units.due_day` (DAD-38) |
| `deleted_at`, `deleted_reason` | timestamptz, text(200) | N | | `V3_1` | existe (divergente) | Motivo em texto livre (REDACT); usar catálogo |
| `anonymized_at` | timestamptz | N | só com pedido de titular atendido | LGPD art. 18 IV | faltando | Anonimizar mantendo FK e o retrato fiscal já emitido |

**Contatos (`customer_contacts`, `V3_1:205-224`).**

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `kind` | enum(`EMAIL`,`PHONE`) | S | CHECK | 20a | existe | |
| `value` | text(255) | S | e-mail RFC 5322; telefone E.164 | N11 art. 22 I "b" | existe (divergente) | Validação só no DTO; sem CHECK. LGPD CT, REDACT |
| `phone_type` | enum(`MOBILE`,`LANDLINE`,`COMMERCIAL`,`WHATSAPP`) | C | se `kind = PHONE` | 07 S4 | faltando | Hoje vai para `label`, texto livre |
| `extension` | text(10) | N | dígitos | 07 S4 | faltando | A tela envia; descartado |
| `contact_name` | text(100) | N | | 07 S4 | faltando | A tela envia; descartado. Dado de terceiro (LGPD ID) |
| `label` | text(50) | N | | `V3_1` | existe | Sobra quando `phone_type` existir |
| `primary_contact` | bool | S | um principal por tipo (índice parcial) | 07 | existe | |
| `consent_id` | uuid FK `lgpd_consent` | C | quando a base legal for consentimento | LGPD art. 7º I | existe (divergente) | Sem FK (DAD-30) |
| `valid_from`, `valid_to` | date | S/N | sem sobreposição do principal | 20a CAD-19 | faltando | Prova de qual contato valia na data do aviso de corte |
| `verified_at`, finalidades | — | — | — | 22a CAD-84 | planejado (22a) | Não repetido aqui |

**Representante (`customer_representations`).** Proposta do 22a CAD-66 sem nível de campo; detalhe abaixo.

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `customer_id` | uuid FK `customers` | S | representado | 22a CAD-66 | planejado (`V3_6`) | |
| `representative_customer_id` | uuid FK `customers` | S | diferente do representado | 22a CAD-66 | faltando | O representante é cliente cadastrado, com documento cifrado |
| `kind` | enum(`ATTORNEY`,`GUARDIAN`,`CURATOR`,`CONDO_MANAGER`,`ESTATE_ADMINISTRATOR`,`LEGAL_REPRESENTATIVE`,`AUTHORIZED_THIRD_PARTY`) | S | | 22a CAD-66, CAD-65 | faltando | |
| `powers` | text[] de domínio (`VIEW`,`REQUEST`,`PAY`,`CHANGE_BILLING`,`OWNERSHIP`) | S | ao menos um | 22a CAD-66 | faltando | Escopo do acesso delegado no portal |
| `unit_id` | uuid FK `consumer_units` | N | nulo = todas as unidades do representado | | faltando | |
| `valid_from`, `valid_to` | date | S/N | | | faltando | |
| `proof_document_id` | uuid FK `documents` | S | | `registry.representation_proof` | faltando | |
| `service_order_id` | uuid FK `service_orders` | S | OS `REPRESENTATIVE_SET` | ADR-0039 | faltando | |

**Consentimentos (`lgpd_consent`, `V2_5:15-49`).** Existe; problemas no nível de campo: `subject_id` e `subject_type`
(texto) sem FK nem domínio; `purpose` em texto livre (VARCHAR 200), quando o 22a CAD-84 e a ED-41 pedem finalidade por
catálogo; `geolocation` JSONB e `ip_address` são dado pessoal do próprio consentimento; a tabela não tem
`UNIQUE (tenant_id, id)`, o que impede FK composta a partir de `customer_contacts`. Campo-alvo adicional:
`purpose_id` (FK catálogo de finalidades, com base legal padrão e prazo de retenção), `channel` (enum do canal de
coleta) e `evidence_document_id`.

**Documentos do cliente.** Ficam em `documents` (seção 3.25 trata do anexo genérico).

**Problemas.**

- Campos coletados e descartados: `customerTypeId`, `documentExemptionReason`, `rg`, `rgState`, `rgIssueDate`,
  `motherName`, `activityBranchId`, `parentCustomerId`, `addresses[]`, `billing.nextMonthDue`, `billing.advanceInvoice`,
  `billing.blockNegativation`, `phones[].extension`, `phones[].contactName` (DAD-02). Os três indicadores de `billing`
  não devem virar coluna do cliente: o 20a CAD-20 manda deixá-los na cobrança.
- `person_type` com só PF e PJ, e `public_entity` na categoria do imóvel, deixam sem lugar o órgão público e a esfera
  de poder (DAD-39).
- `deleted_reason` em texto livre; exclusão lógica libera o documento (índice parcial), sem regra de anonimização.

### 3.2 Unidade consumidora (`consumer_units`)

Tabela criada no `V1_1:84-108` e corrigida no `V3_1:239-288`. Raiz do domínio (ADR-0039).

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `registration_number` ← `matricula` | text(50) | S | numérica; única por tenant; sequência (`V3_5`) | N11 art. 22; 20a | existe (divergente) | Nome em português; hoje gerada por `MAX + 1` com trava (`ConsumerUnitService:509-517`) |
| `check_digit` ← `dv` | char(1) | S | módulo 11 | 20a | existe (divergente) | DTO usa `registrationDv` |
| `block_id`, `lot`, `sublot` | uuid FK, int, int | C | inscrição única entre ativas; lote e sublote com quadra | 20a CAD-01 | existe | Alteração de inscrição por `inscription_changes` (20a CAD-11) |
| `block_face_id` | uuid FK `block_faces` | C | se `registry.use_block_face` | 20a CAD-02; `V3_6` | planejado | A tela envia `blockFaceId`; descartado |
| `address_id` | uuid FK `addresses` | S | | `V3_1` | existe | Ver seção 3.4 |
| `coordinates` | geometry(Point,4674) | N | dentro da área de atendimento | GIS §11 | existe (divergente) | Isenta da redação (DAD-29). LGPD LOC |
| `status` | enum(`ACTIVE`,`PROVISIONAL`) | S | | ADR-0039 item 3 | existe | Ciclo do cadastro |
| `occupancy_status` | enum(`OCCUPIED`,`VACANT`,`UNKNOWN`) | S | `VACANT` só sem usuário vigente | 22a CAD-68 | faltando | Recomenda-se coluna própria em vez de novo valor em `status`, que descreve o cadastro, não a ocupação |
| `property_profile_id` | uuid FK `property_profiles` | S | perfil provisório só com `status = PROVISIONAL` | 20b FAT-76; `V3_6` | planejado | A tela envia `profileId`; descartado. Alteração por OS `UNIT_PROFILE_CHANGE` |
| `water_status_id`, `sewer_status_id` | uuid FK | C | compatível com a rede da quadra | 20a | existe (divergente) | Migram para a ligação (DAD-09) |
| `reading_route_id`, `reading_sequence` | uuid FK, int | C | sequência se `registry.require_reading_sequence` | 20a CAD-10 | existe | |
| `built_area_m2` | numeric(10,2) | C | ≥ 0 | 07 S2 | existe | Alteração por OS `UNIT_PROFILE_UPDATE` |
| `due_day` | smallint | N | 1 a 31 | 20b | existe (divergente) | DAD-38 |
| `sewer_discharge_type_id` | uuid FK | C | se a situação de esgoto fatura | 20a CAD-44 | faltando | A tela envia `sewerDischargeId`; descartado |
| `water_source_id`, `well_type_id` | uuid FK | N | | 22a CAD-69; 07 | faltando | A tela envia; descartado |
| `parent_unit_id`, `parent_relation` | uuid FK, enum(`CONDOMINIUM_MACRO`,`ANNEX`) | N | mesma quadra; sem ciclo | 20b FAT-78; 07 S2 | faltando | DAD-37 |
| `municipal_property_id` | text(30) | N | único por município | decisoes-a-rever lote 1 item 6; 22a CAD-103 | faltando | A tela envia `iptu`; descartado. LGPD ID indireto |
| `usage_points`, `residents` | int | N | ≥ 0 | 07 S2 | faltando | `residents` é dado da família: declarar finalidade antes de coletar |
| `delivery_mode_id`, `delivery_route_id`, `delivery_sequence` | uuid FK, uuid FK, int | C | rota de entrega ativa | 07 S2; 20b | faltando | A tela envia; descartado |
| `notes` | text(750) | N | | `V3_1` | existe | REDACT |
| `deleted_at`, `deleted_reason` | timestamptz, text(200) | N | encerra vínculos vigentes | 20a CAD-17 | existe | Motivo em texto livre (REDACT, `V3_3`) |
| `tariff_type_id` | uuid | — | — | `V1_1` | sobra | Sem FK, sem tabela, não mapeado (DAD-03) |
| `meter_id` | uuid | — | — | `V1_1` | sobra | Contradiz `meter_installations` (DAD-03) |
| `properties` | jsonb | — | — | `V1_1` | sobra | Mapeado e sem uso; convida a dado sem esquema |

**Mapa campo → tipo de OS (ADR-0039 itens 4, 6 e 8).** Os nomes de tipo vêm do 20b e do 20d §3.3. A coluna
"aprovação" é o padrão de fábrica do ADR-0039 item 8.

| Campo ou grupo | Tipo de OS | Aprovação por outra pessoa | Vigência do efeito |
|---|---|---|---|
| `customer_contacts.*` | `CUSTOMER_CONTACT_UPDATE` | não | imediata |
| `customers.name`, documento, `social_name` | `CUSTOMER_DATA_UPDATE` | sim | imediata |
| `unit_customers` (abrir, encerrar, trocar papel) | `OWNERSHIP_TRANSFER` | sim | data do vínculo |
| `unit_subcategories.*` | `ECONOMIES_CATEGORY_CHANGE` | sim | competência seguinte |
| `property_profile_id`, `built_area_m2` | `UNIT_PROFILE_CHANGE`, `UNIT_PROFILE_UPDATE` | sim | competência seguinte |
| `water_status_id`, `sewer_status_id` | OS de ligação, corte, religação, supressão, tamponamento | sim | encerramento da OS |
| `due_day` | `ALTERNATIVE_DUE_DAY` | sim | próxima emissão |
| `addresses` de entrega | `DELIVERY_ADDRESS_CHANGE` | não | próxima emissão |
| inscrição (`block_id`, `lot`, `sublot`) | alteração de inscrição (20a CAD-11) | sim | imediata |

**Problemas.**

- `ConsumerUnitService:541-542` grava `water_status_id` e `sewer_status_id` vindos do formulário, e
  `recordStatus(..., "Alteração do cadastro")` cria histórico sem `service_order_id`; o `PUT` também substitui
  vínculos (fim com a data de hoje) e economias (DAD-01). O ADR-0039 já registra o fato; o que falta é o mapa acima
  como contrato da P1.31.
- `unit_status_history.service_order_id` é nulo e sem FK; `service_orders` (`V1_1`) não tem `UNIQUE (tenant_id, id)`.
- `reason` em texto livre no histórico (REDACT).

### 3.3 Vínculo cliente-unidade (`unit_customers`)

Tabela `V3_1:294-321`.

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `unit_id`, `customer_id` | uuid FK | S | | ADR-0039 item 2 | existe | |
| `role_id` ← `role` | uuid FK `relation_roles` (com `system_role`) ← enum(`OWNER`,`USER`,`RESPONSIBLE`) | S | | ADR-0039 item 2 ("outros papéis do catálogo") | existe (divergente) | DTO chama de `relation` |
| `start_date`, `end_date` | date | S/N | início não futuro; fim ≥ início | 20a CAD-23 | existe | |
| período sem sobreposição | EXCLUDE gist `(tenant_id, unit_id, customer_id, role_id, daterange)` | S | | 20a CAD-23 | faltando | Hoje só o índice parcial `WHERE end_date IS NULL`; períodos históricos podem se sobrepor |
| `end_reason_id` ← `end_reason` | uuid FK `relation_end_reasons` ← text(200) | C | obrigatório com `end_date` | 20a CAD-24; 22a CAD-65 | existe (divergente) | Texto livre (REDACT) |
| `show_on_bill` | bool | S | exatamente um vigente; papel permitido por parâmetro | N11 art. 33 §2º; ADR-0039 item 2 | existe (divergente) | Aceita qualquer papel (`UnitConsistency:116`); a N11 manda a fatura sair em nome do usuário titular |
| `delivery_mode_id` | uuid FK | C | obrigatório se houver `RESPONSIBLE` | 07 S2 | faltando | |
| `opened_by_service_order_id`, `closed_by_service_order_id` | uuid FK `service_orders` | S/C | | ADR-0039 item 6 | faltando | |

**Problemas.** O papel é enum fixo, e o ADR-0039 prevê catálogo. A regra do nome na conta é bifurcação típica (ERI que
admite o responsável): pelo ADR-0038 vira parâmetro (sugestão: `registry.bill_name_roles`, padrão `USER`), e não
decisão desta revisão.

### 3.4 Endereço, localização e território

**Endereço (`addresses`, `V1_1:118-138`, ajustada no `V3_1:230-233`).**

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `entity_type`, `entity_id` | text(50), uuid | S | | `V1_1` | existe (divergente) | Polimórfico sem FK, e a unidade também aponta por `address_id`: duas formas de ligar. Alvo: tabelas de ligação (`customer_addresses`, `unit.address_id`) |
| `type` | enum(`PHYSICAL`,`BILLING`,`CORRESPONDENCE`,`ALTERNATIVE`) | S | CHECK | 20b | existe (divergente) | Domínio só em comentário e no enum Java |
| `cep` | char(8) | N | 8 dígitos | 07 S2 | existe (divergente) | `VARCHAR(10)` sem CHECK. REDACT |
| `street_id` / `street` | uuid FK `streets` / text(200) | S | | 22a CAD-83 | existe (divergente) | Texto; DTO chama de `streetName`. REDACT |
| `number` | text(20) | S | aceita "S/N" | 07 S2 | existe | REDACT |
| `complement` | text(100) | N | | | existe | REDACT |
| `neighborhood_id` / `neighborhood` | uuid FK / text(100) | N | | 22a CAD-83 | existe (divergente) | Texto livre |
| `municipality_ibge` | char(7) | S | 7 dígitos; FK a catálogo IBGE | NFAG (município do destinatário); `holidays` | faltando | `city` é texto. DAD-04 |
| `city` | text(100) | — | — | `V1_1` | sobra (após `municipality_ibge`) | Nome derivado do código |
| `state` | char(2) | S | UF válida | | existe (divergente) | Sem CHECK |
| `reference_point` | text(100) | N | | 07 S2 | faltando | |
| `coordinates` | geometry(Point,4674) | N | | | existe (divergente) | Coordenada também em `consumer_units`: duas fontes do ponto do imóvel. OMIT aqui, isenta lá |
| `is_primary` | bool | S | | | existe | |
| `valid_from`, `valid_to` | date | N | | 20b `DELIVERY_ADDRESS_CHANGE` | faltando | Endereço de entrega com vigência |

**Território.** `localities.municipality_ibge` existe sem CHECK de formato (o de `holidays` tem). `blocks` guarda rede
de água e de esgoto como booleanos (20a CAD-03 propõe `NONE`/`FULL`/`PARTIAL`) e `dma_id` com FK a `dma_district`.
`reading_routes` não tem setor nem grupo de faturamento: a regra "rota do mesmo setor da quadra" (20a CAD-04) não tem
como ser verificada no banco, e o cronograma por grupo da P2.5 (`V4_3`) precisa de `billing_group_id` (DAD-36).

**Problemas.** O cliente não tem endereço próprio (a tela envia `addresses[]` com marca de correspondência; a API
descarta), e a N11 art. 22 pede endereço no cadastro (DAD-05). Sem código IBGE no endereço, a NFAg-e, o IBS por destino
e o feriado municipal ficam sem chave (DAD-04).

### 3.5 Economias e categorias

**Categoria (`categories`, `V3_1:100-113`) e subcategoria (`subcategories`, `V3_1:115-130`).**

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `code`, `name` | text(20), text(100) | S | código único por tenant | `V3_1` | existe | |
| `public_entity` | bool | S | | `V3_1` | existe (divergente) | Atributo de cliente (esfera) e não de categoria; manter só se a ERI tarifar "pública" como categoria |
| `regulatory_class` | enum(`RESIDENTIAL`,`SOCIAL_RESIDENTIAL`,`COMMERCIAL`,`INDUSTRIAL`,`PUBLIC`,`OTHER`) | S | | N13 art. 6º; 08 R-05 | faltando | Relatório à ERI e ao SINISA por classe padronizada (DAD-08) |
| `system_role` | enum | N | um por papel | 20a CAD-16 | faltando | Substitui ids fixos do GSAN |
| `active` | bool | S | | | existe | |

**Economias da unidade (`unit_subcategories`, `V3_1:327-342`).**

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `unit_id`, `subcategory_id` | uuid FK | S | sem repetir na vigência | `V3_1` | existe | Índice único sem vigência |
| `economies` | int | S | ≥ 1; aviso acima do limite | N11 art. 22; N13 arts. 12-13 | existe | |
| `private_units`, `collective_units` | int | N | ≥ 0 | 07 S2 | faltando | A tela envia; descartado |
| `activity_branch_ids` | uuid[] FK | N | | 07 S2 | faltando | A tela envia; descartado |
| `valid_from`, `valid_to` | date | S/N | EXCLUDE por `(unit_id, subcategory_id)`; início no primeiro dia da competência | 20b; 20d `UNIT_PROFILE_UPDATE` | faltando | DAD-07 |
| `service_order_id` | uuid FK | S | OS `ECONOMIES_CATEGORY_CHANGE` | ADR-0039 | faltando | |

**Problemas.** Categoria principal e total de economias são derivados (correto, `V3_1:11-12`), mas, sem vigência, a
derivação na competência passada usa o valor de hoje. `bill_items` grava as economias da conta, o que protege a conta
emitida e não a retificação nem o recálculo.

### 3.6 Ligação de água e de esgoto (`service_connections`, planejada `V3_7`)

Hoje a situação está na unidade e as datas de ligação foram retiradas no `V3_1:245-246`. O plano prevê
`service_connections` (P2.23, ED-13), 1:1 com a unidade por tipo enquanto ativa.

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `unit_id` | uuid FK | S | uma ativa por `(unit_id, kind)` (índice parcial) | ADR-0034; P2.23 | planejado | |
| `kind` | enum(`WATER`,`SEWER`) | S | | P2.23 | planejado | O 01 §8 propunha duas tabelas; ficar com uma |
| `code` | text(30) | N | único por tenant | P2.23 | planejado | |
| `status_id` | uuid FK `connection_statuses` | S | `connection_statuses.kind = kind` | 20a §situações | planejado (divergente) | Com dois catálogos atuais, a FK dependeria de `kind`; catálogo único com `kind`, `system_role` e `days_to_cut` (DAD-09) |
| `connected_on` | date | S | | N11 art. 22 ("data de início") | faltando | DAD-10 |
| `ended_on`, `end_reason_id` | date, uuid FK | C | encerrar, nunca apagar | 20a CAD-28 | faltando | |
| `diameter_id`, `material_id` | uuid FK | N | | 07 S6; GIS §4 | faltando | Catálogo do GIS (`gis_material`, item) ou catálogo comercial: decidir um |
| `connection_profile_id` | uuid FK | S (água) | o perfil precisa ter tarifa | 20a CAD-27 | faltando | |
| `fixed_minimum_m3`, `valid_from` | int, date | C | ≥ mínimo calculado; só com `reg.contract_pre_nr13` | 20a CAD-37; N13 arts. 8º-9º | faltando | Vigência em tabela filha |
| `sewer_percent`, `collection_percent`, `sewer_min_volume_m3`, `grease_trap` | numeric(7,4), numeric(7,4), int, bool | C (esgoto) | 0 a 100 | 20b FAT-70; N13 arts. 14-16 | faltando | Vigência em tabela filha; OS `SEWER_PARAMETERS_CHANGE` |
| `cut_seal`, `suppression_seal` | text(40) | N | | 20a CAD-52 | faltando | Ver DAD-12 sobre lacre |
| `in_field_since` | timestamptz | N | bloqueia operações de ligação e hidrômetro | 20a CAD-12 | faltando | Estado "em campo" |

**Histórico.** `connection_status_history` (substitui `unit_status_history`): `connection_id`, `from_status_id`,
`to_status_id`, `changed_at`, `service_order_id` (S), `parameter_version_ids` (versões G9 consultadas, 20a §situações).

### 3.7 Ramal e cavalete

O ramal é feição `gis_connection` (`V13_2`, `V13_5`) e o cavalete é nó `METER_SETTING` (GIS §3.11, §4.2.6).

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `service_connection_id` | uuid FK | C | obrigatório em `IN_SERVICE` | GIS §3.6; P2.23 | planejado | Aviso: ramal sem ligação e ligação ativa sem ramal |
| `arc_id`, `tap_node_id` | uuid FK | S/N | ramal de água só em arco de distribuição | GIS §5.3 | planejado | |
| `end_node_id` | uuid FK `gis_node` | S | nó `METER_SETTING` ou `SEWER_CONNECTION_BOX` | GIS §3.6 | faltando | O GIS cita a ponta sem nome de coluna |
| `catalog_item_id` | uuid FK | S | DN e material pelo item | GIS §3.5 | planejado | DN de ramal a validar |
| `declared_length_m`, `depth_m`, `street_side` | numeric, numeric, enum | N | | GIS §3.6 | planejado | Comprimento geodésico calculado |
| metadados de captura | ver GIS §3.10 | S | `capture_method` obrigatório | GIS §3.10 | planejado | `AUTO_SERVICE_LINE` dá `APPROXIMATE` |
| cavalete: `setting_type`, `position` | enum | N | | GIS §4.2.6 | planejado | Valores a validar |

**Problemas.** O lacre aparece em três lugares possíveis: ligação (20a CAD-52), cavalete (20a) e instalação do
hidrômetro (`meter_installations.seal_number`). Recomenda-se um registro de lacres (`seals`: número, tipo, alvo,
aplicado em, retirado em, OS), com os três como alvo. O GIS ainda diz "1:1 com o imóvel" e "ligação desenhada no mapa
cria a ligação", sem OS, o que diverge do ajuste v2.14 do ADR-0034 e do ADR-0039.

### 3.8 Hidrômetro e histórico

**Hidrômetro (`meters`, `V5_1:31-55`).**

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `meter_number` | text(30) | S | único por tenant; formato por parâmetro | 20a CAD-49; `abnt-nbr-8194` | existe | Numeração de 12 caracteres da NBR 8194 a confirmar (seção 4) |
| `model_id` | uuid FK `meter_models` (marca, modelo, portaria de aprovação, DN, Q3, R, classe, posição) | S | modelo com aprovação vigente | P155; ED-14 | faltando | Hoje `brand`, `model` e `inmetro_approval` em texto livre |
| `brand_id` | uuid FK `meter_brands` (`revision_interval_days`) | S | | 20a CAD-51 | faltando | |
| `accuracy_class` ← `metrological_class` | enum(`CLASS_1`,`CLASS_2`,`LEGACY_A`,`LEGACY_B`,`LEGACY_C`,`LEGACY_D`) | S | classes antigas só para parque migrado | P155; ED-14 | existe (divergente) | Texto livre de 10 posições |
| `q3_m3h`, `ratio_r`, `q1_m3h`, `q2_m3h`, `q4_m3h` | numeric | S/C | Q4/Q3 e Q2/Q1 pela P155 | P155 RTM | faltando | Podem ficar no modelo; o hidrômetro herda |
| `nominal_diameter_mm` | numeric(6,1) | S | catálogo de DN | 20e | existe (divergente) | Valor livre |
| `digits` | int | S | 1 a 9 | `V5_1` | existe | |
| `manufacture_year` | int | S | ≤ ano corrente | 20e IND-16 | existe | |
| `initial_verification_on` | date | S | | P155; ED-14 | faltando | |
| `last_verification_on` ← `last_revision_on` | date | C | | N11 art. 22 ("última aferição"); P155 | existe (divergente) | "Revisão" não é "verificação" |
| `verification_due_on` | date (calculada) | — | até 7 anos contados do ano de instalação, ou `meter.verification_interval_years` | P155 item 9.2 do RTM; ED-14 | faltando | Derivada; não digitar |
| `status` | enum(`AVAILABLE`,`INSTALLED`,`IN_MAINTENANCE`,`STOLEN`,`WRITTEN_OFF`) | S | muda só por instalação, retirada ou movimentação | 20a CAD-53 | existe | Alterável direto hoje |
| `write_off_reason_id` | uuid FK | C | se `WRITTEN_OFF` | 20a CAD-53 | faltando | |
| `storage_location_id` | uuid FK | C | se `AVAILABLE` ou `IN_MAINTENANCE` | 20a CAD-46 | faltando | |
| `receipt_batch_id` | uuid FK | N | | ED-14 | faltando | Recebimento de lote |
| `asset_id` | uuid FK `asset` | N | | ED-14 (v2.19); ALVOS §4.4 | faltando | DAD-26 |
| `notes` | text(500) | N | | `V5_2` | existe | REDACT |

**Instalação (`meter_installations`, `V5_1:67-99`).**

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `meter_id` | uuid FK | S | só hidrômetro `AVAILABLE` | 20a CAD-45 | existe | |
| `service_connection_id` ← `unit_id` | uuid FK | S | | GIS §3.11; P2.23 | existe (divergente) | Hoje ligada à unidade (DAD-12) |
| `kind` | enum(`WATER`,`WELL`) | S | | 20a CAD-54 | existe | Poço exige `well_type_id` |
| `installed_on`, `installed_reading` | date, int | S | | 20a | existe | Exclusão temporal por unidade e por hidrômetro |
| `removed_on`, `removed_reading` | date, int | C | juntos | 20a | existe | |
| `removed_to_status`, `removed_to_location_id`, `removal_reason_id` | enum, uuid FK, uuid FK | C | destino ≠ `INSTALLED` | 20a CAD-46 | faltando | |
| `install_location_id` ← `location_description` | uuid FK ← text(100) | S | | 20a CAD-54 | existe (divergente) | Texto livre |
| `protection_type_id` ← `protection_type` | uuid FK ← text(40) | N | | 20a CAD-45 | existe (divergente) | Texto livre |
| `protection_replaced`, `valve_replaced` | bool | N | | 20a CAD-45 | faltando | |
| `seal_number` | text(40) | N | | 20a CAD-54 | existe (divergente) | DAD-12 |
| `installed_by_service_order_id`, `removed_by_service_order_id` | uuid FK | S/C | | ADR-0039; 20a | faltando | |

**Movimentação (`meter_movements`, faltando).** `moved_at`, `reason_id`, `from_location_id`, `to_location_id`,
`opinion`, `meter_ids` (tabela filha), `user_id`; cadastro em lote por prefixo e faixa (20a CAD-49, CAD-50).

### 3.9 Leitura (`meter_readings`, `V5_1:186-219`)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `unit_id`, `installation_id` | uuid FK | S | instalação vigente em `read_on` | `V5_1` | existe | |
| `kind` | enum(`WATER`,`WELL`) | S | igual ao da instalação | `V5_1` | existe | Sem CHECK cruzado |
| `reference_month` ← `reference` | date | S | dia 1 | `V5_1` | existe (divergente) | Nome difere de `bills.reference_month` |
| `read_on`, `captured_at` | date, timestamptz | S | | `V5_1` | existe | |
| `reading` | int | C | ≥ 0; menor que 10^`digits` | `V5_1` | existe | |
| `reading_status` | enum(`REALIZED`,`NOT_REALIZED`,`CONFIRMED`,`CHANGED`) | S | | `V5_1` | existe | |
| `anomaly_id` | uuid FK | C | obrigatório sem leitura | 20b | existe | |
| `informed_consumption` | int | N | | `V5_1` | existe | |
| `source` | enum(`MANUAL`,`MOBILE`,`COLLECTOR`,`TELEMETRY`) | S | | 20e | existe | |
| `reader_id` | uuid FK `users` | C | | 20e IND-17 | existe (divergente) | Sem FK porque `users` não tem `UNIQUE (tenant_id, id)` (DAD-13) |
| `reading_task_id` | uuid FK (`V5_3`) | C | | P2.7 | planejado | Liga a rota e ao ciclo |
| `idempotency_key` | text(120) | S | único | `V5_4` | planejado | |
| `photo_document_id` | uuid FK | N | | 20d | faltando | Prova da leitura; LGPD: foto pode mostrar pessoas |
| `validation_warnings` | jsonb | N | | `V5_1` | existe | |

### 3.10 Consumo (`consumption_records`, `V5_1:230-286`)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `unit_id`, `service_kind`, `reference_month` ← `reference` | uuid, enum, date | S | único por unidade, serviço e competência vigente | `V5_1` | existe (divergente) | O índice único impede guardar versões (DAD-14) |
| `billed_consumption`, `calculated_consumption` | int | S | ≥ 0 | `V5_1` | existe | |
| `billable`, `billing_paused` | bool | S | | `V5_1` | existe | |
| `consumption_type` | enum (10 valores) | S | | 20e | existe | |
| `special_situation_id` | uuid FK | C | se `consumption_type = SPECIAL_SITUATION_FIXED` | 20b FAT-61 | faltando | DAD-22 |
| `anomaly_id`, `billed_reading_anomaly_id`, `reading_id` | uuid FK | N | | `V5_1` | existe | |
| `average_consumption`, `average_months`, `average_source` | int, int, enum | C | | N11 art. 35 | existe | |
| `collection_percent` | numeric(7,4) | C | só esgoto | 20b | existe (divergente) | Na conta chama `collection_pct` |
| `condominium_macro_record_id`, `apportioned_consumption` | uuid FK, int | C | | 20b FAT-78 | existe | Sem `parent_unit_id` na unidade (DAD-37) |
| `version_no`, `superseded_by_id`, `bill_id` | int, uuid FK, uuid FK | S/N | | 20b FAT-46, FAT-49; ADR-0002 | faltando | Versão por linha, em vez de `UPDATE` com histórico no `audit_log` |
| `parameter_version_ids` | uuid[] | S | | ADR-0002; PARAMETROS §6 | faltando | Hoje só `calculation_snapshot` JSONB |

### 3.11 Fatura (`bills`, `V4_1:229-286`)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `unit_id`, `reference_month` | uuid FK, date | S | uma conta viva por unidade e competência | `V4_1` | existe | |
| `billed_customer_id` | uuid FK `customers` | S | vínculo `show_on_bill` vigente na emissão | N11 art. 33 §2º; CDC art. 42-A | faltando | DAD-15 |
| `billed_customer_snapshot` | jsonb cifrado (nome, documento mascarado, endereço de entrega) | S | gravado na emissão | N11 art. 33 §2º; NFAG (destinatário) | faltando | LGPD ID; retenção da conta |
| `billing_cycle_id`, `billing_group_id` | uuid FK | S | | P2.5 (`V4_3`); 20b FAT-81 | planejado | |
| `status`, `previous_status` | enum (14 valores) | S | | 20b §situações | existe (divergente) | `CREDIT_CARD` sem origem documentada; histórico de um nível só |
| `bill_status_events` (tabela) | `from_status`, `to_status`, `reason_id`, `service_order_id`, `occurred_at` | S | só inserção | 20b FAT-30 | faltando | Como `payment_status_events` |
| `period_from`, `period_to` | date | C | | `V4_1` | existe | |
| `water_consumption`, `sewer_volume`, `sewer_pct`, `collection_pct` | int, int, numeric | C | | `V4_1` | existe | |
| `water_value` … `total_value` | numeric(14,2) | S | total = água + esgoto + débitos − créditos − retenções | `V4_1` | existe (divergente) | Sem tributo sobre o fornecimento (IBS e CBS); `V4_6` prevê o CHECK com tributo |
| `due_date`, `original_due_date`, `valid_until`, `issued_on` | date | C | obrigatórios com `completed` | 20b FAT-52 | existe (divergente) | Todos anuláveis sem CHECK condicional |
| `due_change_count`, `rectification_count` | int | S | limites por parâmetro | 20b FAT-47, FAT-52 | existe | |
| `review_reason_id` ← `review_reason` | uuid FK ← text(100) | C | | 20b FAT-30 | existe (divergente) | Texto livre |
| `cancel_reason_id` ← `cancel_reason` | uuid FK ← text(200) | C | | 20b FAT-42 | existe (divergente) | Texto livre |
| `protocol_id`, `service_order_id` | uuid FK | C | cancelamento e retificação | ADR-0039 item 10; 20b FAT-42 | faltando | |
| `origin_bill_id`, `version_no` | uuid FK, int | C | | 20b FAT-47 | existe (parcial) | Falta `version_no` |
| `accounting_month` | date | S | | 20b FAT-13 | faltando | |
| `invoice_group_id` | uuid FK (fatura agrupadora) | N | | 20b FAT-83; ED-22 | faltando | |
| `fiscal_document_id` | uuid FK | C | uma NFAg-e viva por conta | FISCAL-PORTE-JAVA §3.4; NFAG | planejado (`V4_7`) | |
| `barcode`, `digitable_line`, `pix_txid` | text | C | | 11 R01; P3.2 | faltando | |
| `delivery_mode_id` | uuid FK | S | | 20b | faltando | |
| `tariff_snapshot`, `parameter_version_ids` | jsonb, uuid[] | S | | ADR-0002 | existe (parcial) | Versões de parâmetro só dentro do JSON |
| `late_fee_charged`, `completed` | bool | S | | `V4_1` | existe | |

### 3.12 Item de fatura (`bill_items`, `bill_debits`, `bill_credits`, `bill_taxes`)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `bill_items.*` (serviço, categoria, subcategoria, versão da tarifa, período, rateio, linha, economias, faixa, preço, consumo, bruto, desconto, líquido) | `V4_1:291-330` | S | aritmética conferida no banco | N13 arts. 12-13 | existe | Bom desenho; reproduz o cálculo |
| `bill_items.fiscal_item_code` | text | S | classificação do item no leiaute | NFAG (MOC v1.00l) | faltando | Código a confirmar no MOC |
| `bill_items.ibs_cbs_*` (CST, `cClassTrib`, base, alíquotas, valores) | numeric e text | C | grupo obrigatório | `nt-2026-002-rtc-nfag` | faltando | Alíquota de `tax_rate` com vigência (`V4_5`) |
| `bill_debits.type_code` | text(40) | S | | `V4_1` | existe (divergente) | Repete o texto do recebível; usar `debit_type_id` |
| `bill_credits.origin` | text(40) | S | | `V4_1` | existe (divergente) | Idem; usar `credit_origin_id` |
| `bill_taxes.tax_code` | text(20) | S | | `V4_1` | existe (divergente) | Texto livre; usar FK a catálogo de tributos retidos com vigência |

### 3.13 Documento fiscal (NFAg-e, planejado `V4_5` e `V4_7`)

Desenho em `FISCAL-PORTE-JAVA.md` §3. Nenhuma tabela existe.

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `issuer_id` | uuid FK `fiscal_issuers` | S | CNPJ, IE e município do emitente | NFAG | planejado | Dados do emitente faltam no tenant (DAD-32) |
| `document_type` | enum(`NFAG`,`NFSE_NACIONAL`) | S | | FISCAL §3.1 | planejado | |
| `model`, `series`, `number` | text(2) `75`, int, bigint | S | numeração sem lacuna por `fiscal_series` | `svrs-nfag-e-modelo-75` | planejado | |
| `access_key` | char(44) | C | única; DV | `moc-nfag-modelo-75` | planejado | |
| `source_type`, `source_id` | enum, uuid | S | `UNIQUE (tenant_id, source_type, source_id, document_type)` entre vivos | FISCAL §3.4 | planejado | |
| `status` | enum(`RESERVED`,`SIGNED`,`TRANSMITTED`,`AUTHORIZED`,`REJECTED`,`PENDING_RECONCILIATION`,`CANCELLED`,`REPLACED`,`VOIDED`) | S | | FISCAL §3.3 | planejado | |
| `environment` | enum(`SIMULATED`,`HOMOLOGATION`,`PRODUCTION`) | S | produção com trava tripla | FISCAL §3.6 | planejado | |
| `issued_at` | timestamptz | S | fuso America/Sao_Paulo | FISCAL §2 | planejado | |
| `authorization_protocol`, `authorized_at`, `rejection_code`, `rejection_message` | text, timestamptz, text, text | C | | MOC | planejado | |
| `contingency`, `contingency_reason` | bool, text | C | | ADR-0031 item 4 | planejado | Regime a confirmar no MOC |
| `recipient_document_ciphertext`, `recipient_document_hash` | text | C | | NFAG (destinatário) | faltando | Se o leiaute exigir; destinatário sem CPF a confirmar |
| `recipient_municipality_ibge` | char(7) | S | | NFAG; IBS por destino | faltando | Depende do DAD-04 |
| `xml_sha256`, `xml_storage_uri` | char(64), text | S (autorizada) | XML original imutável | ADR-0022 itens 2-3 | planejado | |
| `replaced_by_id`, `cancel_event_id` | uuid FK | N | | FISCAL §3.5 | planejado | |
| `payment_link` (tabela de vínculo com `payments`) | uuid FK | C | | `nt-2026-001-rtc-nfag` | faltando | Recolhimento na liquidação |

### 3.14 Pagamento (`payments`, `V6_1:95-145`)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `agreement_id`, `return_file_id` | uuid FK | S/N | | 20c ARR-13 | existe | |
| `amount`, `pending_excess`, `excess_amount` | numeric(14,2) | S | | 20c | existe | |
| `paid_on`, `credited_on` | date | S/C | crédito obrigatório para baixa | 20c ARR-14 | existe | |
| `collection_month` | date | S | dia 1 | 20b (referência de arrecadação) | faltando | Independente da competência de faturamento |
| `channel` | enum(`BANK_SLIP`,`COLLECTION`,`PIX`,`AUTO_DEBIT`,`CARD`,`CASH_DESK`) | S | | 20c ARR-17, ARR-26; P3.2 | faltando | `payment_form` (1 a 6) é código do FEBRABAN 150 |
| `capture_form`, `payment_form`, `collecting_branch`, `authentication`, `line_number`, `barcode` | text e int | C | conforme o leiaute | FEBRABAN 150 | existe (divergente) | Pertencem ao detalhe do arquivo; Pix e cartão não usam (DAD-19) |
| `status`, `previous_status` | enum (15 valores) | S | trilha em `payment_status_events` | 20c §situações | existe | `previous_status` sem CHECK |
| `identified` | bool | S | | 20c | existe | |
| `document_type`, `document_id` | text(30), uuid | C | | 20c | existe (divergente) | Polimórfico sem domínio no tipo; usar enum `BILL`, `PAYMENT_SLIP`, `INSTALLMENT_SLIP` |
| `unit_id` | uuid | C | | 20c | existe (divergente) | Sem FK de propósito (`V6_1:14`); com o módulo implantado junto do registry, a FK é possível |
| `adjustment_kind`, `adjustment_amount` | enum, numeric | S | | 20c ARR-03 | existe | |
| `fiscal_document_id` | uuid FK | C | | `nt-2026-001-rtc-nfag` | faltando | |
| `idempotency_key` | text(120) | S | único | `V6_1` | existe | |

### 3.15 Débito e crédito (`debit_receivables`, `credit_receivables`, `V4_1:170-215`)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `debit_type_id` ← `type_code` | uuid FK `debit_types` ← text(40) | S | | 20b FAT-20, FAT-23 | existe (divergente) | Texto livre |
| `generates_bill` | bool | S | | 20b FAT-23 | existe (divergente) | O 20b põe no tipo (`debit_types.generates_bill`); o código põe em cada recebível |
| `credit_origin_id` ← `origin` | uuid FK `credit_origins` (16 origens, classe a) ← text(40) | S | | 20b FAT-10 | existe (divergente) | Texto livre |
| `source_type`, `source_id` | enum(`SERVICE_ORDER`,`INSTALLMENT_AGREEMENT`,`INFRACTION`,`PAYMENT_ADJUSTMENT`,`LEGACY_IMPORT`,`MANUAL`), uuid | S | | 20b FAT-24; P2.10 | faltando | A P2.10 migra prestações com "origem parcelamento e referência ao acordo do legado" e não tem onde gravar (DAD-20) |
| `legacy_reference` | text(60) | C | se `LEGACY_IMPORT` | P2.10 | faltando | |
| `service_order_id` | uuid FK | C | obrigatório para débito de serviço | 20b FAT-24; ADR-0039 | faltando | |
| `customer_id` | uuid FK | C | devedor | `unit.transfer_with_previous_debt` (débito pessoal) | faltando | Sem ele, o débito segue a unidade e não o titular que o contraiu |
| `total_amount`, `installments`, `installments_done` | numeric, int, int | S | | `V4_1` | existe | |
| `bonus_installment` | bool | N | | 20b FAT-08 | faltando | |
| `residual_amount` | numeric | S | só crédito | `V4_1` | existe | |
| `financing_term` | enum(`SHORT`,`LONG`) | S | | 20c COB-31; FIN-01 | faltando | |
| `accounting_month`, `collection_month` | date | S | | 20b FAT-13 | faltando | |
| `status`, `under_review` | enum(`NORMAL`,`CANCELLED`), bool | S | cancelar só por OS | 20b FAT-21 | existe | Sem `cancelled_by_service_order_id` |

### 3.16 Parcelamento (faltando; P4.1)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `unit_id`, `customer_id` | uuid FK | S | devedor é o titular | 20c COB-38 | faltando | |
| `policy_version_id` | uuid FK (perfil de parcelamento versionado) | S | | 20c COB-26 | faltando | |
| `protocol_id`, `service_order_id` | uuid FK | S | OS de parcelamento | ADR-0039 item 10 | faltando | |
| `down_payment_amount`, `down_payment_due_on` | numeric, date | C | ≥ mínimo, salvo permissão | 20c COB-28 | faltando | |
| `installments`, `installment_amount` | int, numeric | S | ≤ máximo; ≥ prestação mínima | 20c COB-29 | faltando | |
| `interest_method`, `interest_rate_pct` | enum(`PRICE`,`COMPOUND`,`NONE`), numeric | S | | 20c COB-30 | faltando | |
| `discounts` (tabela filha) | tipo, valor | N | teto de abatimento | 20c COB-27 | faltando | |
| `status` | enum(`PENDING_CONFIRMATION`,`ACTIVE`,`UNDONE`,`CANCELED`,`REPLACED`,`CONCLUDED`) | S | | 20c §situações | faltando | |
| `replaces_agreement_id` | uuid FK | C | reparcelamento | 20c COB-35 | faltando | |
| `included_bills` (tabela filha) | `bill_id`, valor original | S | | 20c COB-33 | faltando | Restauração ao desfazer |
| `legacy_reference` | text(60) | C | | P2.10 | faltando | |

### 3.17 Situação especial de faturamento e de cobrança (faltando; ED-22)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `unit_id` | uuid FK | S | | 20b FAT-61 | faltando | |
| `domain` | enum(`BILLING`,`COLLECTION`) | S | | 20b FAT-60; 20c COB-14 | faltando | |
| `situation_type_id` | uuid FK (tipo com indicadores: paralisa faturamento, leitura, água, esgoto; exige consumo, volume; paralisa ações, acréscimos, corte) | S | | 20b FAT-60; 20c COB-14 | faltando | |
| `reason_id` | uuid FK | S | | 20b FAT-61 | faltando | |
| `legal_basis` | text e FK de norma | C | isenção exige base legal | 20b FAT-65; DAD-28 | faltando | |
| `start_month`, `end_month`, `removed_month` | date (dia 1) | S/N | | 20b FAT-61 | faltando | |
| `fixed_water_m3`, `fixed_sewer_m3` | int | C | conforme o tipo | 20b FAT-61 | faltando | |
| `opened_by_service_order_id`, `closed_by_service_order_id` | uuid FK | S/C | `SPECIAL_SITUATION_SET`, `_REMOVE`, `COLLECTION_HOLD` | 20b; 20c | faltando | |
| `batch_id` | uuid FK (comando em lote) | N | | 20b FAT-62 | faltando | OS interna de lote (ADR-0039 item 9) |

### 3.18 Tarifa social

Existe a regra tarifária (`tariff_versions.social_min_charge_discount_pct`, `tariff_social_bands`, `V4_1:45-140`) e a
contagem `bill_items.social_economies`. A inscrição está reservada para a `V4_2` (P1.5) sem campos definidos.

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `unit_id` | uuid FK | S | | L14898; N13 art. 26 | planejado (`V4_2`) | |
| `economies` | int | S | uma economia por família | 08 R-14; N13 | faltando | Desempate entre famílias na mesma unidade é regra da N13 |
| `holder_customer_id` | uuid FK | S | | 07 | faltando | |
| `eligibility_basis` | enum(`CADUNICO`,`BPC`,`LOCAL_PROGRAM`) | S | | L14898 art. 2º; N13 art. 31 | faltando | |
| `list_source` | enum(`REGULATOR_LIST`,`PUBLIC_ENTITY_QUERY`,`HOLDER_REQUEST`,`IMPORT`) | S | | 22c PLT-36; 22a CAD-91 | planejado (22a, 22c) | |
| `family_code_hash` | text(64) | C | índice cego | N13 art. 31 | faltando | LGPD SOC; nunca em claro fora do cofre |
| `cadunico_updated_on` | date | C | ≤ `social.cadunico_max_age_months` (24) | N13 art. 31 | faltando | |
| `valid_from`, `valid_to` | date | S/N | | ADR-0002 | faltando | |
| `status` | enum(`ACTIVE`,`LOSS_NOTICE`,`GRACE`,`ENDED`) | S | sem campo de adimplência (classe a) | L14898 art. 3º p.ú. | faltando | |
| `loss_notice_started_on` | date | C | perda só após aviso na fatura por ≥ 3 meses | L14898 art. 3º p.ú.; 20b FAT-07 | faltando | |
| `end_reason_id` | uuid FK | C | | | faltando | |
| `service_order_id` | uuid FK | S | OS interna | ADR-0039 item 4 | faltando | |
| `legal_basis_ref` | uuid FK `lgpd_consent` ou base legal declarada | S | | LGPD art. 7º; ADR-0002 G2 | faltando | |

### 3.19 Protocolo de atendimento (faltando; `V8_1`, P3.4)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `protocol_number` | text(20) | S | ano + sequência por tenant; único | N11 art. 52 §1º; 20d ATD-01 | planejado | |
| `channel` | enum(`COUNTER`,`PHONE`,`PORTAL`,`APP`,`EMAIL`,`WHATSAPP`,`REGULATOR`,`OMBUDSMAN`) | S | | 20e (meio de solicitação) | planejado | |
| `request_type_id` | uuid FK (especificação com prazo, documentos exigidos, exige matrícula) | S | | 20d ATD-02 a 16 | planejado | |
| `unit_id` | uuid FK | C | se a especificação exige matrícula | ADR-0039 item 3 | planejado | |
| `address_id`, `location` | uuid FK, geometry(Point,4674) | C | sem unidade | 20d ATD-02 | planejado | LGPD LOC |
| `requester_customer_id` | uuid FK | C | um dos três solicitantes | 20d ATD-10 | planejado | |
| `requester_name`, `requester_contact` | text | C | sem cliente cadastrado; minimização | 20d ATD-10 | planejado | LGPD ID, CT |
| `requester_role` | enum(`HOLDER`,`USER`,`THIRD_PARTY`,`INTERNAL`) | S | | 20d ATD-10 | planejado | |
| `opened_at`, `original_due_at`, `current_due_at` | timestamptz | S | prorrogação como evento | 20d ATD-03, ATD-05 | planejado | |
| `status` | enum(`OPEN`,`IN_PROGRESS`,`ANSWER_PENDING`,`ANSWERED`,`CLOSED`,`CANCELLED`) | S | | 20d §situações | planejado | |
| `closure_reason_id`, `duplicate_of_id` | uuid FK | C | duplicidade exige referência | 20d ATD-14 | planejado | |
| `regulator_reference` | text | N | | 20d ATD-21 | planejado | |

### 3.20 Ordem de serviço e alvos

Tabela atual `service_orders` (`V1_1:167-201`), sem entidade JPA; refeita na `V9_1` (P1.31).

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `order_number` | text(50) | S | único | `V1_1` | existe | |
| `service_type_id` ← `service_code_id` | uuid FK `service_types` (operação, efeito, interna ou de campo, aprovação, débito, alvos aceitos) | S | | ADR-0039; P1.31 | existe (divergente) | Sem FK |
| `is_internal`, `execution_mode` | bool, enum(`FIELD`,`INTERNAL`,`CONTRACTOR`) | S | | 20d OS-05 | faltando | |
| `protocol_id` | uuid FK | C | toda solicitação externa | ADR-0039 item 7 | faltando | DAD-24 |
| `requester_id` | uuid FK `users` | — | — | `V1_1` | existe (divergente) | Solicitante externo vem do protocolo; o usuário que registra é `created_by` |
| `consumer_unit_id`, `address_id` | uuid FK simples | — | — | `V1_1` | existe (divergente) | Substituídos por `service_order_targets` |
| `assignee_id`, `team_id`, `vehicle_id` | uuid | N | | `V1_1` | existe (divergente) | FK simples a `users`; equipe e veículo sem tabela |
| `priority` | enum | S | | 20d OS-16 | existe (divergente) | Texto livre `NORMAL` |
| `status` | enum(`DRAFT`,`OPEN`,`SCHEDULED`,`IN_EXECUTION`,`EXECUTED`,`WAITING_DEPENDENCY`,`PENDING_APPROVAL`,`CLOSED_EXECUTED`,`CLOSED_NOT_EXECUTED`) | S | máquina de estados | 20d §situações | existe (divergente) | Texto livre `OPEN` |
| `scheduled_window_start`, `scheduled_window_end` | timestamptz | N | | 22b OPE-21 | faltando | Hoje só `scheduled_at` |
| `approved_by`, `approved_at` | uuid, timestamptz | C | `approved_by ≠ created_by` quando o tipo exige | ADR-0039 item 8 | faltando | |
| `closure_reason_id`, `closing_opinion` | uuid FK, text(400) | C | data retroativa limitada | 20d OS-11, OS-12 | faltando | Hoje `resolution` e `cancellation_reason` em texto |
| `reverses_order_id` | uuid FK | C | OS inversa | ADR-0039 item 5 | faltando | |
| `parent_order_id`, `batch_id` | uuid FK | N | | 20b; ADR-0039 item 9 | existe (parcial) | Lote não existe |
| `materials_used`, `photos` | jsonb | — | — | `V1_1` | existe (divergente) | Materiais vão à requisição (ADR-0032); fotos a `documents` |
| `gps_track` | geometry(LineString,4674) | N | retenção curta | 22b OPE-11 | existe | OMIT |

**Alvos (`service_order_targets`, faltando).** `service_order_id` (S), `target_type` (S, enum da ALVOS §3:
`CONSUMER_UNIT`, `WATER_CONNECTION`, `SEWER_CONNECTION`, `METER`, `GIS_LINEAR`, `GIS_POINT`, `OPERATIONAL_SITE`,
`EQUIPMENT`, `COMPONENT`, `BUILDING`, `FLEET_ASSET`, `NETWORK_INCIDENT`, `ZONE`, `LOCATION_ONLY`), `target_id` (C),
`location` (C, para `LOCATION_ONLY`), `effect_code`, `effect_applied_at` (marca de efeito aplicado por alvo),
`effect_payload` (JSONB validado por esquema do tipo). Como `target_id` é polimórfico, cada tipo precisa de verificação
de existência no aplicador de efeitos.

### 3.21 Ativo (`asset`, faltando; ED-56)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `asset_class_id` | uuid FK (classe com UP e UAR) | S | | ALVOS §4; N16 | faltando | |
| `parent_asset_id` | uuid FK `asset` | N | sem ciclo; seis níveis | ALVOS §2 | faltando | |
| `gis_feature_type`, `gis_feature_id` | enum, uuid | C | instalação que é estrutura do GIS | ALVOS §1 | faltando | |
| `cib` | char(25) | C | só bem individual | N16 (MCPSB) | faltando | |
| `loc_ibge` | char(7) | S | | N16 | faltando | |
| `ac` | char(6) | N | | N16 | faltando | |
| `bcp`, `brv` | bool | S | | N16 | faltando | |
| `nts_sds` | text | S | 19 códigos | N16 | faltando | |
| `up` | text(3) | S | 2 ou 3 dígitos (ver seção 4) | N16 | faltando | |
| `uar` | char(7) | S | atribuído pela ERI | N16 | faltando | |
| `at1` … `at5` | char(2) | S | `00` se não se aplica | N16 | faltando | |
| `control_mode`, `unit_of_measure` | enum(`INDIVIDUAL`,`MASS`), text | S | | N16 | faltando | |
| `manufacturer`, `model`, `serial_number`, `manufacture_year`, `power_kw`, `capacity`, `dimensions` | text, text, text, int, numeric, numeric, jsonb | N | | ALVOS §5 | faltando | |
| `in_service_on`, `condition_rating`, `historic_cost`, `acquisition_document_id` | date, enum, numeric, uuid FK | C | | ALVOS §5 | faltando | |
| `useful_life_years` | int (calculado do MCPSB por perfil de agência) | S | tabela nacional | N16; ALVOS §5 | faltando | Números a conferir |
| `lifecycle_status`, `location` | enum, geometry(Point,4674) | S/N | | GIS §3.5 | faltando | |

**Problemas.** O GIS prevê `asset_codes` JSONB na feição (GIS §3.5) e a ALVOS prevê a tabela `asset`; o ED-14 v2.19
diz que o hidrômetro também é ativo. São três lugares para o mesmo código patrimonial (DAD-26). Recomenda-se o código
MCPSB só no `asset`, com a feição e o hidrômetro apontando para ele por FK.

### 3.22 Ocorrência de rede (`network_incidents`, faltando; ED-92)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `code` | text(30) | S | único por tenant | 22b OPE-16 | faltando | |
| `incident_type_id` | uuid FK (interrupção, rompimento, extravasamento, obstrução, falta de energia, alagamento) | S | | 22b OPE-16 | faltando | |
| `planned` | bool | S | interrupção programada é subtipo | N11 arts. 39-41 | faltando | |
| `origin` | enum(`COMPLAINTS`,`TELEMETRY`,`CREW`,`PLANNED`) | S | | 22b OPE-16 | faltando | |
| `cause_id` | uuid FK (`outage.cause_catalog`) | C | no encerramento | 22b OPE-16 | faltando | |
| `started_at`, `ended_at` | timestamptz | S/C | | N9 (I-04) | faltando | |
| `affected_area` | geometry(MultiPolygon,4674) | C | | ED-54 | faltando | |
| `affected_units` (tabela filha) | `unit_id`, `economies` | C | retrato no início | N9 (I-04) | faltando | Base do indicador por economia afetada |
| `network_length_km` | numeric | C | esgoto | N9 (I-05) | faltando | |
| `regulator_notified_at`, `users_notified_at`, `emergency_supply_started_at` | timestamptz | C | aviso conforme a N11 | N11 art. 41 | faltando | |
| `status` | enum(`OPEN`,`CONTROLLED`,`RESTORED`,`CLOSED`) | S | | | faltando | |

**Problemas.** `leak_detection` (`V2_2:101-160`) já tem ciclo próprio de reparo (`IN_REPAIR`, `REPAIRED`,
`repaired_by`, `repair_cost`) fora da OS, `detection_code` único fora do tenant, material e diâmetro em texto e
endereço em JSONB (DAD-27). Recomenda-se tratá-la como achado de pesquisa de vazamento (22b OPE-17), ligada à
ocorrência e à OS, com o reparo registrado só na OS.

### 3.23 Norma e ativação de norma

Catálogo atual em `docs/normas/catalogo-normas.json` (30 chaves por item); carga prevista no core `V9_8` (P1.32,
P1.34); ativação por tenant via `cert.<norma>.enabled` e `parameter_versions`, mais a adesão às NR da ANA em
`reg.ana_nr_adoption`.

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `id` | text (slug, por exemplo `ana-nr-11-res-230-2024`) | S | estável entre versões do catálogo | catálogo | existe (JSON) | Chave a usar nas FKs de norma |
| `kind`, `number`, `year`, `issuer`, `title_pt`, `norm_class` | text, enum | S | | catálogo | existe (JSON) | |
| `status`, `verified` | enum | S | | catálogo | existe (JSON) | |
| `effective_from`, `effective_to` | date | C | | P1.34 (selo de vigência futura) | faltando | Hoje a vigência futura está só em `status` e no texto de `status_note` (por exemplo, NFAg-e) |
| ativação: `norm_id`, `scope`, `edition`, `enabled`, `valid_from`, `acceptance_id` | text FK, text, text, bool, date, uuid FK | S | quem propõe não aprova; desligar não apaga | CERTIFICACOES §4 | planejado (`V21_1`) | |
| adesão regulatória: `norm_id`, `adopted_by_regulator_on`, `regulator_act` | text FK, date, text | C | | PARAMETROS G1 (`reg.ana_nr_adoption`) | planejado (como parâmetro) | Dois mecanismos de "norma ativa": certificação e adesão regulatória. Recomenda-se uma tabela com `activation_kind` |

**Problemas.** Referências normativas em texto livre: `tariff_versions.regulatory_act`, `collection_policies.norm_source`,
`holidays.source`, `surcharge_indexes.source` (DAD-28). Sugere-se manter o texto (ato local nem sempre está no
catálogo) e acrescentar `norm_id` opcional.

### 3.24 Parâmetro do tenant

Hoje: `public.tenant_settings` (chave e valor, `V1:107-131`), `tenant_metadata.settings` e `tenant_catalog.settings`
(JSONB sem uso), e tabelas com vigência própria (`tariff_versions`, `tax_withholding_rates`, `collection_policies`).
Alvo: `parameter_definitions`, `parameter_legal_limits`, `parameter_versions`, `parameter_acceptances` (`V1_5`, P0.19;
PARAMETROS §6). O dicionário-alvo está completo em PARAMETROS §6 e não se repete.

**Problemas.**

- `registry.require_user_document` muda o atendimento à N11 art. 22 I "b" e à L13460 art. 10-A, e fica em
  `tenant_settings`, sem vigência nem aceite (DAD-33). PARAMETROS §6 classifica as chaves `registry.*` como
  operacionais; esta é exceção.
- `collection_policies` tem só índice único em `valid_from` (sem EXCLUDE), já registrado em PARAMETROS §6.
- `tenant_metadata.settings` e `tenant_catalog.settings` são sobra (DAD-32).

### 3.25 Documento anexo (`documents`, `V1_1:144-161`)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `entity_type`, `entity_id` | text(50), uuid | S | | `V1_1` | existe (divergente) | Polimórfico sem FK |
| `document_type_id` ← `type` | uuid FK ← text(50) | S | | ADR-0022 | existe (divergente) | Texto livre |
| `file_name`, `content_type`, `file_size`, `storage_path`, `checksum` | text, text, bigint, text, char(64) | S | SHA-256 | ADR-0022 item 3 | existe | `file_name` REDACT |
| `retention_class_id` | uuid FK (classe de G6) | S | | ADR-0002 G6; 13 RT-01 | faltando | |
| `data_classification` | enum (seção 1.3) | S | | LGPD art. 37 | faltando | |
| `immutable` | bool | S | XML autorizado nunca é regerado | ADR-0022 item 2 | faltando | |
| `service_order_id`, `protocol_id` | uuid FK | N | | ADR-0039 | faltando | |
| `scan_status` | enum | S | | P1.20 | planejado | |

### 3.26 Usuário e permissão (`users`, `roles`, `user_roles`, `V1_1:29-78`)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `users.keycloak_sub` | uuid | S | único | ADR-0040 | faltando | Identidade vem do Keycloak |
| `users.username`, `email`, `full_name`, `phone` | text | S/N | | `V1_1` | existe | REDACT |
| `users.password_hash`, `mfa_secret` | text | — | — | `V1_1` | sobra | P0.13 manda remover o hash duplicado (DAD-31) |
| `users.status` | enum(`ACTIVE`,`INACTIVE`,`LOCKED`) | S | nunca remover, só inativar | 20d SEG-12 | existe (divergente) | Texto livre |
| `users.failed_login_attempts`, `locked_until` | int, timestamptz | — | — | `V1_1` | sobra | Contagem passa ao autenticador do Keycloak por tenant (ADR-0040 item 4) |
| `users` `UNIQUE (tenant_id, id)` | constraint | S | | | faltando | DAD-13 |
| `roles.permissions` | jsonb | — | — | `V1_1` | existe (divergente) | Alvo: `role_permissions(role_id, permission_code)` com FK ao catálogo `recurso:ação` (P0.13) |
| `user_roles` | FK simples a `users(id)`, `roles(id)` | S | | `V1_1` | existe (divergente) | FK composta com tenant |
| `org_units`, `org_unit_areas`, `user_scopes` | tabelas | S | escopo por localidade, setor e rota | ADR-0040 item 2 | planejado (`V1_8`, P3.30) | |

### 3.27 Tenant (`public.tenant_catalog`, `tenant_metadata`)

| Campo | Tipo | Obr. | Regra de validação | Origem | Situação | Observação |
|---|---|---|---|---|---|---|
| `legal_name`, `trade_name` | text(200) | S | | `V1:22`; `V1_1:14` | existe (divergente) | `name` e `display_name` em duas tabelas |
| `cnpj` | char(14) | S | DV; alfanumérico | `V1:23`; `V1_1:15` | existe (divergente) | Duplicado; `VARCHAR(18)` aceita máscara |
| `legal_nature` | enum | S | | `reg.legal_nature`; PARAMETROS §5 item 1 | planejado (parâmetro) | Muda o regime LGPD e a fonte do CadÚnico |
| `address` com `municipality_ibge` | estruturado | S | | NFAG (emitente) | existe (divergente) | JSONB livre nas duas tabelas |
| `state_registration`, `tax_regime` | text, enum | C | emitente de NFAg-e | NFAG | faltando | Pode ficar em `fiscal_issuers` |
| `dpo_name`, `dpo_contact` | text | S | divulgação pública | LGPD art. 41 | faltando | PARAMETROS §5 item 1 exige "encarregado nomeado" |
| `status` | enum | S | ciclo de vida do tenant | core `V9` (`tenant_lifecycle`) | existe (divergente) | Texto livre |
| `plan`, `settings` | text, jsonb | — | — | `V1:28-29` | sobra | Contrato e plano vão ao core `V9`/`V10_1` |

### 3.28 Nomes divergentes (glossário mínimo)

| Conceito | Banco | DTO, tela ou documento | Recomendação |
|---|---|---|---|
| Matrícula | `matricula`, `dv` | `registration`, `registrationDv`; entidade `checkDigit` | `registration_number`, `check_digit` |
| Papel do vínculo | `role` | `relation` | `role` (ou `role_id` com catálogo) |
| Logradouro | `street` | `streetName` | `street_name` ou `street_id` |
| Competência | `reference` (leitura, consumo) | `reference_month` (conta) | `reference_month` |
| Percentual de coleta | `collection_percent` (consumo) | `collection_pct` (conta) | um só |
| Convênio | `collection_agreements.agreement_code` | `bank_return_files.convenio_code` | `agreement_code` |
| Perfil do imóvel | — | `property_profiles` (plano `V3_6`, 07) × `unit_profiles` (20b FAT-76) | `property_profiles` |
| Contatos | `customer_contacts` | `customer_emails`, `customer_phones` (07) | manter `customer_contacts` |
| Ligação | — | `service_connections` (plano) × `water_connections`, `sewer_connections` (01 §8) | `service_connections` |
| Verificação metrológica | `last_revision_on` | "última aferição" (N11), "verificação" (P155) | `last_verification_on` |
| Distrito de medição | `blocks.dma_id` | `dma_district_id` (GIS §3.6) | um só |

## 4 Inferências e pontos a confirmar

1. **Conteúdo da N11 sobre cadastro.** O catálogo declara leitura parcial da Res. ANA 230/2024 e não detalha o art. 22.
   Os campos mínimos usados aqui (código único, nome, CPF/CNPJ, telefone, e-mail, endereço, atividade econômica,
   economias por categoria, data de início, histórico de 120 meses, medidor com instalação e última aferição) vêm dos
   levantamentos 08 (R-01), 11 e 13 (CD-01, RT-01). Conferir no texto integral antes de travar obrigatoriedade.
2. **Destinatário e classificação na NFAg-e.** Se o leiaute aceita destinatário sem CPF/CNPJ, qual código classifica o
   item e quais campos do grupo IBS/CBS vão por item dependem do MOC v1.00l, que o portal da SVRS ainda chama de
   minuta. O dicionário da seção 3.13 marca esses campos como condicionais.
3. **Atividade econômica: da unidade ou do cliente.** O 08 (R-01) a põe no cadastro da unidade usuária; o 07 tem
   `activityBranchId` no cliente e `activityBranchIds` por subcategoria. Decidir um lugar.
4. **Portaria Inmetro 155/2022.** O catálogo a marca como fonte secundária e diverge internamente sobre as portarias
   revogadas; o FAQ do Inmetro citado no 22b (OPE-24) ainda menciona a 295/2018. A regra de sete anos para a
   verificação subsequente (item 9.2 do RTM) foi lida no catálogo, não no texto.
5. **Numeração do hidrômetro.** A NBR 8194 com 12 caracteres vem de fonte secundária (`correcoes-aos-documentos.md`).
6. **MCPSB.** A unidade patrimonial aparece com 2 dígitos numa tabela e 3 na figura do manual; a versão 2 (2025) do
   manual da ARSESP não foi lida; os números de vida útil foram lidos de forma automática. Os tamanhos de campo da
   seção 3.21 seguem a ALVOS §5 e precisam dessa conferência.
7. **Tela × API.** A comparação dos campos descartados foi feita sobre os tipos e formulários do frontend; parte dos
   contratos da tela está marcada como "SUPOSIÇÃO" no próprio código. Um teste de contrato confirma o que de fato
   chega à API.
8. **Classificação de `consumer_units.coordinates`.** O `AuditRedactionCoverageTest` isenta a coluna como "localização
   da ligação (rede)"; o GIS e o plano tratam o ponto do imóvel como dado pessoal publicável só em agregado. Tratar como
   LOC é recomendação desta revisão, não decisão.
9. **Nome na conta.** Restringir `show_on_bill` ao usuário titular segue a N11 art. 33 §2º, mas há ERIs que admitem o
   responsável; pelo ADR-0038 isso é parâmetro, e o padrão mais protetivo é decisão de produto a confirmar por cartão.
10. **Ordem protocolo × OS.** A P1.31 (`V9_1`) entra antes da `V8_1` (P3.4), mas o ADR-0039 item 7 liga toda
    solicitação externa a um protocolo. Inferência: ou a tabela mínima de protocolo entra com a P1.31, ou a OS reserva
    `protocol_id` sem FK até a P3.4.
11. **Prescrição contra a Fazenda Pública.** O prazo de 5 anos citado no 13 (RT-03) está marcado como não confirmado;
    o campo `government_sphere` é proposto para suportar a regra, não para fixá-la.
12. **`residents` e `municipal_property_id`.** Tratados como dado pessoal indireto por inferência; a finalidade e a
    base legal são do controlador (tenant).
13. **Decisões do dono preservadas.** Nada aqui altera ADR, plano ou parâmetro. Onde a proposta toca decisão (ADR-0039,
    ADR-0038, ADR-0034), ela é recomendação para a tarefa indicada no quadro da seção 2.
