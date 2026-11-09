# Pacote LC.3 — RDS, backup e PITR
# Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor) — bloco de conclusao, 09/11/2026.
# Fonte unica do aceite: ~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json
# Sem segredo literal neste arquivo. Valores sem fonte ficam como variaveis
# obrigatorias marcadas [A DEFINIR] — nunca como constantes inventadas.
# Composicao com a LC.2: este modulo recebe rede/conta por variaveis de
# entrada; nao presume caminhos internos da LC.2 alem da arvore declarada
# no LANDING deste pacote.

# --- RDS PostgreSQL 17, Multi-AZ, com replica de leitura -------------------
resource "aws_db_subnet_group" "this" {
  name       = "ziipsan-${var.environment}-db"
  subnet_ids = var.db_subnet_ids
  tags       = var.tags
}

resource "aws_db_instance" "primary" {
  identifier     = "ziipsan-${var.environment}-pg17"
  engine         = "postgres"
  engine_version = "17"
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage_gib
  max_allocated_storage = 0 # [A DEFINIR] autoscaling de storage nao consta do aceite; 0 = desligado ate decisao (P-LC3-01).
  storage_encrypted     = true
  kms_key_id            = var.kms_key_arn

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = var.db_security_group_ids
  multi_az               = true # Multi-AZ (PLT-04: cobre falha de zona)

  username = var.master_username
  # manage_master_user_password faz o RDS guardar a senha mestre no
  # Secrets Manager gerido pela AWS — nenhum segredo literal no codigo/state deste modulo.
  manage_master_user_password = true

  # PITR: backups automaticos + retencao; a restauracao point-in-time do RDS
  # usa os transaction logs (WAL) arquivados continuamente — RPO <= 15 min e
  # RTO <= 4 h sao MEDIDOS no roteiro de provas (RNF-14/RNF-15), nao declarados aqui.
  backup_retention_period = var.backup_retention_days
  copy_tags_to_snapshot   = true

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "ziipsan-${var.environment}-final"
  tags                      = var.tags
}

resource "aws_db_instance" "read_replica" {
  identifier          = "ziipsan-${var.environment}-pg17-replica"
  replicate_source_db = aws_db_instance.primary.identifier
  instance_class      = var.replica_instance_class
  storage_encrypted   = true
  kms_key_id          = var.kms_key_arn
  tags                = var.tags
}

# --- AWS Backup: plano + cofre isolado em OUTRA CONTA, MESMA regiao --------
# O cofre de destino vive na conta var.vault_destination_account_id
# (sa-east-1). Este modulo declara a regra de copia; o cofre e o seu
# Vault Lock sao declarados em vault.tf, aplicado NA CONTA DE DESTINO.
resource "aws_backup_vault" "local" {
  name        = "ziipsan-${var.environment}-vault"
  kms_key_arn = var.kms_key_arn
  tags        = var.tags
}

resource "aws_backup_plan" "this" {
  name = "ziipsan-${var.environment}-pitr-plan"
  rule {
    rule_name         = "snapshots-e-backups-com-copia-isolada"
    target_vault_name = aws_backup_vault.local.name
    # Backup continuo (PITR) do AWS Backup para o RDS + snapshots diarios:
    enable_continuous_backup = true
    recovery_point_tags      = var.tags
    schedule                 = "cron(0 5 * * ? *)"
    lifecycle {
      delete_after = var.backup_copy_delete_after_days
    }
    copy_action {
      destination_vault_arn = "arn:aws:backup:${var.aws_region}:${var.vault_destination_account_id}:backup-vault:ziipsan-isolated-vault"
      lifecycle {
        delete_after = var.backup_copy_delete_after_days
      }
    }
  }
  tags = var.tags
}

resource "aws_backup_selection" "rds" {
  name         = "ziipsan-${var.environment}-rds"
  plan_id      = aws_backup_plan.this.id
  iam_role_arn = var.backup_role_arn
  resources    = [aws_db_instance.primary.arn]
}
