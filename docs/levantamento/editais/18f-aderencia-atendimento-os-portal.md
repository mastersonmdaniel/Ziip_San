# 18f — Matriz de aderência: atendimento ao público, ordens de serviço, portal e app do cliente, notificações

Levantamento de 12/10/2026. Produto Ziip SAN (código interno SIGSAN, renomeado `ziipsan` na P0.33). Compara os requisitos dos editais com o código em `sigsan` e com o plano de execução v2.2 (`docs/PLANO-EXECUCAO-PDCA-R0-R9.md`).

**Fontes de requisitos.** `18a-videira-termo-de-referencia.md` (IDs `TR-`, VISAN, PE 06/2026), `18c-sigcos-pe-2-2026.md` (IDs `SG-`, SAMAE São Ludgero, PE 2/2026) e `18b-videira-edital-etp-precos.md` (sem requisito novo da área; ED-024, ED-037, ED-057, ED-059 e A-19 entram na fronteira).

**Fontes do estado do produto.** Código (`pom.xml`, `commercial/*`, `operations/*`, `platform/*`, migrations e controllers); plano v2.2; ADR-0020 e ADR-0022; `docs/arquitetura/ARQUITETURA-ALVO.md`, `PARAMETROS-DO-TENANT.md`, `REQUISITOS-NAO-FUNCIONAIS.md`; levantamentos `04` (épicos E1–E24) e `13` (AT-01 a AT-12, CB-01 a CB-09). `AJUDA-SUPORTE-SOBRE.md` e o protocolo `SUP-AAAA-NNNNNN` tratam do suporte do Ziip ao tenant e não contam como atendimento ao consumidor.

**Seleção.** Todas as linhas das seções 14 a 18, 20 e 21 do TR de Videira e 2.17 a 2.25 do TR do SIGCOS, mais o item n) do roteiro de POC de Videira que não tem linha própria no TR (anexos na OS, POC-46). Itens da área que estão em seções de outras áreas (OS geradas por hidrometria, leitura, fiscalização e cobrança; notificações de cobrança; relatórios e painéis de atendimento; OS no GIS; cláusulas comerciais) ficam na seção 2.15, sem entrar nos totais.

## 1. Resumo

### 1.1 Requisitos selecionados

| Edital | Seções | Requisitos (IDs) | IDs em linhas com POC “sim” | Observação |
|---|---|---|---|---|
| 18a Videira (TR) | 14, 15, 16, 17, 18, 20, 21 | 225 | 72 | 24 dos 81 itens do roteiro de POC (Anexo II) são desta área; todo o intervalo 5.1–23.1 pode ser cobrado e deve ser ≥ 90% nativo (4.19) |
| 18c São Ludgero (SG) | 2.17 a 2.25 | 341 | 301 | 2.17, 2.18, 2.19, 2.22 e 2.24 são críticas (eliminatórias sem implementação posterior); 2.20 e 2.21 essenciais; 2.23 e 2.25 avaliáveis |
| 18b Videira (edital, ETP, preços) | — | 0 novos | — | ED-057 e ED-059 repetem TR-653 e TR-856; ED-024, ED-037 e A-19 tratam de subcontratação e custo variável (seção 2.15) |
| **Total** | | **566** | **373** | 325 linhas na matriz, depois de juntar equivalentes (mais 1 item do roteiro sem ID) |

### 1.2 Situação no código

| Situação | TR (IDs) | SG (IDs) | Total de IDs | Linhas da matriz |
|---|---|---|---|---|
| Atende | 3 | 4 | 7 | 4 |
| Parcial | 20 | 35 | 55 | 28 |
| Não atende | 202 | 302 | 504 | 293 |

### 1.3 Situação no plano v2.2

| Situação | TR (IDs) | SG (IDs) | Total de IDs | Linhas da matriz |
|---|---|---|---|---|
| Planejado | 8 | 16 | 24 | 17 |
| Parcial | 76 | 108 | 184 | 87 |
| Não planejado | 141 | 217 | 358 | 221 |

Um ID que aparece em duas linhas com situações diferentes (TR-836: portal planejado, totem não) conta como parcial.

### 1.4 Leitura

1. **A área quase não existe no código.** Só 7 IDs atendem (matrícula com dígito verificador, localização e rota da UC e a cláusula que deixa livre a arquitetura da IA). Os 55 parciais se apoiam no cadastro (`sigsan-registry`) ou em ganchos sem uso: a tabela `service_orders`, o `service_order_id` do histórico de situação da UC e o indicador `generates_service_order` das anomalias de leitura. Não há protocolo, OS, portal, app, notificação nem canal de conversa.
2. **O plano cobre o mínimo regulatório, não os editais.** 24 IDs estão planejados e 184 em parte, quase todos pelas P3.3, P3.4, P3.5 e P3.20, pensadas para NR 11 e Lei 14.015. Ficam sem tarefa 358 IDs (63%): catálogo de serviços, programação e baixa completa de OS, app de campo de OS, app do cliente nas lojas, totem e filas, omnichannel, chatbot, IA e telefonia.
3. **POC de Videira.** 24 dos 81 itens do roteiro (48 de 162 pontos) são desta área (POC-26, POC-41 a POC-55, POC-69 a POC-76). A aprovação pede 146 pontos; zerar mais de 8 itens reprova, e sem esta área o máximo possível é 114 pontos (70%).
4. **POC do SIGCOS.** As seções 2.17, 2.18, 2.19, 2.22 e 2.24 são críticas e somam 274 dos 341 IDs da área: um requisito crítico não demonstrado desclassifica, sem implementação posterior (edital 7.7.7–7.7.8). Entre eles estão o app nas lojas com o nome do SAMAE (SG-779), o omnichannel dentro do sistema comercial (SG-633) e a programação de OS (SG-481).
5. **Conflitos com decisões vigentes.** (a) O ADR-0022 define a conta do consumidor por e-mail ou celular; o SIGCOS exige login por CPF ou CNPJ (SG-706) e Videira por matrícula ou CPF (TR-656, TR-657). A instrução que originou esta matriz cita “CPF ou e-mail”, que não é o texto do ADR. (b) O ADR-0022 rejeita documento com dado pessoal por e-mail (remete ao ADR-0020, item 5); os editais pedem enviar faturas ao e-mail do usuário (TR-570, SG-546, POC-20). (c) A P4.4 prevê PWA para a leitura; o app de OS de Videira deve ser instalado por APK com banco local (TR-621, TR-639). (d) O vínculo do ADR-0022 exige o código impresso na conta; o SIGCOS valida só nome e CPF/CNPJ (SG-708, SG-709) e pede mensagem orientativa onde o ADR manda resposta indistinta (SG-711). (e) O app nas lojas com o nome do prestador (SG-779, SG-780) obriga uma publicação por tenant.
6. **Regulatório fora dos editais.** Nenhum dos editais cita NR 11, Decreto 11.034/2022 ou ouvidoria. Essas obrigações vêm do levantamento 13 (AT-01 a AT-12) e já orientam a P3.4; ouvidoria (AT-05) e pesquisa de satisfação periódica (AT-08) seguem sem tarefa. A pesquisa ao fim do atendimento aparece no SIGCOS (SG-654) e no relatório de Videira (TR-682).
7. **Custos de terceiros.** O SIGCOS paga mensagens por uso (30.000 omnichannel a R$ 0,79 e 12.000 SMS a R$ 0,18). Videira cobra o omnichannel como mensalidade fixa (R$ 2.233,33), exige franquia mínima sem cobrança adicional (TR-880), veda subcontratação (ED-024) e não informa volume (A-19). Hardware de campo e de totem não tem responsável definido em Videira (ponto ambíguo 21 e 31 do 18a).

## 2. Matriz

### 2.0 Legenda

**POC?** “sim” quando o item está no roteiro de Videira (`V POC-nn`, Anexo II) ou numa seção crítica (`S crít.`) ou essencial (`S ess.`) do SIGCOS; “não” quando só pode ser cobrado a critério da comissão (`V poss.`, intervalo 5.1–23.1) ou está em seção avaliável não essencial (`S aval.`, 2.23 e 2.25). No SIGCOS a comissão escolhe 80 itens e qualquer requisito do TR pode entrar.

**Código.** Atende, Parcial ou Não atende, com a evidência. Códigos de evidência:

- `C0` Sem módulo: `commercial/sigsan-portal`, `commercial/sigsan-dunning`, `operations/sigsan-field` e `operations/sigsan-maintenance` são diretórios vazios, fora do reator Maven (módulos em `pom.xml`, linhas 48–60). Os únicos controllers REST são os do cadastro (`commercial/sigsan-registry/.../web/`) e o `MeController`.
- `C1` Tabela `service_orders` em `platform/sigsan-platform/src/main/resources/db/migration/tenant/V1_1__create_tenant_schema.sql:167` (número, serviço, UC, equipe, veículo, prioridade, estado, datas, `materials_used`, `photos`, `gps_track`, `form_data`, `parent_order_id`). Sem entidade, serviço nem API; só aparece no `TenantProvisioningServiceTest`.
- `C2` Faturamento, medição e arrecadação têm só motores puros, sem serviço, API nem lote (plano §1; revisão 17).
- `C3` API do cadastro em `/api/registry`: `POST /consumer-units/search` (`q` = inscrição, matrícula, CPF/CNPJ ou nome; CEP; faixa de número; localidade, setor, quadra, rota, categoria, situações), `GET /consumer-units/{id}`, `/{id}/customers` e `/{id}/history`. Um endereço por UC, com logradouro em texto (`ConsumerUnitDtos.AddressInput`).
- `C4` `commercial/sigsan-registry/.../rules/Matricula.java`: dígito verificador módulo 11; `UnitView` expõe `registration` e `registrationDv`.
- `C5` `CustomerContact`: e-mail e telefone (E.164, com rótulo) do cliente; o e-mail aceita `consentId`. Não há canal WhatsApp nem opt-in por canal e finalidade.
- `C6` `unit_status_history` (registry `V3_1`): histórico, só de inserção, das situações de água e esgoto, com `service_order_id`; consulta por `GET /consumer-units/{id}/history`.
- `C7` `reading_anomalies.generates_service_order` (metering `V5_1`): indicador sem gerador de OS.
- `C8` Tabela genérica `documents` (platform `V1_1`), sem API; o produto não recebe arquivos hoje.
- `C9` Auditoria por trigger (`audit_log`, core `V1`, endurecida no tenant `V1_4`), sem dado pessoal; não registra ações por protocolo.
- `C10` `compliance/sigsan-lgpd`: `lgpd_consent` (`V2_5`) e `DataSubjectRightsEngine` (prazo único de 15 dias); prazos de retenção ainda não semeados.
- `C11` `Customer` existe sem UC (`POST /customers`, `POST /customers/search` por nome ou CPF/CNPJ), sem identificador próprio com dígito verificador.
- `C12` `UnitCustomer`: papéis OWNER, USER e RESPONSIBLE, com início, fim e motivo; `PUT /customers/{id}`.
- `C13` `dueDay` de 1 a 31 na UC e no cliente (`ConsumerUnitDtos.java:98`), sem lista de dias por grupo de faturamento.
- `C14` `UnitInput`: localidade, setor, quadra, lote, sublote, rota, sequência, subcategorias com economias, área construída, situações de água e esgoto e `notes`; JSON `properties` na entidade.
- `C15` `intelligence/sigsan-quality` só com schema (`V2_1`), sem API.
- `C16` Frontend só com as telas de cadastro (`frontend/src/pages/cadastro`) e o login do backoffice.

**Plano.** Tarefa e fase do plano v2.2 que cobre o requisito (Planejado), cobre parte (Parcial) ou nenhuma (Não planejado). Tarefas citadas: P0.11 máscara e registro de leitura de dado pessoal; P0.14–P0.16 BFF, MFA, superfície web; P0.19–P0.20 parâmetros e pisos; P0.22 busca; P0.28 HA, backup e PITR; P1.3 R2 cadastro completo; P1.4 matrícula por sequência; P1.5 R3 tarifa social e N4 (simulador de conta); P1.7 partição do `audit_log`; P1.9 outbox e worker; P1.12 frontend robusto; P1.19 marca do tenant; P1.20 arquivos com ClamAV (hoje só anexos de chamado e logos); P2.5 R4 medição, ciclo e conta; P2.17 marca nos PDFs; P2.18 XML imutável e `HashChain`; P3.2 Pix; P3.3 portal (2ª via, Pix, protocolo, realm `ziipsan-portal`); P3.4 R7 mínimo (protocolo NR 11, OS com bloqueio de corte, `V8_1`, `V9_1`); P3.5 notificações com prova de entrega; P3.6 NFS-e de serviço avulso; P3.8 pentest; P3.10 RIPD; P3.20 portal com vínculo, 120 meses e documentos; P3.21 marca no portal e nos e-mails; P4.1 R8 cobrança; P4.3 BI em réplica; P4.4 app de leitura offline (PWA).

**Esforço.** Incremental do requisito dentro da entrega indicada, na escala do plano (§4): P até 2 dias, M até 1 semana, G mais de 1 semana, para uma pessoa com agentes.

**Entrega.** Código da entrega proposta na seção 3 para o que não está planejado (`ENT-nn`) ou da matriz de outra área (`OUT-xxx`).

