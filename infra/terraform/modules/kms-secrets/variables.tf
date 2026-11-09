# Pacote LC.4 — KMS e Secrets Manager: R1 em producao
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
variable "tenant_ids" {
  type        = list(string)
  description = "Tenants com DEK propria gerada pelo KMS. Lista real fornecida na janela AWS; vazia por padrao neste pacote (nenhum tenant inventado)."
  default     = []
}
variable "tenant_key_modes" {
  type        = map(string)
  description = "crypto.key_mode por tenant: PLATFORM (padrao do aceite), IMPORTED ou EXTERNAL — so sob demanda de contrato (PLT-41) e com aceite registrado do tenant (modelos na entrega)."
  default     = {}
  validation {
    condition     = alltrue([for m in values(var.tenant_key_modes) : contains(["PLATFORM", "IMPORTED", "EXTERNAL"], m)])
    error_message = "crypto.key_mode so admite PLATFORM, IMPORTED ou EXTERNAL (aceite LC.4/PLT-41)."
  }
}
variable "external_key_store_id" {
  type        = string
  description = "[A DEFINIR] ID do external key store (XKS) para tenants em modo EXTERNAL — so existe sob contrato que o exija (pendencia P-LC4-02)."
  default     = ""
}
variable "secret_names" {
  type        = list(string)
  description = "Nomes dos segredos de producao a criar no Secrets Manager (sem valores). A lista concreta e [A DEFINIR] com a LC.2/aplicacao (pendencia P-LC4-03)."
  default     = []
}
variable "rotation_lambda_arn" {
  type        = string
  description = "[A DEFINIR] Lambda de rotacao automatica dos segredos, quando aplicavel (pendencia P-LC4-03)."
  default     = ""
}
variable "kek_backup_procedure_ref" {
  type        = string
  description = "Referencia documental da forma de atender ao backup da KEK (exigencia do aceite: forma REGISTRADA). Ver REGISTRO-BACKUP-KEK.md na entrega."
  default     = "REGISTRO-BACKUP-KEK.md"
}
variable "tags" {
  type    = map(string)
  default = {}
}
