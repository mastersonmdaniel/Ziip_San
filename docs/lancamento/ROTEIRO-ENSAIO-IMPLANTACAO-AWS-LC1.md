# LC.1 — Roteiro do ensaio de implantação na AWS com dado sintético

- **Pacote:** LC.1 — "Ensaio de implantação na AWS com dado sintético"
- **Produtor:** sessão `a1981a33-6f88-49c6-8f6f-f9d70266f27b` (papel: Produtor)
- **Bloco:** conclusão do ZiipSan, 09/11/2026 — pacote de **preparação**. A execução
  do ensaio é externa (exige conta AWS) e **não foi executada nem simulada** por
  este pacote.
- **Origem:** ADR-0033; ADR-0011. **Dependências (fonte):** P0.45, P0.27, GL.4, ED-01.
- **Fonte única do aceite:** `docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json`,
  registro `LC.1` (lido na íntegra pelo produtor).

## Aceite decomposto (itens A1–A11 — referência de cobertura)

Texto do aceite (fonte, resumo fiel com os qualificadores preservados):
"começa com todas as fases (P0 a P3, GL e P4) e as ondas A, B (grupos B1 e B2) e C
da trilha concluídas, exceto a seção de pacotes pendentes e sob demanda (P4.6,
ED-68, ED-102, ED-103 e ED-104), que não entra no critério nem bloqueia o início;
aceite da homologação local (GL.4) e prontidão de POC (ED-01) concluídos; o grupo
B2 conta aqui, embora fique fora da prontidão de POC (PD-01); a instância
dedicada (LC.15) fica fora do caminho; conta de ensaio em `sa-east-1`; IaC
aplicada do zero; suíte de contrato das interfaces da P0.45 verde contra KMS,
Secrets Manager, S3 com Object Lock e o serviço de e-mail; testes de fumaça e
carga de referência curta comparados com a linha de base do ambiente local da
P0.27; versões de PostGIS, `pg_trgm`, `unaccent` e `pg_partman` conferidas no
RDS; diferenças entre local e AWS registradas, cada uma com correção ou tarefa;
ambiente destruído ao fim, sem segredo nem dado remanescente".

| Item | Conteúdo |
|---|---|
| A1 | Fases P0–P3, GL, P4 e ondas A, B (B1 **e B2**) e C concluídas — **exceto** a seção de pendentes/sob demanda (P4.6, ED-68, ED-102, ED-103, ED-104), que não entra no critério nem bloqueia |
| A2 | Aceite da homologação local (GL.4) concluído |
| A3 | Prontidão de POC (ED-01) concluída |
| A4 | Qualificadores: B2 conta na LC.1 embora fora da prontidão de POC (PD-01); LC.15 (instância dedicada) fora do caminho |
| A5 | Conta de ensaio em `sa-east-1` |
| A6 | IaC aplicada do zero |
| A7 | Suíte de contrato das interfaces da P0.45 **verde** contra KMS, Secrets Manager, S3 com Object Lock e o serviço de e-mail |
| A8 | Testes de fumaça e carga de referência curta **comparados** com a linha de base do ambiente local da P0.27 |
| A9 | Versões de PostGIS, `pg_trgm`, `unaccent` e `pg_partman` conferidas no RDS |
| A10 | Diferenças local × AWS registradas, **cada uma com correção ou tarefa** |
| A11 | Ambiente destruído ao fim, **sem segredo nem dado remanescente** |
| A0 | (do título) Ensaio **só com dado sintético** — nenhum dado real/pessoal em nenhuma fase do ensaio |

> Valores e limiares que o aceite não fixa estão marcados **[A DEFINIR]** e
> listados como pendências no RELATORIO-LC.1. Nenhum número deste roteiro é
> resultado de execução: o ensaio ainda não rodou.

## Regras do ensaio (valem para todos os passos)

1. **Só dado sintético** (A0): massa sintética com a marca de sintético; a
   varredura que recusa dado pessoal real (da P0.45) roda antes da carga e
   depois dela.
2. **Região única:** todos os recursos em `sa-east-1`; nenhum backup, réplica
   ou snapshot fora da região (ADR-0011, item 1 da decisão).
