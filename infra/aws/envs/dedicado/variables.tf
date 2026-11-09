# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
# Parametros do ambiente. Valores concretos vivem em terraform.tfvars
# (NAO versionado com valores de conta/dominio reais ate existirem —
# ver terraform.tfvars.example). Nada aqui tem padrao presumido de
# tamanho: tamanho sem fonte fica [A DEFINIR] e o apply nao roda sem ele.

variable "environment_name" {
  type = string
}

variable "environment_kind" {
  type = string
}

variable "tenant_slug" {
  description = "Obrigatorio (nao nulo) apenas em ambiente dedicated (LC.15)."
  type        = string
  default     = null
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
  description = "[A DEFINIR] imagem da release (ECR + digest). Mesma imagem em todos os ambientes (ADR-0024)."
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

variable "config_delivery_bucket" {
  description = "[A DEFINIR] bucket S3 (sa-east-1) das entregas do AWS Config."
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
