# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
# Inventario de residencia (RNF-44): a saida abaixo e a lista declarada
# do ambiente. Regra: todo recurso de DADO em sa-east-1; as unicas linhas
# fora sao certificado/WAF do CloudFront (metadados, us-east-1 por
# exigencia do servico), nomeadas explicitamente.
output "inventory_residency_br" {
  value = {
    environment = var.environment_name
    kind        = var.environment_kind
    data_region = "sa-east-1"
    resources_in_sa_east_1 = [
      "vpc/subnets (foundation)",
      "alb/ecs-fargate/ecr (platform)",
      "guardduty/inspector/config (security, TR-091/TR-092/TR-096)",
      "ses-identities/configuration-sets/sns (messaging)",
      "route53-zone (edge, servico global com registros servidos globalmente; zona e metadado DNS publico)",
      "terraform-state (bucket backend.hcl, sa-east-1)",
    ]
    metadata_outside_region = [
      "acm-certificate cloudfront viewer (us-east-1, exigencia CloudFront; certificado publico, sem dado)",
      "wafv2-web-acl scope CLOUDFRONT (us-east-1, exigencia CloudFront; regras, sem dado)",
    ]
    replication_outside_region = "none"
    backups_outside_region     = "none"
  }
}

output "edge_distribution_domain" {
  value = module.edge.distribution_domain
}

output "ses_configuration_sets" {
  value = module.messaging.ses_configuration_sets
}