3. **Nenhum segredo no repositório nem no state em claro:** segredos só via
   Secrets Manager; o state da IaC sem segredo (critério herdado da LC.2).
4. **Parada dura por reprovação:** passo reprovado interrompe o ensaio; o
   ambiente vai direto à Fase 6 (destruição), e a reprovação entra no registo
   de diferenças (Fase 5) com correção ou tarefa (A10).
5. Quem executa ≠ quem aprova: cada passo tem executor e conferente nomeados
   no registo do ensaio (nomes/ papéis: **[A DEFINIR]** — pendência P-LC1-06).

---

## FASE 0 — Pré-condições (nenhum recurso de nuvem é criado nesta fase)

### Passo 0.1 — Conferência do critério de conclusão das fases e ondas [ACEITE A1, A4]
- **Ação:** conferir, tarefa a tarefa, o estado de conclusão de P0, P1, P2, P3,
  GL, P4 e das ondas A, B1, **B2** e C, **excluindo** do critério P4.6, ED-68,
  ED-102, ED-103 e ED-104 (não bloqueiam) e a LC.15 (fora do caminho, A4).
- **Evidência esperada:** checklist de conclusão assinado, com a fonte de cada
  estado (pacote local + pouso) e a lista explícita das exclusões aplicadas.
- **Aprova:** todas as tarefas do critério constam concluídas, com evidência.
- **Reprova:** qualquer tarefa do critério sem evidência de conclusão →
  ensaio não começa; a tarefa vira pendência nomeada do ensaio.

### Passo 0.2 — Aceite da GL.4 juntado ao dossiê do ensaio [ACEITE A2]
- **Ação:** anexar a decisão formal da GL.4 (dono do produto e piloto — no
  modelo vigente, decisão do dono; ver PREREQUISITOS-LC1.md) e o relatório do
  paralelo da GL.2 + ensaio da GL.3 que a fundamentam.
- **Evidência esperada:** documento de decisão da GL.4 datado e identificado.
- **Aprova:** decisão formal presente e favorável. **Reprova:** dossiê sem
  decisão formal (dossiê produzido ≠ aceite concluído) → ensaio não começa.

### Passo 0.3 — Prontidão de POC (ED-01) juntada ao dossiê [ACEITE A3, A4]
- **Ação:** anexar a verificação de prontidão da ED-01 (PD-01: onda A, grupo
  B1 e P4 — **sem** B2; o B2 é exigido pelo passo 0.1, não por este).
- **Evidência esperada:** registo de prontidão da ED-01 com veredito.
- **Aprova:** prontidão concluída. **Reprova:** verificação em produção ou
  veredito pendente → ensaio não começa.

### Passo 0.4 — Conta de ensaio provisionada e delimitada [ACEITE A5]
- **Ação:** confirmar a conta AWS **de ensaio** (separada de qualquer conta de
  produção futura), região padrão `sa-east-1`, com: responsável nomeado,
  teto/orçamento de custo do ensaio **[A DEFINIR]**, alerta de custo ativo,
  MFA na raiz e acesso por papéis (nenhuma chave de longa duração distribuída).
- **Evidência esperada:** identificação da conta (id mascarado no registo),
  captura da configuração de orçamento/alerta e da região.
- **Aprova:** conta dedicada ao ensaio, em `sa-east-1`, com alerta de custo.
- **Reprova:** conta partilhada com outro fim, sem alerta de custo, ou região
  diferente → ensaio não começa. **(Pendência externa central P-LC1-01: a
  criação/autorização e o custo da conta são do dono/projeto.)**

### Passo 0.5 — IaC do ensaio identificada e revista [ACEITE A6]
- **Ação:** identificar **qual** IaC será aplicada do zero. Nota de desenho: a
  IaC definitiva de ambientes é a LC.2, que **depende** da LC.1 — logo a LC.1
  não pode esperar pela LC.2. O ensaio usa a IaC mínima do ensaio
  **[A DEFINIR — pendência P-LC1-02: autor e recorte da IaC do ensaio
  (rede, RDS, KMS, Secrets Manager, S3, serviço de e-mail, runtime da
  aplicação) ou decisão expressa de antecipar o recorte da LC.2]**.
  Conferir antes: nenhum segredo no código nem no state; região fixa
  `sa-east-1`; tags de identificação do ensaio em todos os recursos
  (nome da tag **[A DEFINIR]**) — são elas que a Fase 6 usa para provar a
  destruição completa.
