# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
variable "environment_name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "certificate_arn_alb" {
  description = "ARN do certificado regional (sa-east-1) do ALB. [A DEFINIR] na aplicacao: o ACM regional e emitido na composicao do ambiente quando houver dominio real."
  type        = string
  default     = null
}

variable "container_image" {
  description = "Imagem unica da aplicacao (ADR-0024: mesma imagem em todos os ambientes). Referencia SEM tag presumida: [A DEFINIR] no tfvars (repositorio ECR + digest da release)."
  type        = string
}

variable "api_cpu" {
  description = "CPU da tarefa api (unidades Fargate). [A DEFINIR] por ambiente."
  type        = number
}

variable "api_memory" {
  description = "Memoria da tarefa api (MiB). [A DEFINIR] por ambiente."
  type        = number
}

variable "api_desired_count" {
  description = "Replicas desejadas da api. [A DEFINIR] por ambiente."
  type        = number
}
