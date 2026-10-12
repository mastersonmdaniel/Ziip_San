# SIGSAN (Ziip SAN)

SaaS multi-tenant para prestadores de saneamento (água, esgoto e resíduos): cadastro comercial,
medição, faturamento, arrecadação, perdas, qualidade da água, GIS, MTR e LGPD.

- Backend: Java 21, Spring Boot 4.1, Hibernate 7, Flyway, PostgreSQL 15 + PostGIS.
  Um schema por tenant, com Row Level Security forçada; o tenant da requisição vem do token.
- Identidade: Keycloak 26, realm único `sigsan` (login com PKCE no frontend).
- Frontend: React 18 + TypeScript + Vite (`frontend/`).

Estado, decisões e histórico: [`docs/DIARIO-DE-BORDA.md`](docs/DIARIO-DE-BORDA.md).
Ordem de prioridade e plano: [`docs/PLANO-EXECUCAO-PDCA-R0-R9.md`](docs/PLANO-EXECUCAO-PDCA-R0-R9.md).

## Pré-requisitos

- Docker Desktop, Git Bash e Node 24. **Não** é preciso JDK nem Maven na máquina: o build roda num
  container Maven.
- O fonte fica no Google Drive (`G:`), que o Docker não enxerga. Por isso os scripts copiam o que
  precisam para uma pasta local (`%LOCALAPPDATA%\sigsan-build`, ou `SIGSAN_BUILD_DIR`) antes de
  montar no container. Edite sempre no `G:`; as cópias são descartáveis.
- Não edite a cópia antiga em `C:\PROJETOS\Ziip SAN\sigsan`.

## Configuração

Copie `.env.example` para `.env` (não versionado) e preencha todas as senhas. Não há valor padrão:
sem elas o `docker compose` e a aplicação recusam subir.

## Comandos (Git Bash, na raiz)

| Comando | O que faz |
|---|---|
| `scripts/dev-up.sh` | Sobe PostgreSQL e Keycloak de desenvolvimento (`--reset` apaga os volumes) |
| `scripts/kc-dev-user.sh <usuario> VIEWER OPERATOR` | Cria seu usuário no realm `sigsan` |
| `scripts/run-app.sh` | Gera o jar e roda a API (perfil `local`, porta 8090) |
| `cd frontend && npm ci && npm run dev` | Frontend em http://localhost:3000 |
| `scripts/reset-it-db.sh` | Recria o PostgreSQL descartável dos testes (porta 55433) |
| `scripts/build.sh clean test` | Build e testes Java (precisa do banco de integração) |
| `scripts/ci.sh` | Verificação completa: banco de teste, backend e frontend |

## Portas

| Porta | Serviço |
|---|---|
| 5432 | PostgreSQL de desenvolvimento (só 127.0.0.1) |
| 55433 | PostgreSQL descartável dos testes de integração |
| 8080 | Keycloak (realm `sigsan`) |
| 8090 | API (`/api/**`, `/actuator/health`) |
| 3000 | Frontend (Vite) |

## Regras do projeto

- Nunca semear dado inventado ou regulatório; ausência de dado é erro explícito.
- Dado pessoal: CPF/CNPJ só cifrado (com índice cego e máscara); nunca na URL; a trilha de
  auditoria redige colunas pessoais (`public.audit_redaction_rule`).
- JSON em `jsonb` sempre pela classe `br.com.sigsan.core.json.Json`.
- Build verde não prova correção: cada regra de negócio tem caso de teste calculado à mão.