### 2.1 Atendimento: protocolo, registro e tela única

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| TR-544, TR-546; SG-520, SG-521 | Módulo de atendimento que registra todo atendimento (V: balcão, e-mail, telefone, correspondência; S: balcão, call center, chatbot) com protocolo único gerado na abertura e tarefas registradas até o encerramento | sim (V POC-47; S crít.) | Não atende: C0 | Parcial: P3.4 (protocolo NR 11, `V8_1`) | P3.4 prevê o protocolo mínimo; faltam canal de origem, tipos de atendimento, numeração parametrizável e o chatbot como canal | G | ENT-02 |
| TR-545, TR-552; SG-528 | Tela única do usuário com navegação ágil: faturas, dados cadastrais, OS, leituras e consumos, documentos de cobrança, parcelamentos | sim (V POC-48; S crít.) | Parcial: C3 (UC, vínculos, histórico); sem faturas, leituras, OS nem parcelamentos (C2) | Não planejado | Visão 360 no atendimento, agregando APIs de billing, metering, collection e OS | M | ENT-02 |
| TR-547; SG-522 | Registro automático das ações do atendente (S: e do chatbot) durante o atendimento, além de comentários | sim (V POC-47; S crít.) | Parcial: C9 (trilha por trigger, sem vínculo com protocolo) | Parcial: P3.4; P0.11 (registro de leitura de dado pessoal) | Linha do tempo do protocolo com ações automáticas e comentários | M | ENT-02 |
| TR-548; SG-523 | Encerrar o atendimento em curso antes de iniciar outro, com tipo de atendimento e observações; enviar o protocolo por e-mail ou SMS (S: também WhatsApp) | sim (V POC-47; S crít.) | Não atende: C0 | Parcial: P3.4; P3.5 (e-mail e SMS); WhatsApp não planejado | Sessão de atendimento por atendente, tipos de atendimento e envio do comprovante pelo serviço de notificações | M | ENT-02 |
| TR-549; SG-524 | Monitorar em local próprio protocolos e OS com prazo, até a execução e o retorno ao usuário | sim (V poss.; S crít.) | Não atende: C0 | Parcial: P3.4; parâmetro `sac.response_days` (P3) | Fila de pendências por prazo, alerta de vencimento e registro do retorno ao usuário | M | ENT-02 |
| SG-525 | Workflow de atividades predefinidas (ex.: ligação nova: cadastro, OS de viabilidade, fiscalização e vistoria, execução, cobrança) | sim (S crít.) | Não atende: C0 | Não planejado | Fluxo por tipo de solicitação encadeando OS e cobrança | G | ENT-02 |
| TR-550; SG-526 | Identificador da UC formado por números e dígito verificador | sim (V poss.; S crít.) | Atende: C4 | Planejado: P1.4 (matrícula por sequência) | Nenhuma; manter a matrícula do legado na migração | P | — |
| TR-551; SG-527 | Localizar a UC pela matrícula ou por pesquisa avançada (logradouro, CEP, número, CPF/CNPJ, localização, hidrômetro) | sim (V POC-48; S crít.) | Parcial: C3: matrícula, CPF/CNPJ, nome, CEP, faixa de número, setor, quadra, rota; sem filtro por hidrômetro nem por logradouro | Parcial: P0.22 (busca com índice); P1.3 (endereço) | Filtros por logradouro e por número do hidrômetro (depende de metering com API) | P | ENT-02 |

### 2.2 Atendimento: ações sobre faturas

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| TR-553; SG-529 | Conjunto de recursos de atendimento sobre emissão e ajuste de faturas (caput) | sim (V poss.; S crít.) | Não atende: C2 | Não planejado (P2.5 gera a conta; não prevê ajuste pelo atendimento) | Serviço de revisão de conta acionado a partir do protocolo | G | ENT-15 |
| TR-554; SG-530 | Pesquisar faturas da UC por situação (pendente, quitada, cancelada, parcelada, em processo, em alteração; V: em dívida ativa; S: reparcelada) | sim (V poss.; S crít.) | Não atende: C2 | Parcial: P2.5 (conta); P3.20 (histórico no portal) | Consulta de faturas por situação no backoffice | M | ENT-15 |
| TR-555; SG-531 | Cancelar fatura com anotação automática (usuário, data, parecer) | sim (V POC-50; S crít.) | Não atende: C2 | Não planejado | Cancelamento com motivo e registro; a POC da VISAN pede o registro via OS | M | ENT-15 |
| TR-556; SG-532 | Alterar vencimento da fatura com anotação automática | sim (V POC-49; S crít.) | Não atende: C2 | Não planejado | Alteração com motivo, registro e reemissão | P | ENT-15 |
| TR-557; SG-533 | Alterar a leitura aferida na fatura com anotação automática | sim (V POC-49; S crít.) | Não atende: C2 | Não planejado | Recalcular a conta pela leitura corrigida | M | ENT-15 |
| TR-558; SG-534 | Alterar o consumo faturado com anotação automática | sim (V POC-26; S crít.) | Não atende: C2 | Não planejado | Recalcular a conta pelo consumo informado | M | ENT-15 |
| TR-559; SG-535 | Alterar serviços faturados com anotação automática | sim (V poss.; S crít.) | Não atende: C2 | Não planejado | Inclusão e exclusão de itens de serviço na conta | M | ENT-15 |
| TR-560; SG-536 | Alterar a fatura em razão de atualização cadastral da UC, com anotação | sim (V poss.; S crít.) | Não atende: C2 | Não planejado | Recalcular pela situação cadastral atual | M | ENT-15 |
| TR-561; SG-537 | Atualizar serviços faturados de uma fatura, com anotação | sim (V poss.; S crít.) | Não atende: C2 | Não planejado | Mesmo serviço da linha anterior | P | ENT-15 |
| TR-562; SG-538 | Retirar a cobrança de água ou de esgoto de uma fatura, com anotação | sim (V poss.; S crít.) | Não atende: C2 | Não planejado | Recalcular sem o serviço retirado | P | ENT-15 |
| TR-563; SG-539 | Conceder desconto por vazamento na fatura, com anotação | sim (V POC-26; S crít.) | Não atende: C2 | Não planejado | Desconto limitado pelos percentuais do tenant | M | ENT-15 |
| SG-540 | Marcar a fatura como aguardando pagamento a ser remetido pelo agente arrecadador, com anotação | sim (S crít.) | Não atende: C2 | Não planejado | Estado da fatura que suspende cobrança até a baixa | P | ENT-15 |
| TR-564; SG-541 | Emitir fatura antecipada ao processamento do grupo, com cálculo prévio e anotação | sim (V poss.; S crít.) | Não atende: C2 | Não planejado | Faturamento individual fora do lote | M | ENT-15 |
| TR-565; SG-542 | Emitir fatura não gerada no processamento do grupo, com cálculo prévio e anotação | sim (V poss.; S crít.) | Não atende: C2 | Não planejado | Mesmo serviço da linha anterior | P | ENT-15 |
| TR-566; SG-543 | Reenviar fatura para débito automático | sim (V poss.; S crít.) | Não atende: C2 | Não planejado (débito automático depende dos leiautes do G4) | Reenvio no próximo arquivo de débito | P | OUT-ARR |
| TR-567; SG-544 | Emitir 2ª via com as mesmas características da original | sim (V poss.; S crít.) | Não atende: C2 | Parcial: P3.3 (2ª via no portal); P2.17 (PDF com marca) | 2ª via também no backoffice, gerada dos dados gravados | P | ENT-15 |
| TR-568; SG-550 | V: fatura espelho sem código de barras; S: 2ª via de fatura quitada, sem código de barras e com mensagem de quitação | sim (V poss.; S crít.) | Não atende: C2 | Parcial: P3.20 (conta paga com quitação no portal) | Espelho no backoffice | P | ENT-15 |
| TR-569; SG-545 | Simular o cálculo de uma fatura sem emiti-la | sim (V poss.; S crít.) | Não atende: C2 (`TariffEngine` puro, sem API) | Planejado: P1.5 (N4: simulador de conta) | Expor o simulador no atendimento | P | — |
| TR-570, TR-542; SG-546 | Selecionar uma ou várias faturas e enviá-las ao e-mail do usuário (V 14.1.1.39.7: informar o e-mail no envio) | sim (V poss.; S crít.) | Não atende: C0 | Parcial: P3.5 (notificações); P3.21 (e-mail com marca) | Conflito: ADR-0022 rejeitou documentos por e-mail com dado pessoal (ADR-0020 item 5); definir envio por link autenticado ou anexo com aceite | M | ENT-11 |
| TR-571; SG-547 | Enviar valor, vencimento e código de barras das faturas selecionadas por SMS (S: SMS ou WhatsApp) | sim (V poss.; S crít.) | Não atende: C0 | Parcial: P3.5 (SMS); WhatsApp não planejado | Modelo de mensagem com linha digitável e Pix copia e cola | M | ENT-11 |
| TR-572; SG-548 | Excluir fatura registrada em cartório de protesto (V) ou no SPC/Serasa (S) | sim (V poss.; S crít.) | Não atende: C0 | Não planejado (negativação só em 2028; protesto não previsto) | Baixa de protesto e negativação a partir do atendimento | M | OUT-COB |
| TR-573; SG-549 | Colocar fatura em processo administrativo ou judicial, sem corte nem cobrança enquanto durar, com anotação | sim (V poss.; S crít.) | Não atende: C0 | Parcial: P4.1 (contestação bloqueia corte) | Estado “em processo” consultado pelo corte e pela cobrança | M | ENT-15 |

### 2.3 Atendimento: ações cadastrais

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| TR-574; SG-551 | Conjunto de recursos de atendimento sobre o cadastro da UC (caput) | sim (V poss.; S crít.) | Parcial: C3, C12, C14 | Parcial: P1.3 (R2: cadastro completo) | Alterações cadastrais abertas a partir do protocolo e registradas nele | M | ENT-02 |
| TR-575; SG-552 | Alterar dados cadastrais do usuário e cadastrar inquilino ou proprietário da UC | sim (V poss.; S crít.) | Parcial: C12 | Planejado: P1.3 | Ligar a alteração ao protocolo | P | — |
| TR-576; SG-553 | Digitalizar e anexar documentos pessoais do usuário, visíveis na tela | sim (V poss.; S crít.) | Não atende: C8 | Parcial: P1.20 (pipeline com ClamAV, hoje só chamados e logos) | Estender o pipeline a documentos do cliente, com retenção e registro de acesso | M | ENT-02 |
| TR-577; SG-554 | Manter endereço físico principal e alternativo da UC (trocar, incluir, excluir, desativar) | sim (V poss.; S crít.) | Parcial: C3 (um endereço por UC) | Parcial: P1.3 (endereço R2) | Endereço alternativo ou de correspondência | P | OUT-CAD |
| TR-578; SG-555 | Atualizar dados técnicos da ligação de água e observações da instalação | sim (V poss.; S crít.) | Parcial: C14 (situação da água e `notes`; sem dados técnicos da ligação) | Não planejado nesta forma | Modelo da ligação de água (diâmetro, material, cavalete) | M | OUT-CAD |
| TR-579; SG-556 | Cadastrar ramal de espera para a UC | sim (V poss.; S crít.) | Não atende: C3 (sem atributo) | Não planejado | Atributo da ligação | P | OUT-CAD |
| TR-580; SG-557 | Atualizar dados técnicos da ligação de esgoto | sim (V poss.; S crít.) | Parcial: C14 (situação do esgoto) | Não planejado nesta forma | Modelo da ligação de esgoto | M | OUT-CAD |
| TR-581; SG-558 | Gerar comunicado de cobrança de esgoto com texto parametrizado | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Modelo de comunicado no serviço de documentos e notificações | P | ENT-11 |
| TR-582; SG-559 | Atualizar localização da UC (setor, quadra, lote; S: rota e sequência) | sim (V poss.; S crít.) | Atende: C14 (localidade, setor, quadra, lote, sublote, rota e sequência em `UnitInput`) | Planejado: P1.3 | Nenhuma | P | — |
| TR-583; SG-560 | Cadastrar (S) ou excluir a opção de débito em conta da UC | sim (V poss.; S crít.) | Não atende: C0 | Não planejado (débito automático; leiautes do G4) | Cadastro de optantes por convênio | M | OUT-ARR |
| TR-584; SG-561 | Atualizar atributos do imóvel (inscrição, edificação, área, moradores, fonte própria, pavimentação, caixa d’água, cisterna; S: caixa de gordura, válvula de retenção, padrão, fossa) | sim (V poss.; S crít.) | Parcial: C14 (inscrição, área construída, JSON `properties`) | Planejado: P1.3 (`property_profiles`) | Conferir a lista mínima dos editais no perfil de imóvel | P | — |
| TR-585; SG-562 | Atualizar categoria, tipo de tarifa e número de economias | sim (V poss.; S crít.) | Parcial: C14 (subcategorias com economias; sem tipo de tarifa) | Parcial: P1.3; P2.5 | Tipo de tarifa por UC | P | OUT-CAD |
| TR-586; SG-563 | Atualizar rota de leitura (S: setor, rota e sequência) | sim (V poss.; S crít.) | Atende: C14 | Planejado: P1.3 | Nenhuma | P | — |
| TR-587; SG-564 | Cadastrar isenções de tarifa para a UC | sim (V poss.; S crít.) | Não atende: C2 | Parcial: P1.5 (tarifa social); outras isenções não previstas | Isenção com vigência e motivo | M | OUT-FAT |
| TR-588; SG-565 | Vencimento alternativo escolhido entre os dias definidos para o grupo de faturamento | sim (V poss.; S crít.) | Parcial: C13 | Parcial: P2.5 (ciclo); CB-03 do levantamento 13 | Lista de dias por grupo e validação da escolha | P | OUT-FAT |
| TR-589; SG-566 | Centralizar a UC em órgão pagador cadastrado | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Vínculo UC–órgão centralizador | M | ENT-16 |
| TR-590; SG-567, SG-571 | V: gerar notificação e ordem de corte para a UC; S: permissão por UC para gerar notificação e ordem de corte (item repetido no edital) | sim (V poss.; S crít.) | Não atende: C0 | Parcial: P3.4 (OS com bloqueio de corte); P4.1 (R8) | Indicador por UC que impede notificação e OS de corte, com motivo | M | ENT-04 |
| TR-591; SG-568 | Instalar e desinstalar hidrômetro da UC | sim (V poss.; S crít.) | Não atende: C2 (schema `metering` sem API) | Não planejado como operação de atendimento | Operação com OS gerada (fronteira com hidrometria) | M | OUT-HID |
| TR-592; SG-569 | Consultar o histórico de trocas de hidrômetro da UC | sim (V poss.; S crít.) | Não atende: C2 | Não planejado | Consulta sobre o histórico de instalação | P | OUT-HID |
| TR-593; SG-570 | Consultar o histórico de cortes e religações da UC | sim (V poss.; S crít.) | Parcial: C6 | Planejado: P3.4 | Gravar a OS em `service_order_id` no encerramento | P | — |

