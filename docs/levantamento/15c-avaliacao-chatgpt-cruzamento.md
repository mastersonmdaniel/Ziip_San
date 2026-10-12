# 15c. Avaliação cruzada do relatório do ChatGPT (11/10/2026)

Objeto: `docs/ANALISE-FONTES-OPEN-ENRIQUECIMENTO-ZIIP-SAN.md` (~3.050 palavras).
Contra: `DIARIO-DE-BORDA.md` (seções 1, 2, 9, 11, 13–15), relatórios `00` a `14` (~137 mil palavras) e o código em 11/10/2026.
Escopo: cruzamento. As afirmações sobre o código-fonte do GSAN e do GEOSAN estão com outros dois agentes e **não** foram reverificadas aqui.
Convenção: **[código]** = conferi no repositório agora; **[doc]** = está num relatório nosso; **[inferência]** = conclusão minha, sem fonte direta.

---

## 1. O que o relatório acrescenta e vale incorporar

Antes de tudo: **a maior parte do conteúdo já está nos relatórios 00 a 14**. Conferi cada ideia sugerida pelo pedido com Grep nos 15 relatórios.

| Ideia do relatório | Já coberta? | Onde |
|---|---|---|
| Ficha 360° do imóvel | **Sim** | `07` S3 (cabeçalho fixo, abas, histórico "como era em dd/mm"); `04:280-290` |
| Pipeline de importação/exportação GIS | **Sim** | `05` P1-7 (staging, Q-26, `legacy_object_id`); `06` P1 e item 14 (`ogr2ogr`, validação de SRID) |
| Eventos de domínio | **Em princípio** | `02:652` (eventos em `sigsan-events`), `02:938` ("Kafka no compose; sem consumidores"), `03:509`, `12` RES-47 (outbox) |
| Consumidores afetados (traçado de manobra) | **Em parte** | `05` P1-2, `06` item 11 e `3.3`, `04` E11 (interrupção programada x RA), `08` R-27 (unidades essenciais) |
| Painel de fechamento de ciclo | **Em parte** | `02:666` (M8, simulação em dry-run), `02:710` (`billing_cycle`: OPEN→CLOSED), `04` E18 (painel de KPI) |
| Conciliação com exceções | **Em parte** | `03` P0.2 (14 estados, "a contabilizar", não identificados); alçada/segregação em `04` E14 e `04:214-221` |
| Simulador de conta | **Em parte** | `02` M8 (lote); `03` (simulação de parcelamento e de negativação) |
| Campanhas de investigação de perdas | **Não** | nenhuma ocorrência em `04`, `05`, `06`, `09` |

### Ideias realmente novas (ou com parte nova)

| # | Ideia | O que é novo | Encaixe | Prioridade | Esforço |
|---|---|---|---|---|---|
| N1 | **Campanha de investigação de perdas** (área, hipótese, equipe, evidência, achado, reparo, **benefício medido depois**) e fila de suspeitas ponderada | O ciclo hipótese → intervenção → ganho medido não existe em nenhum relatório. Os nossos só tratam o balanço (`LossesService`) | `intelligence/sigsan-losses` + `operations/sigsan-field` | P2. Só faz sentido com entrada real: `LossesService` ainda usa placeholder (`LossesService.java:53,63,125`) **[código]** | M |
| N2 | **Incidente de rede + comunicação segmentada de afetados** (template, janela prevista, atualização de status, restauração no mapa) e **impacto real × previsto** | Temos a lista de afetados e o aviso ao atendente (`05`, `04` E11), mas não o incidente como entidade que amarra OS, lista, comunicação e correção da topologia | `sigsan-gis` (traçado) + `sigsan-field` (incidente/OS) + `sigsan-portal` (canais) + `sigsan-lgpd` (finalidade) | P1, **depois** do protocolo e da OS | M a G |
| N3 | **Dupla checagem em ajuste manual de conciliação**, com sugestão de vínculo | Aplica a segregação de funções de `04` E14 a um caso concreto (baixa manual, estorno, pagamento duplicado). `03` não descreve fila de exceções com aprovação | `commercial/sigsan-collection` | P1, junto da conciliação | S a M |
| N4 | **Simulador de conta sob demanda (atendimento) e simulador de impacto de tarifa sobre a base** | `02` só prevê o dry-run em lote do ciclo. Impacto de nova tabela (e da tarifa social) sobre a base não consta. `TariffEngine` é função pura **[código]**, então é barato | `commercial/sigsan-billing` + `sigsan-portal` | P0 para a tarifa social (11/12/2026), P1 para o atendimento | S |
| N5 | **Crítica de consumo por comparação com pares do setor** | `02` e o `ConsumptionCalculator` usam média histórica e faixa esperada; comparação com vizinhança não aparece. Também alimenta perdas aparentes | `operations/sigsan-metering` | P2 | S a M |
| N6 | **Painel de fechamento como porta de saída** (pendências de leitura, críticas abertas, contas a emitir, exceções, decisão "pode fechar") | A máquina de estados está em `02`; a tela como *gate* não | `commercial/sigsan-billing` + frontend | P1, depende do ciclo (fase 4 de `00 §3`) | S a M |
| N7 | **Regra de publicação de simulação hidráulica**: só exibir resultado com solver, versão, premissas e confiança | Coerente com os stubs do diário §9, mas vira critério de aceite verificável do futuro EPANET | `intelligence/sigsan-gis` | P2 | S |

