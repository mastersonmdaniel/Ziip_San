# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
# Plataforma minima do ambiente: ALB (TLS 1.3) + cluster ECS Fargate com a
# MESMA imagem em todos os ambientes (ADR-0024) + repositorio ECR.
# Keycloak de producao e da LC.6 e a observabilidade gerenciada e da LC.7:
# aqui ficam so o cluster, o servico api e as saidas que elas usam.
# (Correcao da revisao: o comentario prometia "api/worker", mas este
# modulo so declara o servico api — nenhum servico worker existe nele.
# Sessao da correcao: b36fd7a8-91ec-4322-9e7e-8bfe051efd54.)
# RDS (LC.3) e segredos (LC.4) entram por referencia (ARN), nunca por valor.

resource "aws_ecr_repository" "app" {
  name                 = "ziipsan-${var.environment_name}/app"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
  tags = { Environment = var.environment_name, Package = "LC.2" }
}

resource "aws_security_group" "alb" {
  name   = "ziipsan-${var.environment_name}-alb"
  vpc_id = var.vpc_id
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTPS da borda (CloudFront)"
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Environment = var.environment_name, Package = "LC.2" }
}

resource "aws_lb" "this" {
  name               = "ziipsan-${var.environment_name}"
  load_balancer_type = "application"
  subnets            = var.public_subnet_ids
  security_groups    = [aws_security_group.alb.id]
  tags               = { Environment = var.environment_name, Package = "LC.2" }
}

resource "aws_lb_target_group" "api" {
  name        = "ziipsan-${var.environment_name}-api"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id
  health_check {
    path = "/actuator/health"
  }
  tags = { Environment = var.environment_name, Package = "LC.2" }
}

# TLS 1.3 no ALB: politica TLS13-1-2 de 2021. Sem certificado regional
# (certificate_arn_alb nulo), o listener HTTPS nao e criado e o ambiente
# nao e dado como publicado — nunca ha listener HTTP publico como atalho.
resource "aws_lb_listener" "https" {
  count             = var.certificate_arn_alb == null ? 0 : 1
  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn_alb
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}

resource "aws_ecs_cluster" "this" {
  name = "ziipsan-${var.environment_name}"
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  tags = { Environment = var.environment_name, Package = "LC.2" }
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ziipsan/${var.environment_name}/api"
  retention_in_days = 30
  tags              = { Environment = var.environment_name, Package = "LC.2" }
}

# Papel de execucao: referencia segredos por ARN na definicao da tarefa da
# release (LC.4); nenhum valor de segredo e declarado neste arquivo.
resource "aws_iam_role" "task_execution" {
  name = "ziipsan-${var.environment_name}-task-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
  tags = { Environment = var.environment_name, Package = "LC.2" }
}

resource "aws_ecs_task_definition" "api" {
  family                   = "ziipsan-${var.environment_name}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.api_cpu
  memory                   = var.api_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  container_definitions = jsonencode([{
    name      = "api"
    image     = var.container_image
    essential = true
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = "sa-east-1"
        "awslogs-stream-prefix" = "api"
      }
    }
  }])
  tags = { Environment = var.environment_name, Package = "LC.2" }
}

resource "aws_ecs_service" "api" {
  name            = "ziipsan-${var.environment_name}-api"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.api_desired_count
  launch_type     = "FARGATE"
  network_configuration {
    subnets = var.private_subnet_ids
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }
  tags = { Environment = var.environment_name, Package = "LC.2" }
}
