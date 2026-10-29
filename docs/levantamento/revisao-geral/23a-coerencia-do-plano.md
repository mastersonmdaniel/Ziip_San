# 23a — Coerência interna do plano v2.21 e dos ADRs

Revisão estrutural do plano canônico [PLANO-EXECUCAO-PDCA-R0-R9.md](../../PLANO-EXECUCAO-PDCA-R0-R9.md) (versão 2.21,
22/10/2026) e dos ADRs em [arquitetura/adr](../../arquitetura/adr/README.md), feita em 28/10/2026. Trata de dependências,
duplicação, ajustes acumulados, mapa Flyway, ADRs, ondas, termos e resumo. Não repete as lacunas funcionais dos
levantamentos 22a a 22d. Nenhum arquivo do plano nem dos ADRs foi alterado; as propostas são recomendações, e as que
tocam decisão do dono ficam indicadas como tal.

## 0. Leitura rápida

- O grafo declarado de dependências é limpo: 270 tarefas, nenhum ID inexistente, nenhum ciclo na coluna Dep., totais de
  esforço iguais à soma das tarefas estimadas. Os problemas estão no que ficou fora da coluna Dep. e fora das tabelas.
- 28 linhas de tarefa têm uma sétima célula (cabeçalho de seis); os ajustes v2.13 a v2.15 dessas linhas, inclusive os do
  ADR-0039, somem quando o Markdown é renderizado.
- 27 entregas marcadas "onda B" no título estão na tabela da onda C; as pendentes ED-68, ED-102 e ED-103 também estão
  nela, e a LC.1 depende de "todas as fases, inclusive a onda C".
- Ciclo lógico: ED-04 (onda B) depende da LC.2, que só começa depois de toda a trilha.
- A hierarquia de ativos (v2.19) nasce na ED-56, onda C, mas a P1.31 (P1), a ED-14 e a ED-36 (onda A) já a usam.
- P1.2 (caminho crítico), P3.4 e ED-36 se apoiam na P1.31 sem dependerem dela; ED-78 cita a ED-85 só no texto.
- Tarefas incorporam login gov.br e pagamento sem conta, que o ADR-0022 vigente rejeita, sem ADR novo nem pendência.
- 123 dos 127 ajustes v2.18 são listas de títulos de achados, sem critério de aceite; 121 achados estão espalhados em
  duas a seis tarefas sem tarefa dona.
- Esforço: faltam estimar 49 tarefas não pendentes (33 da trilha e 16 das fases); 49 das 60 entregas estimadas, que somam
  188,9 das 225,8 semanas-pessoa, mudaram de escopo desde a v2.14 sem nova estimativa.

## 1. Método

1. Extração mecânica das tabelas `| # | Tarefa | Origem | Tam. | Dep. | Aceite |` por script Node, com a seção (`###`),
   a subseção de onda (`**Onda X**`), a onda declarada no título, as dependências expandidas (inclusive faixas como
   "P3.25 a P3.27" e "LC.2–LC.7"), o texto de dependência sem ID e os marcadores "Ajuste v2.xx". As células são
   separadas por `|` fora de crase.
2. Análise do grafo: IDs inexistentes, dependência de fase ou onda posterior, ciclos (DFS), órfãs, dependências
   citadas no texto ("depende da X", "antes da X", "sobre o núcleo da X") e ausentes do fecho transitivo da coluna Dep.
3. Confronto do §3 (mapa Flyway) com as versões citadas nas tarefas, nos dois sentidos.
4. Confronto dos achados CAD, OPE, PLT e GES citados no plano com a coluna "Destino no plano" do
   [22-INCORPORACAO-NO-PLANO](../lacunas/22-INCORPORACAO-NO-PLANO.md).
5. Leitura dirigida dos trechos apontados pelos scripts, das seções 1, 2, 5, 6, 7 e 8 do plano, do índice e do status de
   cada ADR, de [ARQUITETURA-ALVO](../../arquitetura/ARQUITETURA-ALVO.md) e de
   [PLATAFORMA-E-VERTICAIS](../../arquitetura/PLATAFORMA-E-VERTICAIS.md); contagem de termos por `grep`.

Scripts em [scripts/](scripts/), executáveis com o Node 24 a partir da pasta `scripts`:

| Script | Faz |
|---|---|
| `01-extrair-tarefas.js` | extrai as 270 tarefas para JSON (`node 01-extrair-tarefas.js > tarefas.json`) |
| `02-dependencias.js` | IDs inexistentes, fase e onda posteriores, pendentes, ciclos, órfãs, dependência textual, onda divergente |
| `03-rastreabilidade-22.js` | achados do relatório 22 sem tarefa, com destino divergente, e distribuição por tarefa |
| `04-flyway.js` | versões repetidas no §3, reserva que a tarefa não cita, versão citada sem reserva, tarefas sem versão |
| `05-esforco-ajustes-referencias.js` | esforço por onda, ajustes por versão, achados em várias tarefas, referências adiante, fecho do GL.4 |
| `06-conferir-codificacao.js` | UTF-8 válido, sem U+FFFD, sem CR e sem BOM |

Os scripts 02 a 05 recebem o `tarefas.json` como argumento. O JSON não foi gravado no projeto: é gerado de novo a cada
execução, a partir do plano vigente.

## 2. Quadro de achados

