# Pacote LC.15 — Produtor, sessao b3a03bf9-b502-478f-a071-9590cb482d99
# Pilha propria, parte 2: WORKER e KEYCLOAK da instancia dedicada.
#
# Por que aqui e nao num modulo novo: o modulo `platform` da LC.2 entrega
# cluster ECS + servico api (a api desta instancia sai dele, em main.tf).
# Worker e Keycloak sao servicos ADICIONAIS sobre o MESMO cluster, com a
# MESMA imagem da release (worker) e a MESMA imagem do Keycloak de
# producao (LC.6) — composicao do ambiente, sem bifurcacao de codigo nem
# de versao (ADR-0024). Nenhum modulo partilhado foi alterado.
#
# Worker: a imagem unica (P0.5, Dockerfile na main) tem tres modos pelo
# entrypoint — api (padrao), worker, migrate. O worker desta instancia e
# a mesma imagem (var.container_image, a mesma variavel da api) com
# comando ["worker"].
#
# Keycloak: parametros herdados da LC.6 (valores-implantacao.yaml vivo na
# main): start --optimized --import-realm, 2 replicas fixas do aceite da
# LC.6, cache ispn/jdbc-ping sobre o banco compartilhado, TLS obrigatorio
# (sslRequired=all no realm da LC.6; aqui KC_HTTP_ENABLED=false e a
# terminacao TLS e da borda/ALB). Banco proprio (banco "keycloak" no RDS
# exclusivo desta instancia — ver database.tf e o roteiro de ativacao).

resource "aws_cloudwatch_log_group" "worker" {
  name              = "/ziipsan/${var.environment_name}/worker"
  retention_in_days = 30
  tags              = { Environment = var.environment_name, Package = "LC.15" }
}

resource "aws_cloudwatch_log_group" "keycloak" {
  name              = "/ziipsan/${var.environment_name}/keycloak"
  retention_in_days = 30
  tags              = { Environment = var.environment_name, Package = "LC.15" }
}

# Papel de execucao dos servicos adicionais: mesmo desenho do papel do
# modulo platform (ecs-tasks), escopado a este ambiente/conta. Segredos
# entram por ARN nas definicoes de tarefa da release (LC.4) — nenhum
# valor de segredo e declarado neste arquivo.
resource "aws_iam_role" "dedicated_task_execution" {
  name = "ziipsan-${var.environment_name}-dedicated-task-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
  tags = { Environment = var.environment_name, Package = "LC.15" }
}

resource "aws_iam_role_policy_attachment" "dedicated_task_execution" {
  role       = aws_iam_role.dedicated_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_ecs_task_definition" "worker" {
  family                   = "ziipsan-${var.environment_name}-worker"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.worker_cpu
  memory                   = var.worker_memory
  execution_role_arn       = aws_iam_role.dedicated_task_execution.arn
  container_definitions = jsonencode([{
    name      = "worker"
    image     = var.container_image
    essential = true
    command   = ["worker"]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.worker.name
        "awslogs-region"        = "sa-east-1"
        "awslogs-stream-prefix" = "worker"
      }
    }
  }])
  tags = { Environment = var.environment_name, Package = "LC.15" }
}

resource "aws_ecs_service" "worker" {
  name            = "ziipsan-${var.environment_name}-worker"
  cluster         = module.platform.cluster_name
  task_definition = aws_ecs_task_definition.worker.arn
  desired_count   = var.worker_desired_count
  launch_type     = "FARGATE"
  network_configuration {
    subnets          = module.foundation.private_subnet_ids
    assign_public_ip = false
  }
  tags = { Environment = var.environment_name, Package = "LC.15" }
}

resource "aws_ecs_task_definition" "keycloak" {
  family                   = "ziipsan-${var.environment_name}-keycloak"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.keycloak_cpu
  memory                   = var.keycloak_memory
  execution_role_arn       = aws_iam_role.dedicated_task_execution.arn
  container_definitions = jsonencode([{
    name      = "keycloak"
    image     = var.keycloak_image
    essential = true
    command   = ["start", "--optimized", "--import-realm"]
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]
    environment = [
      { name = "KC_DB", value = "postgres" },
      { name = "KC_DB_URL", value = "jdbc:postgresql://${module.database.rds_primary_endpoint}/keycloak" },
      { name = "KC_DB_USERNAME", value = "keycloak" },
      { name = "KC_CACHE", value = "ispn" },
      { name = "KC_CACHE_STACK", value = "jdbc-ping" },
      { name = "KC_HTTP_ENABLED", value = "false" },
      { name = "KC_HOSTNAME", value = var.keycloak_hostname },
      { name = "KC_HOSTNAME_STRICT", value = "true" },
      { name = "KC_HEALTH_ENABLED", value = "true" },
      { name = "KC_METRICS_ENABLED", value = "true" },
    ]
    # A senha do banco do Keycloak entra SO como referencia de segredo
    # (LC.4): o ARN vem de var.keycloak_db_secret_arn ([A DEFINIR] na
    # ativacao); o valor nunca aparece nesta IaC nem no state como texto
    # desta entrega (o ECS resolve a referencia em runtime).
    secrets = var.keycloak_db_secret_arn == "" ? [] : [{
      name      = "KC_DB_PASSWORD"
      valueFrom = var.keycloak_db_secret_arn
    }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.keycloak.name
        "awslogs-region"        = "sa-east-1"
        "awslogs-stream-prefix" = "keycloak"
      }
    }
  }])
  tags = { Environment = var.environment_name, Package = "LC.15" }
}

resource "aws_ecs_service" "keycloak" {
  name            = "ziipsan-${var.environment_name}-keycloak"
  cluster         = module.platform.cluster_name
  task_definition = aws_ecs_task_definition.keycloak.arn
  # 2 replicas: numero fixo do aceite da LC.6, reaplicado aqui — nao e
  # parametro livre.
  desired_count = 2
  launch_type   = "FARGATE"
  network_configuration {
    subnets          = module.foundation.private_subnet_ids
    assign_public_ip = false
  }
  tags = { Environment = var.environment_name, Package = "LC.15" }
}
