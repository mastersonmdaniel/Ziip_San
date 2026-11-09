# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
variable "environment_name" {
  type = string
}

variable "root_domain" {
  description = "Dominio raiz do ambiente (ex.: o dominio da borda do ambiente). Valor concreto [A DEFINIR] no tfvars de cada ambiente."
  type        = string
}

variable "origin_domain" {
  description = "Dominio de origem (ALB do modulo platform). Ligado pela composicao do ambiente."
  type        = string
}

variable "tenant_domains" {
  description = "Dominios proprios dos tenants (P2.27, PLT-05): um item por dominio. verification_txt e o valor que o TENANT publica como TXT _ziipsan-verify.<dominio> no DNS DELE; possession_proven e a marca da aplicacao (platform.tenant_domain) de que conferiu esse TXT. So itens com possession_proven = true entram nos SANs do certificado e nos aliases da distribuicao. Esta IaC nao publica registo nenhum no dominio do tenant."
  type = list(object({
    tenant_slug       = string
    domain            = string
    verification_txt  = string
    possession_proven = bool
  }))
  default = []
}

variable "waf_rate_limit" {
  description = "Limite de requisicoes do WAF por IP/janela. [A DEFINIR] — sem padrao presumido; obrigatorio no tfvars."
  type        = number
}