- **Evidência esperada:** referência da IaC (repositório/pasta e revisão),
  saída da validação estática da IaC e do plano (`plan`) em ambiente vazio.
- **Aprova:** plano gerado contra conta/ambiente vazios, sem segredo exposto.
- **Reprova:** plano exige recurso pré-existente não declarado, ou expõe
  segredo → corrigir a IaC antes de aplicar (diferença registada, A10).

### Passo 0.6 — Linha de base local da P0.27 congelada [ACEITE A8]
- **Ação:** obter a linha de base do ambiente local da P0.27: resultados dos
  testes de fumaça e da carga de referência curta **no ambiente local**
  (perfil de homologação local), com massa sintética, versão da imagem e
  configuração registadas. Se a linha de base numérica não existir registada,
  ela é **produzida localmente antes do ensaio** (execução local, fora da
  nuvem) e arquivada — estado atual: **[SEM FONTE] numérica local**
  (pendência P-LC1-04; o documento do ambiente local existe — ver
  PREREQUISITOS-LC1.md — mas sem placar de fumaça/carga arquivado).
  Definir antes: roteiro de fumaça (casos **[A DEFINIR]**), perfil da carga
  curta (duração, concorrência e mistura **[A DEFINIR]**) e tolerâncias de
  comparação (**[A DEFINIR]** — o aceite manda comparar, não fixa desvio
  aceitável).
- **Evidência esperada:** ficheiro de linha de base datado (métricas brutas +
  ambiente + versão), reutilizável na Fase 3 sem nova execução local.
- **Aprova:** linha de base arquivada e íntegra. **Reprova:** sem linha de
  base, a Fase 3 não tem contra o que comparar → ensaio não começa.

### Passo 0.7 — Suíte de contrato da P0.45 pronta para alvo AWS [ACEITE A7]
- **Ação:** confirmar que a suíte de contrato das interfaces da P0.45
  (armazenamento de objetos, chaves `TenantKeyProvider`, segredos e e-mail —
  recorte do aceite da LC.1; a interface de fila/outbox não entra neste
  recorte) roda por configuração de alvo (local × AWS) sem alteração de
  código, e que está verde contra a implementação local (pré-condição de
  sanidade antes de gastar nuvem).
- **Evidência esperada:** placar da suíte contra o alvo local + registo da
  configuração que apontará para a AWS (endpoints/serviços por variável).
- **Aprova:** verde no local e alvo trocável por configuração.
- **Reprova:** suíte acoplada ao local ou vermelha no local → corrigir antes
  (a falha é local, não é diferença local × AWS).

---

## FASE 1 — IaC aplicada do zero [ACEITE A6, A5]

### Passo 1.1 — Aplicação do zero
- **Ação:** aplicar a IaC do passo 0.5 na conta de ensaio vazia, do zero (sem
  importar recurso manual), incluindo: rede, RDS for PostgreSQL, KMS,
  Secrets Manager, S3, serviço de e-mail e o runtime da aplicação com a
  **mesma imagem** usada no ambiente local (P0.5/P0.27). Rodar o job
  `migrate` como na implantação local.
- **Evidência esperada:** log completo da aplicação (recursos criados),
  inventário resultante (lista de recursos com as tags do ensaio) e tempo
  decorrido medido.
- **Aprova:** aplicação termina sem erro e o inventário = plano aprovado no
  passo 0.5 (sem recurso a mais nem a menos).
- **Reprova:** erro de aplicação ou divergência plano × inventário →
  registar (A10), destruir (Fase 6) e só repetir após correção.

### Passo 1.2 — Verificação de residência e de segredos no state
- **Ação:** conferir que 100% dos recursos estão em `sa-east-1` e varrer o
  state e os artefactos gerados à procura de segredo em claro.
- **Evidência esperada:** relatório de regiões por recurso + resultado da
  varredura de segredos (ferramenta/método **[A DEFINIR]**).