| ID | Tipo | Gravidade | Onde | Proposta |
|---|---|---|---|---|
| COE-01 | Formato que oculta conteúdo | alta | 28 linhas: P0.13, P1.1, P1.2, P1.3, P1.26, P2.5, P2.10, P2.20, P3.1, P3.3, P3.4, P3.20, P3.22, P3.25, ED-06, ED-13, ED-16, ED-20, ED-22, ED-24, ED-26, ED-30, ED-32, ED-35, ED-36, ED-54, ED-64, ED-65 | Trocar, em cada uma, o ` \| ` que antecede o primeiro `**Ajuste` por um espaço, juntando o ajuste à coluna Aceite. Correção mecânica, sem mudança de texto; conferir depois com o `01-extrair-tarefas.js` (nenhuma linha com mais de 6 células) |
| COE-02 | Onda ambígua | alta | ED-69 a ED-79, ED-83 a ED-94, ED-96 a ED-99 (título "onda B", tabela "Onda C"); ED-01; M12 | Criar a subtabela "Onda B (v2.17 e v2.18, a estimar)" e mover as 27 linhas para ela; registrar se essas entregas entram na prontidão de POC (ED-01 depende de "ondas A e B"), o que é decisão do dono |
| COE-03 | Pacote pendente dentro de onda e de critério de fase | alta | ED-68, ED-102, ED-103; LC.1; M14; §7 (linha da trilha) | Seção própria "Pacotes pendentes, fora das ondas e dos totais", depois da onda C; LC.1 e M14 passam a ler "todas as fases e ondas, exceto pacotes pendentes"; §7 sem as pendentes |
| COE-04 | Ciclo lógico | alta | ED-04 → LC.2 → LC.1 → "todas as fases, inclusive a trilha" | Dividir a ED-04: pacote de reversão na onda B (sem LC.2) e instância dedicada como tarefa da fase LC, depois da LC.2; retirar a P4.6 ("só com demanda") das dependências da parte que fica na onda B |
| COE-05 | Ordem invertida entre entidade e consumidor | alta | P1.31 (ajuste v2.19), ED-14, ED-36, ED-56, ED-59, ED-72, ED-92 | Separar da ED-56 o cadastro de ativo e a hierarquia de seis níveis numa tarefa anterior às consumidoras (fase P2 ou P3, ou onda A), com reserva Flyway; na P1.31, alvos como contrato tipado, com validação de existência habilitada quando o tipo de alvo existir; rever tamanho e dependências da P1.31 |
| COE-06 | Dependência fora da coluna Dep. | alta | P1.2, P3.4, ED-36 (→ P1.31); ED-78 (→ ED-85); ED-74 ("módulo LGPD"); P2.28 (→ P3.30); P3.31 (→ ED-37); P1.26 (← P1.28); ED-77 (← ED-92); P3.10 (→ P2.25) | Acrescentar os IDs na coluna Dep.; trocar "módulo de perdas" por ED-85 e "módulo LGPD" pelo ID que o cria; P2.28 só com escopo por área depois da P3.30 (ou dividir); P3.31 avalia fornecedor genérico, com a ED-37 como extensão |
| COE-07 | Tarefa contra ADR vigente | alta | P3.3, P0.38, ED-38 (CAD-97, CAD-101, PLT-06, GES-32) × ADR-0022; ED-06, P3.3 (PLT-30) × ADR-0040; PLT-38 × ADR-0008 | Registrar ADR "Proposto" que altera o ADR-0022 (login gov.br e pagamento sem conta) e pendência no §2.4; nas tarefas, marcar esses itens como condicionados ao ADR; nota de alinhamento ao NIST 800-63B-4 no ADR-0040, prevista no 22c e não aplicada |
| COE-08 | Ajuste sem critério de aceite | média | 123 dos 127 marcadores v2.18 ("levantamentos 22a a 22d: …") | Converter cada achado listado em critério testável ou em remissão "fora desta tarefa: ver X"; até lá, nota no §4 de que a lista é escopo indicativo, com aceite a definir |
| COE-09 | Achado sem tarefa dona | média | 121 de 154 achados em duas a seis tarefas; ex.: CAD-96, PLT-02, PLT-03, PLT-12, PLT-13, PLT-20, GES-05, GES-15 | Na tabela do 22-INCORPORACAO, marcar a tarefa que constrói e as que consomem; nas consumidoras, trocar a lista por "usa X da tarefa Y" |
| COE-10 | Sobreposição: OS | média | P1.31, P3.4, ED-20, ED-34, ED-36, ED-87; OPE-02 em P1.31, ED-17, ED-50 e ED-87 | Fronteira do §3.10: P1.31 modelo e tipos; ED-34 preço e faturamento do serviço; P3.4 regras de corte e religação (título e `V9_1` corrigidos); ED-36 programação e execução no backoffice; ED-20 execução offline; OPE-02 só na ED-87 |
| COE-11 | Sobreposição: perdas e indicadores | média | ED-48, ED-77, ED-78, ED-85, ED-86, ED-90, ED-94; schema `losses`; §4 "2028, sob demanda"; §7 linha 2028 | ED-85 dona do módulo `losses` (setorização, macromedição, balanço por setor e por DMC); ED-78 só a saída da NR 15; ED-48 catálogo SINISA, painel e motor único de indicadores; ED-77 conteúdo das ISO 24510 e 24512 sobre esse motor; tirar da lista de 2028 o que já está na trilha |
| COE-12 | Esforço desatualizado | média | §4 (trilha), §5 (M12, M14), 49 tarefas a estimar, 49 entregas reajustadas | Rodada de estimativa indicativa das 49 tarefas e revisão das 49 entregas reajustadas; enquanto isso, M12 e M14 com "esforço a recalcular" no lugar de 217,8 e 234,2 |
| COE-13 | Caminho crítico desatualizado | média | §5: caminho até GL.4 e marcos M6 a M8 | Incluir P1.31 (antes de P1.2, P1.3 e P2.20), P3.29 e P3.30 (antes de GL.2) no caminho; marcos M6 a M8 com as tarefas novas da fase ou com "todas as tarefas da fase" |
| COE-14 | Data tratada como prazo depois da diretriz de 21/10 | média | §5 (texto e M12, M14), §8 ("Aceite de fase"), §2.4 (coluna "Até"), §6 ("data-limite 13/11"); ADR-0035 item 1 | Registrar a diretriz em ADR ou em ajuste do ADR-0035 (regra do §8); nos trechos citados, data como "indicação de sequência" e coluna "Até" como "Antes de" (tarefa ou marco); decisão do dono |
| COE-15 | Mapa Flyway incompleto ou divergente | média | §3; P1.32, P1.34, P2.25, P2.26 a P2.29, P3.4, P3.12, P3.31 a P3.38, ED-74, ED-78, ED-83 a ED-101 | Reservas `V21_1` a `V21_3` e `R__` do catálogo de normas no core; faixas para `losses`, `lgpd`, operação, ocorrência de rede, regulatório e integração; tirar `V9_1` da P3.4; P3.12 dependente das tarefas que usam faixa |
| COE-16 | Arquitetura e fronteira divergentes | média | ARQUITETURA-ALVO; PLATAFORMA-E-VERTICAIS §2 e §3; plano §3 e §8; P1.32, P2.25 | Tabela única de módulos (nome Maven, schema, faixa Flyway, núcleo ou pacote) em PLATAFORMA-E-VERTICAIS, citada pelo §3; decidir se a OS é do núcleo; atualizar ARQUITETURA-ALVO com os ADRs 0034 a 0042 |
| COE-17 | Termo com dois sentidos | média | "imóvel", "unidade consumidora", "matrícula", "edificação", "economia" (P2.23, `V3_7`, piso de agregação, ED-95, alvo `BUILDING`, ADR-0039) | Glossário no §8 com imóvel ou lote (físico), edificação, unidade consumidora (raiz comercial), economia, matrícula (identificador da unidade) e ligação; rever `service_connections` "1:1 com o imóvel" |
| COE-18 | Tarefa que pede reescrita | média | P1.31, P3.4, P3.3, P3.22, P1.3, P2.5, ED-17, ED-35, ED-48, ED-56 | Reescrever o aceite consolidado, com o histórico de ajustes ao fim; padronizar "Complemento de 15/10/2026" (P3.1, P3.4, ED-32) e "verificado em 22/10/2026" nas tarefas (ED-67, ED-82) como "Ajuste v2.xx" |
| COE-19 | Sobreposições menores | baixa | P0.47 × P1.13; P3.32 × ED-16; P3.5 × P3.36 × ED-41 × ED-07; P2.26 × P3.37 × ED-62; P1.6 × P2.29 | Fronteiras do §3.19 |
| COE-20 | Pendências de decisão incompletas | baixa | §2.4 | Incluir em "Em aberto": liberação de ED-68, ED-102 e ED-103, ADR que altera o ADR-0022, exceção do ADR-0010 para ingestão de supervisório (OPE-30 na ED-17); separar "Resolvidas" por data |
| COE-21 | Resumo e cabeçalho defasados | baixa | §7; §2.2; §1; cabeçalho (v2.20) | Ver §3.21 |
| COE-22 | Governança dos ADRs | baixa | README dos ADRs (regra 2); ADR-0001, ADR-0011, ADR-0029, ADR-0035 | Ajustar a regra 2 à prática das linhas de ajuste, ou voltar a substituir por ADR novo; ADR-0029 "Substituído por ADR-0035"; ver §3.22 |
| COE-23 | Termos sobrecarregados | baixa | "protocolo", "Central", "pacote", "go-live" | Qualificar sempre: "protocolo de atendimento" × "chamado de suporte"; "Central Ziip" só para o console; "pacote de vertical", "pacote de edição" etc.; "go-live" só no histórico |

