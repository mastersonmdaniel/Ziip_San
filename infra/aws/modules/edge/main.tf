# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
# Correcao LC.2 (estagio 3), sessao b36fd7a8-91ec-4322-9e7e-8bfe051efd54:
# R-2 — modelo de dominio do tenant alinhado a P2.27 (TXT e CNAME no DNS
# DO TENANT; esta IaC nao cria registos na zona de dominios externos) e
# comentarios reescritos para dizerem so o que o codigo faz. Ver
# CORRECAO-LC.2.md.
# Borda (aceite LC.2): CloudFront + AWS WAF, TLS 1.3 e HSTS, certificado
# automatico (ACM com validacao DNS) e protecao contra tomada de subdominio.
#
# NOTA DE RESIDENCIA (declarada, nao escondida): por exigencia do proprio
# CloudFront, o certificado do viewer e o WAF de escopo CLOUDFRONT vivem na
# regiao us-east-1 (provedor aws.us_east_1, definido na composicao do
# ambiente). Sao metadados publicos (certificado e regras), NENHUM dado de
# tenant, replica ou backup: todo o dado permanece em sa-east-1 (RNF-44).
# O inventario de residencia lista estes dois artefatos nominalmente.

# --- Certificado automatico: emissao e renovacao pelo ACM, validacao DNS ---
resource "aws_acm_certificate" "edge" {
  provider                  = aws.us_east_1
  domain_name               = var.root_domain
  subject_alternative_names = concat(["*.${var.root_domain}"], [for t in var.tenant_domains : t.domain if t.possession_proven])
  validation_method         = "DNS"
  lifecycle {
    create_before_destroy = true
  }
  tags = { Environment = var.environment_name, Package = "LC.2" }
}

resource "aws_route53_zone" "root" {
  name = var.root_domain
  tags = { Environment = var.environment_name, Package = "LC.2" }
}

# Registos de validacao do ACM criados NESTA zona: apenas os do dominio
# raiz e do seu wildcard, que sao os dominios desta zona. As opcoes de
# validacao dos dominios de tenant sao EXCLUIDAS aqui de proposito: esses
# CNAMEs de validacao vivem no DNS do tenant e sao publicados pelo tenant
# (saida acm_validation_records; procedimento no README de infra/aws).
resource "aws_route53_record" "acm_validation" {
  for_each = {
    for dvo in aws_acm_certificate.edge.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    } if !contains([for t in var.tenant_domains : t.domain], dvo.domain_name)
  }
  zone_id = aws_route53_zone.root.zone_id
  name    = each.value.name
  type    = each.value.type
  ttl     = 60
  records = [each.value.record]
}

# Espera pela emissao do certificado. Nota precisa: o ACM so emite o
# certificado quando TODOS os nomes (raiz e SANs de tenant) estiverem
# validados; os FQDNs listados abaixo sao os desta zona (raiz), e a
# validacao de cada dominio de tenant depende de o tenant publicar o
# CNAME de validacao no DNS dele (ver comentario acima e o README).
# Sem essa publicacao pelo tenant, esta espera nao conclui — e o dominio
# nao deve ser acrescentado a tenant_domains antes dela.
resource "aws_acm_certificate_validation" "edge" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.edge.arn
  validation_record_fqdns = [for r in aws_route53_record.acm_validation : r.fqdn]
}

# --- WAF da borda (escopo CLOUDFRONT) ---
resource "aws_wafv2_web_acl" "edge" {
  provider    = aws.us_east_1
  name        = "ziipsan-${var.environment_name}-edge"
  scope       = "CLOUDFRONT"
  description = "WAF da borda LC.2 do ambiente ${var.environment_name}"
  default_action {
    allow {}
  }
  rule {
    name     = "managed-common"
    priority = 1
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "ziipsan-${var.environment_name}-common"
      sampled_requests_enabled   = true
    }
  }
  rule {
    name     = "rate-limit"
    priority = 2
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit              = var.waf_rate_limit
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "ziipsan-${var.environment_name}-rate"
      sampled_requests_enabled   = true
    }
  }
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "ziipsan-${var.environment_name}-edge"
    sampled_requests_enabled   = true
  }
}