### Aproveitável só como lista de nomes
Os 11 eventos (`property.updated`, `invoice.issued`, `payment.reconciled` etc.) servem de **ponto de partida do catálogo**. Falta payload, versão e regra de PII (ver 2d).

**Não vale incorporar:** ficha 360°, pipeline GIS, recadastramento móvel, catálogo de serviços com SLA e jornada de cobrança. Todos já têm tratamento mais detalhado nos relatórios `04`, `05`, `06`, `07` e `03`.

---

## 2. O que o relatório erra, exagera ou deixa desatualizado

### (a) "Fundação técnica forte"
O relatório repete o tom do diário, mas o próprio diário diz o contrário na seção 15 e na seção 2:
- O baseline "verde" **não estava verde**: o `sigsan-platform` tinha 1 teste falhando e derrubava 5 módulos (diário §2).
- A RLS era **inerte** (`usesuper=t`, `usebypassrls=t`) até 11/10 (§4). O relatório a lista como "já implementado", e só é verdade depois da Fase 0.
- Provisionamento criava tenant com **zero tabelas** e `success=true` (§5). `V1__` de cada módulo colidia e nunca rodava num tenant (§15.1).
- 25 `@Entity` sem `@Id`, 68 colunas `jsonb` sem `@JdbcTypeCode`, FKs sem `UNIQUE` (§15.2).
- ILI com unidade errada (m³ do período ÷ UARL em m³/dia) e água não faturada errada: 2.800 m³ no código, 3.000 m³ correto (§15.3).
- Pendências abertas em §15.5: `.inp` do EPANET inválido, `LossesService.auditLog` vazio, `PlatformConfig` inutilizável, `@SpringBootApplication` inexistente.

Hoje, **[código]**: não há nenhum `@RestController` nem `@SpringBootApplication` no repositório. `intelligence/sigsan-quality` e `compliance/sigsan-lgpd` têm **0 testes**. `FieldCipher` e `BlindIndex` **não são usados por nenhum módulo** fora de `sigsan-security` (a cifra de campo existe, mas ninguém a chama).

Os módulos "especializados" que o relatório elogia (perdas, qualidade, GIS, resíduos e LGPD) são justamente os mais frágeis: `14 §5` lista 10 defeitos neles.

### (b) "Comercial e operações ainda não implementados"
Está **desatualizado**. O relatório herdou o diário §11.2 ("`commercial` e `operations` continuam vazios, sem POM"), que ainda não foi atualizado. Estado real **[código]**:

