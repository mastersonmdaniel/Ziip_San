# Estrutura da v3.0 do plano: raciocínio e checagem

Etapa de arquitetura da v3.0 do plano canônico, feita em 28/10/2026 sobre a v2.21. Produz a estrutura de fases, ondas e
tarefas ([estrutura-v3.json](estrutura-v3.json)) e o mapa de donos dos achados ([mapa-de-donos.json](mapa-de-donos.json)).
A redação das tarefas fica para a etapa seguinte. Nenhum arquivo do plano, dos ADRs ou dos relatórios foi alterado.

Fontes: [decisoes-do-dono-28-10-2026.md](decisoes-do-dono-28-10-2026.md), [tarefas-v2.21.json](tarefas-v2.21.json), as
seções em `secoes/`, o [22-INCORPORACAO-NO-PLANO](../../levantamento/lacunas/22-INCORPORACAO-NO-PLANO.md) (156 achados
CAD, OPE, PLT e GES) e os relatórios [23a](../../levantamento/revisao-geral/23a-coerencia-do-plano.md) a
[23e](../../levantamento/revisao-geral/23e-rastreabilidade-normas-x-plano.md) (177 achados COE, DAD, COD, JOR e RAS).
Como o dono aprovou todas as recomendações dos relatórios 23a a 23e, as propostas deles foram tratadas como decididas;
ficam pendentes só as alternativas que os próprios relatórios deixaram em aberto.

## 1. Como ler os arquivos

- `estrutura-v3.json`: `phases` traz a ordem das fases, as ondas A, B (grupos B1, B2 e o fim da onda) e C, e a seção
  PEND; `tasks` traz as 286 tarefas na ordem de execução, cada uma com `action`, `from` (IDs da v2.21 de origem), `deps`,
  `size` (o da v2.21; tarefa nova "a estimar"), `findings_owned` e `notes_for_writer`; `dropped` traz as duas tarefas
  que saem; `owner_decisions_pending` traz as decisões que dependem do dono.
- `notes_for_writer` tem duas partes: a nota de arquitetura (fronteira, decisão, pontos de ajuste a manter) e linhas
  geradas por script: ajustes da v2.21 a incorporar ao aceite, achados que a tarefa só consome, achados citados na v2.21
  que saem dela (com a tarefa dona) e diferenças de dependência em relação à v2.21.
- `mapa-de-donos.json`: um registro por achado, com `owner` (ID de tarefa, ou `doc:...` quando o achado não vira
  tarefa), `consumers` e `destination_note`.
- Ordem de precedência usada nas verificações: P0 < P1 < P2 < P3 < GL < P4 = onda A < onda B < onda C < PEND < LC.
  Dependência só para posição igual ou anterior; nada fora da PEND depende da PEND.

## 2. Totais

| Ação | Tarefas |
|---|---|
| keep | 229 |
| move | 32 |
| split | 5 |
| merge | 2 |
| new | 18 |
| **total v3.0** | **286** |
| dropped | 2 (P4.3 e P4.4) |

270 da v2.21 − 2 descartadas + 18 novas = 286. 96 títulos foram revistos: a maior parte só perdeu marcadores de versão
("(v2.13)", "(v2.17, onda B)", "(antes P0.27)"), cujo texto foi para `notes_for_writer`; o histórico fica no git e no
anexo, como manda a decisão 1.

## 3. Fases e ondas

1. **P0.** A limpeza do código passa a vir logo depois da P0.1 e antes da renomeação: P0.1 → P0.25 → P0.7 → P0.33. A
   limpeza é a P0.25 ampliada, sem ID novo, porque o 23c (§4.1) a propõe como extensão dela e a P0.25 já tratava de
   remover código morto (SINIR, Jackson 2). Recebe os itens de "Apagar" do 23c §4.3, inclusive os que o plano previa na
   P0.13 (KeycloakAdminService) e a reescrita da `V1_1`.
2. **P1.** Ganha o protocolo de atendimento (P1.36), o cadastro mínimo de ativos (P1.35), os perfis de fábrica (P1.37),
   o catálogo de eventos (P1.38), o CNPJ alfanumérico (P1.39), o importador genérico de planilha (P1.40) e a separação
   das situações do titular (P1.41).