## 3. Detalhamento

### 3.1 COE-01 — Coluna extra oculta ajustes

O cabeçalho das tabelas de tarefa tem seis colunas. Em 28 linhas, um `|` separa o aceite original do primeiro ajuste
(v2.13, v2.14 ou v2.15), o que cria uma sétima célula. Na especificação de tabelas do GitHub Flavored Markdown, células
além do número do cabeçalho são descartadas. Ficam invisíveis na leitura renderizada, entre outros:

- P1.3: toda inclusão ou alteração de cliente e unidade gera OS interna (ADR-0039) e o teste de arquitetura que recusa
  gravação direta;
- P2.20: dependência da P1.31 e retificação como OS interna tipada;
- P3.4: corte como operação do núcleo de OS, compensação por corte indevido, seleção mínima do aviso, revalidação do
  corte offline e o complemento de 15/10/2026;
- P0.13 e ED-06: catálogo `recurso:ação` e perfis compostos (ADR-0040);
- P3.1: efeito da classificação de pagamento;
- ED-36: "constrói sobre o núcleo da P1.31".

Os ajustes v2.17 em diante, quando estão na mesma linha, ficam dentro dessa sétima célula e somem junto. A correção é
mecânica e não muda texto.

### 3.2 COE-02 — Onda do título × tabela

