# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
output "distribution_id" {
  value = aws_cloudfront_distribution.edge.id
}

output "distribution_domain" {
  description = "Dominio da distribuicao CloudFront. E o ALVO do CNAME que o tenant publica no DNS dele para apontar o dominio proprio para esta borda (P2.27) — passo do tenant, documentado no README de infra/aws; esta IaC nao cria esse CNAME."
  value       = aws_cloudfront_distribution.edge.domain_name
}

output "tenant_dns_publication" {
  description = "Por dominio de tenant: os registos que o TENANT publica no DNS DELE (P2.27) — TXT de verificacao de posse e alvo do CNAME de apontamento. Sao dados para o procedimento de onboarding/encerramento; nenhum destes registos e criado por esta IaC."
  value = { for t in var.tenant_domains : t.domain => {
    txt_record_name  = "_ziipsan-verify.${t.domain}"
    txt_record_value = t.verification_txt
    cname_target     = aws_cloudfront_distribution.edge.domain_name
  } }
}

output "acm_validation_records" {
  description = "Opcoes de validacao DNS do certificado ACM (nome/tipo/valor por dominio). Os registos do dominio raiz sao criados nesta IaC (aws_route53_record.acm_validation); os de dominios de tenant sao publicados pelo tenant no DNS dele, sem o que o certificado nao e emitido."
  value       = aws_acm_certificate.edge.domain_validation_options
}

output "certificate_arn" {
  value = aws_acm_certificate.edge.arn
}

output "waf_arn" {
  value = aws_wafv2_web_acl.edge.arn
}

output "root_zone_id" {
  value = aws_route53_zone.root.zone_id
}