| Módulo | Arquivos Java | `@Test` | Conteúdo |
|---|---|---|---|
| `commercial/sigsan-registry` | 38 | 41 | Domínio (cliente, imóvel, ligação, territorial), `Matricula`, `Inscription`, `DocumentNumber`, migration `V3_1` |
| `commercial/sigsan-billing` | 68 | 33 | `TariffEngine`, `SocialTariffRule`, `BillCalculator`, `LateFeeCalculator`, `CreditApplier`, impostos retidos, rateio de condomínio |
| `commercial/sigsan-collection` | 45 | 93 | Código de barras FEBRABAN **v08**, retorno FEBRABAN 150, `PaymentClassifier`, acréscimos, migration `V6_1` |
| `operations/sigsan-metering` | 45 | 87 | `ConsumptionCalculator`, `ReadingValidator`, média, faixa esperada, esgoto, rateio |
| `shared/sigsan-security` | 6 | 18 | `FieldCipher`, `BlindIndex`, `TenantKeyProvider` |

O relatório propõe como "Onda 1" o que já tem **regra pura pronta e testada**. Para ser justo: são bibliotecas de regra, **sem serviço, API, job de ciclo nem tela integrada** (frontend só tem `pages/cadastro`). O critério de sucesso da Onda 1 ("cadastrar ligação, registrar leitura, simular e emitir conta em tenant de teste") continua **não atendido**, e o que falta (orquestração, API, persistência) o relatório não distingue.

**Onde o relatório acerta:** `dunning`, `portal`, `accounting`, `assets`, `field`, `maintenance`, `reading` e `compliance/sigsan-integration` seguem **sem POM** **[código]**. Atendimento, OS, cobrança e SINISA realmente não existem.

### (c) Nomes e fronteiras de módulo
O repositório agrupa por pasta de domínio com `artifactId sigsan-*` (`pom.xml:128-140`). A proposta do relatório conflita:

| Proposto | Já existe | Problema |
|---|---|---|
| `sigsan-commercial` (cliente, imóvel, ligação, tarifa, faturamento) | `commercial/sigsan-registry` + `commercial/sigsan-billing` | Funde dois módulos já separados (decisão de 11/10: criar `registry` à parte, diário §14) e colidiria com migrations `V3_x` e `V2_x` |
| `sigsan-metering` | `operations/sigsan-metering` | Mesmo nome, mas o relatório o trata como raiz. A regra de leitura já mora aqui, não em `operations/sigsan-reading` (sem POM) |
| `sigsan-revenue` (arrecadação, conciliação, contas a receber) | `commercial/sigsan-collection` (arrecadação) + `commercial/sigsan-accounting` (vazio) | Mistura três coisas; `00 §8` Q1 já fixou `collection` = arrecadação e `dunning` = cobrança |
| `sigsan-customer-care` | `commercial/sigsan-portal` (destino de E1/E2/E21, `04`) | Nome duplicado do `portal` |
| `sigsan-field`, `sigsan-gis`, `sigsan-losses` | `operations/sigsan-field`, `intelligence/sigsan-gis`, `intelligence/sigsan-losses` | Sem divergência real, só o prefixo de pasta |
| (ausentes) | `grs/*`, `compliance/*`, `platform/sigsan-auth`, `sigsan-tenant`, `shared/sigsan-events` | O relatório ignora resíduos, qualidade, SINISA (`sigsan-integration`) e autenticação |

**Recomendação:** manter a estrutura do repositório. Nenhum módulo novo do relatório.

### (d) Kafka e eventos
- Compose tem `cp-kafka:7.5.0` e ZooKeeper, ambos `healthy` (diário §8; `docker-compose.yml:108-135`) **[código]**.
- `pom.xml:81` declara `spring-kafka` só em `dependencyManagement`. **Nenhum módulo depende dele.**
- Grep em todo `*.java|xml|yml|properties`: **zero** ocorrências de `KafkaTemplate`, `@KafkaListener`, `@EventListener`, `@TransactionalEventListener` ou `outbox`. `shared/sigsan-events` é uma pasta **sem POM** e fora do reactor (13 módulos listados).
- Resultado: hoje **não há produtor nem consumidor**. `02:938` já registrava "Kafka no compose; sem consumidores".

O relatório diz "orientado a eventos e APIs" como se fosse capacidade existente. É intenção. Riscos que ele não enxerga:
- Evento com PII (endereço, telefone, "lista de afetados") circulando em tópico sem política de retenção ou cifra.
- Contexto de tenant: a GUC `app.current_tenant` vazou entre conexões do pool no passado (diário §14, `set_current_tenant` removida). Consumidor Kafka precisa restabelecer o tenant por mensagem.
- `03:509` já aceita "executor local para o MVP".

