# Pacote LC.3 — RDS, backup e PITR
# Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor) — bloco de conclusao, 09/11/2026.
# Fonte unica do aceite: ~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json
# Sem segredo literal neste arquivo. Valores sem fonte ficam como variaveis
# obrigatorias marcadas [A DEFINIR] — nunca como constantes inventadas.
# Composicao com a LC.2: este modulo recebe rede/conta por variaveis de
# entrada; nao presume caminhos internos da LC.2 alem da arvore declarada
# no LANDING deste pacote.

variable "environment" {
  type        = string
  description = "Ambiente consumidor (homologacao/producao/demonstracao, parametrizado pela LC.2)."
}
variable "vpc_id" {
  type        = string
  description = "VPC da LC.2 (entrada por composicao; caminho interno da LC.2 nao presumido)."
}
variable "db_subnet_ids" {
  type        = list(string)
  description = "Sub-redes privadas de banco, em >= 2 AZs de sa-east-1 (Multi-AZ). Fornecidas pela LC.2."
}
variable "db_security_group_ids" {
  type        = list(string)
  description = "Security groups do RDS/PgBouncer, fornecidos pela LC.2."
}
variable "kms_key_arn" {
  type        = string
  description = "Chave KMS de criptografia em repouso (modulo LC.4: saida kms_key_arn da plataforma ou por tenant)."
}
variable "instance_class" {
  type        = string
  description = "[A DEFINIR] Classe da instancia RDS — sem fonte no aceite (pendencia P-LC3-01)."
}
variable "replica_instance_class" {
  type        = string
  description = "[A DEFINIR] Classe da replica de leitura — sem fonte no aceite (pendencia P-LC3-01)."
}
variable "allocated_storage_gib" {
  type        = number
  description = "[A DEFINIR] Armazenamento inicial em GiB — sem fonte no aceite (pendencia P-LC3-01)."
}
variable "master_username" {
  type        = string
  description = "Usuario mestre do RDS. A senha NUNCA entra neste modulo: vem do Secrets Manager (LC.4) via referencia externa."
}
variable "backup_retention_days" {
  type        = number
  description = "[A DEFINIR] Retencao dos backups automaticos (dias). O aceite fixa RPO <= 15 min e RTO <= 4 h (RNF-14/RNF-15), nao a retencao (pendencia P-LC3-02)."
}
variable "vault_destination_account_id" {
  type        = string
  description = "[A DEFINIR] Conta AWS distinta da producao, MESMA regiao sa-east-1, dona do cofre isolado (PLT-04). ID fornecido na janela AWS (pendencia P-LC3-03)."
}
variable "vault_lock_min_retention_days" {
  type        = number
  description = "[A DEFINIR] Retencao minima do Vault Lock em modo de conformidade (pendencia P-LC3-04)."
}
variable "vault_lock_max_retention_days" {
  type        = number
  description = "[A DEFINIR] Retencao maxima / politica de expiracao do cofre (pendencia P-LC3-04)."
}
variable "backup_copy_delete_after_days" {
  type        = number
  description = "[A DEFINIR] Expiracao das copias no cofre isolado, conforme a politica de expiracao do aceite (pendencia P-LC3-04)."
}
variable "tags" {
  type    = map(string)
  default = {}
}
