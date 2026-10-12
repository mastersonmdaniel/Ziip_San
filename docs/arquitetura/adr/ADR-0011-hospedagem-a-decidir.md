# ADR-0011 — Hospedagem de produção

- **Status:** Proposto (pendente de decisão), 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** revisão 17, achados X1, X2 e O2, §5 (arquitetura-alvo) e §8.1; R1 (cofre e chaves)

## Contexto

Só existe ambiente local (X1). O banco é nó único, sem alta disponibilidade, backup, PITR nem meta de RPO/RTO (X2). O
Terraform existente aponta para `us-east-1` e será arquivado (ADR-0010). A revisão 17 propõe RPO de 15 min e RTO de 4 h
(§6) e uma arquitetura-alvo com banco PostgreSQL 17 + PostGIS com HA e PITR, armazenamento de objetos com retenção
imutável para documentos fiscais, cofre gerenciado e chave de dados por tenant (§5).

A escolha da hospedagem define como o R1 é feito (Vault próprio ou KMS do provedor), como o backup é feito e testado e
qual IaC será escrita. A decisão ainda não foi tomada.

## Opções levantadas

| Opção | Banco | Chaves e segredos | Objetos | Observações |
|---|---|---|---|---|
| **AWS `sa-east-1` gerenciado** | PostgreSQL gerenciado multi-AZ com PITR | KMS e Secrets Manager | S3 com Object Lock | Uma região no Brasil; recuperação de desastre fora do país exigiria avaliar a residência dos dados |
| **Azure Brasil, duas regiões** | PostgreSQL Flexible Server com HA por zona e réplica entre regiões | Key Vault | Blob com política de imutabilidade | Duas regiões no país (Sul e Sudeste); conferir a disponibilidade dos serviços na região secundária |
| **VPS ou servidor próprio** | Patroni (HA) e pgBackRest (backup, PITR) operados pela equipe | Vault próprio, fora do modo dev, com TLS e backup da chave mestra | Armazenamento compatível com S3 com retenção imutável, próprio ou contratado | Menor custo direto; HA, backup, atualização e plantão ficam com a equipe |

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

## O que fica bloqueado até decidir

- **R1** (cofre e chaves por tenant): Vault próprio ou KMS do provedor.
- **Backup e PITR** do banco e teste de restauração.
- **IaC** de homologação e produção, e o destino de implantação do CI.
- Borda (CDN, WAF, TLS) e armazenamento de objetos.

O desenvolvimento do R1 pode avançar até o contrato `TenantKeyProviderContract`, que já é neutro quanto ao provedor.

## Diretriz enquanto não houver decisão

O plano permanece **neutro quanto a provedor**: PostgreSQL padrão com extensões comuns às três opções, objetos pela
API S3, chaves atrás de `TenantKeyProvider`, telemetria por OpenTelemetry, imagens de container sem dependência de
serviço proprietário. Nenhum código novo depende de API específica de um provedor.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Tabela comparativa com custo mensal estimado para a capacidade do §6 e para o cliente piloto | P0 |
| Prova de restauração PITR na opção preferida, medindo RPO e RTO | P0 |
| Decisão registrada em ADR que substitui este (status Aceito) | P0 |
| IaC de homologação e produção na hospedagem escolhida, sem segredos no state | P0 |
| R1 sobre o KMS ou cofre escolhido | P0 |