**Proposta [inferência]:** evento de domínio **em processo** + tabela **outbox transacional** por tenant (com RLS). Kafka só quando houver segundo processo consumidor.

### Outras imprecisões
- "Priorização por território baseada em telemetria real" (`Perdas e priorização territorial`): não há telemetria. SCADA é stub (`ScadaIntegrationService`), DMAs fictícios foram removidos (diário §15.4) e a entrada do balanço é constante. A entrada real depende de **consumo faturado por DMA** e de `producao_agua` (`00 §4.2`), que o relatório não conhece.
- "Gerador de cenário EPANET": o escritor `.inp` continua **inválido** (diário §15.5; `06`). Precisa ser corrigido antes de qualquer cenário.
- Contagem do GSAN ("7.804 classes Java"): diverge do `00 §1` ("~8.600 arquivos Java em `src/gcom`"). Está com os outros agentes; sinalizo só a inconsistência entre os dois documentos.
- Lista de estados da conta mistura estado persistido com estado derivado ("vencida" decorre de data e saldo). Nossa classificação de pagamento tem 14 estados (`03` P0.2).
- `frontend/src/services/api.ts` é citado como "superfícies de API pretendidas". Tem 102 linhas e só configura `API_BASE_URL` **[código]**. Não há backend HTTP para essas superfícies.

---

## 3. Prioridades contra os prazos regulatórios

> **Superado em 12/10/2026.** A ordem de prioridade canônica é a de `docs/PLANO-EXECUCAO-PDCA-R0-R9.md`. Esta seção fica como registro do raciocínio da época.


Referência: `14 §3` e `08`, `11`, `13`. Hoje = 11/10/2026. Dias contados daqui.

| Marco | Data | Dias | O relatório |
|---|---|---|---|
| SINISA preliminar / validação / adimplência | 03/11, 17/11, 31/12/2026 | 23 / 37 / 81 | **Ausente.** "Relatórios gerenciais" na Onda 3 |
| NFAg-e (a confirmar no DOU) | 01/12/2026 | 51 | **Ausente.** "Tributos" só como linha da conta |
| Tarifa social | 11/12/2026 | 61 | Dentro do cadastro (P0) como "elegibilidade, vigência", sem data, sem desconto, sem aviso de 3 meses |
| Pix + boleto híbrido | 01/02/2027 | 113 | Pix na conta (P0), conciliação Pix em **P1** |
| NR 11 (atendimento) | 20/05/2027 | 221 | Atendimento em P1, sem 24x7, protocolo numerado nem histórico de 120 meses |
| Pix Automático / débito | 01/07/2027 | 263 | Ausente |
| Corte (Lei 14.015, em vigor) | já vigente | — | Cobrança em **P2**; "bloqueios legais" genéricos |

### Onde conflita
1. **Cobrança em P2, mas OS de corte em P1.** O relatório põe "OS" na Onda 2 e "corte/religação" na Onda 3. Se o tipo de serviço "corte" nascer com a OS, o **bloqueio da Lei 14.015** (sexta a domingo, feriado e véspera) e o **aviso de 30 dias** (Lei 11.445, art. 40 §2º) precisam existir já na OS. `14 §4.3`: "o sistema impede, não só avisa". Hoje não há essa regra no código (Grep de `14.015`, `sexta`, `véspera` em `commercial/*/src/main`: só o calendário de feriados de `collection`) **[código]**.
2. **Tarifa social sem data.** É o prazo mais curto que o produto pode cumprir e o relatório não o trata como urgência regulatória. A regra de cálculo já existe (`SocialTariffRule`, parametrizada); o gargalo é **classificação** (CadÚnico/BPC, base legal, renda/NIS como dado sensível: `14 §7` Q2), **aviso na fatura** (`10` TS-04) e **relatório anual**. É decisão jurídica e integração, não engenharia de cálculo.
3. **Arrecadação não pode ser "depois" da conta.** O layout FEBRABAN v08 vale desde 01/06/2026 (`14 §2`; `Segment.java` já o cita). O boleto emitido na conta já precisa do código de barras correto e, a partir de 01/02/2027, do QR Pix. Emissão e reconciliação andam juntas; o relatório as separa em P0 e P1.
4. **Atendimento P1 está na faixa certa para 20/05/2027, mas por motivo errado.** O relatório o justifica por "conectar cidadão, campo e cobrança", não por NR 11. Faltam: protocolo numerado, 24x7, relatório de reclamações, retenção de 120 meses (que conflita com minimização LGPD e precisa ser decidida) e SAC (aplicabilidade do Decreto 11.034/2022 em aberto: `14 §7` Q5).
5. **SINISA e NFAg-e são "decisão antes de código".** A NFAg-e é pendência jurídica (`14 §6` item 2). O SINISA tem `sigsan-integration` vazio e depende de cadastro e balanço que ainda não estão em produção.

