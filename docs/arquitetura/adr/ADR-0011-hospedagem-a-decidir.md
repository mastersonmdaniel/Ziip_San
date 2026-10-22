# ADR-0011 — Hospedagem de produção

- **Status:** Aceito, 12/10/2026 (editais). Proposto em 12/10/2026; aceito no mesmo dia por decisão do dono do produto,
  depois da análise dos editais (`docs/levantamento/editais/18h-aderencia-nao-funcionais-servicos-habilitacao.md` §3.3
  e §6, decisão 2). O nome do arquivo (`-a-decidir`) foi mantido para não quebrar as remissões existentes.
- **Decisor:** dono do produto
- **Afeta:** revisão 17, achados X1, X2 e O2, §5 (arquitetura-alvo) e §8.1; R1 (cofre e chaves); ADR-0010; ADR-0012;
  ADR-0024; `docs/arquitetura/ARQUITETURA-ALVO.md` §1, §4 e §7; RNF-14, RNF-32, RNF-43, RNF-44; plano P0.26 a P0.28
- **Ajuste de 12/10/2026 (v2.7), antes de qualquer implementação:** provedor, região e serviços do item 2 não mudam;
  muda o momento: a infraestrutura na AWS só é criada no lançamento comercial, depois do P4 (ADR-0033; plano, fase
  LC); IaC de homologação e produção, RDS Multi-AZ com PITR e cópia de snapshots, R1 sobre o KMS, Secrets Manager, S3
  com Object Lock, borda e observabilidade gerenciada saem do P0 e vão para a LC.2 a LC.7, precedidas de ensaio de
  implantação com dado sintético (LC.1); até lá, o R1 roda sobre um cofre local e o backup e o PITR são ensaiados no
  ambiente local, atrás das interfaces do item 4, com teste de contrato (plano P0.28, P0.45); no P0 ficam a tabela de
  custo e as conferências documentais (certificações no AWS Artifact, versões de extensões no RDS); as tarefas
  derivadas marcadas "P0" que exigem recurso na AWS leem-se "lançamento comercial"; a consequência positiva "P0.27 e
  P0.28 podem começar" e a negativa sobre o perfil `vault` ficam substituídas: o cofre local é o provedor do R1 até o
  lançamento (exceção no ADR-0010); decisão do dono do produto.

## Contexto

Só existe ambiente local (X1). O banco é nó único, sem alta disponibilidade, backup, PITR nem meta de RPO/RTO (X2). O
Terraform existente aponta para `us-east-1` e será arquivado (ADR-0010). A revisão 17 propõe RPO de 15 min e RTO de 4 h
(§6) e uma arquitetura-alvo com banco PostgreSQL 17 + PostGIS com HA e PITR, armazenamento de objetos com retenção
imutável para documentos fiscais, cofre gerenciado e chave de dados por tenant (§5).

A escolha da hospedagem define como o R1 é feito (Vault próprio ou KMS do provedor), como o backup é feito e testado e
qual IaC será escrita. Três opções foram levantadas (seção "Alternativas consideradas").

A análise dos editais VISAN Videira PE 06/2026 e SAMAE São Ludgero PE 2/2026 (`18h` §3.3) acrescentou um requisito
eliminatório: o edital de Videira desclassifica provedor de nuvem sem ISO 9001, ISO/IEC 27001, ISO/IEC 27017, SOC 1 e
SOC 3 (TR-078) e descreve serviços de catálogo de hiperescalador (backup gerenciado, banco com PITR, detecção de
ameaças, inspeção de vulnerabilidades, registro de configuração). VPS ou servidor próprio não atende a essas
certificações; a VPS do ecossistema Ziip Cloud não serve de comprovação.

## Requisitos que a escolha precisa atender

1. **Dados no Brasil**, incluindo réplicas e backups.
2. **PITR** do PostgreSQL, com restauração testada (trimestral; mensal quando automatizada).
3. **RPO de 15 min e RTO de 4 h** no início, com caminho para 5 min e 1 h.
4. **KMS ou cofre** para a chave mestra (KEK), chave de dados por tenant, rotação, backup da KEK e crypto-shredding no
   encerramento de contrato.
5. **Armazenamento de objetos com retenção imutável** para documentos fiscais e de auditoria.
6. PostgreSQL 17 com PostGIS 3.5, `pg_trgm`, `unaccent` e `pg_partman` disponíveis.
7. Ambientes separados de homologação e produção; capacidade de projeto de ~50 tenants e 2 milhões de ligações por
   cluster (§6), com caminho para banco dedicado por tenant grande (ADR-0005).
8. Custo compatível com o preço por ligação dos clientes-alvo.
9. **Certificações do provedor exigidas em edital** (incluído em 12/10/2026): ISO 9001, ISO/IEC 27001, ISO/IEC 27017,
   SOC 1 e SOC 3, com escopo que alcance a região usada (verificado em 22/10/2026: SOC 1 e SOC 3 não são
   certificações, e sim relatórios de asseguração emitidos por auditor independente; o SOC 1 trata de controles de
   relatório financeiro e o SOC 3 pertence à família SOC 2; confirmar com o órgão o que a exigência abrange).