- **Aprova:** tudo em `sa-east-1`, zero segredo em claro.
- **Reprova:** qualquer recurso fora da região ou segredo exposto → falha
  grave: destruir (Fase 6) e tratar como diferença com tarefa (A10).

### Passo 1.3 — Carga da massa sintética [ACEITE A0]
- **Ação:** carregar **só** massa sintética (A0) — tenant de ensaio com a
  marca de sintético — pelos mesmos importadores do ambiente local; rodar a
  varredura de dado pessoal real antes e depois.
- **Evidência esperada:** registo da carga (volumes por entidade) + varredura
  verde nos dois momentos.
- **Aprova:** carga completa e varredura verde. **Reprova:** qualquer dado
  sem marca de sintético → parar, destruir (Fase 6), registar (A10).

---

## FASE 2 — Suíte de contrato da P0.45 contra a AWS [ACEITE A7]

> A mesma suíte do passo 0.7, agora com o alvo apontado para a AWS. Cada
> interface tem passo próprio: reprovação de uma não é escondida pelas outras.

### Passo 2.1 — Chaves contra o AWS KMS
- **Ação:** rodar a suíte de contrato de chaves (`TenantKeyProvider`) contra o
  KMS: criar/usar KEK, gerar DEK por tenant, cifrar/decifrar, rotação e
  destruição da DEK (crypto-shredding) conforme o contrato local.
- **Evidência esperada:** placar da suíte (passou/falhou por caso) + log.
- **Aprova:** suíte **verde** integral. **Reprova:** qualquer caso falho →
  diferença registada com correção ou tarefa (A10); o ensaio pode prosseguir
  só se a falha não for de isolamento/segurança — critério **[A DEFINIR]**
  (pendência P-LC1-05); falha de isolamento/segurança = parada dura.

### Passo 2.2 — Segredos contra o AWS Secrets Manager
- **Ação:** rodar a suíte de contrato de segredos contra o Secrets Manager:
  gravar, ler, rodar (rotação) e apagar segredo de ensaio; confirmar que a
  aplicação lê as credenciais de execução e de banco do cofre, sem segredo
  em variável de ambiente em claro nem no repositório.
- **Evidência esperada:** placar da suíte + evidência da origem das
  credenciais na aplicação em execução.
- **Aprova:** suíte verde. **Reprova:** idem passo 2.1.

### Passo 2.3 — Objetos contra o S3 com Object Lock
- **Ação:** rodar a suíte de contrato de armazenamento contra o S3 com
  Object Lock ativo: gravar objeto, ler, tentar sobrescrever e tentar apagar
  dentro da retenção (ambas as tentativas têm de ser **recusadas** pelo
  provedor), com prefixo e chave por tenant.
- **Evidência esperada:** placar da suíte + registo das duas recusas
  (sobrescrita e exclusão) com a resposta do serviço.
- **Aprova:** suíte verde **e** as duas recusas comprovadas.
- **Reprova:** sobrescrita ou exclusão aceite = falha grave (retenção
  imutável é requisito de documentos fiscais, ADR-0011/ADR-0022) → parada
  dura, registar (A10), destruir (Fase 6).

### Passo 2.4 — E-mail contra o serviço de e-mail da AWS
- **Ação:** rodar a suíte de contrato de e-mail contra o serviço de e-mail
  definido na IaC (Amazon SES ou equivalente — **[A DEFINIR]** na IaC do
  ensaio, pendência P-LC1-02): enviar para destinatário de ensaio controlado,
  confirmar a gravação/registo do envio e o comportamento de falha. Nenhum
  envio para destinatário real fora do ensaio.
- **Evidência esperada:** placar da suíte + registo do envio de ensaio
  (identificador da mensagem no provedor).
- **Aprova:** suíte verde e envio de ensaio comprovado.
- **Reprova:** idem passo 2.1.

### Passo 2.5 — Fronteira de provedor (conferência complementar)
- **Ação:** repetir, contra o código implantado, o teste de fronteira da
  P0.45: nenhum módulo de domínio depende de SDK de provedor.
- **Evidência esperada:** resultado do teste de fronteira no pipeline do
  ensaio. **Aprova:** verde. **Reprova:** dependência encontrada vira
  diferença com tarefa (A10) — não é contornada com exceção manual.