### 2.4 OS abertas pelo atendimento (UC, usuário comercial, logradouro, órgão centralizador)

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| TR-594; SG-572 | Recursos de atendimento sobre solicitações de OS (caput) | sim (V poss.; S crít.) | Não atende: C1 | Parcial: P3.4 (OS mínima) | Ver bloco E | G | ENT-03 |
| TR-595, TR-604, TR-612; SG-573, SG-583, SG-592 | Consultar e manter as OS da UC, do usuário comercial e do logradouro | sim (V poss.; S crít.) | Não atende: C1 | Parcial: P3.4 | Consulta por UC, cliente e logradouro | M | ENT-03 |
| TR-596, TR-605; SG-574, SG-584 | Gerar OS para a UC e para o usuário comercial | sim (V POC-42; S crít.) | Não atende: C1 | Parcial: P3.4 | Abertura por tipo de serviço do catálogo | M | ENT-03 |
| TR-613; SG-593 | Gerar OS para logradouro, mostrando a imagem do local por georreferenciamento (Google Maps) | sim (V poss.; S crít.) | Não atende: C1 | Não planejado | OS sem UC, com ponto no mapa (`sigsan-gis` existe sem camada de OS) | M | ENT-03 |
| TR-524, TR-597, TR-606, TR-614; SG-481, SG-575, SG-585, SG-594 | Programar a execução da OS, informando equipe ou funcionário | sim (V POC-45; S crít.) | Não atende: C1 | Não planejado (P3.4 não cita programação) | Agenda por equipe e dia, com roteirização simples | G | ENT-03 |
| TR-526, TR-598, TR-607, TR-615; SG-483, SG-576, SG-586, SG-595 | Cancelar OS pendente ou não encerrada, com motivo e parecer | sim (V POC-45; S crít.) | Não atende: C1 | Parcial: P3.4 | Motivos parametrizados | P | ENT-03 |
| SG-577, SG-587, SG-596 | Desdobrar OS em outra com vínculo de dependência: a original só executa depois da desdobrada | sim (S crít.) | Não atende: C1 (coluna `parent_order_id` sem regra) | Não planejado | Dependência entre OS no ciclo de vida | M | ENT-03 |
| TR-599, TR-608, TR-616; SG-578, SG-588, SG-597 | Encerrar OS informando: executada ou não; destino da não executada (cancelamento, reprogramação, desdobramento; S: postergação); parecer; executor; períodos de deslocamento, espera e execução; veículo; materiais; serviços adicionais; troca do serviço | sim (V POC-43; S crít.) | Não atende: C1 | Parcial: P3.4 (encerramento mínimo) | Baixa completa e parametrizável por serviço | G | ENT-03 |
| TR-600; SG-579 | Atendimento a usuários eventuais, sem UC padrão (caput) | sim (V poss.; S crít.) | Parcial: C11 | Não planejado | Fluxo do cliente avulso | M | ENT-16 |
| TR-601; SG-580 | Cadastro de usuário comercial (nome, CPF/CNPJ, endereço, telefone) com identificador próprio e dígito verificador | sim (V POC-51; S crít.) | Parcial: C11 (sem identificador com DV para cliente) | Não planejado | Identificador com DV para cliente avulso e fatura de serviço avulso (a POC-51 pede a fatura) | M | ENT-16 |
| TR-602; SG-581 | Localizar usuário comercial por matrícula, CPF/CNPJ ou nome | sim (V poss.; S crít.) | Parcial: C11 (`POST /customers/search` por nome ou documento) | Parcial: P0.22 | Busca pelo identificador do cliente avulso | P | ENT-16 |
| TR-603; SG-582 | Tela única do usuário comercial: OS, faturamento de serviços, compensação de valores | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Visão do cliente avulso | M | ENT-16 |
| TR-609; SG-589 | Ver serviço faturado do usuário comercial; alterar vencimento, reimprimir e cancelar o documento de cobrança | sim (V poss.; S crít.) | Não atende: C2 | Parcial: P3.6 (NFS-e de serviço avulso) | Documento de cobrança avulso com ciclo de vida | M | ENT-16 |
| TR-610; SG-590 | Gestão das OS geradas para logradouros (caput) | sim (V poss.; S crít.) | Não atende: C1 | Não planejado | Ver linha de OS por logradouro | M | ENT-03 |
| TR-611; SG-591 | Pesquisar logradouro por código próprio ou por UF, município, bairro, tipo e nome | sim (V poss.; S crít.) | Parcial: C3 (logradouro como texto no endereço, sem cadastro com código) | Parcial: P1.3 (endereço R2, `block_faces`) | Cadastro de logradouros com código | M | OUT-CAD |
| TR-617; SG-598 | Localizar órgão centralizador por código ou nome | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Cadastro de órgão centralizador | P | ENT-16 |
| TR-618; SG-599 | Tela única do órgão centralizador: faturas, cadastro, faturamento, UCs centralizadas, valores a compensar | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Visão do órgão centralizador | M | ENT-16 |
| TR-619; SG-600 | Ver, reimprimir (simples ou detalhada) e alterar vencimento das faturas centralizadas | sim (V poss.; S crít.) | Não atende: C2 | Não planejado | Fatura agrupada com código de barras único (fronteira com faturamento) | M | ENT-16 |

### 2.5 Gestão de serviços e OS no backoffice

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| TR-495; SG-448 | Procedimentos e controles para os serviços de campo e as tarefas administrativas correspondentes (caput) | sim (V poss.; S crít.) | Não atende: C0 | Parcial: P3.4 (OS mínima) | Módulo `field` com POM, entidades e API | G | ENT-03 |
| TR-496; SG-449 | Cadastro paramétrico de códigos de serviço e políticas, atualizável a qualquer tempo (caput) | sim (V poss.; S crít.) | Não atende: C0 | Não planejado (P3.4 não define catálogo) | Catálogo de serviços versionado por tenant (épico E1 do levantamento 04) | G | ENT-01 |
| TR-497; SG-450 | Serviço: descrição | sim (V POC-41; S crít.) | Não atende: C0 | Não planejado | Campo do catálogo | P | ENT-01 |
| TR-498; SG-451 | Serviço: código | sim (V POC-41; S crít.) | Não atende: C0 | Não planejado | Campo do catálogo | P | ENT-01 |
| TR-499; SG-452 | Serviço: grupo | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Campo do catálogo | P | ENT-01 |
| TR-500; SG-453 | Serviço: tipo de faturamento (obrigatório, opcional, variável, não faturável) | sim (V POC-41; S crít.) | Não atende: C0 | Não planejado | Regra de cobrança no encerramento | P | ENT-01 |
| TR-501; SG-454 | Serviço: unidade de medida | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Campo do catálogo | P | ENT-01 |
| TR-502; SG-455 | Serviço: setor de controle da execução | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Roteamento da OS por setor | P | ENT-01 |
| TR-503; SG-456 | Serviço: número máximo de parcelas | sim (V POC-41; S crít.) | Não atende: C0 | Não planejado | Usado no parcelamento do serviço (P4.1) | P | ENT-01 |
| TR-504; SG-457 | Serviço: cobrança de juros | sim (V POC-41; S crít.) | Não atende: C0 | Não planejado | Campo do catálogo | P | ENT-01 |
| SG-458 | Serviço: incidência de imposto | sim (S crít.) | Não atende: C0 | Não planejado | Ligar ao motor fiscal (NFS-e de serviço, P3.6) | P | ENT-01 |
| TR-505; SG-459 | Serviço: cobrança de parcela à vista | sim (V POC-41; S crít.) | Não atende: C0 | Não planejado | Campo do catálogo | P | ENT-01 |
| TR-506; SG-460 | Serviço: percentual mínimo da parcela à vista | sim (V POC-41; S crít.) | Não atende: C0 | Não planejado | Campo do catálogo | P | ENT-01 |
| TR-507; SG-461 | Serviço: prazo máximo de execução em dias ou horas | sim (V POC-41; S crít.) | Não atende: C0 | Parcial: P3.4 (prazos NR 11); `sac.response_days` | Prazo por serviço, dias úteis ou corridos, com feriados (AT-03 do levantamento 13) | P | ENT-01 |
| TR-508; SG-462 | Serviço: número de prioridade | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Campo do catálogo | P | ENT-01 |
| TR-509; SG-463 | Serviço: quantidade prevista de membros da equipe | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Campo do catálogo | P | ENT-01 |
| TR-510; SG-464 | Serviço: número máximo de postergações | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Contador na OS | P | ENT-01 |
| TR-511; SG-465 | Serviço: até seis modelos de leiaute do formulário da ordem de execução | sim (V POC-41; S crít.) | Não atende: C0 | Não planejado | Modelos de impressão da OS | M | ENT-01 |
| TR-512; SG-466 | Serviço: rubricas contábeis (V: várias, normal e intra; S: uma) | sim (V POC-41; S crít.) | Não atende: C0 (`sigsan-accounting` vazio) | Não planejado | Depende do plano de contas da integração contábil | P | ENT-01 |
| TR-513; SG-467 | Serviço: definir se a abertura da OS gera termo (documento) e qual | sim (V POC-41; S crít.) | Não atende: C0 | Não planejado | Vínculo serviço–modelo de termo | P | ENT-01 |
| TR-514; SG-468 | Cadastro de materiais e unidades de medida para registrar o uso na execução | sim (V POC-41; S crít.) | Não atende: C1 (`materials_used` JSON) | Não planejado | Cadastro de materiais | M | ENT-03 |
| TR-515; SG-469 | Cadastro de equipes de campo e membros | sim (V POC-41; S crít.) | Não atende: C1 (`team_id` sem tabela) | Não planejado | Cadastro de equipes | M | ENT-03 |
| TR-516; SG-470 | Cadastro de veículos | sim (V POC-41; S crít.) | Não atende: C1 (`vehicle_id` sem tabela) | Não planejado | Cadastro de veículos | P | ENT-03 |
| TR-517; SG-471 | Cadastro de setores (S: e rotas) de execução de serviço | sim (V POC-41; S crít.) | Não atende: C0 | Não planejado | Cadastro de setores de execução | P | ENT-03 |
| TR-518; SG-472 | Notificação por e-mail na abertura de OS, por serviço, com destinatários por setor de execução | sim (V poss.; S crít.) | Não atende: C0 | Parcial: P1.9 (outbox); P3.5 (notificações) | Regra de notificação por serviço e setor | M | ENT-11 |
| TR-519; SG-473 | Editor de termos gerados na abertura da OS, com formatação (fonte, alinhamento, cor, tabelas, quebra de página, fundo; V: imagens) e campos da UC e do serviço | sim (V POC-41; S crít.) | Não atende: C0 | Não planejado | Editor de modelos com variáveis e geração de PDF | G | ENT-01 |
| TR-520; SG-474 | Fluxo de abertura, programação e encerramento para UC, logradouro e usuários, com baixa parametrizável por serviço | sim (V POC-42, POC-43; S crít.) | Não atende: C1 | Parcial: P3.4 (OS mínima) | Máquina de estados da OS e formulário de baixa por serviço | G | ENT-03 |
| Anexo II n) POC-46 | Incluir anexos (imagens, arquivos) na abertura ou no encerramento da OS | sim (V POC-46) | Não atende: C8 | Parcial: P1.20 (pipeline de arquivos; escopo a estender) | Anexos da OS com varredura | M | ENT-03 |
| SG-475 | Kits de materiais definidos por serviço | sim (S crít.) | Não atende: C0 | Não planejado | Kit associado ao serviço do catálogo | M | ENT-03 |
| SG-477 | Informar desdobramento de serviços já na abertura da OS | sim (S crít.) | Não atende: C0 | Não planejado | Abertura com OS dependentes | P | ENT-03 |
| TR-522; SG-478 | OS que, após a abertura, ficam suspensas até aprovação e liberação para execução | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Estado “aguardando liberação” com aprovador | M | ENT-03 |
| SG-479 | Retrabalho: OS encerrada sem qualidade satisfatória é gerada de novo automaticamente, em estrutura própria de retrabalho | sim (S crít.) | Não atende: C0 | Não planejado | Vínculo de retrabalho e indicador | M | ENT-03 |
| TR-523; SG-480 | Registrar em toda OS veículos, materiais, equipe executora, data e tempo de execução | sim (V POC-43; S crít.) | Não atende: C1 | Parcial: P3.4 | Campos obrigatórios na baixa | M | ENT-03 |
| TR-525; SG-482 | Desprogramar OS programada indevidamente (S: e alterar o tipo de serviço) | sim (V POC-45; S crít.) | Não atende: C0 | Não planejado | Transição de estado com motivo | P | ENT-03 |
| TR-527; SG-484 | Prorrogar o prazo de execução da OS (S: com justificativa) | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Prorrogação limitada pelo máximo de postergações | P | ENT-03 |
| TR-528; SG-485 | Encerrar OS cujo serviço não foi executado (S: com justificativa) | sim (V POC-45; S crít.) | Não atende: C0 | Parcial: P3.4 | Motivos de não execução | P | ENT-03 |
| TR-531; SG-488 | Informar mais de um serviço executado no encerramento | sim (V POC-43; S crít.) | Não atende: C0 | Não planejado | Itens de serviço na baixa | P | ENT-03 |
| TR-532; SG-489 | Acompanhar prazos de execução, priorizando as OS com prazo mais curto ou a vencer | sim (V POC-44; S crít.) | Não atende: C0 | Parcial: P3.4; `sac.response_days` | Painel de prazos por setor e situação | M | ENT-03 |
| TR-533; SG-490 | Abrir OS em lote para UCs filtradas (situação, categoria, hidrômetro, data de instalação, rotas, consumo) | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Geração em lote no worker (P2.1) | M | ENT-03 |
| TR-534; SG-491 | Exibir OS solicitadas e programadas em mapa (Google Maps ou base cartográfica), com filtro por endereço, equipe e tipo | sim (V poss.; S crít.) | Não atende: C0 (`sigsan-gis` sem camada de OS) | Não planejado | Camada de OS no GIS | M | ENT-03 |

