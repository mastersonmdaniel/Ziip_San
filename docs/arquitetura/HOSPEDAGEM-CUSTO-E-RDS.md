# P0.26 — Tabela de custo de hospedagem e conferência de extensões no RDS

Decisão de hospedagem: ADR-0011 (aceito 12/10/2026): AWS, região `sa-east-1`,
serviços gerenciados, implantada só no lançamento comercial (ADR-0033).
Nenhum recurso na AWS é provisionado no P0. O custo abaixo é contado a
partir do lançamento (data de referência: outubro de 2028, opção A do §2.4
do plano), e não a partir do P0.

## 1. Base de capacidade (ARQUITETURA-ALVO §6, valores de partida)

| Componente | Inicial |
|---|---|
| `api` | 3 × 2 vCPU / 4 GB |
| `worker` | 2 × 2 vCPU / 4 GB, autoscaling pela fila |
| Keycloak | 2 × 1 vCPU / 2 GB |
| PostgreSQL | 4 vCPU / 16 GB / 500 GB SSD, mais standby e réplica de leitura |
| Cluster dedicado (tenant grande) | 8 vCPU / 32 GB, workers próprios |

Capacidade de projeto: ~50 tenants e 2 milhões de ligações por cluster
(ADR-0011, requisito 7).

## 2. Tabela de custo — estrutura e referência

Estimativa grosseira já registrada no ADR-0011 (levantamento `18h` §3.3):
**ordem de US$ 2 mil por mês** para o dimensionamento inicial da
ARQUITETURA-ALVO §6 — mais que a mensalidade de referência inteira do
SAMAE (R$ 7.636,74, citada no mesmo levantamento). O modelo só fecha com
vários tenants por cluster ou dimensionamento menor nos primeiros clientes;
a tabela abaixo é, por isso, a estrutura a precificar, não um preço
congelado: os valores unitários devem ser refeitos no AWS Pricing
Calculator no momento da proposta (LC.1) e na renovação de cada proposta,
porque preços de instância, armazenamento e transferência mudam e a
cotação em reais depende do câmbio do dia.

| Item (AWS sa-east-1) | Base de cálculo | Observação |
|---|---|---|
| RDS for PostgreSQL 17 Multi-AZ (primário + standby síncrono) | 4 vCPU / 16 GB, 500 GB SSD | PITR e backup automático incluídos no modelo RDS; cópia de snapshots para outra conta na mesma região |
| Réplica de leitura RDS (relatórios, SINISA, BI) | 1 instância da mesma classe | ADR-0027 |
| ECS Fargate: api (3 réplicas) + worker (2 réplicas, autoscaling) | 2 vCPU / 4 GB por réplica | Ver §6; autoscaling do worker pela fila |
| Keycloak (2 réplicas) | 1 vCPU / 2 GB por réplica | Modo de produção (P0.29 local; LC.6 na AWS) |
| ALB + CloudFront + WAF | Tráfego do portal e da API | Mapeamento §7 da ARQUITETURA-ALVO |
| S3 com Object Lock (documentos fiscais e de auditoria) | Volume por tenant × retenção legal | Prefixo e chave por tenant (ADR-0022) |
| KMS (KEK) + Secrets Manager | Chaves e segredos de execução | Rotação anual da KEK; rotação de segredos |
| CloudWatch / Prometheus e Grafana gerenciados | Séries e logs do §6 | Orçamento de séries declarado na P0.8 |
| Transferência de dados | Saída para internet (CloudFront) | Dados de tenant, réplicas e backups não saem de sa-east-1 |

Dimensões a precificar à parte, com a mesma estrutura:

- **Piloto:** mesmo desenho, em escala mínima (1 réplica de api/worker,
  RDS sem réplica de leitura até a carga justificar), com o custo
  contado a partir do lançamento — o piloto em homologação local (P0.27)
  não usa AWS e não entra nesta conta.
- **Dimensionamento mínimo dos primeiros tenants:** 1 cluster
  compartilhado (vários tenants por cluster, como exige o fechamento do
  modelo) + S3/KMS/Secrets Manager; cluster dedicado (8 vCPU / 32 GB)
  só para tenant grande, por ADR-0005.

## 3. Extensões no RDS for PostgreSQL 17 — conferência documental

Fonte: página oficial "Extension versions for Amazon RDS for PostgreSQL"
(AWS, consultada em 01/11/2026). Valores da coluna 17.11 (minor mais
recente listada na consulta):

| Extensão | RDS 17.11 | Uso no Ziip SAN |
|---|---|---|
| PostGIS | 3.5.6 | GIS; **não 3.7.0** |
| pg_trgm | 1.6 | Busca (P0.22, GIN trigram) |
| unaccent | 1.1 | Busca (P0.22) |
| pg_partman | 5.4.3 | Partição do audit_log e tabelas de lote |
| pgrouting | 3.6.3 | GIS (ADR-0034) |
| pgaudit | 17.1 | Auditoria (ver ADR-0034 / P1.7) |
| pg_stat_statements | 1.11 | Observabilidade (P0.8) |

**Divergência registrada (não mascarada):** o ambiente local usa
PostgreSQL 17 + PostGIS 3.7.0 por decisão do dono (PGDG, 31/10/2026),
enquanto o RDS para PostgreSQL 17 oferece, na consulta acima, no máximo
PostGIS 3.5.6. Consequência prática: o código não pode depender de
recursos exclusivos do PostGIS 3.7 — e hoje não depende (a única
diferença 3.5 → 3.7 mapeada no turno 3 é a remoção de
`address_standardizer`/`postgis_tiger_geocoder` no 3.7, que o projeto
não usa). A prova prática desta tabela é da LC.1, com instância real.

**Não listado na tabela RDS para PG17 (consultado 01/11/2026):
`pg_jsonschema`.** Se algum módulo passar a exigi-lo, a decisão é da
LC.1 (alternativa fora do RDS ou validação na aplicação), não do P0 —
não se afirma aqui indisponibilidade absoluta, só ausência na tabela
consultada, a reconfirmar com instância real.

**SRID 5396:** a disponibilidade no RDS é conferida junto com a prova
prática da LC.1 (a tabela de extensões não lista SRIDs); nenhum código
do P0 depende hoje de SRID 5396.

## 4. Pendente de conta do dono (não inventado)

- Relatórios de certificações do provedor no AWS Artifact (ISO 9001,
  ISO/IEC 27001, ISO/IEC 27017; SOC 1 e SOC 3 são relatórios de
  asseguração de auditor independente, não certificações) com escopo
  em `sa-east-1`, para o kit da P0.44. Exige só a conta AWS, sem recurso
  provisionado — e a conta ainda não existe para este projeto. Este
  item fica como pendência do dono; nenhum relatório foi baixado nem
  inventado aqui.