**Ressalva [inferência]:** prazos de 2026 são do **prestador** (o tenant). Não há aplicação executável nem cliente em produção; o que a Ziip entrega antes de 03/11 ou 11/12 é, no máximo, pacote de regra + decisões. Vale dizer isso aos clientes em vez de prometer prazo.

### Ordem corrigida

| Ordem | Entrega | Motivo/prazo | Estado no código |
|---|---|---|---|
| R0 | **Portões**: decisões jurídicas (NFAg-e, tarifa social/NIS, prescrição, agência dos primeiros clientes); `@SpringBootApplication` + API mínima | Bloqueia R1 a R4 | Diário §15.5 |
| R1 | **Cifra de campo no Vault + `audit_log` sem PII** antes do primeiro CPF real | `14 §4.7`, §6 item 5 | Primitivo pronto; integração e correção do `audit_log` pendentes |
| R2 | Cadastro (serviço, API, UI) com cifra e índice cego | Base de tudo | Domínio e migration prontos |
| R3 | Tarifa social completa (classificação, aviso, relatório) + simulador (N4) | **11/12/2026** | Regra pronta |
| R4 | Medição, ciclo mensal, conta (+ NFAg-e conforme decisão) | Ciclo mínimo | Regras prontas; falta orquestração |
| R5 | Arrecadação: boleto v08, QR Pix, retorno, conciliação com dupla checagem (N3) | **01/02/2027** | Barcode e parser prontos |
| R6 | SINISA (exportação assistida do cadastro e do balanço) | 03/11, 17/11, 31/12/2026 | Não iniciado |
| R7 | Protocolo + OS mínima **com bloqueio de corte** | NR 11 **20/05/2027**; Lei 14.015 | Não existe |
| R8 | Cobrança: aviso de 30 dias, bloqueios por agência, parcelamento; **negativação por último** | Depois de R7 | Não existe |
| R9 | GIS, incidente/afetados (N2), perdas com entrada real, campanhas (N1) | Sem prazo legal próximo | Stubs |
| Paralelo | **MTR/qualidade/LGPD**: não ficam atrás do comercial. O diário §1 diz que são o "escopo que interessa primeiro" | `14 §6` itens 8 e 9: "antes de qualquer uso" | Ver §4 |

---

## 4. Lacunas de conformidade que o relatório ignora

Busca por termos no relatório inteiro: **zero** menções a SINISA, NFAg-e, reforma tributária, NR/ANA, Lei 14.015, Lei 14.898, ANPD, WCAG, MTR, SINIR, Portaria 888, ICP-Brasil, prescrição e GPL. O que há são "LGPD desde o desenho", "tarifa social" e um genérico "bloqueios legais".

