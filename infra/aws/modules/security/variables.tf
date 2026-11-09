# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
variable "environment_name" {
  type = string
}

variable "aws_region" {
  type    = string
  default = "sa-east-1"
  validation {
    condition     = var.aws_region == "sa-east-1"
    error_message = "Residencia RNF-44: a regiao deve ser sa-east-1."
  }
}

variable "config_delivery_bucket" {
  description = "Bucket S3 (sa-east-1) que recebe as entregas do AWS Config. Criado pela composicao (LC.5 disciplina o S3); aqui entra so o nome/ARN de destino. [A DEFINIR] na aplicacao."
  type        = string
}