### 2.6 Efeitos comerciais da OS: corte, religação, serviços e materiais cobrados

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| TR-529; SG-486 | Restringir a programação de OS de corte de grandes consumidores ou clientes definidos a equipes especiais | sim (V poss.; S crít.) | Não atende: C0 | Parcial: P3.4; parâmetros `cut.*` (G9) | Regra de equipe por perfil de cliente | P | ENT-04 |
| TR-530; SG-487 | Tabela de preços de serviços com vigência; valor lançado na conta seguinte do solicitante | sim (V poss.; S crít.) | Não atende: C2 | Não planejado (P2.5 não trata serviço cobrado) | Lançamento de serviço a faturar ao encerrar a OS | M | ENT-04 |
| TR-535; SG-497 | Faturamento dos materiais usados na execução (caput) | sim (V poss.; S crít.) | Não atende: C2 | Não planejado | Ver linhas seguintes | M | ENT-04 |
| TR-536; SG-498 | Definir quais serviços cobram materiais | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Indicador no catálogo | P | ENT-04 |
| TR-537; SG-499 | Tabela de preços de materiais com vigência | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Tabela com vigência | P | ENT-04 |
| TR-538; SG-500 | Cobrar automaticamente na fatura os materiais informados no encerramento | sim (V poss.; S crít.) | Não atende: C2 | Não planejado | Item de conta gerado pela baixa | M | ENT-04 |
| TR-539; SG-501 | Registrar a execução de corte por nível (cavalete, ramal, supressão) para a UC | sim (V poss.; S crít.) | Não atende: C6 (só o gancho no histórico) | Parcial: P3.4 (OS com bloqueio de corte) | Níveis de corte e efeito na situação da ligação | M | ENT-04 |
| TR-540; SG-502 | Registrar a religação (S: por nível) para a UC | sim (V poss.; S crít.) | Não atende: C6 | Parcial: P3.4 (religação sem taxa quando faltar prova) | Efeito na situação da ligação | M | ENT-04 |
| SG-503 | Registrar corte de ligação de esgoto | sim (S crít.) | Não atende: C0 | Não planejado | Situação do esgoto pela OS | P | ENT-04 |
| SG-504 | Registrar religação de ligação de esgoto | sim (S crít.) | Não atende: C0 | Não planejado | Idem | P | ENT-04 |
| SG-505 | Cartas de liberação de instalação de esgoto com controle de prazos, notificações e multas | sim (S crít.) | Não atende: C0 | Não planejado | Processo com prazo e penalidade | M | ENT-04 |
| TR-541 | Selecionar uma ou várias faturas para impressão de uma vez | não (V poss.) | Não atende: C2 | Não planejado | Impressão em lote de PDFs | P | ENT-15 |
| TR-543 | Gerar a Certidão Negativa Anual na leitura seguinte à quitação das pendências do ano anterior | não (V poss.) | Não atende: C2 | Não planejado (P3.20 entrega conta paga, não a declaração anual) | Declaração anual de quitação (Lei 12.007/2009) gerada no ciclo | M | ENT-08 |

### 2.7 Empreiteiras terceirizadas e estoque

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| SG-492 | Gestão de contratos de empreiteiras terceirizadas (caput) | sim (S crít.) | Não atende: C0 | Não planejado | Módulo de terceirizadas (épico E8/E23 do levantamento 04) | M | ENT-06 |
| SG-493 | Cadastro de empresas, equipes e membros terceirizados | sim (S crít.) | Não atende: C0 | Não planejado | Cadastro | P | ENT-06 |
| SG-494 | Contratos de terceirização pagos por valor fixo ou por serviço executado | sim (S crít.) | Não atende: C0 | Não planejado | Contrato com regra de pagamento | M | ENT-06 |
| SG-495 | Penalidades contratuais por dias de atraso ou por percentual de serviços fora do prazo | sim (S crít.) | Não atende: C0 | Não planejado | Regras de penalidade | P | ENT-06 |
| SG-496 | Medição automática dos serviços terceirizados, com valor a pagar e penalidades | sim (S crít.) | Não atende: C0 | Não planejado | Boletim de medição | M | ENT-06 |
| TR-521; SG-476 | Integração com o estoque (V: entradas e custos ao menos diariamente; S: envio on-line da previsão de materiais dos kits na abertura) | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Conector com o sistema de estoque do cliente (não nomeado nos editais) | M | ENT-07 |

### 2.8 App de OS em campo (Android, offline, fotos, GPS)

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| TR-620, TR-638; SG-506 | Módulo móvel de execução de OS, integrado e na mesma plataforma, em Android, recebendo as OS do sistema | sim (V poss.; S crít.) | Não atende: C0 | Não planejado (P4.4 é app de leitura em PWA) | App de campo; ver decisão D1 | G | ENT-05 |
| TR-621, TR-639 | App instalado por APK, com banco de dados próprio no aparelho | não (V poss.) | Não atende: C0 | Não planejado; conflita com a P4.4 em PWA | Empacotamento Android (decisão D1) | M | ENT-05 |
| TR-621, TR-640 | Coleta de no mínimo 3 fotos por ocorrência pelas equipes, enviadas quando houver sinal | não (V poss.) | Não atende: C0 | Não planejado | Fotos com fila de envio | M | ENT-05 |
| TR-622; SG-507 | Monitorar a execução e rastrear o deslocamento diário e por OS (distâncias, velocidades, tempos de percurso e de parada) | sim (V POC-52; S crít.) | Não atende: C1 (`gps_track` sem uso) | Não planejado | Coleta de trilha GPS e cálculo de métricas | G | ENT-05 |
| TR-623; SG-508 | Acesso ao app por usuário e senha, com perfis de operador e administrador | sim (S crít.) | Parcial: Keycloak e papéis do backoffice existem; não há app | Não planejado | Client móvel no Keycloak e papéis de campo | P | ENT-05 |
| TR-624; SG-509 | Registrar no app veículo (S: placa e distância), materiais, início e fim com foto (S: tempos), campos parametrizáveis por serviço, permitindo encerrar qualquer OS no aparelho | sim (S crít.) | Não atende: C1 | Não planejado | Formulário dinâmico de baixa no app | G | ENT-05 |
| TR-625 | Obter no app dados do veículo, rota, trajeto, tempos, distância, materiais disponíveis e o cadastro da infraestrutura de água e esgoto | não (V poss.) | Não atende: C0 | Não planejado | Pacote de dados por roteiro | M | ENT-05 |
| TR-626 | Usar os dados das OS para mapeamento colaborativo da infraestrutura (retroalimentar o cadastro técnico) | não (V poss.) | Não atende: C0 (`sigsan-gis` sem edição de campo) | Não planejado | Proposta de edição georreferenciada com aprovação | M | ENT-05 |
| TR-627; SG-510 | Informar a paralisação da execução com motivo (V: e fotos) | sim (S crít.) | Não atende: C0 | Não planejado | Estado “paralisada” | P | ENT-05 |
| TR-628; SG-511 | Programar e enviar OS aos aparelhos das equipes e alterar prioridades | sim (S crít.) | Não atende: C0 | Não planejado | Distribuição de OS por equipe | M | ENT-05 |
| TR-629; SG-512 | Acompanhar as OS em campo, o deslocamento dos veículos e a produtividade em mapa georreferenciado (Google Maps ou outra base) | sim (V POC-53; S crít.) | Não atende: C0 | Não planejado | Painel de campo no mapa | G | ENT-05 |
| TR-630; SG-513 | Acompanhar em tempo real a produtividade das equipes (deslocamento e serviços executados) | sim (S crít.) | Não atende: C0 | Não planejado | Indicadores por equipe | M | ENT-05 |
| TR-631; SG-514 | Capturar fotos em campo e associá-las à UC | sim (S crít.) | Não atende: C8 | Não planejado | Fotos ligadas à UC | P | ENT-05 |
| TR-632; SG-515 | Ver em tempo real as fotos tiradas em campo (V: por fase da execução, como chave para a fase seguinte) | sim (S crít.) | Não atende: C0 | Não planejado | Galeria por OS e fase | M | ENT-05 |
| TR-633; SG-516 | Emitir no aparelho notificações ao usuário com as fotos do imóvel | sim (S crít.) | Não atende: C0 | Não planejado | Notificação de irregularidade com foto | M | ENT-05 |
| TR-634; SG-517 | Enviar automaticamente os serviços executados por GPRS/GSM em intervalo predefinido | sim (S crít.) | Não atende: C0 | Não planejado | Sincronização periódica | P | ENT-05 |
| TR-635; SG-518 | Identificar os executores por crachá com código de barras ou QR | sim (S crít.) | Não atende: C0 | Não planejado | Leitura de código pela câmera | P | ENT-05 |
| TR-636; SG-519 | Abrir OS em campo, em horário normal ou plantão | sim (S crít.) | Não atende: C0 | Não planejado | Abertura no app | P | ENT-05 |
| TR-637 | Ler QR ou código de barras das OS | não (V poss.) | Não atende: C0 | Não planejado | Código na OS impressa | P | ENT-05 |
| TR-641 | Importar materiais, funcionários de campo e cadastros de consistência (hidrômetros, usuários, lacres) | sim (V POC-54) | Não atende: C0 | Não planejado | Carga de cadastros no app | M | ENT-05 |
| TR-642 | Sincronizar dados cadastrais | não (V poss.) | Não atende: C0 | Parcial: P4.4 (sincronização com chave de idempotência, só leitura) | Mesmo padrão para OS | M | ENT-05 |
| TR-643 | Integração com banco de dados | não (V poss.) | Não atende: C0 | Não planejado | API de sincronização | P | ENT-05 |
| TR-644 | Atualização a qualquer hora e lugar com internet | não (V poss.) | Não atende: C0 | Não planejado | Idem | P | ENT-05 |
| TR-645 | Sincronizar OS gerada da aprovação de solicitação importada do sistema corporativo | não (V poss.) | Não atende: C0 | Não planejado | Idem | P | ENT-05 |
| TR-646 | Filtrar OS por pendentes, concluídas e canceladas | não (V poss.) | Não atende: C0 | Não planejado | Filtro no app | P | ENT-05 |
| TR-647 | Rastreamento GPS integrado no tablet ou smartphone | não (V poss.) | Não atende: C0 | Não planejado | Geolocalização em segundo plano | P | ENT-05 |
| TR-648 | Requisitos para falha de comunicação (caput) | não (V poss.) | Não atende: C0 | Parcial: P4.4 (offline-first, só leitura) | Operação offline do app de OS | M | ENT-05 |
| TR-649 | Acumular as ocorrências e transmiti-las quando a comunicação voltar | não (V poss.) | Não atende: C0 | Parcial: P4.4 (padrão de fila offline) | Fila local com reenvio idempotente | M | ENT-05 |
| TR-650 | Preencher e encerrar a ocorrência sem sinal e transmitir ao recuperar o sinal | não (V poss.) | Não atende: C0 | Parcial: P4.4 | Idem | M | ENT-05 |
| TR-651 | Toda atualização refletida nos sistemas da VISAN; pontos de integração avaliados com a TI | não (V poss.) | Não atende: C0 | Não planejado | Eventos da outbox para integrações | P | ENT-05 |
| TR-652 | A critério da VISAN, formulários de cadastro técnico dentro da OS, baixa só com preenchimento e nova OS ao setor de cadastro técnico | não (V poss.) | Não atende: C0 | Não planejado | Formulário obrigatório e OS derivada | M | ENT-05 |