| Área | O que falta no relatório | Fato nosso |
|---|---|---|
| **Fiscal** | NFAg-e, PIS/Cofins até 2027, IBS/CBS e *cashback* na fatura | `11`; `14 §2` |
| **Regulatório** | Perfil regulatório por tenant **com vigência** (ADASA corta só com 60 dias; ARSESP veda corte por débito de outro serviço; ciclos de média de 3 a 6). Trava legal × default (multa ≤ 2%, CDC art. 52 §1º) | `14 §4.1-4.2`; `08` R-39 |
| **Compensação automática** | Crédito por prazo descumprido (ARSAE-MG 2× fatura; AGERGS ≥ 12 h; ADASA 20%) altera o modelo de créditos do **billing P0** | `08` R-41 |
| **LGPD concreta** | Ziip como operador que vira controlador se treinar IA/benchmark; renda/NIS sensível; `audit_log` com linha inteira em claro; código cita "art. 18" onde é art. 19 II; encarregado, RIPD, incidente; Decreto 12.975/2026 (porta lógica de origem junto do IP) | `10`, `14 §4.6-4.7`, `§5`; `sigsan-lgpd` com 0 testes **[código]** |
| **Acessibilidade** | LBI art. 63; relatório só diz "app do leiturista" e "portal" sem WCAG/eMAG | `13` AS-xx; `14 §5`. O frontend de cadastro tem alguns `aria-*` (`Layout`, `Field`, `Dialog`) **[código]**, mas não há auditoria |
| **MTR sem valor legal** | O MTR do SIGSAN **não** vale sem SINIR ou sistema estadual; Portaria MMA 280/2020 em revisão; ANTT 5.998/2022 | `12`; `MtrService.java:28` já avisa **[código]** |
| **Qualidade da água** | Conformidade não é valor máximo (percentil, média móvel, plano de amostragem); comunicação de não conformidade ainda é `log.warn` (`LabService`, `ScadaIntegrationService`); seed removido por 16/28 linhas divergentes da Portaria | `09`; `14 §4.4`, §5 |
| **Atendimento** | NR 11: 24x7, protocolo numerado, relatório de reclamações, histórico de 120 meses; Lei 13.460; Lei 14.129 só se o tenant a adotou | `13`; `14 §3` |
| **Licença GSAN/GEOSAN** | O relatório diz "verificar licença". **Já temos o fato**: GEOSAN **GPL-2.0-only** (sem `LICENSE` na raiz); GSAN **sem `LICENSE`**, README cita o Portal do Software Público Brasileiro (provável GPL, não confirmada). Decisão de 11/10: só domínio, **clean-room**, sem copiar código, SQL, `.jrxml`, layouts ou nomes de coluna; layouts bancários vêm do manual FEBRABAN. Falta parecer jurídico | `00 §6`; diário §14 |
| **Higiene de dado** | Senhas em texto claro em docs e `.ini` do GEOSAN (considerar comprometidas); nomes de analistas nos comentários do GSAN; backups `banco_geosan.backup` podem ter dados reais | `00 §6` |
| **Prescrição** | Prazo aplicável à cobrança de água e esgoto: em aberto (Tema não confirmado) | `14 §7` Q3 |

---

## 5. Qualidade da escrita como especificação

**Não serve como especificação de requisitos.** Serve como resumo executivo de produto.

| Critério | Relatório ChatGPT | Nossos relatórios |
|---|---|---|
| Tamanho | ~3.050 palavras | `01`–`13`: ~125 mil palavras |
| Regras de cálculo | Nenhuma. Não há arredondamento, tolerâncias, fator de esgoto nem acréscimos | `02`: cascata de consumo, 2 percentuais de esgoto, `RoundingMode` por regra; `03`: 14 estados de pagamento |
| Casos de teste | **Zero** | `02` ~90 casos calculados à mão; `03` 30+; `09`/`10`/`13` com IDs por requisito |
| Modelo de dados | Só a tabela de entidades espaciais (5 linhas); sem ER | `01` com ER; `05` com esquema PostGIS por tenant e 26 regras Q-01…Q-26 |
| Campos de tela | Listas de "campos/ações essenciais" de 1 linha | `07` S1–S8 campo a campo, com obrigatoriedade e catálogo |
| Critério de aceite | 2 frases ("Critério de sucesso" nas Ondas 1 e 2) | Casos de teste dourados e nota de verificação [verificado]/[agente] |
| Rastreabilidade a norma | Nenhuma | `08`–`13` com ID, norma, artigo, módulo, situação, lacuna |
| Rastreabilidade a fonte | Caminhos de arquivo, sem linha nem classe | `[V]`, `[I]`, `[INF]`, `[EXT]` por afirmação |
| Honestidade de limites | Uma nota sobre o `.doc` do EPANET | `00 §7`: lacunas declaradas por relatório |
| Estado do código | Desatualizado (2b) | Cruzado com o repositório |

