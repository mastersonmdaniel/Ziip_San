# Pacote LC.4 — KMS e Secrets Manager: R1 em producao
# Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor) — bloco de conclusao, 09/11/2026.
# Fonte unica do aceite: ~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json
# Sem segredo literal neste arquivo. Valores sem fonte ficam como variaveis
# obrigatorias marcadas [A DEFINIR] — nunca como constantes inventadas.
# Composicao com a LC.2: este modulo recebe rede/conta por variaveis de
# entrada; nao presume caminhos internos da LC.2 alem da arvore declarada
# no LANDING deste pacote.

locals {
  key_mode = { for t in var.tenant_ids : t => lookup(var.tenant_key_modes, t, "PLATFORM") }
}

# Chave da plataforma (KEK base dos segredos e dos servicos AWS).
resource "aws_kms_key" "platform" {
  description             = "ziipsan-${var.environment}-platform-kek"
  enable_key_rotation     = true # rotacao automatica: so no modo PLATFORM
  deletion_window_in_days = 30
  tags                    = var.tags
}
resource "aws_kms_alias" "platform" {
  name          = "alias/ziipsan-${var.environment}-platform"
  target_key_id = aws_kms_key.platform.key_id
}

# Uma chave por tenant (base da DEK por tenant gerada pelo KMS e do
# crypto-shredding RNF-43: revogar/destruir ESTA chave torna ilegivel so
# o dado deste tenant).
resource "aws_kms_key" "tenant" {
  for_each = local.key_mode

  description = "ziipsan-${var.environment}-tenant-${each.key}-kek (crypto.key_mode=${each.value})"
  # Origem por modo (PLT-41):
  #  PLATFORM -> AWS_KMS (material gerado pelo KMS)
  #  IMPORTED -> EXTERNAL (material de chave importado no KMS)
  #  EXTERNAL -> EXTERNAL_KEY_STORE (armazenamento externo de chaves)
  origin = (
    each.value == "PLATFORM" ? "AWS_KMS" :
    each.value == "IMPORTED" ? "EXTERNAL" : "EXTERNAL_KEY_STORE"
  )
  # Limitacao do provedor registrada no aceite: IMPORTED/EXTERNAL NAO tem
  # rotacao automatica. Rotacao so e ligada no modo PLATFORM.
  enable_key_rotation = each.value == "PLATFORM"
  # Modo EXTERNAL (PLT-41): a chave vive no armazenamento externo (XKS);
  # o ID chega por variavel sob contrato. Modo IMPORTED: a chave nasce
  # sem material e o material e importado no passo pos-apply do roteiro
  # (LC4-T5) — o Terraform nao importa material de chave.
  custom_key_store_id     = each.value == "EXTERNAL" ? var.external_key_store_id : null
  deletion_window_in_days = 30
  tags = merge(var.tags, { tenant_id = each.key, crypto_key_mode = each.value })
}

# Segredos de producao: criados VAZIOS (sem secret_string). Valores sao
# inseridos fora do Terraform, direto no Secrets Manager, na janela AWS.
# Nada do cofre local e reaproveitado: nenhum valor, ARN ou nome de
# segredo/chave local e referenciado neste modulo (prova LC4-T3).
resource "aws_secretsmanager_secret" "prod" {
  for_each    = toset(var.secret_names)
  name        = "ziipsan/${var.environment}/${each.key}"
  kms_key_id  = aws_kms_key.platform.arn
  description = "Segredo de producao ZiipSan (valor inserido fora do IaC). LC.4."
  tags        = var.tags
}

resource "aws_secretsmanager_secret_rotation" "prod" {
  for_each          = var.rotation_lambda_arn == "" ? {} : { for s in var.secret_names : s => s }
  secret_id         = aws_secretsmanager_secret.prod[each.key].id
  rotation_lambda_arn = var.rotation_lambda_arn
  rotation_rules {
    automatically_after_days = 30 # [A DEFINIR] periodicidade nao consta do aceite (P-LC4-03); valor so se aplica quando ha Lambda de rotacao.
  }
}
