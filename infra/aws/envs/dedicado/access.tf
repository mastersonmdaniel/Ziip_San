# Pacote LC.15 — Produtor, sessao b3a03bf9-b502-478f-a071-9590cb482d99
# TR-104 — acesso administrativo da TI do ORGAO: concedido SO na instancia
# dedicada (esta pasta so existe neste ambiente; nenhum recurso deste
# arquivo existe nos envs compartilhados), SOMENTE LEITURA sobre
# configuracao e monitoramento, com MFA e auditoria.
#
# Desenho:
# 1. Papel IAM "orgao-ti-readonly" assumivel apenas pelos principais do
#    orgao nomeados na ativacao (var.orgao_ti_principal_arns; vazio =
#    ninguem), com ExternalId e com condicoes de MFA:
#    aws:MultiFactorAuthPresent = true e sessao com no maximo 12 h desde
#    a autenticacao MFA (aws:MultiFactorAuthAge <= 43200). Sem MFA, a
#    assuncao e recusada pelo proprio trust.
# 2. Politica SOMENTE LEITURA: a lista canonica de acoes esta em
#    policies/orgao-ti-readonly-tr104.json (artefato legivel pelo orgao e
#    pela auditoria) e e carregada por este arquivo — fonte unica, sem
#    copia divergente. Todas as acoes sao de leitura (Get/List/Describe);
#    NAO ha GetSecretValue nem kms:Decrypt (segredos: so metadados) e NAO
#    ha nenhuma acao de escrita. O que nao esta concedido esta negado por
#    omissao (IAM deny-by-default).
# 3. Negacao explicita de escrita (defesa em profundidade + o teste do
#    aceite): uma segunda politica anexada ao papel nega explicitamente
#    qualquer acao fora da lista de leitura nos servicos cobertos. Uma
#    tentativa de escrita do perfil do orgao e, portanto, recusada duas
#    vezes (omissao + Deny explicito).
# 4. AUDITORIA: CloudTrail desta conta regista TODAS as chamadas de API
#    (incluindo as recusadas — o AccessDenied entra no log), com
#    validacao de integridade dos ficheiros, criptografia KMS e entrega
#    em bucket S3 da propria conta/regiao + grupo CloudWatch. E esse
#    registo que o teste vivo do aceite confere (pendencia nomeada:
#    execucao na contratacao, decisao 41).
# Lado aplicacao: o papel do Keycloak correspondente esta em
# keycloak/orgao-ti-readonly-role.json (leitura de configuracao e
# monitoramento na consola da instancia; sem escrita).

data "aws_caller_identity" "dedicated" {}

resource "aws_iam_role" "orgao_ti_readonly" {
  name = "ziipsan-${var.environment_name}-orgao-ti-readonly"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "OrgaoTIComMFA"
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { AWS = var.orgao_ti_principal_arns }
      Condition = {
        Bool = {
          "aws:MultiFactorAuthPresent" = "true"
        }
        NumericLessThanEquals = {
          "aws:MultiFactorAuthAge" = 43200
        }
        StringEquals = var.orgao_ti_external_id == "" ? {} : {
          "sts:ExternalId" = var.orgao_ti_external_id
        }
      }
    }]
  })
  # Sessoes do papel: no maximo 1 h por assuncao (renovavel com MFA).
  max_session_duration = 3600
  tags                 = { Environment = var.environment_name, Package = "LC.15", FunctionRef = "TR-104" }
}

resource "aws_iam_policy" "orgao_ti_readonly" {
  name   = "ziipsan-${var.environment_name}-orgao-ti-readonly"
  policy = file("${path.module}/policies/orgao-ti-readonly-tr104.json")
  tags   = { Environment = var.environment_name, Package = "LC.15", FunctionRef = "TR-104" }
}

resource "aws_iam_role_policy_attachment" "orgao_ti_readonly" {
  role       = aws_iam_role.orgao_ti_readonly.name
  policy_arn = aws_iam_policy.orgao_ti_readonly.arn
}

