# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
output "vpc_id" {
  value = aws_vpc.this.id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "common_tags" {
  value = local.common_tags
}

output "name_prefix" {
  value = local.name_prefix
}
