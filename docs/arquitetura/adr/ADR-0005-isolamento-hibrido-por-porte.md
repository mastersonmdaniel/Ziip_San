# ADR-0005 — Isolamento híbrido de tenants por porte

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** revisão 17, achados D1, D2 e D3, §5 (dimensionamento) e §6 (capacidade de projeto)

## Contexto

Hoje cada tenant tem um schema próprio no mesmo banco, e toda tabela de tenant tem `tenant_id` com RLS FORCE
(`FreshInstallTest`). O catálogo exige um schema por tenant (`schema_name ... UNIQUE`,
`shared/sigsan-core/src/main/resources/db/migration/core/V1__create_core_schema.sql:21`) e não registra em que banco o
tenant está. A aplicação tem um único pool para um único banco
(`platform/sigsan-app/src/main/java/br/com/sigsan/app/config/DataSourceConfig.java:20-27`).

A revisão 17 (D2) aponta o limite prático do modelo: 76 tabelas e 227 índices e constraints por tenant, com tendência
a dobrar. Centenas de schemas inflam o catálogo do PostgreSQL, alongam migrations (D1), autovacuum e backup. Ao
mesmo tempo, o mercado-alvo vai de autarquias com poucos milhares de ligações a prestadores com centenas de milhares.
A capacidade de projeto proposta é de ~50 tenants e 2 milhões de ligações por cluster (§6).

As constraints de unicidade das tabelas de tenant já incluem `tenant_id` (por exemplo,
`commercial/sigsan-registry/src/main/resources/db/migration/tenant/V3_1__create_registry_schema.sql:35-36`), o que
permite mais de um tenant no mesmo schema sem colisão.

## Decisão

Modelo híbrido, escolhido por porte na implantação e registrado no catálogo:

1. **Padrão: schema por tenant**, como hoje.
2. **Teto de tenants por cluster**, com **coluna de shard no catálogo** indicando o cluster de banco de cada tenant. Ao
   atingir o teto, novos tenants vão para outro cluster.
3. **Tenant grande em banco dedicado** (cluster próprio, workers e janela de lote próprios). Referência inicial da
   revisão 17 §5: acima de 200 mil ligações.
4. **Autarquias muito pequenas podem dividir schema**, isoladas por `tenant_id` e RLS, que já são obrigatórios em toda
   tabela de tenant.
5. **Teste sintético com 300 schemas** antes de fixar o teto e os limiares de porte. O teto e os limiares entram neste
   ADR como adendo de status quando medidos (ou em ADR substituto, se mudarem a decisão).

## Consequências

### Positivas

- O custo por tenant pequeno cai, e o crescimento do número de clientes não esbarra no limite de schemas de um único
  cluster.
- Tenant grande não disputa recursos com os demais (vizinho barulhento) e pode ter janela de manutenção própria.
- A RLS continua sendo a garantia de isolamento em todos os modos, inclusive no schema compartilhado.

### Negativas

- Três modos de isolamento a testar, operar e documentar.
- No schema compartilhado, exportação, restauração e eliminação de um único tenant deixam de ser operação de schema
  (`pg_dump -n`, `DROP SCHEMA`) e passam a ser por `tenant_id`, com crypto-shredding da chave do tenant.
- Roteamento de conexão por cluster aumenta o número de pools e a complexidade das migrations.
- Mudar um tenant de modo (crescimento) exige procedimento de migração entre schemas ou clusters.

### O que muda no código

- `tenant_catalog`: `schema_name` deixa de ser `UNIQUE`; entram `isolation_mode` (schema compartilhado, schema
  dedicado, banco dedicado) e a coluna de shard (cluster). Migration do core na próxima versão livre.
- Resolução de tenant devolve também o cluster; a aplicação passa a rotear pools por cluster, separados por papel
  (API, lote, ingestão), conforme D3.
- `TenantMigrator` e `DatabaseMigrator` migram por (cluster, schema), uma vez por schema compartilhado, com status por
  schema e por tenant (D1).
- Sequências sempre por `tenant_id`, nunca por schema. A matrícula já segue essa regra
  (`commercial/sigsan-registry/src/main/java/br/com/sigsan/commercial/registry/application/ConsumerUnitService.java:511-520`);
  a troca por tabela de sequência por tenant (médio da revisão 17) mantém a mesma chave.
- Provisionamento recusa schema existente salvo no modo compartilhado explícito (corrige a colisão de
  `TenantProvisioningService.java:231,465`).
- Teste de isolamento passa a cobrir dois tenants no mesmo schema e dois tenants em clusters diferentes.

### O que muda no roadmap

- Teste com 300 schemas e coluna de shard entram no P0 e P1. Roteamento multi-cluster é necessário só no primeiro
  tenant em banco dedicado ou ao atingir o teto.

## Alternativas consideradas

- **Só schema por tenant.** Rejeitada: limite prático de schemas por cluster e custo fixo alto para autarquias
  pequenas.
- **Só schema compartilhado com RLS.** Rejeitada: aumenta o raio de dano de um erro de política, dificulta
  exportação e eliminação por cliente e não resolve o vizinho barulhento para tenants grandes.
- **Banco por tenant para todos.** Rejeitada: custo e operação inviáveis para dezenas de autarquias pequenas.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Coluna de shard e `isolation_mode` no catálogo; correção da colisão de schema no provisionamento | P0 |
| Teste sintético com 300 schemas (tamanho do catálogo, tempo de migration, autovacuum, backup, latência) e registro do teto | P0–P1 |
| Migrations por (cluster, schema) com status e quarentena (D1) | P1 |
| Pools por papel e roteamento por cluster (D3) | P2 |
| Procedimento de exportação e eliminação por `tenant_id` no schema compartilhado | P3 |
| Modo schema compartilhado habilitado para a primeira autarquia muito pequena | sob demanda (P4) |
| Banco dedicado para o primeiro tenant grande | sob demanda (a partir do P3) |