3. **P2.** Ganha o motor de modelos (P2.30), os perfis regulatórios das agências (P2.31), o conteúdo mínimo da fatura
   com o motor de mensagens legais (P2.32), a fila fiscal do tenant (P2.33) e o cashback de CBS e IBS (P2.34). A P2.28
   vira "minhas tarefas".
4. **P3.** Ganha a página inicial com agenda (P3.39), a privacidade do titular (P3.40) e o registro único de
   indicadores (P3.41). A P3.4 passa a "Corte e religação: regras e bloqueios".
5. **GL.** A GL.2 passa a depender da P2.31 e da P2.32 (23e: perfil e conteúdo da fatura antes do paralelo).
6. **P4.** Ficam P4.1, P4.2, P4.5, P4.7 e P4.8. P4.3 e P4.4 saem (ver §4); a P4.6 vai para a PEND.
7. **Trilha.** Onda A com 34 entregas; onda B com o grupo B1 (18 entregas originais), o grupo B2 (29: as 27 entregas
   de título "onda B" que estavam na tabela da C, mais ED-56 e ED-105) e a ED-01 no fim; onda C com 12.
8. **PEND.** Seção nova, fora das ondas, dos totais e do critério da LC.1: ED-68, ED-102, ED-103, P4.6 e ED-104.
9. **LC.** Ganha a LC.15 (instância dedicada). A LC.1 passa a depender, por texto, de todas as fases e ondas, exceto a
   PEND; na coluna, de P0.27, P0.45, GL.4 e ED-01.

## 4. Fusões e descartes

- **P4.4 → ED-18.** Duplicata pura: o aceite da P4.4 era "aceite da ED-18". ED-16, ED-19 e ED-20 passam a depender da
  ED-18.
- **P4.3 → ED-46, ED-47 e ED-49.** Agrupamento sem escopo próprio. A regra "nenhum relatório nem consulta de BI no
  primário" vai para a ED-46 e a ED-47.

Nenhuma outra tarefa saiu: as sobreposições restantes foram resolvidas por fronteira, não por fusão, para preservar IDs
citados em ADRs e documentos.

## 5. Divisões

| Tarefa | Fica | Sai para | Motivo |
|---|---|---|---|
| P1.3 | modelo de dados (`V3_6`), tela e carga | P1.41 (situações do titular), P1.40 (importador) | COE-18: 13 achados 22 e 15 achados DAD numa tarefa só |
| P3.4 | corte e religação (`V9_2`) | P1.36 (protocolo, `V8_1`) | COE-10 e decisão do dono 7: o protocolo precede a OS |
| ED-04 | pacote de reversão (onda B) | LC.15 (instância dedicada) | COE-04: ciclo ED-04 → LC.2 → LC.1 → trilha |
| ED-48 | catálogo SINISA, painel, fechamento | P3.41 (registro de indicadores) | JOR-19 e COE-11 (ver §8) |
| ED-80 | água bruta, outorga, reúso, biossólido | ED-105 (efluente e lodo) | JOR-16: obrigação legal fora do pacote da onda C |

Além delas, sem mudar de ação: a ED-56 cede o cadastro de ativo à P1.35 (COE-05, decisão do dono), a ED-74 cede o
núcleo de LGPD à P3.40 (JOR-15, decisão do dono), a ED-70 cede o lodo à ED-105 e a ED-79 cede o motor de mensagens
legais à P2.32 (RAS-04, CAD-96).

## 6. Tarefas novas

| ID | Título | Pedido por |
|---|---|---|
| P1.35 | Cadastro mínimo de ativos e hierarquia de seis níveis | decisão do dono (COE-05) |
| P1.36 | Protocolo de atendimento (NR 11) como origem de toda solicitação externa | decisão do dono 7, DAD-24 |
| P1.37 | Perfis de acesso de fábrica por função, com menu próprio | decisão do dono (JOR-01) |
| P1.38 | Catálogo único de eventos de negócio | JOR-20 |
| P1.39 | CNPJ alfanumérico em todas as fronteiras | RAS-03 |
| P1.40 | Importador genérico de planilha com aplicação por OS de lote | JOR-26 |
| P1.41 | Cadastro: situações do titular e validação externa | COE-18, CAD-65 a CAD-82 |
| P2.30 | Motor único de modelos de documento e mensagem | decisão do dono (JOR-06) |
| P2.31 | Perfis regulatórios das agências de SC: carga, simulação e teste | RAS-01 |
| P2.32 | Conteúdo mínimo da fatura por lei e por agência e motor de mensagens legais | RAS-04, CAD-96 |
| P2.33 | Documentos fiscais do tenant: fila de rejeições, contingência, inutilização e reenvio | JOR-08 |
| P2.34 | Cashback de CBS e IBS na fatura | RAS-02, GES-13 |
| P3.39 | Início por perfil: página inicial e agenda | JOR-04, JOR-30 |
| P3.40 | Privacidade do titular para todo tenant | decisão do dono (JOR-15) |
| P3.41 | Registro único de indicadores e metas | JOR-19 |
| ED-104 | Trilha GRS sob demanda (PEND) | RAS-17, JOR-17, OPE-31 |
| ED-105 | Efluente da ETE e lodo de ETA e ETE (onda B) | JOR-16, OPE-25 |
| LC.15 | Instância dedicada na AWS | COE-04 |

