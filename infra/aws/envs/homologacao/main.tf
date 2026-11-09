# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
# Ambiente: homologacao — Homologacao na AWS (espelho da homologacao local da P0.27, com dado anonimizado na origem — ADR-0033).
# Composicao dos modulos da LC.2. Backend remoto em S3 na MESMA regiao
# (sa-east-1), com lock e criptografia; a configuracao concreta do backend
# vai em backend.hcl (fora do controle de versao com valores de conta —
# ver backend.hcl.example). O STATE NAO GUARDA SEGREDO: nenhum recurso
# desta IaC recebe valor de segredo; segredos vivem no Secrets Manager
# (LC.4) e entram nos servicos por ARN.

terraform {
  required_version = ">= 1.6.0"
  backend "s3" {}
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

provider "aws" {
  region = "sa-east-1"
  default_tags {
    tags = {
      Project     = "ziipsan"
      Environment = "homologacao"
      ManagedBy   = "terraform-lc2"
      Package     = "LC.2"
    }
  }
}

# Somente certificado do CloudFront e WAF CLOUDFRONT (metadados publicos,
# sem dado de tenant) — exigencia do servico; ver modulo edge.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}

module "foundation" {
  source           = "../../modules/foundation"
  environment_name = var.environment_name
  environment_kind = var.environment_kind
  aws_region       = "sa-east-1"
  vpc_cidr         = var.vpc_cidr
  tenant_slug      = var.tenant_slug
}

module "platform" {
  source             = "../../modules/platform"
  environment_name   = var.environment_name
  vpc_id             = module.foundation.vpc_id
  private_subnet_ids = module.foundation.private_subnet_ids
  public_subnet_ids  = module.foundation.public_subnet_ids
  container_image    = var.container_image
  api_cpu            = var.api_cpu
  api_memory         = var.api_memory
  api_desired_count  = var.api_desired_count
  certificate_arn_alb = var.certificate_arn_alb
}

module "edge" {
  source = "../../modules/edge"
  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }
  environment_name = var.environment_name
  root_domain      = var.root_domain
  origin_domain    = module.platform.alb_dns_name
  tenant_domains   = var.tenant_domains
  waf_rate_limit   = var.waf_rate_limit
}

module "security" {
  source                 = "../../modules/security"
  environment_name       = var.environment_name
  config_delivery_bucket = var.config_delivery_bucket
}

module "messaging" {
  source            = "../../modules/messaging"
  environment_name  = var.environment_name
  ses_tenants       = var.ses_tenants
  messaging_tenants = var.messaging_tenants
  alarm_email       = var.alarm_email
}
