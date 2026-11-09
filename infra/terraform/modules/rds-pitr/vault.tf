# Pacote LC.3 — RDS, backup e PITR
# Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor) — bloco de conclusao, 09/11/2026.
# Fonte unica do aceite: ~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json
# Sem segredo literal neste arquivo. Valores sem fonte ficam como variaveis
# obrigatorias marcadas [A DEFINIR] — nunca como constantes inventadas.
# Composicao com a LC.2: este modulo recebe rede/conta por variaveis de
# entrada; nao presume caminhos internos da LC.2 alem da arvore declarada
# no LANDING deste pacote.

# APLICAR NA CONTA DE DESTINO (conta distinta da producao, mesma regiao
# sa-east-1) — PLT-04, decisao do dono de 22/10/2026.
# Cofre logico isolado do AWS Backup com Vault Lock em MODO DE CONFORMIDADE:
# nem credencial administrativa da conta de producao altera/exclui copias
# dentro da retencao (prova LC3-T3 do roteiro — nao executada neste bloco).
resource "aws_backup_vault" "isolated" {
  name        = "ziipsan-isolated-vault"
  kms_key_arn = var.kms_key_arn
  tags        = var.tags
}

resource "aws_backup_vault_lock_configuration" "isolated" {
  backup_vault_name   = aws_backup_vault.isolated.name
  min_retention_days  = var.vault_lock_min_retention_days
  max_retention_days  = var.vault_lock_max_retention_days
  # changeable_for_days omitido de proposito: em modo de conformidade o
  # Vault Lock, apos o periodo de resfriamento, nao pode ser alterado nem
  # pelo root da conta do cofre. [A DEFINIR] periodo de resfriamento, se
  # adotado antes do travamento definitivo (pendencia P-LC3-04).
}
