# Pacote LC.3 — RDS, backup e PITR
# Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor) — bloco de conclusao, 09/11/2026.
# Fonte unica do aceite: ~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json
# Sem segredo literal neste arquivo. Valores sem fonte ficam como variaveis
# obrigatorias marcadas [A DEFINIR] — nunca como constantes inventadas.
# Composicao com a LC.2: este modulo recebe rede/conta por variaveis de
# entrada; nao presume caminhos internos da LC.2 alem da arvore declarada
# no LANDING deste pacote.
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

# Regiao travada pelo aceite (RNF-44): nada fora de sa-east-1.
variable "aws_region" {
  type    = string
  default = "sa-east-1"
  validation {
    condition     = var.aws_region == "sa-east-1"
    error_message = "RNF-44: regiao obrigatoria sa-east-1; nenhuma replica/backup fora dela."
  }
}
