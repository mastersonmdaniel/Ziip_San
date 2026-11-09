# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
# Rede minima do ambiente. Os servicos de dados (RDS da LC.3, KMS/Secrets
# da LC.4, S3 da LC.5) consomem as sub-redes e os grupos daqui pelas saidas
# deste modulo; a configuracao profunda deles NAO esta na LC.2.

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name_prefix = "ziipsan-${var.environment_name}"
  common_tags = {
    Project     = "ziipsan"
    Environment = var.environment_name
    EnvKind     = var.environment_kind
    TenantSlug  = var.tenant_slug == null ? "shared" : var.tenant_slug
    Region      = var.aws_region
    Residency   = "BR-sa-east-1"
    ManagedBy   = "terraform-lc2"
    Package     = "LC.2"
  }
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags = merge(local.common_tags, { Name = "${local.name_prefix}-vpc" })
}

resource "aws_subnet" "private" {
  count             = var.az_count
  vpc_id            = aws_vpc.this.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags              = merge(local.common_tags, { Name = "${local.name_prefix}-private-${count.index}", Tier = "private" })
}

resource "aws_subnet" "public" {
  count             = var.az_count
  vpc_id            = aws_vpc.this.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 20)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags              = merge(local.common_tags, { Name = "${local.name_prefix}-public-${count.index}", Tier = "public" })
}

# Endpoints de VPC na MESMA regiao: o trafego aos servicos AWS nao sai de
# sa-east-1 nem atravessa a internet publica.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = []
  tags              = merge(local.common_tags, { Name = "${local.name_prefix}-vpce-s3" })
}
