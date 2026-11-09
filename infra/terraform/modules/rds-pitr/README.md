# Modulo `rds-pitr` (LC.3)
Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor). Caminho de repo declarado:
`infra/terraform/modules/rds-pitr/`. Consumido pelo root de ambiente da
LC.2 via `source` + variaveis de entrada (vpc/sub-redes/roles/KMS).
PgBouncer: `config/pgbouncer.ini` (pool_mode=transaction) na entrega;
a hospedagem do processo e da camada de computo da LC.2 [A DEFINIR — P-LC3-05].
