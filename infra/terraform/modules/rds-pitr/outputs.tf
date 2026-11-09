# Pacote LC.3 — RDS, backup e PITR
# Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor) — bloco de conclusao, 09/11/2026.
# Fonte unica do aceite: ~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json
# Sem segredo literal neste arquivo. Valores sem fonte ficam como variaveis
# obrigatorias marcadas [A DEFINIR] — nunca como constantes inventadas.
# Composicao com a LC.2: este modulo recebe rede/conta por variaveis de
# entrada; nao presume caminhos internos da LC.2 alem da arvore declarada
# no LANDING deste pacote.

output "rds_primary_endpoint" { value = aws_db_instance.primary.endpoint }
output "rds_primary_arn" { value = aws_db_instance.primary.arn }
output "rds_replica_endpoint" { value = aws_db_instance.read_replica.endpoint }
output "backup_vault_name" { value = aws_backup_vault.local.name }
output "backup_plan_id" { value = aws_backup_plan.this.id }
output "pgbouncer_pool_mode" { value = "transaction" }