Pontos fortes do relatório, que merecem crédito: princípios claros ("não tratar adoção do GSAN como migração de arquitetura", "não apresentar EPANET como calculado"), diagrama de fluxo cliente→imóvel→ligação→medidor→conta, separação "solicitação ≠ OS", e a proposta de OS gerada a partir de decisão auditável.

---

## 6. Veredito e recomendação de uso

**Veredito:** síntese de produto competente e **majoritariamente redundante**. Das ~8 ideias avaliadas, 1 é totalmente nova (N1) e 6 são complementos parciais (N2 a N7). O relatório **não** tem informação regulatória, está desatualizado quanto ao código e propõe nomes de módulo que conflitam com o repositório.

**Como usar:**
1. **Visão de produto / apresentação a stakeholder:** sim, com a ressalva de que o estado do código está defasado.
2. **Backlog:** **não** use como está. Use a lista N1–N7 abaixo, já reordenada.
3. **Especificação:** não. A especificação continua sendo `01`–`07` (domínio) e `08`–`14` (regulatório).
4. **Nomes de módulo e ondas:** descartar. Vale `00 §3` + a ordem R0–R9 acima.
5. **Ação sugerida (para o Daniel, fora do meu escopo editar):** acrescentar ao topo do relatório uma nota "superado por `00`, `14` e `15c`", e atualizar o diário §11.2 (ainda diz que `commercial`/`operations` estão vazios).

### Backlog consolidado (ideias aprovadas)

| # | Item | Módulo | Prazo / gatilho | Esforço |
|---|---|---|---|---|
| 1 | Decisões jurídicas (NFAg-e, NIS/renda, prescrição, agência dos primeiros clientes) | — | antes de 01/12/2026 | — |
| 2 | Cifra de campo no Vault; `audit_log` sem PII; `@SpringBootApplication` e API mínima | `sigsan-security`, `sigsan-core`, `sigsan-app` | antes do 1º dado real | M |
| 3 | **N4** Simulador de conta e de impacto de tarifa (inclui tarifa social) | `billing` | **11/12/2026** | S |
| 4 | Tarifa social: classificação, aviso de 3 meses na fatura (mensagem por público), relatório anual | `billing`, `registry` | **11/12/2026** | M |
| 5 | Eventos: POM de `sigsan-events`, outbox por tenant, catálogo versionado (partir dos 11 nomes do relatório; sem PII no payload) | `shared/sigsan-events` | antes do ciclo | S a M |
| 6 | Ciclo mensal idempotente + **N6** painel de fechamento | `billing` | fase 4 de `00 §3` | M a G |
| 7 | Conciliação com fila de exceções, sugestão de vínculo e **N3** dupla checagem; Pix e2e | `collection` | **01/02/2027** | M |
| 8 | Exportação SINISA assistida | `compliance/sigsan-integration` | 03/11, 17/11, 31/12/2026 | M |
| 9 | Protocolo numerado, 24x7, retenção 120 meses (decidir com LGPD) + OS mínima com **bloqueio de corte** (Lei 14.015, 30 dias) | `portal`, `field` | **20/05/2027** | G |
| 10 | Cobrança: aviso, bloqueios por agência, parcelamento; negativação por último | `dunning` | após item 9 | G |
| 11 | **N2** Incidente de rede + afetados + comunicação segmentada (base legal decidida, não "consentida" por presunção) | `gis`, `field`, `portal`, `lgpd` | após 9 | M a G |
| 12 | **N5** Crítica por pares do setor | `metering` | P2 | S a M |
| 13 | **N1** Campanha de investigação de perdas + benefício medido | `losses`, `field` | P2, só com entrada real | M |
| 14 | **N7** Regra de publicação de resultado hidráulico; corrigir `.inp` antes | `gis` | P2 | S |
| 15 | Pipeline de importação/exportação GIS (já em `05` P1-7) | `gis` | P2 | M |