"Minhas tarefas" (JOR-05) não ganhou ID novo: o 23d pede ampliar a P2.28, que foi renomeada e virou a dona do contrato
de item de trabalho. A limpeza antes da P0.33 também não ganhou ID (§3, item 1). Nenhuma tarefa nova no P0 foi
necessária; a sequência P0.48 em diante fica livre.

## 7. Ondas corrigidas

- **COE-02.** ED-69 a ED-79, ED-83 a ED-94 e ED-96 a ED-99 (27 entregas) passam da tabela da onda C para a onda B, no
  grupo B2. ED-80, ED-81, ED-82, ED-95, ED-100 e ED-101 continuam na C, como dizem os títulos.
- **ED-56 sobe para a B.** Com a correção, ED-72 e ED-96 (onda B) dependeriam da ED-56 (onda C). Como o cadastro de
  ativo já foi para o P1, o que resta da ED-56 (inventário MCPSB, manutenção, risco) cabe na B sem nova dependência
  posterior. A ED-72 deixa de depender da ED-59 (frota, onda C): a ED-59 passa a estender a manutenção da ED-56 para
  veículos.
- **ED-83** deixa de depender da ED-80 (onda C) e passa a depender da ED-105 (onda B), onde está o catálogo de padrão de
  efluente.
- **COE-03.** ED-68, ED-102 e ED-103 saem das ondas para a PEND, junto da P4.6 (só com demanda) e da ED-104 (GRS sob
  demanda).
- **COE-04.** A ED-04 fica na onda B só com o pacote de reversão, sem LC.2 e sem P4.6; a instância dedicada vira LC.15.

## 8. Fronteiras resolvidas

- **OS (COE-10).** P1.31: modelo, tipos, estados, efeitos, OS de lote e alvos tipados; P1.36: protocolo que origina a OS
  externa; P1.35: ativo como alvo; ED-34: preço e lançamento do serviço; P3.4: corte e religação; ED-36: programação,
  execução no backoffice, impressão e baixa posterior; ED-20: execução offline; ED-87: diário de estações (OPE-02 sai
  da P1.31, da ED-17 e da ED-50).
- **Ativos (COE-05).** P1.27: catálogo de tipos; P1.35: entidade e hierarquia; ED-56: inventário regulatório,
  manutenção e risco; ED-72: conteúdo da ISO 55001; ED-59: frota sobre a manutenção da ED-56.
- **Perdas e indicadores (COE-11, JOR-19).** P3.41: registro único de indicadores (definição, fórmula, fonte, meta,
  confiança, fechamento); ED-48: catálogo SINISA e painel sobre o registro; ED-77: fichas ISO 24510, 24512 e NR 9;
  ED-85: módulo `losses` (setorização, macromedição, balanço por setor e por DMC); ED-78: só a saída da NR 15; ED-49 e
  ED-93: leitura. O registro saiu da ED-48 para o P3 porque a P3.31 (objetivos) e a P3.39 (página inicial) leem
  indicadores antes da trilha; com a ED-48 como dona, como o JOR-19 propõe, haveria dependência de fase posterior.
- **Notificações (COE-19, JOR-20).** P1.38: catálogo de eventos; P3.36: central de avisos e registro de preferências;
  P3.5: envio com prova de entrega e e-mail por tenant; ED-41: SMS, WhatsApp e push; ED-07: alertas do tenant; P2.26:
  webhooks.
