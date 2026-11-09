# Pacote LC.5 — S3 com Object Lock
# Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor) — bloco de conclusao, 09/11/2026.
# Fonte unica do aceite: ~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json
# Sem segredo literal neste arquivo. Valores sem fonte ficam como variaveis
# obrigatorias marcadas [A DEFINIR] — nunca como constantes inventadas.
# Composicao com a LC.2: este modulo recebe rede/conta por variaveis de
# entrada; nao presume caminhos internos da LC.2 alem da arvore declarada
# no LANDING deste pacote.

# Bucket com Object Lock — o lock nasce com o bucket (exigencia do S3:
# object_lock_enabled so na criacao). Versionamento obrigatorio.
resource "aws_s3_bucket" "docs" {
  bucket              = var.bucket_name
  object_lock_enabled = true
  tags                = var.tags
}

resource "aws_s3_bucket_versioning" "docs" {
  bucket = aws_s3_bucket.docs.id
  versioning_configuration {
    status = "Enabled" # pre-condicao do Object Lock
  }
}

# Retencao padrao em MODO DE CONFORMIDADE: apagar ou sobrescrever XML
# retido e recusado pelo S3 ate expirar a retencao — inclusive para o
# administrador do bucket (prova LC5-T2 do roteiro, na AWS).
resource "aws_s3_bucket_object_lock_configuration" "docs" {
  bucket = aws_s3_bucket.docs.id
  rule {
    default_retention {
      mode = "COMPLIANCE"
      days = var.retention_days
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "docs" {
  bucket = aws_s3_bucket.docs.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = var.kms_key_arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "docs" {
  bucket                  = aws_s3_bucket.docs.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Prefixo e chave por tenant: objetos em tenant/<tenant_id>/... e a
# policy nega escrita fora do prefixo do tenant chamante (a identidade
# por tenant chega via role/tag de sessao da aplicacao — P0.45).
data "aws_iam_policy_document" "tenant_prefix" {
  statement {
    sid    = "DenyWriteOutsideOwnTenantPrefix"
    effect = "Deny"
    actions = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.docs.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "StringNotLike"
      variable = "s3:prefix"
      values   = ["tenant/$${aws:PrincipalTag/tenant_id}/*"]
    }
  }
  statement {
    sid    = "DenyDeleteOfRetainedObjects"
    effect = "Deny"
    actions = ["s3:DeleteObject", "s3:DeleteObjectVersion"]
    resources = ["${aws_s3_bucket.docs.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    # O Object Lock em COMPLIANCE ja recusa a exclusao; esta negacao
    # explicita fecha tambem o caminho sem versao e documenta a intencao.
    condition {
      test     = "Bool"
      variable = "s3:ObjectLockRetainUntilDate"
      values   = ["true"]
    }
  }
}

resource "aws_s3_bucket_policy" "docs" {
  bucket = aws_s3_bucket.docs.id
  policy = data.aws_iam_policy_document.tenant_prefix.json
}
