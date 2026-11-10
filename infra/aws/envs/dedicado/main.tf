# Pacote LC.15 — Produtor, sessao b3a03bf9-b502-478f-a071-9590cb482d99
# Ambiente: dedicado — INSTANCIA DEDICADA (LC.15), executada por decisao 52
# do dono (09/11/2026): integralmente ja, mesmo sem contrato de cliente.
# Os actos em AWS real executam-se na contratacao (decisao 41).
#
# Aceite LC.15 (fonte unica, registro LC.15): pilha propria (api, worker,
# Keycloak e banco) em CONTA AWS SEPARADA, em sa-east-1, criada pela MESMA
# IaC da LC.2, com a mesma imagem e o mesmo pipeline de atualizacao, sem
# bifurcacao de codigo nem de versao (ADR-0024).
#
# Como a conta separada se materializa aqui:
# - o provider assume o papel da conta dedicada (var.dedicated_account_role_arn)
#   e a validacao em variables.tf exige conta distinta declarada;
# - backend S3 proprio DESSA conta (backend.hcl, fora de versao com valores);
# - o pipeline assume o papel OIDC proprio do ambiente dedicado
#   (vars.AWS_IAC_ROLE_ARN_DEDICADO no workflow deploy-infra.yml).
#
# Composicao desta pasta:
#   main.tf      fundacao/borda/plataforma/seguranca/mensageria (modulos LC.2)
#   stack.tf     worker + Keycloak proprios sobre o MESMO cluster (LC.6)
#   database.tf  banco exclusivo (modulo RDS/PITR da LC.3) + chaves/segredos
#                (modulo KMS/Secrets da LC.4), padroes da producao
#   access.tf    TR-104: papel SOMENTE LEITURA da TI do orgao + auditoria
# O STATE NAO GUARDA SEGREDO: nenhum recurso recebe valor de segredo;
# segredos vivem no Secrets Manager (LC.4) e entram por ARN.

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
  # Conta AWS SEPARADA do cliente dedicado: o papel assumido vive nessa
  # conta ([A DEFINIR] na ativacao — conta ainda nao existe). Sem este
  # assume_role, o apply cairia na conta do pipeline (compartilhada) —
  # o que o aceite proibe para a instancia dedicada.
  assume_role {
    role_arn     = var.dedicated_account_role_arn
    session_name = "ziipsan-dedicado-iac"
  }
  default_tags {
    tags = {
      Project     = "ziipsan"
      Environment = "dedicado"
      ManagedBy   = "terraform-lc2"
      Package     = "LC.15"
    }
  }
}

# Somente certificado do CloudFront e WAF CLOUDFRONT (metadados publicos,
# sem dado de tenant) — exigencia do servico; ver modulo edge e o
# inventario de residencia em outputs.tf (mesma excecao declarada da LC.2).
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}

# Guarda de ativacao: ambiente dedicado sem tenant identificado NAO e
# instancia dedicada — e erro de preenchimento. O roteiro de ativacao
# (docs/operacao/INSTANCIA-DEDICADA-ATIVACAO-LC15.md) trata este assert
# como portao: tenant_slug e a conta dedicada sao preenchidos juntos.
check "dedicado_tem_tenant_e_conta" {
  assert {
    condition     = var.environment_kind != "dedicated" || (var.tenant_slug != null && var.tenant_slug != "" && var.dedicated_account_role_arn != "")
    error_message = "Ambiente dedicated exige tenant_slug e dedicated_account_role_arn (conta AWS separada do cliente)."
  }
  assert {
    condition     = var.tenant_db_mode == "schema-per-tenant"
    error_message = "LC.15: o banco exclusivo usa o MODO PADRAO schema-por-tenant; modos da P4.6 nao sao dependencia desta instancia."
  }
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
  source              = "../../modules/platform"
  environment_name    = var.environment_name
  vpc_id              = module.foundation.vpc_id
  private_subnet_ids  = module.foundation.private_subnet_ids
  public_subnet_ids   = module.foundation.public_subnet_ids
  container_image     = var.container_image
  api_cpu             = var.api_cpu
  api_memory          = var.api_memory
  api_desired_count   = var.api_desired_count
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