### 2.9 Portal de autoatendimento, totem e filas

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| TR-836 (portal); SG-601 | Portal de atendimento na internet, com unicidade entre o que o portal publica e o sistema de gestão (caput) | sim (S ess.; V poss.) | Não atende: C0, C16 | Planejado: P3.3, P3.20 | Portal no realm `ziipsan-portal`, sobre as mesmas APIs | G | — |
| TR-836 (totem); SG-614 | Terminais de autoatendimento (totem) e app com o mesmo conjunto de funções (caput) | sim (S ess.; V poss.) | Não atende: C0 | Não planejado | Totem como portal em modo quiosque (decisão D4) | M | ENT-10 |
| TR-837; SG-602, SG-615 | Atualizar (S: cadastrar) e-mail e celular pelo portal e pelo totem | sim (S ess.; V poss.) | Parcial: C5 (contatos no cadastro, sem canal do consumidor) | Parcial: P3.3 (conta do consumidor por e-mail ou celular); atualização do cadastro do titular não prevista | Gravar no cadastro do titular, com verificação e opt-in | M | ENT-08 |
| TR-838, TR-658; SG-603, SG-713 | Consultar e imprimir a ficha cadastral (S app: só autenticado) | sim (S ess./crít.; V poss.) | Não atende: C0 | Não planejado | PDF da ficha do titular | P | ENT-08 |
| TR-839, TR-659; SG-604, SG-616, SG-715 | Ver e imprimir o histórico de leitura e consumo dos últimos 12 meses (V portal: ou por período) no portal, no totem e no app | sim (V POC-55; S ess./crít.) | Não atende: C2 | Parcial: P3.20 (contas e pagamentos de 120 meses; leitura e consumo não citados) | Incluir leitura e consumo no histórico | M | ENT-08 |
| TR-840, TR-660; SG-605, SG-617, SG-717 | Ver faturas pendentes e imprimir a 2ª via (V portal: várias de uma vez) no portal, no totem e no app | sim (V POC-55, POC-69; S ess./crít.) | Não atende: C2 | Parcial: P3.3 no portal; totem e app não planejados | Mesma API nos três canais | P | ENT-08 |
| TR-841; SG-606, SG-618, SG-721 | Ver as OS da UC (V: geradas pelo autoatendimento; S app: exceto internas ou sigilosas, só autenticado) | sim (S ess./crít.; V poss.) | Não atende: C1 | Parcial: P3.3 (protocolo no portal) | Expor OS e marcar as internas ou sigilosas | M | ENT-08 |
| TR-842; SG-607, SG-619 | Simular parcelamento de débitos no portal e no totem | sim (V POC-69; S ess.) | Não atende: C2 | Parcial: P4.1 (parcelamento com demonstrativo); canal do portal não citado | Simulador exposto no portal | M | ENT-08 |
| TR-843; SG-608, SG-620, SG-722 | Simular o valor da fatura por consumo informado (S app: e por tipo e quantidade de economias) | sim (V POC-69; S ess./crít.) | Não atende: C2 | Parcial: P1.5 (N4: simulador de conta) | Expor no portal, totem e app | P | ENT-08 |
| TR-844; SG-609, SG-621, SG-723 | Emitir a certidão de quitação anual de débitos | sim (V POC-69; S ess./crít.) | Não atende: C2 | Não planejado (P3.20 entrega conta paga, não a declaração anual) | Declaração anual de quitação (Lei 12.007/2009) | M | ENT-08 |
| TR-845; SG-610, SG-622, SG-726 | Ver a data da última leitura e a previsão da próxima | sim (S ess./crít.; V poss.) | Não atende: C2 | Não planejado | Calendário de leitura por grupo exposto ao consumidor | P | ENT-08 |
| TR-846; SG-611, SG-767 | Ver parâmetros de qualidade da água por ponto de coleta (S app: boletins e certificados) | sim (S ess./crít.; V poss.) | Não atende: C15 | Não planejado (qualidade da água só em 2028) | Publicação dos resultados do módulo `quality` | M | ENT-08 |
| TR-847, TR-848; SG-612, SG-623, SG-761 | Registrar solicitações de serviço pelo autoatendimento (S: falta d’água, entupimento, tapa-buraco, vazamentos, troca de registro) sem ir direto à programação: triagem pelo setor, liberação e retorno ao usuário | sim (S ess./crít.; V poss.) | Não atende: C0 | Parcial: P3.3 (protocolo no portal) | Fila de triagem por setor antes de virar OS | M | ENT-08 |
| TR-849; SG-613, SG-624, SG-766 | Consultar a tabela tarifária vigente (V e S app: e os valores de serviços) | sim (S ess./crít.; V poss.) | Não atende: C2 | Não planejado | Publicação da tabela vigente por tenant | P | ENT-08 |
| TR-850; SG-627 | Enviar informativos e avisos (S: notícias) aos usuários | sim (S ess.; V poss.) | Não atende: C0 | Não planejado | Mural de avisos no portal e envio pelo serviço de notificações | M | ENT-11 |
| SG-625 | Emitir senha de atendimento, com controle de filas parametrizável | sim (S ess.) | Não atende: C0 | Não planejado | Módulo de senhas e filas com prioridade legal (AT-10 do levantamento 13) | M | ENT-10 |
| SG-626 | Chamar a senha pelo próprio sistema comercial, sem abrir outro sistema | sim (S ess.) | Não atende: C0 | Não planejado | Chamada na tela de atendimento e painel de TV | P | ENT-10 |

### 2.10 App do cliente (Android e iOS)

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| TR-653; SG-705 | Apps para Android e iOS (ED-057 do 18b repete o item) | sim (V POC-55; S crít.) | Não atende: C0 | Não planejado (só portal web no P3) | App sobre o portal; decisão D1 | G | ENT-09 |
| SG-704 | Formato responsivo e unicidade das funções com o sistema de gestão (caput) | sim (S crít.) | Não atende: C16 | Parcial: P3.3 (portal web); P1.12 (tabelas no celular) | Mesma base do portal | M | ENT-09 |
| SG-779 | App publicado na Play Store e na App Store com o nome e a identidade visual do prestador | sim (S crít.) | Não atende: C0 | Não planejado | Publicação por tenant (white-label) nas contas do prestador | G | ENT-09 |
| SG-778 | Identidade visual do prestador no app (cores, tipografia, logos) | sim (S crít.) | Não atende: Pacote de marca planejado, não implementado | Parcial: P1.19 (marca do tenant); P3.21 (marca no portal) | Aplicar a marca do tenant no app | P | ENT-09 |
| SG-780 | Ao ser instalado, o app conecta direto à base do prestador, sem escolher numa lista de clientes da contratada | sim (S crít.) | Não atende: C0 | Não planejado | Build por tenant com o subdomínio fixo | P | ENT-09 |
| TR-654 | Qualquer usuário pode acessar o app para consultas e solicitações | não (V poss.) | Não atende: C0 | Parcial: P3.3 (cadastro aberto no realm do portal) | Funções públicas sem login | P | ENT-09 |
| TR-655 | Acesso restrito e seguro à base de dados | não (V poss.) | Parcial: RLS e isolamento por tenant provados; não há rotas do portal | Planejado: P3.3, P3.8 (pentest), P3.20 | Nenhuma além do plano | P | — |
| TR-656, TR-657; SG-706 | Autenticação: V por matrícula, inscrição, UC ou CPF, com senha criada no primeiro acesso; S por CPF (PF) ou CNPJ (PJ), com senha e aceite dos termos de uso e da política de privacidade | sim (S crít.; V poss.) | Não atende: C0 | Parcial: P3.3 (conta por e-mail ou celular, ADR-0022) | Divergência: o ADR-0022 usa e-mail ou celular; os editais pedem CPF/CNPJ ou matrícula como login (decisão D5) | M | ENT-09 |
| SG-707 | Redefinir senha e alterar o e-mail cadastrado | sim (S crít.) | Não atende: C0 | Planejado: P3.3 (recuperação de senha pelo consumidor) | Nenhuma além do plano | P | — |
| SG-708, SG-709 | Cadastro do responsável com nome e CPF/CNPJ validados contra o cadastro do prestador | sim (S crít.) | Não atende: C0 | Parcial: P3.20 (vínculo pelo código da conta mais CPF/CNPJ) | O ADR-0022 exige o código impresso; o edital valida só nome e documento | P | ENT-09 |
| SG-710 | E-mail com validação da conta informada | sim (S crít.) | Não atende: C0 | Planejado: P3.3 | Nenhuma além do plano | P | — |
| SG-711 | Cadastro não localizado: o app informa e orienta a procurar o atendimento | sim (S crít.) | Não atende: C0 | Parcial: P3.20 (resposta indistinta na vinculação) | Conciliar a mensagem orientativa com a resposta anti-enumeração | P | ENT-09 |
| SG-712 | Cadastro de acesso mantido pelo sistema de gestão, com atualização automática | sim (S crít.) | Não atende: C0 | Parcial: P3.3 (realm `ziipsan-portal`) | Espelhar a conta do portal no cadastro do titular | P | ENT-09 |
| SG-714 | Ver matrículas, endereços, economias e responsáveis vinculados (proprietário, morador, administrador), só autenticado | sim (S crít.) | Não atende: C0 | Parcial: P3.20 no portal web (uma conta, vários imóveis); app não planejado | Reusar no app | P | ENT-09 |
| SG-716 | Ver e imprimir o histórico de faturas dos últimos 12 meses, só autenticado | sim (S crít.) | Não atende: C2 | Parcial: P3.20 no portal web (120 meses); app não planejado | Reusar no app | P | ENT-09 |
| SG-718 | Ver os débitos do responsável por CPF/CNPJ, inclusive negativados, e imprimir a 2ª via | sim (S crít.) | Não atende: C2 | Parcial: P3.20 (por imóvel vinculado) | Visão por documento, somando os imóveis | M | ENT-09 |
| SG-719 | Ver o histórico de atendimentos da matrícula e do responsável | sim (S crít.) | Não atende: C0 | Parcial: P3.3 (protocolo) | Lista de protocolos do titular | M | ENT-09 |
| TR-662; SG-720 | Acompanhar solicitações pelo protocolo, com linha do tempo gráfica (tipo, situação, eventos, motivos, desdobramentos) | sim (V POC-55; S crít.) | Não atende: C0 | Parcial: P3.3 (protocolo no portal) | Linha do tempo do protocolo e das OS | M | ENT-09 |
| SG-724 | Emitir certidão negativa de débitos, só autenticado | sim (S crít.) | Não atende: C2 | Não planejado | Certidão com validação | P | ENT-09 |
| SG-725 | Emitir carta de anuência para documentos protestados, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado (protesto não previsto) | Depende do fluxo de protesto | P | OUT-COB |
| SG-727 | Consultar a disponibilidade de água e esgoto num local do município | sim (S crít.) | Não atende: C0 (`sigsan-gis` sem consulta pública) | Não planejado | Consulta por endereço ou ponto no mapa | M | ENT-09 |
| SG-728 | Ver em mapa os serviços em aberto de falta d’água e as intervenções no trânsito | sim (S crít.) | Não atende: C0 | Não planejado (incidente de rede, N2, só em 2028) | Camada pública de ocorrências | M | ENT-09 |
| TR-661; SG-729 | Solicitar serviços pelo app (caput) | sim (V POC-55; S crít.) | Não atende: C0 | Parcial: P3.3 (protocolo no portal) | Solicitações do catálogo (ENT-01) | M | ENT-09 |
| SG-730 | Solicitação pelo app: 2ª via da fatura | sim (S crít.) | Não atende: C0 | Parcial: P3.3 (2ª via no portal) | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-731 | Solicitação pelo app: 2ª via de boleto de cobrança | sim (S crít.) | Não atende: C0 | Parcial: P3.3 | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-732 | Solicitação pelo app: vazamento | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-733 | Solicitação pelo app: falta de água | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-734 | Solicitação pelo app: reclamação da qualidade da água | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-735 | Solicitação pelo app: reclamação da qualidade da pavimentação | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-736 | Solicitação pelo app: religação de água | sim (S crít.) | Não atende: C0 | Parcial: P3.4 (religação) | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-737 | Solicitação pelo app: viabilidade técnica | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-738 | Solicitação pelo app: dispensa de viabilidade técnica | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-739 | Solicitação pelo app: aprovação de projeto | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-740 | Solicitação pelo app: vistoria de caixa padrão | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-741 | Solicitação pelo app: “ressarcimento de dados” (texto do edital; sentido a esclarecer) | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-742 | Solicitação pelo app: ligação de água, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-743 | Solicitação pelo app: padronização de ligação de água, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-744 | Solicitação pelo app: ligação de esgoto, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-745 | Solicitação pelo app: simulação e solicitação de parcelamento, só autenticado | sim (S crít.) | Não atende: C0 | Parcial: P4.1 (parcelamento) | Tipo de solicitação no catálogo, com triagem | M | ENT-09 |
| SG-746 | Solicitação pelo app: alteração de vencimento, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-747 | Solicitação pelo app: prazo de pagamento, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-748 | Solicitação pelo app: débito em conta, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | M | ENT-09 |
| SG-749 | Solicitação pelo app: alteração do formato de entrega da fatura, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-750 | Solicitação pelo app: contestação de fatura, só autenticado | sim (S crít.) | Não atende: C0 | Parcial: P4.1 (contestação bloqueia corte) | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-751 | Solicitação pelo app: corte temporário da ligação, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-752 | Solicitação pelo app: transferência de responsabilidade, só autenticado | sim (S crít.) | Não atende: C0 | Parcial: P3.20 (troca de titularidade revoga o vínculo) | Tipo de solicitação no catálogo, com triagem | M | ENT-09 |
| SG-753 | Solicitação pelo app: reclamação de serviço, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-754 | Solicitação pelo app: defesa de notificação, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | P | ENT-09 |
| SG-755 | Solicitação pelo app: faturamento por autoleitura, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | M | ENT-09 |
| SG-756 | Solicitação pelo app: agendamento de serviços, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | M | ENT-09 |
| SG-757 | Solicitação pelo app: atualização cadastral do responsável, com campos parametrizados pelo prestador, só autenticado | sim (S crít.) | Não atende: C0 | Não planejado | Tipo de solicitação no catálogo, com triagem | M | ENT-09 |
| SG-758 | Validar e-mail, telefone e WhatsApp quando atualizados pelo app | sim (S crít.) | Não atende: C5 | Parcial: P3.3 (verificação do e-mail ou celular da conta) | Verificação também do WhatsApp | P | ENT-09 |
| SG-759 | Registrar autorização ou recusa de mensagens por e-mail, telefone e WhatsApp | sim (S crít.) | Parcial: C5, C10 (`consent_id` no contato) | Não planejado | Opt-in por canal e finalidade | P | ENT-11 |
| SG-760 | Cada solicitação do app pede e valida as informações específicas do serviço, conforme regras do sistema e carta de serviços | sim (S crít.) | Não atende: C0 | Não planejado | Formulário por tipo de serviço no catálogo | M | ENT-01 |
| TR-663; SG-762 | Anexar fotos (S: e PDF) e registrar a localização na solicitação (V: registro fotográfico de problemas) | sim (V POC-55; S crít.) | Não atende: C8 | Parcial: P1.20 (varredura de arquivos; escopo a estender) | Anexos e ponto no mapa na solicitação | M | ENT-09 |
| SG-763 | Administrar quais solicitações ficam disponíveis no app e quais exigem autenticação | sim (S crít.) | Não atende: C0 | Não planejado | Configuração no catálogo | P | ENT-01 |
| SG-764 | Acesso rápido e cópia do código de barras das faturas e documentos de cobrança | sim (S crít.) | Não atende: C2 | Parcial: P3.3 (2ª via e Pix no portal) | Botão copiar no app | P | ENT-09 |
| SG-765 | Compartilhar o PDF das faturas com outros apps do aparelho | sim (S crít.) | Não atende: C0 | Não planejado | Compartilhamento nativo | P | ENT-09 |
| SG-768 | Ver dentro do app, sem redirecionar, conteúdos do site do prestador (caput) | sim (S crít.) | Não atende: C0 | Não planejado | Conteúdo do tenant publicado no app | P | ENT-09 |
| SG-769 | Conteúdo: perguntas frequentes | sim (S crít.) | Não atende: C0 | Não planejado (a ajuda da P1.21 é do Ziip para o tenant) | Página de conteúdo do tenant | P | ENT-09 |
| SG-770 | Conteúdo: entendendo minha fatura | sim (S crít.) | Não atende: C0 | Não planejado | Idem | P | ENT-09 |
| SG-771 | Conteúdo: locais de atendimento | sim (S crít.) | Não atende: C0 | Não planejado | Idem | P | ENT-09 |
| SG-772 | Conteúdo: fale conosco | sim (S crít.) | Não atende: C0 | Não planejado | Idem | P | ENT-09 |
| SG-773 | Conteúdo: descritivo dos serviços | sim (S crít.) | Não atende: C0 | Não planejado | Gerado do catálogo (carta de serviços) | P | ENT-09 |
| SG-774 | Notificação push sobre emissão de cobrança, falta d’água, andamento de solicitações, intervenções no trânsito e comunicação e marketing | sim (S crít.) | Não atende: C0 | Não planejado | Push (FCM e APNs) pelo serviço de notificações | G | ENT-11 |
| SG-775 | Na solicitação, o responsável escolhe se quer notificações do andamento | sim (S crít.) | Não atende: C0 | Não planejado | Preferência por protocolo | P | ENT-11 |
| SG-776 | Consultar no app todas as notificações e comunicados recebidos | sim (S crít.) | Não atende: C0 | Não planejado | Caixa de mensagens do titular | M | ENT-11 |
| SG-777 | Autenticação biométrica (digital, reconhecimento facial) | sim (S crít.) | Não atende: C0 | Parcial: ADR-0022: MFA opcional com WebAuthn/passkey | Passkey ou biometria do aparelho no app | P | ENT-09 |
| SG-781 | Tutorial de uso pela opção “Ajuda” | sim (S crít.) | Não atende: C0 | Não planejado (P1.21 é ajuda do backoffice) | Tutorial do consumidor | P | ENT-09 |
| SG-782 | Funcionar corretamente com os recursos de acessibilidade do aparelho habilitados | sim (S crít.) | Não atende: C0 | Parcial: RNF-60; ADR-0022 item 5 (WCAG 2.1 AA no portal) | Testes de acessibilidade também no app | M | ENT-09 |

