# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
output "alb_dns_name" {
  value = aws_lb.this.dns_name
}

output "cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "api_service_name" {
  value = aws_ecs_service.api.name
}
