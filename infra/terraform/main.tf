terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  backend "s3" {
    bucket         = "sigsan-terraform-state"
    key            = "infrastructure/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "sigsan"
      Environment = var.environment
      ManagedBy   = "terraform"
      Owner       = "platform-team"
    }
  }
}

# Random providers for secrets
resource "random_password" "postgres_password" {
  length  = 32
  special = true
  override_special = "!@#$%^&*()_+-="
}

resource "random_password" "redis_password" {
  length  = 32
  special = true
}

resource "random_password" "keycloak_password" {
  length  = 24
  special = true
}

resource "random_password" "grafana_password" {
  length  = 24
  special = true
}

resource "random_password" "minio_password" {
  length  = 32
  special = true
}

resource "random_password" "vault_root_token" {
  length  = 32
  special = true
}

resource "tls_private_key" "ssh_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

# Data sources
data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
}

output "postgres_password" {
  value     = random_password.postgres_password.result
  sensitive = true
}

output "redis_password" {
  value     = random_password.redis_password.result
  sensitive = true
}

output "keycloak_password" {
  value     = random_password.keycloak_password.result
  sensitive = true
}

output "grafana_password" {
  value     = random_password.grafana_password.result
  sensitive = true
}

output "minio_password" {
  value     = random_password.minio_password.result
  sensitive = true
}

output "vault_root_token" {
  value     = random_password.vault_root_token.result
  sensitive = true
}

output "ssh_public_key" {
  value = tls_private_key.ssh_key.public_key_openssh
}

output "ssh_private_key_pem" {
  value     = tls_private_key.ssh_key.private_key_pem
  sensitive = true
}