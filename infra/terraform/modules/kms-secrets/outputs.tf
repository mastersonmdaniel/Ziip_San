# Pacote LC.4 — KMS e Secrets Manager: R1 em producao
# Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor) — bloco de conclusao, 09/11/2026.
# Fonte unica do aceite: ~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json
# Sem segredo literal neste arquivo. Valores sem fonte ficam como variaveis
# obrigatorias marcadas [A DEFINIR] — nunca como constantes inventadas.
# Composicao com a LC.2: este modulo recebe rede/conta por variaveis de
# entrada; nao presume caminhos internos da LC.2 alem da arvore declarada
# no LANDING deste pacote.

output "platform_kms_key_arn" { value = aws_kms_key.platform.arn }
output "tenant_kms_key_arns" { value = { for t, k in aws_kms_key.tenant : t => k.arn } }
output "tenant_key_modes_effective" { value = local.key_mode }
output "secret_arns" { value = { for n, s in aws_secretsmanager_secret.prod : n => s.arn } }
output "kek_backup_procedure_ref" { value = var.kek_backup_procedure_ref }
