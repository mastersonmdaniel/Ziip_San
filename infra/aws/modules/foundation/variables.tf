# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
# Fundacao por ambiente: rede, tags de residencia e referencias de segredo.
# NENHUM valor de segredo entra neste modulo: apenas ARNs/nomes de recursos
# do Secrets Manager (o valor e criado/rotacionado fora da IaC — LC.4).

variable "environment_name" {
  description = "Nome do ambiente (homologacao, producao, demonstracao ou dedicado-<tenant>)."
  type        = string
}

variable "environment_kind" {
  description = "Tipo do ambiente: shared (homolog/producao), demo ou dedicated (LC.15)."
  type        = string
  validation {
    condition     = contains(["shared", "demo", "dedicated"], var.environment_kind)
    error_message = "environment_kind deve ser shared, demo ou dedicated."
  }
}

variable "aws_region" {
  description = "Regiao AWS. Fixa em sa-east-1 por ADR-0011/RNF-44; outro valor e recusado."
  type        = string
  default     = "sa-east-1"
  validation {
    condition     = var.aws_region == "sa-east-1"
    error_message = "Residencia RNF-44: a regiao deve ser sa-east-1."
  }
}

variable "vpc_cidr" {
  description = "CIDR da VPC do ambiente. [A DEFINIR] por ambiente no tfvars; sem padrao presumido."
  type        = string
}

variable "az_count" {
  description = "Numero de zonas de disponibilidade (Multi-AZ dentro de sa-east-1)."
  type        = number
  default     = 2
}

variable "tenant_slug" {
  description = "Slug do tenant; obrigatorio quando environment_kind = dedicated (LC.15)."
  type        = string
  default     = null
}