# Negacao explicita de escrita: qualquer acao que nao seja de leitura nos
# servicos da instancia e negada, mesmo que uma politica futura mal
# anexada a concedesse por engano.
resource "aws_iam_policy" "orgao_ti_deny_write" {
  name = "ziipsan-${var.environment_name}-orgao-ti-deny-write"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "DenyAnythingNotReadOnly"
      Effect = "Deny"
      NotAction = [
        "cloudwatch:Describe*", "cloudwatch:Get*", "cloudwatch:List*",
        "logs:Describe*", "logs:Get*", "logs:List*", "logs:FilterLogEvents",
        "ecs:Describe*", "ecs:List*",
        "rds:Describe*", "rds:ListTagsForResource",
        "elasticloadbalancing:Describe*",
        "ec2:Describe*",
        "cloudfront:Get*", "cloudfront:List*",
        "wafv2:Get*", "wafv2:List*",
        "config:Describe*", "config:Get*", "config:List*", "config:SelectResourceConfig",
        "guardduty:Get*", "guardduty:List*",
        "inspector2:Get*", "inspector2:List*", "inspector2:BatchGet*",
        "secretsmanager:DescribeSecret", "secretsmanager:ListSecrets", "secretsmanager:ListSecretVersionIds",
        "kms:Describe*", "kms:Get*", "kms:List*",
        "s3:Get*", "s3:List*",
        "backup:Describe*", "backup:Get*", "backup:List*",
        "events:Describe*", "events:List*",
        "sns:Get*", "sns:List*",
        "ses:Get*", "ses:List*",
        "sts:GetCallerIdentity",
      ]
      Resource = "*"
    }]
  })
  tags = { Environment = var.environment_name, Package = "LC.15", FunctionRef = "TR-104" }
}

resource "aws_iam_role_policy_attachment" "orgao_ti_deny_write" {
  role       = aws_iam_role.orgao_ti_readonly.name
  policy_arn = aws_iam_policy.orgao_ti_deny_write.arn
}

# --- Auditoria da conta dedicada (regista inclusive as recusas) ---------

resource "aws_kms_key" "audit" {
  description             = "ziipsan-${var.environment_name}-audit (CloudTrail/S3 de auditoria, TR-104)"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  tags                    = { Environment = var.environment_name, Package = "LC.15" }
}

resource "aws_s3_bucket" "audit" {
  bucket_prefix = "ziipsan-${var.environment_name}-audit-"
  tags          = { Environment = var.environment_name, Package = "LC.15" }
}

resource "aws_s3_bucket_versioning" "audit" {
  bucket = aws_s3_bucket.audit.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "audit" {
  bucket                  = aws_s3_bucket.audit.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "audit" {
  bucket = aws_s3_bucket.audit.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.audit.arn
    }
  }
}

# Sem replication_configuration: auditoria e backups NAO replicam para
# fora de sa-east-1 (aceite LC.15). O bucket vive nesta conta e regiao.

resource "aws_s3_bucket_policy" "audit" {
  bucket = aws_s3_bucket.audit.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AWSCloudTrailAclCheck"
        Effect    = "Allow"
        Principal = { Service = "cloudtrail.amazonaws.com" }
        Action    = "s3:GetBucketAcl"
        Resource  = aws_s3_bucket.audit.arn
      },
      {
        Sid       = "AWSCloudTrailWrite"
        Effect    = "Allow"
        Principal = { Service = "cloudtrail.amazonaws.com" }
        Action    = "s3:PutObject"
        Resource  = "${aws_s3_bucket.audit.arn}/AWSLogs/${data.aws_caller_identity.dedicated.account_id}/*"
        Condition = {
          StringEquals = { "s3:x-amz-acl" = "bucket-owner-full-control" }
        }
      },
    ]
  })
}

resource "aws_cloudwatch_log_group" "audit" {
  name              = "/ziipsan/${var.environment_name}/audit-trail"
  retention_in_days = var.audit_log_retention_days
  kms_key_id        = aws_kms_key.audit.arn
  tags              = { Environment = var.environment_name, Package = "LC.15", FunctionRef = "TR-104" }
}

resource "aws_iam_role" "cloudtrail_logs" {
  name = "ziipsan-${var.environment_name}-cloudtrail-logs"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "cloudtrail.amazonaws.com" }
    }]
  })
  tags = { Environment = var.environment_name, Package = "LC.15" }
}

resource "aws_iam_role_policy" "cloudtrail_logs" {
  name = "cloudtrail-to-cloudwatch"
  role = aws_iam_role.cloudtrail_logs.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
      Resource = "${aws_cloudwatch_log_group.audit.arn}:*"
    }]
  })
}

resource "aws_cloudtrail" "dedicated_audit" {
  name                          = "ziipsan-${var.environment_name}-audit"
  s3_bucket_name                = aws_s3_bucket.audit.id
  kms_key_id                    = aws_kms_key.audit.arn
  enable_log_file_validation    = true
  is_multi_region_trail         = false
  include_global_service_events = true
  cloud_watch_logs_group_arn    = "${aws_cloudwatch_log_group.audit.arn}:*"
  cloud_watch_logs_role_arn     = aws_iam_role.cloudtrail_logs.arn
  event_selector {
    read_write_type           = "All"
    include_management_events = true
  }
  depends_on = [aws_s3_bucket_policy.audit]
  tags       = { Environment = var.environment_name, Package = "LC.15", FunctionRef = "TR-104" }
}