- **Integrações (COE-19).** P2.26: API pública e eventos de saída; P3.37: operação dos conectores e catálogo de
  conectores de parceiros; ED-62: conectores específicos.
- **Documentos.** P2.30: motor de modelos; P2.32: conteúdo legal da fatura; P2.17: marca nos PDFs; P2.8: arquivo da
  gráfica.
- **Fornecedores (JOR-18).** P3.31 cria o cadastro mínimo; ED-37 estende para empreiteiras; ED-58 e ED-51 leem.
- **Privacidade (JOR-15).** P0.11: classificação de dados por coluna; P3.10: incidente e RIPD; P3.40: direitos do
  titular, registro de operações e canal; ED-74: só o que a ISO/IEC 27701 pede além da lei.
- **GUC por transação.** Fica na P0.28 (COD-04); a P2.2 fica com os pools por papel.

## 9. Dependências

- **COE-06.** Declaradas na coluna: P1.2, P3.4 e ED-36 → P1.31; ED-78 → ED-85; ED-74 → P3.40 (no lugar de "módulo
  LGPD"); P3.30 → P2.28 (o escopo por área é aplicado à caixa, e não o contrário); ED-37 → P3.31; P1.26 → P1.28;
  ED-77 → ED-92; P3.10 → P2.25. Também: P0.33 → P0.25 e P0.7; P1.13 → P0.47; P3.12 → P3.22, P3.29, P3.30 e P3.31
  (COE-15).
- **Consumo de achados.** Quando uma tarefa consome o que outra constrói, a dona entrou no fecho de dependências da
  consumidora; 74 dependências foram acrescentadas assim, cada uma registrada nas notas da tarefa com o achado que a
  motiva.
- **Dependências textuais mantidas.** "Ondas A e B" na ED-01, "todas as fases" na LC.1, "fonte de cada relatório" na
  ED-46, "leiaute de contingência da NFAg-e" na ED-19, "core `V12`" na ED-55 e "decisão do dono" em ED-102 e ED-103.
  Dependências condicionais: P3.24 na GL.4 e na LC.13 (só com optantes); ED-20 na ED-58 (só a baixa de campo).
- **Caminho mais longo até o GL.4** (por número de tarefas): P0.1 → P0.7 → P0.3 → P0.5 → P1.9 → P1.20 → P1.40 → P1.3
  → P1.41 → P2.5 → P2.6 → P2.18 → P2.19 → GL.1 → GL.2 → GL.4. O fecho do GL.4 tem 81 tarefas. O §5 do plano deve
  ser refeito a partir daqui (COE-13).

## 10. Mapa de donos

- 333 achados: 311 com tarefa dona e 22 com destino documental (os 20 COE estruturais ou de documento, GES-08 no
  ADR-0042 e PLT-38 no ADR-0008, este por decisão de 22/10/2026 já registrada).
- Cada achado tem uma dona. Consumidoras são sempre de posição igual ou posterior à dona e têm a dona no fecho de
  dependências.
- Desvios deliberados em relação à tarefa sugerida nos relatórios:
  - os achados que pediam tarefa nova ficaram com a tarefa nova (P1.35 a P1.41, P2.30 a P2.34, P3.39 a P3.41, ED-104,
    ED-105), e não com as tarefas que os relatórios citavam como lugar possível;
  - CAD-65, CAD-66, CAD-67, CAD-68, CAD-78, CAD-82 e PLT-24 vão para a P1.41, separada da P1.3;
  - login gov.br (CAD-101, PLT-06, GES-32) fica com a ED-38, e o pagamento sem conta (CAD-97) com a P3.3, ambos sob o
    ADR-0043;
  - COD-08, COD-09 e COD-21 vão para a P0.25, porque são remoção antes da renomeação; COD-42 vai para a P1.4
    (matrícula por sequência); COD-26 e COD-36 vão para a P1.9 (outbox e worker);
  - JOR-36 (aba Estoque da Central) vai para a ED-58, porque a P3.15 não pode depender do almoxarifado da onda A;
  - OPE-12 fica só na ED-56; a ED-64, que vem antes na onda B, não a consome.

## 11. Decisões estruturais tomadas nesta etapa

Consequências das decisões e recomendações aprovadas; o dono pode revertê-las por cartão:

1. Limpeza como P0.25 ampliada, sem ID novo.
2. ED-56 da onda C para a B; ED-72 sem dependência da ED-59.
3. Registro de indicadores no P3 (P3.41), separado da ED-48.
4. Protocolo mínimo no P1 (P1.36), com a `V8_1` saindo da P3.4.
5. ED-105 nova na onda B, com partes da ED-80 e da ED-70.
6. P1.3 dividida (P1.41 e P1.40).
7. Login gov.br na ED-38 (onda A), e não na P3.3.

## 12. Decisões que dependem do dono

| ID | Tema | Recomendação |
|---|---|---|
| PD-01 | Recorte da prontidão de POC (ED-01) depois da correção das ondas (COE-02) | ED-01 depende da onda A, do grupo B1 e do P4; o grupo B2 entra só no critério da LC.1 |
| PD-02 | OS no núcleo ou no pacote de saneamento (COE-16) | motor de OS no núcleo; tipos e alvos de saneamento no pacote `sanitation`; registrar em ADR |
| PD-03 | Forma da regra 2 do README dos ADRs (COE-22) | linha de ajuste só para esclarecimento; mudança de substância por ADR novo que substitui |
| PD-04 | Exceção ao ADR-0010 para ingestão de supervisório (OPE-30) | ADR "Proposto" antes da onda A: REST e arquivo, somente leitura, sem broker |
| PD-05 | P4.6 na seção de pacotes sob demanda | confirmar; deixa de contar no critério da LC.1 |
| PD-06 | Atividade econômica na unidade ou no cliente (23b, inferência 3) | na unidade consumidora (NR 11, R-01) |
| PD-07 | Caixa próprio do prestador (JOR-13) | item condicional da ED-26, ligado por parâmetro; confirmar na P1.1 |
| PD-08 | Pacote de validação do sistema por versão da ISO/IEC 17025 (ED-51) | manter pendente até haver cliente que o exija |
| PD-09 | Liberação de ED-68 e ED-103 e recorte da ED-102 | manter na PEND |
| PD-10 | Pareceres jurídicos (RAS-06, RAS-11, RAS-27, RAS-28, protesto de CDA) | encaminhar primeiro o da RAS-06, que decide o escopo da P3.24 e da ED-27 |

## 13. Destinos fora das tarefas

A redação da v3.0 precisa também destes textos, que não são tarefa: ADR-0043 (altera o ADR-0022: login gov.br e
pagamento sem conta); ADR-0044 (o projeto não tem prazos; datas como indicação de sequência); nota de alinhamento ao
NIST 800-63B-4 no ADR-0040; §2.4 com as pendências da §12 e "Antes de" no lugar de "Até"; §3 com as reservas Flyway das
tarefas novas e das faixas `losses`, `lgpd`, operação, ocorrência de rede, regulatório e integração (COE-15); §5 com o
caminho da §9; glossário no §8 (COE-17, COE-23); tabela única de módulos em PLATAFORMA-E-VERTICAIS e ARQUITETURA-ALVO
atualizada (COE-16); resumo, §2.2, §1 e cabeçalho reescritos (COE-21); lista "2028, sob demanda" sem o que já está na
trilha (COE-11).

## 14. Checagem final por script

Feita com `verify.js` (no diretório de trabalho da sessão), que lê só os três arquivos de saída. Resultado:

| Verificação | Resultado |
|---|---|
| UTF-8 válido, sem BOM, sem CR, sem U+FFFD (três arquivos) | ok |
| JSON válido (`JSON.parse` no Node) | ok |
| IDs de tarefa duplicados | 0 |
| Dependência para ID inexistente | 0 |
| Dependência para tarefa descartada (P4.3, P4.4) | 0 |
| Ciclos no grafo de dependências | 0 |
| Dependência de fase ou onda posterior | 0 |
| Dependência de tarefa fora da PEND para a PEND | 0 |
| Dependência listada depois da dependente | 0 |
| Achados no mapa | 333 (CAD 40, OPE 40, PLT 42, GES 34, COE 23, DAD 40, COD 42, JOR 39, RAS 33) |
| Achados duplicados ou sem dono | 0 |
| Dono inexistente; consumidora inexistente | 0; 0 |
| Consumidora anterior à dona; consumidora sem a dona no fecho | 0; 0 |
| Divergência entre `findings_owned` e o mapa | 0 |
| Título de entrega com onda diferente da onda da tabela | 0 |
| Ondas | A 34; B 48 (B1 18, B2 29, fim 1); C 12; PEND 5 |