As entregas ED-69 a ED-82 (v2.17) e ED-83 a ED-103 (v2.18) foram acrescentadas no fim da tabela "Onda C". O título de 27
delas diz "onda B". Consequências:

- ED-01 (prontidão de POC, dependência "ondas A e B") e o marco M12 passam a depender, ou não, de pacotes ISO, do módulo
  de perdas e de operação de estações, conforme se leia o título ou a tabela.
- Pela onda do título, há quatro dependências de onda B para onda C: ED-72 → ED-56 e ED-59; ED-83 → ED-80; ED-96 →
  ED-56. Pela tabela, essas dependências ficam dentro da onda C e são coerentes; a contradição vem só do título.
- O cartão de 22/10/2026 pôs o módulo de perdas na onda B (ED-85); a tabela o põe na C.

### 3.3 COE-03 — Pacotes pendentes no critério do lançamento

ED-68 (v2.16), ED-102 e ED-103 (v2.18) são "pacote pendente: sem estimativa, fora dos totais, até o dono liberar", mas
estão na tabela da onda C. LC.1 depende de "todas as fases concluídas, inclusive a onda C"; M14 tem o mesmo critério; o
§7 lista "ED-01 a ED-103" como condição do lançamento. Lido ao pé da letra, o lançamento espera pacotes parados por
decisão do dono. Nenhuma tarefa depende das pendentes (verificado por script).

### 3.4 COE-04 — Ciclo da ED-04

ED-04 (onda B, "Instância dedicada e pacote de reversão") depende de "LC.2 para a instância dedicada". LC.2 depende de
LC.1, cuja dependência é "todas as fases concluídas, inclusive a onda C". A trilha inteira, que contém a ED-04, precede
a LC.1. O ciclo não aparece no grafo porque a dependência da LC.1 é textual. A ED-04 também depende da P4.6, que é
"só com demanda": uma entrega contada na prontidão de POC fica presa a uma tarefa condicional.

### 3.5 COE-05 — Ativos e alvos da OS

O ajuste v2.19 da P1.31 (P1) torna a OS polimórfica, com alvos `OPERATIONAL_SITE`, `EQUIPMENT`, `COMPONENT`, `BUILDING`,
`FLEET_ASSET`, `NETWORK_INCIDENT` e outros. As entidades correspondem a tarefas posteriores: o cadastro `asset` com a
hierarquia de seis níveis está no ajuste v2.19 da ED-56 (onda C); a frota, na ED-59 (onda C); a ocorrência de rede, na
ED-92 (onda B). Na onda A, a ED-14 diz que o hidrômetro é "ativo com classe própria no catálogo de ativos" e a ED-36
trata "alvos múltiplos e tipados da P1.31, inclusive equipamento e componente", ambas antes da ED-56. A ED-72 (onda B)
declara dependência da ED-56. A P1.31 manteve o tamanho G e as dependências originais (P0.19, P0.33, P1.9) depois de três
ajustes (v2.17, v2.18 e v2.19). O catálogo de instalações foi para a P1.27 (catálogo de fábrica do GIS), o que dá ao
mesmo conceito três lugares: P1.27, ED-56 e ED-72.

### 3.6 COE-06 — Dependências fora da coluna Dep.

| Tarefa | O texto diz | Coluna Dep. |
|---|---|---|
| P1.2 | "cada carga gera uma OS interna de lote … pela regra da P1.31" (v2.14) | P1.1, P1.4, P0.28; P1.31 fora do fecho |
| P3.4 | "corte e religação como operações do núcleo da P1.31" (v2.14) | P2.5; P1.31 fora do fecho |
| ED-36 | "constrói sobre o núcleo da P1.31" (v2.14) | ED-34, P3.4, P1.9, P2.1; P1.31 fora do fecho |
| ED-78 | "passa a depender da ED-85" (v2.18) | "ED-48, ED-17, P4.2, módulo de perdas" |
| ED-74 | — | "módulo LGPD", sem ID |
| P2.28 (P2) | "escopo por área (P3.30)" | P1.31, P2.20 |
| P3.31 (P3) | "avaliação de fornecedor sobre a ED-37" (trilha) | P2.25, P3.22, P3.30, P3.15 |
| P1.28 | "antes da P1.26" | a P1.26 não depende da P1.28 |
| ED-92 | "antes da ED-77" | a ED-77 não depende da ED-92 |
| P3.10 | "se ligam à ocorrência da P2.25" | "—" |

A P1.2 está no caminho crítico do §5; a falta da P1.31 nela deixa o importador sem a OS de lote que o ADR-0039 exige.

### 3.7 COE-07 — Tarefas contra ADR vigente

