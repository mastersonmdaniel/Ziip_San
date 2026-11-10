# Pacote LC.15 — Produtor, sessao b3a03bf9-b502-478f-a071-9590cb482d99
# Base: outputs.tf vivo da LC.2 (main), estendido com as saidas da pilha
# completa da LC.15.
# Inventario de residencia (RNF-44): todo recurso de DADO em sa-east-1; as
# unicas linhas fora sao certificado/WAF do CloudFront (metadados,
# us-east-1 por exigencia do servico), nomeadas explicitamente.
output "inventory_residency_br" {
  value = {
    environment = var.environment_name
    kind        = var.environment_kind
    data_region = "sa-east-1"
    resources_in_sa_east_1 = [
      "vpc/subnets (foundation)",
      "alb/ecs-fargate/ecr (platform)",
      "ecs worker + keycloak (stack.tf, mesmo cluster)",
      "rds postgresql 17 exclusivo + replica de leitura + cofre de backup (database.tf, modulo LC.3)",
      "kms/secrets manager (database.tf, modulo LC.4)",
      "guardduty/inspector/config (security, TR-091/TR-092/TR-096)",
      "cloudtrail + bucket de auditoria + log group de auditoria (access.tf, TR-104)",
      "ses-identities/configuration-sets/sns (messaging)",
      "route53-zone (edge, servico global com registros servidos globalmente; zona e metadado DNS publico)",
      "terraform-state (bucket backend.hcl, sa-east-1, na conta dedicada)",
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

output "dedicated_stack" {
  value = {
    account_scope       = "conta AWS separada do cliente (provider com assume_role; ver main.tf)"
    api_service         = module.platform.api_service_name
    worker_service      = aws_ecs_service.worker.name
    keycloak_service    = aws_ecs_service.keycloak.name
    keycloak_replicas   = aws_ecs_service.keycloak.desired_count
    cluster             = module.platform.cluster_name
    database_endpoint   = module.database.rds_primary_endpoint
    database_replica    = module.database.rds_replica_endpoint
    tenant_db_mode      = var.tenant_db_mode
    p46_dependency      = "none"
    platform_kms_key    = module.secrets.platform_kms_key_arn
    backup_vault        = module.database.backup_vault_name
    backup_plan         = module.database.backup_plan_id
  }
}

output "tr104_access" {
  value = {
    role_name       = aws_iam_role.orgao_ti_readonly.name
    role_arn        = aws_iam_role.orgao_ti_readonly.arn
    scope           = "somente esta instancia dedicada; inexistente nos ambientes compartilhados"
    access_level    = "read-only (configuracao e monitoramento)"
    mfa_required    = true
    audit_trail_arn = aws_cloudtrail.dedicated_audit.arn
    audit_bucket    = aws_s3_bucket.audit.id
  }
}
