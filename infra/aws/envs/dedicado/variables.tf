# Pacote LC.15 — Produtor, sessao b3a03bf9-b502-478f-a071-9590cb482d99
# Parametros do ambiente dedicado. Base: variables.tf vivo da LC.2 (main),
# estendido com os parametros da pilha completa da LC.15 (banco, worker,
# Keycloak, TR-104 e conta separada). Valores concretos vivem em
# terraform.tfvars (NAO versionado com valores de conta/dominio reais ate
# existirem — ver terraform.tfvars.example). Nada aqui tem padrao
# presumido de tamanho: tamanho sem fonte fica [A DEFINIR].

variable "environment_name" {
  type = string
}

variable "environment_kind" {
  type = string
  validation {
    condition     = var.environment_kind == "dedicated"
    error_message = "Este ambiente e o dedicado da LC.15: environment_kind deve ser \"dedicated\"."
  }
}

variable "tenant_slug" {
  description = "Obrigatorio (nao nulo) em ambiente dedicated (LC.15): identifica o cliente dono da instancia. Cliente concreto: [A DEFINIR] (decisao 52 — execucao integral sem contrato; nenhum cliente inventado)."
  type        = string
  default     = null
}

variable "dedicated_account_id" {
  description = "[A DEFINIR] ID (12 digitos) da conta AWS SEPARADA do cliente dedicado, criada na ativacao. Declarado para conferencia contra o papel assumido."
  type        = string
  default     = ""
  validation {
    condition     = var.dedicated_account_id == "" || can(regex("^[0-9]{12}$", var.dedicated_account_id))
    error_message = "dedicated_account_id deve ser vazio ([A DEFINIR]) ou ter 12 digitos."
  }
}

variable "dedicated_account_role_arn" {
  description = "[A DEFINIR] ARN do papel IaC DENTRO da conta dedicada, assumido pelo provider e pelo pipeline. Vazio ate a conta existir; o check em main.tf impede apply dedicado sem ele."
  type        = string
  default     = ""
}

variable "tenant_db_mode" {
  description = "Modo do banco exclusivo do cliente. Aceite LC.15: MODO PADRAO de schema por tenant — a instancia dedicada NAO depende do pacote sob demanda da P4.6 (banco dedicado por tenant / schema compartilhado)."
  type        = string
  default     = "schema-per-tenant"
  validation {
    condition     = var.tenant_db_mode == "schema-per-tenant"
    error_message = "LC.15: tenant_db_mode so admite \"schema-per-tenant\" (modo padrao). Modos da P4.6 estao fora desta instancia."
  }
}

variable "vpc_cidr" {
  type = string
}

variable "root_domain" {
  description = "[A DEFINIR] dominio raiz real do ambiente."
  type        = string
}

variable "tenant_domains" {
  type = list(object({
    tenant_slug       = string
    domain            = string
    verification_txt  = string
    possession_proven = bool
  }))
  default = []
}

variable "waf_rate_limit" {
  description = "[A DEFINIR] limite de requisicoes do WAF."
  type        = number
}

variable "container_image" {
  description = "[A DEFINIR] imagem da release (ECR + digest). MESMA imagem em todos os ambientes (ADR-0024): api e worker desta instancia usam exatamente esta variavel — a mesma que o ambiente producao recebe da mesma release."
  type        = string
}

variable "certificate_arn_alb" {
  type    = string
  default = null
}

variable "api_cpu" {
  description = "[A DEFINIR] CPU Fargate da api."
  type        = number
}

variable "api_memory" {
  description = "[A DEFINIR] memoria Fargate da api (MiB)."
  type        = number
}

variable "api_desired_count" {
  description = "[A DEFINIR] replicas da api."
  type        = number
}

variable "worker_cpu" {
  description = "[A DEFINIR] CPU Fargate do worker (mesma imagem, modo \"worker\" do entrypoint — P0.5)."
  type        = number
  default     = null
}

variable "worker_memory" {
  description = "[A DEFINIR] memoria Fargate do worker (MiB)."
  type        = number
  default     = null
}

variable "worker_desired_count" {
  description = "[A DEFINIR] replicas do worker."
  type        = number
  default     = null
}

