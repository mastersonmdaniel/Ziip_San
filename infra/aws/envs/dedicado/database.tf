# Pacote LC.15 — Produtor, sessao b3a03bf9-b502-478f-a071-9590cb482d99
# Pilha propria, parte 3: BANCO EXCLUSIVO + CHAVES/SEGREDOS da instancia
# dedicada, nos MESMOS padroes da producao (LC.3 e LC.4).
#
# Banco: modulo RDS/PITR da LC.3 JA NA MAIN
# (infra/terraform/modules/rds-pitr) — o mesmo que a producao usa:
# PostgreSQL 17 Multi-AZ, PITR, criptografia com KMS, senha mestre gerida
# pelo Secrets Manager, backup com copia para cofre isolado em outra
# conta na MESMA regiao sa-east-1. Nenhuma configuracao de replicacao ou
# backup para fora de sa-east-1 existe neste ambiente (guarda verificada
# pelo harness da LC.15).
#
# MODO DO BANCO (ponto central do aceite): a instancia RDS e exclusiva
# deste cliente, mas DENTRO dela a aplicacao corre no MODO PADRAO de
# schema por tenant (var.tenant_db_mode = "schema-per-tenant", validado
# em variables.tf e no check de main.tf). A LC.15 NAO usa nem depende do
# pacote sob demanda da P4.6 (banco dedicado por tenant / schema
# compartilhado entre tenants): nao ha referencia a isolation_mode nem a
# modos da P4.6 nesta pasta. Dentro do RDS exclusivo vivem, separados por
# banco/role como na producao (LC.6): o banco da aplicacao (schemas por
# tenant) e o banco "keycloak".
#
# Chaves e segredos: modulo KMS/Secrets da LC.4 JA NA MAIN
# (infra/terraform/modules/kms-secrets) — chave da plataforma com rotacao
# automatica, chave por tenant (aqui: o tenant unico desta instancia) e
# segredos criados VAZIOS, preenchidos fora da IaC. Padroes identicos aos
# da producao; nada de padrao "dedicado" paralelo.

resource "aws_security_group" "db" {
  name   = "ziipsan-${var.environment_name}-db"
  vpc_id = module.foundation.vpc_id
  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
    description = "PostgreSQL apenas de dentro da VPC dedicada (api/worker/Keycloak)"
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Environment = var.environment_name, Package = "LC.15" }
}

resource "aws_iam_role" "backup" {
  name = "ziipsan-${var.environment_name}-backup"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "backup.amazonaws.com" }
    }]
  })
  tags = { Environment = var.environment_name, Package = "LC.15" }
}

resource "aws_iam_role_policy_attachment" "backup" {
  role       = aws_iam_role.backup.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForBackup"
}

module "secrets" {
  source       = "../../../terraform/modules/kms-secrets"
  environment  = var.environment_name
  tenant_ids   = var.tenant_slug == null ? [] : [var.tenant_slug]
  secret_names = []
  tags         = { Environment = var.environment_name, Package = "LC.15" }
}

module "database" {
  source                         = "../../../terraform/modules/rds-pitr"
  environment                    = var.environment_name
  vpc_id                         = module.foundation.vpc_id
  db_subnet_ids                  = module.foundation.private_subnet_ids
  db_security_group_ids          = [aws_security_group.db.id]
  kms_key_arn                    = module.secrets.platform_kms_key_arn
  instance_class                 = var.db_instance_class
  replica_instance_class         = var.db_replica_instance_class
  allocated_storage_gib          = var.db_allocated_storage_gib
  master_username                = var.db_master_username
  backup_retention_days          = var.db_backup_retention_days
  vault_destination_account_id   = var.db_vault_destination_account_id
  vault_lock_min_retention_days  = var.db_vault_lock_min_retention_days
  vault_lock_max_retention_days  = var.db_vault_lock_max_retention_days
  backup_copy_delete_after_days  = var.db_backup_copy_delete_after_days
  backup_role_arn                = aws_iam_role.backup.arn
  aws_region                     = "sa-east-1"
  tags                           = { Environment = var.environment_name, Package = "LC.15" }
}