- O ADR-0022 fixa o login do portal por CPF, CNPJ ou e-mail com senha e rejeita, entre as alternativas, "acesso só por
  matrícula e CPF, sem conta". A v2.18 incluiu CAD-97 (pagamento sem conta) e CAD-101, PLT-06 e GES-32 (login gov.br)
  nos ajustes da P3.3, da P0.38 e da ED-38. O cabeçalho da v2.18 e o 22-INCORPORACAO dizem que esses itens dependem de
  ADR que altera o ADR-0022; esse ADR não existe nem como "Proposto", a pendência não está no §2.4 e as tarefas não
  trazem a condição. Pela regra do plano, prevalece o ADR, e o aceite fica contraditório.
- O ADR-0040 e o aceite da ED-06 tratam expiração e complexidade de senha por tenant. A PLT-30, incorporada na ED-06 e
  na P3.3, alinha a política ao NIST 800-63B-4, que veda troca periódica sem indício de comprometimento e regras de
  composição. A configuração por tenant resolve o conflito (o PARAMETROS-DO-TENANT já deixa a expiração desligada), mas
  a nota de alinhamento no ADR-0040, prevista no 22c, não foi aplicada.
- PLT-38 prevê ajuste no ADR-0008 e no RNF-41, sem tarefa com ID; o ADR-0008 não mudou.

### 3.8 COE-08 — Ajustes v2.18 como lista de títulos

Dos 127 marcadores "Ajuste v2.18", 123 trazem só "levantamentos 22a a 22d:" seguido de IDs e títulos de achados. Não
dizem o que muda no aceite. Exemplos: a ED-17, tamanho M (2), recebe dez achados, entre eles OPE-04 (gestão de pressão,
que é a ED-86), OPE-02 (diário de operação, que é a ED-87) e PLT-01 (API pública, que é a P2.26); a ED-48 e a ED-78
citam "OPE-01 Módulo de perdas operacional sem tarefa dona", quando a ED-85 passou a ser a dona. O aceite efetivo dessas
tarefas só se reconstrói lendo os relatórios 22a a 22d.

### 3.9 COE-09 — Achados sem tarefa dona

A rastreabilidade com o 22-INCORPORACAO confere: 154 dos 156 achados aparecem no plano (OPE-31 e PLT-38 ficam sem
tarefa por decisão registrada) e os destinos batem, salvo tarefas a mais em GES-08 (ED-43), GES-15 (P0.30, P3.24,
ED-27), GES-19 (P3.5, LC.2) e GES-32 (P0.38). O problema é a distribuição: 121 achados estão em duas ou mais tarefas
(58 em duas, 42 em três, 16 em quatro, 2 em cinco, 3 em seis), sem indicação de qual constrói. Casos de construção
concorrente:

- CAD-96, motor de mensagens legais da fatura: P2.5, P2.17 e P2.24;
- PLT-12, central de notificações do usuário: P1.9, P1.18, P3.36 e ED-07 (a P3.36 tem o mesmo título do achado);
- PLT-02, webhooks de saída: P1.9, P2.26 e ED-07;
- PLT-03, restauração seletiva de um tenant: P0.28, P3.16, P3.33, GL.3 e LC.3 (a P3.33 é a tarefa nova do achado);
- PLT-20, hub de integrações: P3.15 e P3.37;
- GES-05, agenda de obrigações regulatórias: P3.15, ED-07 e ED-98;
- PLT-13, caixa de pendências: P1.27, P2.20, P2.22, P2.28, P3.1 e P3.30.

### 3.10 COE-10 — OS em várias tarefas

| Tarefa | Hoje | Fronteira proposta |
|---|---|---|
| P1.31 | núcleo: tipos de serviço com "operação, efeito, aprovação e débito", estados, efeitos, OS de lote, alvos; e ainda OPE-02 e OPE-14 | modelo, tipos, estados, efeitos e alvos; o débito do tipo só como referência ao item de preço da ED-34; OPE-02 sai |
| P3.4 | título "R7 mínimo: protocolo NR 11, OS com bloqueio de corte"; aceite cita `V9_1` | "Corte e religação: regras e bloqueios"; `V9_2` apenas |
| ED-34 | catálogo de serviços e serviços faturáveis; cobrança no encerramento | preço, vigência e lançamento da cobrança sobre os tipos da P1.31 |
| ED-36 | OS completa no backoffice; depende de P3.4, não de P1.31 | programação, executores, desdobramento, retrabalho, materiais previstos; depende da P1.31 |
| ED-20 | OS em campo offline | execução e sincronização |
| ED-87 | diário, ronda e turno de estações | dona de OPE-02 (retirar de P1.31, ED-17 e ED-50) |

### 3.11 COE-11 — Perdas e indicadores

