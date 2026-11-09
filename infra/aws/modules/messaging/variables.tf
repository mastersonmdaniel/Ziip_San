# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
variable "environment_name" {
  type = string
}

variable "ses_tenants" {
  description = "Entregabilidade por tenant (P3.5, PLT-10): um item por tenant com dominio de envio proprio (msg.email_from_domain, classe d). Sem item, o tenant NAO envia por este ambiente."
  type = list(object({
    tenant_slug        = string
    email_from_domain  = string
    mail_from_subdomain = string
  }))
  default = []
}

variable "messaging_tenants" {
  description = "SMS/WhatsApp por tenant (ED-41): remetente de SMS (msg.sms_sender_id) e conta de WhatsApp (msg.whatsapp_account_id) sao REFERENCIAS (nomes no catalogo do tenant), nunca credenciais. Categorias de preco do WhatsApp: MARKETING, UTILITY, AUTHENTICATION (msg.template_category_map)."
  type = list(object({
    tenant_slug         = string
    sms_sender_ref      = string
    whatsapp_account_ref = string
    monthly_cap_ref     = string
  }))
  default = []
}

variable "alarm_email" {
  description = "E-mail operacional que recebe alarmes de custo/entregabilidade. [A DEFINIR] por ambiente; nulo desliga a assinatura (declarado, nunca presumido)."
  type        = string
  default     = null
}
