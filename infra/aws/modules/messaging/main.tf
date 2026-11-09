# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
# Correcao LC.2 (estagio 3), sessao b36fd7a8-91ec-4322-9e7e-8bfe051efd54:
# R-3 (identidade SES ligada ao configuration set do proprio tenant) e
# m3 (messaging_tenants consumido em saida; comentarios de alarme
# reescritos — nenhum recurso de alarme existia). Ver CORRECAO-LC.2.md.
# Mensageria (aceite LC.2): e-mail da AWS (Amazon SES em sa-east-1) com
# entregabilidade ISOLADA por tenant (P3.5/PLT-10) + base de SMS/custo.
#
# E-mail por tenant: identidade de dominio propria + configuration set
# PROPRIO por tenant, com eventos delivered/bounced/complained publicados
# em topico SNS proprio do tenant -> a aplicacao consome pela interface de
# e-mail da P0.45 e grava a prova (P3.5). Um tenant pausado/suprimido nao
# afeta os demais porque NADA aqui e compartilhado entre tenants, exceto
# a conta SES em si.
# SPF/DKIM/DMARC: DKIM de 3 tokens do SES + MAIL FROM proprio por tenant;
# os registros DNS (TXT/CNAME) sao saidas para a zona do tenant — o SPF e o
# DMARC vivem no dominio DO tenant (fora desta zona), e o procedimento de
# publicacao/conferencia esta em docs/operacao/MENSAGERIA.md. A IaC nao
# finge publicar DNS que nao controla.

resource "aws_sesv2_email_identity" "tenant" {
  for_each = { for t in var.ses_tenants : t.tenant_slug => t }
  email_identity = each.value.email_from_domain
  # R-3 (correcao): ligacao identidade -> configuration set DO MESMO
  # tenant. E o configuration set por omissao (default) desta identidade:
  # um envio feito a partir desta identidade, sem configuration set
  # explicito, usa este conjunto — e sao os eventos dele (SEND/DELIVERY/
  # BOUNCE/COMPLAINT/REJECT) que fluem para o SNS do tenant, a prova de
  # entregabilidade da P3.5. Sem esta linha, a identidade e o conjunto
  # coexistiam sem ligacao e os eventos nao fluiriam por omissao.
  # Obrigacao restante da aplicacao (docs/operacao/MENSAGERIA.md): nao
  # sobrepor outro configuration set no envio sem nomear o deste tenant.
  configuration_set_name = aws_sesv2_configuration_set.tenant[each.key].configuration_set_name
  tags = {
    Environment = var.environment_name
    TenantSlug  = each.value.tenant_slug
    Package     = "LC.2"
  }
}

resource "aws_ses_domain_mail_from" "tenant" {
  for_each         = { for t in var.ses_tenants : t.tenant_slug => t }
  domain           = each.value.email_from_domain
  mail_from_domain = "${each.value.mail_from_subdomain}.${each.value.email_from_domain}"
}

resource "aws_sns_topic" "ses_events" {
  for_each = { for t in var.ses_tenants : t.tenant_slug => t }
  name     = "ziipsan-${var.environment_name}-ses-events-${each.value.tenant_slug}"
  tags = {
    Environment = var.environment_name
    TenantSlug  = each.value.tenant_slug
    Package     = "LC.2"
  }
}

resource "aws_sesv2_configuration_set" "tenant" {
  for_each               = { for t in var.ses_tenants : t.tenant_slug => t }
  configuration_set_name = "ziipsan-${var.environment_name}-${each.value.tenant_slug}"
  tags = {
    Environment = var.environment_name
    TenantSlug  = each.value.tenant_slug
    Package     = "LC.2"
  }
}

resource "aws_sesv2_configuration_set_event_destination" "tenant" {
  for_each               = { for t in var.ses_tenants : t.tenant_slug => t }
  configuration_set_name = aws_sesv2_configuration_set.tenant[each.key].configuration_set_name
  event_destination_name = "app-delivery-events"
  event_destination {
    enabled = true
    matching_event_types = ["SEND", "DELIVERY", "BOUNCE", "COMPLAINT", "REJECT"]
    sns_destination {
      topic_arn = aws_sns_topic.ses_events[each.key].arn
    }
  }
}

# Supressao por tenant: a lista de supressao e da CONTA no SES; o isolamento
# exigido pela P3.5 e garantido na aplicacao (msg.suppression_policy por
# tenant, DeliveryStore por tenant). Declarado aqui para nao prometer no
# provedor o que o provedor nao separa: ver RELATORIO-LC.2, desvio D-2.

# --- SMS e custo por categoria (ED-41) ---
# SMS sai pelo Amazon SNS (atributo de remetente por tenant vem da
# referencia sms_sender_ref resolvida pela aplicacao contra o catalogo;
# o numero/credencial do provedor de WhatsApp e EXTERNO a AWS: a LC.2
# carrega so as referencias de messaging_tenants, expostas na saida
# messaging_tenant_refs (outputs.tf) para a aplicacao as resolver —
# nenhum recurso de SMS/WhatsApp e criado por este modulo. Categorias
# de preco do WhatsApp (MARKETING/UTILITY/AUTHENTICATION) vivem em
# msg.template_category_map na aplicacao (ED-41).
resource "aws_sns_topic" "messaging_alerts" {
  name = "ziipsan-${var.environment_name}-messaging-alerts"
  tags = { Environment = var.environment_name, Package = "LC.2" }
}

resource "aws_sns_topic_subscription" "ops_email" {
  count     = var.alarm_email == null ? 0 : 1
  topic_arn = aws_sns_topic.messaging_alerts.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# Gasto de mensageria: o teto fino, por tenant e por categoria, e regra
# da aplicacao na ED-41 (alertas a 80% e 100% do msg.monthly_cap).
# NENHUM recurso de alarme (aws_cloudwatch_metric_alarm) e criado neste
# modulo: o limiar de um alarme grosso de conta e [A DEFINIR] e, sem
# limiar aprovado, nao ha alarme a declarar. O que existe acima e so o
# topico messaging_alerts e a assinatura de e-mail opcional
# (alarm_email). (A versao anterior deste comentario anunciava "o alarme
# grosso de conta" como se existisse abaixo; nao existia — removido
# nesta correcao, ver CORRECAO-LC.2.md, m3.)
