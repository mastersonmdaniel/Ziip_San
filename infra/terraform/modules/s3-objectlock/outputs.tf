# Pacote LC.5 — S3 com Object Lock
# Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor) — bloco de conclusao, 09/11/2026.
# Fonte unica do aceite: ~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json
# Sem segredo literal neste arquivo. Valores sem fonte ficam como variaveis
# obrigatorias marcadas [A DEFINIR] — nunca como constantes inventadas.
# Composicao com a LC.2: este modulo recebe rede/conta por variaveis de
# entrada; nao presume caminhos internos da LC.2 alem da arvore declarada
# no LANDING deste pacote.

output "bucket_name" { value = aws_s3_bucket.docs.bucket }
output "bucket_arn" { value = aws_s3_bucket.docs.arn }
output "object_lock_mode" { value = "COMPLIANCE" }
output "tenant_prefix_pattern" { value = "tenant/<tenant_id>/" }