variable "keycloak_image" {
  description = "Imagem do Keycloak: a MESMA da producao (LC.6, infra/keycloak/producao/valores-implantacao.yaml na main: quay.io/keycloak/keycloak:26.8.0, com o tema da P0.35 embutido pelo Dockerfile da LC.6). Sem fork de versao (ADR-0024)."
  type        = string
  default     = "quay.io/keycloak/keycloak:26.8.0"
}

variable "keycloak_cpu" {
  description = "[A DEFINIR] CPU Fargate do Keycloak (LC.6 nao fixa; pendencia herdada P-LC6-04)."
  type        = number
  default     = null
}

variable "keycloak_memory" {
  description = "[A DEFINIR] memoria Fargate do Keycloak (MiB)."
  type        = number
  default     = null
}

variable "keycloak_hostname" {
  description = "[A DEFINIR] hostname publico do IdP dedicado, atras da borda (LC.2/LC.6)."
  type        = string
  default     = ""
}

variable "keycloak_db_secret_arn" {
  description = "[A DEFINIR] ARN do segredo (Secrets Manager, LC.4) com a credencial do banco do Keycloak. So a referencia entra aqui — nunca o valor."
  type        = string
  default     = ""
}

variable "db_instance_class" {
  description = "[A DEFINIR] classe da instancia RDS exclusiva (LC.3, P-LC3-01)."
  type        = string
}

variable "db_replica_instance_class" {
  description = "[A DEFINIR] classe da replica de leitura do RDS (LC.3, padrao da producao)."
  type        = string
}

variable "db_allocated_storage_gib" {
  description = "[A DEFINIR] armazenamento inicial do RDS em GiB (LC.3, P-LC3-01)."
  type        = number
}

variable "db_master_username" {
  description = "Usuario mestre do RDS exclusivo. A senha NUNCA entra nesta IaC: o modulo da LC.3 usa manage_master_user_password (Secrets Manager gerido)."
  type        = string
}

variable "db_backup_retention_days" {
  description = "[A DEFINIR] retencao dos backups automaticos em dias (LC.3, P-LC3-02; RPO/RTO medidos no roteiro da LC.3)."
  type        = number
}

variable "db_vault_destination_account_id" {
  description = "[A DEFINIR] conta do cofre isolado de backup, MESMA regiao sa-east-1 (padrao LC.3/PLT-04). Backup fora de sa-east-1 e proibido pelo aceite da LC.15."
  type        = string
}

variable "db_vault_lock_min_retention_days" {
  description = "[A DEFINIR] retencao minima do Vault Lock (LC.3, P-LC3-04)."
  type        = number
}

variable "db_vault_lock_max_retention_days" {
  description = "[A DEFINIR] retencao maxima do Vault Lock (LC.3, P-LC3-04)."
  type        = number
}

variable "db_backup_copy_delete_after_days" {
  description = "[A DEFINIR] expiracao das copias no cofre isolado (LC.3, P-LC3-04)."
  type        = number
}

variable "config_delivery_bucket" {
  description = "[A DEFINIR] bucket S3 (sa-east-1, na conta dedicada) das entregas do AWS Config."
  type        = string
}

variable "ses_tenants" {
  type = list(object({
    tenant_slug         = string
    email_from_domain   = string
    mail_from_subdomain = string
  }))
  default = []
}

variable "messaging_tenants" {
  type = list(object({
    tenant_slug          = string
    sms_sender_ref       = string
    whatsapp_account_ref = string
    monthly_cap_ref      = string
  }))
  default = []
}

variable "alarm_email" {
  type    = string
  default = null
}

# --- TR-104: TI do orgao (somente leitura, so neste ambiente) ------------

variable "orgao_ti_principal_arns" {
  description = "[A DEFINIR] ARNs (raiz da conta AWS do orgao ou papeis/usuarios nomeados) autorizados a assumir o papel TR-104. Vazio = ninguem assume: o papel nasce sem uso ate a ativacao nomear o orgao."
  type        = list(string)
  default     = []
}

variable "orgao_ti_external_id" {
  description = "[A DEFINIR] ExternalId exigido na assuncao do papel TR-104, combinado com o orgao na ativacao. Nunca um valor presumido."
  type        = string
  default     = ""
}

variable "audit_log_retention_days" {
  description = "Retencao do grupo de logs da auditoria (CloudTrail -> CloudWatch) do ambiente dedicado."
  type        = number
  default     = 365
}