## Decisão

1. **Provedor e região:** AWS, região `sa-east-1` (São Paulo), com serviços gerenciados. Nenhum dado de tenant,
   réplica ou backup sai da região.
2. **Serviços adotados:**
   - **Banco:** Amazon RDS for PostgreSQL 17 em Multi-AZ (standby síncrono em outra zona), com réplica de leitura para
     relatórios, SINISA e BI (ADR-0027); backup automático com PITR; cópia de snapshots para outra conta AWS na mesma
     região.
   - **Chaves:** AWS KMS para a KEK; DEK por tenant gerada pelo KMS e guardada cifrada, atrás do `TenantKeyProvider`
     (R1); rotação anual; crypto-shredding pela destruição da DEK do tenant (RNF-43).
   - **Segredos:** AWS Secrets Manager para credenciais de execução e de banco, com rotação.
   - **Objetos:** Amazon S3 com Object Lock para documentos fiscais e de auditoria (ADR-0022), prefixo e chave por
     tenant.
   - **Demais componentes:** a coluna AWS do mapeamento da `ARQUITETURA-ALVO.md` §7 (CloudFront e AWS WAF, ALB, ECS
     Fargate, ECR, CloudWatch ou serviço gerenciado de Prometheus/Grafana) é a referência; cada item é confirmado na IaC
     da P0.27.
3. **Certificações:** as certificações do requisito 9 são conferidas no portal de conformidade da AWS (AWS Artifact) a
   cada proposta, com o escopo que inclua `sa-east-1`, e o relatório entra no kit de habilitação (ADR-0023).
4. **Neutralidade no código continua:** PostgreSQL padrão com as extensões do requisito 6, objetos pela API S3, chaves
   atrás do `TenantKeyProvider`, telemetria por OpenTelemetry e imagens de contêiner sem dependência de serviço
   proprietário. A neutralidade passa a servir à cláusula de reversão do ADR-0024 (execução na infraestrutura do
   órgão), e não mais à escolha de provedor. A IaC e a operação podem usar serviços da AWS.
5. **Pooler:** PgBouncer em modo transaction, como na `ARQUITETURA-ALVO.md` §4 e §7. O Amazon RDS Proxy só entra depois
   de teste de pinning (seção "Alerta: RDS Proxy e contexto de sessão").

## Alerta: RDS Proxy e contexto de sessão

O RDS Proxy multiplexa conexões de cliente sobre conexões do banco e as fixa (pinning) quando detecta estado de sessão:
`SET` e `set_config` de escopo de sessão, prepared statements do driver JDBC, tabelas temporárias, cursores e travas
consultivas, entre outros (conferir a lista na documentação vigente do serviço). Conexão fixada deixa de ser
compartilhada, e o proxy pode esgotar as conexões do banco sem ganho de multiplexação.

Hoje o contexto de tenant é aplicado com escopo de sessão (`MultiTenantConnectionProvider.java:39-47`). Com qualquer
pooler em modo transaction, isso é também um risco de isolamento: um contexto de sessão não limpo pode ser herdado por
outra requisição. Por isso:

- o contexto passa a ser local à transação (`set_config(..., true)`, `ARQUITETURA-ALVO.md` §4) antes de qualquer pooler
  em produção;
- o RDS Proxy só é avaliado com teste de carga que meça a métrica de sessões fixadas
  (`DatabaseConnectionsCurrentlySessionPinned`) e prove que o contexto de um tenant nunca aparece em outro;
- até lá, o pooler é o PgBouncer.

## O que esta decisão desbloqueia

- **R1** (cofre e chaves por tenant) sobre o KMS, a partir do contrato `TenantKeyProviderContract`, já neutro.
- **Backup e PITR** do banco e o primeiro ensaio de restauração com tempo medido (RNF-14 a RNF-16).
- **IaC** de homologação e produção (ferramenta escolhida na P0.27, sem segredos no state), e o destino de implantação
  do CI.
- Borda (CDN, WAF, TLS) e armazenamento de objetos com retenção imutável.
- Serviços de segurança do provedor citados no edital de Videira (detecção de ameaças, inspeção de vulnerabilidades,
  registro de configuração), a habilitar conforme o custo.

## Consequências

### Positivas

- Atende às cinco certificações e aos serviços de catálogo do edital de Videira sem a ressalva de backup
  geo-redundante fora do país (`18h` §3.3).
- HA, backup, PITR, cofre e chaves passam a ser serviços gerenciados, o que reduz a operação para uma equipe pequena.
- O plano P0 deixa de esperar a decisão: P0.27 e P0.28 podem começar.