### 2.11 Omnichannel (WhatsApp, redes sociais, webchat, e-mail)

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| TR-851; SG-628 | Plataforma multicanal integrada (V: WhatsApp, Messenger, webchat; S: também Instagram e Telegram), com unicidade de funções em relação ao sistema de gestão | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Plataforma de conversas; decisão D2 | G | ENT-12 |
| SG-629; TR-881 | Estrutura para controlar e gerir toda a comunicação dos canais e aumentar a eficiência do atendimento | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Gestão de filas e indicadores | M | ENT-12 |
| SG-631 | Atendimento pelo canal mais conveniente, somado a respostas e serviços automatizados (chatbot) | sim (S crít.) | Não atende: C0 | Não planejado | Ver bloco L | M | ENT-13 |
| SG-632 | Ambiente seguro e cômodo ao cliente e canal de relacionamento explorável pela autarquia | sim (S crít.) | Não atende: C0 | Não planejado | Requisito genérico; atendido pela entrega | P | ENT-12 |
| TR-852; SG-633 | Interface de atendimento dentro do sistema comercial, com troca de mensagens de todos os canais na tela de atendimento | sim (V POC-70; S crít.) | Não atende: C0 | Não planejado | Caixa de conversas na tela do protocolo | G | ENT-12 |
| TR-853, TR-869; SG-648 | Toda a conversa, da primeira interação ao fim, registrada no protocolo da matrícula e acessível no sistema comercial | sim (V POC-70; S crít.) | Não atende: C0 | Parcial: P3.4 (protocolo) | Mensagens gravadas no protocolo, com retenção | M | ENT-12 |
| TR-883; SG-647 | Gerar automaticamente um protocolo no sistema comercial para cada atendimento | sim (V poss.; S crít.) | Não atende: C0 | Parcial: P3.4 | Abertura automática pelo canal | P | ENT-12 |
| TR-870 | Mensagens automáticas aos clientes em fila, com posição e tempo médio de espera | não (V poss.) | Não atende: C0 | Não planejado | Recurso da fila | P | ENT-12 |
| TR-871 | Alerta sonoro na tela de atendimento para conversas pendentes dos canais | não (V poss.) | Não atende: C0 | Não planejado | Recurso da interface | P | ENT-12 |
| TR-872 | Correção ortográfica automática em português nas respostas (texto truncado no edital) | não (V poss.) | Não atende: C0 | Não planejado | Corretor no campo de resposta | P | ENT-12 |
| TR-873 | Questionário inicial customizável antes do atendimento | não (V poss.) | Não atende: C0 | Não planejado | Pré-atendimento configurável | P | ENT-12 |
| TR-874; SG-649 | Mensagens customizáveis de saudação e encerramento (S: e de espera) | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Modelos por tenant | P | ENT-12 |
| TR-875 | Enviar e receber arquivos nos diálogos, acessíveis no módulo de atendimento | sim (V POC-75) | Não atende: C0 | Parcial: P1.20 (varredura de arquivos) | Anexos de conversa com varredura | M | ENT-12 |
| TR-876 | Gestão em tempo real do status dos agentes e da fila de espera | não (V poss.) | Não atende: C0 | Não planejado | Painel do supervisor | M | ENT-12 |
| TR-877, TR-887 | Número de atendimentos e sessões de chat simultâneas configurável por atendente | não (V poss.) | Não atende: C0 | Não planejado | Limite por agente | P | ENT-12 |
| TR-878 | Roteamento das interações por regras de negócio parametrizáveis | não (V poss.) | Não atende: C0 | Não planejado | Regras de distribuição | M | ENT-12 |
| TR-879 | Sem limite de filas, equipes ou atendentes cadastrados | não (V poss.) | Não atende: C0 | Não planejado | Modelo comercial sem cota | P | ENT-12 |
| TR-880 | Franquia mínima de mensagens sem interrupção nem cobrança adicional, revisável se ultrapassada | não (V poss.) | Não atende: C0 | Não planejado (modelo comercial) | Custo de mensagem da Meta e de SMS a precificar (decisão D2) | P | ENT-12 |
| TR-884; SG-650 | Transferir o atendimento para outra equipe (V: com mensagens de transferência customizáveis) | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Transferência entre filas | P | ENT-12 |
| TR-888 | Transferir diálogos entre atendentes | não (V poss.) | Não atende: C0 | Não planejado | Transferência entre agentes | P | ENT-12 |
| TR-885; SG-652 | Consultar o histórico de atendimentos anteriores na interface de atendimento | sim (V poss.; S crít.) | Não atende: C0 | Parcial: P3.4 (protocolo) | Histórico do titular ao lado da conversa | P | ENT-12 |
| TR-889 | Horários de bloqueio de entrada de atendimentos configuráveis | não (V poss.) | Não atende: C0 | Não planejado | Calendário do canal | P | ENT-12 |
| TR-890 | Webchat na página institucional e atendimento por e-mail | não (V poss.) | Não atende: C0 | Não planejado | Widget de webchat e caixa de e-mail ligada ao protocolo | M | ENT-12 |
| TR-891 | Campanhas de envio automático de mensagens (inadimplência, falta d’água e outros) | não (V poss.) | Não atende: C0 | Não planejado | Campanhas segmentadas com opt-in | M | ENT-11 |
| SG-651 | Mensagens rápidas pré-definidas | sim (S crít.) | Não atende: C0 | Não planejado | Respostas prontas | P | ENT-12 |
| SG-653 | Contabilizar o tempo de atendimento | sim (S crít.) | Não atende: C0 | Não planejado | Tempos por conversa | P | ENT-12 |
| SG-654 | Pesquisa de satisfação ao fim de cada atendimento | sim (S crít.) | Não atende: C0 | Não planejado | Pesquisa pós-atendimento (épico E21; AT-08 do levantamento 13) | M | ENT-12 |

### 2.12 Chatbot e inteligência artificial

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| TR-854; SG-634 | Chatbot no site com árvore de atendimento parametrizável; informações úteis na árvore (horário, documentos para ligação nova, tarifas) | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Editor de fluxos por tenant | M | ENT-13 |
| TR-855, TR-868; SG-646 | Transbordo do bot para atendimento humano a qualquer momento e em qualquer canal; serviços automáticos sem atendente | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Transbordo com contexto | M | ENT-13 |
| TR-856, TR-886; SG-671 | Atendimento por IA em linguagem natural, sem menus, além do pré-menu; compreensão tolerante a variações, erros e linguagem informal (ED-059 do 18b repete) | sim (V POC-76; S aval.) | Não atende: C0 | Não planejado | Orquestração de LLM com ferramentas sobre as APIs do produto | G | ENT-13 |
| TR-857, TR-882; SG-635 | Serviços automatizados integrados ao sistema comercial, sem intervenção humana (caput) | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | APIs de consulta expostas ao bot | G | ENT-13 |
| TR-865; SG-636, SG-637 | Identificação do cliente e acesso aos seus dados | sim (V poss.; S crít.) | Não atende: C0 | Não planejado | Identificação com fator de posse (ver SG-645) | M | ENT-13 |
| TR-858; SG-641 | 2ª via pelo bot (V: completa ou simplificada; S: fatura ou cobrança diversa) | sim (V POC-71; S crít.) | Não atende: C2 | Parcial: P3.3 (2ª via no portal, reaproveitável) | Ferramenta do bot sobre a API da 2ª via | M | ENT-13 |
| TR-859 | QR Code Pix pelo bot para pagar uma fatura específica | sim (V POC-72) | Não atende: C2 | Parcial: P3.2 (Pix Cobrança) | Ferramenta do bot sobre a API Pix | P | ENT-13 |
| TR-860; SG-640 | Extrato de débitos e pendências financeiras (S: inclui resíduos quando houver) | sim (V POC-73; S crít.) | Não atende: C2 | Não planejado | Consulta de pendências exposta ao bot | P | ENT-13 |
| TR-861; SG-643 | Histórico de consumo pelo bot | sim (V poss.; S crít.) | Não atende: C2 | Não planejado | Idem | P | ENT-13 |
| TR-862 | Solicitar OS pelo bot (falta d’água, vazamento) | não (V poss.) | Não atende: C0 | Parcial: P3.3 (protocolo) | Abertura de solicitação pelo bot | P | ENT-13 |
| TR-863 | Consultar tarifas pelo bot | não (V poss.) | Não atende: C2 | Não planejado | Idem | P | ENT-13 |
| TR-864; SG-644 | Simular ou buscar dados de negociação de parcelamento pelo bot | sim (V poss.; S crít.) | Não atende: C2 | Parcial: P4.1 (parcelamento) | Ferramenta do bot | P | ENT-13 |
| TR-866; SG-638 | Previsão da próxima leitura pelo bot | sim (V POC-74; S crít.) | Não atende: C2 | Não planejado | Calendário de leitura exposto | P | ENT-13 |
| TR-867; SG-639 | Situação do abastecimento da UC pelo bot | sim (V poss.; S crít.) | Não atende: C0 | Não planejado (incidente de rede, N2, só em 2028) | Cadastro de ocorrências de abastecimento por área | M | ENT-13 |
| SG-642 | Simulação do valor da fatura pelo bot | sim (S crít.) | Não atende: C2 | Parcial: P1.5 (N4) | Ferramenta do bot | P | ENT-13 |
| SG-645 | Autenticação em dois fatores no atendimento automatizado | sim (S crít.) | Não atende: C0 | Não planejado | Código de uso único por SMS ou e-mail antes de dado pessoal | M | ENT-13 |
| SG-670 | Recursos de IA da plataforma (caput) | não (S aval.) | Não atende: C0 | Não planejado | Ver linhas seguintes | G | ENT-13 |
| SG-672, SG-673, SG-674, SG-675, SG-676, SG-677, SG-678 | Classificação automática de intenções: 2ª via, consumo, falta d’água, vazamentos, negociação de débitos, reclamações e elogios | não (S aval.) | Não atende: C0 | Não planejado | Classificador com conjunto de avaliação por intenção | M | ENT-13 |
| SG-679, SG-680, SG-681, SG-682, SG-683, SG-684 | Extração de entidades: UC, datas de leitura, valores de fatura, protocolo, endereço ou bairro | não (S aval.) | Não atende: C0 | Não planejado | Extração com validação contra o cadastro | M | ENT-13 |
| SG-685 | Detecção de sentimento (positivo, neutro, negativo) para priorizar ou escalar | não (S aval.) | Não atende: C0 | Não planejado | Sinal de prioridade na fila | P | ENT-13 |
| SG-686 | Diálogos em múltiplas etapas com preservação de contexto | não (S aval.) | Não atende: C0 | Não planejado | Estado da conversa | M | ENT-13 |
| SG-687 | Contexto contínuo entre canais e na transferência para humano | não (S aval.) | Não atende: C0 | Não planejado | Contexto ligado ao protocolo | M | ENT-13 |
| SG-688 | Fallback: reapresentar, reformular a pergunta ou transferir para humano | não (S aval.) | Não atende: C0 | Não planejado | Política de fallback | P | ENT-13 |
| SG-689 | Roteamento de atendimentos críticos (falta d’água, vazamento) para filas prioritárias ou equipes específicas | não (S aval.) | Não atende: C0 | Não planejado | Regra de roteamento por intenção | P | ENT-13 |
| SG-690 | Geração de linguagem natural com a linguagem institucional da autarquia | não (S aval.) | Não atende: C0 | Não planejado | Instruções de estilo por tenant | P | ENT-13 |
| SG-691 | Respostas dinâmicas com dados do sistema comercial (valores, datas, consumo, situação financeira) | não (S aval.) | Não atende: C0 | Não planejado | Ferramentas sobre as APIs | M | ENT-13 |
| SG-692 | Personalização por perfil, histórico de atendimentos e permissões | não (S aval.) | Não atende: C0 | Não planejado | Contexto do titular com controle de acesso | M | ENT-13 |
| SG-693 | Resumo automático da conversa no transbordo para humano | não (S aval.) | Não atende: C0 | Não planejado | Resumo gravado no protocolo | P | ENT-13 |
| SG-694 | Aprendizado contínuo, com melhora da taxa de acerto das intenções | não (S aval.) | Não atende: C0 | Não planejado | Métrica de acerto e ciclo de revisão | M | ENT-13 |
| SG-695 | Humano no ciclo: gestores corrigem respostas e alimentam o treinamento | não (S aval.) | Não atende: C0 | Não planejado | Tela de curadoria | M | ENT-13 |
| SG-696 | Identificar intenções novas não mapeadas e sugerir a inclusão na base de conhecimento | não (S aval.) | Não atende: C0 | Não planejado | Agrupamento de perguntas não resolvidas | M | ENT-13 |
| SG-697 | Busca semântica em bases de conhecimento, manuais e informações institucionais | não (S aval.) | Não atende: C0 | Não planejado | Índice vetorial por tenant (pgvector ou equivalente) | M | ENT-13 |
| SG-698 | Integração com dados de consumo, faturamento, arrecadação e serviços operacionais | não (S aval.) | Não atende: C2 | Não planejado | Depende das APIs desses módulos | M | ENT-13 |
| SG-699 | Detectar comportamento anômalo (tentativas repetidas de acesso, automação maliciosa) | não (S aval.) | Parcial: Limite de taxa por IP, usuário e tenant planejado (P0.16) | Parcial: P0.16 (rate limit); P3.20 (bloqueio progressivo na vinculação) | Aplicar aos canais de conversa | P | ENT-13 |
| SG-700 | Mascaramento inteligente de dados sensíveis no atendimento automatizado | não (S aval.) | Parcial: Máscara configurável de documento (ADR-0008) no backoffice | Parcial: P0.11 (máscara com teto) | Máscara também nas respostas do bot e nos registros | P | ENT-13 |
| SG-701 | Auditoria inteligente das conversas para compliance e melhoria | não (S aval.) | Não atende: C0 | Não planejado | Amostragem e classificação de conversas | M | ENT-13 |
| SG-702 | IA integrada ao sistema comercial e à plataforma, comprovável por demonstração ou POC a critério da Administração | não (S aval.) | Não atende: C0 | Não planejado | Roteiro de demonstração | P | ENT-13 |
| SG-703 | A IA pode usar qualquer arquitetura, desde que atenda aos requisitos | não (S aval.) | Atende: Cláusula permissiva; nada a implementar | Planejado: Não exige tarefa | Nenhuma | P | — |

