# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
output "ses_configuration_sets" {
  value = { for k, v in aws_sesv2_configuration_set.tenant : k => v.configuration_set_name }
}

output "ses_event_topics" {
  value = { for k, v in aws_sns_topic.ses_events : k => v.arn }
}

output "ses_identity_arns" {
  description = "ARN das identidades SES por tenant. Os registros DKIM (3 CNAMEs) sao lidos da identidade criada, no ato da aplicacao, e publicados na zona do tenant (procedimento em docs/operacao/MENSAGERIA.md)."
  value       = { for k, v in aws_sesv2_email_identity.tenant : k => v.arn }
}

output "ses_identity_configuration_sets" {
  description = "Ligacao identidade -> configuration set por tenant (R-3): o configuration_set_name declarado em cada aws_sesv2_email_identity.tenant — o conjunto por omissao dos envios dessa identidade, cujos eventos vao para o SNS do tenant."
  value       = { for k, v in aws_sesv2_email_identity.tenant : k => v.configuration_set_name }
}

output "messaging_tenant_refs" {
  description = "Consumo de messaging_tenants (m3): referencias de SMS/WhatsApp por tenant (remetente, conta e teto — nomes no catalogo do tenant, nunca credenciais), expostas para a aplicacao as resolver (ED-41). Esta saida e o unico consumo da variavel neste modulo: nenhum recurso de SMS/WhatsApp e criado aqui."
  value = { for t in var.messaging_tenants : t.tenant_slug => {
    sms_sender_ref       = t.sms_sender_ref
    whatsapp_account_ref = t.whatsapp_account_ref
    monthly_cap_ref      = t.monthly_cap_ref
  } }
}