- Balanço hídrico em três lugares: ED-48 ("macromedição e balanço por setor", ajuste com OPE-01), ED-85 ("balanço
  hídrico por DMC") e ED-78 ("balanço padronizado na base do SINISA com entrada real"). O schema `losses` já existe
  (`V2_x`), mas o §3 não tem faixa para ele.
- Motor de indicadores em dois lugares: ED-48 ("indicadores parametrizáveis com metas, fechamento e semáforo"; o ajuste
  v2.17 diz que o motor comporta a ficha da ED-77) e ED-77 ("Motor de fichas de indicador"). GES-03, GES-30 e OPE-08
  estão nos dois; OPE-08 e GES-21 também na ED-90.
- A lista "2028, sob demanda" do §4 (P4) ainda traz "R9 sem o GIS (incidente, N5, N1, N7, perdas com entrada real)" e
  "ISO 27001", e a linha 2028 do §7 repete "R9 sem o GIS, ISO 27001". Desde a v2.17 e a v2.18, incidente é a ED-92,
  perdas com entrada real são a ED-78 e a ED-85, e a ISO 27001 é a ED-73, todas na trilha.

### 3.12 COE-12 — Esforço

Os totais do texto conferem com a soma das tarefas estimadas: onda A 120,9; onda B 88,5; onda C 16,4; trilha 225,8;
M12 = A + B + 8,4 do P4 = 217,8; M14 = 225,8 + 8,4 = 234,2; P4.3 = ED-46 + ED-47 + ED-49 = 24. Falta estimar:

| Grupo | Tarefas | Quantidade |
|---|---|---|
| Trilha, onda B pelo título | ED-69 a ED-79, ED-83 a ED-94, ED-96 a ED-99 | 27 |
| Trilha, onda C | ED-80, ED-81, ED-82, ED-95, ED-100, ED-101 | 6 |
| Trilha, pendentes (fora dos totais por decisão) | ED-68, ED-102, ED-103 | 3 |
| Fases | P1.32, P1.33, P1.34, P2.25 a P2.29, P3.31 a P3.38 | 16 |

Além disso, 49 das 60 entregas estimadas (188,9 de 225,8 semanas-pessoa, 84%) receberam ajuste de escopo da v2.14 em
diante sem nova estimativa. As tarefas de fase fora do GIS seguem só com P, M e G, e a P1.28 tem "custo a estimar". O
tamanho continua indicação de esforço, sem conversão em equipe (ADR-0035).

### 3.13 COE-13 — Caminho crítico

O fecho de dependências do GL.4 tem 39 tarefas. O texto do §5 não cita P1.31, P3.29 e P3.30, que entraram nesse fecho
na v2.14 e na v2.15 (P1.3 e P2.20 dependem da P1.31; GL.2 depende da P3.29 e da P3.30). A maior cadeia por número de
tarefas é P0.1 → P0.3 → P0.5 → P0.27 → P0.28 → P1.8 → P2.6 → P2.18 → P2.19 → GL.1 → GL.2 → GL.4, que passa pelo porte
fiscal e pelos documentos antigos, e não pelo ciclo de faturamento descrito como crítico. Os marcos M6 a M8 listam as
tarefas do GIS e omitem as tarefas novas das fases (P1.31 a P1.34, P2.24 a P2.29, P3.28 a P3.38).

### 3.14 COE-14 — Datas depois da diretriz de 21/10/2026

A v2.16 registrou que o projeto não está sujeito a prazos e que as datas das fases e dos marcos indicam só sequência.
Continuam tratando data como meta ou prazo: o §5 ("metas de calendário", "Não há folga", "Atraso da trilha desloca este
marco", M12 "meta de calendário"); o §8 ("tarefa que escorregar recebe nova data aqui e no diário"); a coluna "Até" do
§2.4, com datas; o §6 ("data-limite 13/11"). O ADR-0035, item 1, ainda diz "metas de calendário", e a diretriz não
virou ADR nem linha de ajuste, contra a regra do §8 ("decisão nova só por ADR"). Datas legais externas (NFAg-e, CBS/IBS,
fim de suporte do Spring Boot) não entram neste achado.

### 3.15 COE-15 — Mapa Flyway

1. Tarefas novas sem reserva nem faixa: P1.33 (visões salvas), P2.26 (credenciais e eventos da API pública), P2.27
   (domínio do tenant), P2.28 (caixa e delegação), P2.29 (versões tarifárias e simulação), P3.32 a P3.38 (regras de
   qualidade cadastral, assinatura e carimbo de tempo, verificação pública da fatura, central de notificações,
   credenciais e reprocessamento de integrações, divulgação de vulnerabilidade). Na trilha, ED-83 a ED-101 criam
   entidades de módulos sem faixa: `losses` (ED-78, ED-85, ED-86, ED-94), `lgpd` (ED-74), operação de estações (ED-87,
   ED-89, ED-90, ED-93), ocorrência de rede (ED-92), regulatório (ED-96 a ED-99).
2. P1.32, P2.25 e P3.31 citam `V21_1`, `V21_2` e `V21_3`, usados antes da P3.12, mas o §3 só tem a faixa `V21_x`; o
   parágrafo que lista as reservas condicionais (`V4_8`, `V15_1`, `V6_5`, `V1_8`, `V13_6` a `V13_8`) não as inclui.
3. P1.34 carrega o catálogo de normas "no catálogo global do core (`V9_8`, migration repetível)". `V9_8` é migration
   versionada da P1.32; a carga precisa de um `R__` no core, que o §3 não registra (só há o `R__gis_factory_catalog.sql`
   de tenant).
4. P3.4 cita `V8_1` e `V9_1`; `V9_1` passou à P1.31 na v2.14, e os bloqueios de corte estão em `V9_2`.
5. P3.12 (fim do `outOfOrder`) depende só da P1.14. Nada no grafo a põe depois das tarefas de P2 e P3 que usam versão de
   faixa; o texto do §3 resolve por condição, o que deixa a ordem implícita.
6. `V1_5` e `V13_x` aparecem em duas tabelas do §3, com conteúdo coerente; é só redundância.

### 3.16 COE-16 — Arquitetura, módulos e fronteira

- ARQUITETURA-ALVO declara como base os ADRs até o 0033. Não registra GeoServer (ADR-0035, exceção do ADR-0010),
  pgRouting e o acesso do QGIS (ADR-0034), modelo de linguagem de terceiro (ADR-0028, item c; ADR-0042), API pública e
  webhooks (P2.26), nem serviço de carimbo de tempo (P3.34).
- Nomes de módulo em três formas: o plano e o FISCAL-PORTE-JAVA usam `commercial/sigsan-*` (que a P0.33 transforma em
  `commercial/ziipsan-*`); o PLATAFORMA-E-VERTICAIS prevê `platform/*` e `packages/sanitation/*`, "nomes definitivos na
  P0.47"; o §3 usa nomes curtos (`registry`, `billing`, `field`, `losses`). A P0.33 e a P0.47 renomeiam os mesmos módulos
  em sequência, sem regra escrita de qual nome prevalece.
- O PLATAFORMA-E-VERTICAIS põe "OS" no pacote de vertical e o motor de certificações no núcleo. A P1.32 (núcleo de
  certificações) depende da P1.31 (OS), e a P2.25 faz "pontes com protocolo, OS"; a própria P1.32 exige teste de que o
  núcleo não depende de pacote. Com os alvos genéricos da v2.19, a OS se comporta como serviço do núcleo.
- O manifesto prevê "faixa de versões Flyway do pacote", enquanto o §3 adota sequência global a partir de `V100` depois
  da P3.12.

### 3.17 COE-17 — Imóvel, unidade consumidora e matrícula

O ADR-0039 iguala "unidade consumidora" à "unidade usuária" da NR 11 e ao "imóvel" do GSAN e dos textos anteriores. O
plano usa "imóvel" 51 vezes, muitas no sentido físico, distinto da unidade: P2.23 ("vínculo feição ↔ imóvel ↔ ligação ↔
ramal"), `V3_7` (`service_connections` "1:1 com o imóvel por tipo"), piso de agregação ("mínimo de 5 imóveis"), ED-95
("cruzamento de edificações com unidades"), alvo `BUILDING`. "Matrícula" aparece como identificador (`V3_5`, sequência
de matrícula) e como entidade ("saldo por matrícula", "protocolo da matrícula"). "Economia" aparece duas vezes, sem
definição. Se imóvel for a unidade, a regra 1:1 da ligação impede condomínio com várias unidades numa ligação; se for o
lote físico, a regra precisa dizer isso.

### 3.18 COE-18 — Tarefas para reescrever

| Tarefa | Caracteres | Ajustes | Achados 22 | Observação |
|---|---|---|---|---|
| P1.31 | 2.260 | v2.17, v2.18, v2.19 | 8 | escopo multiplicado, tamanho e dependências iguais |
| P3.4 | 2.126 | v2.14, v2.15, v2.18 e complemento | — | título e `V9_1` anteriores à v2.14 |
| P3.22 | 2.040 | v2.15, v2.17, v2.18 | — | |
| P3.3 | 1.927 | v2.14, v2.18 | 9 | inclui itens do COE-07 |
| P1.3 | 1.164 | v2.14, v2.18 | 13 | |
| P2.5 | 1.517 | v2.15, v2.18 | 11 | |
| ED-17 | 1.197 | v2.18 | 10 | tamanho M (2) |
| ED-35 | 1.743 | v2.15, v2.17, v2.18 | 10 | |
| ED-48 | — | v2.17, v2.18 | 8 | ver COE-11 |
| ED-56 | 1.750 | v2.17, v2.18, v2.19 | 6 | ver COE-05 |

Os marcadores também variam: "Complemento de 15/10/2026" aparece em P3.1, P3.4 e ED-32, e "verificado em 22/10/2026"
aparece sete vezes no plano, duas dentro de tarefas (ED-67 e ED-82, esta no título), inseridas na v2.21 sem marcador
de versão.

### 3.19 COE-19 — Sobreposições menores

- P0.47 (teste de fronteira núcleo × pacote, P0) e P1.13 (fronteiras de módulos com ArchUnit ou Spring Modulith, P1):
  mesma ferramenta, ordem invertida, sem dependência. Proposta: P1.13 depende da P0.47 e estende a mesma suíte.
- P3.32 (fila de correção cadastral que gera OS de lote) e ED-16 (recadastramento em lotes de OS). Proposta: P3.32 gera
  a fila; ED-16 executa campanha de campo sobre ela.
- P3.5 (preferências por finalidade, CAD-84), P3.36 (preferência por canal e finalidade), ED-41 (opt-in por canal e
  finalidade) e ED-07 (PLT-12). Proposta: P3.36 dona do registro de preferências; P3.5 e ED-41 consomem.
- P2.26 (catálogo de conectores de parceiros), P3.37 (hub de integrações) e ED-62 (conectores de ERP). Proposta: P2.26
  só API e eventos; P3.37 operação dos conectores; ED-62 conectores específicos.
- P2.29 diz que "corrige a remissão da P1.6", mas a P1.6 ainda lista GES-01 (gestão tarifária) e GES-11.

### 3.20 COE-20 — §2.4

"Em aberto" não lista: liberação de ED-68, recorte da ED-102 e liberação da ED-103; o ADR que altera o ADR-0022; a
exceção do ADR-0010 para ingestão de supervisório (OPE-30 no ajuste da ED-17 fala em "rascunho do ADR de exceção").
"Resolvidas pelo dono do produto em 12/10/2026" contém decisões de 14, 21 e 22/10/2026.

### 3.21 COE-21 — Resumo, §2.2, §1 e cabeçalho

- §7: as linhas de P1, P2 e P3 param nas tarefas da v2.12; o parágrafo "Acréscimos das versões 2.13 a 2.15" cobre até a
  v2.19, fora de ordem (2.16 depois de 2.19), não cita a P1.34 (v2.20) nem a v2.21, e resume as tarefas da v2.18 só como
  "12 tarefas novas". A linha da trilha diz "ED-01 a ED-103 (menos ED-60 e ED-61; ED-52 e parte da ED-55 no P1–P3)":
  inclui as pendentes e omite ED-02, ED-03, ED-21 e ED-45, que estão nas fases. A linha 2028 repete o que o COE-11
  aponta. Há "127tarefas" sem espaço.
- §2.2: título "Decisões de 12/10/2026" com ADRs de 14, 21 e 22/10; as linhas 0035 a 0042 têm três células e a tabela
  tem duas colunas (o status é descartado na renderização); ordem 0029, 0033, 0030.
- §1 "Estado atual (12/10/2026)" não foi revisto desde a v2.1.
- Cabeçalho da v2.20: lista ajustes em ED-76 e P3.15; a P1.32 também tem "Ajuste v2.20".
- As listas de ajustadas das v2.13, v2.14, v2.15, v2.17 e v2.19 conferem com os marcadores das tarefas.

### 3.22 COE-22 — Governança dos ADRs

- A regra 2 do README dos ADRs diz que ADR não se reescreve e que só a linha de status muda. 26 dos 42 ADRs têm linhas
  "Ajuste de …", algumas com mudança de substância (ADR-0001: marco de go-live passa a homologação local e lançamento
  em 2028).
- ADR-0001 mantém o título "com marco de go-live piloto" no arquivo e no índice.
- ADR-0029 está "Revogado"; a decisão que o substitui é o ADR-0035, e a regra 2 pede "Substituído por ADR-0035". A nota
  da v2.12 no cabeçalho do plano não menciona a criação do ADR-0035.
- ADR-0035 junta duas decisões sem relação (planejamento sem equipe e GeoServer).
- ADR-0011 conserva no nome do arquivo "hospedagem-a-decidir", decidida em 12/10/2026.
- As referências do README não incluem 21a e 21b (base do ADR-0041), 22a a 22d (base do ADR-0042 e de ajustes) nem
  `docs/normas`.
- Índice do README e status nos arquivos estão iguais para os 42 ADRs.

### 3.23 COE-23 — Termos sobrecarregados

"Protocolo" designa o protocolo de atendimento da NR 11 e o número do chamado de suporte (`SUP-AAAA-NNNNNN`). "Central"
designa a Central Ziip, a "Central de faturamento e fiscal" (§7), a "Central de notificações" (P3.36) e a "Central de
Segurança" (22-INCORPORACAO, PLT-38). "Pacote" tem oito sentidos: de vertical, de edição do GIS, de campo offline, de
reversão, pendente, por norma (ED-69 a ED-74), de evidência e de marca. "Go-live" aparece seis vezes fora do histórico,
ora como homologação local, ora como primeira produção. "Certificações de qualidade" e "certificações de qualidade"
alternam a caixa, sem prejuízo de sentido.

## 4. O que está coerente

- Coluna Dep.: nenhum ID inexistente, nenhum ciclo entre as 270 tarefas, nenhuma tarefa de fase (P0 a P3, GL) com
  entrega ED na coluna; a única dependência para fase posterior é a da ED-04 (COE-04). Nenhuma tarefa depende de ED-68,
  ED-102 ou ED-103.
- IDs sem duplicata. Lacunas de numeração só nas entregas que o plano explica (ED-02, ED-03, ED-21, ED-45 e ED-52 nas
  fases; ED-60 e ED-61 em 2029).
- Totais de esforço iguais à soma das tarefas estimadas, inclusive M12, M14 e P4.3.
- Listas de tarefas ajustadas nos cabeçalhos das versões 2.13, 2.14, 2.15, 2.17 e 2.19 iguais aos marcadores nas tarefas;
  contagens da v2.18 (12 tarefas nas fases, 21 na trilha, 127 ajustes) conferem.
- Rastreabilidade do 22-INCORPORACAO: 154 de 156 achados no plano, os outros dois sem tarefa por decisão registrada;
  nenhum achado do plano fora do relatório.
- Reservas de versões do core e de tenant com o mesmo número estão separadas pelo prefixo "core", como o §3 define;
  as versões citadas por tarefas das fases P0 a P2 têm reserva, salvo os casos do COE-15.
- Índice dos ADRs igual ao status de cada arquivo; ADR-0029 marcado como revogado em todos os lugares.
- Nomes `sigsan` remanescentes no plano estão cobertos pela regra "Nomes depois da renomeação" (§4).
- Seis tarefas sem dependência nem dependente (P0.7, P0.9, P0.25, P0.31, P1.13, P3.14) são de manutenção e não pedem
  ligação, salvo a P1.13 (COE-19).