### 2.13 Segurança, auditoria e LGPD da plataforma de atendimento

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| SG-630 | Segurança da informação na plataforma multicanal: confidencialidade, integridade, disponibilidade e rastreabilidade | sim (S crít.) | Parcial: Base do produto (RLS, cifra de CPF, auditoria); não há plataforma multicanal | Parcial: P0.14–P0.16; P3.8 (pentest) | Incluir os canais no modelo de ameaças e no pentest | M | ENT-12 |
| SG-655, SG-656, SG-657, SG-658, SG-659, SG-660 | Logs de auditoria de acessos, início e fim de atendimentos, serviços automatizados, transferências para humano e alterações de configuração | sim (S crít.) | Parcial: C9 (alterações por trigger); sem eventos de atendimento | Parcial: P1.7 (partição do `audit_log`); P0.11 | Eventos de atendimento na trilha | M | ENT-12 |
| SG-661 | Logs armazenados com segurança e controle de integridade, para auditoria e órgãos de controle | sim (S crít.) | Parcial: Auditoria endurecida (tenant `V1_4`) | Planejado: P1.7; P2.18 (`HashChain`) | Nenhuma além do plano | P | — |
| SG-662, SG-663, SG-664 | Plataforma em conformidade com a LGPD: tratamento adequado e registro de consentimento | sim (S crít.) | Parcial: C10 | Parcial: Produto coberto (DPA na P3.9); canais de conversa não | Base legal e consentimento por canal; provedores como suboperadores | M | ENT-12 |
| SG-665 | Atender solicitações de titulares de dados | sim (S crít.) | Parcial: C10 (motor de direitos do titular, prazo único de 15 dias) | Parcial: Motor existente; canal pelo atendimento não planejado | Pedido do titular aberto como protocolo | M | ENT-12 |
| SG-666 | Políticas de retenção e descarte das informações | sim (S crít.) | Parcial: C10 (prazos ainda não semeados) | Planejado: P0.20 (pisos de retenção); `ret.service_request_years` (P3) | Incluir conversas na política | P | — |
| SG-667 | Backup e recuperação de dados | sim (S crít.) | Não atende: Só ambiente local, sem backup (plano §1) | Planejado: P0.28 (HA, backup e PITR) | Nenhuma além do plano | P | — |
| SG-668 | Alta disponibilidade compatível com atendimento crítico ao cidadão e com o SLA do TR | sim (S crít.) | Não atende: Idem | Planejado: P0.28; RNF-11 (portal 99,9%) | Nenhuma além do plano | P | — |
| SG-669 | Comprovar, a pedido, os mecanismos de segurança e a conformidade com a LGPD | sim (S crít.) | Não atende: Sem evidências produzidas | Planejado: P3.8 (pentest); P3.10 (RIPD) | Nenhuma além do plano | P | — |

### 2.14 Telefonia: URA, CTI e telemarketing ativo

| IDs | Requisito | POC? | Código | Plano | Lacuna / ação | Esf. | Entrega |
|---|---|---|---|---|---|---|---|
| SG-783 | Integração total e transparente com a telefonia da autarquia e com a URA do contact center (caput) | não (S aval.) | Não atende: C0 | Não planejado | Conector de telefonia; decisão D3 | G | ENT-14 |
| SG-784 | URA automatizada com dados do banco (falta d’água, postos, religação, novas instalações, faturas pendentes), sem atendente | não (S aval.) | Não atende: C0 | Não planejado | API de consulta para a URA | M | ENT-14 |
| SG-785 | Identificar quem liga de região com problema de abastecimento e informar o problema e a previsão de retorno | não (S aval.) | Não atende: C0 | Não planejado | Depende do cadastro de ocorrências por área | M | ENT-14 |
| SG-786 | Transferir para atendente com abertura automática dos dados do cliente na tela (CTI) | não (S aval.) | Não atende: C0 | Não planejado | Abertura de tela por número chamador | M | ENT-14 |
| SG-787 | Campanhas de telemarketing ativo com ligações automáticas (pesquisa, manutenção preventiva, campanhas educativas, cobrança) | não (S aval.) | Não atende: C0 | Não planejado | Discador com mensagens gravadas | M | ENT-14 |
| SG-788 | Monitorar em tela a fila do call center (espera média, abandonos, estatísticas) | não (S aval.) | Não atende: C0 | Não planejado | Painel alimentado pela telefonia | M | ENT-14 |

### 2.15 Requisitos de fronteira (outras seções dos editais)

Itens que tocam esta área, mas cuja regra principal pertence a outra matriz. Não entram nos totais da seção 1.

| IDs | Tema | Código | Plano | Observação |
|---|---|---|---|---|
| TR-162; SG-163 | Aferição de hidrômetro registrada e apresentada como OS | Não atende (C1) | Não planejado | Hidrometria gera OS pelo catálogo (ENT-03) |
| TR-166; SG-167 | Alerta de fim de validade de hidrômetros e OS automática de troca | Não atende | Não planejado | Idem |
| SG-168 | OS corretiva automática por ocorrência de leitura | Parcial (C7) | Não planejado | Gerador de OS a partir de `reading_anomalies` |
| TR-168, TR-169 | Instalação e retirada de hidrômetro geram OS de registro | Não atende | Não planejado | Idem |
| TR-232; SG-230 | Solicitação de serviço automática a partir da leitura (vazamento, violação de corte) | Parcial (C7) | Não planejado | Idem |
| TR-325; SG-312 | OS de fiscalização de consumo gerada por critérios | Não atende | Não planejado | OS em lote (ENT-03) |
| TR-334, TR-335; SG-321, SG-322 | OS de fiscalização de fraude e encerramento com tipo de fraude, fotos e boletim de ocorrência | Não atende | Não planejado | Matriz de faturamento e fiscalização |
| TR-406; SG-389, SG-390 | Alteração e cancelamento de notificação de corte geram OS de registro | Não atende | P3.4; P4.1 (parcial) | Matriz de cobrança |
| TR-407–TR-418; SG-391–SG-401 | Ciclo de corte: geração parametrizada das OS de corte (prioridade, limite diário, valor mínimo, código do serviço, mensagem, dias e horários, níveis sucessivos, bloqueio por motivo) | Não atende | P3.4 (bloqueio de corte); G9; P4.1 (parcial) | Matriz de cobrança; depende de ENT-03 e ENT-04 |
| TR-246, TR-269; SG-243, SG-259 | Revisão de consumo com protocolo e no dispositivo móvel “no modo das OS” | Não atende | Não planejado | Matriz de faturamento; reusa ENT-02 e ENT-05 |
| TR-206, TR-273, TR-403; SG-263; POC-20 (Videira) | Fatura e comunicado de débito por e-mail; laudo da revisão por e-mail ou WhatsApp; aviso de pendência por SMS, WhatsApp ou e-mail antes do aviso de corte | Não atende | P3.5 (e-mail e SMS); WhatsApp não planejado | Usa ENT-11 |
| TR-244; SG-241 | Comunicado de alerta de vencimento de benefício (tarifa social e outros) | Não atende | P1.5 (parcial) | Usa ENT-11 |
| TR-196, TR-205 | Ver leituristas on-line e enviar mensagens aos aparelhos | Não atende | P4.4 (parcial) | Matriz de leitura |
| TR-681, TR-682, TR-756, TR-764, TR-818, TR-819, TR-824, TR-825, TR-827, TR-830; SG-907, SG-908, SG-983, SG-991, SG-1046, SG-1052, SG-1057; POC-64 (Videira) | Relatórios de atendimentos, pesquisa de satisfação, processos administrativos por protocolo, painel de OS por setor, serviços por canal, tempos médios e espelho de OS | Não atende | Não planejado | Matriz de relatórios; dependem de ENT-02 e ENT-03 |
| TR-925–TR-935, TR-949, TR-995; SG-1099–SG-1110, SG-1122, SG-1172; POC-78 (Videira) | Painel de atendimento no BI (por atendente, canal, serviço, tempos), quantidade de avisos e de OS | Não atende | P4.3 (BI em réplica, parcial) | Matriz de BI |
| TR-1061, TR-1064, TR-1065; SG-877–SG-879 | OS no GIS: filtros e exportação, abertura em lote por manobra ou fechamento de rede, camadas de OS de vazamento e falta d’água | Não atende | 2028 (R9, parcial) | Matriz de GIS |
| Itens 3 e 4 do lote VISAN; TR-880; ED-024, ED-037, A-19 (18b) | Omnichannel como item próprio (implantação R$ 5.833,33; R$ 2.233,33 por mês), vedada a subcontratação, volume de mensagens não informado e custo variável por conta da contratada | — | Não planejado | Precificação e forma de contratar os provedores (decisão D2) |
| SG-1370, SG-1371, SG-1375, SG-1376 | Mensagens omnichannel (30.000 a R$ 0,79) e SMS (12.000 a R$ 0,18) pagas por uso; subcontratação de “comunicação” permitida; OS e gestão comercial não podem ser subcontratadas | — | Não planejado | Precificação (decisão D2) |
| ED-057, ED-059 (18b) | App do usuário em Android e iOS; omnichannel com IA em linguagem natural na POC (classificados como alto risco no 18b) | Não atende | Não planejado | Repetem TR-653 e TR-856; ver ENT-09 e ENT-13 |

## 3. Requisitos não planejados, agrupados em entregas

Cada entrega reúne as linhas da matriz com plano “Não planejado” (coluna 2) e as com plano “Parcial” cuja parte faltante cabe nela (coluna 3). Fases no calendário do plano v2.2; esforço da entrega inteira.

