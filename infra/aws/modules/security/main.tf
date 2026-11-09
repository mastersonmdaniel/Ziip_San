# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
# Servicos de seguranca do provedor (aceite LC.2). Mapeamento funcao ->
# servico DECLARADO como interpretacao de engenharia no RELATORIO-LC.2:
#   TR-091 deteccao de ameacas            -> Amazon GuardDuty
#   TR-092 inspecao de vulnerabilidades   -> Amazon Inspector
#   TR-096 registro de configuracao       -> AWS Config
# (Correcao de menor da revisao: este cabecalho citava o CloudTrail como
# parte do TR-096, mas nenhum recurso de CloudTrail existe neste modulo
# nem e criado por ele; a citacao foi removida para o comentario dizer
# so o que o codigo faz. Sessao da correcao:
# b36fd7a8-91ec-4322-9e7e-8bfe051efd54.)
# As tres funcoes sao nomeadas assim no ADR-0011 (servicos do edital
# VISAN); os nomes de servico sao a leitura de engenharia desta entrega.

# TR-091 — deteccao de ameacas continua na conta/regiao do ambiente.
resource "aws_guardduty_detector" "this" {
  enable                       = true
  finding_publishing_frequency = "FIFTEEN_MINUTES"
  tags                         = { Environment = var.environment_name, Package = "LC.2", FunctionRef = "TR-091" }
}

# TR-092 — inspecao de vulnerabilidades (EC2/ECR/Lambda da conta).
resource "aws_inspector2_enabler" "this" {
  account_ids    = [data.aws_caller_identity.current.account_id]
  resource_types = ["EC2", "ECR", "LAMBDA"]
}

data "aws_caller_identity" "current" {}

# TR-096 — registro de configuracao: gravador + canal de entrega + regras.
resource "aws_config_configuration_recorder" "this" {
  name     = "ziipsan-${var.environment_name}"
  role_arn = aws_iam_role.config.arn
  recording_group {
    all_supported                 = true
    include_global_resource_types = false
  }
}

resource "aws_iam_role" "config" {
  name = "ziipsan-${var.environment_name}-config"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "config.amazonaws.com" }
    }]
  })
  tags = { Environment = var.environment_name, Package = "LC.2" }
}

resource "aws_config_delivery_channel" "this" {
  name           = "ziipsan-${var.environment_name}"
  s3_bucket_name = var.config_delivery_bucket
  depends_on     = [aws_config_configuration_recorder.this]
}

resource "aws_config_configuration_recorder_status" "this" {
  name       = aws_config_configuration_recorder.this.name
  is_enabled = true
  depends_on = [aws_config_delivery_channel.this]
}

# Regras geridas ligadas aos controles desta entrega.
resource "aws_config_config_rule" "s3_public_block" {
  name = "ziipsan-${var.environment_name}-s3-account-level-public-access-blocks"
  source {
    owner             = "AWS"
    source_identifier = "S3_ACCOUNT_LEVEL_PUBLIC_ACCESS_BLOCKS"
  }
  depends_on = [aws_config_configuration_recorder_status.this]
}

resource "aws_config_config_rule" "cloudfront_tls" {
  name = "ziipsan-${var.environment_name}-cloudfront-viewer-certificate"
  source {
    owner             = "AWS"
    source_identifier = "CLOUDFRONT_VIEWER_CERTIFICATE_EXPIRATION_CHECK"
  }
  depends_on = [aws_config_configuration_recorder_status.this]
}

resource "aws_config_config_rule" "rds_storage_encrypted" {
  name = "ziipsan-${var.environment_name}-rds-storage-encrypted"
  source {
    owner             = "AWS"
    source_identifier = "RDS_STORAGE_ENCRYPTED"
  }
  depends_on = [aws_config_configuration_recorder_status.this]
}
