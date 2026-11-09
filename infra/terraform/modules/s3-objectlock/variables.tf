# Pacote LC.5 — S3 com Object Lock
# Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor) — bloco de conclusao, 09/11/2026.
# Fonte unica do aceite: ~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json
# Sem segredo literal neste arquivo. Valores sem fonte ficam como variaveis
# obrigatorias marcadas [A DEFINIR] — nunca como constantes inventadas.
# Composicao com a LC.2: este modulo recebe rede/conta por variaveis de
# entrada; nao presume caminhos internos da LC.2 alem da arvore declarada
# no LANDING deste pacote.

variable "environment" {
  type        = string
  description = "Ambiente consumidor (LC.2)."
}
variable "bucket_name" {
  type        = string
  description = "[A DEFINIR] Nome do bucket de documentos/XML retidos — convencao de nomes e da LC.2 (pendencia P-LC5-01). Object Lock so pode ser ligado na CRIACAO do bucket."
}
variable "kms_key_arn" {
  type        = string
  description = "Chave KMS (modulo LC.4) para SSE-KMS dos objetos."
}
variable "retention_days" {
  type        = number
  description = "[A DEFINIR] Retencao do Object Lock em modo de conformidade (dias) para XML retido — prazo fiscal/contratual nao consta do aceite (pendencia P-LC5-02)."
}
variable "tenant_ids" {
  type        = list(string)
  description = "Tenants com prefixo proprio tenant/<tenant_id>/ e chave por tenant via condicao de prefixo na policy. Lista real na janela AWS; vazia por padrao (nenhum tenant inventado)."
  default     = []
}
variable "tags" {
  type    = map(string)
  default = {}
}