---

## FASE 3 — Fumaça e carga de referência curta × linha de base [ACEITE A8]

### Passo 3.1 — Testes de fumaça na AWS
- **Ação:** executar o mesmo roteiro de fumaça da linha de base (passo 0.6)
  contra o ambiente AWS: arranque da aplicação, autenticação, fluxos
  essenciais definidos no roteiro **[A DEFINIR — lista de casos]**.
- **Evidência esperada:** placar de fumaça AWS lado a lado com o local.
- **Aprova:** mesmo resultado funcional do local (casos que passam lá passam
  cá). **Reprova:** divergência funcional = diferença registada (A10) com
  causa classificada (ambiente × código) antes de prosseguir.

### Passo 3.2 — Carga de referência curta na AWS
- **Ação:** executar a carga curta com o mesmo perfil, a mesma massa
  sintética e a mesma mistura da linha de base local (passo 0.6).
- **Evidência esperada:** métricas brutas da AWS (tempos, vazão, erros) no
  mesmo formato da linha de base.
- **Aprova:** execução completa, sem erro de infraestrutura, métricas
  recolhidas. **Reprova:** carga interrompida por falha do ambiente →
  registar (A10) e repetir uma vez após correção; segunda falha = ensaio
  reprovado neste item.

### Passo 3.3 — Comparação com a linha de base da P0.27
- **Ação:** produzir a tabela comparativa local × AWS, métrica a métrica,
  com o desvio calculado. O aceite manda **comparar e registar**; não fixa
  tolerância — desvios além de **[A DEFINIR]** (pendência P-LC1-03) viram
  diferença com correção ou tarefa (A10); desvios dentro da tolerância são
  registados como diferença conhecida sem tarefa, com justificativa.
- **Evidência esperada:** tabela comparativa assinada pelo executor e pelo
  conferente, anexa ao registo do ensaio.
- **Aprova:** tabela completa (nenhuma métrica da linha de base sem par na
  AWS). **Reprova:** métrica sem comparação = item A8 não cumprido.

---

## FASE 4 — Extensões e versões no RDS [ACEITE A9]

### Passo 4.1 — Conferência de versões no RDS
- **Ação:** no RDS criado pela IaC, executar a conferência de extensões e
  registar a versão instalada de cada uma, ao lado da versão do ambiente
  local (da linha de base, passo 0.6) e da versão esperada:
  - PostgreSQL 17 (ADR-0011, requisito 6);
  - PostGIS — esperado **3.5** (ADR-0011 req. 6 / ADR-0033 item 1);
  - `pg_trgm` — versão esperada **[A DEFINIR]** (o ADR exige disponibilidade);
  - `unaccent` — versão esperada **[A DEFINIR]**;
  - `pg_partman` — versão esperada **[A DEFINIR]** (extensão externa ao
    núcleo: confirmar também o mecanismo de instalação/suporte no RDS).
  Consultas: `SELECT version();` e
  `SELECT extname, extversion FROM pg_extension WHERE extname IN
  ('postgis','pg_trgm','unaccent','pg_partman');` (ou equivalente registado).
- **Evidência esperada:** saída integral das consultas (ficheiro de texto)
  com data/hora e identificação da instância de ensaio.
- **Aprova:** as quatro extensões presentes, com versão registada; versão
  diferente da esperada não reprova por si — vira diferença (A10) com
  avaliação de impacto e correção ou tarefa.
- **Reprova:** extensão **ausente** no RDS = item A9 reprovado; registar
  (A10) com tarefa; o ensaio não é dado por concluído sem as quatro.

---

## FASE 5 — Registo de diferenças local × AWS [ACEITE A10]

### Passo 5.1 — Consolidação do registo de diferenças
- **Ação:** consolidar **todas** as diferenças observadas nas Fases 1–4 (e
  nas reprovações da Fase 0, se o ensaio tiver sido repetido) no modelo
  `MODELO-REGISTRO-DIFERENCAS-LC1.md`: descrição, fase/passo, evidência,
  impacto, e **correção aplicada ou tarefa aberta** (identificador da
  tarefa) — nenhuma linha sem um dos dois.