# --- HSTS e cabecalhos de seguranca ---
resource "aws_cloudfront_response_headers_policy" "security" {
  name = "ziipsan-${var.environment_name}-security-headers"
  security_headers_config {
    strict_transport_security {
      access_control_max_age_sec = 63072000
      include_subdomains         = true
      preload                    = true
      override                   = true
    }
    content_type_options {
      override = true
    }
    frame_options {
      frame_option = "DENY"
      override     = true
    }
    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }
  }
}

# --- Distribuicao: somente TLS 1.2 de 2021 em diante (= TLS 1.3 habilitado) ---
resource "aws_cloudfront_distribution" "edge" {
  enabled             = true
  aliases             = concat([var.root_domain], [for t in var.tenant_domains : t.domain if t.possession_proven])
  default_root_object = "index.html"
  web_acl_id          = aws_wafv2_web_acl.edge.arn

  origin {
    domain_name = var.origin_domain
    origin_id   = "alb-${var.environment_name}"
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    target_origin_id           = "alb-${var.environment_name}"
    viewer_protocol_policy     = "redirect-to-https"
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
    forwarded_values {
      query_string = true
      headers      = ["Host", "Authorization"]
      cookies {
        forward = "all"
      }
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # TLSv1.2_2021 e a politica que habilita TLS 1.3 no CloudFront.
  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.edge.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = { Environment = var.environment_name, Package = "LC.2" }
}

# --- Dominio raiz aponta SOMENTE para a distribuicao (alias, nunca CNAME solto) ---
resource "aws_route53_record" "root_alias" {
  zone_id = aws_route53_zone.root.zone_id
  name    = var.root_domain
  type    = "A"
  alias {
    name                   = aws_cloudfront_distribution.edge.domain_name
    zone_id                = aws_cloudfront_distribution.edge.hosted_zone_id
    evaluate_target_health = false
  }
}

# --- Dominio proprio do tenant (P2.27/PLT-05) + anti-tomada de subdominio ---
# Modelo da P2.27 (aceite lido na fonte): "CNAME do tenant apontado para a
# borda do Ziip, com verificacao de posse por registro TXT". Ou seja, o TXT
# de verificacao e o CNAME de apontamento vivem no DNS DO TENANT — esta IaC
# NAO cria nenhum registo no dominio do tenant, porque nao controla essa
# zona, e nenhum recurso deste modulo finge cria-lo.
#
# O que este codigo faz, e so isto:
#  1. Um dominio de tenant so entra nos SANs do certificado (acima) e nos
#     aliases da distribuicao (acima) quando o item traz
#     possession_proven = true — marca que cabe a aplicacao (P2.27,
#     platform.tenant_domain) apos conferir o TXT que o TENANT publicou
#     no DNS dele. Sem posse provada, o dominio nao e servido: e esta a
#     protecao contra tomada de subdominio implementada aqui.
#  2. A saida tenant_dns_publication (outputs.tf) devolve, por dominio,
#     o nome/valor do TXT a publicar pelo tenant e o alvo do CNAME (o
#     dominio da distribuicao) — dados para o procedimento, nao registos.
#  3. No encerramento do contrato, remover o item de tenant_domains tira
#     o dominio dos SANs e dos aliases no mesmo apply: a distribuicao
#     deixa de o servir. O CNAME/TXT que restarem no DNS do tenant sao
#     registos do tenant, fora do alcance desta IaC; o procedimento de
#     encerramento (README de infra/aws) manda confirma-los removidos
#     pelo tenant — um CNAME residual apontaria para uma distribuicao
#     que ja nao tem esse alias, nao para outro tenant.
# (Versao anterior deste bloco criava o TXT na zona raiz da plataforma e
# afirmava um ALIAS de tenant gerido por esta IaC que nao existia no
# codigo; ambos foram removidos nesta correcao — ver CORRECAO-LC.2.md.)