| Entrega | Não planejados | Parcialmente planejados | Fase sugerida | Esforço | Dependências |
|---|---|---|---|---|---|
| **ENT-01** Catálogo de serviços, termos e carta de serviços | 39 IDs: TR-496–TR-506, TR-508–TR-513, TR-519; SG-449–SG-460, SG-462–SG-467, SG-473, SG-760, SG-763 | 2 IDs: TR-507; SG-461 | P3, antes da P3.4 | G | P0.19 (parâmetros); rubricas dependem da integração contábil (`sigsan-accounting` vazio); P3.6 (imposto do serviço) |
| **ENT-02** Atendimento completo: protocolo multicanal, visão 360, workflow e documentos do cliente | 4 IDs: TR-545, TR-552; SG-525, SG-528 | 16 IDs: TR-544, TR-546–TR-549, TR-551, TR-574, TR-576; SG-520–SG-524, SG-527, SG-551, SG-553 | P3 (amplia a P3.4) | G | P3.4; APIs de billing, metering e collection; P1.20 estendida; P3.5 |
| **ENT-03** OS completa no backoffice: estados, programação, baixa parametrizável, desdobramento, lote, retrabalho, mapa, equipes, veículos e materiais | 38 IDs: TR-514–TR-517, TR-522, TR-524, TR-525, TR-527, TR-531, TR-533, TR-534, TR-597, TR-606, TR-610, TR-613, TR-614; SG-468–SG-471, SG-475, SG-477–SG-479, SG-481, SG-482, SG-484, SG-488, SG-490, SG-491, SG-575, SG-577, SG-585, SG-587, SG-590, SG-593, SG-594, SG-596; Anexo II n) POC-46 | 36 IDs: TR-495, TR-520, TR-523, TR-526, TR-528, TR-532, TR-594–TR-596, TR-598, TR-599, TR-604, TR-605, TR-607, TR-608, TR-612, TR-615, TR-616; SG-448, SG-474, SG-480, SG-483, SG-485, SG-489, SG-572–SG-574, SG-576, SG-578, SG-583, SG-584, SG-586, SG-588, SG-592, SG-595, SG-597 | P3 (núcleo: abrir, programar, cancelar, encerrar); P4 (lote, retrabalho, kits, mapa) | G | ENT-01; P3.4; P1.9 (outbox); P2.1 (lote); `sigsan-gis` |
| **ENT-04** Efeitos comerciais da OS: corte e religação por nível (água e esgoto), serviços e materiais lançados na conta, cartas de liberação de esgoto | 13 IDs: TR-530, TR-535–TR-538; SG-487, SG-497–SG-500, SG-503–SG-505 | 9 IDs: TR-529, TR-539, TR-540, TR-590; SG-486, SG-501, SG-502, SG-567, SG-571 | P3 (corte e religação, junto da P3.4); P4 (serviços e materiais cobrados, cartas) | G | P2.5; P3.4; P4.1; parâmetros `cut.*` (G9) |
| **ENT-05** App de campo de OS e painel de monitoramento (rastreamento, fotos, crachá/QR, offline) | 43 IDs: TR-620–TR-641, TR-643–TR-647, TR-651, TR-652; SG-506–SG-519 | 4 IDs: TR-642, TR-648–TR-650 | P4, junto da P4.4 | G | ENT-03; P4.4 (padrão offline); decisão D1 |
| **ENT-06** Empreiteiras terceirizadas: cadastro, contratos, penalidades e medição | 5 IDs: SG-492–SG-496 | — | P4 ou 2028, sob demanda | M | ENT-03 |
| **ENT-07** Integração com estoque do cliente | 2 IDs: TR-521; SG-476 | — | Sob demanda, por cliente | M | ENT-03 (kits); API do sistema de estoque do cliente |
| **ENT-08** Portal: serviços adicionais (ficha, histórico de consumo, simulações, declaração anual de quitação, leitura, qualidade da água, tarifas, OS, solicitações com triagem) | 20 IDs: TR-543, TR-658, TR-838, TR-844–TR-846, TR-849; SG-603, SG-609–SG-611, SG-613, SG-621, SG-622, SG-624, SG-713, SG-723, SG-726, SG-766, SG-767 | 29 IDs: TR-659, TR-660, TR-837, TR-839–TR-843, TR-847, TR-848; SG-602, SG-604–SG-608, SG-612, SG-615–SG-620, SG-623, SG-715, SG-717, SG-721, SG-722, SG-761 | P3 (amplia P3.3 e P3.20) | G | P3.3; P3.20; P1.5 (N4); P4.1; ENT-01; módulo `quality` com API |
| **ENT-09** App do cliente white-label publicado nas lojas, com marca do prestador | 37 IDs: TR-653; SG-705, SG-724, SG-727, SG-728, SG-732–SG-735, SG-737–SG-744, SG-746–SG-749, SG-751, SG-753–SG-757, SG-765, SG-768–SG-773, SG-779–SG-781 | 30 IDs: TR-654, TR-656, TR-657, TR-661–TR-663; SG-704, SG-706, SG-708, SG-709, SG-711, SG-712, SG-714, SG-716, SG-718–SG-720, SG-729–SG-731, SG-736, SG-745, SG-750, SG-752, SG-758, SG-762, SG-764, SG-777, SG-778, SG-782 | P4 | G | ENT-08; P3.21; decisões D1 e D5; contas de desenvolvedor do prestador na Apple e no Google |
| **ENT-10** Totem e gestão de senhas e filas | 4 IDs: TR-836; SG-614, SG-625, SG-626 | — | P4 | M | ENT-08; decisão D4 |
| **ENT-11** Notificações multicanal (e-mail, SMS, WhatsApp, push) com opt-in, modelos, caixa de mensagens e campanhas | 9 IDs: TR-581, TR-850, TR-891; SG-558, SG-627, SG-759, SG-774–SG-776 | 7 IDs: TR-518, TR-542, TR-570, TR-571; SG-472, SG-546, SG-547 | P3 (e-mail e SMS, amplia a P3.5); P4 (WhatsApp, push, campanhas) | G | P1.9; P3.5; provedores como suboperadores no DPA (P3.9); decisão D2 |
| **ENT-12** Omnichannel com atendimento dentro do Ziip SAN, ligado ao protocolo | 27 IDs: TR-851, TR-852, TR-870–TR-874, TR-876–TR-881, TR-884, TR-887–TR-890; SG-628, SG-629, SG-632, SG-633, SG-649–SG-651, SG-653, SG-654 | 19 IDs: TR-853, TR-869, TR-875, TR-883, TR-885; SG-630, SG-647, SG-648, SG-652, SG-655–SG-660, SG-662–SG-665 | P4 | G | ENT-02; ENT-11; decisão D2 |
| **ENT-13** Chatbot por menu e assistente com IA (seção 2.23 do SIGCOS) | 55 IDs: TR-854–TR-857, TR-860, TR-861, TR-863, TR-865–TR-868, TR-882, TR-886; SG-631, SG-634–SG-640, SG-643, SG-645, SG-646, SG-670–SG-698, SG-701, SG-702 | 9 IDs: TR-858, TR-859, TR-862, TR-864; SG-641, SG-642, SG-644, SG-699, SG-700 | P4 (bot por menu e serviços automatizados); 2028 (IA completa) | G | ENT-12; APIs de consulta; decisão D2 |
| **ENT-14** Telefonia: URA com consulta, CTI e telemarketing ativo | 6 IDs: SG-783–SG-788 | — | Sob demanda (2028) | G | ENT-02; ENT-13; decisão D3 |
| **ENT-15** Ajustes de fatura pelo atendimento, com anotação (cancelar, alterar, descontar, emitir fora do lote, 2ª via e espelho) | 26 IDs: TR-541, TR-553, TR-555–TR-565; SG-529, SG-531–SG-542 | 8 IDs: TR-554, TR-567, TR-568, TR-573; SG-530, SG-544, SG-549, SG-550 | P3, depois da P2.5 e da P2.6 | G | P2.5; P2.6 (cancelamento ou substituição da NFAg-e); ENT-02; coordenar com a matriz de faturamento |
| **ENT-16** Usuário comercial avulso e órgão centralizador | 14 IDs: TR-589, TR-600, TR-601, TR-603, TR-617–TR-619; SG-566, SG-579, SG-580, SG-582, SG-598–SG-600 | 4 IDs: TR-602, TR-609; SG-581, SG-589 | P3 | M | P2.5; P3.6 |

**Destinados a matrizes de outras áreas** (registrados aqui porque aparecem nas seções de atendimento dos editais):

| Área | IDs | Situação no plano |
|---|---|---|
| Cadastro (ligações, endereço alternativo, logradouros, tipo de tarifa) | TR-577–TR-580, TR-585, TR-611; SG-554–SG-557, SG-562, SG-591 | Parcial / Não planejado |
| Faturamento (isenções, vencimento alternativo por grupo) | TR-587, TR-588; SG-564, SG-565 | Parcial |
| Arrecadação (débito automático) | TR-566, TR-583; SG-543, SG-560 | Não planejado |
| Cobrança (protesto, SPC/Serasa, carta de anuência) | TR-572; SG-548, SG-725 | Não planejado |
| Hidrometria (instalação, retirada e histórico de hidrômetros) | TR-591, TR-592; SG-568, SG-569 | Não planejado |

**Ordem sugerida.** ENT-01 e ENT-02 antes ou junto da P3.4, porque protocolo e OS sem catálogo de serviços não atendem à NR 11 (lista de serviços com prazo, arts. 72–73) nem às duas POCs; ENT-03 núcleo, ENT-04 (corte e religação) e ENT-11 (e-mail e SMS) no P3; ENT-15 e ENT-16 no P3, depois do ciclo; ENT-08 no P3 sobre a P3.3 e a P3.20; ENT-05, ENT-09, ENT-10, ENT-12 e o bot por menu da ENT-13 no P4; IA completa, empreiteiras, estoque e telefonia em 2028 ou sob demanda.

## 4. Decisões do dono do produto

No máximo cinco, em ordem de impacto nas POCs. Cada uma pede escolha entre as opções; a recomendada está indicada abaixo da tabela.

### D1. Apps móveis do cliente e de campo: como construir e publicar

Editais: app Android e iOS (TR-653, SG-705), nas lojas com nome e marca do prestador (SG-778, SG-779), ligado direto à base dele (SG-780); app de OS instalado por APK, com banco local e fotos (TR-621, TR-639). O plano tem portal web (P3.3) e PWA de leitura (P4.4).

| Opção | Efeito |
|---|---|
| a) Só PWA (portal e campo) | Menor custo; não atende APK (TR-621), publicação nas lojas como app do prestador (SG-779) nem push e GPS em segundo plano confiáveis no iOS |
| b) App híbrido (Capacitor) sobre o mesmo frontend React | Um código para portal, app do cliente white-label gerado por tenant (nome, ícone, cores, subdomínio fixo) e app de campo Android em APK; publicação nas contas de desenvolvedor do prestador |
| c) Apps nativos (Kotlin e Swift) | Melhor desempenho; duas bases a manter e custo por cliente alto para o porte dos SAAEs |

**Recomendada: b).** O levantamento 04 (épico E7) já apontava Capacitor offline-first. Exige pipeline de build por tenant e rever a P4.4 para usar a mesma base.

### D2. Omnichannel, chatbot e IA: construir ou integrar

Editais: atendimento de todos os canais dentro do sistema comercial (TR-852, SG-633), conversa gravada no protocolo (TR-853, SG-648), bot com transbordo e IA em linguagem natural (TR-856, SG 2.23). Videira veda subcontratação (ED-024); o SIGCOS admite subcontratar “comunicação” (SG-1375) e paga mensagens por uso.

| Opção | Efeito |
|---|---|
| a) Construir tudo | Conectores oficiais (WhatsApp Business Platform, Messenger, Instagram, Telegram, webchat, e-mail), filas, bot e IA próprios; controle total e prazo longo |
| b) Integrar uma plataforma de atendimento de mercado | Rápido; custo por mensagem e por agente, dependência do fornecedor, conversa fora do Ziip e risco de ser lida como subcontratação em Videira |
| c) Híbrido | Conectores oficiais, filas, tela de atendimento e protocolo no Ziip; modelo de linguagem de terceiro por API, com orquestração e ferramentas próprias sobre as APIs do produto; provedores como componentes e suboperadores no DPA |

**Recomendada: c).** Mantém protocolo e histórico no Ziip, exigência das duas POCs, e permite cobrar mensagens por uso (SIGCOS) ou por franquia (Videira). Bot por menu e serviços automatizados no P4; IA da seção 2.23 depois.

### D3. Telefonia: URA, CTI e telemarketing ativo

Editais: só o SIGCOS pede (seção 2.25, avaliável e não essencial). Videira cita apenas o canal telefone e o relatório por canal.

| Opção | Efeito |
|---|---|
| a) Não ofertar agora | Risco de perder itens da POC do SIGCOS se a comissão os escolher |
| b) Integrar à telefonia do cliente | API de consulta para a URA de terceiros (falta d’água, faturas pendentes), abertura de tela pelo número chamador (CTI) e telemarketing ativo por parceiro |
| c) Contact center próprio | PABX em nuvem, URA e discador no produto; fora do foco e caro de operar |

**Recomendada: b), na forma mínima**, só quando um edital ou cliente exigir.

### D4. Totem e gestão de filas

Editais: totem com as funções do portal (TR-836, SG-614) e senha emitida e chamada pelo próprio sistema comercial (SG-625, SG-626, essenciais no SIGCOS). Nenhum edital diz quem fornece o hardware.

| Opção | Efeito |
|---|---|
| a) Totem como portal em modo quiosque, no hardware do cliente, e módulo de senhas e filas no Ziip | Emissão de senha com prioridade legal (Lei 10.048), chamada na tela de atendimento e painel de TV em navegador; reaproveita o portal |
| b) Integrar um sistema de filas de terceiros | Menos código; outra tela para o atendente, o que contraria SG-626 |
| c) Fornecer a solução com hardware | Receita adicional; logística, garantia e manutenção de equipamento fora do modelo SaaS |

**Recomendada: a)**, com o hardware explicitamente a cargo do cliente na proposta.

### D5. Identificador de login do consumidor (ajuste do ADR-0022)

Hoje: conta no realm `ziipsan-portal` por e-mail ou celular e senha; vínculo pelo código impresso na conta mais o CPF/CNPJ do titular. Editais: login por CPF ou CNPJ (SG-706, crítico) ou por matrícula ou CPF (TR-656, TR-657); cadastro validado por nome e documento (SG-708, SG-709).

| Opção | Efeito |
|---|---|
| a) Manter o ADR-0022 | Falha SG-706, item crítico do SIGCOS |
| b) Aceitar CPF/CNPJ como identificador de login, ao lado do e-mail | Atende os dois editais; mantém conta própria, vínculo e resposta indistinta; o documento usado no login passa pelo índice cego e entra no pentest (P3.8) |
| c) Acesso por matrícula e CPF, sem conta | Já rejeitado no ADR-0022: não prova posse nem permite limitar e registrar a sessão |

**Recomendada: b)**, por novo ADR que substitua o item 4 do ADR-0022. Decidir no mesmo ADR se o código impresso pode ser dispensado por parâmetro do tenant quando o edital exigir só nome e documento, com o risco de enumeração registrado.