### Negativas

- Uma só região no país. Desastre que alcance toda a região `sa-east-1` não é coberto pelo RPO e pelo RTO do RNF-14 e
  do RNF-15; recuperação fora do Brasil exigiria rever o requisito 1 e fica fora desta decisão.
- Custo: o dimensionamento inicial da `ARQUITETURA-ALVO.md` §6 fica, em estimativa grosseira do `18h` §3.3, na ordem de
  US$ 2 mil por mês, mais que a mensalidade de referência inteira do SAMAE (R$ 7.636,74). O modelo só fecha com vários
  tenants por cluster ou dimensionamento menor nos primeiros clientes; a tabela de custo continua devida.
- O backup automático do RDS guarda no máximo 35 dias, compatível com o RNF-43; snapshots manuais e cópias em outra
  conta não expiram sozinhos e precisam de política própria de expiração.
- O KMS não exporta a KEK. O requisito "backup da KEK" (RNF-32) passa a depender da durabilidade do serviço e da proteção
  contra exclusão (período de espera e política de chave); a forma de atendê-lo é decidida na P0.28.
- O perfil `vault` do ADR-0010 deixa de ser caminho de produção; o uso no desenvolvimento local é revisto na tarefa do
  R1.
- Dependência de fornecedor na operação (IaC, monitoramento, chaves). O código permanece neutro (item 4 da decisão).

### O que muda no código

- `TenantKeyProvider` com implementação sobre o KMS e teste de contrato verde contra ele.
- Contexto de tenant com escopo de transação antes da produção (já previsto na `ARQUITETURA-ALVO.md` §4).
- IaC nova em diretório próprio; o `infra/terraform` arquivado (ADR-0010) não é reaproveitado.

### O que muda no roadmap

- P0.26 concluída por este ADR. P0.27 (IaC, homologação e produção) e P0.28 (HA, backup, PITR e R1) passam a ter
  destino definido.

## Alternativas consideradas

Opções levantadas em 12/10/2026, mantidas como histórico:

| Opção | Banco | Chaves e segredos | Objetos | Observações |
|---|---|---|---|---|
| **AWS `sa-east-1` gerenciado** (escolhida) | PostgreSQL gerenciado multi-AZ com PITR | KMS e Secrets Manager | S3 com Object Lock | Uma região no Brasil; recuperação de desastre fora do país exigiria avaliar a residência dos dados |
| **Azure Brasil, duas regiões** | PostgreSQL Flexible Server com HA por zona e réplica entre regiões | Key Vault | Blob com política de imutabilidade | Duas regiões no país (Sul e Sudeste); conferir a disponibilidade dos serviços na região secundária |
| **VPS ou servidor próprio** | Patroni (HA) e pgBackRest (backup, PITR) operados pela equipe | Vault próprio, fora do modo dev, com TLS e backup da chave mestra | Armazenamento compatível com S3 com retenção imutável, próprio ou contratado | Menor custo direto; HA, backup, atualização e plantão ficam com a equipe |

- **Azure Brasil.** Rejeitada: atende às certificações, mas o backup geo-redundante do Flexible Server pareia fora do
  Brasil (`ARQUITETURA-ALVO.md` §7), o que obriga a restringir o backup a redundância local ou por zona.
- **VPS ou servidor próprio.** Rejeitada: sem SOC 1 e SOC 3 na maioria dos provedores, o que elimina em editais do tipo
  Videira (TR-078), e HA, backup, PITR e plantão ficariam com a equipe. A coluna correspondente da
  `ARQUITETURA-ALVO.md` §7 continua útil como referência do pacote de reversão do ADR-0024.
- **Google Cloud ou OCI.** Não avaliadas: têm região no Brasil, mas exigiriam conferir item a item as certificações,
  em especial a ISO 9001 (`18h` §3.3).

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Tabela de custo mensal na AWS para a capacidade do §6 e para o cliente piloto, com dimensionamento mínimo para os primeiros tenants | P0 |
| Prova de restauração PITR no RDS, medindo RPO e RTO | P0 |
| Decisão registrada (status Aceito) | P0, cumprida em 12/10/2026 |
| IaC de homologação e produção na AWS `sa-east-1`, sem segredos no state; cópia de snapshots em outra conta na mesma região, com política de expiração | P0 |
| R1 sobre o KMS; forma de atender ao backup da KEK | P0 |
| Contexto de tenant local à transação; PgBouncer em modo transaction | P0 |
| Teste de pinning e de vazamento de contexto antes de qualquer uso do RDS Proxy | Sob demanda (antes de adotar o RDS Proxy) |
| Conferência das certificações no AWS Artifact e relatório no kit de habilitação (ADR-0023) | P0, repetida a cada proposta |
| Conferência das versões de PostGIS, `pg_trgm`, `unaccent` e `pg_partman` disponíveis no RDS for PostgreSQL 17 | P0 |