- **Evidência esperada:** registo completo, revisto pelo conferente.
- **Aprova:** 100% das diferenças com correção ou tarefa; zero diferença
  "só anotada". **Reprova:** qualquer diferença sem destino = A10 não
  cumprido, mesmo com todo o resto verde.

### Passo 5.2 — Veredito do ensaio
- **Ação:** lavrar o veredito: para cada item A1–A11, "cumprido / não
  cumprido" com a evidência correspondente (matriz em
  `MATRIZ-EVIDENCIAS-LC1.md`). Veredito parcial é veredito: item sem
  evidência consta **não cumprido**, sem arredondamento.
- **Evidência esperada:** termo de veredito assinado (executor + conferente).
- **Aprova:** termo lavrado. (A aprovação do ensaio = todos os itens
  cumpridos; a Fase 6 é obrigatória em qualquer veredito.)
- **Reprova:** termo sem correspondência item ↔ evidência, ou item marcado
  cumprido sem evidência arquivada → o veredito volta ao passo 5.1 até a
  matriz estar completa e honesta.

---

## FASE 6 — Destruição e verificação de remanescentes [ACEITE A11]

### Passo 6.1 — Destruição do ambiente
- **Ação:** destruir o ambiente pela IaC (mesma ferramenta que criou),
  incluindo: instâncias e snapshots do ensaio, buckets e objetos, chaves
  KMS do ensaio (agendar destruição das chaves criadas), segredos do
  Secrets Manager (apagar com remoção imediata quando o serviço o permitir;
  quando houver janela de recuperação, registar a data final), identidades
  e filas do serviço de e-mail do ensaio, papéis/usuários criados para o
  ensaio e o state do ensaio (tratado conforme a regra 3: sem segredo).
  Ordem e comandos: **[A DEFINIR]** na IaC do ensaio (P-LC1-02).
- **Evidência esperada:** log completo da destruição + inventário pós-
  destruição vazio para as tags do ensaio.
- **Aprova:** destruição termina sem erro. **Reprova:** erro ou recurso
  órfão → remoção manual registada e repetição do passo 6.2.

### Passo 6.2 — Verificação de remanescentes (prova do A11)
- **Ação:** varredura independente da conta de ensaio, **fora** da IaC
  (console/CLI com perfil de leitura), à procura de: recursos com as tags
  do ensaio em qualquer serviço usado; snapshots/backups do ensaio;
  segredos em janela de recuperação (listados com a data de expiração —
  enquanto existirem, o item consta "em extinção" com data, não
  "destruído"); chaves KMS pendentes de destruição (idem). Nenhum dado do
  ensaio pode restar fora dessas janelas nomeadas.
- **Evidência esperada:** relatório de varredura com resultado por serviço
  e, para cada janela de recuperação, o identificador e a data final.
- **Aprova:** zero recurso ativo; janelas de recuperação listadas com data
  (verificação final de extinção agendada e nomeada — responsável
  **[A DEFINIR]**, P-LC1-06).
- **Reprova:** recurso ativo remanescente → destruir e repetir a varredura;
  o ensaio **não fecha** com remanescente ativo.

### Passo 6.3 — Fecho do registo e custo
- **Ação:** fechar o registo do ensaio: custo total apurado da conta de
  ensaio (relatório de faturação do período), veredito (passo 5.2),
  registo de diferenças (passo 5.1) e evidências arquivadas no dossiê.
- **Evidência esperada:** dossiê completo indexado pela matriz de
  evidências. **Aprova:** dossiê completo. **Reprova:** evidência em falta
  = item correspondente volta a "não cumprido" no veredito.

---

## O que este roteiro NÃO faz

- Não executa nem simula o ensaio (sem conta AWS neste bloco — P-LC1-01).
- Não substitui a IaC definitiva da LC.2, o RDS/PITR da LC.3, o KMS/Secrets
  de produção da LC.4 nem o S3 de produção da LC.5: a LC.1 é um **ensaio
  destruído ao fim**, não a implantação.
- Não usa dado real em hipótese alguma (A0); dado real só entra nas trilhas
  próprias (GL/LC.10+), fora deste roteiro.
